package app.storymaker.accessibility

import android.app.Application
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import app.storymaker.editor.EditorActions
import app.storymaker.editor.EditorContent
import app.storymaker.editor.EditorUiState
import app.storymaker.media.EditorTab
import app.storymaker.media.StickerLayer
import app.storymaker.screenshots.fixtureLayers
import app.storymaker.screenshots.fixturePhoto
import app.storymaker.stickers.StickerCatalog
import app.storymaker.ui.theme.StoryMakerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Screen reader alternatives to pinch, twist, and the handle for the selected sticker. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
class StickerAccessibilityActionsTest {

    @get:Rule
    val compose = createComposeRule()

    private val sticker = fixtureLayers.filterIsInstance<StickerLayer>().single()
    private val changes = mutableListOf<StickerLayer>()
    private val removed = mutableListOf<String>()

    private fun showSelectedSticker() {
        val actions = EditorActions(
            {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {},
            onStickerChanged = { _, after -> changes += after },
            onRemoveSticker = { removed += it },
        )
        compose.setContent {
            StoryMakerTheme {
                EditorContent(
                    uiState = EditorUiState(
                        photo = fixturePhoto().asImageBitmap(),
                        layers = fixtureLayers,
                        tab = EditorTab.Stickers,
                        stickers = StickerCatalog.all,
                        selectedLayerId = sticker.id,
                    ),
                    actions = actions,
                )
            }
        }
    }

    private fun perform(label: String) =
        compose.onNodeWithContentDescription("Photo, Done sticker selected").performCustomAccessibilityActionWithLabel(label)

    @Test
    fun canvas_announcesTheSelectedSticker() {
        showSelectedSticker()
        compose.onNodeWithContentDescription("Photo, Done sticker selected").assertExists()
    }

    @Test
    fun biggerAndSmaller_scaleTheSticker() {
        showSelectedSticker()
        perform("Make sticker bigger")
        perform("Make sticker smaller")
        assertEquals(sticker.scale * 1.25f, changes[0].scale, 1e-4f)
        assertEquals(sticker.scale / 1.25f, changes[1].scale, 1e-4f)
    }

    @Test
    fun rotate_turnsTheStickerBy15Degrees() {
        showSelectedSticker()
        perform("Rotate sticker left")
        perform("Rotate sticker right")
        assertEquals(sticker.rotation - 15f, changes[0].rotation, 1e-4f)
        assertEquals(sticker.rotation + 15f, changes[1].rotation, 1e-4f)
    }

    @Test
    fun remove_removesTheSticker() {
        showSelectedSticker()
        perform("Remove sticker")
        assertEquals(listOf(sticker.id), removed)
        assertTrue(changes.isEmpty())
    }
}
