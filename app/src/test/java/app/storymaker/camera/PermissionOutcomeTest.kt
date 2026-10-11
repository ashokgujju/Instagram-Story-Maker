package app.storymaker.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionOutcomeTest {

    @Test
    fun granted_isGranted_regardlessOfRationale() {
        assertEquals(PermissionOutcome.Granted, permissionOutcome(granted = true, shouldShowRationale = false))
        assertEquals(PermissionOutcome.Granted, permissionOutcome(granted = true, shouldShowRationale = true))
    }

    @Test
    fun deniedWithRationale_canAskAgain() {
        assertEquals(PermissionOutcome.AskAgain, permissionOutcome(granted = false, shouldShowRationale = true))
    }

    @Test
    fun deniedWithoutRationale_isPermanent() {
        assertEquals(PermissionOutcome.PermanentlyDenied, permissionOutcome(granted = false, shouldShowRationale = false))
    }
}
