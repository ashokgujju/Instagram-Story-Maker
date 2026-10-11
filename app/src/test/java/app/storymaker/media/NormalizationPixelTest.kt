package app.storymaker.media

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Runs PhotoNormalizer on real JPEGs tagged with each EXIF orientation and checks where each
 * colored corner of the stored image ends up.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class NormalizationPixelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val normalizer = PhotoNormalizer(Dispatchers.Unconfined)

    /** Corner colors as R(ed), G(reen), B(lue), Y(ellow): top-left, top-right, bottom-left, bottom-right. */
    private data class Corners(val topLeft: Char, val topRight: Char, val bottomLeft: Char, val bottomRight: Char) {
        fun mirrored() = Corners(topRight, topLeft, bottomRight, bottomLeft)
    }

    // The stored image is R G / B Y; each orientation's expected upright result follows the EXIF definition.
    private val upright = mapOf(
        ExifInterface.ORIENTATION_NORMAL to Corners('R', 'G', 'B', 'Y'),
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL to Corners('G', 'R', 'Y', 'B'),
        ExifInterface.ORIENTATION_ROTATE_180 to Corners('Y', 'B', 'G', 'R'),
        ExifInterface.ORIENTATION_FLIP_VERTICAL to Corners('B', 'Y', 'R', 'G'),
        ExifInterface.ORIENTATION_TRANSPOSE to Corners('R', 'B', 'G', 'Y'),
        ExifInterface.ORIENTATION_ROTATE_90 to Corners('B', 'R', 'Y', 'G'),
        ExifInterface.ORIENTATION_TRANSVERSE to Corners('Y', 'G', 'B', 'R'),
        ExifInterface.ORIENTATION_ROTATE_270 to Corners('G', 'Y', 'R', 'B'),
    )
    private val flipVariants = setOf(
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
        ExifInterface.ORIENTATION_FLIP_VERTICAL,
        ExifInterface.ORIENTATION_TRANSPOSE,
        ExifInterface.ORIENTATION_TRANSVERSE,
    )

    @Test
    fun backCamera_isTurnedUprightForEveryOrientation() = runBlocking {
        for ((orientation, expected) in upright) {
            assertEquals("orientation $orientation", expected, normalizedCorners(orientation, isFrontCamera = false))
        }
    }

    @Test
    fun frontCamera_isMirroredExactlyOnce() = runBlocking {
        for ((orientation, upright) in upright) {
            // A flip already in EXIF is the mirroring; otherwise the normalizer adds it.
            val expected = if (orientation in flipVariants) upright else upright.mirrored()
            assertEquals("orientation $orientation", expected, normalizedCorners(orientation, isFrontCamera = true))
        }
    }

    @Test
    fun rotatedOrientations_swapWidthAndHeight() = runBlocking {
        val file = taggedPhoto(ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(PhotoSize(HEIGHT, WIDTH), normalizer.normalize(file, isFrontCamera = false))
        assertEquals(ExifInterface.ORIENTATION_UNDEFINED, ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED))
    }

    private suspend fun normalizedCorners(orientation: Int, isFrontCamera: Boolean): Corners {
        val file = taggedPhoto(orientation)
        normalizer.normalize(file, isFrontCamera)
        val result = BitmapFactory.decodeFile(file.path)
        val inset = 6
        return Corners(
            topLeft = colorName(result.getPixel(inset, inset)),
            topRight = colorName(result.getPixel(result.width - 1 - inset, inset)),
            bottomLeft = colorName(result.getPixel(inset, result.height - 1 - inset)),
            bottomRight = colorName(result.getPixel(result.width - 1 - inset, result.height - 1 - inset)),
        )
    }

    private fun taggedPhoto(orientation: Int): File {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        Canvas(bitmap).apply {
            val paint = Paint()
            fun quadrant(color: Int, left: Int, top: Int) {
                paint.color = color
                drawRect(left.toFloat(), top.toFloat(), (left + WIDTH / 2).toFloat(), (top + HEIGHT / 2).toFloat(), paint)
            }
            quadrant(Color.RED, 0, 0)
            quadrant(Color.GREEN, WIDTH / 2, 0)
            quadrant(Color.BLUE, 0, HEIGHT / 2)
            quadrant(Color.YELLOW, WIDTH / 2, HEIGHT / 2)
        }
        val file = tempFolder.newFile()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file
    }

    private fun colorName(pixel: Int): Char {
        val red = Color.red(pixel) > 128
        val green = Color.green(pixel) > 128
        val blue = Color.blue(pixel) > 128
        return when {
            red && green && !blue -> 'Y'
            red && !green && !blue -> 'R'
            !red && green && !blue -> 'G'
            !red && !green && blue -> 'B'
            else -> '?'
        }
    }

    private companion object {
        const val WIDTH = 80
        const val HEIGHT = 40
    }
}
