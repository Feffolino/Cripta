package com.cripta.app.data.db

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** Per-folder direct aggregate: how many files sit directly in [folderId] and their total size. */
data class FolderAgg(
    val folderId: Long,
    val cnt: Int,
    val bytes: Long,
)

/**
 * How much a tag is used: [files] and [folders] count the files in the vault (not in the trash),
 * the root counting as one folder; [links] counts every file it is on, the trash included.
 */
data class TagUsage(
    val tagId: Long,
    val files: Int,
    val folders: Int,
    val links: Int,
)

/** A file together with its tags, assembled by Room. */
data class FileWithTags(
    @Embedded val file: FileEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = FileTagCrossRef::class,
            parentColumn = "fileId",
            entityColumn = "tagId",
        )
    )
    val tags: List<TagEntity>,
)
