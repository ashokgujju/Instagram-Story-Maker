package app.storymaker.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import app.storymaker.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

sealed interface SaveResult {
    data class Saved(val uri: Uri) : SaveResult
    data object Failed : SaveResult
}

/** Gallery file name for a photo saved at [time], e.g. `StoryMaker_20261011_153012.jpg`. */
fun galleryFileName(time: LocalDateTime): String =
    "StoryMaker_${time.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}.jpg"

/**
 * Saves images to `Pictures/StoryMaker` in the user's gallery: through MediaStore with the
 * pending-file pattern on API 29+, or as a public file plus a media scan on API 26–28
 * (which needs the WRITE_EXTERNAL_STORAGE permission, requested by the caller).
 */
class GallerySaver @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun save(bitmap: Bitmap, quality: Int, time: LocalDateTime = LocalDateTime.now()): SaveResult =
        withContext(io) {
            val name = galleryFileName(time)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveWithMediaStore(bitmap, quality, name)
                } else {
                    saveAsPublicFile(bitmap, quality, name)
                }
            } catch (e: IOException) {
                SaveResult.Failed
            } catch (e: SecurityException) {
                SaveResult.Failed
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveWithMediaStore(bitmap: Bitmap, quality: Int, name: String): SaveResult {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: return SaveResult.Failed
        return try {
            resolver.openOutputStream(uri)?.use { bitmap.writeJpeg(it, quality) } ?: throw IOException("No output stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            SaveResult.Saved(uri)
        } catch (e: IOException) {
            // Don't leave a broken pending entry behind.
            resolver.delete(uri, null, null)
            SaveResult.Failed
        }
    }

    private suspend fun saveAsPublicFile(bitmap: Bitmap, quality: Int, name: String): SaveResult {
        @Suppress("DEPRECATION")
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
        if (!folder.isDirectory && !folder.mkdirs()) return SaveResult.Failed
        val file = File(folder, name)
        try {
            FileOutputStream(file).use { bitmap.writeJpeg(it, quality) }
        } catch (e: IOException) {
            file.delete()
            return SaveResult.Failed
        }
        // Register the file so it shows up in the gallery.
        val uri = suspendCancellableCoroutine { continuation ->
            MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf(MIME_TYPE)) { _, uri ->
                continuation.resume(uri)
            }
        }
        return SaveResult.Saved(uri ?: Uri.fromFile(file))
    }

    private companion object {
        const val FOLDER = "StoryMaker"
        const val MIME_TYPE = "image/jpeg"
    }
}
