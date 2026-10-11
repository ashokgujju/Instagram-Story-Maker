package app.storymaker.media

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** The FileProvider must expose share files and nothing else. */
@RunWith(AndroidJUnit4::class)
class FileProviderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val authority = "${context.packageName}.fileprovider"

    @Test
    fun shareFile_isReadableThroughItsContentUri() {
        val file = File(context.cacheDir, "share/test_${System.nanoTime()}.jpg").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        try {
            val uri = FileProvider.getUriForFile(context, authority, file)
            assertEquals("content", uri.scheme)
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            assertArrayEquals(byteArrayOf(1, 2, 3), bytes)
        } finally {
            file.delete()
        }
    }

    @Test
    fun filesOutsideTheShareFolder_areRefused() {
        val session = File(context.noBackupFilesDir, "provider_test.jpg").apply { writeBytes(byteArrayOf(1)) }
        val otherCache = File(context.cacheDir, "not_shared.jpg").apply { writeBytes(byteArrayOf(1)) }
        try {
            assertThrows(IllegalArgumentException::class.java) { FileProvider.getUriForFile(context, authority, session) }
            assertThrows(IllegalArgumentException::class.java) { FileProvider.getUriForFile(context, authority, otherCache) }
        } finally {
            session.delete()
            otherCache.delete()
        }
    }
}
