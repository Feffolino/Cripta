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
import com.cripta.app.data.db.PersonEntity
import com.cripta.app.data.db.PersonMediaEntity
import com.cripta.app.data.db.TrackCard
import com.cripta.app.data.db.TrackRow
import com.cripta.app.media.ChannelMediaDataSource
import com.cripta.app.media.face.EmbeddingCodec
import com.cripta.app.media.face.FaceEngine
import com.cripta.app.media.face.FaceImage
import com.cripta.app.media.face.FaceReject
import com.cripta.app.media.face.FaceStats
import com.cripta.app.media.face.FaceThresholds
import com.cripta.app.media.face.FaceTracker
import com.cripta.app.media.face.FramePlan
import com.cripta.app.media.face.PeopleIndex
import com.cripta.app.media.face.Sighting
import com.cripta.app.media.face.TrackInfo
import com.cripta.app.media.face.similarity
import com.cripta.app.security.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
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

    /** Face pictures already decoded (track ids are never reused). */
    private val crops = LruCache<Long, Bitmap>(300)

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
        db.withTransaction { dao.purgeScans() + dao.purgeTracks() + dao.purgeLinks().also { dao.deleteEmptyPersons() } }
            .also { if (it > 0) index = null }
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
        val started = android.os.SystemClock.elapsedRealtime()
        val engine = engine()
        val sightings = ArrayList<Sighting>()
        val rejects = HashMap<FaceReject, Int>()
        val sharp = IntArray(FaceStats.SHARP_BANDS.size + 1)
        var faces = 0; var read = 0; var failed = 0; var duplicates = 0; var extra = 0
        /** Analyse one frame; true when it shows a face (usable or not). */
        fun take(frame: Int, timeMs: Long, img: FaceImage): Boolean {
            read++
            val found = engine.analyze(img)
            for (f in found) {
                faces++
                f.sharpness?.let { sharp[FaceStats.band(it)]++ }
                if (f.reject != null) rejects.merge(f.reject, 1, Int::plus)
                else sightings += Sighting(frame, timeMs, f.embedding!!, f.quality, f)
            }
            return found.isNotEmpty()
        }
        val status = try {
            withContext(Dispatchers.Default) {
                if (VaultRepository.isVideo(file.mimeType)) {
                    val r = videoFrames(file) { i, t, img -> take(i, t, img) }
                    failed = r.failed; duplicates = r.duplicates; extra = r.extra
                    when {
                        r.why != null -> "skip:${r.why}"
                        r.read == 0 -> "skip:nessun fotogramma leggibile"
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
                    crop = jpeg(face.aligned!!), times = t.times.joinToString(",")))
            }
            dao.putScan(FaceScanEntity(file.id, status, read, failed, faces,
                rejects.entries.joinToString(",") { "${it.key.name}=${it.value}" }, VERSION, System.currentTimeMillis(),
                ms = android.os.SystemClock.elapsedRealtime() - started, stats = FaceStats.encode(sharp, duplicates, extra)))
        }
        return status == "ok"
    }

    private class FrameRun(val read: Int, val failed: Int, val duplicates: Int, val extra: Int, val why: String?)

    /**
     * Read [file]'s sampled frames one at a time (longest side at most [FRAME_SIDE]). The same
     * keyframe returned for two moments is analysed once; where a face shows, the exact frame
     * [REFINE_MS] later is read too (another angle, often a better one).
     */
    private suspend fun videoFrames(file: FileEntity, onFrame: (Int, Long, FaceImage) -> Boolean): FrameRun {
        val r = MediaMetadataRetriever()
        val channel = repo.seekableChannel(file)
        try {
            r.setDataSource(ChannelMediaDataSource(channel, file.sizeBytes))
            val dur = file.durationMs?.takeIf { it > 0 }
                ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return FrameRun(0, 0, 0, 0, "durata non leggibile")
            var ok = 0; var bad = 0; var dup = 0; var extra = 0
            val params = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.RGB_565 }
            val plan = FramePlan.times(dur)
            val withFaces = ArrayList<Long>()
            var lastHash: Long? = null
            for ((i, t) in plan.withIndex()) {
                coroutineContext.ensureActive()
                val bmp = runCatching { r.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, params) }.getOrNull()
                if (bmp == null) { bad++; continue }
                val img = faceImage(bmp, FRAME_SIDE)
                val h = FaceStats.frameHash(img)
                if (lastHash != null && FaceStats.sameFrame(h, lastHash)) { dup++; continue }
                lastHash = h
                ok++
                if (onFrame(i, t, img)) withFaces += t
            }
            // Frame bits stay below 64: the plan takes at most 40, the extra ones at most MAX_EXTRA.
            for ((k, t) in withFaces.take(MAX_EXTRA).withIndex()) {
                coroutineContext.ensureActive()
                val at = t + REFINE_MS
                if (at >= dur) continue
                val bmp = runCatching { r.getFrameAtTime(at * 1000, MediaMetadataRetriever.OPTION_CLOSEST, params) }.getOrNull() ?: continue
                extra++; ok++
                onFrame(plan.size + k, at, faceImage(bmp, FRAME_SIDE))
            }
            return FrameRun(ok, bad, dup, extra, null)
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

    private suspend fun currentIndex(): PeopleIndex = index ?: loadIndex().also { index = it }

    /** Place the faces of the media analysed but not placed yet. Returns how many media they were. */
    suspend fun placeNew(): Int = mutex.withLock {
        withContext(Dispatchers.Default) {
            val ids = dao.unclusteredIds()
            if (ids.isEmpty()) return@withContext 0
            val idx = currentIndex()
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

    /** Place [tracks] (best first), then merge the unnamed people that turned out to be the same. */
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
                // An uncertain likeness waits on the other one's page ("Forse è X?").
                p.suggested -> dao.place(t.id, p.personId, "suggested", p.similarity)
                else -> {
                    val id = dao.addPerson(PersonEntity(createdAt = System.currentTimeMillis()))
                    idx.person(id); idx.add(id, t); touched += id
                    dao.place(t.id, id, "auto", p.similarity)
                }
            }
        }
        for ((keep, drop, score) in idx.merges(touched)) mergeRows(keep, drop, score, auto = true)
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
        dao.deletePerson(drop)
        if (p?.favorite == true) dao.setFavorite(keep, true)
        return logId
    }

    // ---- What the screens show -------------------------------------------------------------

    /** A person for the lists: their media (live only), the face shown and the suggestions waiting. */
    class Person(val entity: PersonEntity, val media: List<FileEntity>, val cover: TrackCard?, val faces: List<TrackCard>,
                 val suggestions: List<TrackCard>) {
        val id get() = entity.id
        val title get() = entity.name.ifBlank { "Persona ${entity.id}" }
        /** Shown in the main lists: named, starred, or seen in two media at least. */
        val main get() = !entity.ignored && (entity.name.isNotBlank() || entity.favorite || media.size >= 2)
    }

    class Overview(val scans: List<FaceScanEntity>, val media: Int, val people: List<Person>, val files: Map<String, FileEntity>) {
        val suggestions get() = people.filter { !it.entity.ignored }.sumOf { it.suggestions.size }
    }

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
        .debounce(400)
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
                placed, own.filter { it.state == "suggested" })
        }.filter { it.media.isNotEmpty() || it.entity.name.isNotBlank() || it.entity.favorite || it.suggestions.isNotEmpty() }
            .sortedWith(compareBy<Person>({ it.entity.ignored }, { !it.entity.favorite }, { !it.main }, { -it.media.size }, { it.title.lowercase() }))
        val media = i.files.count { VaultRepository.isVideo(it.mimeType) || VaultRepository.isImage(it.mimeType) }
        return Overview(i.scans.filter { it.fileId in byId }, media, people, byId)
    }

    fun people(): Flow<Overview> = overview

    /** The faces found in [fileId], live. */
    fun facesOf(fileId: String): Flow<List<TrackCard>> = live { it.observeCardsOf(fileId) }

    /** People linked to [fileId] by hand. */
    fun linksOf(fileId: String): Flow<List<Long>> = live { it.observePersonMedia() }.map { l -> l.filter { it.fileId == fileId }.map { it.personId } }

    /** Merges, most recent first (automatic and the user's), for "Unioni". */
    fun merges(): Flow<List<FaceMergeEntity>> = live { it.observeMerges() }

    suspend fun crop(trackId: Long): Bitmap? {
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

    /** People who look like [personId] but were not merged: (id, similarity), most alike first. */
    suspend fun similarPeople(personId: Long): List<Pair<Long, Float>> = mutex.withLock {
        withContext(Dispatchers.Default) { currentIndex().similarTo(personId).take(6) }
    }

    // ---- The user's changes ----------------------------------------------------------------

    private suspend fun <T> change(block: suspend () -> T): T = mutex.withLock {
        db.withTransaction { block() }.also { index = null }
    }

    /** Naming someone confirms their faces: they stay with them whatever the next scans find. */
    suspend fun rename(personId: Long, name: String) = change {
        dao.rename(personId, name.trim())
        if (name.isNotBlank()) dao.lockTracks(personId)
    }

    suspend fun setFavorite(personId: Long, favorite: Boolean) = change { dao.setFavorite(personId, favorite) }

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

    /** A media added to [personId] without one of its faces. */
    suspend fun link(personId: Long, fileId: String) = change { dao.addPersonMedia(PersonMediaEntity(personId, fileId)) }

    /** A new named person, for "Chi è? › Nuova persona". */
    suspend fun newPerson(name: String): Long = change { dao.addPerson(PersonEntity(name = name.trim(), createdAt = System.currentTimeMillis())) }

    suspend fun setCover(personId: Long, trackId: Long) = change { dao.setCover(personId, trackId) }

    /** Place again tracks the user took away from someone. */
    private suspend fun replace(ids: List<Long>) = mutex.withLock {
        withContext(Dispatchers.Default) {
            val idx = loadIndex()
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
        dao.deleteAllScans(); dao.deleteAllTracks(); dao.deleteAllMerges()
        if (everything) { dao.deleteAllPersons(); dao.deleteAllPersonMedia(); dao.deleteAllRejections() }
    }

    // ---- Diagnostics -----------------------------------------------------------------------

    private fun rejectText(s: String) = s.split(',').mapNotNull { e ->
        val (k, v) = e.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        FaceReject.of(k)?.let { "$v ${it.label}" }
    }.joinToString(", ")

    private fun nameOf(p: PersonEntity?) = p?.let { it.name.ifBlank { "Persona ${it.id}" } } ?: "nessuno"

    /** Why [fileId] has (no) faces: what was read, found and set aside, and where each face went. */
    suspend fun describe(fileId: String): String = withContext(Dispatchers.Default) {
        val s = dao.scanOf(fileId) ?: return@withContext "Non ancora analizzato."
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
        if (!s.clustered) lines += "I volti non sono ancora stati confrontati con il resto del vault."
        if (s.ms > 0) lines += "Analisi: %.1f s.".format(s.ms / 1000.0)
        for (c in cards) {
            val who = c.personId?.let { names[it] }
            lines += "• " + when {
                who == null -> "volto non assegnato"
                c.state == "suggested" -> "forse è ${nameOf(who)} (somiglianza %.2f): in attesa di conferma".format(c.similarity)
                c.state == "manual" -> "${nameOf(who)} (confermato)"
                else -> "${nameOf(who)} (somiglianza %.2f)".format(c.similarity)
            }
        }
        lines.joinToString("\n")
    }

    /** "Confronta": the people [trackId] looks most like, with the similarity and the verdict. */
    suspend fun explain(trackId: Long): String = withContext(Dispatchers.Default) {
        val row = dao.trackRow(trackId) ?: return@withContext "Volto non trovato."
        val idx = mutex.withLock { currentIndex() }
        val names = dao.persons().associateBy { it.id }
        val verdicts = idx.explain(info(row)).filter { it.personId != row.personId || row.state == "suggested" }
        val head = "Soglie: unione da %.2f, suggerimento da %.2f.".format(FaceThresholds.AUTO, FaceThresholds.SUGGEST)
        if (verdicts.isEmpty()) return@withContext "$head\nNessun'altra persona da confrontare."
        head + "\n" + verdicts.joinToString("\n") { v ->
            val p = names[v.personId]
            "• ${nameOf(p)}${if (p?.ignored == true) " (ignorata)" else ""}: %.2f, %s".format(v.similarity, v.reason)
        }
    }

    /** "Confronta volti" of two media: each pair of their faces, how alike, and why (not) together. */
    suspend fun compareMedia(a: String, b: String): String = withContext(Dispatchers.Default) {
        val ta = dao.trackRowsOf(listOf(a)); val tb = dao.trackRowsOf(listOf(b))
        fun state(id: String, t: List<TrackRow>) = when {
            t.isNotEmpty() -> null
            else -> "Il ${if (id == a) "primo" else "secondo"} media non ha volti utilizzabili."
        }
        state(a, ta)?.let { return@withContext it + "\n" + describe(a) }
        state(b, tb)?.let { return@withContext it + "\n" + describe(b) }
        val names = dao.persons().associateBy { it.id }
        val rejected = dao.rejections().map { it.trackId to it.personId }.toSet()
        val pairs = ta.flatMap { x -> tb.map { y -> Triple(x, y, maxPair(info(x), info(y))) } }.sortedByDescending { it.third }
        "Soglie: unione da %.2f, suggerimento da %.2f.\n".format(FaceThresholds.AUTO, FaceThresholds.SUGGEST) +
            pairs.take(6).joinToString("\n") { (x, y, s) ->
                val px = x.personId?.let { names[it] }; val py = y.personId?.let { names[it] }
                val why = when {
                    x.personId != null && x.personId == y.personId && x.state != "suggested" && y.state != "suggested" -> "stessa persona (${nameOf(px)})"
                    x.id to y.personId in rejected || y.id to x.personId in rejected -> "separati da te"
                    s >= FaceThresholds.AUTO -> "molto simili ma in persone diverse: usa «Unisci» o «Ricalcola i gruppi»"
                    s >= FaceThresholds.SUGGEST -> "somiglianza incerta: diventa un suggerimento, non un'unione"
                    else -> "troppo diversi"
                }
                "• ${nameOf(px)} ↔ ${nameOf(py)}: %.2f, %s".format(s, why)
            }
    }

    private fun maxPair(a: TrackInfo, b: TrackInfo): Float {
        var best = -1f
        for (x in a.embeddings) for (y in b.embeddings) best = maxOf(best, similarity(x, y))
        return best
    }

    /** Totals for "Statistiche": speed, what was set aside and why, how the thresholds behave. */
    class Stats(
        val media: Int, val skipped: Int, val videoMs: Long, val videos: Int, val photoMs: Long, val photos: Int,
        val faces: Int, val rejects: Map<FaceReject, Int>, val sharp: IntArray, val duplicates: Int, val extra: Int,
        val confirmed: List<Float>, val rejected: List<Float>, val autoMerges: Int,
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
        val cards = dao.allTrackRows()
        val merges = mutex.withLock { db.query("SELECT COUNT(*) FROM face_merges WHERE auto = 1", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } }
        Stats(scans.size, scans.count { it.status != "ok" }, vMs, v, pMs, p, scans.sumOf { it.faces }, rejects, sharp, dup, extra,
            cards.filter { it.state == "manual" && it.similarity in 0.01f..0.999f }.map { it.similarity },
            dao.rejections().map { it.similarity }.filter { it > 0f }, merges)
    }

    companion object {
        /** Analyser version: raise it to analyse every media again after a change in the pipeline. */
        const val VERSION = 2
        /** Frames and photos are read at most this large; detection runs on half of it. */
        const val FRAME_SIDE = 1280
        /** Where a sampled frame shows a face, the exact frame this much later is read too. */
        const val REFINE_MS = 2_000L
        /** At most this many such extra frames per video. */
        const val MAX_EXTRA = 12
    }
}
