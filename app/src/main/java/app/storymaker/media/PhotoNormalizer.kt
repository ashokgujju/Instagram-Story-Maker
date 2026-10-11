package app.storymaker.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import app.storymaker.IoDispatcher
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Rotate clockwise by [rotationDegrees], then mirror horizontally if [flipHorizontal]. */
data class OrientationTransform(val rotationDegrees: Int, val flipHorizontal: Boolean) {
    val isIdentity: Boolean get() = rotationDegrees == 0 && !flipHorizontal
}

/**
 * Transform that turns a stored JPEG upright and matching the preview.
 *
 * Front-camera photos are mirrored to match the (mirrored) preview. Capture requests no
 * mirroring from CameraX, but if the EXIF orientation already encodes a flip it is not applied twice.
 */
fun orientationTransform(exifOrientation: Int, isFrontCamera: Boolean): OrientationTransform {
    val (rotation, exifFlip) = when (exifOrientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0 to true
        ExifInterface.ORIENTATION_ROTATE_180 -> 180 to false
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180 to true
        ExifInterface.ORIENTATION_TRANSPOSE -> 90 to true
        ExifInterface.ORIENTATION_ROTATE_90 -> 90 to false
        ExifInterface.ORIENTATION_TRANSVERSE -> 270 to true
        ExifInterface.ORIENTATION_ROTATE_270 -> 270 to false
        else -> 0 to false
    }
    return OrientationTransform(rotation, flipHorizontal = exifFlip || isFrontCamera)
}

/** Applies orientation and front-camera mirroring to a capture once, in place (D10). */
class PhotoNormalizer @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun normalize(file: File, isFrontCamera: Boolean): PhotoSize = withContext(io) {
        val orientation = ExifInterface(file).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        val transform = orientationTransform(orientation, isFrontCamera)
        val bounds = readBounds(file) ?: error("Unreadable capture")
        val sampleSize = sampleSizeAtMost(maxOf(bounds.width, bounds.height), MAX_LONG_SIDE)

        if (transform.isIdentity && sampleSize == 1) {
            return@withContext bounds
        }

        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: error("Unreadable capture")
        val matrix = Matrix().apply {
            postRotate(transform.rotationDegrees.toFloat())
            if (transform.flipHorizontal) postScale(-1f, 1f)
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright !== decoded) decoded.recycle()
        // Re-encoding writes no EXIF, so the orientation tag is gone.
        file.writeAtomically { upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        PhotoSize(upright.width, upright.height).also { upright.recycle() }
    }

    private companion object {
        const val MAX_LONG_SIDE = 4096
        const val JPEG_QUALITY = 95
    }
}

/** Decodes [file] downsampled for display, keeping its long side at or above [maxLongSide] when possible. */
class PhotoDecoder @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun decode(file: File, maxLongSide: Int): Bitmap? = withContext(io) {
        val bounds = readBounds(file) ?: return@withContext null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeAtLeast(maxOf(bounds.width, bounds.height), maxLongSide)
        }
        BitmapFactory.decodeFile(file.path, options)
    }
}

object BitmapPhotoSizeReader : PhotoSizeReader {
    override fun read(file: File): PhotoSize? = readBounds(file)
}

private fun readBounds(file: File): PhotoSize? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, options)
    return if (options.outWidth > 0 && options.outHeight > 0) PhotoSize(options.outWidth, options.outHeight) else null
}

/** Smallest power-of-two sample size that brings the long side down to at most [maxLongSide]. */
internal fun sampleSizeAtMost(longSide: Int, maxLongSide: Int): Int {
    var sample = 1
    while (longSide / sample > maxLongSide) sample *= 2
    return sample
}

/** Largest power-of-two sample size that keeps the long side at or above [targetLongSide] (for display). */
internal fun sampleSizeAtLeast(longSide: Int, targetLongSide: Int): Int {
    var sample = 1
    while (longSide / (sample * 2) >= targetLongSide) sample *= 2
    return sample
}
