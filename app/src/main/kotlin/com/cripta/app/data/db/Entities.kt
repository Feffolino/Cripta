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
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is FileEntity && other.id == id)

    override fun hashCode(): Int = id.hashCode()
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
