package app.storymaker.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GallerySaverTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val saver = GallerySaver(context, Dispatchers.IO)
    private var savedUri: android.net.Uri? = null

    @After
    fun deleteSavedImage() {
        savedUri?.let { context.contentResolver.delete(it, null, null) }
    }

    @Test
    fun save_onApi29Plus_createsCompleteMediaStoreEntryInStoryMakerFolder() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }

        val result = saver.save(bitmap, quality = 90)

        assertTrue(result is SaveResult.Saved)
        val uri = (result as SaveResult.Saved).uri
        savedUri = uri
        val projection = arrayOf(
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.IS_PENDING,
        )
        context.contentResolver.query(uri, projection, null, null, null).use { cursor ->
            assertNotNull(cursor)
            assertTrue(cursor!!.moveToFirst())
            assertEquals("Pictures/StoryMaker/", cursor.getString(0))
            assertEquals("image/jpeg", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
        }
        val decoded = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
        assertEquals(64, decoded.width)
        assertEquals(32, decoded.height)
    }
}
