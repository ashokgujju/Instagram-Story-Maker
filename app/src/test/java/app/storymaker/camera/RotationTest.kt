package app.storymaker.camera

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class RotationTest {

    @Test
    fun deviceAngle_mapsToCameraTargetRotation() {
        assertEquals(Surface.ROTATION_0, surfaceRotationFor(0))
        assertEquals(Surface.ROTATION_0, surfaceRotationFor(30))
        assertEquals(Surface.ROTATION_0, surfaceRotationFor(330))
        // Turned clockwise onto its right side.
        assertEquals(Surface.ROTATION_270, surfaceRotationFor(90))
        assertEquals(Surface.ROTATION_180, surfaceRotationFor(180))
        // Turned counter-clockwise onto its left side.
        assertEquals(Surface.ROTATION_90, surfaceRotationFor(270))
    }

    @Test
    fun boundaries_switchAtFortyFiveDegrees() {
        assertEquals(Surface.ROTATION_0, surfaceRotationFor(44))
        assertEquals(Surface.ROTATION_270, surfaceRotationFor(45))
        assertEquals(Surface.ROTATION_180, surfaceRotationFor(135))
        assertEquals(Surface.ROTATION_90, surfaceRotationFor(225))
        assertEquals(Surface.ROTATION_0, surfaceRotationFor(315))
    }
}
