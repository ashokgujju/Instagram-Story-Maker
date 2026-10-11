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
 * Caption sticker metrics, in pixels of a [STORY_WIDTH]-wide Story. They match the design's
 * caption label (white rounded box, dark Manrope 700 text) scaled from the 290px mockup.
 */
object CaptionStickerSpec {
    const val STORY_WIDTH = 1080
    const val TEXT_SIZE = 56f
    const val MIN_TEXT_SIZE = 40f
    const val TEXT_SIZE_STEP = 2f
    const val PADDING_HORIZONTAL = 52f
    const val PADDING_VERTICAL = 30f
    const val CORNER_RADIUS = 37f
    /** 80% of the Story width. */
    const val MAX_BOX_WIDTH = 864f
    const val MAX_LINES = 6
    const val LINE_SPACING = 1.1f
    /** Transparent space around the box so its corners are never clipped. */
    const val MARGIN = 24f
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
        val text = normalizeCaption(caption)
        if (text.isEmpty()) return null
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CaptionStickerSpec.TEXT_COLOR
            typeface = ResourcesCompat.getFont(context, R.font.manrope)
            fontVariationSettings = "'wght' 700"
        }
        val layout = fitLayout(text, paint)
        val contentWidth = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }
        val boxWidth = ceil(contentWidth + 2 * CaptionStickerSpec.PADDING_HORIZONTAL)
        val boxHeight = ceil(layout.height + 2 * CaptionStickerSpec.PADDING_VERTICAL)
        val margin = CaptionStickerSpec.MARGIN

        val bitmap = createBitmap((boxWidth + 2 * margin).toInt(), (boxHeight + 2 * margin).toInt())
        val canvas = Canvas(bitmap)
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CaptionStickerSpec.BOX_COLOR }
        canvas.drawRoundRect(
            RectF(margin, margin, margin + boxWidth, margin + boxHeight),
            CaptionStickerSpec.CORNER_RADIUS,
            CaptionStickerSpec.CORNER_RADIUS,
            boxPaint,
        )
        // The layout is centered within its own width; center that width on the box.
        val left = margin + boxWidth / 2 - layout.width / 2f
        canvas.withTranslation(left, margin + CaptionStickerSpec.PADDING_VERTICAL) { layout.draw(this) }
        return bitmap
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
