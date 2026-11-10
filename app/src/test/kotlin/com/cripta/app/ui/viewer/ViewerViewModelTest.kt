package com.cripta.app.ui.viewer

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cripta.app.data.BlobStore
import com.cripta.app.data.SettingsStore
import com.cripta.app.data.VaultRepository
import com.cripta.app.data.face.FaceRepository
import com.cripta.app.data.intro.IntroRepository
import com.cripta.app.media.ThumbnailLoader
import com.cripta.app.media.intro.AudioDecoder
import com.cripta.app.security.SessionManager
import com.cripta.app.viewer.ViewerQueue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ViewerViewModelTest {
    @Test
    fun storedPlaybackAndRotationArePublishedBeforeSettingsRead() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val settings = SettingsStore(app)
        val orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        settings.setAutoRotate(false)
        settings.setViewerRotationLock(orientation)
        val session = SessionManager()
        val repo = VaultRepository(app, session, BlobStore(app), settings)

        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = ViewerViewModel(
                app, repo, ThumbnailLoader(app, repo, session), settings,
                IntroRepository(session, repo, AudioDecoder(repo)),
                FaceRepository(app, session, repo), session, ViewerQueue(), SavedStateHandle(),
            )
            try {
                assertFalse(vm.settingsRead.value)
                assertTrue(vm.playback.value.autoRotate)
                assertFalse(vm.rotationLocked.value)

                // Observe the first ready emission before the producer can publish anything else.
                val atReady = async(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
                    vm.settingsRead.first { it }
                    Triple(vm.playback.value.autoRotate, vm.rotationLocked.value, vm.lockedOrientation)
                }
                assertEquals(Triple(false, true, orientation), atReady.await())
            } finally {
                vm.viewModelScope.cancel()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
