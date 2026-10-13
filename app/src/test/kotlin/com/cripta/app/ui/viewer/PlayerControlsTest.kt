package com.cripta.app.ui.viewer

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.cripta.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.RobolectricTestRunner
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerControlsTest {
    private fun withPlayerView(test: (PlayerView, ExoPlayer) -> Unit) {
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = activityController.get()
        val view = LayoutInflater.from(activity).inflate(R.layout.cripta_player_view, null) as PlayerView
        activity.setContentView(view)
        val player = ExoPlayer.Builder(activity).build()
        player.playWhenReady = true
        view.player = player
        try { test(view, player) } finally {
            view.player = null
            player.release()
            activityController.pause().stop().destroy()
        }
    }

    @Test
    fun automaticHideDoesNotEchoVisibleAndReopenControls() = withPlayerView { view, _ ->
        var chromeVisible = true
        val notifications = mutableListOf<Boolean>()
        view.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            val visible = visibility == View.VISIBLE
            notifications += visible
            // The same two-way synchronization as the viewer's listener and Compose effect.
            if (chromeVisible != visible) {
                chromeVisible = visible
                view.post { view.syncControllerVisibility(chromeVisible) }
            }
        })
        view.syncControllerVisibility(true)
        shadowOf(Looper.getMainLooper()).idle()
        notifications.clear()

        // The viewer's timeout expires without a manual tap to hide the controls.
        chromeVisible = false
        view.syncControllerVisibility(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))

        assertFalse(chromeVisible)
        assertFalse(view.isControllerFullyVisible)
        assertEquals(listOf(false), notifications)
    }

    @Test
    fun normalViewerHasNoSecondNativeHideTimer() = withPlayerView { view, _ ->
        view.updateControllerTimeout(selfManaged = false, paused = false, timeoutMs = 1000)
        view.syncControllerVisibility(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertEquals(0, view.controllerShowTimeoutMs)
        assertTrue(view.isControllerFullyVisible)
        view.syncControllerVisibility(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertFalse(view.isControllerFullyVisible)
    }

    @Test
    fun splitViewTimeoutIsNotRestartedByRecomposition() = withPlayerView { view, _ ->
        view.updateControllerTimeout(selfManaged = true, paused = false, timeoutMs = 1000)
        view.showController()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
        view.updateControllerTimeout(selfManaged = true, paused = false, timeoutMs = 1000)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertFalse(view.isControllerFullyVisible)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertFalse(view.isControllerFullyVisible)
    }

    @Test
    fun pauseRetainsVisibleControlsButDoesNotRevealHiddenControls() = withPlayerView { view, player ->
        view.updateControllerTimeout(selfManaged = true, paused = false, timeoutMs = 1000)
        view.showController()
        player.pause()
        view.updateControllerTimeout(selfManaged = true, paused = true, timeoutMs = 1000)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertTrue(view.isControllerFullyVisible)
        view.hideController()
        player.play()
        player.pause()
        view.updateControllerTimeout(selfManaged = true, paused = true, timeoutMs = 1000)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertFalse(view.isControllerFullyVisible)
    }
}
