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
        for (t in listOf("audio_prints", "media_segments", "rejected_prints", "segment_rules", "signatures", "signature_sounds",
            "face_scans", "face_tracks", "persons", "person_media", "face_rejections", "face_merges")) {
            assertEquals(t, columns(expected, t), columns(migrated, t))
        }
        for (t in listOf("face_tracks", "person_media")) assertEquals(t, indices(expected, t), indices(migrated, t))
        fresh.close(); helper.close()
    }
}
