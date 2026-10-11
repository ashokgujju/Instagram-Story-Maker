package app.storymaker.screenshots

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import app.storymaker.ui.components.StoryDialog
import app.storymaker.ui.theme.Background
import app.storymaker.ui.theme.StoryMakerTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Dialogs render in their own window, so they are captured with a full-screen capture. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
class DialogScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun dialog(name: String, title: String, body: List<String>, confirm: String, dismiss: String?) {
        compose.setContent {
            StoryMakerTheme {
                Box(Modifier.fillMaxSize().background(Background))
                StoryDialog(title = title, body = body, confirmText = confirm, onConfirm = {}, onDismissRequest = {}, dismissText = dismiss)
            }
        }
        captureScreenRoboImage("$SCREENSHOTS/$name.png")
    }

    @Test
    fun coachMark() = dialog(
        name = "dialog_coach_mark",
        title = "Before you share",
        body = listOf(
            "Your caption is a sticker: drag, pinch or rotate it in Instagram.",
            "Your caption is copied: long-press in Instagram to paste it as text.",
        ),
        confirm = "Got it",
        dismiss = null,
    )

    @Test
    fun discard() = dialog(
        name = "dialog_discard",
        title = "Discard photo?",
        body = listOf("Your drawing will be lost."),
        confirm = "Discard",
        dismiss = "Keep editing",
    )
}
