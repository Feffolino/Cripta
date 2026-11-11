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
    /** Emoji of older vaults, shown in place of the folder icon until turned into an [icon]. */
    val emoji: String? = null,
    /** Set when the folder is in the trash (with its content); null = live. */
    val deletedAt: Long? = null,
    val isFavorite: Boolean = false,
    /** Also excludes descendants from every random queue. */
    val excludedFromRandom: Boolean = false,
    /** Null preserves the default until a sort is chosen for this folder. */
    val sortKey: String? = null,
    val sortAscending: Boolean = false,
    /** File chosen as the folder's cover (front of its stack); null = the newest file. */
    val coverFileId: String? = null,
    /** Icon drawn in place of the folder symbol (a [com.cripta.app.ui.tagicons.TagIcons] name). */
    val icon: String? = null,
)

/**
 * An encrypted file. [id] is the random blob UUID (also the ciphertext filename).
 * [folderId] null means it lives at the vault root. [wrappedKeyset] is the per-file
 * StreamingAead keyset wrapped by the DEK.
 */
@Entity(
    tableName = "files",
    // deletedAt+importedAt: every live list ("deletedAt IS NULL ORDER BY importedAt"); sizeBytes and
    // sourceUrl: the "already in the vault" checks run on each import and download.
    indices = [Index("folderId"), Index("isFavorite"), Index("deletedAt", "importedAt"), Index("sizeBytes"), Index("sourceUrl")]
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
    /** Short acronym shown as a badge on thumbnails (older vaults: also an emoji). */
    val alias: String? = null,
    /** User-defined position for the Custom tag order (ascending = first). */
    val orderIndex: Int = 0,
    /** User-chosen colour (ARGB); null = automatic colour from the name. */
    val color: Int? = null,
    /** Pinned tags are always offered first in the tag pickers, in a fixed order. */
    val pinned: Boolean = false,
    /** When the tag was last assigned to a file (epoch ms), for the "Recenti" row; null = never. */
    val lastUsedAt: Long? = null,
    /** Icon shown with the tag (a [com.cripta.app.ui.tagicons.TagIcons] name); null = none. */
    val icon: String? = null,
    /** Shown on covers; off, the tag stays in the filters and the details only. */
    @androidx.room.ColumnInfo(defaultValue = "1") val onCovers: Boolean = true,
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
    /** How long the analysis took (ms). */
    val ms: Long = 0,
    /**
     * Numbers for "Statistiche": "sharp=a,b,c,d,e,f" (faces per sharpness band, see
     * [com.cripta.app.media.face.FaceStats]), "dup=n" (repeated frames), "extra=n" (frames added near a face).
     */
    val stats: String = "",
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
    /** Every moment it shows in (ms, comma separated): the viewer's "momenti". */
    val times: String = "",
    /** Where the best face is in its picture, as fractions of it: "x,y,w,h" (empty for older scans). */
    val box: String = "",
    /** Each face of the track (see [com.cripta.app.media.face.FaceCodec]), to split it; empty for older scans. */
    val sightings: ByteArray = ByteArray(0),
)

/**
 * A face the user deleted: found again in that media ([fileId]) it is left out; with no media
 * ("Non è un volto") it is left out everywhere.
 */
/**
 * Two faces the user said are not one person ("Dividi il gruppo" in "Da sistemare"): never offered
 * together again. Stored once per pair, smaller id first.
 */
@Entity(tableName = "face_apart", primaryKeys = ["a", "b"], indices = [Index("b")])
data class FaceApartEntity(val a: Long, val b: Long)

@Entity(tableName = "face_ignored", indices = [Index("fileId")])
data class IgnoredFaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: String?,
    /** Its embeddings, as a track keeps them (see [com.cripta.app.media.face.EmbeddingCodec]). */
    val embeddings: ByteArray,
    val createdAt: Long,
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
    /** The user's notes on the person's profile. */
    val notes: String = "",
    /** Links on the person's profile, one per line. */
    val links: String = "",
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
    /** How alike they looked when the user said no: shown in "Statistiche" to judge the thresholds. */
    val similarity: Float = 0f,
)

/**
 * A merge of two people (automatic or by the user), remembered so it can be undone: [mergedId]
 * comes back with its name and the faces and media it brought.
 */
@Entity(tableName = "face_merges")
data class FaceMergeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keptId: Long,
    val mergedId: Long,
    val mergedName: String,
    val mergedFavorite: Boolean,
    /** Track ids and manually linked media ids it brought (comma separated). */
    val trackIds: String,
    val fileIds: String,
    val similarity: Float,
    val auto: Boolean,
    val createdAt: Long,
)

/**
 * A media's similarity fingerprint, kept so the next "simili" scan only decodes new media: one
 * dHash for a picture, one per sampled frame for a video, as little-endian longs. Empty [hashes]:
 * the media could not be read. [sizeBytes] and [version] guard against a stale row (another
 * content under the same id, or a fingerprint made another way).
 */
@Entity(tableName = "dup_prints")
data class DupPrintEntity(
    @PrimaryKey val fileId: String,
    val sizeBytes: Long,
    val version: Int,
    val hashes: ByteArray,
)

/**
 * Two media the user said are not alike ("Non sono simili"): the "simili" scan never puts them in
 * one group again. [a] < [b], file ids.
 */
@Entity(tableName = "dup_apart", primaryKeys = ["a", "b"], indices = [Index("b")])
data class DupApartEntity(val a: String, val b: String)
