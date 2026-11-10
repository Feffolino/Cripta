package com.cripta.app.data.db

import android.app.Application
import androidx.compose.ui.graphics.toArgb
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.cripta.app.ui.theme.FormerPresetColors
import com.cripta.app.ui.theme.TagPalette
import com.cripta.app.ui.theme.currentPresetColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PresetColorMigrationTest {
    private val presets = TagPalette.map { it.toArgb() }.toSet()

    @Test
    fun everyFormerPresetMovesToACurrentOneAndCurrentOnesStay() {
        for ((old, new) in FormerPresetColors) {
            assertFalse("old %08X is not current".format(old), old in presets)
            assertTrue("new %08X is a preset".format(new), new in presets)
        }
        for (p in presets) assertEquals(p, currentPresetColor(p))
        val own = 0xFF123456.toInt()
        assertEquals(own, currentPresetColor(own))
    }

    @Test
    fun migrationRecoloursTagsAndFoldersButNotOwnColours() {
        val app = RuntimeEnvironment.getApplication()
        val db: SupportSQLiteDatabase = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(app).name(null).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }).build(),
        ).writableDatabase
        for (table in listOf("tags", "folders")) {
            db.execSQL("CREATE TABLE `$table` (`id` TEXT NOT NULL PRIMARY KEY, `color` INTEGER)")
            db.execSQL("INSERT INTO `$table` VALUES ('old', ?), ('mid', ?), ('own', ?), ('none', NULL), ('new', ?)",
                arrayOf<Any>(0xFF6D9DF5.toInt(), 0xFF90B8FE.toInt(), 0xFF123456.toInt(), 0xFFB9D2FF.toInt()))
        }

        CriptaDatabase.migratePresetColors(db)

        for (table in listOf("tags", "folders")) {
            fun color(id: String): Int? = db.query("SELECT color FROM `$table` WHERE id = ?", arrayOf(id)).use { c ->
                c.moveToFirst(); if (c.isNull(0)) null else c.getInt(0)
            }
            assertEquals(0xFFB9D2FF.toInt(), color("old"))
            assertEquals(0xFFB9D2FF.toInt(), color("mid"))
            assertEquals(0xFF123456.toInt(), color("own"))
            assertNull(color("none"))
            assertEquals(0xFFB9D2FF.toInt(), color("new"))
        }
    }
}
