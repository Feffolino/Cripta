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

    /** One-shot read of the live folders (a backup's snapshot, inside its transaction). */
    @Query("SELECT * FROM folders WHERE deletedAt IS NULL")
    suspend fun liveNow(): List<FolderEntity>

    @Query("UPDATE folders SET deletedAt = :at WHERE id IN (:ids)")
    suspend fun setDeletedAt(ids: List<Long>, at: Long?)

    @Query("UPDATE folders SET parentId = :parentId WHERE id = :id")
    suspend fun setParent(id: Long, parentId: Long?)

    @Query("SELECT COUNT(*) FROM folders WHERE parentId = :id")
    suspend fun childCount(id: Long): Int

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun byId(id: Long): FolderEntity?

    @Query("SELECT * FROM folders WHERE id IN (:ids)")
    suspend fun byIds(ids: Collection<Long>): List<FolderEntity>

    /** Colour and icon; an emoji left from older vaults is dropped with it. Returns rows changed. */
    @Query("UPDATE folders SET color = :color, icon = :icon, emoji = NULL WHERE id = :id")
    suspend fun setStyle(id: Long, color: Int?, icon: String?): Int

    /** Colour only (the icon untouched, an older vault's emoji kept). Returns rows changed. */
    @Query("UPDATE folders SET color = :color WHERE id = :id")
    suspend fun setColor(id: Long, color: Int?): Int

    /** A backup of an older vault: its emoji, turned into an icon after the restore. */
    @Query("UPDATE folders SET emoji = :emoji WHERE id = :id")
    suspend fun setEmoji(id: Long, emoji: String?)

    @Query("SELECT * FROM folders WHERE emoji IS NOT NULL")
    suspend fun withEmoji(): List<FolderEntity>

    @Query("UPDATE folders SET coverFileId = :fileId WHERE id = :id")
    suspend fun setCover(id: Long, fileId: String?): Int

    /** A file that left its folder (moved, or destroyed) is no longer the cover of any other folder. */
    @Query("UPDATE folders SET coverFileId = NULL WHERE coverFileId = :fileId AND (:keepFolderId IS NULL OR id != :keepFolderId)")
    suspend fun dropCoverOf(fileId: String, keepFolderId: Long?)

    @Query("UPDATE folders SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE folders SET excludedFromRandom = :excluded WHERE id = :id AND deletedAt IS NULL")
    suspend fun setRandomExcluded(id: Long, excluded: Boolean): Int

    /** Whether the folder or one of its ancestors is kept out of "Casuale" (as [FileDao.randomEligibleIds] counts it). */
    @Query("""
        WITH RECURSIVE up(id, parentId, excludedFromRandom) AS (
            SELECT id, parentId, excludedFromRandom FROM folders WHERE id = :id
            UNION
            SELECT f.id, f.parentId, f.excludedFromRandom FROM folders f JOIN up u ON f.id = u.parentId
        )
        SELECT EXISTS(SELECT 1 FROM up WHERE excludedFromRandom = 1 AND id != :id)
    """)
    suspend fun ancestorExcludedFromRandom(id: Long): Boolean

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

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun byId(id: Long): TagEntity?

    /** One-shot read of the whole library (name resolution inside a transaction). */
    @Query("SELECT * FROM tags")
    suspend fun allNow(): List<TagEntity>

    /** Every editable field of one tag, by id: the editor saves them together. Returns rows changed. */
    @Query("UPDATE tags SET name = :name, alias = :alias, color = :color, pinned = :pinned, icon = :icon, onCovers = :onCovers WHERE id = :id")
    suspend fun update(id: Long, name: String, alias: String?, color: Int?, pinned: Boolean, icon: String?, onCovers: Boolean): Int

    @Query("UPDATE tags SET onCovers = :onCovers WHERE name = :name")
    suspend fun setOnCovers(name: String, onCovers: Boolean)

    /** Every tag's use (see [TagUsage]); a tag on no file has zeros. */
    @Query("""
        SELECT t.id AS tagId,
            (SELECT COUNT(*) FROM file_tags l JOIN files f ON f.id = l.fileId
                WHERE l.tagId = t.id AND f.deletedAt IS NULL) AS files,
            (SELECT COUNT(DISTINCT IFNULL(f.folderId, -1)) FROM file_tags l JOIN files f ON f.id = l.fileId
                WHERE l.tagId = t.id AND f.deletedAt IS NULL) AS folders,
            (SELECT COUNT(*) FROM file_tags l WHERE l.tagId = t.id) AS links
        FROM tags t
    """)
    fun usage(): Flow<List<TagUsage>>

    /** Of [ids], the tags on no file at all (the trash included). */
    @Query("SELECT id FROM tags WHERE id IN (:ids) AND id NOT IN (SELECT tagId FROM file_tags)")
    suspend fun unusedAmong(ids: Collection<Long>): List<Long>

    /** Put [into] on every file that has [from] (files that have both keep one link). */
    @Query("INSERT OR IGNORE INTO file_tags (fileId, tagId) SELECT fileId, :into FROM file_tags WHERE tagId = :from")
    suspend fun copyLinks(from: Long, into: Long)

    @Query("SELECT id FROM tags WHERE id IN (:ids)")
    suspend fun existingIds(ids: Collection<Long>): List<Long>

    @Query("SELECT tagId FROM file_tags WHERE fileId = :fileId")
    suspend fun tagIdsOf(fileId: String): List<Long>

    @Query("SELECT * FROM file_tags WHERE fileId IN (:fileIds) AND tagId IN (:tagIds)")
    suspend fun linksAmong(fileIds: Collection<String>, tagIds: Collection<Long>): List<FileTagCrossRef>

    @Query("UPDATE tags SET alias = :alias WHERE name = :name")
    suspend fun setAlias(name: String, alias: String?)

    @Query("UPDATE tags SET icon = :icon WHERE name = :name")
    suspend fun setIcon(name: String, icon: String?)

    @Query("UPDATE tags SET pinned = :pinned WHERE name = :name")
    suspend fun setPinned(name: String, pinned: Boolean)

    @Query("UPDATE tags SET lastUsedAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("DELETE FROM file_tags WHERE fileId = :fileId AND tagId = :tagId")
    suspend fun unlink(fileId: String, tagId: Long): Int

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

    @Query("DELETE FROM file_tags WHERE tagId NOT IN (SELECT id FROM tags) OR fileId NOT IN (SELECT id FROM files)")
    suspend fun deleteOrphanLinks(): Int

    @Query("DELETE FROM file_tags WHERE fileId = :fileId")
    suspend fun clearTagsOf(fileId: String)

    /** -1 when the file already had the tag. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(ref: FileTagCrossRef): Long
}

@Dao
interface FileDao {
    @Insert suspend fun insert(file: FileEntity)
    @Update suspend fun update(file: FileEntity)
    @Delete suspend fun delete(file: FileEntity)

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun byId(id: String): FileEntity?

    @Query("SELECT id FROM files WHERE id IN (:ids)")
    suspend fun existingIds(ids: Collection<String>): List<String>

    @Transaction
    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun withTagsById(id: String): FileWithTags?

    @Transaction
    @Query("SELECT * FROM files WHERE deletedAt IS NULL ORDER BY importedAt DESC")
    fun allWithTags(): Flow<List<FileWithTags>>

    /** One-shot [allWithTags] (a backup's snapshot, inside its transaction). */
    @Transaction
    @Query("SELECT * FROM files WHERE deletedAt IS NULL ORDER BY importedAt DESC")
    suspend fun allWithTagsNow(): List<FileWithTags>

    /** A live file of [folderId] (null = the root) with this content: the same file restored twice. */
    @Query("SELECT id FROM files WHERE contentHash = :hash AND deletedAt IS NULL AND ((:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId)")
    suspend fun sameContentIn(hash: String, folderId: Long?): List<String>

    @Transaction
    @Query("SELECT * FROM files WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun trashedWithTags(): Flow<List<FileWithTags>>

    /** Every file row, live and trashed: what the vault keeps on disk (Impostazioni › Spazio). */
    @Query("SELECT * FROM files")
    suspend fun everyFile(): List<FileEntity>

    @Query("SELECT id FROM files WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun trashedBefore(before: Long): List<String>

    @Query("UPDATE files SET deletedAt = :at WHERE id = :id")
    suspend fun setDeletedAt(id: String, at: Long?)

    @Query("UPDATE files SET playbackPosMs = :pos, lastPlayedAt = :at WHERE id = :id")
    suspend fun setPlaybackPos(id: String, pos: Long?, at: Long?)

    @Query("UPDATE files SET contentHash = :hash WHERE id = :id")
    suspend fun setContentHash(id: String, hash: String)

    @Query("SELECT * FROM files WHERE id IN (:ids)")
    suspend fun byIds(ids: Collection<String>): List<FileEntity>

    /** Newest covers of a folder (for its 2x2 mosaic). */
    @Query("SELECT * FROM files WHERE folderId = :folderId AND deletedAt IS NULL ORDER BY importedAt DESC LIMIT :limit")
    suspend fun latestInFolder(folderId: Long, limit: Int): List<FileEntity>

    @Query("SELECT * FROM files WHERE sourceUrl = :url AND deletedAt IS NULL LIMIT 1")
    suspend fun bySourceUrl(url: String): FileEntity?

    @Query("SELECT * FROM files WHERE sizeBytes = :size AND id != :exceptId AND deletedAt IS NULL")
    suspend fun sameSize(size: Long, exceptId: String): List<FileEntity>

    /**
     * Id and name of the live files whose name matches the LIKE [pattern] (escape char '\'),
     * [exceptId] excluded. Names only: a pattern that cannot narrow much (a name with no ASCII
     * start) lists many rows, and only the few that match are read in full.
     */
    @Query("SELECT id, originalName FROM files WHERE originalName LIKE :pattern ESCAPE '\\' AND id != :exceptId AND deletedAt IS NULL")
    suspend fun namesLike(pattern: String, exceptId: String): List<FileName>

    @Transaction
    @Query("SELECT * FROM files WHERE ((:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId) AND deletedAt IS NULL ORDER BY importedAt DESC")
    fun inFolderWithTags(folderId: Long?): Flow<List<FileWithTags>>

    @Query("SELECT id FROM files WHERE folderId = :folderId")
    suspend fun idsInFolder(folderId: Long): List<String>

    /** Live files only: a folder's permanent delete must not touch what is already in the trash. */
    @Query("SELECT id FROM files WHERE folderId = :folderId AND deletedAt IS NULL")
    suspend fun liveIdsInFolder(folderId: Long): List<String>

    /** Take trashed files out of folders about to disappear: they stay in the trash, at the root. */
    @Query("UPDATE files SET folderId = NULL WHERE folderId IN (:folderIds) AND deletedAt IS NOT NULL")
    suspend fun detachTrashedFromFolders(folderIds: List<Long>)

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
    suspend fun move(id: String, folderId: Long?): Int

    @Query("UPDATE files SET sortWeight = :weight WHERE id = :id")
    suspend fun setWeight(id: String, weight: Long)

    @Query("UPDATE files SET width = :w, height = :h WHERE id = :id")
    suspend fun setResolution(id: String, w: Int, h: Int)

    @Query("UPDATE files SET sourceUrl = :url WHERE id = :id")
    suspend fun setSourceUrl(id: String, url: String?)

    /** A note rewritten: its name, type and size; its old hash would match the old text. */
    @Query("UPDATE files SET originalName = :name, mimeType = :mime, sizeBytes = :size, contentHash = NULL WHERE id = :id")
    suspend fun setNoteContent(id: String, name: String, mime: String, size: Long)
}

@Dao
interface SavedFilterDao {
    @Query("SELECT * FROM saved_filters ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<SavedFilterEntity>>

    @Query("SELECT * FROM saved_filters")
    suspend fun allNow(): List<SavedFilterEntity>

    @Insert suspend fun insert(f: SavedFilterEntity): Long

    @Query("DELETE FROM saved_filters WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE saved_filters SET json = :json WHERE id = :id")
    suspend fun setJson(id: Long, json: String)
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

    /** Bytes the intro/outro analysis adds to a backup (fingerprints and their index). */
    @Query("SELECT (SELECT COALESCE(SUM(length(hashes) + 80), 0) FROM audio_prints) + (SELECT COALESCE(SUM(length(hashes) + 20), 0) FROM signature_sounds) " +
        "+ (SELECT COALESCE(SUM(length(hashes) + 120), 0) FROM segment_rules) + (SELECT COALESCE(SUM(length(hashes) + 120), 0) FROM rejected_prints)")
    fun observeBackupBytes(): Flow<Long>

    // Whole tables, for the backup.
    @Query("SELECT * FROM audio_prints")
    suspend fun backupPrints(): List<AudioPrintEntity>

    @Query("SELECT * FROM signatures ORDER BY id")
    suspend fun backupSignatures(): List<SignatureEntity>

    @Query("SELECT * FROM signature_sounds ORDER BY id")
    suspend fun backupSignatureSounds(): List<SignatureSoundEntity>

    @Query("SELECT * FROM segment_rules ORDER BY id")
    suspend fun backupRules(): List<SegmentRuleEntity>

    @Query("SELECT * FROM rejected_prints ORDER BY id")
    suspend fun backupRejected(): List<RejectedPrintEntity>

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

    @Query("DELETE FROM segment_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

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
    val personId: Long?, val state: String, val similarity: Float, val width: Int = 0,
    /** The moments (ms) of its faces, comma separated: frame bits differ between sampling plans, these do not. */
    val times: String = "",
)

/** A track without its embeddings and picture, to score it against cached embeddings. */
data class TrackMeta(val id: Long, val fileId: String, val frames: Long, val quality: Float, val personId: Long?, val state: String)

class TrackEmbeddings(val id: Long, val embeddings: ByteArray)

/** A track for the lists: everything but its embeddings and picture. */
data class TrackCard(
    val id: Long, val fileId: String, val timeMs: Long, val width: Int, val faceCount: Int,
    val personId: Long?, val state: String, val similarity: Float, val times: String = "",
    /** Where the face is in its picture: "x,y,w,h" as fractions (empty for older scans). */
    val box: String = "",
    val quality: Float = 0f,
) {
    /** Every moment it shows in (ms), sorted. */
    val moments: List<Long> get() = times.split(',').mapNotNull { it.toLongOrNull() }.sorted()
}

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

    @Query("SELECT * FROM face_scans WHERE fileId = :fileId")
    fun observeScanOf(fileId: String): Flow<FaceScanEntity?>

    /** The closer look's result kept with the scan (see [com.cripta.app.media.face.FaceStats.withRefine]). */
    @Query("UPDATE face_scans SET stats = :stats WHERE fileId = :fileId")
    suspend fun setScanStats(fileId: String, stats: String)

    @Query("SELECT fileId FROM face_scans WHERE clustered = 0 AND status = 'ok'")
    suspend fun unclusteredIds(): List<String>

    @Query("UPDATE face_scans SET clustered = 1 WHERE fileId IN (:ids)")
    suspend fun markClustered(ids: List<String>)

    @Query("DELETE FROM face_scans WHERE fileId = :fileId")
    suspend fun deleteScan(fileId: String)

    /** Faces the analysis found in [ids] that nobody confirmed (those added or confirmed by hand stay). */
    @Query("DELETE FROM face_tracks WHERE fileId IN (:ids) AND frames != 0 AND personId IS NULL AND state NOT IN ('manual', 'carried')")
    suspend fun deleteDetectedOf(ids: List<String>): Int

    /** Media whose faces were cleared: analysed (so not picked up again), with nothing left to show. */
    @Query("UPDATE face_scans SET status = :status, faces = 0, rejects = '' WHERE fileId IN (:ids)")
    suspend fun markCleared(ids: List<String>, status: String)

    /** Media deleted for good (not in the trash): their faces and links go too. */
    @Query("DELETE FROM face_scans WHERE fileId NOT IN (SELECT id FROM files)")
    suspend fun purgeScans(): Int

    @Query("DELETE FROM face_tracks WHERE fileId NOT IN (SELECT id FROM files)")
    suspend fun purgeTracks(): Int

    @Query("DELETE FROM person_media WHERE fileId NOT IN (SELECT id FROM files)")
    suspend fun purgeLinks(): Int

    @Insert
    suspend fun addTrack(t: FaceTrackEntity): Long

    @Query("SELECT * FROM face_tracks WHERE id IN (:ids)")
    suspend fun tracks(ids: List<Long>): List<FaceTrackEntity>

    @Query("SELECT id, fileId, frames, quality, personId, state FROM face_tracks")
    suspend fun allTrackMeta(): List<TrackMeta>

    @Query("SELECT id, embeddings FROM face_tracks WHERE id IN (:ids)")
    suspend fun embeddingsOf(ids: List<Long>): List<TrackEmbeddings>

    @Insert
    suspend fun addIgnored(f: IgnoredFaceEntity): Long

    @Query("DELETE FROM face_ignored WHERE id IN (:ids)")
    suspend fun deleteIgnored(ids: List<Long>)

    /** Faces deleted from [fileId] and those that are not a face anywhere. */
    @Query("SELECT * FROM face_ignored WHERE fileId = :fileId OR fileId IS NULL")
    suspend fun ignoredFor(fileId: String): List<IgnoredFaceEntity>

    @Query("SELECT * FROM face_ignored")
    suspend fun allIgnored(): List<IgnoredFaceEntity>

    @Query("SELECT COUNT(*) FROM face_ignored")
    fun observeIgnoredCount(): Flow<Int>

    @Query("DELETE FROM face_ignored")
    suspend fun deleteAllIgnored()

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun addApart(pairs: List<FaceApartEntity>)

    @androidx.room.Delete
    suspend fun deleteApart(pairs: List<FaceApartEntity>)

    @Query("SELECT * FROM face_apart")
    suspend fun apart(): List<FaceApartEntity>

    @Query("DELETE FROM face_apart")
    suspend fun deleteAllApart()

    /** The "Dividi il gruppo" pairs [ids] are part of. */
    @Query("SELECT * FROM face_apart WHERE a IN (:ids) OR b IN (:ids)")
    suspend fun apartOf(ids: List<Long>): List<FaceApartEntity>

    /** Pairs left by faces that are gone (deleted, or not found again by a new analysis). */
    @Query("DELETE FROM face_apart WHERE a NOT IN (SELECT id FROM face_tracks) OR b NOT IN (SELECT id FROM face_tracks)")
    suspend fun purgeApartOrphans(): Int

    /** Faces deleted from media deleted for good ("Non è un volto" ones stay). */
    @Query("DELETE FROM face_ignored WHERE fileId IS NOT NULL AND fileId NOT IN (SELECT id FROM files)")
    suspend fun purgeIgnored(): Int

    /** Rejections left by faces or people that are gone. */
    @Query("DELETE FROM face_rejections WHERE trackId NOT IN (SELECT id FROM face_tracks) OR personId NOT IN (SELECT id FROM persons)")
    suspend fun purgeRejections(): Int

    /** A face back as it was (undoing a deletion or a join), with its old id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTrack(t: FaceTrackEntity)

    @Query("UPDATE face_tracks SET frames = :frames, faceCount = :faceCount, embeddings = :embeddings, times = :times, sightings = :sightings WHERE id = :id")
    suspend fun joinedTrack(id: Long, frames: Long, faceCount: Int, embeddings: ByteArray, times: String, sightings: ByteArray)

    @Query("DELETE FROM face_tracks WHERE id IN (:ids)")
    suspend fun deleteTracks(ids: List<Long>)

    @Query("SELECT * FROM face_scans WHERE version = :version")
    suspend fun scansOfVersion(version: Int): List<FaceScanEntity>

    @Query("UPDATE face_scans SET version = :version WHERE fileId IN (:ids)")
    suspend fun setScanVersion(ids: List<String>, version: Int)

    /** Every face of [fileId] with its picture and embeddings (the backup). */
    @Query("SELECT * FROM face_tracks WHERE fileId = :fileId")
    suspend fun tracksOfFile(fileId: String): List<FaceTrackEntity>

    @Query("SELECT * FROM person_media")
    suspend fun allPersonMedia(): List<PersonMediaEntity>

    /** Bytes the faces add to a backup: pictures, embeddings and about 200 bytes of index each. */
    @Query("SELECT COALESCE(SUM(length(crop) + length(embeddings) + 208), 0) FROM face_tracks")
    fun observeBackupBytes(): Flow<Long>

    @Query("SELECT id, fileId, frames, quality, embeddings, personId, state, similarity, width, times FROM face_tracks")
    suspend fun allTrackRows(): List<TrackRow>

    @Query("SELECT id, fileId, frames, quality, embeddings, personId, state, similarity, width, times FROM face_tracks WHERE fileId IN (:ids)")
    suspend fun trackRowsOf(ids: List<String>): List<TrackRow>

    @Query("SELECT id, fileId, frames, quality, embeddings, personId, state, similarity, width, times FROM face_tracks WHERE id = :id")
    suspend fun trackRow(id: Long): TrackRow?

    @Query("SELECT id, fileId, timeMs, width, faceCount, personId, state, similarity, times, box, quality FROM face_tracks")
    fun observeCards(): Flow<List<TrackCard>>

    @Query("SELECT id, fileId, timeMs, width, faceCount, personId, state, similarity, times, box, quality FROM face_tracks")
    suspend fun allCards(): List<TrackCard>

    @Query("SELECT id, fileId, timeMs, width, faceCount, personId, state, similarity, times, box, quality FROM face_tracks WHERE fileId = :fileId")
    fun observeCardsOf(fileId: String): Flow<List<TrackCard>>

    @Query("SELECT id, fileId, timeMs, width, faceCount, personId, state, similarity, times, box, quality FROM face_tracks WHERE fileId = :fileId")
    suspend fun cardsOf(fileId: String): List<TrackCard>

    @Query("SELECT id, fileId, timeMs, width, faceCount, personId, state, similarity, times, box, quality FROM face_tracks WHERE id = :id")
    suspend fun card(id: Long): TrackCard?

    @Query("SELECT id, fileId, timeMs, width, faceCount, personId, state, similarity, times, box, quality FROM face_tracks WHERE personId = :personId")
    suspend fun cardsOfPerson(personId: Long): List<TrackCard>

    @Query("SELECT crop FROM face_tracks WHERE id = :id")
    suspend fun crop(id: Long): ByteArray?

    @Query("UPDATE face_tracks SET personId = :personId, state = :state, similarity = :similarity WHERE id = :id")
    suspend fun place(id: Long, personId: Long?, state: String, similarity: Float)

    /** The user's choice: kept with [personId] for good (the similarity it had stays, for the statistics). */
    @Query("UPDATE face_tracks SET personId = :personId, state = 'manual' WHERE id = :id")
    suspend fun confirm(id: Long, personId: Long)

    @Query("UPDATE face_tracks SET personId = :to WHERE personId = :from")
    suspend fun moveTracks(from: Long, to: Long)

    @Query("UPDATE face_tracks SET personId = :to WHERE id IN (:ids) AND personId = :from")
    suspend fun moveTrackIds(ids: List<Long>, from: Long, to: Long)

    @Query("UPDATE face_tracks SET state = 'manual' WHERE personId = :personId AND state = 'auto'")
    suspend fun lockTracks(personId: Long)

    @Query("UPDATE face_tracks SET personId = NULL, state = 'auto', similarity = 0 WHERE state NOT IN ('manual', 'carried')")
    suspend fun unplaceAuto()

    @Query("DELETE FROM face_tracks WHERE fileId = :fileId")
    suspend fun deleteTracksOf(fileId: String)

    @Query("DELETE FROM person_media WHERE fileId = :fileId")
    suspend fun deleteLinksOf(fileId: String)

    @Query("DELETE FROM face_ignored WHERE fileId = :fileId")
    suspend fun deleteIgnoredOf(fileId: String)

    /** A media destroyed for good: everything the people feature kept about it goes with it. */
    @androidx.room.Transaction
    suspend fun forgetFile(fileId: String) {
        deleteTracksOf(fileId)
        deleteScan(fileId)
        deleteLinksOf(fileId)
        deleteIgnoredOf(fileId)
        purgeRejections()
        deleteEmptyPersons()
    }

    /** The faces the analysis found in [fileId]; those added by hand (no frame) stay. */
    @Query("DELETE FROM face_tracks WHERE fileId = :fileId AND frames != 0")
    suspend fun deleteScannedTracksOf(fileId: String)

    @Insert
    suspend fun addPerson(p: PersonEntity): Long

    /** A person back with their old id (undoing a merge). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPerson(p: PersonEntity)

    @Query("SELECT * FROM persons")
    suspend fun persons(): List<PersonEntity>

    @Query("SELECT * FROM persons")
    fun observePersons(): Flow<List<PersonEntity>>

    @Query("UPDATE persons SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE persons SET notes = :notes, links = :links WHERE id = :id")
    suspend fun setProfile(id: Long, notes: String, links: String)

    @Query("UPDATE persons SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE persons SET ignored = :ignored WHERE id = :id")
    suspend fun setIgnored(id: Long, ignored: Boolean)

    @Query("UPDATE persons SET coverTrackId = :trackId WHERE id = :id")
    suspend fun setCover(id: Long, trackId: Long?)

    /** A track analysed again: the people whose face it was keep it as their face. */
    @Query("UPDATE persons SET coverTrackId = :to WHERE coverTrackId = :from")
    suspend fun moveCover(from: Long, to: Long)

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun deletePerson(id: Long)

    /**
     * People left with no face and no media: unnamed ones go (a named one stays, the user may add
     * to it), unless they hold notes or links the user wrote on their profile.
     */
    @Query("DELETE FROM persons WHERE name = '' AND ignored = 0 AND favorite = 0 AND notes = '' AND links = '' AND id NOT IN (SELECT personId FROM face_tracks WHERE personId IS NOT NULL AND state != 'suggested') AND id NOT IN (SELECT personId FROM person_media)")
    suspend fun deleteEmptyPersons()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addPersonMedia(m: PersonMediaEntity)

    @Query("DELETE FROM person_media WHERE personId = :personId AND fileId = :fileId")
    suspend fun removePersonMedia(personId: Long, fileId: String)

    @Query("UPDATE OR IGNORE person_media SET personId = :to WHERE personId = :from")
    suspend fun movePersonMedia(from: Long, to: Long)

    @Query("DELETE FROM person_media WHERE personId = :personId")
    suspend fun deletePersonMedia(personId: Long)

    @Query("SELECT fileId FROM person_media WHERE personId = :personId")
    suspend fun linksOfPerson(personId: Long): List<String>

    @Query("SELECT * FROM person_media")
    fun observePersonMedia(): Flow<List<PersonMediaEntity>>

    /** Media showing [id] (faces placed, or added by hand); the caller intersects for several people. */
    @Query("SELECT fileId FROM face_tracks WHERE personId = :id AND state != 'suggested' UNION SELECT fileId FROM person_media WHERE personId = :id")
    suspend fun mediaOf(id: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addRejection(r: FaceRejectionEntity)

    @Query("DELETE FROM face_rejections WHERE trackId = :trackId AND personId = :personId")
    suspend fun deleteRejection(trackId: Long, personId: Long)

    @Query("SELECT * FROM face_rejections")
    suspend fun rejections(): List<FaceRejectionEntity>

    @Query("SELECT * FROM face_rejections WHERE trackId IN (:ids)")
    suspend fun rejectionsOfTracks(ids: List<Long>): List<FaceRejectionEntity>

    @Query("UPDATE OR IGNORE face_rejections SET trackId = :to WHERE trackId IN (:from)")
    suspend fun moveTrackRejections(from: List<Long>, to: Long)

    @Query("DELETE FROM face_rejections WHERE trackId IN (:ids)")
    suspend fun deleteRejectionsOfTracks(ids: List<Long>)

    @Query("UPDATE OR IGNORE face_rejections SET personId = :to WHERE personId = :from")
    suspend fun moveRejections(from: Long, to: Long)

    @Query("DELETE FROM face_rejections WHERE personId = :personId")
    suspend fun deleteRejectionsOf(personId: Long)

    @Insert
    suspend fun addMerge(m: FaceMergeEntity): Long

    @Query("SELECT * FROM face_merges WHERE id = :id")
    suspend fun mergeById(id: Long): FaceMergeEntity?

    @Query("SELECT * FROM face_merges ORDER BY createdAt DESC LIMIT 200")
    fun observeMerges(): Flow<List<FaceMergeEntity>>

    @Query("DELETE FROM face_merges WHERE id = :id")
    suspend fun deleteMerge(id: Long)

    @Query("DELETE FROM face_scans")
    suspend fun deleteAllScans()

    @Query("DELETE FROM face_tracks")
    suspend fun deleteAllTracks()

    /**
     * Every face the user has not confirmed or added: what an analysis found and nobody took. Faces
     * the user said are not someone stay too: the next analysis carries that "no" to the face it
     * finds again (deleted here, the "no" went with them and the face was suggested again). So do
     * faces split from others ("Dividi il gruppo"), for the same reason: the split moves with them.
     */
    @Query("DELETE FROM face_tracks WHERE (personId IS NULL OR state NOT IN ('manual', 'carried')) AND id NOT IN (SELECT trackId FROM face_rejections) " +
        "AND id NOT IN (SELECT a FROM face_apart) AND id NOT IN (SELECT b FROM face_apart)")
    suspend fun deleteUnconfirmedTracks()

    /** [deleteUnconfirmedTracks] for one media. */
    @Query("DELETE FROM face_tracks WHERE fileId = :fileId AND (personId IS NULL OR state NOT IN ('manual', 'carried')) AND id NOT IN (SELECT trackId FROM face_rejections) " +
        "AND id NOT IN (SELECT a FROM face_apart) AND id NOT IN (SELECT b FROM face_apart)")
    suspend fun deleteUnconfirmedTracksOf(fileId: String)

    @Query("DELETE FROM persons")
    suspend fun deleteAllPersons()

    @Query("DELETE FROM person_media")
    suspend fun deleteAllPersonMedia()

    @Query("DELETE FROM face_rejections")
    suspend fun deleteAllRejections()

    @Query("DELETE FROM face_merges")
    suspend fun deleteAllMerges()
}

@Dao
interface DupPrintDao {
    @Query("SELECT * FROM dup_prints")
    suspend fun all(): List<DupPrintEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(p: DupPrintEntity)

    @Query("DELETE FROM dup_prints WHERE fileId = :fileId")
    suspend fun forget(fileId: String)

    /** Live photos and videos, for "N di M già analizzati". */
    @Query("SELECT COUNT(*) FROM files WHERE deletedAt IS NULL AND (mimeType LIKE 'image/%' OR mimeType LIKE 'video/%')")
    suspend fun mediaCount(): Int

    /** Live media with a fingerprint of their current content. */
    @Query("SELECT COUNT(*) FROM dup_prints p JOIN files f ON f.id = p.fileId WHERE f.deletedAt IS NULL AND f.sizeBytes = p.sizeBytes")
    suspend fun printedCount(): Int

    /** Rows of files no longer in the vault (destroyed some other way than [forget]). */
    @Query("DELETE FROM dup_prints WHERE fileId NOT IN (SELECT id FROM files)")
    suspend fun purgeOrphans()

    @Query("SELECT * FROM dup_apart")
    suspend fun apartPairs(): List<DupApartEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun putApart(pairs: List<DupApartEntity>)

    @Query("DELETE FROM dup_apart WHERE a = :fileId OR b = :fileId")
    suspend fun forgetApart(fileId: String)

    @Query("DELETE FROM dup_apart WHERE a NOT IN (SELECT id FROM files) OR b NOT IN (SELECT id FROM files)")
    suspend fun purgeApartOrphans()
}

/** A file's id and name, without the rest of its row (see [FileDao.namesLike]). */
data class FileName(val id: String, val originalName: String)
