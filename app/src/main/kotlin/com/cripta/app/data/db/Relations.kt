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
