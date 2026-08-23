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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.channels.SeekableByteChannel
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val blobs: BlobStore,
) {
    private val db get() = session.requireDb()
    private val dek get() = session.requireDek()

    // --- Flows ---
    fun folders(parentId: Long?): Flow<List<FolderEntity>> = db.folderDao().childrenOf(parentId)
    fun allFolders(): Flow<List<FolderEntity>> = db.folderDao().all()
    fun files(folderId: Long?): Flow<List<FileWithTags>> = db.fileDao().inFolderWithTags(folderId)
    fun allFiles(): Flow<List<FileWithTags>> = db.fileDao().allWithTags()
    fun tags(): Flow<List<TagEntity>> = db.tagDao().all()

    // --- Folders ---
    suspend fun createFolder(name: String, parentId: Long?): Long = withContext(Dispatchers.IO) {
        db.folderDao().insert(FolderEntity(name = name.trim(), parentId = parentId, createdAt = now()))
    }

    suspend fun renameFolder(folder: FolderEntity, newName: String) = withContext(Dispatchers.IO) {
        db.folderDao().update(folder.copy(name = newName.trim()))
    }

    suspend fun moveFile(fileId: String, folderId: Long?) = withContext(Dispatchers.IO) {
        db.fileDao().move(fileId, folderId)
    }

    /** Recursively crypto-shred every file in the folder subtree, then delete the folders. */
    suspend fun deleteFolderRecursive(folderId: Long) = withContext(Dispatchers.IO) {
        val allFolders = db.folderDao().all()
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
        // Suppress unused warning for allFolders (kept for clarity of intent)
        allFolders
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
        val (name, size) = queryNameSize(uri)
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val uuid = UUID.randomUUID().toString()
        val wrappedKeyset = FileCrypto.createWrappedFileKeyset(dek)
        val blob = blobs.blob(uuid)
        context.contentResolver.openInputStream(uri)!!.use { input ->
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
            sizeBytes = size,
            folderId = folderId,
            createdAt = now(),
            importedAt = now(),
            wrappedKeyset = wrappedKeyset,
        )
        db.fileDao().insert(entity)
        entity
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
        dao.purgeUnusedTags()
    }

    suspend fun toggleFavorite(fileId: String, fav: Boolean) = withContext(Dispatchers.IO) {
        db.fileDao().setFavorite(fileId, fav)
    }

    // --- Read / open ---
    suspend fun fileById(id: String): FileEntity? = withContext(Dispatchers.IO) {
        db.fileDao().byId(id)
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

    // --- Secure delete (crypto-shred) ---
    suspend fun secureDelete(fileId: String) = withContext(Dispatchers.IO) {
        val f = db.fileDao().byId(fileId) ?: return@withContext
        db.fileDao().delete(f)      // destroys the wrapped keyset -> ciphertext unrecoverable
        blobs.shred(fileId)         // best-effort overwrite + delete of the blob
        db.tagDao().purgeUnusedTags()
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

    private fun now() = System.currentTimeMillis()

    companion object {
        fun isImage(mime: String) = mime.startsWith("image/")
        fun isVideo(mime: String) = mime.startsWith("video/")
        fun isPlayable(mime: String) = isVideo(mime) || mime.startsWith("audio/")
    }
}

/** Convenience for reading a file entity's blob file directly (used by the media source). */
fun BlobStore.fileFor(entity: FileEntity): File = blob(entity.id)
