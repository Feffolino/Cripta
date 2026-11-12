package com.cripta.app.data.db

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * A vault of an older version, opened by Room through the migrations: Room checks the migrated
 * tables against the schema it expects (and fails the open when they differ), and that schema is
 * the one exported to app/schemas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SchemaMigrationTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val name = "schema-migration-test.db"

    @Before
    @After
    fun clean() { app.deleteDatabase(name) }

    private fun raw(block: (SupportSQLiteDatabase) -> Unit) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(app).name(name).callback(object : SupportSQLiteOpenHelper.Callback(LATEST) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }).build()
        )
        try { block(helper.writableDatabase) } finally { helper.close() }
    }

    private fun identityHash(db: SupportSQLiteDatabase): String =
        db.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { c -> c.moveToFirst(); c.getString(0) }

    @Test
    fun aVersion35VaultMigratesToTheSchemaRoomExpects() {
        // Version 38 as Room creates it, then turned back into version 35: the tables were the
        // same but for the tags' "Sulle copertine" (38), and the colours of 36 and 37 were older.
        Room.databaseBuilder(app, CriptaDatabase::class.java, name).allowMainThreadQueries().build()
            .also { it.openHelper.writableDatabase }.close()
        val oldColor = com.cripta.app.ui.theme.FormerPresetColors.keys.first()
        raw { db ->
            // The tags without "onCovers" (no DROP COLUMN in this SQLite): rebuilt, indexes kept.
            val indexes = db.query("SELECT sql FROM sqlite_master WHERE type = 'index' AND tbl_name = 'tags' AND sql IS NOT NULL")
                .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            db.execSQL("CREATE TABLE tags_35 (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `alias` TEXT, " +
                "`orderIndex` INTEGER NOT NULL, `color` INTEGER, `pinned` INTEGER NOT NULL, `lastUsedAt` INTEGER, `icon` TEXT)")
            db.execSQL("DROP TABLE tags")
            db.execSQL("ALTER TABLE tags_35 RENAME TO tags")
            indexes.forEach { db.execSQL(it) }
            assertTrue(indexes.isNotEmpty())
            db.execSQL("INSERT INTO tags (name, orderIndex, pinned, color) VALUES ('Mare', 0, 0, ?)", arrayOf<Any>(oldColor))
            db.execSQL("PRAGMA user_version = 35")
        }

        // Room runs 35 -> 38 and validates every table: a mismatch throws here.
        val migrated = Room.databaseBuilder(app, CriptaDatabase::class.java, name).allowMainThreadQueries()
            .addMigrations(CriptaDatabase.MIGRATION_35_36, CriptaDatabase.MIGRATION_36_37, CriptaDatabase.MIGRATION_37_38)
            .build()
        try {
            val db = migrated.openHelper.writableDatabase
            assertEquals(LATEST, db.version)
            db.query("SELECT onCovers, color FROM tags WHERE name = 'Mare'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
                assertTrue(c.getInt(1) != oldColor)
            }
            // The exported schema is the one this build expects.
            val exported = File("schemas/com.cripta.app.data.db.CriptaDatabase/$LATEST.json")
            assertTrue("missing ${exported.absolutePath}", exported.isFile)
            val json = JSONObject(exported.readText()).getJSONObject("database")
            assertEquals(LATEST, json.getInt("version"))
            assertEquals(json.getString("identityHash"), identityHash(db))
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val LATEST = 38
    }
}
