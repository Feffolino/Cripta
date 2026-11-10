package com.cripta.app.ui.tagicons

import android.app.Application
import android.content.res.Configuration
import androidx.room.Room
import com.cripta.app.data.db.CriptaDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TagIconsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun locale(tag: String) = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
    )

    @Test
    fun localizedAndEnglishWordsFindTheIcons() = runBlocking {
        val queries = mapOf(
            "it" to "gatto", "de" to "Katze", "ja" to "猫", "ru" to "кошка", "zh-CN" to "猫", "ar" to "قطة",
        )
        for ((lang, query) in queries) {
            assertTrue("$lang: $query", "paw" in TagIcons.search(locale(lang), query))
        }
        for (lang in listOf("en", "it", "ar", "de", "es", "fr", "hi", "ja", "ko", "pt-BR", "ru", "zh-CN")) {
            assertTrue(lang, "heart" in TagIcons.search(locale(lang), "heart"))
        }
        assertTrue("lock" in TagIcons.search(locale("it"), "lucchetti"))
        assertTrue("mug" in TagIcons.search(locale("it"), "caffè"))
        assertTrue("heart" in TagIcons.search(locale("ja"), "ハー"))
        assertTrue("heart" in TagIcons.search(locale("zh-CN"), "红"))
    }

    @Test
    fun anEmojiLeadsToItsIcon() = runBlocking {
        assertEquals("heart", TagIcons.forEmoji(context, "❤️"))
        assertEquals("heart", TagIcons.forEmoji(context, "❤"))
        assertEquals("paw", TagIcons.forEmoji(context, "🐱"))
        assertEquals("mouse", TagIcons.forEmoji(context, "🖱️"))
        // An acronym is not an emoji.
        assertNull(TagIcons.forEmoji(context, "TV"))
    }

    @Test
    fun titlesFollowTheLocaleAndResolveLegacyNames() = runBlocking {
        assertEquals("cuore rosso", TagIcons.title(locale("it"), "red-heart"))
        assertEquals("赤いハート", TagIcons.title(locale("ja"), "heart"))
    }

    @Test
    fun catalogIsCompleteFilteredAndBuildsFilledVectors() = runBlocking {
        val asset = context.assets.open("tagicons/tabler-filled.txt").bufferedReader().use { it.readText() }
        val icons = TagIcons.parseCatalog(asset)
        assertTrue(icons.keys.containsAll(TagIcons.DEFAULTS))
        assertTrue(icons.keys.containsAll(listOf("heart", "paw", "key", "crown")))
        assertTrue(icons.keys.none { it.startsWith("arrow-") || it.startsWith("brand-") || it.endsWith("-logo") })
        assertTrue(icons.keys.none { it.startsWith("battery") || it.startsWith("player-") || it.startsWith("layout") })
        assertTrue(TagIcons.parseCatalog(asset.dropLast(10)).isEmpty())
        assertTrue(TagIcons.parseCatalog("").isEmpty())
        for (name in icons.keys) {
            val vector = checkNotNull(TagIcons.vector(context, name))
            assertEquals(24f, vector.viewportWidth, 0f)
            assertEquals(24f, vector.viewportHeight, 0f)
            assertTrue(vector.root.size > 0)
        }
    }

    @Test
    fun migrationUpdatesBothTablesOnlyWithAValidatedCatalog() = runBlocking {
        val room = Room.inMemoryDatabaseBuilder(context, CriptaDatabase::class.java).build()
        try {
            val db = room.openHelper.writableDatabase
            db.execSQL("INSERT INTO tags (name, icon, pinned, orderIndex) VALUES ('one', 'red-heart', 0, 0)")
            db.execSQL("INSERT INTO tags (name, icon, pinned, orderIndex) VALUES ('two', 'unknown-legacy', 0, 0)")
            db.execSQL("INSERT INTO folders (name, icon, createdAt, isFavorite, excludedFromRandom, sortAscending) VALUES ('one', 'cat-face', 0, 0, 0, 0)")
            db.execSQL("INSERT INTO folders (name, icon, createdAt, isFavorite, excludedFromRandom, sortAscending) VALUES ('two', 'unknown-legacy', 0, 0, 0, 0)")
            fun icons(table: String) = db.query("SELECT icon FROM $table ORDER BY name").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            CriptaDatabase.migrateTagIcons(db, null)
            assertEquals(listOf("red-heart", "unknown-legacy"), icons("tags"))
            assertEquals(listOf("cat-face", "unknown-legacy"), icons("folders"))

            val resolver = checkNotNull(TagIcons.migrationResolver(context))
            repeat(2) { CriptaDatabase.migrateTagIcons(db, resolver) }
            assertEquals(listOf("heart", null), icons("tags"))
            assertEquals(listOf("paw", null), icons("folders"))
            assertEquals("paw", TagIcons.vector(context, "cat-face")?.name)
            assertNull(TagIcons.vector(context, "unknown-legacy"))
        } finally {
            room.close()
        }
    }
}
