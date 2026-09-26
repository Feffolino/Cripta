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
    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE ((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId) AND deletedAt IS NULL ORDER BY name COLLATE NOCASE")
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

    /** Newest covers across a set of folders (a folder and its subfolders, for the mosaic). */
    @Query("SELECT * FROM files WHERE folderId IN (:folderIds) AND deletedAt IS NULL ORDER BY importedAt DESC LIMIT :limit")
    suspend fun latestInFolders(folderIds: List<Long>, limit: Int): List<FileEntity>

    @Query("UPDATE files SET contentHash = :hash WHERE id = :id")
    suspend fun setContentHash(id: String, hash: String)

    /** Newest covers of a folder (for its 2x2 mosaic). */
    @Query("SELECT * FROM files WHERE folderId = :folderId AND deletedAt IS NULL ORDER BY importedAt DESC LIMIT :limit")
    suspend fun latestInFolder(folderId: Long, limit: Int): List<FileEntity>

    @Query("SELECT * FROM files WHERE sourceUrl = :url AND deletedAt IS NULL LIMIT 1")
    suspend fun bySourceUrl(url: String): FileEntity?

    @Query("SELECT * FROM files WHERE sizeBytes = :size AND id != :exceptId AND deletedAt IS NULL")
    suspend fun sameSize(size: Long, exceptId: String): List<FileEntity>

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
