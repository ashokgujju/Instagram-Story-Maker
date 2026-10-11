package app.storymaker.media

import org.junit.Assert.assertEquals
import org.junit.Test

class StrokeGeometryTest {

    private val delta = 1e-4f

    @Test
    fun fitRect_tallImageInWideContainer_isPillarboxed() {
        val rect = fitRect(imageWidth = 900, imageHeight = 1600, containerWidth = 1000f, containerHeight = 800f)
        assertEquals(PhotoRect(275f, 0f, 450f, 800f), rect)
    }

    @Test
    fun fitRect_wideImageInTallContainer_isLetterboxed() {
        val rect = fitRect(imageWidth = 1600, imageHeight = 900, containerWidth = 800f, containerHeight = 1000f)
        assertEquals(PhotoRect(0f, 275f, 800f, 450f), rect)
    }

    @Test
    fun fitRect_sameAspect_fillsContainer() {
        assertEquals(PhotoRect(0f, 0f, 540f, 960f), fitRect(1080, 1920, 540f, 960f))
    }

    @Test
    fun normalizedMapping_roundTripsThroughLetterbox() {
        val rect = PhotoRect(275f, 0f, 450f, 800f)
        assertEquals(0f, rect.toNormalizedX(275f), delta)
        assertEquals(1f, rect.toNormalizedX(725f), delta)
        assertEquals(0.5f, rect.toNormalizedY(400f), delta)
        assertEquals(500f, rect.fromNormalizedX(rect.toNormalizedX(500f)), delta)
        // Outside the photo maps outside 0..1 (strokes may run past the edge and are clipped when drawn).
        assertEquals(-0.1f, rect.toNormalizedX(230f), delta)
    }

    @Test
    fun strokeWidthFraction_isRelativeToPhotoWidth() {
        assertEquals(0.02f, strokeWidthFraction(brushSizePx = 9f, rect = PhotoRect(0f, 0f, 450f, 800f)), delta)
    }

    @Test
    fun roundNormalized_keepsFourDecimals() {
        assertEquals(listOf(0.1235f, 0.5f), roundNormalized(listOf(0.123456f, 0.5f)))
    }

    @Test
    fun smoothStroke_singlePoint_isDot() {
        assertEquals(listOf(StrokeSegment.Dot(0.2f, 0.3f)), smoothStroke(listOf(0.2f, 0.3f)))
    }

    @Test
    fun smoothStroke_twoPoints_isLine() {
        assertEquals(
            listOf(StrokeSegment.MoveTo(0f, 0f), StrokeSegment.LineTo(1f, 1f)),
            smoothStroke(listOf(0f, 0f, 1f, 1f)),
        )
    }

    @Test
    fun smoothStroke_manyPoints_usesQuadsThroughMidpoints() {
        val segments = smoothStroke(listOf(0f, 0f, 2f, 0f, 2f, 2f, 0f, 2f))
        assertEquals(
            listOf(
                StrokeSegment.MoveTo(0f, 0f),
                StrokeSegment.QuadTo(2f, 0f, 2f, 1f),
                StrokeSegment.QuadTo(2f, 2f, 1f, 2f),
                StrokeSegment.LineTo(0f, 2f),
            ),
            segments,
        )
    }

    @Test
    fun smoothStroke_empty_hasNoSegments() {
        assertEquals(emptyList<StrokeSegment>(), smoothStroke(emptyList()))
    }
}
