package app.storymaker.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SessionStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val files by lazy { SessionFiles(tempFolder.root) }
    private val photoSize = PhotoSize(2160, 3840)

    private fun TestScope.newStore() = SessionStore(
        files = files,
        scope = backgroundScope,
        io = StandardTestDispatcher(testScheduler),
        photoSizeReader = { photoSize },
    )

    /** Creates a session with a (fake) captured photo and its first snapshot. */
    private suspend fun SessionStore.capture(): String {
        val session = createSession()
        session.captureFile.writeBytes(byteArrayOf(1, 2, 3))
        completeCapture(session.id, photoSize)
        return session.id
    }

    private fun readSnapshot(id: String): SessionSnapshot =
        SessionSnapshot.json.decodeFromString(SessionSnapshot.serializer(), files.snapshotFile(id).readText())

    @Test
    fun createSession_createsFolderAndPointer_andReplacesPrevious() = runTest {
        val store = newStore()
        val first = store.capture()

        val second = store.createSession()

        assertFalse(files.sessionDir(first).exists())
        assertTrue(files.sessionDir(second.id).isDirectory)
        assertEquals(second.id, files.activeSessionFile.readText())
        assertNull(store.state.value)
    }

    @Test
    fun completeCapture_writesSnapshotAndState() = runTest {
        val store = newStore()
        val id = store.capture()

        assertEquals(id, store.state.value?.sessionId)
        assertEquals(store.state.value, readSnapshot(id))
        assertEquals(2160, readSnapshot(id).photoWidth)
    }

    @Test
    fun update_isSavedOnlyAfterDebounce() = runTest {
        val store = newStore()
        val id = store.capture()

        store.update { it.copy(caption = "hi") }
        advanceTimeBy(SessionStore.SAVE_DEBOUNCE_MILLIS - 1)
        runCurrent()
        assertEquals("", readSnapshot(id).caption)

        advanceTimeBy(2)
        runCurrent()
        assertEquals("hi", readSnapshot(id).caption)
    }

    @Test
    fun rapidUpdates_areCoalescedIntoTheLatestState() = runTest {
        val store = newStore()
        val id = store.capture()

        repeat(5) { i ->
            store.update { it.copy(caption = "v$i") }
            advanceTimeBy(100)
        }
        assertEquals("", readSnapshot(id).caption)

        advanceTimeBy(SessionStore.SAVE_DEBOUNCE_MILLIS + 1)
        runCurrent()
        assertEquals("v4", readSnapshot(id).caption)
    }

    @Test
    fun flush_savesImmediately() = runTest {
        val store = newStore()
        val id = store.capture()

        store.update { it.copy(showOnPhoto = false) }
        store.flush()

        assertFalse(readSnapshot(id).showOnPhoto)
    }

    @Test
    fun concurrentUpdates_areNeverLost() = runTest {
        val store = newStore()
        val id = store.capture()

        withContext(Dispatchers.Default) {
            repeat(1_000) { launch { store.update { it.copy(revision = it.revision + 1) } } }
        }
        store.flush()

        assertEquals(1_000L, store.state.value?.revision)
        assertEquals(1_000L, readSnapshot(id).revision)
    }

    @Test
    fun failedSave_keepsStateAndReportsError() = runTest {
        val store = newStore()
        val errors = mutableListOf<SessionError>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { store.errors.toList(errors) }
        val id = store.capture()
        // A directory where the temporary file should go makes the write fail.
        java.io.File(files.sessionDir(id), "session.json.tmp").mkdirs()

        store.update { it.copy(caption = "unsaved") }
        store.flush()

        assertEquals(listOf(SessionError.SaveFailed), errors)
        assertEquals("unsaved", store.state.value?.caption)
        assertEquals("", readSnapshot(id).caption)
    }

    @Test
    fun load_afterProcessDeath_restoresSnapshotFromDisk() = runTest {
        val id = newStore().run {
            val id = capture()
            update { it.copy(caption = "kept") }
            flush()
            id
        }

        val result = newStore().load(id)

        assertTrue(result is LoadResult.Restored)
        assertEquals("kept", (result as LoadResult.Restored).snapshot.caption)
    }

    @Test
    fun load_withCorruptSnapshot_keepsPhotoAndResetsEdits() = runTest {
        val id = newStore().capture()
        files.snapshotFile(id).writeText("{ not json")

        val store = newStore()
        val result = store.load(id)

        assertTrue(result is LoadResult.RestoredWithoutEdits)
        val snapshot = (result as LoadResult.RestoredWithoutEdits).snapshot
        assertEquals(photoSize.width, snapshot.photoWidth)
        assertEquals(snapshot, store.state.value)
        assertTrue(files.captureFile(id).exists())
    }

    @Test
    fun load_withUnsupportedVersion_resetsEdits() = runTest {
        val id = newStore().capture()
        files.snapshotFile(id).writeText(
            """{"version":99,"sessionId":"$id","createdAt":1,"photoWidth":1,"photoHeight":1,"caption":"x"}""",
        )

        val result = newStore().load(id)

        assertTrue(result is LoadResult.RestoredWithoutEdits)
        assertEquals("", (result as LoadResult.RestoredWithoutEdits).snapshot.caption)
    }

    @Test
    fun load_withMissingPhoto_deletesSession() = runTest {
        val id = newStore().capture()
        files.captureFile(id).delete()

        val result = newStore().load(id)

        assertEquals(LoadResult.PhotoMissing, result)
        assertFalse(files.sessionDir(id).exists())
        assertFalse(files.activeSessionFile.exists())
    }

    @Test
    fun discardActive_deletesSessionPointerAndState() = runTest {
        val store = newStore()
        val id = store.capture()

        store.discardActive()

        assertFalse(files.sessionDir(id).exists())
        assertFalse(files.activeSessionFile.exists())
        assertNull(store.state.value)
    }

    @Test
    fun pendingSave_afterDiscard_doesNotRecreateSession() = runTest {
        val store = newStore()
        val id = store.capture()

        store.update { it.copy(caption = "late") }
        store.discardActive()
        advanceTimeBy(SessionStore.SAVE_DEBOUNCE_MILLIS + 1)
        runCurrent()

        assertFalse(files.sessionDir(id).exists())
    }

    @Test
    fun layers_surviveReloadInNewStore() = runTest {
        val stroke = StrokeLayer("a", 0xFFFFFFFF.toInt(), 0.02f, listOf(0.1f, 0.2f, 0.3f, 0.4f))
        val id = newStore().run {
            val id = capture()
            update { it.copy(layers = listOf(stroke), undoStack = listOf(LayerChange(0, after = stroke)), revision = 1) }
            flush()
            id
        }

        val restored = (newStore().load(id) as LoadResult.Restored).snapshot

        assertEquals(listOf(stroke), restored.layers)
        assertEquals(1, restored.undoStack.size)
    }
}
