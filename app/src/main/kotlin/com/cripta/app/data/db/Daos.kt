package com.cripta.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Insert suspend fun insert(folder: FolderEntity): Long
    @Update suspend fun update(folder: FolderEntity)
    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)
    @Delete suspend fun delete(folder: FolderEntity)

    /** Live folders only (trashed ones are listed by [trashed]). */
    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY isFavorite DESC, name COLLATE NOCASE")
    fun all(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE ((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId) AND deletedAt IS NULL ORDER BY isFavorite DESC, name COLLATE NOCASE")
    fun childrenOf(parentId: Long?): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE deletedAt IS NOT NULL ORDER BY name COLLATE NOCASE")
    fun trashed(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE deletedAt IS NOT NULL")
    suspend fun trashedNow(): List<FolderEntity>

    @Query("UPDATE folders SET deletedAt = :at WHERE id IN (:ids)")
    suspend fun setDeletedAt(ids: List<Long>, at: Long?)

    @Query("UPDATE folders SET parentId = :parentId WHERE id = :id")
    suspend fun setParent(id: Long, parentId: Long?)

    @Query("SELECT COUNT(*) FROM folders WHERE parentId = :id")
    suspend fun childCount(id: Long): Int

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun byId(id: Long): FolderEntity?

    @Query("UPDATE folders SET color = :color, emoji = :emoji WHERE id = :id")
    suspend fun setStyle(id: Long, color: Int?, emoji: String?)

    @Query("UPDATE folders SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE folders SET excludedFromRandom = :excluded WHERE id = :id")
    suspend fun setRandomExcluded(id: Long, excluded: Boolean)

    @Query("UPDATE folders SET sortKey = :key, sortAscending = :ascending WHERE id = :id")
    suspend fun setSort(id: Long, key: String, ascending: Boolean)
}

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY orderIndex ASC, name COLLATE NOCASE")
    fun allByOrder(): Flow<List<TagEntity>>

    @Query("UPDATE tags SET orderIndex = :idx WHERE id = :id")
    suspend fun setOrder(id: Long, idx: Int)

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): TagEntity?

    @Query("UPDATE tags SET alias = :alias WHERE name = :name")
    suspend fun setAlias(name: String, alias: String?)

    @Query("UPDATE tags SET pinned = :pinned WHERE name = :name")
    suspend fun setPinned(name: String, pinned: Boolean)

    @Query("UPDATE tags SET lastUsedAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("DELETE FROM file_tags WHERE fileId = :fileId AND tagId = :tagId")
    suspend fun unlink(fileId: String, tagId: Long)

    @Query("UPDATE tags SET color = :color WHERE id = :id")
    suspend fun setColor(id: Long, color: Int?)

    @Query("UPDATE tags SET color = :color WHERE name = :name")
    suspend fun setColorByName(name: String, color: Int?)

    @Query("UPDATE tags SET name = :newName WHERE id = :id")
    suspend fun rename(id: Long, newName: String)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM file_tags WHERE tagId = :id")
    suspend fun unlinkAll(id: Long)

    @Query("DELETE FROM file_tags WHERE fileId = :fileId")
    suspend fun clearTagsOf(fileId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(ref: FileTagCrossRef)
}

@Dao
interface FileDao {
    @Insert suspend fun insert(file: FileEntity)
    @Update suspend fun update(file: FileEntity)
    @Delete suspend fun delete(file: FileEntity)

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun byId(id: String): FileEntity?

    @Transaction
    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun withTagsById(id: String): FileWithTags?

    @Transaction
    @Query("SELECT * FROM files WHERE deletedAt IS NULL ORDER BY importedAt DESC")
    fun allWithTags(): Flow<List<FileWithTags>>

    @Transaction
    @Query("SELECT * FROM files WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun trashedWithTags(): Flow<List<FileWithTags>>

    @Query("SELECT id FROM files WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun trashedBefore(before: Long): List<String>

    @Query("UPDATE files SET deletedAt = :at WHERE id = :id")
    suspend fun setDeletedAt(id: String, at: Long?)

    @Query("UPDATE files SET playbackPosMs = :pos, lastPlayedAt = :at WHERE id = :id")
    suspend fun setPlaybackPos(id: String, pos: Long?, at: Long?)

    @Query("UPDATE files SET contentHash = :hash WHERE id = :id")
    suspend fun setContentHash(id: String, hash: String)

    /** Newest covers of a folder (for its 2x2 mosaic). */
    @Query("SELECT * FROM files WHERE folderId = :folderId AND deletedAt IS NULL ORDER BY importedAt DESC LIMIT :limit")
    suspend fun latestInFolder(folderId: Long, limit: Int): List<FileEntity>

    @Query("SELECT * FROM files WHERE sourceUrl = :url AND deletedAt IS NULL LIMIT 1")
    suspend fun bySourceUrl(url: String): FileEntity?

    @Query("SELECT * FROM files WHERE sizeBytes = :size AND id != :exceptId AND deletedAt IS NULL")
    suspend fun sameSize(size: Long, exceptId: String): List<FileEntity>

    /** Live files whose name matches the LIKE [pattern] (escape char '\'), [exceptId] excluded. */
    @Query("SELECT * FROM files WHERE originalName LIKE :pattern ESCAPE '\\' AND id != :exceptId AND deletedAt IS NULL")
    suspend fun nameLike(pattern: String, exceptId: String): List<FileEntity>

    @Transaction
    @Query("SELECT * FROM files WHERE ((:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId) AND deletedAt IS NULL ORDER BY importedAt DESC")
    fun inFolderWithTags(folderId: Long?): Flow<List<FileWithTags>>

    @Query("SELECT id FROM files WHERE folderId = :folderId")
    suspend fun idsInFolder(folderId: Long): List<String>

    @Query("SELECT id FROM files WHERE folderId IN (:folderIds) AND deletedAt IS NOT NULL")
    suspend fun trashedIdsInFolders(folderIds: List<Long>): List<String>

    /** Trash the live files of these folders, all with the same timestamp as the folders. */
    @Query("UPDATE files SET deletedAt = :at WHERE folderId IN (:folderIds) AND deletedAt IS NULL")
    suspend fun trashInFolders(folderIds: List<Long>, at: Long)

    /** Restore the files trashed together with a folder (same timestamp). */
    @Query("UPDATE files SET deletedAt = NULL WHERE folderId IN (:folderIds) AND deletedAt = :at")
    suspend fun restoreInFolders(folderIds: List<Long>, at: Long)

    @Query("SELECT id FROM files WHERE deletedAt IS NULL")
    suspend fun allIds(): List<String>

    /** UNION (not UNION ALL) also terminates safely if an old folder tree contains a cycle. */
    @Query("""
        WITH RECURSIVE excluded(id) AS (
            SELECT id FROM folders WHERE excludedFromRandom = 1 OR deletedAt IS NOT NULL
            UNION
            SELECT f.id FROM folders f JOIN excluded e ON f.parentId = e.id
        )
        SELECT id FROM files WHERE deletedAt IS NULL
        AND (folderId IS NULL OR folderId NOT IN (SELECT id FROM excluded))
    """)
    suspend fun randomEligibleIds(): List<String>

    /** Direct (non-recursive) file count + total size per folder. Root files (null folder) are excluded. */
    @Query("SELECT folderId AS folderId, COUNT(*) AS cnt, COALESCE(SUM(sizeBytes), 0) AS bytes FROM files WHERE folderId IS NOT NULL AND deletedAt IS NULL GROUP BY folderId")
    fun folderAggregates(): Flow<List<FolderAgg>>

    @Query("UPDATE files SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("UPDATE files SET originalName = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE files SET folderId = :folderId WHERE id = :id")
    suspend fun move(id: String, folderId: Long?)

    @Query("UPDATE files SET sortWeight = :weight WHERE id = :id")
    suspend fun setWeight(id: String, weight: Long)

    @Query("UPDATE files SET width = :w, height = :h WHERE id = :id")
    suspend fun setResolution(id: String, w: Int, h: Int)

    @Query("UPDATE files SET sourceUrl = :url WHERE id = :id")
    suspend fun setSourceUrl(id: String, url: String?)
}

@Dao
interface SavedFilterDao {
    @Query("SELECT * FROM saved_filters ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<SavedFilterEntity>>

    @Insert suspend fun insert(f: SavedFilterEntity): Long

    @Query("DELETE FROM saved_filters WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PendingJobDao {
    @Query("SELECT * FROM pending_jobs ORDER BY createdAt")
    suspend fun all(): List<PendingJobEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(job: PendingJobEntity)

    @Query("DELETE FROM pending_jobs WHERE id = :id")
    suspend fun delete(id: String)
}

data class PrintSize(val fileId: String, val status: String, val bytes: Int)

@Dao
interface IntroDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPrint(p: AudioPrintEntity)

    @Query("SELECT * FROM audio_prints WHERE fileId = :fileId")
    suspend fun printsOf(fileId: String): List<AudioPrintEntity>

    /** Ids only: loading every print at once would hold all of them in memory. */
    @Query("SELECT fileId FROM audio_prints WHERE status = 'ok' AND part = :part")
    suspend fun printIds(part: String): List<String>

    /** How much of each video's start was fingerprinted (bytes of hashes), without loading them. */
    @Query("SELECT fileId, status, length(hashes) AS bytes FROM audio_prints WHERE part = 'head'")
    suspend fun headSizes(): List<PrintSize>

    @Query("DELETE FROM audio_prints WHERE fileId = :fileId")
    suspend fun clearPrints(fileId: String)

    @Query("DELETE FROM audio_prints")
    suspend fun deleteAllPrints()

    /** Bytes of all usable prints of [part] (4 per hash), to size the index. */
    @Query("SELECT COALESCE(SUM(length(hashes)), 0) FROM audio_prints WHERE part = :part AND status = 'ok'")
    suspend fun printBytes(part: String): Long

    @Query("DELETE FROM media_segments WHERE source = 'auto'")
    suspend fun deleteAutoSegments()

    @Query("DELETE FROM media_segments")
    suspend fun deleteAllSegments()

    @Query("DELETE FROM rejected_prints")
    suspend fun deleteAllRejected()

    @Query("SELECT DISTINCT fileId FROM audio_prints WHERE status = 'ok' AND matched = 0")
    suspend fun unmatchedIds(): List<String>

    /** At most a few hundred ids at a time (SQLite caps the parameters). */
    @Query("UPDATE audio_prints SET matched = 1 WHERE fileId IN (:ids)")
    suspend fun markMatched(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSegment(s: MediaSegmentEntity)

    @Query("SELECT * FROM media_segments WHERE fileId = :fileId")
    suspend fun segmentsOf(fileId: String): List<MediaSegmentEntity>

    @Query("SELECT * FROM media_segments WHERE fileId = :fileId")
    fun observeSegments(fileId: String): Flow<List<MediaSegmentEntity>>

    @Query("SELECT * FROM media_segments WHERE kind = :kind")
    suspend fun segmentsOfKind(kind: String): List<MediaSegmentEntity>

    @Query("SELECT * FROM media_segments WHERE source != 'none'")
    suspend fun allSegments(): List<MediaSegmentEntity>

    @Query("DELETE FROM media_segments WHERE fileId = :fileId AND kind = :kind")
    suspend fun deleteSegment(fileId: String, kind: String)

    @Query("DELETE FROM media_segments WHERE fileId = :fileId")
    suspend fun clearSegments(fileId: String)

    @Insert
    suspend fun addRule(r: SegmentRuleEntity): Long

    @Query("DELETE FROM segment_rules")
    suspend fun deleteAllRules()

    /** Oldest first: a video whose sound two signatures know belongs to the older one. */
    @Query("SELECT * FROM signatures WHERE kind = :kind ORDER BY createdAt")
    suspend fun signaturesOfKind(kind: String): List<SignatureEntity>

    @Query("SELECT * FROM signature_sounds WHERE signatureId IN (SELECT id FROM signatures WHERE kind = :kind)")
    suspend fun signatureSoundsOfKind(kind: String): List<SignatureSoundEntity>

    @Insert
    suspend fun addSignature(s: SignatureEntity): Long

    @Insert
    suspend fun addSignatureSound(s: SignatureSoundEntity): Long

    @Query("UPDATE signatures SET name = :name WHERE id = :id")
    suspend fun renameSignature(id: Long, name: String)

    @Query("UPDATE signatures SET startDeltaMs = :start, endDeltaMs = :end WHERE id = :id")
    suspend fun setSignatureDeltas(id: Long, start: Long, end: Long)

    @Query("UPDATE signature_sounds SET signatureId = :to WHERE signatureId = :from")
    suspend fun moveSignatureSounds(from: Long, to: Long)

    @Query("DELETE FROM signature_sounds WHERE signatureId = :id")
    suspend fun deleteSignatureSounds(id: Long)

    @Query("DELETE FROM signatures WHERE id = :id")
    suspend fun deleteSignature(id: Long)

    @Query("DELETE FROM signatures")
    suspend fun deleteAllSignatures()

    @Query("DELETE FROM signature_sounds")
    suspend fun deleteAllSignatureSounds()

    /** Newest first: a later correction of the same sound wins. */
    @Query("SELECT * FROM segment_rules WHERE kind = :kind ORDER BY createdAt DESC")
    suspend fun rulesOfKind(kind: String): List<SegmentRuleEntity>

    @Insert
    suspend fun addRejected(r: RejectedPrintEntity): Long

    @Query("SELECT * FROM rejected_prints ORDER BY createdAt DESC")
    fun observeRejected(): Flow<List<RejectedPrintEntity>>

    @Query("SELECT * FROM rejected_prints WHERE kind = :kind")
    suspend fun rejectedOfKind(kind: String): List<RejectedPrintEntity>

    @Query("SELECT * FROM rejected_prints WHERE id = :id")
    suspend fun rejectedById(id: Long): RejectedPrintEntity?

    @Query("DELETE FROM rejected_prints WHERE id = :id")
    suspend fun deleteRejected(id: Long)
}

/** A track without its picture, for the clustering. */
data class TrackRow(
    val id: Long, val fileId: String, val frames: Long, val quality: Float, val embeddings: ByteArray,
    val personId: Long?, val state: String, val similarity: Float,
)

/** A track for the lists: everything but its embeddings. */
data class TrackCard(
    val id: Long, val fileId: String, val timeMs: Long, val width: Int, val faceCount: Int,
    val personId: Long?, val state: String, val similarity: Float,
)

@Dao
interface FaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putScan(s: FaceScanEntity)

    @Query("SELECT * FROM face_scans WHERE fileId = :fileId")
    suspend fun scanOf(fileId: String): FaceScanEntity?

    @Query("SELECT * FROM face_scans")
    suspend fun allScans(): List<FaceScanEntity>

    @Query("SELECT * FROM face_scans")
    fun observeScans(): Flow<List<FaceScanEntity>>

    @Query("SELECT fileId FROM face_scans WHERE clustered = 0 AND status = 'ok'")
    suspend fun unclusteredIds(): List<String>

    @Query("UPDATE face_scans SET clustered = 1 WHERE fileId IN (:ids)")
    suspend fun markClustered(ids: List<String>)

    @Query("UPDATE face_scans SET clustered = 0")
    suspend fun markAllUnclustered()

    @Query("DELETE FROM face_scans WHERE fileId = :fileId")
    suspend fun deleteScan(fileId: String)

    @Insert
    suspend fun addTrack(t: FaceTrackEntity): Long

    @Query("SELECT id, fileId, frames, quality, embeddings, personId, state, similarity FROM face_tracks")
    suspend fun allTrackRows(): List<TrackRow>

    @Query("SELECT id, fileId, frames, quality, embeddings, personId, state, similarity FROM face_tracks WHERE fileId IN (:ids)")
    suspend fun trackRowsOf(ids: List<String>): List<TrackRow>

    @Query("SELECT id, fileId, frames, quality, embeddings, personId, state, similarity FROM face_tracks WHERE id = :id")
    suspend fun trackRow(id: Long): TrackRow?

    @Query("SELECT id, fileId, timeMs, width, faceCount, personId, state, similarity FROM face_tracks")
    fun observeCards(): Flow<List<TrackCard>>

    @Query("SELECT id, fileId, timeMs, width, faceCount, personId, state, similarity FROM face_tracks WHERE fileId = :fileId")
    fun observeCardsOf(fileId: String): Flow<List<TrackCard>>

    @Query("SELECT id, fileId, timeMs, width, faceCount, personId, state, similarity FROM face_tracks WHERE fileId = :fileId")
    suspend fun cardsOf(fileId: String): List<TrackCard>

    @Query("SELECT crop FROM face_tracks WHERE id = :id")
    suspend fun crop(id: Long): ByteArray?

    @Query("UPDATE face_tracks SET personId = :personId, state = :state, similarity = :similarity WHERE id = :id")
    suspend fun place(id: Long, personId: Long?, state: String, similarity: Float)

    @Query("UPDATE face_tracks SET personId = :to WHERE personId = :from")
    suspend fun moveTracks(from: Long, to: Long)

    @Query("UPDATE face_tracks SET state = 'manual' WHERE personId = :personId AND state = 'auto'")
    suspend fun lockTracks(personId: Long)

    @Query("UPDATE face_tracks SET personId = NULL, state = 'auto', similarity = 0 WHERE state != 'manual'")
    suspend fun unplaceAuto()

    @Query("DELETE FROM face_tracks WHERE fileId = :fileId")
    suspend fun deleteTracksOf(fileId: String)

    @Insert
    suspend fun addPerson(p: PersonEntity): Long

    @Query("SELECT * FROM persons")
    suspend fun persons(): List<PersonEntity>

    @Query("SELECT * FROM persons")
    fun observePersons(): Flow<List<PersonEntity>>

    @Query("UPDATE persons SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE persons SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE persons SET ignored = :ignored WHERE id = :id")
    suspend fun setIgnored(id: Long, ignored: Boolean)

    @Query("UPDATE persons SET coverTrackId = :trackId WHERE id = :id")
    suspend fun setCover(id: Long, trackId: Long?)

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun deletePerson(id: Long)

    /** People left with no face and no media: unnamed ones go (a named one stays, the user may add to it). */
    @Query("DELETE FROM persons WHERE name = '' AND ignored = 0 AND id NOT IN (SELECT personId FROM face_tracks WHERE personId IS NOT NULL AND state != 'suggested') AND id NOT IN (SELECT personId FROM person_media)")
    suspend fun deleteEmptyPersons()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addPersonMedia(m: PersonMediaEntity)

    @Query("DELETE FROM person_media WHERE personId = :personId AND fileId = :fileId")
    suspend fun removePersonMedia(personId: Long, fileId: String)

    @Query("UPDATE OR IGNORE person_media SET personId = :to WHERE personId = :from")
    suspend fun movePersonMedia(from: Long, to: Long)

    @Query("DELETE FROM person_media WHERE personId = :personId")
    suspend fun deletePersonMedia(personId: Long)

    @Query("SELECT * FROM person_media")
    fun observePersonMedia(): Flow<List<PersonMediaEntity>>

    @Query("SELECT * FROM person_media")
    suspend fun allPersonMedia(): List<PersonMediaEntity>

    /** Media showing every one of [ids]... one person at a time: the caller intersects. */
    @Query("SELECT fileId FROM face_tracks WHERE personId = :id AND state != 'suggested' UNION SELECT fileId FROM person_media WHERE personId = :id")
    suspend fun mediaOf(id: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addRejection(r: FaceRejectionEntity)

    @Query("SELECT * FROM face_rejections")
    suspend fun rejections(): List<FaceRejectionEntity>

    @Query("UPDATE OR IGNORE face_rejections SET personId = :to WHERE personId = :from")
    suspend fun moveRejections(from: Long, to: Long)

    @Query("DELETE FROM face_rejections WHERE personId = :personId")
    suspend fun deleteRejectionsOf(personId: Long)

    @Query("DELETE FROM face_scans")
    suspend fun deleteAllScans()

    @Query("DELETE FROM face_tracks")
    suspend fun deleteAllTracks()

    @Query("DELETE FROM persons")
    suspend fun deleteAllPersons()

    @Query("DELETE FROM person_media")
    suspend fun deleteAllPersonMedia()

    @Query("DELETE FROM face_rejections")
    suspend fun deleteAllRejections()
}