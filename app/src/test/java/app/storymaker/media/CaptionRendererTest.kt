package app.storymaker.media

import android.app.Application
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class CaptionRendererTest {

    private val renderer = CaptionRenderer(ApplicationProvider.getApplicationContext(), Dispatchers.Unconfined)
    private val maxBitmapWidth = CaptionStickerSpec.MAX_BOX_WIDTH + 2 * CaptionStickerSpec.MARGIN

    @Test
    fun emptyOrWhitespaceCaption_rendersNothing() {
        assertNull(renderer.renderNow(""))
        assertNull(renderer.renderNow("  \n "))
    }

    @Test
    fun everySticker_hasTheFixedCanvasWidth() {
        assertEquals(CaptionStickerSpec.CANVAS_WIDTH, renderer.renderNow("Done!")!!.width)
        assertEquals(CaptionStickerSpec.CANVAS_WIDTH, renderer.renderNow("word ".repeat(30))!!.width)
    }

    @Test
    fun shortCaption_boxHugsItsTextOnTransparentCanvas() {
        val bitmap = renderer.renderNow("Done!")!!
        val middle = bitmap.height / 2
        // The box is centered and narrow: the canvas is transparent well inside its left and right edges.
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(bitmap.width / 4, middle))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(bitmap.width * 3 / 4, middle))
        assertEquals(Color.WHITE, bitmap.getPixel(bitmap.width / 2, (CaptionStickerSpec.MARGIN + 4).toInt()))
    }

    @Test
    fun longCaption_staysWithinMaximumWidth() {
        val bitmap = renderer.renderNow("word ".repeat(30))!!
        assertTrue(bitmap.width <= maxBitmapWidth + 1)
    }

    @Test
    fun maxLengthCaptions_fitWithinSixLines() {
        val worstCases = listOf(
            "a".repeat(CAPTION_MAX_LENGTH),
            List(15) { "a".repeat(10) }.joinToString(" ").take(CAPTION_MAX_LENGTH),
            List(30) { "WWWW" }.joinToString(" ").take(CAPTION_MAX_LENGTH),
            "SHIPPED THE NEW ONBOARDING FLOW TODAY WITH THE WHOLE TEAM, HUGE THANKS TO EVERYONE WHO REVIEWED, TESTED AND CHEERED US ON THROUGH THE LAST SPRINT!!".take(CAPTION_MAX_LENGTH),
        )
        for (caption in worstCases) {
            val layout = renderer.layoutFor(caption)!!
            assertTrue("${layout.lineCount} lines for \"$caption\"", layout.lineCount <= CaptionStickerSpec.MAX_LINES)
            assertTrue(renderer.renderNow(caption)!!.width <= maxBitmapWidth + 1)
        }
    }

    @Test
    fun shortCaption_keepsFullTextSize() {
        assertEquals(CaptionStickerSpec.TEXT_SIZE, renderer.layoutFor("Done!")!!.paint.textSize)
    }

    @Test
    fun margin_isTransparent_andWideBoxIsWhite() {
        val bitmap = renderer.renderNow("word ".repeat(30))!!
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
        val inset = (CaptionStickerSpec.MARGIN + CaptionStickerSpec.CORNER_RADIUS).toInt()
        assertEquals(Color.WHITE, bitmap.getPixel(inset, bitmap.height / 2))
    }
}
