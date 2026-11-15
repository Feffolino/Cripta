package com.cripta.app.ui.people

import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OpenLinkTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Test fun aBareAddressOpensAsHttps() {
        openLink(app, "example.com/a")
        val i = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, i.action)
        assertEquals("https://example.com/a", i.data.toString())
    }

    @Test fun anAddressWithASchemeIsKept() {
        openLink(app, "http://example.com")
        assertEquals("http://example.com", shadowOf(app).nextStartedActivity.data.toString())
    }

    @Test fun anAddressWithAPortButNoSchemeOpensAsHttps() {
        openLink(app, "example.com:8080/a")
        assertEquals("https://example.com:8080/a", shadowOf(app).nextStartedActivity.data.toString())
        openLink(app, "localhost:3000")
        assertEquals("https://localhost:3000", shadowOf(app).nextStartedActivity.data.toString())
    }

    @Test fun schemesWithoutSlashesAreKept() {
        for (link in listOf("mailto:a@example.com", "tel:0123456", "tel:+39 06 1234", "https://example.com:8080/a")) {
            openLink(app, link)
            assertEquals(link, shadowOf(app).nextStartedActivity.data.toString())
        }
    }

    @Test fun fromAnActivityAWebLinkOpensInACustomTabOverCripta() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        openLink(activity, "example.com/a")
        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals("https://example.com/a", started.intent.data.toString())
        assertTrue(started.intent.hasExtra(androidx.browser.customtabs.CustomTabsIntent.EXTRA_SESSION))
        // Same task as Cripta: Back from the page returns here.
        assertEquals(0, started.intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertTrue(started.requestCode >= 0)
    }

    @Test fun fromAnActivityOtherSchemesStillOpenAsAPlainView() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        openLink(activity, "mailto:a@example.com")
        val started = shadowOf(activity).nextStartedActivity
        assertEquals("mailto:a@example.com", started.data.toString())
        assertTrue(!started.hasExtra(androidx.browser.customtabs.CustomTabsIntent.EXTRA_SESSION))
    }
}
