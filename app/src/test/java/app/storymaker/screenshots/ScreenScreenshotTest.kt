package app.storymaker.screenshots

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.core.app.ApplicationProvider
import app.storymaker.camera.CaptureContent
import app.storymaker.camera.CaptureUiState
import app.storymaker.camera.PermissionContent
import app.storymaker.camera.PermissionDeniedContent
import app.storymaker.caption.CaptionContent
import app.storymaker.caption.CaptionUiState
import app.storymaker.editor.EditorActions
import app.storymaker.editor.EditorContent
import app.storymaker.editor.EditorUiState
import app.storymaker.media.CaptionRenderer
import app.storymaker.media.CaptionStickerSpec
import app.storymaker.media.EditorTab
import app.storymaker.share.CaptionStickerPreview
import app.storymaker.share.SaveState
import app.storymaker.share.ShareContent
import app.storymaker.stickers.StickerCatalog
import app.storymaker.ui.theme.StoryMakerTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of every screen in its main states, at the design's 390×844dp frame.
 * Record with `./gradlew recordRoborazziDebug`, verify with `./gradlew verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
class ScreenScreenshotTest {

    private val photo = fixturePhoto().asImageBitmap()
    private val noActions = EditorActions({}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, { _, _ -> }, {})

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) =
        captureRoboImage("$SCREENSHOTS/$name.png") { StoryMakerTheme { content() } }

    @Test
    fun permission() = capture("permission") {
        PermissionContent(onContinue = {}, onNotNow = {}, onClose = {})
    }

    @Test
    fun permissionDenied() = capture("permission_denied") {
        PermissionDeniedContent(onOpenSettings = {}, onNotNow = {}, onClose = {})
    }

    @Test
    fun capture() = capture("capture") {
        CaptureContent(
            uiState = CaptureUiState(hasFlashUnit = true),
            onClose = {},
            onFlash = {},
            onShutter = {},
            onSwitchCamera = {},
            onRetry = {},
            viewfinder = { Box(Modifier.fillMaxSize().background(Color(0xFFF4C7A1))) },
        )
    }

    @Test
    fun editorDraw() = capture("editor_draw") {
        EditorContent(
            uiState = EditorUiState(photo = photo, layers = fixtureLayers, canUndo = true, stickers = StickerCatalog.all),
            actions = noActions,
        )
    }

    @Test
    fun editorStickers() = capture("editor_stickers") {
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
    fun captionEmpty() = capture("caption_empty") {
        CaptionContent(
            uiState = CaptionUiState(loaded = true, thumbnail = photo),
            fieldState = TextFieldState(),
            onBack = {},
            onContinue = {},
            onShowOnPhotoChange = {},
            onCopyToClipboardChange = {},
        )
    }

    @Test
    fun captionFilledWithMentions() = capture("caption_mentions") {
        val caption = "Launch day with the @team #shipit"
        CaptionContent(
            uiState = CaptionUiState(loaded = true, thumbnail = photo, caption = caption, copyToClipboard = true),
            fieldState = TextFieldState(caption),
            onBack = {},
            onContinue = {},
            onShowOnPhotoChange = {},
            onCopyToClipboardChange = {},
        )
    }

    @Test
    fun shareWithoutCaption() = capture("share") {
        ShareContent(preview = photo, captionSticker = null, saveState = SaveState.Idle, onBack = {}, onNewPhoto = {}, onSave = {})
    }

    @Test
    fun shareWithCaption() {
        val renderer = CaptionRenderer(ApplicationProvider.getApplicationContext(), Dispatchers.Unconfined)
        val sticker = checkNotNull(renderer.renderNow("Launch day with the @team #shipit"))
        val preview = CaptionStickerPreview(sticker.asImageBitmap(), sticker.width.toFloat() / CaptionStickerSpec.STORY_WIDTH)
        capture("share_caption") {
            ShareContent(preview = photo, captionSticker = preview, saveState = SaveState.Saved, onBack = {}, onNewPhoto = {}, onSave = {})
        }
    }
}

internal const val SCREENSHOTS = "src/test/screenshots"
