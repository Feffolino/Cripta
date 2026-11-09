package com.cripta.app.ui.people

import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
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
}
