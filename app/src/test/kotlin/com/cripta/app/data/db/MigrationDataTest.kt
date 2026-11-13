package com.cripta.app.data.db

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Migrations that change data, run on rows: the structure tests only see empty tables. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationDataTest {
    private fun withDb(block: (SupportSQLiteDatabase) -> Unit) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication()).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                }).build()
        )
        try { block(helper.writableDatabase) } finally { helper.close() }
    }

    private fun SupportSQLiteDatabase.rows(sql: String): List<String> = query(sql).use { c ->
        buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { c.getString(it) ?: "null" }) }
    }

    @Test
    fun peopleMadeByHandStayAndEverythingAutomaticGoes() = withDb { db ->
        db.execSQL("CREATE TABLE persons (id INTEGER PRIMARY KEY, name TEXT NOT NULL, favorite INTEGER NOT NULL, ignored INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE face_tracks (id INTEGER PRIMARY KEY, personId INTEGER, state TEXT NOT NULL, similarity REAL NOT NULL)")
        db.execSQL("CREATE TABLE person_media (personId INTEGER NOT NULL, fileId TEXT NOT NULL)")
        db.execSQL("CREATE TABLE face_merges (id INTEGER PRIMARY KEY, keptId INTEGER NOT NULL)")
        // 1 named; 2 unnamed with a confirmed face; 3 unnamed with only automatic faces; 4 unnamed starred;
        // 5 unnamed with a media placed by hand; 6 unnamed and ignored; 7 unnamed with nothing.
        for ((id, name, fav, ign) in listOf(
            listOf(1, "Anna", 0, 0), listOf(2, "", 0, 0), listOf(3, "", 0, 0), listOf(4, "", 1, 0),
            listOf(5, "", 0, 0), listOf(6, "", 0, 1), listOf(7, "", 0, 0),
        )) db.execSQL("INSERT INTO persons VALUES ($id, '$name', $fav, $ign)")
        db.execSQL("INSERT INTO face_tracks VALUES (10, 1, 'auto', 0.9), (11, 2, 'manual', 0.8), (12, 3, 'auto', 0.7), " +
            "(13, 3, 'suggested', 0.6), (14, 1, 'carried', 0.5), (15, NULL, 'auto', 0.4)")
        db.execSQL("INSERT INTO person_media VALUES (5, 'f1')")
        db.execSQL("INSERT INTO face_merges VALUES (1, 1)")

        CriptaDatabase.MIGRATION_25_26.migrate(db)

        assertEquals(listOf("10|null|auto|0", "11|2|manual|0.8", "12|null|auto|0", "13|null|auto|0", "14|1|carried|0.5", "15|null|auto|0"),
            db.rows("SELECT id, personId, state, similarity FROM face_tracks ORDER BY id"))
        assertEquals(listOf("1", "2", "4", "5", "6"), db.rows("SELECT id FROM persons ORDER BY id"))
        assertEquals(emptyList<String>(), db.rows("SELECT * FROM face_merges"))
    }

    @Test
    fun foldersGetAnEmptyCoverAndKeepTheirRows() = withDb { db ->
        db.execSQL("CREATE TABLE folders (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
        db.execSQL("INSERT INTO folders VALUES (1, 'Trips'), (2, 'Work')")
        CriptaDatabase.MIGRATION_26_27.migrate(db)
        assertEquals(listOf("1|Trips|null", "2|Work|null"), db.rows("SELECT id, name, coverFileId FROM folders ORDER BY id"))
    }
}
