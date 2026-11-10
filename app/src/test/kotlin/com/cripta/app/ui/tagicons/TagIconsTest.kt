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
    private val languages = listOf("en", "it", "ar", "de", "es", "fr", "hi", "ja", "ko", "pt-BR", "ru", "zh-CN")

    private fun locale(tag: String) = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
    )

    private fun asset(name: String) = context.assets.open("tagicons/$name").bufferedReader().use { it.readText() }

    @Test
    fun localizedAndEnglishWordsFindTheIcons() = runBlocking {
        val queries = mapOf(
            "it" to "gatto", "de" to "Katze", "ja" to "猫", "ru" to "кошка", "zh-CN" to "猫", "ar" to "قطة",
            "es" to "gato", "fr" to "chat", "pt-BR" to "gato", "ko" to "고양이", "hi" to "बिल्ली",
        )
        for ((lang, query) in queries) {
            val found = TagIcons.search(locale(lang), query)
            assertTrue("$lang: $query", "cat-face" in found || "cat" in found)
        }
        for (lang in languages) {
            assertTrue(lang, "red-heart" in TagIcons.search(locale(lang), "heart"))
        }
        assertTrue("locked" in TagIcons.search(locale("it"), "lucchetto"))
        assertTrue("hot-beverage" in TagIcons.search(locale("it"), "caffè"))
        assertTrue("red-heart" in TagIcons.search(locale("ja"), "ハー"))
        assertTrue("red-heart" in TagIcons.search(locale("zh-CN"), "红"))
    }

    @Test
    fun anEmojiLeadsToItsIcon() = runBlocking {
        assertEquals("red-heart", TagIcons.forEmoji(context, "❤️"))
        assertEquals("red-heart", TagIcons.forEmoji(context, "❤"))
        assertEquals("cat-face", TagIcons.forEmoji(context, "🐱"))
        assertEquals("computer-mouse", TagIcons.forEmoji(context, "🖱️"))
        // The only drawing of an emoji that comes in skin tones is the default one.
        assertEquals("thumbs-up", TagIcons.forEmoji(context, "👍"))
        assertEquals("thumbs-up", TagIcons.forEmoji(context, "👍🏽"))
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
            val suggestions = TagIcons.suggest(localized, name)
            assertTrue("$lang: $name", suggestions.any { it == "camera" || it == "camera-with-flash" || it == "video-camera" })
            assertTrue(localized.getString(R.string.icon_for, name).contains(name))
        }
        val suggestions = TagIcons.suggest(locale("it"), "Foto di famiglia")
        assertTrue("camera" in suggestions)
        assertTrue("house" in TagIcons.suggest(locale("it"), "casa"))
        assertTrue("bow-and-arrow" in TagIcons.search(locale("it"), "arrow"))
        assertNull(TagIcons.resolveName(context, "file-pencil"))
    }

    @Test
    fun titlesFollowTheLocaleAndResolveLegacyNames() = runBlocking {
        assertEquals("cuore rosso", TagIcons.title(locale("it"), "red-heart"))
        assertEquals("赤いハート", TagIcons.title(locale("ja"), "red-heart"))
        // A Tabler name of the previous catalog.
        assertEquals("赤いハート", TagIcons.title(locale("ja"), "heart"))
    }

    @Test
    fun theCatalogBuildsColouredVectors() = runBlocking {
        val icons = TagIcons.parseCatalog(asset("fluent-emoji-flat.txt"))
        assertTrue(icons.size > 1000)
        assertTrue(icons.keys.containsAll(TagIcons.DEFAULTS))
        assertTrue(icons.keys.containsAll(listOf("red-heart", "paw-prints", "key", "crown", "cat-face", "pizza")))
        // No skin tones, no abstract signs, nothing the vectors cannot draw.
        assertTrue(icons.keys.none { it.endsWith("-light") && it.removeSuffix("-light") in icons })
        assertTrue(icons.keys.none { it.startsWith("keycap-") || (it.endsWith("-arrow") && it != "bow-and-arrow") || it == "teacher" })
        assertTrue(TagIcons.parseCatalog(asset("fluent-emoji-flat.txt").dropLast(10)).isEmpty())
        assertTrue(TagIcons.parseCatalog("").isEmpty())
        // The checksum covers the rest of the file, whatever its line endings.
        assertEquals(icons, TagIcons.parseCatalog(asset("fluent-emoji-flat.txt").replace("\n", "\r\n")))
        val colours = HashSet<Long>()
        for (name in icons.keys) {
            val vector = checkNotNull(TagIcons.vector(context, name))
            assertEquals(30f, vector.viewportWidth, 0f)
            assertEquals(30f, vector.viewportHeight, 0f)
            assertTrue(vector.root.size > 0)
            collectColours(vector.root, colours)
        }
        // Drawn in their own colours, not one.
        assertTrue(colours.size > 50)
        assertTrue(TagIcons.names(context).containsAll(icons.keys))
    }

    private fun collectColours(group: androidx.compose.ui.graphics.vector.VectorGroup, into: MutableSet<Long>) {
        for (node in group) {
            when (node) {
                is androidx.compose.ui.graphics.vector.VectorGroup -> collectColours(node, into)
                is androidx.compose.ui.graphics.vector.VectorPath -> {
                    val brush = node.fill ?: node.stroke
                    if (brush is androidx.compose.ui.graphics.SolidColor) into += brush.value.value.toLong()
                }
            }
        }
    }

    @Test
    fun everyIconIsInEveryLanguageIndex() {
        val names = TagIcons.parseCatalog(asset("fluent-emoji-flat.txt")).keys
        for (lang in listOf("en", "it", "ar", "de", "es", "fr", "hi", "ja", "ko", "pt", "ru", "zh")) {
            val rows = asset("$lang.txt").trimEnd('\n').lines().map { it.trimEnd('\r').split('\t') }
            assertEquals(lang, names.toList(), rows.map { it[0] })
            assertTrue(lang, rows.all { it.size == 4 && it[2].isNotBlank() && it[1].isNotBlank() })
        }
    }

    @Test
    fun migrationUpdatesBothTablesOnlyWithAValidatedCatalog() = runBlocking {
        val room = Room.inMemoryDatabaseBuilder(context, CriptaDatabase::class.java).build()
        try {
            val db = room.openHelper.writableDatabase
            db.execSQL("INSERT INTO tags (name, icon, pinned, orderIndex) VALUES ('one', 'heart', 0, 0)")
            db.execSQL("INSERT INTO tags (name, icon, pinned, orderIndex) VALUES ('two', 'unknown-legacy', 0, 0)")
            db.execSQL("INSERT INTO folders (name, icon, createdAt, isFavorite, excludedFromRandom, sortAscending) VALUES ('one', 'paw', 0, 0, 0, 0)")
            db.execSQL("INSERT INTO folders (name, icon, createdAt, isFavorite, excludedFromRandom, sortAscending) VALUES ('two', 'unknown-legacy', 0, 0, 0, 0)")
            fun icons(table: String) = db.query("SELECT icon FROM $table ORDER BY name").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            CriptaDatabase.migrateTagIcons(db, null)
            assertEquals(listOf("heart", "unknown-legacy"), icons("tags"))
            assertEquals(listOf("paw", "unknown-legacy"), icons("folders"))

            // Tabler names, from the database of the previous version.
            val resolver = checkNotNull(TagIcons.migrationResolver(context, fluent = false))
            repeat(2) { CriptaDatabase.migrateTagIcons(db, resolver) }
            assertEquals(listOf("red-heart", null), icons("tags"))
            assertEquals(listOf("paw-prints", null), icons("folders"))
            assertEquals("paw-prints", TagIcons.vector(context, "paw")?.name)
            assertNull(TagIcons.vector(context, "unknown-legacy"))
        } finally {
            room.close()
        }
    }

    @Test
    fun savedNamesOfEveryEraKeepAnIcon() = runBlocking {
        val tabler = checkNotNull(TagIcons.migrationResolver(context, fluent = false))
        assertEquals("red-heart", tabler("heart"))
        assertEquals("eleven-oclock", tabler("clock-hour-11"))
        assertEquals("pig-face", tabler("pig"))
        // A name Tabler and Fluent draw alike stays; one Fluent has no drawing for is dropped.
        assertEquals("star", tabler("star"))
        assertNull(tabler("brand-github"))
        // Fluent names (a version 33 database, an old backup) are the catalog's own; the few it dropped
        // lead to a close drawing.
        val fluent = checkNotNull(TagIcons.migrationResolver(context))
        assertEquals("red-heart", fluent("red-heart"))
        assertEquals("cat-face", fluent("cat-face"))
        assertEquals("blossom", fluent("white-flower"))
        assertEquals("man", fluent("man-curly-hair"))
        assertNull(fluent("keycap-1"))
    }

    @Test
    fun theMouseIsTheAnimalOnlyInFluentNames() = runBlocking {
        // Fluent's "mouse" is the animal...
        assertEquals("mouse", checkNotNull(TagIcons.migrationResolver(context))("mouse"))
        // ...while Tabler's is the computer mouse.
        assertEquals("computer-mouse", checkNotNull(TagIcons.migrationResolver(context, fluent = false))("mouse"))
        assertEquals("mouse", TagIcons.resolveName(context, "mouse"))
    }
}
