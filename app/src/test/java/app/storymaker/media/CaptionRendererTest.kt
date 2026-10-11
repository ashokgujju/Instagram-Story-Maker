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
    fun shortCaption_hugsItsText() {
        val bitmap = renderer.renderNow("Done!")!!
        assertTrue("short caption should be narrow, was ${bitmap.width}", bitmap.width < maxBitmapWidth / 2)
    }

    @Test
    fun longCaption_staysWithinMaximumWidth() {
        val bitmap = renderer.renderNow("word ".repeat(30))!!
        assertTrue(bitmap.width <= maxBitmapWidth + 1)
    }

    @Test
    fun maxLengthCaption_fitsWithinSixLines() {
        val bitmap = renderer.renderNow("a".repeat(CAPTION_MAX_LENGTH))!!
        // Six lines of the minimum text size plus padding and margins is the tallest a sticker can get.
        val maxHeight = 6 * CaptionStickerSpec.TEXT_SIZE * 1.6f + 2 * (CaptionStickerSpec.PADDING_VERTICAL + CaptionStickerSpec.MARGIN)
        assertTrue("height ${bitmap.height} exceeds $maxHeight", bitmap.height <= maxHeight)
        assertTrue(bitmap.width <= maxBitmapWidth + 1)
    }

    @Test
    fun margin_isTransparent_andBoxIsWhite() {
        val bitmap = renderer.renderNow("Hello")!!
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
        val inset = (CaptionStickerSpec.MARGIN + CaptionStickerSpec.CORNER_RADIUS).toInt()
        assertEquals(Color.WHITE, bitmap.getPixel(inset, bitmap.height / 2))
    }
}
