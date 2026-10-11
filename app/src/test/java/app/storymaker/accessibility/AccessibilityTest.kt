package app.storymaker.accessibility

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import app.storymaker.camera.CaptureContent
import app.storymaker.camera.CaptureUiState
import app.storymaker.camera.PermissionContent
import app.storymaker.camera.PermissionDeniedContent
import app.storymaker.caption.CaptionContent
import app.storymaker.caption.CaptionUiState
import app.storymaker.editor.EditorActions
import app.storymaker.editor.EditorContent
import app.storymaker.editor.EditorUiState
import app.storymaker.media.EditorTab
import app.storymaker.screenshots.fixtureLayers
import app.storymaker.screenshots.fixturePhoto
import app.storymaker.share.SaveState
import app.storymaker.share.ShareButtonState
import app.storymaker.share.ShareContent
import app.storymaker.stickers.StickerCatalog
import app.storymaker.ui.theme.StoryMakerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Accessibility checks on every screen: each clickable, toggleable, or selectable control must
 * have a label (text or content description) and a touch area of at least 48×48dp.
 *
 * Google's Accessibility Test Framework checks (`enableAccessibilityChecks`) were tried first, but
 * under Robolectric they report nothing, not even a deliberately broken control, so these checks
 * run on the Compose semantics tree instead.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class AccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val photo = fixturePhoto().asImageBitmap()
    private val noActions = EditorActions({}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, { _, _ -> }, {})

    private fun check(content: @Composable () -> Unit) {
        compose.setContent { StoryMakerTheme { content() } }
        compose.assertControlsAreAccessible()
    }

    @Test
    fun permission() = check { PermissionContent(onContinue = {}, onNotNow = {}, onClose = {}) }

    @Test
    fun permissionDenied() = check { PermissionDeniedContent(onOpenSettings = {}, onNotNow = {}, onClose = {}) }

    @Test
    fun capture() = check {
        CaptureContent(
            uiState = CaptureUiState(hasFlashUnit = true),
            onClose = {},
            onFlash = {},
            onShutter = {},
            onSwitchCamera = {},
            onRetry = {},
            viewfinder = { Box(Modifier.fillMaxSize().background(Color(0xFF7A6A5A))) },
        )
    }

    @Test
    fun editorDraw() = check {
        EditorContent(
            uiState = EditorUiState(photo = photo, layers = fixtureLayers, canUndo = true, canRedo = true, stickers = StickerCatalog.all),
            actions = noActions,
        )
    }

    @Test
    fun editorStickersWithSelection() = check {
        EditorContent(
            uiState = EditorUiState(
                photo = photo,
                layers = fixtureLayers,
                tab = EditorTab.Stickers,
                stickers = StickerCatalog.all,
                selectedLayerId = "sticker",
            ),
            actions = noActions,
        )
    }

    @Test
    fun caption() = check {
        CaptionContent(
            uiState = CaptionUiState(loaded = true, thumbnail = photo, caption = "Launch day @team"),
            fieldState = TextFieldState("Launch day @team"),
            onBack = {},
            onContinue = {},
            onShowOnPhotoChange = {},
            onCopyToClipboardChange = {},
        )
    }

    @Test
    fun share() = check {
        ShareContent(
            preview = photo,
            captionSticker = null,
            saveState = SaveState.Idle,
            shareButton = ShareButtonState.Ready,
            onBack = {},
            onNewPhoto = {},
            onShare = {},
            onSave = {},
        )
    }
}
