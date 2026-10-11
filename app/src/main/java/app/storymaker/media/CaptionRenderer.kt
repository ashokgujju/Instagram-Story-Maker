package app.storymaker.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import app.storymaker.IoDispatcher
import app.storymaker.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Caption sticker metrics, in pixels of the rendered sticker. Instagram displays every interactive
 * sticker at [INSTAGRAM_DISPLAY_WIDTH_FRACTION] of the Story width whatever its pixel size
 * (measured on a device in milestone 8), so stickers use a fixed [CANVAS_WIDTH] and the text metrics
 * are the design's caption label (scaled ×1080/290 from the mockup) divided by Instagram's resulting
 * display scale (0.887), so the label appears in Instagram at the design's size.
 */
object CaptionStickerSpec {
    /** Width at which Instagram shows an interactive sticker, as a fraction of the Story width. */
    const val INSTAGRAM_DISPLAY_WIDTH_FRACTION = 0.75f
    const val TEXT_SIZE = 63f
    /** Small enough that even 150 of the widest characters fit in [MAX_LINES] lines; normal captions never get near it. */
    const val MIN_TEXT_SIZE = 28f
    const val TEXT_SIZE_STEP = 2f
    const val PADDING_HORIZONTAL = 59f
    const val PADDING_VERTICAL = 34f
    const val CORNER_RADIUS = 42f
    /** 80% of the Story width. */
    const val MAX_BOX_WIDTH = 864f
    const val MAX_LINES = 6
    const val LINE_SPACING = 1.1f
    /** Transparent space around the box so its corners are never clipped. */
    const val MARGIN = 24f

    /**
     * Every sticker has this fixed width, with the box centered on a transparent canvas.
     * Instagram shows interactive stickers at a fixed fraction of the Story width whatever their
     * pixel size (measured in milestone 8), so a fixed canvas keeps the text size consistent for
     * short and long captions.
     */
    const val CANVAS_WIDTH = (MAX_BOX_WIDTH + 2 * MARGIN).toInt()
    const val BOX_COLOR = 0xFFFFFFFF.toInt()
    const val TEXT_COLOR = 0xFF111111.toInt()
}

/**
 * Renders the caption as a transparent PNG-ready bitmap: the Instagram interactive sticker (D1).
 * Text is centered and wrapped; if it needs more than [CaptionStickerSpec.MAX_LINES] lines the font
 * shrinks (down to [CaptionStickerSpec.MIN_TEXT_SIZE]) so the whole caption always fits.
 */
class CaptionRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** The sticker for [caption], or null when the normalized caption is empty. */
    suspend fun render(caption: String): Bitmap? = withContext(io) { renderNow(caption) }

    fun renderNow(caption: String): Bitmap? {
        val layout = layoutFor(caption) ?: return null
        val contentWidth = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }
        val boxWidth = ceil(contentWidth + 2 * CaptionStickerSpec.PADDING_HORIZONTAL)
        val boxHeight = ceil(layout.height + 2 * CaptionStickerSpec.PADDING_VERTICAL)
        val margin = CaptionStickerSpec.MARGIN

        val canvasWidth = CaptionStickerSpec.CANVAS_WIDTH
        val bitmap = createBitmap(canvasWidth, (boxHeight + 2 * margin).toInt())
        val canvas = Canvas(bitmap)
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CaptionStickerSpec.BOX_COLOR }
        val boxLeft = (canvasWidth - boxWidth) / 2
        canvas.drawRoundRect(
            RectF(boxLeft, margin, boxLeft + boxWidth, margin + boxHeight),
            CaptionStickerSpec.CORNER_RADIUS,
            CaptionStickerSpec.CORNER_RADIUS,
            boxPaint,
        )
        // The layout is centered within its own width; center that width on the canvas.
        val left = canvasWidth / 2f - layout.width / 2f
        canvas.withTranslation(left, margin + CaptionStickerSpec.PADDING_VERTICAL) { layout.draw(this) }
        return bitmap
    }

    /** The wrapped, size-fitted text layout for [caption], or null when the normalized caption is empty. */
    internal fun layoutFor(caption: String): StaticLayout? {
        val text = normalizeCaption(caption)
        if (text.isEmpty()) return null
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CaptionStickerSpec.TEXT_COLOR
            typeface = ResourcesCompat.getFont(context, R.font.manrope)
            fontVariationSettings = "'wght' 700"
        }
        return fitLayout(text, paint)
    }

    private fun fitLayout(text: String, paint: TextPaint): StaticLayout {
        val maxTextWidth = (CaptionStickerSpec.MAX_BOX_WIDTH - 2 * CaptionStickerSpec.PADDING_HORIZONTAL).toInt()
        var size = CaptionStickerSpec.TEXT_SIZE
        while (true) {
            paint.textSize = size
            val desired = ceil(Layout.getDesiredWidth(text, paint)).toInt()
            val width = desired.coerceIn(1, maxTextWidth)
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setLineSpacing(0f, CaptionStickerSpec.LINE_SPACING)
                .setIncludePad(false)
                .build()
            if (layout.lineCount <= CaptionStickerSpec.MAX_LINES || size <= CaptionStickerSpec.MIN_TEXT_SIZE) return layout
            size -= CaptionStickerSpec.TEXT_SIZE_STEP
        }
    }
}
