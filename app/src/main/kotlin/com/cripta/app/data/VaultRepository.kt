package com.cripta.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.cripta.app.data.db.FileEntity
import com.cripta.app.data.db.FileTagCrossRef
import com.cripta.app.data.db.FileWithTags
import com.cripta.app.data.db.FolderEntity
import com.cripta.app.data.db.TagEntity
import com.cripta.app.security.SessionManager
import com.cripta.crypto.FileCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.channels.SeekableByteChannel
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val blobs: BlobStore,
    private val settings: SettingsStore,
) {
    private val db get() = session.requireDb()
    private val dek get() = session.requireDek()
    private val THUMB_AAD = "cripta-thumb".toByteArray()

    /** Bumped after every DB mutation. VMs observe this to re-query reliably,
     *  independent of Room/SQLCipher invalidation timing (which is flaky here). */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes
    private fun notifyChanged() { _changes.value++ }

    // --- Video conversion events (foreground service -> UI) ---
    /** Emitted after a video is successfully transcoded+encrypted into the vault. */
    /** [ask] = the user chose to be asked whether to delete the original (otherwise it's handled). */
    data class ConversionEvent(val originalId: String, val newId: String, val ask: Boolean = true)
    private val _convertEvents =
        kotlinx.coroutines.flow.MutableSharedFlow<ConversionEvent>(extraBufferCapacity = 16)
    val convertEvents: kotlinx.coroutines.flow.SharedFlow<ConversionEvent> = _convertEvents
    private val _convertingIds = MutableStateFlow<Set<String>>(emptySet())
    /** File ids with a conversion in flight; drives the viewer's progress indicator. */
    val convertingIds: StateFlow<Set<String>> = _convertingIds
    fun setConverting(id: String, active: Boolean) {
        _convertingIds.value = if (active) _convertingIds.value + id else _convertingIds.value - id
    }
    /** Conversion queue status for the in-app banner. */
    data class ConvertStatus(
        val currentName: String? = null,
        val pct: Int = 0,
        /** Videos waiting after the current one. */
        val waiting: Int = 0,
        /** Outcome of the last finished conversion, shown until dismissed. */
        val lastResult: String? = null,
        val lastOk: Boolean = true,
    ) {
        val active: Boolean get() = currentName != null
    }
    private val _convertStatus = MutableStateFlow(ConvertStatus())
    val convertStatus: StateFlow<ConvertStatus> = _convertStatus
    fun updateConvertStatus(f: (ConvertStatus) -> ConvertStatus) = _convertStatus.update(f)
    fun dismissConvertResult() = _convertStatus.update { it.copy(lastResult = null) }

    private val _conversionProgress = MutableStateFlow(0)
    /** 0-100 progress of the current transcode; drives the in-app progress popup. */
    val conversionProgress: StateFlow<Int> = _conversionProgress
    fun setConversionProgress(pct: Int) { _conversionProgress.value = pct.coerceIn(0, 100) }

    enum class DownloadPhase { QUEUED, PREPARING, DOWNLOADING, DONE, FAILED, CANCELLED }

    /** One link in the downloader queue, with its destination and live progress. */
    data class DownloadJob(
        val id: String,
        val url: String,
        val maxHeight: Int? = null,
        val folderId: Long? = null,
        val tagIds: List<Long> = emptyList(),
        val phase: DownloadPhase = DownloadPhase.QUEUED,
        val pct: Int = 0,
        val etaSec: Long = 0,
        /** File name once known, or the failure reason. */
        val message: String? = null,
        /** Id of the imported file once done (for "Apri"). */
        val fileId: String? = null,
    ) {
        val active: Boolean get() = phase == DownloadPhase.PREPARING || phase == DownloadPhase.DOWNLOADING
        val finished: Boolean get() = phase == DownloadPhase.DONE || phase == DownloadPhase.FAILED || phase == DownloadPhase.CANCELLED
    }

    private val _downloads = MutableStateFlow<List<DownloadJob>>(emptyList())
    /** The downloader queue, oldest first: running, waiting and recently finished links. */
    val downloads: StateFlow<List<DownloadJob>> = _downloads

    fun enqueueDownload(job: DownloadJob) = _downloads.update { it + job }
    fun updateDownload(id: String, f: (DownloadJob) -> DownloadJob) =
        _downloads.update { list -> list.map { if (it.id == id) f(it) else it } }
    /** Claim the next waiting link (marks it PREPARING), or null when the queue is drained. */
    @Synchronized fun takeNextDownload(): DownloadJob? {
        val next = _downloads.value.firstOrNull { it.phase == DownloadPhase.QUEUED } ?: return null
        updateDownload(next.id) { it.copy(phase = DownloadPhase.PREPARING) }
        return next
    }
    /** Remove a waiting or finished link from the list (a running one must be cancelled first). */
    fun removeDownload(id: String) = _downloads.update { list -> list.filterNot { it.id == id && !it.active } }
    fun clearFinishedDownloads() = _downloads.update { list -> list.filterNot { it.finished } }

    /**
     * Live state of file imports (encryption into the vault), shared by every batch the service is
     * running. Drives the in-app banner so the user can see whether an import is still going, how
     * far along it is, and when it has finished.
     */
    data class ImportState(
        val active: Boolean = false,
        val total: Int = 0,
        val done: Int = 0,
        val failed: Int = 0,
        val currentName: String? = null,
        val currentBytes: Long = 0,
        val currentTotalBytes: Long = 0,
        /** True once every batch finished; stays until the user dismisses the result. */
        val finished: Boolean = false,
        /** Just-imported files that were already in the vault: (new file id, name of the existing copy). */
        val duplicates: List<Pair<String, String>> = emptyList(),
    ) {
        val succeeded: Int get() = done - failed
        /** Overall progress 0..1, counting the partial progress of the file being encrypted. */
        val fraction: Float get() {
            if (total <= 0) return 0f
            val partial = if (active && currentTotalBytes > 0)
                (currentBytes.toFloat() / currentTotalBytes).coerceIn(0f, 1f) else 0f
            return ((done + partial) / total).coerceIn(0f, 1f)
        }
    }

    private val _importState = MutableStateFlow(ImportState())
    val importState: StateFlow<ImportState> = _importState
    private val importBatches = java.util.concurrent.atomic.AtomicInteger(0)

    /** A batch of [count] files is starting (batches started while another runs are merged). */
    fun importBegin(count: Int) {
        val first = importBatches.getAndIncrement() == 0
        _importState.update { s ->
            if (first || !s.active) ImportState(active = true, total = count)
            else s.copy(total = s.total + count)
        }
    }

    fun importCurrent(name: String, totalBytes: Long) =
        _importState.update { it.copy(currentName = name, currentBytes = 0, currentTotalBytes = totalBytes) }

    fun importBytes(bytes: Long) = _importState.update { it.copy(currentBytes = bytes) }

    fun importItemDone(ok: Boolean) = _importState.update {
        it.copy(done = it.done + 1, failed = it.failed + if (ok) 0 else 1, currentBytes = 0, currentTotalBytes = 0)
    }

    fun importDuplicate(newId: String, existingName: String) =
        _importState.update { it.copy(duplicates = it.duplicates + (newId to existingName)) }

    /** Remove the given duplicates from the banner state (after the user resolved them). */
    fun clearImportDuplicates() = _importState.update { it.copy(duplicates = emptyList()) }

    /** A batch ended; returns the final state when it was the last one running, else null. */
    fun importEnd(): ImportState? {
        if (importBatches.decrementAndGet() > 0) return null
        _importState.update { it.copy(active = false, finished = true, currentName = null) }
        return _importState.value
    }

    /** Hide the "import finished" result. */
    fun dismissImportResult() {
        if (!_importState.value.active) _importState.value = ImportState()
    }

    fun emitConvertResult(event: ConversionEvent) { _convertEvents.tryEmit(event) }

    /**
     * Shred+delete any orphaned conversion temp files (conv-*) left in cache by a process that was
     * killed mid-transcode. Safe to call at cold start (no conversion can be in flight then), which
     * guarantees decrypted plaintext never lingers on disk across app restarts.
     */
    fun sweepConversionTemp() {
        runCatching {
            context.cacheDir.listFiles { f -> f.name.startsWith("conv-") }?.forEach { shredTempFile(it) }
        }
    }

    // --- Flows ---
    fun folders(parentId: Long?): Flow<List<FolderEntity>> = db.folderDao().childrenOf(parentId)
    fun allFolders(): Flow<List<FolderEntity>> = db.folderDao().all()
    fun files(folderId: Long?): Flow<List<FileWithTags>> = db.fileDao().inFolderWithTags(folderId)
    fun allFiles(): Flow<List<FileWithTags>> = db.fileDao().allWithTags()
    @OptIn(ExperimentalCoroutinesApi::class)
    fun tags(): Flow<List<TagEntity>> =
        settings.settings.map { it.tagSortMode }.distinctUntilChanged()
            .flatMapLatest { mode ->
                if (mode == TagSortMode.CUSTOM) db.tagDao().allByOrder() else db.tagDao().all()
            }

    /** Persist a custom tag order (position = index in [orderedIds]). */
    suspend fun reorderTags(orderedIds: List<Long>) = withContext(Dispatchers.IO) {
        orderedIds.forEachIndexed { i, id -> db.tagDao().setOrder(id, i) }
        notifyChanged()
    }
    fun folderAggregates(): Flow<List<com.cripta.app.data.db.FolderAgg>> = db.fileDao().folderAggregates()

    // --- Folders ---
    suspend fun createFolder(name: String, parentId: Long?): Long = withContext(Dispatchers.IO) {
        val id = db.folderDao().insert(FolderEntity(name = name.trim(), parentId = parentId, createdAt = now()))
        notifyChanged(); id
    }

    suspend fun renameFolder(folder: FolderEntity, newName: String) = withContext(Dispatchers.IO) {
        db.folderDao().update(folder.copy(name = newName.trim())); notifyChanged()
    }

    suspend fun setFolderStyle(id: Long, color: Int?, emoji: String?) = withContext(Dispatchers.IO) {
        db.folderDao().setStyle(id, color, emoji?.trim()?.ifEmpty { null }); notifyChanged()
    }

    suspend fun moveFile(fileId: String, folderId: Long?) = withContext(Dispatchers.IO) {
        db.fileDao().move(fileId, folderId); notifyChanged()
    }

    /** Persist a manual order: assign ascending weights matching the given id order. */
    suspend fun setSortWeights(orderedIds: List<String>) = withContext(Dispatchers.IO) {
        orderedIds.forEachIndexed { index, id -> db.fileDao().setWeight(id, index.toLong()) }
        notifyChanged()
    }

    /**
     * Recursively crypto-shred every file in the folder subtree, then delete the folders.
     * Returns the ids of the deleted files so the caller can evict their cover cache.
     */
    suspend fun deleteFolderRecursive(folderId: Long): List<String> = withContext(Dispatchers.IO) {
        // Gather subtree by walking children via a snapshot query set.
        val toVisit = ArrayDeque<Long>().apply { add(folderId) }
        val subtree = mutableListOf<Long>()
        val childrenMap = snapshotChildren()
        while (toVisit.isNotEmpty()) {
            val id = toVisit.removeFirst()
            subtree.add(id)
            childrenMap[id]?.forEach { toVisit.add(it) }
        }
        val deletedIds = mutableListOf<String>()
        for (fid in subtree) {
            db.fileDao().idsInFolder(fid).forEach { secureDelete(it); deletedIds.add(it) }
        }
        // delete deepest first
        for (fid in subtree.reversed()) {
            db.folderDao().byId(fid)?.let { db.folderDao().delete(it) }
        }
        notifyChanged()
        deletedIds
    }

    private suspend fun snapshotChildren(): Map<Long?, List<Long>> = withContext(Dispatchers.IO) {
        // One-shot read of the folder table to build a parent -> children map.
        val result = mutableMapOf<Long?, MutableList<Long>>()
        // Use a blocking first collection via a simple query through DAO.all() is a Flow;
        // instead reconstruct from repeated byId is costly. We read via a raw snapshot.
        val folders = db.query("SELECT id, parentId FROM folders", null)
        folders.use { c ->
            val idIdx = c.getColumnIndexOrThrow("id")
            val parentIdx = c.getColumnIndexOrThrow("parentId")
            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val parent = if (c.isNull(parentIdx)) null else c.getLong(parentIdx)
                result.getOrPut(parent) { mutableListOf() }.add(id)
            }
        }
        result
    }

    // --- Import ---
    /** Display name and reported size (0 if unknown) of a picked document. */
    suspend fun nameAndSize(uri: Uri): Pair<String, Long> = withContext(Dispatchers.IO) {
        runCatching { queryNameSize(uri) }.getOrDefault("file" to 0L)
    }

    suspend fun import(uri: Uri, folderId: Long?, onBytes: ((Long) -> Unit)? = null): FileEntity = withContext(Dispatchers.IO) {
        val (name, _) = queryNameSize(uri)
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        // Read media duration from the still-plaintext source before it is encrypted.
        val duration = if (isPlayable(mime)) durationOf(uri) else null
        // Resolution too (drives the HD/4K badge on covers), also read before encryption.
        val res = resolutionOf(uri, mime)
        val uuid = UUID.randomUUID().toString()
        val wrappedKeyset = FileCrypto.createWrappedFileKeyset(dek)
        val blob = blobs.blob(uuid)
        // Count the actual plaintext bytes streamed in, rather than trusting OpenableColumns.SIZE
        // (some providers report it wrong). The exact length is what the seekable player needs.
        val written = context.contentResolver.openInputStream(uri)!!.use { input ->
            blob.outputStream().use { out ->
                FileCrypto.encryptingStream(wrappedKeyset, dek, uuid, out).use { enc ->
                    if (onBytes == null) input.copyTo(enc)
                    else {
                        // Same as copyTo, but reports progress (throttled to ~every 1 MB).
                        val buf = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                        var total = 0L
                        var lastReport = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            enc.write(buf, 0, n)
                            total += n
                            if (total - lastReport >= 1_048_576) { onBytes(total); lastReport = total }
                        }
                        onBytes(total)
                        total
                    }
                }
            }
        }
        val entity = FileEntity(
            id = uuid,
            originalName = name,
            mimeType = mime,
            sizeBytes = written,
            folderId = folderId,
            createdAt = now(),
            importedAt = now(),
            wrappedKeyset = wrappedKeyset,
            durationMs = duration,
            sortWeight = now(),   // new files append to the bottom of the manual order
            width = res?.first,
            height = res?.second,
        )
        db.fileDao().insert(entity)
        notifyChanged()
        entity
    }

    /** Decrypting input stream for a file's plaintext (no full-file buffer in RAM). */
    fun decryptingStream(file: FileEntity): java.io.InputStream =
        FileCrypto.decryptingStream(file.wrappedKeyset, dek, file.id, blobs.blob(file.id))

    // --- Video conversion (transcode to MP4) support ---

    /** Decrypt a file's plaintext to a fresh temp file in app-private cache. Caller deletes it. */
    suspend fun decryptToTempFile(file: FileEntity, suffix: String): File = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "conv-${UUID.randomUUID()}.$suffix")
        decryptingStream(file).use { input -> tmp.outputStream().use { input.copyTo(it) } }
        tmp
    }

    /** A fresh empty temp path in app-private cache (not created). */
    fun newTempFile(suffix: String): File = File(context.cacheDir, "conv-${UUID.randomUUID()}.$suffix")

    /** Best-effort overwrite + delete of a plaintext temp file produced during conversion. */
    fun shredTempFile(f: File) {
        if (!f.exists()) return
        runCatching {
            val len = f.length()
            java.io.RandomAccessFile(f, "rw").use { raf ->
                val rnd = java.security.SecureRandom()
                val chunk = ByteArray(64 * 1024)
                var written = 0L
                while (written < len) {
                    rnd.nextBytes(chunk)
                    val n = minOf(chunk.size.toLong(), len - written).toInt()
                    raf.write(chunk, 0, n); written += n
                }
                raf.fd.sync()
            }
        }
        f.delete()
    }

    /**
     * Encrypt a converted plaintext MP4 into the vault as a new file, inheriting the original's
     * folder, favorite and tags. The original is left untouched (the caller decides whether to
     * delete it). Returns the new file entity.
     */
    suspend fun importConvertedMp4(original: FileEntity, mp4: File, replace: Boolean = false): FileEntity = withContext(Dispatchers.IO) {
        val uuid = UUID.randomUUID().toString()
        val wrapped = FileCrypto.createWrappedFileKeyset(dek)
        val written = mp4.inputStream().use { input ->
            blobs.blob(uuid).outputStream().use { out ->
                FileCrypto.encryptingStream(wrapped, dek, uuid, out).use { input.copyTo(it) }
            }
        }
        // Never keep a short write: the vault copy must hold every byte of the verified MP4.
        if (written != mp4.length()) {
            blobs.shred(uuid)
            throw java.io.IOException("Scrittura incompleta della copia convertita")
        }
        val baseName = original.originalName.substringBeforeLast('.', original.originalName)
        val convertedRes = videoResolutionOf(mp4)
        val entity = FileEntity(
            id = uuid,
            originalName = "$baseName.mp4",
            mimeType = "video/mp4",
            sizeBytes = written,
            folderId = original.folderId,
            isFavorite = original.isFavorite,
            createdAt = original.createdAt,
            // Replacing: take the original's place in date and manual order too.
            importedAt = if (replace) original.importedAt else now(),
            wrappedKeyset = wrapped,
            durationMs = original.durationMs,
            sortWeight = if (replace) original.sortWeight else now(),
            sourceUrl = original.sourceUrl,
            width = convertedRes?.first ?: original.width,
            height = convertedRes?.second ?: original.height,
            playbackPosMs = if (replace) original.playbackPosMs else null,
        )
        db.fileDao().insert(entity)
        // Carry over the original's tags.
        db.fileDao().withTagsById(original.id)?.tags?.forEach {
            db.tagDao().link(FileTagCrossRef(fileId = uuid, tagId = it.id))
        }
        // The original always goes to the trash (never shredded here), so a bad conversion can be undone.
        if (replace) db.fileDao().setDeletedAt(original.id, now())
        notifyChanged()
        entity
    }

    /** Encrypt a freshly downloaded plaintext MP4 into the vault as a new file. */
    suspend fun importDownloadedMp4(
        mp4: File, displayName: String, folderId: Long?, sourceUrl: String?, tagIds: Collection<Long> = emptyList(),
    ): FileEntity =
        withContext(Dispatchers.IO) {
            val uuid = UUID.randomUUID().toString()
            val wrapped = FileCrypto.createWrappedFileKeyset(dek)
            val written = mp4.inputStream().use { input ->
                blobs.blob(uuid).outputStream().use { out ->
                    FileCrypto.encryptingStream(wrapped, dek, uuid, out).use { input.copyTo(it) }
                }
            }
            val duration = runCatching {
                val r = android.media.MediaMetadataRetriever()
                try {
                    r.setDataSource(mp4.absolutePath)
                    r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()?.takeIf { it > 0 }
                } finally { runCatching { r.release() } }
            }.getOrNull()
            val downloadedRes = videoResolutionOf(mp4)
            val name = displayName.ifBlank { "download" }.let { if (it.endsWith(".mp4")) it else "$it.mp4" }
            val entity = FileEntity(
                id = uuid,
                originalName = name,
                mimeType = "video/mp4",
                sizeBytes = written,
                folderId = folderId,
                createdAt = now(),
                importedAt = now(),
                wrappedKeyset = wrapped,
                durationMs = duration,
                sortWeight = now(),
                sourceUrl = sourceUrl,
                width = downloadedRes?.first,
                height = downloadedRes?.second,
            )
            // The chosen folder may have been deleted while downloading: fall back to the root.
            val safeEntity = if (folderId != null && db.folderDao().byId(folderId) == null) entity.copy(folderId = null) else entity
            db.fileDao().insert(safeEntity)
            tagIds.forEach { db.tagDao().link(FileTagCrossRef(fileId = uuid, tagId = it)) }
            notifyChanged()
            safeEntity
        }

    /** Display resolution (rotation applied) of a plaintext video file; null on failure. */
    private fun videoResolutionOf(f: File): Pair<Int, Int>? {
        val r = android.media.MediaMetadataRetriever()
        return try {
            r.setDataSource(f.absolutePath)
            resolutionFrom(r)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** Display resolution of an image or video content uri (before it is encrypted); null if unknown. */
    private fun resolutionOf(uri: Uri, mime: String): Pair<Int, Int>? = runCatching {
        when {
            isImage(mime) -> {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
                if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
            }
            isVideo(mime) -> {
                val r = android.media.MediaMetadataRetriever()
                try { r.setDataSource(context, uri); resolutionFrom(r) } finally { runCatching { r.release() } }
            }
            else -> null
        }
    }.getOrNull()

    private fun resolutionFrom(r: android.media.MediaMetadataRetriever): Pair<Int, Int>? {
        val w = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (w <= 0 || h <= 0) return null
        return if (rot == 90 || rot == 270) h to w else w to h
    }

    /**
     * Store resolutions discovered after import (backfill for files imported before it was
     * recorded). One change notification for the whole batch, so the grid re-queries once.
     */
    suspend fun setResolutions(values: Map<String, Pair<Int, Int>>) = withContext(Dispatchers.IO) {
        if (values.isEmpty()) return@withContext
        values.forEach { (id, wh) -> db.fileDao().setResolution(id, wh.first, wh.second) }
        notifyChanged()
    }

    /** Best-effort media duration (ms) read directly from a content uri; null on failure. */
    private fun durationOf(uri: Uri): Long? {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.takeIf { it > 0 }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    // --- Tags ---
    suspend fun setTags(fileId: String, tagNames: List<String>) = withContext(Dispatchers.IO) {
        val dao = db.tagDao()
        dao.clearTagsOf(fileId)
        for (raw in tagNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
            val existing = dao.byName(raw)
            val tagId = existing?.id ?: dao.insert(TagEntity(name = raw)).let {
                if (it == -1L) dao.byName(raw)!!.id else it
            }
            dao.link(FileTagCrossRef(fileId = fileId, tagId = tagId))
        }
        // Tags are a reusable library: keep unlinked ones available for other files. They are
        // removed only when the user explicitly deletes them (see deleteTag).
        notifyChanged()
    }

    suspend fun toggleFavorite(fileId: String, fav: Boolean) = withContext(Dispatchers.IO) {
        db.fileDao().setFavorite(fileId, fav); notifyChanged()
    }

    /** All file ids only (cheap) — for building a random queue over large libraries. */
    suspend fun allFileIds(): List<String> = withContext(Dispatchers.IO) { db.fileDao().allIds() }

    /** One-shot snapshot of every file entity (no tags). Used by the duplicate scanner. */
    suspend fun allFilesSnapshot(): List<FileEntity> = withContext(Dispatchers.IO) {
        db.fileDao().allWithTags().first().map { it.file }
    }

    // --- Thumbnail sealing (persistent cover cache) ---
    /** Encrypt small thumbnail bytes with the session DEK (safe to persist on disk). */
    fun sealThumb(bytes: ByteArray): ByteArray = dek.encrypt(bytes, THUMB_AAD)
    /** Decrypt a sealed thumbnail; null on any failure (stale/locked). */
    fun openThumb(sealed: ByteArray): ByteArray? = runCatching { dek.decrypt(sealed, THUMB_AAD) }.getOrNull()
    val hasKey: Boolean get() = session.isUnlocked

    /** Set (or clear, with a blank value) the source link stored on a file. */
    suspend fun setSourceUrl(fileId: String, url: String?) = withContext(Dispatchers.IO) {
        db.fileDao().setSourceUrl(fileId, url?.trim()?.ifEmpty { null }); notifyChanged()
    }

    suspend fun renameFile(fileId: String, newName: String) = withContext(Dispatchers.IO) {
        db.fileDao().rename(fileId, newName.trim()); notifyChanged()
    }

    suspend fun setTagAlias(tagName: String, alias: String?) = withContext(Dispatchers.IO) {
        db.tagDao().setAlias(tagName, alias?.trim()?.ifEmpty { null }); notifyChanged()
    }

    /** Add [tagNames] to a file without clearing its existing tags (used for batch tagging). */
    suspend fun addTags(fileId: String, tagNames: List<String>) = withContext(Dispatchers.IO) {
        val dao = db.tagDao()
        for (raw in tagNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
            val existing = dao.byName(raw)
            val tagId = existing?.id ?: dao.insert(TagEntity(name = raw)).let {
                if (it == -1L) dao.byName(raw)!!.id else it
            }
            dao.link(FileTagCrossRef(fileId = fileId, tagId = tagId))
        }
        notifyChanged()
    }

    suspend fun createTag(name: String, alias: String? = null) = withContext(Dispatchers.IO) {
        val n = name.trim()
        if (n.isEmpty()) return@withContext
        val a = alias?.trim()?.ifEmpty { null }
        val id = db.tagDao().insert(TagEntity(name = n, alias = a))
        // Tag already existed (insert ignored): just refresh its alias if one was provided.
        if (id == -1L && a != null) db.tagDao().setAlias(n, a)
        notifyChanged()
    }

    /** Set a tag's colour (null = automatic). */
    suspend fun setTagColor(tagId: Long, color: Int?) = withContext(Dispatchers.IO) {
        db.tagDao().setColor(tagId, color); notifyChanged()
    }

    suspend fun setTagColorByName(name: String, color: Int?) = withContext(Dispatchers.IO) {
        db.tagDao().setColorByName(name.trim(), color); notifyChanged()
    }

    suspend fun renameTag(tagId: Long, newName: String) = withContext(Dispatchers.IO) {
        db.tagDao().rename(tagId, newName.trim()); notifyChanged()
    }

    suspend fun deleteTag(tagId: Long) = withContext(Dispatchers.IO) {
        db.tagDao().unlinkAll(tagId)
        db.tagDao().deleteById(tagId)
        notifyChanged()
    }

    // --- Read / open ---
    suspend fun fileById(id: String): FileEntity? = withContext(Dispatchers.IO) {
        db.fileDao().byId(id)
    }

    suspend fun tagNamesOf(fileId: String): List<String> = withContext(Dispatchers.IO) {
        db.fileDao().withTagsById(fileId)?.tags?.map { it.name } ?: emptyList()
    }

    suspend fun decryptBytes(file: FileEntity): ByteArray = withContext(Dispatchers.IO) {
        val out = ByteArrayOutputStream()
        FileCrypto.decryptingStream(file.wrappedKeyset, dek, file.id, blobs.blob(file.id)).use {
            it.copyTo(out)
        }
        out.toByteArray()
    }

    fun seekableChannel(file: FileEntity): SeekableByteChannel =
        FileCrypto.seekableDecryptingChannel(file.wrappedKeyset, dek, file.id, blobs.blob(file.id))

    suspend fun export(file: FileEntity, dest: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(dest)!!.use { out ->
            FileCrypto.decryptingStream(file.wrappedKeyset, dek, file.id, blobs.blob(file.id)).use {
                it.copyTo(out)
            }
        }
    }

    /**
     * Decrypt the file back into shared storage (MediaStore) so it reappears in the gallery.
     * The exact original folder isn't recorded, so images go to Pictures/Cripta, videos to
     * Movies/Cripta, everything else to Download/Cripta. Returns the new MediaStore uri.
     * Note: this intentionally writes plaintext to shared storage (user-requested restore).
     */
    suspend fun restoreToGallery(file: FileEntity): android.net.Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val (collection, relPath) = when {
            isImage(file.mimeType) ->
                android.provider.MediaStore.Images.Media.getContentUri(
                    android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
                ) to "${android.os.Environment.DIRECTORY_PICTURES}/Cripta"
            isVideo(file.mimeType) ->
                android.provider.MediaStore.Video.Media.getContentUri(
                    android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
                ) to "${android.os.Environment.DIRECTORY_MOVIES}/Cripta"
            else ->
                android.provider.MediaStore.Downloads.getContentUri(
                    android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
                ) to "${android.os.Environment.DIRECTORY_DOWNLOADS}/Cripta"
        }
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file.originalName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, file.mimeType)
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return@withContext null
        resolver.openOutputStream(uri)!!.use { out ->
            FileCrypto.decryptingStream(file.wrappedKeyset, dek, file.id, blobs.blob(file.id)).use {
                it.copyTo(out)
            }
        }
        values.clear()
        values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri
    }

    // --- Secure delete (crypto-shred) ---
    suspend fun secureDelete(fileId: String) = withContext(Dispatchers.IO) {
        val f = db.fileDao().byId(fileId) ?: return@withContext
        db.fileDao().delete(f)      // destroys the wrapped keyset -> ciphertext unrecoverable
        blobs.shred(fileId)         // best-effort overwrite + delete of the blob
        // Deleting a file must not remove its tags from the library: they stay available for
        // other files. Tags are only removed via an explicit deleteTag.
        notifyChanged()
    }

    /**
     * Resolve a duplicate set by keeping [keepId] and crypto-shredding [removeIds], without losing
     * the organisation carried by the removed copies: their tags are added to the kept file, a
     * favorite mark carries over, and their source link is copied when the kept file has none.
     * Returns the names of the tags that were newly added to the kept file.
     */
    suspend fun mergeDuplicates(keepId: String, removeIds: List<String>): List<String> = withContext(Dispatchers.IO) {
        val keep = db.fileDao().withTagsById(keepId) ?: return@withContext emptyList()
        val keepTags = keep.tags.map { it.name }.toSet()
        val moved = LinkedHashSet<String>()
        var favorite = keep.file.isFavorite
        var link = keep.file.sourceUrl?.takeIf { it.isNotBlank() }
        for (id in removeIds.filter { it != keepId }.distinct()) {
            val other = db.fileDao().withTagsById(id) ?: continue
            other.tags.map { it.name }.filterNot { it in keepTags }.forEach { moved += it }
            favorite = favorite || other.file.isFavorite
            if (link == null) link = other.file.sourceUrl?.takeIf { it.isNotBlank() }
        }
        if (moved.isNotEmpty()) addTags(keepId, moved.toList())
        if (favorite != keep.file.isFavorite) db.fileDao().setFavorite(keepId, favorite)
        if (link != keep.file.sourceUrl) db.fileDao().setSourceUrl(keepId, link)
        for (id in removeIds.filter { it != keepId }.distinct()) deleteOrTrash(id)
        notifyChanged()
        moved.toList()
    }

    /** Tag names per file id (one query per file; meant for small sets like duplicate groups). */
    suspend fun tagNamesOf(fileIds: Collection<String>): Map<String, List<String>> = withContext(Dispatchers.IO) {
        fileIds.associateWith { id -> db.fileDao().withTagsById(id)?.tags?.map { it.name } ?: emptyList() }
    }

    // --- Trash (optional; see Settings.trashEnabled) ---

    fun trashed(): Flow<List<FileWithTags>> = db.fileDao().trashedWithTags()

    /** Move a file to the trash (restorable). Its key is kept until the trash is emptied. */
    suspend fun trash(fileId: String) = withContext(Dispatchers.IO) {
        db.fileDao().setDeletedAt(fileId, now()); notifyChanged()
    }

    /** Bring a trashed file back; to the root if its folder no longer exists. */
    suspend fun restore(fileId: String) = withContext(Dispatchers.IO) {
        val f = db.fileDao().byId(fileId) ?: return@withContext
        if (f.folderId != null && db.folderDao().byId(f.folderId) == null) db.fileDao().move(fileId, null)
        db.fileDao().setDeletedAt(fileId, null)
        notifyChanged()
    }

    /**
     * Delete honouring the user's trash setting: to the trash when enabled, otherwise crypto-shred
     * right away. Returns true when the file went to the trash.
     */
    suspend fun deleteOrTrash(fileId: String): Boolean {
        val useTrash = runCatching { settings.settingsOnce().trashEnabled }.getOrDefault(false)
        if (useTrash) trash(fileId) else secureDelete(fileId)
        return useTrash
    }

    /** Crypto-shred trashed files older than [days]; returns their ids (for cover eviction). */
    suspend fun purgeExpiredTrash(days: Int): List<String> = withContext(Dispatchers.IO) {
        val ids = db.fileDao().trashedBefore(now() - days * 86_400_000L)
        ids.forEach { secureDelete(it) }
        ids
    }

    // --- Playback resume ---

    /** Remember where playback stopped (null/0 = start over). No-op when unchanged. */
    suspend fun setPlaybackPos(fileId: String, posMs: Long?) = withContext(Dispatchers.IO) {
        val cur = db.fileDao().byId(fileId)?.playbackPosMs
        val v = posMs?.takeIf { it > 0 }
        if (cur != v) { db.fileDao().setPlaybackPos(fileId, v); notifyChanged() }
    }

    // --- "Already in the vault" checks ---

    /** A live file previously downloaded from [url], if any. */
    suspend fun fileBySourceUrl(url: String): FileEntity? = withContext(Dispatchers.IO) {
        url.trim().takeIf { it.isNotEmpty() }?.let { db.fileDao().bySourceUrl(it) }
    }

    /** Live files with exactly the same size as [file] (the only possible byte-identical copies). */
    suspend fun sameSizeAs(file: FileEntity): List<FileEntity> = withContext(Dispatchers.IO) {
        db.fileDao().sameSize(file.sizeBytes, file.id)
    }

    // --- Saved filters (encrypted in the DB with the rest of the vault) ---

    fun savedFilters(): Flow<List<com.cripta.app.data.db.SavedFilterEntity>> = db.savedFilterDao().all()

    suspend fun saveFilter(name: String, json: String) = withContext(Dispatchers.IO) {
        db.savedFilterDao().insert(com.cripta.app.data.db.SavedFilterEntity(name = name.trim(), json = json)); notifyChanged()
    }

    suspend fun deleteSavedFilter(id: Long) = withContext(Dispatchers.IO) {
        db.savedFilterDao().delete(id); notifyChanged()
    }

    // --- Folders / tags helpers ---

    /** Root-to-folder chain for [folderId] (empty when it doesn't exist). */
    suspend fun folderPath(folderId: Long): List<FolderEntity> = withContext(Dispatchers.IO) {
        val chain = ArrayDeque<FolderEntity>()
        var cur = db.folderDao().byId(folderId)
        var guard = 0
        while (cur != null && guard++ < 64) {
            chain.addFirst(cur)
            cur = cur.parentId?.let { db.folderDao().byId(it) }
        }
        chain.toList()
    }

    /** Link existing tags (by id) to a file, keeping its other tags. */
    suspend fun addTagIds(fileId: String, tagIds: Collection<Long>) = withContext(Dispatchers.IO) {
        tagIds.forEach { db.tagDao().link(FileTagCrossRef(fileId = fileId, tagId = it)) }
        if (tagIds.isNotEmpty()) notifyChanged()
    }

    /** Best-effort deletion of the original picked files (SAF documents). */
    suspend fun deleteOriginals(uris: List<Uri>) = withContext(Dispatchers.IO) {
        uris.forEach { uri ->
            runCatching {
                android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
            }.onFailure {
                runCatching {
                    androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.delete()
                }
            }
        }
    }

    private fun queryNameSize(uri: Uri): Pair<String, Long> {
        var name = "file"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (nameIdx >= 0) c.getString(nameIdx)?.let { name = it }
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
            }
        }
        return name to size
    }

    // --- Encrypted backup (cross-device, passphrase-derived key) ---

    suspend fun exportBackup(dest: Uri, passphrase: CharArray): Int = withContext(Dispatchers.IO) {
        val folders = db.folderDao().all().first()
        val tags = db.tagDao().all().first()
        val files = db.fileDao().allWithTags().first()

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveBackupKey(passphrase, salt)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))

        val manifest = org.json.JSONObject().apply {
            put("v", 1)
            put("folders", org.json.JSONArray().apply {
                folders.forEach { put(org.json.JSONObject().put("id", it.id).put("name", it.name).put("parentId", it.parentId ?: org.json.JSONObject.NULL).put("createdAt", it.createdAt)) }
            })
            put("tags", org.json.JSONArray().apply {
                tags.forEach {
                    put(org.json.JSONObject().put("name", it.name).put("alias", it.alias ?: org.json.JSONObject.NULL)
                        .put("color", it.color ?: org.json.JSONObject.NULL))
                }
            })
            put("files", org.json.JSONArray().apply {
                files.forEach { fwt ->
                    put(org.json.JSONObject()
                        .put("name", fwt.file.originalName).put("mime", fwt.file.mimeType)
                        .put("favorite", fwt.file.isFavorite).put("createdAt", fwt.file.createdAt)
                        .put("folderId", fwt.file.folderId ?: org.json.JSONObject.NULL)
                        .put("durationMs", fwt.file.durationMs ?: org.json.JSONObject.NULL)
                        .put("tags", org.json.JSONArray().apply { fwt.tags.forEach { put(it.name) } }))
                }
            })
        }.toString().toByteArray()

        context.contentResolver.openOutputStream(dest)!!.use { raw ->
            raw.write(BACKUP_MAGIC); raw.write(salt); raw.write(iv)
            javax.crypto.CipherOutputStream(raw, cipher).use { cos ->
                val out = java.io.DataOutputStream(cos)
                out.writeInt(manifest.size); out.write(manifest)
                files.forEach { fwt ->
                    val bytes = decryptBytes(fwt.file)
                    out.writeLong(bytes.size.toLong()); out.write(bytes)
                }
                out.flush()
            }
        }
        files.size
    }

    suspend fun importBackup(src: Uri, passphrase: CharArray): Int = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(src)!!.use { raw ->
            val magic = ByteArray(BACKUP_MAGIC.size); raw.read(magic)
            require(magic.contentEquals(BACKUP_MAGIC)) { "Formato non valido" }
            val salt = ByteArray(16); raw.read(salt)
            val iv = ByteArray(12); raw.read(iv)
            val key = deriveBackupKey(passphrase, salt)
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
            javax.crypto.CipherInputStream(raw, cipher).use { cis ->
                val inp = java.io.DataInputStream(cis)
                val mLen = inp.readInt()
                require(mLen in 1..50_000_000) { "Passphrase errata o file corrotto" }
                val mBytes = ByteArray(mLen); inp.readFully(mBytes)
                val manifest = org.json.JSONObject(String(mBytes))

                // Folders: remap old ids to new, inserting parents before children.
                val fArr = manifest.getJSONArray("folders")
                val pending = (0 until fArr.length()).map { fArr.getJSONObject(it) }.toMutableList()
                val idMap = HashMap<Long, Long>()
                var guard = 0
                while (pending.isNotEmpty() && guard < 10000) {
                    guard++
                    val it = pending.iterator()
                    var progressed = false
                    while (it.hasNext()) {
                        val o = it.next()
                        val oldId = o.getLong("id")
                        val parentOld = if (o.isNull("parentId")) null else o.getLong("parentId")
                        val newParent = if (parentOld == null) null else idMap[parentOld]
                        if (parentOld != null && newParent == null) continue // parent not yet inserted
                        val newId = db.folderDao().insert(FolderEntity(name = o.getString("name"), parentId = newParent, createdAt = o.optLong("createdAt", now())))
                        idMap[oldId] = newId; it.remove(); progressed = true
                    }
                    if (!progressed) break
                }

                // Tags with alias.
                val tArr = manifest.getJSONArray("tags")
                for (i in 0 until tArr.length()) {
                    val o = tArr.getJSONObject(i)
                    val name = o.getString("name")
                    db.tagDao().insert(TagEntity(name = name))
                    if (!o.isNull("alias")) db.tagDao().setAlias(name, o.getString("alias"))
                    // Optional (older backups have no colour).
                    if (o.has("color") && !o.isNull("color")) db.tagDao().setColorByName(name, o.getInt("color"))
                }

                // Files: order matches the blob stream order.
                val fileArr = manifest.getJSONArray("files")
                for (i in 0 until fileArr.length()) {
                    val o = fileArr.getJSONObject(i)
                    val len = inp.readLong()
                    require(len in 0..2_000_000_000L) { "File corrotto" }
                    val bytes = ByteArray(len.toInt()); inp.readFully(bytes)
                    val uuid = UUID.randomUUID().toString()
                    val wrapped = FileCrypto.createWrappedFileKeyset(dek)
                    blobs.blob(uuid).outputStream().use { out ->
                        FileCrypto.encryptingStream(wrapped, dek, uuid, out).use { it.write(bytes) }
                    }
                    val folderOld = if (o.isNull("folderId")) null else o.getLong("folderId")
                    val entity = FileEntity(
                        id = uuid, originalName = o.getString("name"), mimeType = o.getString("mime"),
                        sizeBytes = bytes.size.toLong(), folderId = folderOld?.let { idMap[it] },
                        isFavorite = o.optBoolean("favorite", false),
                        createdAt = o.optLong("createdAt", now()), importedAt = now(), wrappedKeyset = wrapped,
                        durationMs = if (o.isNull("durationMs")) null else o.optLong("durationMs").takeIf { it > 0 },
                    )
                    db.fileDao().insert(entity)
                    val tagNames = o.getJSONArray("tags")
                    val names = (0 until tagNames.length()).map { tagNames.getString(it) }
                    if (names.isNotEmpty()) setTags(uuid, names)
                }
                notifyChanged()
                return@withContext fileArr.length()
            }
        }
    }

    private fun deriveBackupKey(passphrase: CharArray, salt: ByteArray): javax.crypto.SecretKey {
        val spec = javax.crypto.spec.PBEKeySpec(passphrase, salt, 210_000, 256)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return javax.crypto.spec.SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun now() = System.currentTimeMillis()

    // --- Notes ---
    suspend fun createNote(folderId: Long?, name: String, text: String): FileEntity = withContext(Dispatchers.IO) {
        val uuid = UUID.randomUUID().toString()
        val wrapped = FileCrypto.createWrappedFileKeyset(dek)
        val bytes = text.toByteArray()
        blobs.blob(uuid).outputStream().use { out ->
            FileCrypto.encryptingStream(wrapped, dek, uuid, out).use { it.write(bytes) }
        }
        val e = FileEntity(
            id = uuid, originalName = name.ifBlank { "Nota" }, mimeType = MIME_NOTE,
            sizeBytes = bytes.size.toLong(), folderId = folderId,
            createdAt = now(), importedAt = now(), wrappedKeyset = wrapped, sortWeight = now(),
        )
        db.fileDao().insert(e)
        notifyChanged()
        e
    }

    suspend fun updateNote(fileId: String, name: String, text: String) = withContext(Dispatchers.IO) {
        val f = db.fileDao().byId(fileId) ?: return@withContext
        val bytes = text.toByteArray()
        blobs.blob(fileId).outputStream().use { out ->
            FileCrypto.encryptingStream(f.wrappedKeyset, dek, fileId, out).use { it.write(bytes) }
        }
        db.fileDao().update(f.copy(originalName = name.ifBlank { f.originalName }, sizeBytes = bytes.size.toLong()))
        notifyChanged()
    }

    suspend fun noteText(file: FileEntity): String = String(decryptBytes(file))

    companion object {
        private val BACKUP_MAGIC = "CRIPTABK".toByteArray()
        const val MIME_NOTE = "text/cripta-note"
        fun isImage(mime: String) = mime.startsWith("image/")
        fun isVideo(mime: String) = mime.startsWith("video/")
        fun isPlayable(mime: String) = isVideo(mime) || mime.startsWith("audio/")
        fun isNote(mime: String) = mime == MIME_NOTE
        fun isPdf(mime: String) = mime == "application/pdf"
    }
}

/** Convenience for reading a file entity's blob file directly (used by the media source). */
fun BlobStore.fileFor(entity: FileEntity): File = blob(entity.id)
