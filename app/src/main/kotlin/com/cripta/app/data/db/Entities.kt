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
    /** Optional ARGB tint for the folder icon. */
    val color: Int? = null,
    /** Optional emoji shown in place of the folder icon. */
    val emoji: String? = null,
    /** Set when the folder is in the trash (with its content); null = live. */
    val deletedAt: Long? = null,
    val isFavorite: Boolean = false,
    /** Also excludes descendants from every random queue. */
    val excludedFromRandom: Boolean = false,
    /** Null preserves the default until a sort is chosen for this folder. */
    val sortKey: String? = null,
    val sortAscending: Boolean = false,
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
    /** User-assigned position for the Manual sort mode (ascending = higher priority / top). */
    val sortWeight: Long = 0,
    /** Source link for files downloaded in-app (shown in the Info dialog); null otherwise. */
    val sourceUrl: String? = null,
    /** Display width/height in px (rotation applied) of an image/video; null until known. */
    val width: Int? = null,
    val height: Int? = null,
    /** Where playback stopped last time (ms), for "resume"; null = from the start. */
    val playbackPosMs: Long? = null,
    /** Set when moved to the trash (epoch ms); null = live file. */
    val deletedAt: Long? = null,
    /** When playback was last left unfinished (epoch ms), for "Continua a guardare". */
    val lastPlayedAt: Long? = null,
    /** SHA-256 of the plaintext, computed while encrypting (instant "already in the vault" check). */
    val contentHash: String? = null,
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
            durationMs == other.durationMs &&
            sourceUrl == other.sourceUrl &&
            width == other.width &&
            height == other.height &&
            playbackPosMs == other.playbackPosMs &&
            deletedAt == other.deletedAt &&
            lastPlayedAt == other.lastPlayedAt
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
        result = 31 * result + (sourceUrl?.hashCode() ?: 0)
        result = 31 * result + (width ?: 0)
        result = 31 * result + (height ?: 0)
        result = 31 * result + (playbackPosMs?.hashCode() ?: 0)
        result = 31 * result + (deletedAt?.hashCode() ?: 0)
        result = 31 * result + (lastPlayedAt?.hashCode() ?: 0)
        return result
    }
}

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Short alias (emoji or acronym) shown as a badge on thumbnails. */
    val alias: String? = null,
    /** User-defined position for the Custom tag order (ascending = first). */
    val orderIndex: Int = 0,
    /** User-chosen colour (ARGB); null = automatic colour from the name. */
    val color: Int? = null,
    /** Pinned tags are always offered first in the tag pickers, in a fixed order. */
    val pinned: Boolean = false,
    /** When the tag was last assigned to a file (epoch ms), for the "Recenti" row; null = never. */
    val lastUsedAt: Long? = null,
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

/** A named filter combination the user saved as a shortcut (stored encrypted with the vault). */
@Entity(tableName = "saved_filters")
data class SavedFilterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Serialized [com.cripta.app.ui.vault.Filters] (JSON). */
    val json: String,
)

/**
 * A queued background job (link download or MP4 conversion) persisted so that, if Android kills the
 * app, waiting jobs are resumed at the next unlock instead of being lost. Stored encrypted.
 */
@Entity(tableName = "pending_jobs")
data class PendingJobEntity(
    @PrimaryKey val id: String,
    /** "download" or "convert". */
    val kind: String,
    /** JSON with the job's parameters. */
    val payload: String,
    val createdAt: Long,
)

/** Audio fingerprint of a video's first minutes ("head") or last minutes ("tail"), for intros and outros. */
@Entity(tableName = "audio_prints", primaryKeys = ["fileId", "part"])
data class AudioPrintEntity(
    val fileId: String,
    /** "head" or "tail". A video that cannot be analysed has one "head" row with status "skip". */
    val part: String,
    /** Time of the first hash in the video (ms). */
    val startMs: Long,
    /** Hashes encoded by [com.cripta.app.media.intro.PrintCodec]. */
    val hashes: ByteArray,
    /** "ok", or "skip:<reason>" (no audio track, undecodable…); a bare "skip" is from an older version. */
    val status: String,
    /** Compared with the rest of the vault already. */
    val matched: Boolean = false,
)

/** A video's intro or outro. */
@Entity(tableName = "media_segments", primaryKeys = ["fileId", "kind"])
data class MediaSegmentEntity(
    val fileId: String,
    /** "intro" or "outro". */
    val kind: String,
    val startMs: Long,
    val endMs: Long,
    /** "auto" (detected), "manual" (marked by the user) or "none" (the user said there is none). */
    val source: String,
    /**
     * The shared audio the detection found, when it differs from the bounds above or the user moved
     * them: the intro's distinctive sound, which "Applica a tutti" searches in the other videos.
     */
    val autoStartMs: Long? = null,
    val autoEndMs: Long? = null,
)

/**
 * A signature the user named, merged or gave a duration to: one intro (or outro) sound, known by one or
 * more reference sounds ([SignatureSoundEntity]). Videos whose segment has one of those sounds belong
 * to it; [startDeltaMs] / [endDeltaMs] move their segments' bounds (an intro longer than its sound).
 */
@Entity(tableName = "signatures")
data class SignatureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val name: String,
    val startDeltaMs: Long = 0,
    val endDeltaMs: Long = 0,
    val createdAt: Long,
)

/** One reference sound of a signature (merging adds the other signatures' sounds). */
@Entity(tableName = "signature_sounds", indices = [Index("signatureId")])
data class SignatureSoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val signatureId: Long,
    val hashes: ByteArray,
)

/**
 * "Applica a tutti i video con la stessa sigla": wherever this audio is found, the segment's start and
 * end move by these deltas (an intro whose distinctive sound covers only part of it).
 */
@Entity(tableName = "segment_rules")
data class SegmentRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val originFileId: String,
    val originName: String,
    val hashes: ByteArray,
    val startDeltaMs: Long,
    val endDeltaMs: Long,
    val createdAt: Long,
)

/** Audio the user said is no intro/outro in any video: stretches matching it never become segments. */
@Entity(tableName = "rejected_prints")
data class RejectedPrintEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    /** The video it was rejected on (it may be gone since) and where in it. */
    val originFileId: String,
    val originName: String,
    val startMs: Long,
    val endMs: Long,
    val hashes: ByteArray,
    val createdAt: Long,
)

/**
 * Face analysis of one media: what was read and found, so the user can tell why a media has no
 * faces. [status] is "ok" or "skip:<reason>"; [rejects] counts the faces set aside by reason
 * ("SMALL=2,PROFILE=1", names of [com.cripta.app.media.face.FaceReject]).
 */
@Entity(tableName = "face_scans")
data class FaceScanEntity(
    @PrimaryKey val fileId: String,
    val status: String,
    val framesRead: Int,
    val framesFailed: Int,
    /** Faces detected, usable or not. */
    val faces: Int,
    val rejects: String,
    /** Analyser version: media analysed by an older one are analysed again. */
    val version: Int,
    val scannedAt: Long,
    /** Its faces were placed among the people already. */
    val clustered: Boolean = false,
)

/** One person's face in one media (all the frames it shows in), with its best embeddings and picture. */
@Entity(tableName = "face_tracks", indices = [Index("fileId"), Index("personId")])
data class FaceTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: String,
    /** Bit n set: the face shows in sampled frame n (photos: frame 0). */
    val frames: Long,
    /** Where its best face is in the video (ms). */
    val timeMs: Long,
    val faceCount: Int,
    /** Width of its best face in the frame (px). */
    val width: Int,
    val quality: Float,
    /** Up to 3 normalised embeddings, 8-bit (see [com.cripta.app.media.face.EmbeddingCodec]). */
    val embeddings: ByteArray,
    /** The aligned face, JPEG. */
    val crop: ByteArray,
    val personId: Long? = null,
    /** "auto" (placed by similarity), "suggested" (waiting for the user), "manual" (the user's choice). */
    val state: String = "auto",
    /** Similarity to its person when placed (or suggested). */
    val similarity: Float = 0f,
)

/** A person: named or not; [ignored] = "non è una persona" (its faces, and similar new ones, are hidden). */
@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val ignored: Boolean = false,
    val coverTrackId: Long? = null,
    val createdAt: Long,
    /** Starred: listed first everywhere and shown on Home. */
    val favorite: Boolean = false,
)

/** A media the user added to a person by hand although none of its faces is theirs (or none was found). */
@Entity(tableName = "person_media", primaryKeys = ["personId", "fileId"], indices = [Index("fileId")])
data class PersonMediaEntity(
    val personId: Long,
    val fileId: String,
)

/** "Non è lui/lei": the track never goes to that person again. */
@Entity(tableName = "face_rejections", primaryKeys = ["trackId", "personId"])
data class FaceRejectionEntity(
    val trackId: Long,
    val personId: Long,
)