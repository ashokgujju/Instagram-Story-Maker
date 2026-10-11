package app.storymaker.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.graphics.asAndroidPath
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import app.storymaker.IoDispatcher
import app.storymaker.stickers.StickerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Output sizes (D4): the long side is capped, and images are never enlarged. */
enum class ExportTarget(val maxLongSide: Int, val jpegQuality: Int) {
    /** Instagram background; Instagram recompresses to ~1080×1920 anyway. */
    Share(maxLongSide = 1920, jpegQuality = 90),

    /** Gallery copy at full capture resolution, capped to bound memory use. */
    Gallery(maxLongSide = 4096, jpegQuality = 95),

    /** On-screen thumbnail (Caption screen); never saved. */
    Thumbnail(maxLongSide = 720, jpegQuality = 85),
}

/** Size of an export of a [width] × [height] photo whose long side is capped at [maxLongSide]. */
fun exportSize(width: Int, height: Int, maxLongSide: Int): PhotoSize {
    val longSide = max(width, height)
    if (longSide <= maxLongSide) return PhotoSize(width, height)
    val scale = maxLongSide.toFloat() / longSide
    return PhotoSize((width * scale).roundToInt(), (height * scale).roundToInt())
}

class ExportException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Flattens the photo and its layers into a bitmap by replaying the layers (D4), using the same
 * stroke and sticker geometry as the editor so the output matches the screen.
 */
class ImageExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val stickerRepository: StickerRepository,
) {
    /** Renders [snapshot] onto [photo] at [target]'s size. Throws [ExportException] if it can't. */
    suspend fun render(snapshot: SessionSnapshot, photo: File, target: ExportTarget): Bitmap = withContext(io) {
        try {
            renderAt(snapshot, photo, target.maxLongSide)
        } catch (e: OutOfMemoryError) {
            try {
                renderAt(snapshot, photo, target.maxLongSide / 2)
            } catch (e: OutOfMemoryError) {
                throw ExportException("Not enough memory to render the photo", e)
            }
        }
    }

    private fun renderAt(snapshot: SessionSnapshot, photo: File, maxLongSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photo.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ExportException("Unreadable photo")
        val size = exportSize(bounds.outWidth, bounds.outHeight, maxLongSide)
        val decoded = BitmapFactory.decodeFile(
            photo.path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSizeAtLeast(max(bounds.outWidth, bounds.outHeight), max(size.width, size.height))
            },
        ) ?: throw ExportException("Unreadable photo")

        val output = createBitmap(size.width, size.height)
        val canvas = Canvas(output)
        canvas.drawBitmap(decoded, null, Rect(0, 0, size.width, size.height), Paint(Paint.FILTER_BITMAP_FLAG))
        decoded.recycle()

        val rect = PhotoRect(0f, 0f, size.width.toFloat(), size.height.toFloat())
        for (layer in snapshot.layers) {
            when (layer) {
                is StrokeLayer -> canvas.drawStroke(layer, rect)
                is StickerLayer -> canvas.drawSticker(layer, rect)
            }
        }
        return output
    }

    private fun Canvas.drawStroke(layer: StrokeLayer, rect: PhotoRect) {
        val segments = smoothStroke(layer.points)
        val width = layer.width * rect.width
        val dot = segments.singleOrNull() as? StrokeSegment.Dot
        if (dot != null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = layer.color; style = Paint.Style.FILL }
            drawCircle(rect.fromNormalizedX(dot.x), rect.fromNormalizedY(dot.y), width / 2, paint)
            return
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layer.color
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        drawPath(strokePath(segments, rect).asAndroidPath(), paint)
    }

    /** Draws the sticker's vector artwork rasterized at its exact output size, so it stays sharp. */
    private fun Canvas.drawSticker(layer: StickerLayer, rect: PhotoRect) {
        val sticker = stickerRepository.find(layer.stickerId) ?: return
        val drawable = ContextCompat.getDrawable(context, sticker.drawable)?.mutate() ?: return
        val aspectRatio = if (drawable.intrinsicWidth > 0) {
            drawable.intrinsicHeight.toFloat() / drawable.intrinsicWidth
        } else {
            1f
        }
        val frame = stickerFrame(layer, rect, aspectRatio)
        withSave {
            rotate(frame.rotation, frame.centerX, frame.centerY)
            drawable.setBounds(
                (frame.centerX - frame.width / 2).roundToInt(),
                (frame.centerY - frame.height / 2).roundToInt(),
                (frame.centerX + frame.width / 2).roundToInt(),
                (frame.centerY + frame.height / 2).roundToInt(),
            )
            drawable.draw(this)
        }
    }
}

/** Writes this bitmap as a JPEG. Throws [IOException] if encoding fails. */
fun Bitmap.writeJpeg(out: OutputStream, quality: Int) {
    if (!compress(Bitmap.CompressFormat.JPEG, quality, out)) throw IOException("JPEG encoding failed")
}
