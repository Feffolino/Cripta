package com.cripta.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A logical folder in the vault tree. parentId == null means a top-level folder. */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val createdAt: Long,
)

/**
 * An encrypted file. [id] is the random blob UUID (also the ciphertext filename).
 * [folderId] null means it lives at the vault root. [wrappedKeyset] is the per-file
 * StreamingAead keyset wrapped by the DEK.
 */
@Entity(
    tableName = "files",
    indices = [Index("folderId"), Index("isFavorite")]
)
data class FileEntity(
    @PrimaryKey val id: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val folderId: Long? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long,
    val importedAt: Long,
    val wrappedKeyset: ByteArray,
    /** Media duration in milliseconds for video/audio; null for other types or unknown. */
    val durationMs: Long? = null,
) {
    // Include every display-affecting field so Compose/StateFlow detect changes such as
    // toggling isFavorite or renaming. wrappedKeyset is excluded on purpose: it's constant
    // for a given id and ByteArray reference-equality would break diffing.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileEntity) return false
        return id == other.id &&
            originalName == other.originalName &&
            mimeType == other.mimeType &&
            sizeBytes == other.sizeBytes &&
            folderId == other.folderId &&
            isFavorite == other.isFavorite &&
            createdAt == other.createdAt &&
            importedAt == other.importedAt &&
            durationMs == other.durationMs
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + originalName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + (folderId?.hashCode() ?: 0)
        result = 31 * result + isFavorite.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + importedAt.hashCode()
        result = 31 * result + (durationMs?.hashCode() ?: 0)
        return result
    }
}

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Short alias (emoji or acronym) shown as a badge on thumbnails. */
    val alias: String? = null,
)

@Entity(
    tableName = "file_tags",
    primaryKeys = ["fileId", "tagId"],
    indices = [Index("tagId")]
)
data class FileTagCrossRef(
    val fileId: String,
    val tagId: Long,
)
