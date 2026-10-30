package com.cripta.app.data.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import androidx.room.withTransaction
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.db.FaceRejectionEntity
import com.cripta.app.data.db.FaceScanEntity
import com.cripta.app.data.db.FaceTrackEntity
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.PersonEntity
import com.cripta.app.data.db.PersonMediaEntity
import com.cripta.app.data.db.TrackCard
import com.cripta.app.data.db.TrackRow
import com.cripta.app.media.ChannelMediaDataSource
import com.cripta.app.media.face.EmbeddingCodec
import com.cripta.app.media.face.FaceEngine
import com.cripta.app.media.face.FaceImage
import com.cripta.app.media.face.FaceReject
import com.cripta.app.media.face.FaceTracker
import com.cripta.app.media.face.FaceThresholds
import com.cripta.app.media.face.FramePlan
import com.cripta.app.media.face.PeopleIndex
import com.cripta.app.media.face.Sighting
import com.cripta.app.media.face.TrackInfo
import com.cripta.app.security.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * People: finds the faces of every photo and video, groups them by person across the whole vault,
 * stores the user's corrections and explains every decision. Everything is read in memory from the
 * encrypted files and stored in the encrypted database.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class FaceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val repo: VaultRepository,
) {
    private val db get() = session.requireDb()
    private val dao get() = db.faceDao()

    @Volatile private var engine: FaceEngine? = null
    private val engineLock = Any()

    /** The models, loaded on first use (about 10 MB) and kept until [release]. */
    private fun engine(): FaceEngine = engine ?: synchronized(engineLock) {
        engine ?: FaceEngine(asset("faces/yunet.onnx"), asset("faces/sface.onnx")).also { engine = it }
    }

    private fun asset(name: String) = context.assets.open(name).use { it.readBytes() }

    /** Free the models after a scan. */
    fun release() = synchronized(engineLock) { engine?.close(); engine = null }

    /** People as last placed; rebuilt from the database after any change by the user. */
    @Volatile private var index: PeopleIndex? = null
    /** One writer at a time: placing new faces and the user's changes never interleave. */
    private val mutex = Mutex()

    // ---- Analysis --------------------------------------------------------------------------

    /** Live photos and videos never analysed (or by an older analyser). */
    suspend fun pending(): List<FileEntity> {
        val done = dao.allScans().associate { it.fileId to it.version }
        return repo.allFilesSnapshot().filter { f ->
            (VaultRepository.isVideo(f.mimeType) || VaultRepository.isImage(f.mimeType)) && (done[f.id] ?: -1) < VERSION
        }
    }

    /** Forget [fileId]'s faces (the user's own links stay) before analysing it again. */
    suspend fun forget(fileId: String): FileEntity? {
        val f = repo.fileById(fileId)?.takeIf { it.deletedAt == null } ?: return null
        mutex.withLock {
            db.withTransaction { dao.deleteTracksOf(fileId); dao.deleteScan(fileId) }
            index = null
        }
        return f
    }

    /** Find and store [file]'s faces (not placed among the people yet). True when it could be read. */
    suspend fun analyse(file: FileEntity): Boolean {
        val engine = engine()
        val sightings = ArrayList<Sighting>()
        val rejects = HashMap<FaceReject, Int>()
        var faces = 0; var read = 0; var failed = 0
        fun take(frame: Int, timeMs: Long, img: FaceImage) {
            read++
            for (f in engine.analyze(img)) {
                faces++
                if (f.reject != null) rejects.merge(f.reject, 1, Int::plus)
                else sightings += Sighting(frame, timeMs, f.embedding!!, f.quality, f)
            }
        }
        val status = try {
            withContext(Dispatchers.Default) {
                if (VaultRepository.isVideo(file.mimeType)) {
                    val (ok, bad, why) = videoFrames(file) { i, t, img -> take(i, t, img) }
                    failed = bad
                    when {
                        why != null -> "skip:$why"
                        ok == 0 -> "skip:nessun fotogramma leggibile"
                        else -> "ok"
                    }
                } else {
                    val img = photo(file)
                    if (img == null) "skip:immagine non decodificabile" else { take(0, 0, img); "ok" }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            "skip:troppo grande per la memoria"
        } catch (e: Exception) {
            android.util.Log.w("FaceRepository", "not readable (${e.javaClass.simpleName})")
            "skip:errore di lettura (${e.javaClass.simpleName})"
        }
        val tracks = FaceTracker.build(sightings)
        db.withTransaction {
            dao.deleteTracksOf(file.id)
            for (t in tracks) {
                val best = t.best.first()
                val face = best.payload as FaceEngine.Face
                dao.addTrack(FaceTrackEntity(fileId = file.id, frames = t.frames, timeMs = best.timeMs, faceCount = t.sightings.size,
                    width = face.det.w.toInt(), quality = t.quality, embeddings = EmbeddingCodec.encode(t.embeddings),
                    crop = jpeg(face.aligned!!)))
            }
            dao.putScan(FaceScanEntity(file.id, status, read, failed, faces,
                rejects.entries.joinToString(",") { "${it.key.name}=${it.value}" }, VERSION, System.currentTimeMillis()))
        }
        return status == "ok"
    }

    /**
     * Read [file]'s sampled frames one at a time (longest side at most [FRAME_SIDE]). Returns frames
     * read, frames that failed, and why the video could not be read at all (or null).
     */
    private suspend fun videoFrames(file: FileEntity, onFrame: (Int, Long, FaceImage) -> Unit): Triple<Int, Int, String?> {
        val r = MediaMetadataRetriever()
        val channel = repo.seekableChannel(file)
        try {
            r.setDataSource(ChannelMediaDataSource(channel, file.sizeBytes))
            val dur = file.durationMs?.takeIf { it > 0 }
                ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return Triple(0, 0, "durata non leggibile")
            var ok = 0; var bad = 0
            val params = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.RGB_565 }
            for ((i, t) in FramePlan.times(dur).withIndex()) {
                coroutineContext.ensureActive()
                val bmp = runCatching { r.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, params) }.getOrNull()
                if (bmp == null) { bad++; continue }
                val img = faceImage(bmp, FRAME_SIDE)
                ok++
                onFrame(i, t, img)
            }
            return Triple(ok, bad, null)
        } finally {
            runCatching { r.release() }
            runCatching { channel.close() }
        }
    }

    /** A photo decoded already turned upright (EXIF) and downsampled. */
    private suspend fun photo(file: FileEntity): FaceImage? {
        val bytes = repo.decryptBytes(file)
        val bmp = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { dec, info, _ ->
                val k = minOf(1.0, FRAME_SIDE.toDouble() / maxOf(info.size.width, info.size.height))
                dec.setTargetSize(maxOf(1, (info.size.width * k).toInt()), maxOf(1, (info.size.height * k).toInt()))
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull() ?: BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
            var s = 1
            while (maxOf(b.outWidth, b.outHeight) / (s * 2) >= FRAME_SIDE) s *= 2
            inSampleSize = s
        }) ?: return null
        return faceImage(bmp, FRAME_SIDE)
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

    private fun jpeg(face: FaceImage): ByteArray {
        val bmp = Bitmap.createBitmap(face.argb, face.width, face.height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it); bmp.recycle(); it.toByteArray() }
    }

    // ---- Placing faces among the people ----------------------------------------------------

    private fun info(r: TrackRow) = TrackInfo(r.id, r.fileId, r.frames, EmbeddingCodec.decode(r.embeddings), r.quality)

    /** Every person with their placed faces, the user's rejections included. */
    private suspend fun loadIndex(): PeopleIndex {
        val idx = PeopleIndex()
        for (p in dao.persons()) idx.person(p.id, named = p.name.isNotBlank(), ignored = p.ignored)
        for (r in dao.allTrackRows()) {
            coroutineContext.ensureActive()
            val pid = r.personId ?: continue
            if (r.state == "suggested" || pid !in idx.people) continue
            idx.add(pid, info(r))
        }
        dao.rejections().forEach { idx.rejected += it.trackId to it.personId }
        return idx
    }

    /** Place the faces of the media analysed but not placed yet. Returns how many media they were. */
    suspend fun placeNew(): Int = mutex.withLock {
        withContext(Dispatchers.Default) {
            val ids = dao.unclusteredIds()
            if (ids.isEmpty()) return@withContext 0
            val idx = index ?: loadIndex()
            index = idx
            try {
                val rows = ids.chunked(500).flatMap { dao.trackRowsOf(it) }.filter { it.personId == null }
                db.withTransaction {
                    place(idx, rows.map { info(it) })
                    ids.chunked(500).forEach { dao.markClustered(it) }
                }
            } catch (e: Throwable) {
                index = null   // the database and the index may differ now
                throw e
            }
            ids.size
        }
    }

    /** Place [tracks] (best first), then merge the people that turned out to be the same. */
    private suspend fun place(idx: PeopleIndex, tracks: List<TrackInfo>) {
        val touched = HashSet<Long>()
        for (t in tracks.sortedByDescending { it.quality }) {
            coroutineContext.ensureActive()
            val p = idx.place(t)
            when {
                p.personId != null && !p.suggested -> {
                    dao.place(t.id, p.personId, "auto", p.similarity)
                    idx.add(p.personId, t); touched += p.personId
                }
                else -> {
                    // A new person; an uncertain likeness is offered to the user on the other one's page.
                    if (p.suggested) {
                        dao.place(t.id, p.personId, "suggested", p.similarity)
                    } else {
                        val id = dao.addPerson(PersonEntity(createdAt = System.currentTimeMillis()))
                        idx.person(id); idx.add(id, t); touched += id
                        dao.place(t.id, id, "auto", p.similarity)
                    }
                }
            }
        }
        for ((keep, drop) in idx.merges(touched)) mergeRows(keep, drop)
    }

    private suspend fun mergeRows(keep: Long, drop: Long) {
        dao.moveTracks(drop, keep)
        dao.movePersonMedia(drop, keep)
        dao.deletePersonMedia(drop)
        dao.moveRejections(drop, keep)
        dao.deleteRejectionsOf(drop)
        dao.deletePerson(drop)
    }

    // ---- What the screens show -------------------------------------------------------------

    /** A person for the lists: their media (live only), the face shown and the suggestions waiting. */
    class Person(val entity: PersonEntity, val media: List<FileEntity>, val cover: TrackCard?, val faces: List<TrackCard>,
                 val suggestions: List<TrackCard>) {
        val id get() = entity.id
        val title get() = entity.name.ifBlank { "Persona ${entity.id}" }
    }

    class Overview(val scans: List<FaceScanEntity>, val media: Int, val people: List<Person>, val files: Map<String, FileEntity>)

    private fun <T> live(block: (com.cripta.app.data.db.FaceDao) -> Flow<T>): Flow<T> =
        session.database.flatMapLatest { d -> if (d == null) emptyFlow() else block(d.faceDao()) }

    /** Everyone, most media first; unnamed people with a single media at the end. */
    fun people(): Flow<Overview> = combine(
        live { it.observePersons() }, live { it.observeCards() }, live { it.observePersonMedia() }, live { it.observeScans() },
        repo.allFiles(),
    ) { persons, cards, links, scans, files ->
        val byId = files.associate { it.file.id to it.file }
        val faces = cards.filter { it.fileId in byId }.groupBy { it.personId }
        val people = persons.map { p ->
            val own = faces[p.id].orEmpty()
            val placed = own.filter { it.state != "suggested" }
            val mediaIds = LinkedHashSet<String>().apply { placed.sortedByDescending { it.width }.forEach { add(it.fileId) }; links.filter { it.personId == p.id }.forEach { add(it.fileId) } }
            Person(p, mediaIds.mapNotNull { byId[it] }, placed.firstOrNull { it.id == p.coverTrackId } ?: placed.maxByOrNull { it.width },
                placed, own.filter { it.state == "suggested" })
        }.filter { it.media.isNotEmpty() || it.entity.name.isNotBlank() || it.suggestions.isNotEmpty() }
            .sortedWith(compareBy<Person>({ it.entity.ignored }, { it.entity.name.isBlank() && it.media.size < 2 }, { -it.media.size }, { it.title.lowercase() }))
        val media = files.count { VaultRepository.isVideo(it.file.mimeType) || VaultRepository.isImage(it.file.mimeType) }
        Overview(scans.filter { it.fileId in byId }, media, people, byId)
    }

    /** The faces found in [fileId], live. */
    fun facesOf(fileId: String): Flow<List<TrackCard>> = live { it.observeCardsOf(fileId) }

    /** People linked to [fileId] by hand. */
    fun linksOf(fileId: String): Flow<List<Long>> = live { it.observePersonMedia() }.map { l -> l.filter { it.fileId == fileId }.map { it.personId } }

    suspend fun crop(trackId: Long): Bitmap? = withContext(Dispatchers.IO) {
        dao.crop(trackId)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
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

    // ---- The user's changes ----------------------------------------------------------------

    private suspend fun change(block: suspend () -> Unit) = mutex.withLock {
        db.withTransaction { block() }
        index = null
    }

    /** Naming someone confirms their faces: they stay with them whatever the next scans find. */
    suspend fun rename(personId: Long, name: String) = change {
        dao.rename(personId, name.trim())
        if (name.isNotBlank()) dao.lockTracks(personId)
    }

    /** "Unisci": everything goes to the first named one (or [into]). */
    suspend fun merge(into: Long, others: Collection<Long>) = change {
        for (o in others) if (o != into) mergeRows(into, o)
    }

    /** "Non è una persona": hidden, and similar new faces go with it (undo with [restore]). */
    suspend fun ignore(personId: Long) = change {
        dao.setIgnored(personId, true)
        dao.lockTracks(personId)
    }

    suspend fun restore(personId: Long) = change { dao.setIgnored(personId, false) }

    /** The track [trackId] is not [personId]: remembered, and the face is placed again elsewhere. */
    suspend fun reject(trackId: Long, personId: Long) {
        change {
            dao.addRejection(FaceRejectionEntity(trackId, personId))
            dao.place(trackId, null, "auto", 0f)
        }
        replace(listOf(trackId))
    }

    /** "Togli da questa persona": every face of [fileId] with [personId] and the manual link go. */
    suspend fun removeMedia(personId: Long, fileId: String) {
        val ids = dao.cardsOf(fileId).filter { it.personId == personId }.map { it.id }
        change {
            dao.removePersonMedia(personId, fileId)
            for (id in ids) { dao.addRejection(FaceRejectionEntity(id, personId)); dao.place(id, null, "auto", 0f) }
            dao.deleteEmptyPersons()
        }
        replace(ids)
    }

    /** "È lui/lei": the face goes to [personId] for good (a confirmed suggestion, or picked by hand). */
    suspend fun assign(trackId: Long, personId: Long) = change {
        dao.place(trackId, personId, "manual", 1f)
        dao.deleteEmptyPersons()
    }

    /** A media added to [personId] without one of its faces. */
    suspend fun link(personId: Long, fileId: String) = change { dao.addPersonMedia(PersonMediaEntity(personId, fileId)) }

    /** A new named person, for "Aggiungi a una persona › Nuova persona". */
    suspend fun newPerson(name: String): Long = mutex.withLock {
        dao.addPerson(PersonEntity(name = name.trim(), createdAt = System.currentTimeMillis())).also { index = null }
    }

    suspend fun setCover(personId: Long, trackId: Long) = change { dao.setCover(personId, trackId) }

    /** Place again tracks the user took away from someone. */
    private suspend fun replace(ids: List<Long>) = mutex.withLock {
        withContext(Dispatchers.Default) {
            val idx = loadIndex().also { index = it }
            val rows = ids.mapNotNull { dao.trackRow(it) }.filter { it.personId == null }
            db.withTransaction { place(idx, rows.map { info(it) }); dao.deleteEmptyPersons() }
            index = null
        }
    }

    /** "Ricalcola gruppi": every face not confirmed by the user is placed again from scratch. */
    suspend fun regroup() = mutex.withLock {
        withContext(Dispatchers.Default) {
            db.withTransaction {
                dao.unplaceAuto()
                dao.deleteEmptyPersons()
                val idx = loadIndex()
                place(idx, dao.allTrackRows().filter { it.personId == null }.map { info(it) })
                dao.deleteEmptyPersons()
            }
            index = null
        }
    }

    /** "Ricomincia da capo": every face and person is forgotten ([everything]: the user's choices too). */
    suspend fun resetAll(everything: Boolean) = change {
        dao.deleteAllScans(); dao.deleteAllTracks()
        if (everything) { dao.deleteAllPersons(); dao.deleteAllPersonMedia(); dao.deleteAllRejections() }
    }

    // ---- Diagnostics -----------------------------------------------------------------------

    private fun rejectText(s: String) = s.split(',').mapNotNull { e ->
        val (k, v) = e.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        FaceReject.of(k)?.let { "$v ${it.label}" }
    }.joinToString(", ")

    /** Why [fileId] has (no) faces: what was read, found and set aside, and where each face went. */
    suspend fun describe(fileId: String): String = withContext(Dispatchers.Default) {
        val s = dao.scanOf(fileId) ?: return@withContext "Non ancora analizzato."
        if (s.status != "ok") return@withContext "Non analizzabile: " + s.status.removePrefix("skip:")
        val cards = dao.cardsOf(fileId)
        val names = dao.persons().associateBy { it.id }
        val lines = ArrayList<String>()
        lines += "Fotogrammi letti: ${s.framesRead}" + (if (s.framesFailed > 0) " (${s.framesFailed} non leggibili)" else "") +
            ". Volti trovati: ${s.faces}" + (if (s.rejects.isNotBlank()) ", scartati: ${rejectText(s.rejects)}" else "") + "."
        if (s.faces == 0) lines += "Nessun volto rilevato nei fotogrammi analizzati."
        if (!s.clustered) lines += "I volti non sono ancora stati confrontati con il resto del vault."
        for (c in cards) {
            val who = c.personId?.let { names[it] }?.let { it.name.ifBlank { "Persona ${it.id}" } }
            lines += "• " + when {
                who == null -> "volto non assegnato"
                c.state == "suggested" -> "forse è $who (somiglianza %.2f): in attesa di conferma".format(c.similarity)
                c.state == "manual" -> "$who (confermato)"
                else -> "$who (somiglianza %.2f)".format(c.similarity)
            }
        }
        lines.joinToString("\n")
    }

    /** "Confronta": the people [trackId] looks most like, with the similarity and the verdict. */
    suspend fun explain(trackId: Long): String = withContext(Dispatchers.Default) {
        val row = dao.trackRow(trackId) ?: return@withContext "Volto non trovato."
        val idx = mutex.withLock { index ?: loadIndex().also { index = it } }
        val names = dao.persons().associateBy { it.id }
        val t = info(row)
        val verdicts = idx.explain(t).filter { it.personId != row.personId || row.state == "suggested" }
        val head = "Soglie: unione da %.2f, suggerimento da %.2f.".format(FaceThresholds.AUTO, FaceThresholds.SUGGEST)
        if (verdicts.isEmpty()) return@withContext "$head\nNessun'altra persona da confrontare."
        head + "\n" + verdicts.joinToString("\n") { v ->
            val n = names[v.personId]?.let { it.name.ifBlank { "Persona ${it.id}" } + if (it.ignored) " (ignorata)" else "" } ?: "Persona ${v.personId}"
            "• $n: %.2f, %s".format(v.similarity, v.reason)
        }
    }

    companion object {
        /** Analyser version: raise it to analyse every media again after a change in the pipeline. */
        const val VERSION = 1
        /** Frames and photos are read at most this large; detection runs on half of it. */
        const val FRAME_SIDE = 1280
    }
}
