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
    data class ConversionEvent(val originalId: String, val newId: String)
    private val _convertEvents =
        kotlinx.coroutines.flow.MutableSharedFlow<ConversionEvent>(extraBufferCapacity = 16)
    val convertEvents: kotlinx.coroutines.flow.SharedFlow<ConversionEvent> = _convertEvents
    private val _convertingIds = MutableStateFlow<Set<String>>(emptySet())
    /** File ids with a conversion in flight; drives the viewer's progress indicator. */
    val convertingIds: StateFlow<Set<String>> = _convertingIds
    fun setConverting(id: String, active: Boolean) {
        _convertingIds.value = if (active) _convertingIds.value + id else _convertingIds.value - id
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

    /** Recursively crypto-shred every file in the folder subtree, then delete the folders. */
    suspend fun deleteFolderRecursive(folderId: Long) = withContext(Dispatchers.IO) {
        // Gather subtree by walking children via a snapshot query set.
        val toVisit = ArrayDeque<Long>().apply { add(folderId) }
        val subtree = mutableListOf<Long>()
        val childrenMap = snapshotChildren()
        while (toVisit.isNotEmpty()) {
            val id = toVisit.removeFirst()
            subtree.add(id)
            childrenMap[id]?.forEach { toVisit.add(it) }
        }
        for (fid in subtree) {
            db.fileDao().idsInFolder(fid).forEach { secureDelete(it) }
        }
        // delete deepest first
        for (fid in subtree.reversed()) {
            db.folderDao().byId(fid)?.let { db.folderDao().delete(it) }
        }
        notifyChanged()
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
    suspend fun import(uri: Uri, folderId: Long?): FileEntity = withContext(Dispatchers.IO) {
        val (name, _) = queryNameSize(uri)
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        // Read media duration from the still-plaintext source before it is encrypted.
        val duration = if (isPlayable(mime)) durationOf(uri) else null
        val uuid = UUID.randomUUID().toString()
        val wrappedKeyset = FileCrypto.createWrappedFileKeyset(dek)
        val blob = blobs.blob(uuid)
        // Count the actual plaintext bytes streamed in, rather than trusting OpenableColumns.SIZE
        // (some providers report it wrong). The exact length is what the seekable player needs.
        val written = context.contentResolver.openInputStream(uri)!!.use { input ->
            blob.outputStream().use { out ->
                FileCrypto.encryptingStream(wrappedKeyset, dek, uuid, out).use { enc ->
                    input.copyTo(enc)
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
    suspend fun importConvertedMp4(original: FileEntity, mp4: File): FileEntity = withContext(Dispatchers.IO) {
        val uuid = UUID.randomUUID().toString()
        val wrapped = FileCrypto.createWrappedFileKeyset(dek)
        val written = mp4.inputStream().use { input ->
            blobs.blob(uuid).outputStream().use { out ->
                FileCrypto.encryptingStream(wrapped, dek, uuid, out).use { input.copyTo(it) }
            }
        }
        val baseName = original.originalName.substringBeforeLast('.', original.originalName)
        val entity = FileEntity(
            id = uuid,
            originalName = "$baseName.mp4",
            mimeType = "video/mp4",
            sizeBytes = written,
            folderId = original.folderId,
            isFavorite = original.isFavorite,
            createdAt = original.createdAt,
            importedAt = now(),
            wrappedKeyset = wrapped,
            durationMs = original.durationMs,
            sortWeight = now(),
        )
        db.fileDao().insert(entity)
        // Carry over the original's tags.
        db.fileDao().withTagsById(original.id)?.tags?.forEach {
            db.tagDao().link(FileTagCrossRef(fileId = uuid, tagId = it.id))
        }
        notifyChanged()
        entity
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

    // --- Thumbnail sealing (persistent cover cache) ---
    /** Encrypt small thumbnail bytes with the session DEK (safe to persist on disk). */
    fun sealThumb(bytes: ByteArray): ByteArray = dek.encrypt(bytes, THUMB_AAD)
    /** Decrypt a sealed thumbnail; null on any failure (stale/locked). */
    fun openThumb(sealed: ByteArray): ByteArray? = runCatching { dek.decrypt(sealed, THUMB_AAD) }.getOrNull()
    val hasKey: Boolean get() = session.isUnlocked

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
                tags.forEach { put(org.json.JSONObject().put("name", it.name).put("alias", it.alias ?: org.json.JSONObject.NULL)) }
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
