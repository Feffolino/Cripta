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

    @Test
    fun theSavedPartOfTheQueueIsCentredAndBounded() {
        val max = ViewerViewModel.MAX_SAVED_QUEUE
        assertTrue(ViewerViewModel.queueWindow(0, 0, max).isEmpty())
        assertEquals(0 until 10, ViewerViewModel.queueWindow(10, 7, max))
        assertEquals(0 until max, ViewerViewModel.queueWindow(5000, 3, max))
        assertEquals(5000 - max until 5000, ViewerViewModel.queueWindow(5000, 4999, max))
        val mid = ViewerViewModel.queueWindow(5000, 2500, max)
        assertEquals(max, mid.count())
        assertEquals(2500 - max / 2, mid.first)
    }

    /**
     * After the process is killed, the restored order is the saved part around the file shown: the
     * page to show is that file's place in the part, which differs from its place in the whole order
     * (what the pager saved) as soon as the part does not start at the first file.
     */
    @Test
    fun aRestoredViewerStartsOnTheFileShownWhateverTheQueueLength() {
        val max = ViewerViewModel.MAX_SAVED_QUEUE
        for (size in listOf(1, 300, 301, 1500)) {
            val all = (0 until size).map { "id$it" }
            for (at in setOf(0, size / 2, size - 1)) {
                val window = ViewerViewModel.queueWindow(size, at, max)
                val saved = all.subList(window.first, window.last + 1)
                val page = ViewerViewModel.startPage(saved, all[at], "id0")
                assertEquals("size $size, at $at", all[at], saved[page])
                assertEquals("size $size, at $at", at - window.first, page)
                assertTrue("size $size, at $at", saved.size <= max)
            }
        }
        // The pager's own page (the place in the whole order) is another file once the part is cut.
        val window = ViewerViewModel.queueWindow(1500, 1499, max)
        assertTrue(1499 != ViewerViewModel.startPage((0 until 1500).map { "id$it" }.slice(window), "id1499", "id0"))
    }

    @Test
    fun aViewerStartsOnTheOpenedFileWithoutASavedPage() {
        val ids = listOf("a", "b", "c")
        assertEquals(1, ViewerViewModel.startPage(ids, null, "b"))
        assertEquals(2, ViewerViewModel.startPage(ids, "c", "b"))
        // The file shown last is no longer in the order: the opened one.
        assertEquals(1, ViewerViewModel.startPage(ids, "gone", "b"))
        assertEquals(0, ViewerViewModel.startPage(ids, "gone", "gone too"))
    }

    @Test
    fun aLongQueueIsSavedAroundThePageShownAndRestoredFromIt() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val settings = SettingsStore(app)
        val session = SessionManager()
        val repo = VaultRepository(app, session, BlobStore(app), settings)
        val all = (0 until 5000).map { "id$it" }
        fun vm(queue: ViewerQueue, state: SavedStateHandle) = ViewerViewModel(
            app, repo, ThumbnailLoader(app, repo, session), settings,
            IntroRepository(session, repo, AudioDecoder(repo)),
            FaceRepository(app, session, repo), session, queue, state,
        )
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val state = SavedStateHandle(mapOf("fileId" to "id4000"))
            val first = vm(ViewerQueue().apply { set(all) }, state)
            try {
                assertEquals(all, first.ids)
                fun saved() = state.get<ArrayList<String>>("viewer_queue")!!
                assertEquals(ViewerViewModel.MAX_SAVED_QUEUE, saved().size)
                assertEquals(ViewerViewModel.MAX_SAVED_QUEUE / 2, saved().indexOf("id4000"))
                // A few pages on: still in the middle of what is saved, nothing saved again.
                val before = saved()
                first.setCurrent("id4050")
                assertTrue(before === saved())
                // Far enough: saved again around the new page.
                first.setCurrent("id4200")
                assertEquals(ViewerViewModel.MAX_SAVED_QUEUE / 2, saved().indexOf("id4200"))
            } finally {
                first.viewModelScope.cancel()
            }
            // The process killed: no queue in memory, the viewer comes back with the saved part.
            val restored = vm(ViewerQueue(), state)
            try {
                assertEquals(ViewerViewModel.MAX_SAVED_QUEUE, restored.ids.size)
                assertEquals("id4200", restored.startId)
                assertEquals(ViewerViewModel.MAX_SAVED_QUEUE / 2, restored.ids.indexOf("id4200"))
                // The screen goes to that file once; a rotation afterwards keeps the pager's own page.
                assertTrue(restored.takeRestoredFromSavedQueue())
                assertFalse(restored.takeRestoredFromSavedQueue())
            } finally {
                restored.viewModelScope.cancel()
            }
            // A viewer opened from the gallery is not a restore.
            val fresh = vm(ViewerQueue().apply { set(all) }, SavedStateHandle(mapOf("fileId" to "id10")))
            try {
                assertFalse(fresh.takeRestoredFromSavedQueue())
            } finally {
                fresh.viewModelScope.cancel()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
