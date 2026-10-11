package app.storymaker.media

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerGeometryTest {

    private val delta = 1e-3f
    private val rect = PhotoRect(left = 100f, top = 0f, width = 400f, height = 800f)
    private val sticker = StickerLayer("s", "done", centerX = 0.5f, centerY = 0.5f, scale = 0.25f, rotation = 0f)

    private fun assertOffset(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, delta)
        assertEquals(expected.y, actual.y, delta)
    }

    @Test
    fun frame_sizeComesFromWidthFractionAndAspectRatio() {
        val frame = stickerFrame(sticker, rect, aspectRatio = 0.5f)
        assertEquals(StickerFrame(300f, 400f, 100f, 50f, 0f), frame)
    }

    @Test
    fun corners_followRotation() {
        val frame = StickerFrame(centerX = 0f, centerY = 0f, width = 20f, height = 10f, rotation = 90f)
        val (topLeft, topRight, bottomRight, bottomLeft) = frame.corners()
        // Rotated 90° clockwise (y down): the top-left corner moves to the top-right.
        assertOffset(Offset(5f, -10f), topLeft)
        assertOffset(Offset(5f, 10f), topRight)
        assertOffset(Offset(-5f, 10f), bottomRight)
        assertOffset(Offset(-5f, -10f), bottomLeft)
    }

    @Test
    fun contains_respectsRotation() {
        val frame = StickerFrame(centerX = 0f, centerY = 0f, width = 100f, height = 20f, rotation = 90f)
        assertTrue(frame.contains(Offset(0f, 45f)))
        assertFalse(frame.contains(Offset(45f, 0f)))
        assertTrue(frame.contains(Offset(12f, 0f), slop = 4f))
        assertFalse(frame.contains(Offset(15f, 0f), slop = 4f))
    }

    @Test
    fun hitTest_returnsTopmostSticker() {
        val bottom = sticker.copy(id = "bottom")
        val top = sticker.copy(id = "top", centerX = 0.55f)
        val stroke = StrokeLayer("stroke", 0, 0.01f, listOf(0f, 0f))
        val layers = listOf(bottom, stroke, top)
        val frameOf = { layer: StickerLayer -> stickerFrame(layer, rect, 1f) }

        assertEquals("top", hitTestSticker(layers, Offset(310f, 400f), frameOf = frameOf)?.id)
        assertEquals("bottom", hitTestSticker(layers, Offset(255f, 400f), frameOf = frameOf)?.id)
        assertNull(hitTestSticker(layers, Offset(120f, 50f), frameOf = frameOf))
        // Stickers without artwork can't be hit.
        assertNull(hitTestSticker(layers, Offset(310f, 400f)) { null })
    }

    @Test
    fun transformed_appliesPanZoomAndRotation() {
        val result = sticker.transformed(pan = Offset(40f, -80f), zoom = 2f, rotationDelta = 30f, rect = rect)
        assertEquals(0.6f, result.centerX, delta)
        assertEquals(0.4f, result.centerY, delta)
        assertEquals(0.5f, result.scale, delta)
        assertEquals(30f, result.rotation, delta)
    }

    @Test
    fun transformed_clampsScaleAndCenter() {
        val tiny = sticker.transformed(Offset.Zero, zoom = 0.01f, rotationDelta = 0f, rect = rect)
        assertEquals(MIN_STICKER_SCALE, tiny.scale, delta)
        val huge = sticker.transformed(Offset.Zero, zoom = 100f, rotationDelta = 0f, rect = rect)
        assertEquals(MAX_STICKER_SCALE, huge.scale, delta)
        val offPhoto = sticker.transformed(Offset(10_000f, -10_000f), zoom = 1f, rotationDelta = 0f, rect = rect)
        assertEquals(1f, offPhoto.centerX, delta)
        assertEquals(0f, offPhoto.centerY, delta)
    }

    @Test
    fun transformed_keepsRotationInHalfTurnRange() {
        val result = sticker.copy(rotation = 170f).transformed(Offset.Zero, 1f, 30f, rect)
        assertEquals(-160f, result.rotation, delta)
    }

    @Test
    fun handleDragged_doublingDistanceDoublesScale() {
        // Center is at (300, 400) on screen.
        val result = sticker.handleDragged(start = Offset(350f, 400f), current = Offset(400f, 400f), rect = rect)
        assertEquals(0.5f, result.scale, delta)
        assertEquals(0f, result.rotation, delta)
    }

    @Test
    fun handleDragged_quarterTurnAddsNinetyDegrees() {
        val result = sticker.handleDragged(start = Offset(350f, 400f), current = Offset(300f, 450f), rect = rect)
        assertEquals(0.25f, result.scale, delta)
        assertEquals(90f, result.rotation, delta)
    }

    @Test
    fun handleDragged_neverMovesTheCenter() {
        val result = sticker.handleDragged(Offset(350f, 400f), Offset(420f, 470f), rect)
        assertEquals(sticker.centerX, result.centerX, delta)
        assertEquals(sticker.centerY, result.centerY, delta)
    }
}
