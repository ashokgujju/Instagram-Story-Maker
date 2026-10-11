package app.storymaker.media

import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationTransformTest {

    // EXIF orientation values 1–8.
    private val expectedForBackCamera = mapOf(
        1 to OrientationTransform(0, false), // normal
        2 to OrientationTransform(0, true), // flip horizontal
        3 to OrientationTransform(180, false), // rotate 180
        4 to OrientationTransform(180, true), // flip vertical
        5 to OrientationTransform(90, true), // transpose
        6 to OrientationTransform(90, false), // rotate 90
        7 to OrientationTransform(270, true), // transverse
        8 to OrientationTransform(270, false), // rotate 270
    )

    @Test
    fun backCamera_appliesExifOrientationOnly() {
        for ((orientation, expected) in expectedForBackCamera) {
            assertEquals("orientation $orientation", expected, orientationTransform(orientation, isFrontCamera = false))
        }
    }

    @Test
    fun frontCamera_isAlwaysMirroredExactlyOnce() {
        for ((orientation, backTransform) in expectedForBackCamera) {
            val front = orientationTransform(orientation, isFrontCamera = true)
            assertEquals("rotation for $orientation", backTransform.rotationDegrees, front.rotationDegrees)
            // Whether or not EXIF already encodes the flip, the result is mirrored once, never twice.
            assertEquals("flip for $orientation", true, front.flipHorizontal)
        }
    }

    @Test
    fun missingOrUnknownOrientation_isTreatedAsNormal() {
        assertEquals(OrientationTransform(0, false), orientationTransform(0, isFrontCamera = false))
        assertEquals(OrientationTransform(0, false), orientationTransform(42, isFrontCamera = false))
    }

    @Test
    fun sampleSizeAtMost_keepsLongSideWithinLimit() {
        assertEquals(1, sampleSizeAtMost(3840, 4096))
        assertEquals(2, sampleSizeAtMost(5000, 4096))
        assertEquals(4, sampleSizeAtMost(16384 + 1, 4096))
    }

    @Test
    fun sampleSizeAtLeast_neverDropsBelowTarget() {
        assertEquals(1, sampleSizeAtLeast(1000, 1920))
        assertEquals(1, sampleSizeAtLeast(3839, 1920))
        assertEquals(2, sampleSizeAtLeast(3840, 1920))
    }
}
