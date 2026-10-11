package app.storymaker.camera

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.storymaker.MainActivity
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end on a real device: grants camera access through the app's own permission flow (the
 * system dialog is answered with UiAutomator, since some OEMs block programmatic grants), then the
 * shutter takes a real photo, which lands in a session folder and opens the Editor.
 */
@RunWith(AndroidJUnit4::class)
class CaptureFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun shutter_capturesIntoSessionAndOpensEditor() {
        grantCameraIfAsked()
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithContentDescription("Take photo").fetchSemanticsNodes().isNotEmpty()
        }
        // Give the camera a moment to start streaming before capturing.
        Thread.sleep(1_500)
        compose.onNodeWithContentDescription("Take photo").performClick()

        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithContentDescription("Undo").fetchSemanticsNodes().isNotEmpty()
        }

        val root = compose.activity.noBackupFilesDir
        val sessionId = File(root, "active_session").readText().trim()
        val capture = File(root, "sessions/$sessionId/capture.jpg")
        assertTrue("capture.jpg missing for session $sessionId", capture.length() > 0)
    }

    private fun grantCameraIfAsked() {
        compose.waitForIdle()
        if (compose.onAllNodesWithText("Allow camera access").fetchSemanticsNodes().isEmpty()) return
        compose.onNodeWithText("Continue").performClick()
        val allow = device.wait(Until.findObject(By.text("While using the app")), TIMEOUT_MILLIS)
            ?: device.wait(Until.findObject(By.textContains("While using")), TIMEOUT_MILLIS)
        checkNotNull(allow) { "System permission dialog not found" }.click()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
