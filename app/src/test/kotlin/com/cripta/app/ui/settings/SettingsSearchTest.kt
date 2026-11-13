package com.cripta.app.ui.settings

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsSearchTest {
    @Test
    fun keywordsWithCapitalsAreFoundByLowerCaseWords() {
        // The pages carry their icons, read from the app's resources.
        com.cripta.app.ui.theme.CriptaIcons.init(RuntimeEnvironment.getApplication().resources)
        val security = SearchEntry("Sicherheit", "entsperren PIN Code Fingerabdruck", SettingsPage.entries.first(), "s")
        // German keywords keep their nouns' capitals: "pin" and "fingerabdruck" found nothing.
        assertEquals(listOf(security), searchSettings(listOf(security), "pin", Locale.GERMAN))
        assertEquals(listOf(security), searchSettings(listOf(security), " Fingerabdruck ", Locale.GERMAN))
        assertEquals(emptyList<SearchEntry>(), searchSettings(listOf(security), "  ", Locale.GERMAN))
    }
}
