package com.cripta.app.data.db

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class IntroMigrationTest {
    private fun columns(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { c ->
            buildList {
                while (c.moveToNext()) add("${c.getString(1)}|${c.getString(2)}|${c.getInt(3)}|${c.getInt(5)}")
            }.sorted()
        }

    private fun indices(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA index_list(`$table`)").use { c -> buildList { while (c.moveToNext()) add(c.getString(1)) }.sorted() }

    @Test
    fun migration15to16CreatesTheTablesRoomExpects() {
        val app = RuntimeEnvironment.getApplication()
        val fresh = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        val expected = fresh.openHelper.writableDatabase
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(app).name(null).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }).build()
        )
        val migrated = helper.writableDatabase
        // Tags as they were at version 27 (created by the first migrations, not replayed here).
        migrated.execSQL("CREATE TABLE tags (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, alias TEXT, " +
            "orderIndex INTEGER NOT NULL, color INTEGER, pinned INTEGER NOT NULL, lastUsedAt INTEGER)")
        migrated.execSQL("CREATE UNIQUE INDEX index_tags_name ON tags (name)")
        CriptaDatabase.MIGRATION_15_16.migrate(migrated)
        CriptaDatabase.MIGRATION_16_17.migrate(migrated)
        CriptaDatabase.MIGRATION_17_18.migrate(migrated)
        CriptaDatabase.MIGRATION_18_19.migrate(migrated)
        CriptaDatabase.MIGRATION_19_20.migrate(migrated)
        CriptaDatabase.MIGRATION_20_21.migrate(migrated)
        CriptaDatabase.MIGRATION_21_22.migrate(migrated)
        CriptaDatabase.MIGRATION_22_23.migrate(migrated)
        CriptaDatabase.MIGRATION_23_24.migrate(migrated)
        CriptaDatabase.MIGRATION_24_25.migrate(migrated)
        CriptaDatabase.MIGRATION_25_26.migrate(migrated)
        CriptaDatabase.MIGRATION_27_28.migrate(migrated)
        CriptaDatabase.MIGRATION_29_30.migrate(migrated)
        CriptaDatabase.MIGRATION_30_31.migrate(migrated)
        for (t in listOf("tags", "audio_prints", "media_segments", "rejected_prints", "segment_rules", "signatures", "signature_sounds",
            "face_scans", "face_tracks", "persons", "person_media", "face_rejections", "face_merges", "face_ignored", "face_apart")) {
            assertEquals(t, columns(expected, t), columns(migrated, t))
        }
        for (t in listOf("face_tracks", "person_media", "face_ignored", "face_apart")) assertEquals(t, indices(expected, t), indices(migrated, t))
        fresh.close(); helper.close()
    }

    @Test
    fun migration28to29AddsTheFolderIconAndTheFileIndexes() {
        val app = RuntimeEnvironment.getApplication()
        val fresh = Room.inMemoryDatabaseBuilder(app, CriptaDatabase::class.java).allowMainThreadQueries().build()
        val expected = fresh.openHelper.writableDatabase
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(app).name(null).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }).build()
        )
        val migrated = helper.writableDatabase
        // Folders and files as they were at version 28.
        migrated.execSQL("CREATE TABLE IF NOT EXISTS `folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
            "`parentId` INTEGER, `createdAt` INTEGER NOT NULL, `color` INTEGER, `emoji` TEXT, `deletedAt` INTEGER, `isFavorite` INTEGER NOT NULL, " +
            "`excludedFromRandom` INTEGER NOT NULL, `sortKey` TEXT, `sortAscending` INTEGER NOT NULL, `coverFileId` TEXT)")
        migrated.execSQL("CREATE TABLE IF NOT EXISTS `files` (`id` TEXT NOT NULL, `originalName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, " +
            "`sizeBytes` INTEGER NOT NULL, `folderId` INTEGER, `isFavorite` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `importedAt` INTEGER NOT NULL, " +
            "`wrappedKeyset` BLOB NOT NULL, `durationMs` INTEGER, `sortWeight` INTEGER NOT NULL, `sourceUrl` TEXT, `width` INTEGER, `height` INTEGER, " +
            "`playbackPosMs` INTEGER, `deletedAt` INTEGER, `lastPlayedAt` INTEGER, `contentHash` TEXT, PRIMARY KEY(`id`))")
        migrated.execSQL("CREATE INDEX IF NOT EXISTS `index_files_folderId` ON `files` (`folderId`)")
        migrated.execSQL("CREATE INDEX IF NOT EXISTS `index_files_isFavorite` ON `files` (`isFavorite`)")
        CriptaDatabase.MIGRATION_28_29.migrate(migrated)
        for (t in listOf("folders", "files")) {
            assertEquals(t, columns(expected, t), columns(migrated, t))
            assertEquals(t, indices(expected, t), indices(migrated, t))
        }
        fresh.close(); helper.close()
    }
}
