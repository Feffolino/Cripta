package com.cripta.app.ui.tagicons

import android.app.Application
import android.content.res.Configuration
import androidx.room.Room
import com.cripta.app.R
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
    fun folderNamesUseTheSharedSearchCatalog() = runBlocking {
        val names = mapOf(
            "en" to "Family photo album",
            "it" to "Foto di famiglia",
            "ar" to "صورة العائلة",
            "de" to "Kamera Urlaub",
            "es" to "Cámara de viaje",
            "fr" to "Appareil photo",
            "hi" to "कैमरा यात्रा",
            "ja" to "家族写真",
            "ko" to "가족 사진",
            "pt-BR" to "Câmera de viagem",
            "ru" to "Семейное фото",
            "zh-CN" to "家庭照片",
        )
        for ((lang, name) in names) {
            val localized = locale(lang)
            assertTrue("$lang: $name", "camera" in TagIcons.suggest(localized, name))
            assertTrue(localized.getString(R.string.icon_for, name).contains(name))
        }
        val suggestions = TagIcons.suggest(locale("it"), "Foto di famiglia")
        assertTrue("camera" in suggestions)
        assertTrue("home" in suggestions)
        assertTrue("arrow-big-left" in TagIcons.search(locale("it"), "arrow"))
        assertNull(TagIcons.resolveName(context, "file-pencil"))
    }

    @Test
    fun titlesFollowTheLocaleAndResolveLegacyNames() = runBlocking {
        assertEquals("cuore rosso", TagIcons.title(locale("it"), "red-heart"))
        assertEquals("赤いハート", TagIcons.title(locale("ja"), "heart"))
    }

    @Test
    fun manualCatalogBuildsFilledVectors() = runBlocking {
        val asset = context.assets.open("tagicons/tabler-filled.txt").bufferedReader().use { it.readText() }
        val icons = TagIcons.parseCatalog(asset)
        assertTrue(icons.keys.containsAll(TagIcons.DEFAULTS))
        assertTrue(icons.keys.containsAll(listOf("heart", "paw", "key", "crown")))
        assertTrue(icons.keys.containsAll(listOf("arrow-big-left", "brand-tabler", "battery-automotive")))
        assertTrue(icons.keys.none { it in setOf("file-pencil", "ironing-1", "ironing-2", "ironing-3", "ironing-steam") })
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

    @Test
    fun moreFluentNamesKeepAnIcon() = runBlocking {
        val resolver = checkNotNull(TagIcons.migrationResolver(context))
        // Picked by hand, the same emoji, a word of the name, a face's keyword.
        assertEquals("heart", resolver("red-heart"))
        assertEquals("woman", resolver("woman-cook"))
        assertEquals("bottle", resolver("baby-bottle"))
        assertEquals("mood-happy", resolver("grinning-face"))
        assertEquals("clock", resolver("eight-oclock"))
        // False friends of a name word are not followed.
        assertEquals("ballpen", resolver("fountain-pen"))
        assertNull(resolver("bell-pepper"))
    }

    @Test
    fun fluentMouseIsTheAnimalOnlyBeforeTabler() = runBlocking {
        // Fluent's "mouse" is the animal: migrated and old backups get the paw...
        assertEquals("paw", checkNotNull(TagIcons.migrationResolver(context))("mouse"))
        // ...while a Tabler-era name (and the runtime lookup) stays the computer mouse.
        assertEquals("mouse", checkNotNull(TagIcons.migrationResolver(context, fluent = false))("mouse"))
        assertEquals("mouse", TagIcons.resolveName(context, "mouse"))
    }
}
