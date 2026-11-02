package com.cripta.app.data.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.room.withTransaction
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FaceMergeEntity
import com.cripta.app.data.db.FaceRejectionEntity
import com.cripta.app.data.db.FaceScanEntity
import com.cripta.app.data.db.FaceTrackEntity
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.IgnoredFaceEntity
import com.cripta.app.data.db.FaceApartEntity
import com.cripta.app.data.db.PersonEntity
import com.cripta.app.data.db.PersonMediaEntity
import com.cripta.app.data.db.TrackCard
import com.cripta.app.data.db.TrackMeta
import com.cripta.app.data.db.TrackRow
import com.cripta.app.media.ChannelMediaDataSource
import com.cripta.app.media.VideoFrameReader
import com.cripta.app.media.face.EmbeddingCodec
import com.cripta.app.media.face.FaceCodec
import com.cripta.app.media.face.FaceEngine
import com.cripta.app.media.face.FaceFollower
import com.cripta.app.media.face.FaceImage
import com.cripta.app.media.face.FaceReject
import com.cripta.app.media.face.FaceStats
import com.cripta.app.media.face.FaceTracker
import com.cripta.app.media.face.FramePlan
import com.cripta.app.media.face.KnownHere
import com.cripta.app.media.face.LooseFace
import com.cripta.app.media.face.SeenFace
import com.cripta.app.media.face.SeenFrame
import com.cripta.app.media.face.Sighting
import com.cripta.app.media.face.StoredFace
import com.cripta.app.media.face.TrackDraft
import com.cripta.app.media.face.TrackInfo
import com.cripta.app.media.face.similarity
import com.cripta.app.security.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** A change the user can take back from the snackbar. */
typealias Undo = suspend () -> Unit

/**
 * People: finds the faces of every photo and video, groups them by person across the whole vault,
 * stores the user's corrections and explains every decision. Everything is read in memory from the
 * encrypted files and stored in the encrypted database.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@Singleton
class FaceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val repo: VaultRepository,
) {
    private val db get() = session.requireDb()
    private val dao get() = db.faceDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var engine: FaceEngine? = null
    private val engineLock = Any()
    /** Callers inside [withEngine] right now, and whether [release] waits for them to finish. */
    private var engineUsers = 0
    private var releaseWanted = false

    /**
     * Run [block] with the models, loaded on first use (about 10 MB) and kept until [release].
     * Counted, so a scan ending (and releasing them) never closes them under another caller, e.g.
     * the viewer looking for the faces of a frame: that would run a closed ONNX session.
     */
    private inline fun <T> withEngine(block: (FaceEngine) -> T): T {
        val e = synchronized(engineLock) {
            engineUsers++
            runCatching { engine ?: FaceEngine(asset("faces/yunet.onnx"), asset("faces/sface.onnx")).also { engine = it } }
                .onFailure { engineUsers-- }.getOrThrow()
        }
        try {
            return block(e)
        } finally {
            synchronized(engineLock) {
                engineUsers--
                if (engineUsers == 0 && releaseWanted) { engine?.close(); engine = null; releaseWanted = false }
            }
        }
    }

    private fun asset(name: String) = context.assets.open(name).use { it.readBytes() }

    /** Free the models after a scan, as soon as nobody is using them. */
    fun release() = synchronized(engineLock) {
        if (engineUsers == 0) { engine?.close(); engine = null } else releaseWanted = true
    }

    /** One writer at a time: placing new faces and the user's changes never interleave. */
    private val mutex = Mutex()

    /** Face pictures already decoded (track ids are never reused within one database). */
    private val crops = LruCache<Long, Bitmap>(300)
    /** The database [crops] belong to: another vault or a restored backup counts ids from 1 again. */
    private var cropsOf: Any? = null
    /** Frames shown by the suggestion pages (about 1 MB each), and the database they belong to. */
    private val frames = LruCache<String, Bitmap>(16)
    private var framesOf: Any? = null

    // ---- Analysis --------------------------------------------------------------------------

    /** Live photos and videos never analysed (or by an older analyser). */
    suspend fun pending(): List<FileEntity> {
        val done = dao.allScans().associate { it.fileId to it.version }
        return repo.allFilesSnapshot().filter { f ->
            (VaultRepository.isVideo(f.mimeType) || VaultRepository.isImage(f.mimeType)) && (done[f.id] ?: -1) < VERSION
        }
    }

    /** Faces and links of media deleted for good (media in the trash keep theirs). */
    suspend fun purgeDeleted(): Int = mutex.withLock {
        db.withTransaction {
            dao.purgeScans() + dao.purgeTracks() + dao.purgeLinks().also { dao.deleteEmptyPersons() } + dao.purgeRejections() + dao.purgeIgnored()
        }
    }

    /**
     * Forget [fileId]'s analysis before analysing it again. The faces the user confirmed or added
     * stay (the analysis carries them to the faces it finds again): "Analizza di nuovo" deleted
     * them along with the rest.
     */
    suspend fun forget(fileId: String): FileEntity? {
        val f = repo.fileById(fileId)?.takeIf { it.deletedAt == null } ?: return null
        mutex.withLock {
            db.withTransaction { dao.deleteUnconfirmedTracksOf(fileId); dao.deleteScan(fileId); dao.purgeRejections() }
        }
        return f
    }

    /** Find and store [file]'s faces (not placed among the people yet). True when it could be read. */
    /**
     * [deep]: "Cerca più a fondo", for media where no face was found (slower, see [FramePlan.times]).
     * [onProgress]: how far into a video it is (0..1), frame by frame, for the notification.
     * [watched]: a reading that stops moving is left behind (the scan of the whole vault); media the
     * user asked for are waited for as long as they take.
     */
    suspend fun analyse(file: FileEntity, deep: Boolean = false, watched: Boolean = true, onProgress: ((Float) -> Unit)? = null): Outcome {
        val started = android.os.SystemClock.elapsedRealtime()
        val sightings = ArrayList<Sighting>()
        val rejects = HashMap<FaceReject, Int>()
        val sharp = IntArray(FaceStats.SHARP_BANDS.size + 1)
        var faces = 0; var read = 0; var failed = 0; var duplicates = 0; var extra = 0
        // When the reading last moved on (a frame read or analysed): what the watchdog looks at.
        val beat = java.util.concurrent.atomic.AtomicLong(started)
        val reader = readers.async {
            // Its own hold on the models: a reading left behind (below) may still be running them.
            withEngine { engine ->
                /** Analyse one frame; true when it shows a face (usable or not). */
                fun take(frame: Int, timeMs: Long, img: FaceImage): Boolean {
                    read++
                    val found = engine.analyze(img, deep = deep)
                    beat.set(android.os.SystemClock.elapsedRealtime())
                    for (f in found) {
                        faces++
                        f.sharpness?.let { sharp[FaceStats.band(it)]++ }
                        if (f.reject != null) rejects.merge(f.reject, 1, Int::plus)
                        else sightings += Sighting(frame, timeMs, f.embedding!!, f.quality, f)
                    }
                    return found.isNotEmpty()
                }
                if (VaultRepository.isVideo(file.mimeType)) {
                    val r = videoFrames(file, deep, { p -> beat.set(android.os.SystemClock.elapsedRealtime()); onProgress?.invoke(p) }) { i, t, img -> take(i, t, img) }
                    failed = r.failed; duplicates = r.duplicates; extra = r.extra
                    when {
                        r.why != null -> "skip:${r.why}"
                        r.read == 0 -> "skip:nessun fotogramma leggibile"
                        else -> "ok"
                    }
                } else {
                    val img = photo(file, if (deep) DEEP_SIDE else FRAME_SIDE)
                    if (img == null) "skip:immagine non decodificabile" else { take(0, 0, img); "ok" }
                }
            }
        }
        // A media whose reading stops moving (a decoder that never returns a frame) is left behind
        // after a while without a single frame, and the scan goes on with the next one: one such
        // file kept the whole scan at 0. Judged frame by frame, never on the whole media, so a deep
        // search, slow but moving, is never cut; and media asked for by hand never are.
        val stallMs = if (deep) STALL_DEEP_MS else STALL_MS
        var stalled = false
        val status = try {
            var outcome: String? = null
            while (outcome == null) {
                outcome = kotlinx.coroutines.withTimeoutOrNull(5_000) { reader.await() }
                if (outcome == null && watched && android.os.SystemClock.elapsedRealtime() - beat.get() > stallMs) {
                    stalled = true
                    android.util.Log.w("FaceRepository", "reading stalled, left behind")
                    outcome = "$STALLED (nessun fotogramma in ${stallMs / 1000} s): riprova dai dettagli del media, senza limite di tempo"
                }
            }
            outcome
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            "skip:troppo grande per la memoria"
        } catch (e: Exception) {
            android.util.Log.w("FaceRepository", "not readable (${e.javaClass.simpleName})")
            "skip:errore di lettura (${e.javaClass.simpleName})"
        } finally {
            // Cancelled, stalled or done: a reading still running stops (its channel closes, see
            // [videoFrames]); nothing waits for it.
            reader.cancel()
        }
        // Under the writers' lock (as placing and the user's changes): a change made to this media's
        // faces while it was being read is not overwritten with what was read before it. A reading
        // left behind may still add to its lists: none of it is kept.
        mutex.withLock {
            if (stalled) store(file, status, emptyList(), HashMap(), 0, 0, 0, IntArray(sharp.size), 0, 0, started)
            else store(file, status, sightings, rejects, read, failed, faces, sharp, duplicates, extra, started)
        }
        return when {
            status == "ok" -> Outcome.OK
            stalled -> Outcome.STALLED
            else -> Outcome.UNREADABLE
        }
    }

    /** How the analysis of one media ended. */
    enum class Outcome { OK, UNREADABLE, STALLED }

    /**
     * Where media are read for [analyse]: not the scan's own job, so that a reading that never
     * returns can be left behind instead of holding the whole scan.
     */
    private val readers = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private suspend fun store(
        file: FileEntity, status: String, sightings: List<Sighting>, rejects: HashMap<FaceReject, Int>,
        read: Int, failed: Int, faces: Int, sharp: IntArray, duplicates: Int, extra: Int, started: Long,
    ) {
        // Faces the user deleted from this media, or said are not a face, are not found again. Unless
        // the face is more like one the user kept here: deleting a second copy of someone's face
        // must not hide that person from this media at the next analysis.
        val ignored = dao.ignoredFor(file.id).map { it.fileId to EmbeddingCodec.decode(it.embeddings) }
        val kept = dao.trackRowsOf(listOf(file.id)).filter { it.personId != null && it.state != "suggested" }
            .map { EmbeddingCodec.decode(it.embeddings) }
        val tracks = FaceTracker.build(sightings).filter { t ->
            val keptLike = kept.maxOfOrNull { maxPair(t.embeddings, it) } ?: -1f
            val gone = ignored.any { (f, e) -> maxPair(t.embeddings, e).let { s -> s >= (if (f == null) NOT_A_FACE else DELETED) && s > keptLike } }
            if (gone) rejects.merge(FaceReject.IGNORED, t.sightings.size, Int::plus)
            !gone
        }
        db.withTransaction {
            // Faces added by hand (no sampled frame) are the user's: a new analysis keeps them.
            val old = dao.trackRowsOf(listOf(file.id)).filter { it.frames != 0L }
            dao.deleteScannedTracksOf(file.id)
            val added = tracks.map { t ->
                val best = t.best.first()
                val face = best.payload as FaceEngine.Face
                val stored = t.sightings.map { s ->
                    val f = s.payload as FaceEngine.Face
                    StoredFace(s.frame, s.timeMs, s.quality, f.det.w.toInt(), f.box ?: FloatArray(4), s.embedding)
                }
                TrackInfo(dao.addTrack(FaceTrackEntity(fileId = file.id, frames = t.frames, timeMs = best.timeMs, faceCount = t.sightings.size,
                    width = face.det.w.toInt(), quality = t.quality, embeddings = EmbeddingCodec.encode(t.embeddings),
                    crop = jpeg(face.preview!!), times = t.times.joinToString(","), box = boxText(face.box),
                    sightings = FaceCodec.encode(stored))), file.id, t.frames, t.embeddings, t.quality)
            }
            if (old.isNotEmpty()) carry(old, added)
            dao.putScan(FaceScanEntity(file.id, status, read, failed, faces,
                rejects.entries.joinToString(",") { "${it.key.name}=${it.value}" }, VERSION, System.currentTimeMillis(),
                ms = android.os.SystemClock.elapsedRealtime() - started, stats = FaceStats.encode(sharp, duplicates, extra)))
        }
    }

    /**
     * The user's choices about [old] (the faces a media had before it was analysed again) move to
     * the new faces they match: same frames and alike. A confirmed face comes back "carried" and is
     * checked against its person when placed ([settle]).
     */
    private suspend fun carry(old: List<TrackRow>, added: List<TrackInfo>) {
        val rejections = dao.rejections().groupBy { it.trackId }
        // Two faces the user gave to different people may now be one track: the first keeps it.
        val placed = HashMap<Long, Long>()
        for (o in old) {
            val before = info(o)
            val match = added.filter { it.frames and o.frames != 0L }.map { it to maxPair(it, before) }
                .filter { it.second >= CARRY }.maxByOrNull { it.second }?.first ?: continue
            if (o.state == "manual" && o.personId != null && placed.getOrPut(match.id) { o.personId } == o.personId)
                dao.place(match.id, o.personId, "carried", o.similarity)
            for (r in rejections[o.id].orEmpty()) { dao.deleteRejection(o.id, r.personId); dao.addRejection(r.copy(trackId = match.id)) }
            dao.moveCover(o.id, match.id)
        }
    }

    private class FrameRun(val read: Int, val failed: Int, val duplicates: Int, val extra: Int, val why: String?)

    /**
     * MediaMetadataRetriever, for videos the app's own decoder cannot read ([VideoFrames]). Some
     * decoders return nothing for the small RGB_565 copy or for the key frame asked: then the
     * default config, then the exact frame, before the moment counts as unreadable (whole videos
     * showed as "non leggibili" in Senza volti that way). Each way that fails costs seconds, so the
     * one that worked last is asked first for the next frames; the others still follow, no frame
     * is lost.
     */
    private class Retrieved(source: ChannelMediaDataSource) : java.io.Closeable {
        private val r = MediaMetadataRetriever().apply { setDataSource(source) }
        private val params = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.RGB_565 }
        /** The way that gave the last frame: the option and whether as the small RGB_565 copy. */
        private var worked: Pair<Int, Boolean>? = null

        fun durationMs(): Long? = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()

        fun frame(t: Long, option: Int): Bitmap? {
            // The same way twice (the exact frame asked already) is asked once.
            val ways = listOf(option to true, option to false, MediaMetadataRetriever.OPTION_CLOSEST to false).distinct()
            for (way in ways.sortedByDescending { it == worked }) {
                val (o, small) = way
                val bmp = runCatching { if (small) r.getFrameAtTime(t * 1000, o, params) else r.getFrameAtTime(t * 1000, o) }.getOrNull()
                    ?: continue
                worked = way
                return bmp
            }
            return null
        }

        override fun close() { runCatching { r.release() } }
    }

    /**
     * Where [videoFrames] takes a video's frames: the app's own decoder ([VideoFrameReader], in
     * process, milliseconds a frame), else the retriever ([Retrieved], seconds a frame through the
     * media server) once no decoder here can read the video. Each opens its own decrypting channel,
     * kept in [channels] so that a cancelled scan closes it (see [videoFrames]).
     */
    private inner class VideoFrames(private val file: FileEntity, private val channels: MutableList<java.nio.channels.SeekableByteChannel>) : java.io.Closeable {
        private var codec: VideoFrameReader? = VideoFrameReader.open(source())
        private var retrieved: Retrieved? = null
        private var noRetriever = false

        private fun source() = ChannelMediaDataSource(repo.seekableChannel(file).also { channels += it }, file.sizeBytes)

        /**
         * Null when the retriever refuses the video too: the frames left count as unreadable, those
         * the app's decoder already read are kept (an exception here lost the whole media).
         */
        private fun retriever(): Retrieved? {
            retrieved?.let { return it }
            if (noRetriever) return null
            return try {
                Retrieved(source()).also { retrieved = it }
            } catch (e: Exception) {
                noRetriever = true
                null
            }
        }

        fun durationMs(): Long? = file.durationMs?.takeIf { it > 0 } ?: codec?.durationMs?.takeIf { it > 0 } ?: retriever()?.durationMs()

        /** When the frame [at] returned last really is (ms); null when the reader cannot tell (a key frame of the retriever). */
        var lastMs: Long? = null
            private set

        /** The frame at [t] ms (see [VideoFrameReader.frame] for [exact]), or null when it cannot be read. */
        suspend fun at(t: Long, exact: Boolean, side: Int = FRAME_SIDE): FaceImage? {
            codec?.let { c ->
                try {
                    return c.frame(t, exact, side).also { lastMs = c.lastMs }
                } catch (e: VideoFrameReader.Unreadable) {
                    fallBack()
                }
            }
            coroutineContext.ensureActive()
            val option = if (exact) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            return retriever()?.frame(t, option)?.let { faceImage(it, side) }.also { lastMs = if (exact) t else null }
        }

        /** From now on the retriever. False when already there. */
        fun fallBack(): Boolean {
            val c = codec ?: return false
            android.util.Log.i("FaceRepository", "video decoder cannot read it, retriever instead")
            c.close()
            codec = null
            return true
        }

        override fun close() {
            codec?.close()
            retrieved?.close()
        }
    }

    /**
     * Read [file]'s sampled frames one at a time (longest side at most [FRAME_SIDE]). The same
     * keyframe returned for two moments is analysed once; where a face shows, the exact frame
     * [REFINE_MS] later is read too (another angle, often a better one).
     */
    private suspend fun videoFrames(file: FileEntity, deep: Boolean, onProgress: ((Float) -> Unit)?, onFrame: (Int, Long, FaceImage) -> Boolean): FrameRun = coroutineScope {
        val channels = java.util.concurrent.CopyOnWriteArrayList<java.nio.channels.SeekableByteChannel>()
        // Annulla while a frame is being read (an exact frame can take seconds): the channels close
        // at once, so the read fails now instead of the scan waiting for the frame to be done.
        val closer = launch { try { awaitCancellation() } finally { channels.forEach { runCatching { it.close() } } } }
        var frames: VideoFrames? = null
        try {
            val source = VideoFrames(file, channels).also { frames = it }
            val dur = source.durationMs() ?: return@coroutineScope FrameRun(0, 0, 0, 0, "durata non leggibile")
            var ok = 0; var bad = 0; var dup = 0; var extra = 0
            val plan = FramePlan.times(dur, deep)
            val withFaces = ArrayList<Long>()
            var lastHash: Long? = null
            suspend fun sample() {
                for ((i, t) in plan.withIndex()) {
                    coroutineContext.ensureActive()
                    // The planned frames are most of the work; the extra ones near faces take the last tenth.
                    onProgress?.invoke(0.9f * i / plan.size)
                    // Deep: the exact frame of each moment, not the nearest key frame (often a cut or a blur).
                    val img = source.at(t, exact = deep)
                    if (img == null) { bad++; continue }
                    val h = FaceStats.frameHash(img)
                    val last = lastHash
                    if (last != null && FaceStats.sameFrame(h, last)) { dup++; continue }
                    lastHash = h
                    ok++
                    // Where the frame really is: a key frame is often well before the moment asked, and
                    // the moment kept is where the details jump to and where the frame is read again.
                    val real = source.lastMs ?: t
                    if (onFrame(i, real, img)) withFaces += real
                }
            }
            sample()
            // Not one frame from the app's decoder, though it opened: the retriever reads them all again.
            if (ok == 0 && dup == 0 && source.fallBack()) { bad = 0; sample() }
            // Frame bits stay below 64: the plan takes at most 40, the extra ones at most MAX_EXTRA.
            for ((k, t) in withFaces.take(MAX_EXTRA).withIndex()) {
                coroutineContext.ensureActive()
                onProgress?.invoke(0.9f + 0.1f * k / minOf(withFaces.size, MAX_EXTRA))
                val at = t + REFINE_MS
                if (at >= dur) continue
                val img = source.at(at, exact = true) ?: continue
                extra++; ok++
                onFrame(plan.size + k, at, img)
            }
            FrameRun(ok, bad, dup, extra, null)
        } finally {
            closer.cancel()
            frames?.close()
            channels.forEach { runCatching { it.close() } }
        }
    }

    /** A photo decoded already turned upright (EXIF) and downsampled. */
    private suspend fun photo(file: FileEntity, side: Int = FRAME_SIDE): FaceImage? {
        val bytes = repo.decryptBytes(file)
        val bmp = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { dec, info, _ ->
                val k = minOf(1.0, side.toDouble() / maxOf(info.size.width, info.size.height))
                dec.setTargetSize(maxOf(1, (info.size.width * k).toInt()), maxOf(1, (info.size.height * k).toInt()))
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull() ?: BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
            var s = 1
            while (maxOf(b.outWidth, b.outHeight) / (s * 2) >= side) s *= 2
            inSampleSize = s
        }) ?: return null
        return faceImage(bmp, side)
    }

    private fun faceImage(src: Bitmap, side: Int): FaceImage {
        val k = minOf(1.0, side.toDouble() / maxOf(src.width, src.height))
        val bmp = if (k < 1.0) Bitmap.createScaledBitmap(src, maxOf(1, (src.width * k).toInt()), maxOf(1, (src.height * k).toInt()), true) else src
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val img = FaceImage(bmp.width, bmp.height, px)
        if (bmp !== src) bmp.recycle()
        src.recycle()
        return img
    }

    private fun boxText(box: FloatArray?) = box?.joinToString(",") { "%.4f".format(java.util.Locale.ROOT, it) }.orEmpty()

    private fun jpeg(face: FaceImage): ByteArray {
        val bmp = Bitmap.createBitmap(face.argb, face.width, face.height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it); bmp.recycle(); it.toByteArray() }
    }

    // ---- New faces --------------------------------------------------------------------------

    private fun info(r: TrackRow) = TrackInfo(r.id, r.fileId, r.frames, EmbeddingCodec.decode(r.embeddings), r.quality)

    /**
     * Mark the media analysed since last time as done. People are made by hand only: new faces wait,
     * unassigned, for "Chi è?"; confirmed faces of media analysed again stay with their person (or
     * wait unassigned when the person is gone). Returns how many media they were.
     */
    suspend fun placeNew(): Int = mutex.withLock {
        withContext(Dispatchers.Default) {
            val ids = dao.unclusteredIds()
            if (ids.isEmpty()) return@withContext 0
            val people = dao.persons().map { it.id }.toSet()
            val carried = ids.chunked(500).flatMap { dao.trackRowsOf(it) }.filter { it.state == "carried" }
            db.withTransaction {
                for (r in carried) {
                    val pid = r.personId?.takeIf { it in people }
                    dao.place(r.id, pid, if (pid != null) "manual" else "auto", r.similarity)
                }
                ids.chunked(500).forEach { dao.markClustered(it) }
            }
            ids.size
        }
    }

    /** Move everything of [drop] into [keep], remembering it for "Annulla". Returns the merge's id. */
    private suspend fun mergeRows(keep: Long, drop: Long, score: Float, auto: Boolean): Long {
        val p = dao.persons().firstOrNull { it.id == drop }
        val tracks = dao.cardsOfPerson(drop).map { it.id }
        val links = dao.linksOfPerson(drop)
        val logId = dao.addMerge(FaceMergeEntity(keptId = keep, mergedId = drop, mergedName = p?.name.orEmpty(),
            mergedFavorite = p?.favorite == true, trackIds = tracks.joinToString(","), fileIds = links.joinToString(","),
            similarity = score, auto = auto, createdAt = System.currentTimeMillis()))
        dao.moveTracks(drop, keep)
        dao.movePersonMedia(drop, keep)
        dao.deletePersonMedia(drop)
        dao.moveRejections(drop, keep)
        dao.deleteRejectionsOf(drop)
        // The merged person's notes and links go to the one kept: deleted with it, they were lost
        // for good (an undo brings back its name, not its profile).
        val kept = dao.persons().firstOrNull { it.id == keep }
        if (p != null && kept != null && (p.notes.isNotBlank() || p.links.isNotBlank())) {
            val notes = listOf(kept.notes, p.notes).filter { it.isNotBlank() }.distinct().joinToString("\n\n")
            val links = (kept.links.lines() + p.links.lines()).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
            dao.setProfile(keep, notes, links)
        }
        dao.deletePerson(drop)
        if (p?.favorite == true) dao.setFavorite(keep, true)
        return logId
    }

    // ---- What the screens show -------------------------------------------------------------

    /** A person for the lists: their media (live only), the face shown and the suggestions waiting. */
    class Person(val entity: PersonEntity, val media: List<FileEntity>, val cover: TrackCard?, val faces: List<TrackCard>) {
        val id get() = entity.id
        val title get() = entity.name.ifBlank { UNNAMED }
        /** Shown in the main lists: named, starred, or seen in two media at least. */
        val main get() = !entity.ignored && (entity.name.isNotBlank() || entity.favorite || media.size >= 2)
    }

    /** [unassigned]: faces nobody was given yet (the "Da sistemare" count). */
    class Overview(val scans: List<FaceScanEntity>, val media: Int, val people: List<Person>, val files: Map<String, FileEntity>,
                   val unassigned: Int = 0)

    private fun <T> live(block: (com.cripta.app.data.db.FaceDao) -> Flow<T>): Flow<T> =
        session.database.flatMapLatest { d -> if (d == null) emptyFlow() else block(d.faceDao()) }

    /**
     * Everyone (starred first, then most media; unnamed people seen once at the end). One shared,
     * debounced computation for every screen: during a scan the tables change many times a second.
     */
    val overview: Flow<Overview> = combine(
        live { it.observePersons() }, live { it.observeCards() }, live { it.observePersonMedia() }, live { it.observeScans() },
        repo.allFiles(),
    ) { persons, cards, links, scans, files -> Inputs(persons, cards, links, scans, files.map { it.file }) }
        // At most one rebuild every 400 ms, the latest state always last. Not a debounce: while a
        // scan writes for minutes a debounce never fires, and every list kept showing the old faces.
        .conflate()
        .transform { emit(it); delay(400) }
        .map { build(it) }
        .flowOn(Dispatchers.Default)
        .shareIn(scope, SharingStarted.WhileSubscribed(10_000), replay = 1)

    private class Inputs(val persons: List<PersonEntity>, val cards: List<TrackCard>, val links: List<PersonMediaEntity>,
                         val scans: List<FaceScanEntity>, val files: List<FileEntity>)

    private fun build(i: Inputs): Overview {
        val byId = i.files.associateBy { it.id }
        val faces = i.cards.filter { it.fileId in byId }.groupBy { it.personId }
        val linksBy = i.links.groupBy { it.personId }
        val people = i.persons.map { p ->
            val own = faces[p.id].orEmpty()
            val placed = own.filter { it.state != "suggested" }
            val mediaIds = LinkedHashSet<String>().apply {
                placed.sortedByDescending { it.width }.forEach { add(it.fileId) }
                linksBy[p.id].orEmpty().forEach { add(it.fileId) }
            }
            Person(p, mediaIds.mapNotNull { byId[it] }, placed.firstOrNull { it.id == p.coverTrackId } ?: placed.maxByOrNull { it.width },
                placed)
        }.filter { it.media.isNotEmpty() || it.entity.name.isNotBlank() || it.entity.favorite }
            .sortedWith(compareBy<Person>({ it.entity.ignored }, { !it.entity.favorite }, { !it.main }, { -it.media.size }, { it.title.lowercase() }))
        val media = i.files.count { VaultRepository.isVideo(it.mimeType) || VaultRepository.isImage(it.mimeType) }
        return Overview(i.scans.filter { it.fileId in byId }, media, people, byId, faces[null].orEmpty().size)
    }

    fun people(): Flow<Overview> = overview

    /** For each media, the faces (cover track ids) of the people in it, starred people first: drawn on its cover. */
    val coverFaces: Flow<Map<String, List<Long>>> = overview.map { o ->
        val m = HashMap<String, MutableList<Long>>()
        for (p in o.people) {
            if (p.entity.ignored) continue
            val face = p.cover?.id ?: continue
            for (f in p.media) m.getOrPut(f.id) { ArrayList() } += face
        }
        m as Map<String, List<Long>>
    }.flowOn(Dispatchers.Default)

    /** The faces found in [fileId], live. */
    fun facesOf(fileId: String): Flow<List<TrackCard>> = live { it.observeCardsOf(fileId) }

    /** People linked to [fileId] by hand. */
    fun linksOf(fileId: String): Flow<List<Long>> = live { it.observePersonMedia() }.map { l -> l.filter { it.fileId == fileId }.map { it.personId } }


    suspend fun crop(trackId: Long): Bitmap? {
        val d = db
        synchronized(crops) { if (cropsOf !== d) { crops.evictAll(); cropsOf = d } }
        crops.get(trackId)?.let { return it }
        return withContext(Dispatchers.IO) {
            dao.crop(trackId)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.also { crops.put(trackId, it) }
        }
    }

    /** Media showing all of [personIds] (for the vault filter). */
    suspend fun mediaWithAll(personIds: Collection<Long>): Set<String> {
        var out: Set<String>? = null
        for (id in personIds) {
            val m = dao.mediaOf(id).toSet()
            out = out?.intersect(m) ?: m
        }
        return out.orEmpty()
    }

    /** Media of the people whose name contains [query] (the vault search). */
    suspend fun mediaOfName(query: String): Set<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptySet()
        return dao.persons().filter { !it.ignored && it.name.contains(q, ignoreCase = true) }.flatMap { dao.mediaOf(it.id) }.toSet()
    }

    // ---- The user's changes ----------------------------------------------------------------

    private suspend fun <T> change(block: suspend () -> T): T = mutex.withLock {
        db.withTransaction { block() }
    }

    /**
     * A name does not confirm the faces: a wrong one would stay with the person for good. Only the
     * faces the user confirms one by one do.
     */
    suspend fun rename(personId: Long, name: String) = change { dao.rename(personId, name.trim()) }

    suspend fun setFavorite(personId: Long, favorite: Boolean) = change { dao.setFavorite(personId, favorite) }

    /** A person left with no media: clearing its name and star lets the usual cleanup remove it. */
    suspend fun deleteEmpty(personId: Long) = change {
        dao.rename(personId, ""); dao.setFavorite(personId, false); dao.deleteEmptyPersons()
    }

    /** "Unisci": everything goes to [into]; each merge can be undone. */
    suspend fun merge(into: Long, others: Collection<Long>): Undo {
        val ids = change { others.filter { it != into }.map { mergeRows(into, it, 0f, auto = false) } }
        return { ids.reversed().forEach { undoMerge(it) } }
    }

    /**
     * Take a merge back: the merged person returns with its name, the faces and media it brought
     * (those still with the other one), and the two are kept apart from then on.
     */
    suspend fun undoMerge(mergeId: Long) = change {
        val m = dao.mergeById(mergeId) ?: return@change
        dao.putPerson(PersonEntity(id = m.mergedId, name = m.mergedName, favorite = m.mergedFavorite, createdAt = m.createdAt))
        val tracks = m.trackIds.split(',').mapNotNull { it.toLongOrNull() }
        tracks.chunked(500).forEach { dao.moveTrackIds(it, m.keptId, m.mergedId) }
        for (f in m.fileIds.split(',').filter { it.isNotBlank() }) {
            dao.removePersonMedia(m.keptId, f); dao.addPersonMedia(PersonMediaEntity(m.mergedId, f))
        }
        for (t in tracks) dao.addRejection(FaceRejectionEntity(t, m.keptId, m.similarity))
        dao.deleteMerge(mergeId)
    }

    /** "Non sono la stessa persona" for two people offered as alike. */
    suspend fun keepApart(a: Long, b: Long) = change {
        for (t in dao.cardsOfPerson(b)) dao.addRejection(FaceRejectionEntity(t.id, a, 0f))
    }

    /** "Non è una persona": hidden, and similar new faces go with it. */
    suspend fun ignore(personId: Long): Undo {
        change { dao.setIgnored(personId, true); dao.lockTracks(personId) }
        return { restore(personId) }
    }

    suspend fun restore(personId: Long) = change { dao.setIgnored(personId, false) }

    /** The track [trackId] is not [personId]: remembered, and the face is placed again elsewhere. */
    suspend fun reject(trackId: Long, personId: Long): Undo {
        val before = dao.card(trackId)
        change {
            dao.addRejection(FaceRejectionEntity(trackId, personId, before?.similarity ?: 0f))
            dao.place(trackId, null, "auto", 0f)
        }
        replace(listOf(trackId))
        return {
            change {
                dao.deleteRejection(trackId, personId)
                if (before != null) dao.place(trackId, before.personId, before.state, before.similarity)
                dao.deleteEmptyPersons()
            }
        }
    }

    /** "Togli da questa persona": the faces of [fileIds] with [personId] and the manual links go. */
    suspend fun removeMedia(personId: Long, fileIds: Collection<String>): Undo {
        val before = fileIds.flatMap { f -> dao.cardsOf(f).filter { it.personId == personId } }
        val links = dao.linksOfPerson(personId).filter { it in fileIds }
        change {
            for (f in links) dao.removePersonMedia(personId, f)
            for (t in before) { dao.addRejection(FaceRejectionEntity(t.id, personId, t.similarity)); dao.place(t.id, null, "auto", 0f) }
        }
        replace(before.map { it.id })
        change { dao.deleteEmptyPersons() }
        return {
            change {
                for (t in before) { dao.deleteRejection(t.id, personId); dao.place(t.id, personId, t.state, t.similarity) }
                for (f in links) dao.addPersonMedia(PersonMediaEntity(personId, f))
                dao.deleteEmptyPersons()
            }
        }
    }

    /**
     * "Dividi" / "Sposta a…": the faces of [fileIds] with [from] (and its manual links) go to [to],
     * or to a new person named [newName]. Returns the person they went to.
     */
    suspend fun moveMedia(from: Long, fileIds: Collection<String>, to: Long?, newName: String = ""): Long = change {
        val target = to ?: dao.addPerson(PersonEntity(name = newName.trim(), createdAt = System.currentTimeMillis()))
        for (f in fileIds) {
            for (t in dao.cardsOf(f).filter { it.personId == from }) {
                dao.addRejection(FaceRejectionEntity(t.id, from, t.similarity))
                dao.confirm(t.id, target)
            }
            if (f in dao.linksOfPerson(from)) { dao.removePersonMedia(from, f); dao.addPersonMedia(PersonMediaEntity(target, f)) }
        }
        dao.deleteEmptyPersons()
        target
    }

    /** "È lui/lei": the face goes to [personId] for good (a confirmed suggestion, or picked by hand). */
    suspend fun assign(trackId: Long, personId: Long): Undo {
        val before = dao.card(trackId)
        change { dao.confirm(trackId, personId); dao.deleteEmptyPersons() }
        return { change { if (before != null) dao.place(trackId, before.personId, before.state, before.similarity); dao.deleteEmptyPersons() } }
    }

    /** A face the user can add by hand: shown even when the analysis would set it aside ([why]). */
    class FoundFace(val face: FaceEngine.Face, val picture: Bitmap, val timeMs: Long) {
        val why get() = face.reject?.label
    }

    /**
     * Every face in [file] at [timeMs] (a photo: the photo), found on a larger picture than the
     * analysis uses and none set aside: the user picks the one to add.
     */
    suspend fun facesAt(file: FileEntity, timeMs: Long): List<FoundFace> = withContext(Dispatchers.Default) {
        val img = if (VaultRepository.isVideo(file.mimeType)) withFrames(file) { it.at(timeMs, exact = true, side = 2 * FRAME_SIDE) }
            else photo(file, 2 * FRAME_SIDE)
        img ?: return@withContext emptyList()
        found(img, timeMs)
    }

    private fun found(img: FaceImage, timeMs: Long): List<FoundFace> =
        withEngine { it.analyze(img, all = true) }.filter { it.embedding != null }.sortedByDescending { it.det.w }.map { f ->
            val p = f.preview!!
            FoundFace(f, Bitmap.createBitmap(p.argb, p.width, p.height, Bitmap.Config.ARGB_8888), timeMs)
        }

    /**
     * "Aggiungi un volto": the faces at [timeMs]; in a video with none there, those of the first
     * moment with a face in the next seconds ([AHEAD_MS], each [FoundFace.timeMs] says where).
     */
    suspend fun facesFrom(file: FileEntity, timeMs: Long): List<FoundFace> = withContext(Dispatchers.Default) {
        if (!VaultRepository.isVideo(file.mimeType)) return@withContext facesAt(file, timeMs)
        // One reader for every moment: half a second apart, each frame is decoded on from the last.
        withFrames(file) { source ->
            val dur = source.durationMs()
            for (dt in AHEAD_MS) {
                coroutineContext.ensureActive()
                val t = timeMs + dt
                if (dur != null && t >= dur) break
                val img = runCatching { source.at(t, exact = true, side = 2 * FRAME_SIDE) }.getOrNull() ?: continue
                val list = found(img, t)
                if (list.isNotEmpty()) return@withFrames list
            }
            emptyList()
        }
    }

    /**
     * [block] with a reader of [file]'s frames ([VideoFrames]: the app's decoder, the retriever
     * when it cannot read the video), closed after. The analysis' reader, milliseconds a frame:
     * the retriever alone took seconds for each face looked up in the details.
     */
    private suspend fun <T> withFrames(file: FileEntity, block: suspend (VideoFrames) -> T): T {
        val channels = java.util.concurrent.CopyOnWriteArrayList<java.nio.channels.SeekableByteChannel>()
        var source: VideoFrames? = null
        try {
            return block(VideoFrames(file, channels).also { source = it })
        } finally {
            source?.close()
            channels.forEach { runCatching { it.close() } }
        }
    }

    /**
     * [f] added by hand to [personId]: a confirmed face of its own (no sampled frame, so a new
     * analysis keeps it) that also teaches the person a new look.
     */
    suspend fun addFace(file: FileEntity, f: FoundFace, personId: Long) = change {
        val face = f.face
        val id = dao.addTrack(FaceTrackEntity(fileId = file.id, frames = 0L, timeMs = f.timeMs, faceCount = 1, width = face.det.w.toInt(),
            quality = face.quality, embeddings = EmbeddingCodec.encode(listOf(face.embedding!!)), crop = jpeg(face.preview!!),
            times = f.timeMs.toString(), box = boxText(face.box)))
        dao.confirm(id, personId)
        dao.removePersonMedia(personId, file.id)
    }

    /** A media added to [personId] without one of its faces. */
    suspend fun link(personId: Long, fileId: String) = change { dao.addPersonMedia(PersonMediaEntity(personId, fileId)) }

    /** "Togli": the media [fileId] added to [personId] by hand stops being theirs. */
    suspend fun unlink(personId: Long, fileId: String): Undo {
        change { dao.removePersonMedia(personId, fileId); dao.deleteEmptyPersons() }
        return { link(personId, fileId) }
    }

    /** A new named person, for "Chi è? › Nuova persona". */
    suspend fun newPerson(name: String): Long = change {
        // No name given: "Volto N", the next number free (the user can rename them later).
        val named = name.trim().ifBlank {
            val used = dao.persons().mapNotNull { VOLTO.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }
            "Volto ${(used.maxOrNull() ?: 0) + 1}"
        }
        dao.addPerson(PersonEntity(name = named, createdAt = System.currentTimeMillis()))
    }

    suspend fun setCover(personId: Long, trackId: Long) = change { dao.setCover(personId, trackId) }

    /** The profile's notes and links (one per line). */
    suspend fun setProfile(personId: Long, notes: String, links: List<String>) =
        change { dao.setProfile(personId, notes, links.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")) }

    // ---- Suggestions ------------------------------------------------------------------------

    /** A face found elsewhere that may be [personId]'s: how alike, best first. Never placed by itself. */
    class Suggestion(val card: TrackCard, val similarity: Float)

    /**
     * Unassigned faces that look like [personId]. Every face the user gave them counts: a face is
     * compared with each of them and scored by its closest matches (the mean of the best three),
     * so the more media the user groups by hand, the surer the suggestions. Faces said "not them"
     * before, and faces in media where they already are, are left out.
     */
    suspend fun suggestionsFor(personId: Long, limit: Int = 300, minMedia: Int = 1): List<Suggestion> = withContext(Dispatchers.Default) {
        val rows = dao.allTrackMeta()
        val own = rows.filter { it.personId == personId && it.state != "suggested" }
        // Faces of one media count once: two of the same video are nearly the same example.
        if (own.isEmpty() || own.map { it.fileId }.distinct().size < minMedia) return@withContext emptyList()
        val ownMedia = own.map { it.fileId }.toSet() + dao.linksOfPerson(personId)
        val refused = dao.rejections().filter { it.personId == personId }.map { it.trackId }.toSet()
        val live = repo.allFilesSnapshot().filter { it.deletedAt == null }.map { it.id }.toSet()
        // Faces the closer look of their video did not find again ([ASIDE]) are hardly faces: never offered.
        val candidates = rows.filter { it.personId == null && it.state != ASIDE && it.id !in refused && it.fileId !in ownMedia && it.fileId in live }
        val emb = embeddings(own.map { it.id } + candidates.map { it.id })
        val ownEmb = own.mapNotNull { emb[it.id] }
        val scored = ArrayList<Pair<Long, Float>>()
        for (r in candidates) {
            coroutineContext.ensureActive()
            val score = score(ownEmb, emb[r.id] ?: continue)
            if (score >= SUGGEST) scored += r.id to score
        }
        val top = scored.sortedByDescending { it.second }.take(limit)
        val cards = dao.allCards().associateBy { it.id }
        top.mapNotNull { (id, sc) -> cards[id]?.let { Suggestion(it, sc) } }
    }

    /** A face of "Da confermare" and the known person it is most like. */
    class PersonSuggestion(val personId: Long, val suggestion: Suggestion)

    /**
     * "Da confermare" (recognition "Solo persone note"): the faces like someone with faces in at
     * least [minMedia] media, each offered once, to the person it is most like, most alike first.
     */
    suspend fun knownSuggestions(minMedia: Int = 2, limit: Int = 300): List<PersonSuggestion> = withContext(Dispatchers.Default) {
        val best = HashMap<Long, PersonSuggestion>()
        for (p in dao.persons().filter { !it.ignored }) {
            for (s in suggestionsFor(p.id, limit, minMedia)) {
                val cur = best[s.card.id]
                if (cur == null || s.similarity > cur.suggestion.similarity) best[s.card.id] = PersonSuggestion(p.id, s)
            }
        }
        best.values.sortedByDescending { it.suggestion.similarity }.take(limit)
    }

    /** How alike a face is to a person's faces: the mean of its three closest matches (or the closest, a little less). */
    private fun score(own: List<List<FloatArray>>, emb: List<FloatArray>): Float {
        val sims = own.map { o -> var best = -1f; for (x in o) for (y in emb) best = maxOf(best, similarity(x, y)); best }.sortedDescending()
        return sims.take(3).average().toFloat().let { mean -> maxOf(mean, sims.first() - 0.04f) }
    }

    /**
     * After "Chi è?" on [trackId]: the other unassigned faces of its media that look like its person
     * and share no frame with it, most alike first, for "Anche questi?". Never joined without a yes.
     */
    suspend fun alikeInMedia(trackId: Long): List<Suggestion> = withContext(Dispatchers.Default) {
        val rows = dao.allTrackMeta()
        val t = rows.firstOrNull { it.id == trackId } ?: return@withContext emptyList()
        val pid = t.personId ?: return@withContext emptyList()
        val refused = dao.rejections().filter { it.personId == pid }.map { it.trackId }.toSet()
        val candidates = rows.filter { it.fileId == t.fileId && it.personId == null && it.state != ASIDE && it.id !in refused &&
            it.frames != 0L && it.frames and t.frames == 0L }
        if (candidates.isEmpty()) return@withContext emptyList()
        val own = rows.filter { it.personId == pid && it.state != "suggested" }
        val emb = embeddings(own.map { it.id } + candidates.map { it.id })
        val ownEmb = own.mapNotNull { emb[it.id] }
        val cards = dao.cardsOf(t.fileId).associateBy { it.id }
        candidates.mapNotNull { r ->
            val s = score(ownEmb, emb[r.id] ?: return@mapNotNull null)
            if (s >= SUGGEST) cards[r.id]?.let { Suggestion(it, s) } else null
        }.sortedByDescending { it.similarity }
    }

    /** Unassigned faces of one person (as far as they look alike) in different media. */
    class FaceGroup(val cards: List<TrackCard>)

    /**
     * "Da sistemare": every unassigned face of the vault, grouped by likeness ([GROUP]) so that a
     * group can be named or deleted at once; biggest groups first, faces seen alone last.
     */
    suspend fun unsortedGroups(): List<FaceGroup> = withContext(Dispatchers.Default) {
        val live = repo.allFilesSnapshot().filter { it.deletedAt == null }.map { it.id }.toSet()
        val free = dao.allTrackMeta().filter { it.personId == null && it.fileId in live }.sortedByDescending { it.quality }
        val emb = embeddings(free.map { it.id })
        val kept = free.filter { emb[it.id] != null }
        val groups = FaceTracker.cluster(kept.map { emb.getValue(it.id).first() }, GROUP)
        val cards = dao.allCards().associateBy { it.id }
        // Faces the user said are not one person ("Dividi il gruppo") never share a group: each goes
        // to the first part of its group holding none of them, best faces first.
        val apart = HashMap<Long, MutableSet<Long>>()
        for (p in dao.apart()) { apart.getOrPut(p.a) { HashSet() } += p.b; apart.getOrPut(p.b) { HashSet() } += p.a }
        groups.flatMap { g ->
            val parts = ArrayList<MutableList<Long>>()
            for (id in g.sorted().map { kept[it].id }) {
                val home = parts.firstOrNull { part -> part.none { apart[id]?.contains(it) == true } }
                if (home != null) home += id else parts += mutableListOf(id)
            }
            parts.map { part -> FaceGroup(part.mapNotNull { cards[it] }) }
        }.filter { it.cards.isNotEmpty() }.sortedByDescending { it.cards.size }
    }

    /** "Dividi il gruppo": no face of [a] is the person of any face of [b]; never grouped together again. */
    suspend fun keepFacesApart(a: Collection<Long>, b: Collection<Long>): Undo {
        val pairs = a.flatMap { x -> b.filter { it != x }.map { y -> FaceApartEntity(minOf(x, y), maxOf(x, y)) } }.distinct()
        change { dao.addApart(pairs) }
        return { change { dao.deleteApart(pairs) } }
    }

    /** A group of faces given to [personId] at once. */
    suspend fun assignAll(trackIds: Collection<Long>, personId: Long): Undo {
        val before = trackIds.mapNotNull { dao.card(it) }
        change { for (t in before) dao.confirm(t.id, personId); dao.deleteEmptyPersons() }
        return { change { for (t in before) dao.place(t.id, t.personId, t.state, t.similarity); dao.deleteEmptyPersons() } }
    }

    // ---- Embeddings in memory ---------------------------------------------------------------

    private val embeddingCache = java.util.concurrent.ConcurrentHashMap<Long, List<FloatArray>>()
    @Volatile private var cacheDb: Any? = null

    init {
        // Face pictures, frames and embeddings leave memory with the lock (as the covers do), not
        // at the next unlock: nothing of the vault should stay readable in RAM behind the lock.
        scope.launch {
            session.locked.collect { locked ->
                if (!locked) return@collect
                synchronized(crops) { crops.evictAll(); cropsOf = null }
                synchronized(frames) { frames.evictAll(); framesOf = null }
                embeddingCache.clear(); cacheDb = null
            }
        }
    }

    /** The embeddings of [ids], decoded once per vault; a change to a track's embeddings calls [stale]. */
    private suspend fun embeddings(ids: Collection<Long>): Map<Long, List<FloatArray>> {
        val d = db
        if (cacheDb !== d) { embeddingCache.clear(); cacheDb = d }
        val missing = ids.filter { !embeddingCache.containsKey(it) }
        for (chunk in missing.chunked(500)) for (r in dao.embeddingsOf(chunk)) embeddingCache[r.id] = EmbeddingCodec.decode(r.embeddings)
        val out = HashMap<Long, List<FloatArray>>(ids.size)
        for (id in ids) embeddingCache[id]?.let { out[id] = it }
        return out
    }

    private fun stale(ids: Collection<Long>) { ids.forEach { embeddingCache.remove(it); crops.remove(it) } }

    /** The picture [card] was found in (a video's frame, or the photo), at most [side] px. */
    suspend fun frameOf(card: TrackCard, side: Int = 720): Bitmap? = withContext(Dispatchers.IO) {
        // Read once: reading the exact frame of an encrypted video takes a while, and the pages of
        // "Forse è …" / "Da sistemare" ask again when they come back into view.
        val key = "${card.id}:${card.timeMs}:$side"
        val d = db
        synchronized(frames) { if (framesOf !== d) { frames.evictAll(); framesOf = d } }
        frames.get(key)?.let { return@withContext it }
        val file = repo.fileById(card.fileId) ?: return@withContext null
        runCatching {
            if (VaultRepository.isVideo(file.mimeType)) {
                withFrames(file) { it.at(card.timeMs, exact = true, side = side) }
                    ?.let { Bitmap.createBitmap(it.argb, it.width, it.height, Bitmap.Config.ARGB_8888) }
            } else {
                val bytes = repo.decryptBytes(file)
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { dec, info, _ ->
                    val k = minOf(1.0, side.toDouble() / maxOf(info.size.width, info.size.height))
                    dec.setTargetSize(maxOf(1, (info.size.width * k).toInt()), maxOf(1, (info.size.height * k).toInt()))
                    dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }
        }.getOrNull()?.also { frames.put(key, it) }
    }

    // ---- A closer look at videos with a confirmed face --------------------------------------

    /** What [refine] did to one video. */
    class Refined(val joined: Int, val aside: Int, val frames: Int)

    /** The confirmed faces of a video, as the closer look records them ("track:person", sorted). */
    private fun signature(rows: List<TrackMeta>) =
        rows.filter { it.personId != null && it.state == "manual" }.map { "${it.id}:${it.personId}" }.sorted().joinToString(",")

    private fun meta(rows: List<FaceTrackEntity>) = rows.map { TrackMeta(it.id, it.fileId, it.frames, it.quality, it.personId, it.state) }

    /** A face of a video nobody was given that the analysis found: the faces the closer look judges. */
    private fun loose(personId: Long?, state: String, frames: Long) =
        frames != 0L && (personId == null || state == "suggested") && state != "manual" && state != "carried"

    /**
     * Live videos with a face the user confirmed whose other faces the closer look ([refine]) has
     * not judged as they are now: never looked at, or confirmed faces changed since.
     */
    suspend fun refineWanted(): List<String> = withContext(Dispatchers.Default) {
        val videos = repo.allFilesSnapshot().filter { it.deletedAt == null && VaultRepository.isVideo(it.mimeType) }.map { it.id }.toSet()
        val scans = dao.allScans().filter { it.status == "ok" && it.fileId in videos }.associateBy { it.fileId }
        dao.allTrackMeta().filter { it.fileId in scans }.groupBy { it.fileId }.filter { (f, rows) ->
            val sig = signature(rows)
            sig.isNotEmpty() && rows.any { loose(it.personId, it.state, it.frames) } &&
                FaceStats.refineOf(scans.getValue(f).stats)?.signature != sig
        }.keys.toList()
    }

    /** The videos among the media of [trackIds]: looked at closer once their faces are confirmed. */
    suspend fun videosOf(trackIds: Collection<Long>): List<String> =
        dao.tracks(trackIds.distinct()).map { it.fileId }.distinct()
            .filter { id -> liveFile(id)?.let { VaultRepository.isVideo(it.mimeType) } == true }

    /** [fileId] while it is in the vault (not in the trash). */
    suspend fun liveFile(fileId: String): FileEntity? = repo.fileById(fileId)?.takeIf { it.deletedAt == null }

    /** What the closer look did to [fileId], in words, live; null when it has not looked at it since it was analysed. */
    fun refineNote(fileId: String): Flow<String?> =
        live { it.observeScanOf(fileId) }.map { s -> s?.let { FaceStats.refineOf(it.stats) }?.let(::refineText) }

    /** [refineNote] once. */
    suspend fun refineNoteNow(fileId: String): String? = dao.scanOf(fileId)?.let { FaceStats.refineOf(it.stats) }?.let(::refineText)

    private fun refineText(r: FaceStats.Refine): String {
        if (r.frames == 0) return "Analizzato meglio: nessun altro volto da controllare."
        val parts = ArrayList<String>()
        if (r.joined == 1) parts += "1 volto era la persona confermata ed è stato unito al suo"
        else if (r.joined > 1) parts += "${r.joined} volti erano la persona confermata e sono stati uniti al suo"
        if (r.aside == 1) parts += "1 volto non è stato ritrovato ed è tra i poco chiari"
        else if (r.aside > 1) parts += "${r.aside} volti non sono stati ritrovati e sono tra i poco chiari"
        return "Analizzato meglio, ${r.frames} fotogrammi attorno a chi hai confermato: " +
            (if (parts.isEmpty()) "nessun altro volto è di sicuro la stessa persona." else parts.joinToString("; ") + ".") +
            (if (r.joined > 0) " Se un'unione è sbagliata, «Dividi» dal menu del volto." else "")
    }

    /**
     * "Analizza meglio": a closer look at [file], a video where the user confirmed someone. Its
     * frames are read about one a second, larger, and around every moment of the faces nobody was
     * given; the confirmed people are found in them by likeness to their own faces of this video
     * and followed by place ([FaceFollower]). A face nobody was given joins the confirmed one only
     * when every moment of it is that person at the same place: nothing is given to anyone, and
     * faces of other media are never touched. Faces the closer reading does not find again are set
     * aside ([ASIDE]: among "poco chiari", still in "Da sistemare"). [force]: look again though
     * nothing was confirmed since the last look. Null when there was nothing to look at.
     */
    suspend fun refine(file: FileEntity, force: Boolean = false, onProgress: ((Float) -> Unit)? = null): Refined? {
        if (!VaultRepository.isVideo(file.mimeType)) return null
        val scan = dao.scanOf(file.id)?.takeIf { it.status == "ok" } ?: return null
        val rows = dao.tracksOfFile(file.id)
        val sig = signature(meta(rows))
        if (sig.isEmpty() || !force && FaceStats.refineOf(scan.stats)?.signature == sig) return null
        val anchors = rows.filter { it.personId != null && it.state == "manual" }
        // What each confirmed person looks like in this very video: their faces here, nothing else.
        val people = anchors.groupBy { it.personId!! }.map { (pid, list) ->
            KnownHere(pid, list.flatMap { r -> EmbeddingCodec.decode(r.embeddings) + FaceCodec.decode(r.sightings).map { it.embedding } },
                list.fold(0L) { m, r -> m or r.frames })
        }
        // Faces analysed before they were kept one by one cannot be checked moment by moment: left as they are.
        val free = rows.filter { loose(it.personId, it.state, it.frames) }
            .map { LooseFace(it.id, it.frames, FaceCodec.decode(it.sightings)) }.filter { it.faces.isNotEmpty() }
        val seen = if (free.isEmpty()) emptyList() else readCloser(file, free, anchors, onProgress)
        val labels = FaceFollower.label(seen, people)
        val verdicts = free.associate { it.id to FaceFollower.judge(it, seen, labels, people) }
        return change {
            // Analysed again while it was read: nothing is applied.
            val now = dao.scanOf(file.id)
            if (now == null || now.scannedAt != scan.scannedAt) return@change null
            var joined = 0; var aside = 0
            for (p in people) {
                // Faces added by hand (no frame) are not joined into: a new analysis keeps them as they are.
                val mine = dao.tracksOfFile(file.id).filter { it.personId == p.personId && it.state == "manual" && it.frames != 0L &&
                    FaceCodec.decode(it.sightings).isNotEmpty() }.sortedByDescending { it.faceCount }
                var target = mine.firstOrNull() ?: continue
                // The same person confirmed twice in this video, never in one frame: one face.
                for (o in mine.drop(1)) if (o.frames and target.frames == 0L) target = dao.tracks(listOf(joinRows(listOf(target, o)))).first()
                for ((id, v) in verdicts) {
                    if (v != FaceFollower.Verdict.Join(p.personId)) continue
                    // Changed meanwhile (given to someone, deleted, joined): left alone. Never two faces of one frame.
                    val row = dao.tracks(listOf(id)).firstOrNull() ?: continue
                    if (!loose(row.personId, row.state, row.frames) || row.frames and target.frames != 0L) continue
                    target = dao.tracks(listOf(joinRows(listOf(target, row)))).first()
                    joined++
                }
            }
            for ((id, v) in verdicts) {
                if (v != FaceFollower.Verdict.Aside) continue
                val row = dao.tracks(listOf(id)).firstOrNull() ?: continue
                if (!loose(row.personId, row.state, row.frames)) continue
                dao.place(id, null, ASIDE, 0f)
                aside++
            }
            val after = signature(meta(dao.tracksOfFile(file.id)))
            dao.setScanStats(file.id, FaceStats.withRefine(now.stats, FaceStats.Refine(after, joined, aside, seen.size)))
            Refined(joined, aside, seen.size)
        }
    }

    /**
     * The frames of the closer look ([FaceFollower.plan]), larger than the analysis reads them,
     * every face found kept with its place. Each moment of the faces to judge is read twice: its
     * key frame (as the analysis read it, the moment asked standing for it) and its exact frame.
     */
    private suspend fun readCloser(file: FileEntity, free: List<LooseFace>, anchors: List<FaceTrackEntity>, onProgress: ((Float) -> Unit)?): List<SeenFrame> = coroutineScope {
        val channels = java.util.concurrent.CopyOnWriteArrayList<java.nio.channels.SeekableByteChannel>()
        // Annulla while a frame is being read: the channels close at once (as in [videoFrames]).
        val closer = launch { try { awaitCancellation() } finally { channels.forEach { runCatching { it.close() } } } }
        var frames: VideoFrames? = null
        try {
            val source = VideoFrames(file, channels).also { frames = it }
            val dur = source.durationMs() ?: return@coroutineScope emptyList()
            val judged = free.flatMap { f -> f.faces.map { it.timeMs } }.toSet()
            val plan = FaceFollower.plan(dur, judged + anchors.flatMap { a -> FaceCodec.decode(a.sightings).map { it.timeMs } })
            val reads = plan.flatMap { t -> if (t in judged) listOf(t to false, t to true) else listOf(t to true) }
            val out = ArrayList<SeenFrame>()
            withEngine { engine ->
                for ((i, read) in reads.withIndex()) {
                    coroutineContext.ensureActive()
                    onProgress?.invoke(i.toFloat() / reads.size)
                    val (t, exact) = read
                    val img = source.at(t, exact, CLOSE_SIDE) ?: continue
                    val at = source.lastMs
                    val faces = engine.analyze(img, all = true).mapNotNull { f -> f.box?.let { SeenFace(it, f.embedding, f.reject == null) } }
                    out += SeenFrame(t, at, faces)
                }
            }
            out
        } finally {
            closer.cancel()
            frames?.close()
            channels.forEach { runCatching { it.close() } }
        }
    }

    // ---- One person split in two, faces that are not faces ----------------------------------

    /**
     * Join [rows] (faces of one media) into one: the face placed with a person, else the best one,
     * keeps its id, picture and person and takes the others' frames, moments and embeddings; their
     * rejections and covers move to it. Returns its id.
     */
    private suspend fun joinRows(rows: List<FaceTrackEntity>): Long {
        val keep = rows.firstOrNull { it.personId != null && it.state != "suggested" } ?: rows.maxBy { it.quality }
        val drop = rows.map { it.id }.filter { it != keep.id }
        val times = rows.flatMap { r -> r.times.split(',').mapNotNull { it.toLongOrNull() }.ifEmpty { listOf(r.timeMs) } }.distinct().sorted()
        val embeddings = FaceTracker.combine(rows.map { FaceTracker.Part(EmbeddingCodec.decode(it.embeddings), it.faceCount, it.quality) })
        // The faces one by one only when every part has them (older scans do not): else no "Dividi".
        // A face added by hand is one face: it gets its own (frame -1, no sampled frame), so joining
        // it to a face of the analysis leaves "Dividi" to take it apart again.
        val faces = rows.map { r -> FaceCodec.decode(r.sightings).ifEmpty { if (r.frames == 0L) handFace(r) else emptyList() } }
        val sightings = if (faces.all { it.isNotEmpty() }) FaceCodec.encode(faces.flatten()) else ByteArray(0)
        dao.joinedTrack(keep.id, rows.fold(0L) { m, r -> m or r.frames }, rows.sumOf { it.faceCount },
            EmbeddingCodec.encode(embeddings), times.joinToString(","), sightings)
        stale(listOf(keep.id))
        dao.moveTrackRejections(drop, keep.id)
        dao.deleteRejectionsOfTracks(drop)
        for (d in drop) dao.moveCover(d, keep.id)
        dao.deleteTracks(drop)
        return keep.id
    }

    /** A face added by hand ([addFace]) as [joinRows] keeps faces one by one. */
    private fun handFace(r: FaceTrackEntity): List<StoredFace> {
        val box = r.box.split(',').mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 4 }?.toFloatArray() ?: FloatArray(4)
        val e = EmbeddingCodec.decode(r.embeddings).firstOrNull() ?: return emptyList()
        return listOf(StoredFace(-1, r.timeMs, r.quality, r.width, box, e))
    }

    /** The unassigned faces the analysis found in [rows] (one media) that are one person, joined. Returns how many went. */
    private suspend fun tidyRows(rows: List<FaceTrackEntity>): Int {
        val free = rows.filter { it.personId == null && it.frames != 0L && it.state != "manual" && it.state != "carried" }
        if (free.size < 2) return 0
        val groups = FaceTracker.groups(free.map { it.frames }, free.map { EmbeddingCodec.decode(it.embeddings).first() }, free.map { it.faceCount })
        var n = 0
        for (g in groups) if (g.size > 1) { joinRows(g.map { free[it] }); n += g.size - 1 }
        return n
    }

    /**
     * Media analysed before the tracks of one person were joined ([TIDY_FROM]): their unassigned
     * faces of one person are joined from what is stored, without reading the media again, and the
     * media count as analysed by this version. Returns how many faces went.
     */
    suspend fun tidyOld(): Int {
        var n = 0
        for (chunk in dao.scansOfVersion(TIDY_FROM).map { it.fileId }.chunked(50)) n += change {
            var k = 0
            for (f in chunk) k += tidyRows(dao.tracksOfFile(f))
            dao.setScanVersion(chunk, VERSION)
            k
        }
        return n
    }

    /** [tidyOld] for one media (its details are open). */
    suspend fun tidy(fileId: String) {
        if (dao.scanOf(fileId)?.version != TIDY_FROM) return
        change { tidyRows(dao.tracksOfFile(fileId)); dao.setScanVersion(listOf(fileId), VERSION) }
    }

    /**
     * "Stessa persona": faces of one media that are one person become one, with all their moments.
     * Null when they are not of one media or belong to two different people.
     */
    suspend fun mergeFaces(trackIds: Collection<Long>): Undo? {
        val before = dao.tracks(trackIds.distinct())
        // Joined already meanwhile (the closer look at the video, see [refine]): nothing left to do,
        // and not "persone diverse" as a null would say.
        if (before.size < 2) return if (before.isNotEmpty()) ({}) else null
        if (before.map { it.fileId }.distinct().size != 1) return null
        if (before.filter { it.personId != null && it.state != "suggested" }.map { it.personId }.distinct().size > 1) return null
        val ids = before.map { it.id }
        val rejections = dao.rejectionsOfTracks(ids)
        val covers = dao.persons().filter { it.coverTrackId in ids }.map { it.id to it.coverTrackId }
        val keep = change { joinRows(before) }
        return {
            change {
                dao.deleteRejectionsOfTracks(listOf(keep))
                before.forEach { dao.putTrack(it) }
                rejections.forEach { dao.addRejection(it) }
                covers.forEach { (p, t) -> dao.setCover(p, t) }
            }
            stale(ids)
        }
    }

    /**
     * "Elimina": faces found by mistake go, with their person's choices about them, and are not
     * found again in their media; [everywhere] ("Non è un volto"): in no media.
     */
    suspend fun deleteFaces(trackIds: Collection<Long>, everywhere: Boolean = false): Undo {
        val ids = trackIds.distinct()
        val before = dao.tracks(ids)
        val rejections = dao.rejectionsOfTracks(ids)
        val all = dao.persons()
        val covers = all.filter { it.coverTrackId in ids }.map { it.id to it.coverTrackId }
        val people = all.filter { p -> before.any { it.personId == p.id } }
        val now = System.currentTimeMillis()
        // "Elimina" on a second copy of a face that stays in the media (same person, alike) is a
        // duplicate going, not a mistake of the analysis: nothing to remember against new analyses.
        val others = if (everywhere) emptyList() else dao.trackRowsOf(before.map { it.fileId }.distinct()).filter { it.id !in ids }
        val remember = before.filter { t ->
            everywhere || others.none { o -> o.fileId == t.fileId &&
                maxPair(EmbeddingCodec.decode(t.embeddings), EmbeddingCodec.decode(o.embeddings)) >= DELETED }
        }
        val remembered = change {
            dao.deleteRejectionsOfTracks(ids)
            dao.deleteTracks(ids)
            for ((p, _) in covers) dao.setCover(p, null)
            dao.deleteEmptyPersons()
            remember.map { dao.addIgnored(IgnoredFaceEntity(fileId = if (everywhere) null else it.fileId, embeddings = it.embeddings, createdAt = now)) }
        }
        return {
            change {
                dao.deleteIgnored(remembered)
                // A person left with nothing went with the face: back first, with their id.
                val now = dao.persons().map { it.id }.toSet()
                people.filter { it.id !in now }.forEach { dao.putPerson(it) }
                before.forEach { dao.putTrack(it) }
                rejections.forEach { dao.addRejection(it) }
                covers.forEach { (p, t) -> dao.setCover(p, t) }
            }
        }
    }

    /** The moments of [trackId] that "Dividi" can move (none for faces analysed before faces were kept one by one). */
    suspend fun splittable(trackId: Long): List<Long> =
        dao.tracks(listOf(trackId)).firstOrNull()?.let { r -> FaceCodec.decode(r.sightings).map { it.timeMs }.distinct().sorted() }.orEmpty()

    /**
     * "Dividi": the faces of [trackId] at [moments] are someone else: they become a face of their
     * own, unassigned, and the rest stays with its person. Null when nothing (or everything) would move.
     */
    suspend fun splitFace(trackId: Long, moments: Set<Long>): Undo? {
        val row = dao.tracks(listOf(trackId)).firstOrNull() ?: return null
        val (moving, staying) = FaceCodec.decode(row.sightings).partition { it.timeMs in moments }
        if (moving.isEmpty() || staying.isEmpty()) return null
        val file = repo.fileById(row.fileId) ?: return null
        val out = draft(moving); val rest = draft(staying)
        val outBest = out.best.first().payload as StoredFace
        val restBest = rest.best.first().payload as StoredFace
        // The face shown stays when its moment stays; else both parts get their own, found again on the frame.
        val restPic = if (staying.any { it.timeMs == row.timeMs }) null else picture(file, restBest)
        val outPic = picture(file, outBest)
        val newId = change {
            dao.putTrack(row.copy(frames = rest.frames, faceCount = rest.sightings.size, embeddings = EmbeddingCodec.encode(rest.embeddings),
                times = rest.times.joinToString(","), sightings = FaceCodec.encode(staying), quality = rest.quality,
                timeMs = if (restPic != null) restBest.timeMs else row.timeMs, crop = restPic?.crop ?: row.crop,
                box = restPic?.box ?: row.box, width = restPic?.width ?: row.width))
            dao.addTrack(FaceTrackEntity(fileId = row.fileId, frames = out.frames, timeMs = outBest.timeMs, faceCount = out.sightings.size,
                width = outPic?.width ?: outBest.width, quality = out.quality, embeddings = EmbeddingCodec.encode(out.embeddings),
                crop = outPic?.crop ?: row.crop, times = out.times.joinToString(","), box = outPic?.box ?: boxText(outBest.box),
                sightings = FaceCodec.encode(moving)))
        }
        stale(listOf(trackId))
        return { change { dao.deleteTracks(listOf(newId)); dao.putTrack(row) }; stale(listOf(trackId)) }
    }

    private fun draft(faces: List<StoredFace>) = TrackDraft(faces.map { Sighting(it.frame, it.timeMs, it.embedding, it.quality, it) })

    private class Picture(val crop: ByteArray, val box: String, val width: Int)

    /** [f]'s face found again on its frame (the nearest to where it was): its picture. */
    private suspend fun picture(file: FileEntity, f: StoredFace): Picture? {
        val cx = f.box[0] + f.box[2] / 2; val cy = f.box[1] + f.box[3] / 2
        val near = runCatching { facesAt(file, f.timeMs) }.getOrNull().orEmpty().mapNotNull { ff ->
            ff.face.box?.let { b -> ff to (b[0] + b[2] / 2 - cx).let { it * it } + (b[1] + b[3] / 2 - cy).let { it * it } }
        }.minByOrNull { it.second }?.first ?: return null
        return Picture(jpeg(near.face.preview!!), boxText(near.face.box), near.face.det.w.toInt())
    }

    /** How many faces the user deleted (left out of new analyses), live. */
    fun ignoredCount(): Flow<Int> = live { it.observeIgnoredCount() }

    /** Forget the deleted faces: a new analysis may find them again. */
    suspend fun forgetIgnored() = change { dao.deleteAllIgnored() }

    // ---- Clearing faces found by mistake ----------------------------------------------------

    /** What [clearDetected] would remove: media and faces analysed since [since] that nobody confirmed. */
    class Detected(val media: List<String>, val faces: Int)

    suspend fun detectedSince(since: Long): Detected = withContext(Dispatchers.Default) {
        val scans = dao.allScans().filter { it.scannedAt >= since && it.status == "ok" }.map { it.fileId }.toSet()
        val faces = dao.allCards().filter { it.fileId in scans && it.personId == null && it.state != "manual" && it.state != "carried" }
        Detected(faces.map { it.fileId }.distinct(), faces.size)
    }

    /**
     * Forget the faces found in [fileIds] that nobody confirmed (the analysis was on by mistake).
     * Faces confirmed or added by hand, people and their names stay. A media left with no face is
     * kept as analysed, so a new analysis does not find them again; "Rianalizza" on the media does.
     */
    suspend fun clearDetected(fileIds: Collection<String>): Int = change {
        var n = 0
        for (chunk in fileIds.distinct().chunked(500)) {
            n += dao.deleteDetectedOf(chunk)
            val left = dao.trackRowsOf(chunk).map { it.fileId }.toSet()
            val empty = chunk.filter { it !in left }
            if (empty.isNotEmpty()) dao.markCleared(empty, CLEARED)
        }
        dao.deleteEmptyPersons()
        n
    }

    /** Faces the user took away from someone wait unassigned; people left with nothing go. */
    private suspend fun replace(@Suppress("UNUSED_PARAMETER") ids: List<Long>) = change { dao.deleteEmptyPersons() }

    /**
     * "Ricomincia da capo": every media is analysed anew. Without [everything] the faces the user
     * confirmed or added stay; with it, people, faces and every choice of the user go too.
     */
    suspend fun resetAll(everything: Boolean) = change {
        dao.deleteAllScans()
        if (everything) {
            dao.deleteAllTracks(); dao.deleteAllMerges()
            dao.deleteAllPersons(); dao.deleteAllPersonMedia(); dao.deleteAllRejections(); dao.deleteAllIgnored()
        } else {
            // "Ricomincia" without "Cancella anche nomi… e correzioni": the faces the user confirmed
            // or added stay, and the new analysis carries them to the faces it finds ([carry]).
            // Deleting every face here left the named people empty, every confirmation lost.
            dao.deleteUnconfirmedTracks()
            dao.purgeRejections()
        }
        dao.deleteAllApart()
        embeddingCache.clear()
    }

    // ---- Diagnostics -----------------------------------------------------------------------

    private fun rejectText(s: String) = s.split(',').mapNotNull { e ->
        val (k, v) = e.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        FaceReject.of(k)?.let { "$v ${it.label}" }
    }.joinToString(", ")

    private fun nameOf(p: PersonEntity?) = p?.let { it.name.ifBlank { "persona senza nome" } } ?: "nessuno"

    /** Why [fileId] has (no) faces: what was read, found and set aside, and where each face went. */
    suspend fun describe(fileId: String): String = withContext(Dispatchers.Default) {
        val s = dao.scanOf(fileId) ?: return@withContext "Non ancora analizzato."
        if (s.status == CLEARED) return@withContext "Hai eliminato i volti trovati qui (da «Elimina i volti non assegnati» o " +
            "«Elimina volti trovati per errore»), quindi non viene analizzato da solo. «Analizza di nuovo» li cerca ancora."
        if (s.status.startsWith(STALLED)) return@withContext s.status.removePrefix("skip:").replaceFirstChar { it.uppercase() }
        if (s.status != "ok") return@withContext "Non analizzabile: " + s.status.removePrefix("skip:")
        val st = FaceStats.parse(s.stats)
        val cards = dao.cardsOf(fileId)
        val names = dao.persons().associateBy { it.id }
        val lines = ArrayList<String>()
        lines += "Fotogrammi letti: ${s.framesRead}" + (if (s.framesFailed > 0) " (${s.framesFailed} non leggibili)" else "") +
            (if (st.duplicates > 0) ", ${st.duplicates} uguali a quello prima saltati" else "") +
            (if (st.extra > 0) ", ${st.extra} aggiunti vicino ai volti" else "") + "."
        lines += "Volti trovati: ${s.faces}" + (if (s.rejects.isNotBlank()) ", scartati: ${rejectText(s.rejects)}" else "") + "."
        if (s.faces == 0) lines += "Nessun volto rilevato nei fotogrammi analizzati: prova «Rianalizza» se ne vedi uno."
        if (s.ms > 0) lines += "Analisi: %.1f s.".format(s.ms / 1000.0)
        FaceStats.refineOf(s.stats)?.let { lines += refineText(it) }
        for (c in cards) {
            val who = c.personId?.let { names[it] }
            lines += "• " + when {
                who == null -> "volto non assegnato"
                c.state == "manual" -> "${nameOf(who)} (confermato)"
                else -> "${nameOf(who)} (somiglianza %.2f)".format(c.similarity)
            }
        }
        lines.joinToString("\n")
    }

    private fun maxPair(a: TrackInfo, b: TrackInfo): Float = maxPair(a.embeddings, b.embeddings)

    private fun maxPair(a: List<FloatArray>, b: List<FloatArray>): Float {
        var best = -1f
        for (x in a) for (y in b) best = maxOf(best, similarity(x, y))
        return best
    }

    /** Totals for "Statistiche": speed, what was set aside and why, how the thresholds behave. */
    class Stats(
        val media: Int, val skipped: Int, val videoMs: Long, val videos: Int, val photoMs: Long, val photos: Int,
        val faces: Int, val rejects: Map<FaceReject, Int>, val sharp: IntArray, val duplicates: Int, val extra: Int,
    )

    suspend fun stats(): Stats = withContext(Dispatchers.Default) {
        val scans = dao.allScans()
        val files = repo.allFilesSnapshot().associateBy { it.id }
        val rejects = HashMap<FaceReject, Int>()
        val sharp = IntArray(FaceStats.SHARP_BANDS.size + 1)
        var dup = 0; var extra = 0; var vMs = 0L; var v = 0; var pMs = 0L; var p = 0
        for (s in scans) {
            s.rejects.split(',').forEach { e ->
                val parts = e.split('=')
                if (parts.size == 2) FaceReject.of(parts[0])?.let { rejects.merge(it, parts[1].toIntOrNull() ?: 0, Int::plus) }
            }
            val st = FaceStats.parse(s.stats)
            st.sharp.forEachIndexed { i, n -> sharp[i] += n }
            dup += st.duplicates; extra += st.extra
            val f = files[s.fileId] ?: continue
            if (s.ms <= 0) continue
            if (VaultRepository.isVideo(f.mimeType)) { vMs += s.ms; v++ } else { pMs += s.ms; p++ }
        }
        Stats(scans.size, scans.count { it.status != "ok" }, vMs, v, pMs, p, scans.sumOf { it.faces }, rejects, sharp, dup, extra)
    }

    companion object {
        /** What an unnamed person is called on screen: faces found get no name, the user gives one. */
        const val UNNAMED = "Senza nome"
        /** Status of a media whose faces the user cleared (analysed, nothing kept). */
        const val CLEARED = "skip:volti eliminati da te"
        /** Start of the status of a media whose reading got stuck and was left behind ([analyse]). */
        const val STALLED = "skip:analisi bloccata"
        /** A face this alike to a person's faces (cosine, see [suggestionsFor]) is offered as theirs. */
        const val SUGGEST = 0.36f
        /** Analyser version: raise it to analyse every media again after a change in the pipeline. */
        const val VERSION = 4
        /** Media analysed by this version have their faces of one person joined by [tidyOld] instead of analysed again. */
        private const val TIDY_FROM = 3
        /** A new face this alike to one the user deleted from the same media is that face again. */
        private const val DELETED = 0.5f
        /** A new face this alike to one marked "Non è un volto" is left out in any media (stricter: other light, other day). */
        private const val NOT_A_FACE = 0.6f
        /** Unassigned faces of different media this alike (to a group's mean) are offered together in "Da sistemare". */
        const val GROUP = 0.45f
        /** Where "Aggiungi un volto" looks when the paused frame has no face: half a second apart, then wider. */
        private val AHEAD_MS = longArrayOf(0, 500, 1_000, 1_500, 2_000, 3_000, 4_000, 5_000, 6_000, 8_000, 10_000, 12_000, 15_000)
        /**
         * A face shown folded under "poco chiari" in the details: unassigned and of low quality, or
         * (videos) seen in a single frame, nearly always a mistake of the analysis.
         */
        fun uncertain(c: TrackCard, video: Boolean) = c.personId == null && c.state != "manual" && c.state != "carried" &&
            (c.state == ASIDE || c.quality < LOW_QUALITY || (video && c.faceCount <= 1))
        /** State of an unassigned face that the closer look at its video ([refine]) did not find again. */
        const val ASIDE = "aside"
        private const val LOW_QUALITY = 0.3f
        /** A face analysed again is the old one when this alike (same frames, same picture: near 1). */
        private const val CARRY = 0.6f
        private val VOLTO = Regex("Volto (\\d+)")
        /** Frames and photos are read at most this large; detection runs on half of it. */
        const val FRAME_SIDE = 1280
        /** Where a sampled frame shows a face, the exact frame this much later is read too. */
        const val REFINE_MS = 2_000L
        /** At most this many such extra frames per video. */
        const val MAX_EXTRA = 12
        /** Frames are read this large by the closer look ([refine]). */
        private const val CLOSE_SIDE = 1600
        /** Photos are read this large by "Cerca più a fondo". */
        private const val DEEP_SIDE = 1920
        /** No frame read for this long: the media's reading is stuck and is left behind. */
        private const val STALL_MS = 90_000L
        /** The same for "Cerca più a fondo": its exact frames, larger, take far longer each. */
        private const val STALL_DEEP_MS = 240_000L
    }
}
