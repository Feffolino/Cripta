package com.cripta.app.data.db

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

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
