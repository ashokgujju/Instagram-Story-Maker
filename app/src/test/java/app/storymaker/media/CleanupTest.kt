package app.storymaker.media

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CleanupTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val now = 1_800_000_000_000L
    private val hour = 60 * 60 * 1000L

    private fun shareFile(name: String, ageMillis: Long): File =
        File(tempFolder.root.resolve("share").apply { mkdirs() }, name).apply {
            writeText("x")
            setLastModified(now - ageMillis)
        }

    @Test
    fun shareCleanup_deletesOnlyFilesOlderThanADay() = runBlocking {
        val old = shareFile("story_old.jpg", ageMillis = 25 * hour)
        val recent = shareFile("story_recent.jpg", ageMillis = 23 * hour)
        val justWritten = shareFile("caption_new.png", ageMillis = 0)

        ShareFiles(tempFolder.root.resolve("share"), Dispatchers.Unconfined).cleanUp(nowMillis = now)

        assertFalse(old.exists())
        assertTrue(recent.exists())
        assertTrue(justWritten.exists())
    }

    @Test
    fun shareCleanup_withoutFolder_doesNothing() = runBlocking {
        ShareFiles(tempFolder.root.resolve("missing"), Dispatchers.Unconfined).cleanUp(nowMillis = now)
        assertFalse(tempFolder.root.resolve("missing").exists())
    }

    @Test
    fun orphanCleanup_keepsOnlyTheActiveSession() = runTest {
        val files = SessionFiles(tempFolder.root)
        val store = SessionStore(files, backgroundScope, StandardTestDispatcher(testScheduler)) { PhotoSize(1, 1) }
        val active = store.createSession()
        val orphan = files.sessionDir("orphan").apply { mkdirs() }
        File(orphan, "capture.jpg").writeText("x")

        store.cleanUpOrphans()

        assertFalse(orphan.exists())
        assertTrue(files.sessionDir(active.id).exists())
        assertEquals(listOf(active.id), files.sessionsDir.list()!!.toList())
    }

    @Test
    fun orphanCleanup_withoutActiveSession_deletesAllSessions() = runTest {
        val files = SessionFiles(tempFolder.root)
        val store = SessionStore(files, backgroundScope, StandardTestDispatcher(testScheduler)) { PhotoSize(1, 1) }
        files.sessionDir("left-over").mkdirs()

        store.cleanUpOrphans()

        assertTrue(files.sessionsDir.list()!!.isEmpty())
    }
}
