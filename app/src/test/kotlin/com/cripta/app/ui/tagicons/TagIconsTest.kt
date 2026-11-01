package com.cripta.app.ui.tagicons

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TagIconsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun italianWordsFindTheIcons() = runBlocking {
        assertTrue(TagIcons.search(context, "gatto").take(5).contains("cat-face"))
        assertTrue(TagIcons.search(context, "lucchetto").take(5).contains("locked"))
        // A word only in the CLDR keywords, plural, and with an accent.
        assertTrue(TagIcons.search(context, "medaglie").isNotEmpty())
        assertTrue(TagIcons.search(context, "caffè").contains("hot-beverage"))
        // English still works.
        assertTrue(TagIcons.search(context, "pizza").first() == "pizza")
    }

    @Test
    fun anEmojiLeadsToItsIcon() = runBlocking {
        assertEquals("red-heart", TagIcons.forEmoji(context, "❤️"))
        assertEquals("red-heart", TagIcons.forEmoji(context, "❤"))
        assertEquals("cat-face", TagIcons.forEmoji(context, "🐱"))
        // An acronym is not an emoji.
        assertNull(TagIcons.forEmoji(context, "TV"))
    }

    @Test
    fun namesAreItalianOnceLoaded() = runBlocking {
        TagIcons.forEmoji(context, "🐱")
        assertEquals("muso di gatto", TagIcons.title("cat-face"))
    }
}
