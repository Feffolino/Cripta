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
        for (t in listOf("audio_prints", "media_segments", "rejected_prints")) {
            assertEquals(t, columns(expected, t), columns(migrated, t))
        }
        fresh.close(); helper.close()
    }
}
