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
    @Delete suspend fun delete(folder: FolderEntity)

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE (:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId ORDER BY name COLLATE NOCASE")
    fun childrenOf(parentId: Long?): Flow<List<FolderEntity>>

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

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): TagEntity?

    @Query("UPDATE tags SET alias = :alias WHERE name = :name")
    suspend fun setAlias(name: String, alias: String?)

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

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tagId FROM file_tags)")
    suspend fun purgeUnusedTags()
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
    @Query("SELECT * FROM files ORDER BY importedAt DESC")
    fun allWithTags(): Flow<List<FileWithTags>>

    @Transaction
    @Query("SELECT * FROM files WHERE (:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId ORDER BY importedAt DESC")
    fun inFolderWithTags(folderId: Long?): Flow<List<FileWithTags>>

    @Query("SELECT id FROM files WHERE folderId = :folderId")
    suspend fun idsInFolder(folderId: Long): List<String>

    /** Direct (non-recursive) file count + total size per folder. Root files (null folder) are excluded. */
    @Query("SELECT folderId AS folderId, COUNT(*) AS cnt, COALESCE(SUM(sizeBytes), 0) AS bytes FROM files WHERE folderId IS NOT NULL GROUP BY folderId")
    fun folderAggregates(): Flow<List<FolderAgg>>

    @Query("UPDATE files SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("UPDATE files SET originalName = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE files SET folderId = :folderId WHERE id = :id")
    suspend fun move(id: String, folderId: Long?)

    @Query("UPDATE files SET sortWeight = :weight WHERE id = :id")
    suspend fun setWeight(id: String, weight: Long)
}
