package app.storymaker.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryShareRequestTest {

    private val request = StoryShareRequest(
        backgroundUri = "content://app.storymaker.fileprovider/share/story_1.jpg",
        stickerUri = "content://app.storymaker.fileprovider/share/caption_1.png",
        topColor = "#F4C7A1",
        bottomColor = "#26392E",
        facebookAppId = "1234",
    )

    @Test
    fun stringExtras_containAppIdAndColors() {
        assertEquals(
            mapOf(
                "source_application" to "1234",
                "top_background_color" to "#F4C7A1",
                "bottom_background_color" to "#26392E",
            ),
            request.stringExtras(),
        )
    }

    @Test
    fun sharedUris_includeStickerOnlyWhenPresent() {
        assertEquals(2, request.sharedUris().size)
        assertEquals(listOf(request.backgroundUri), request.copy(stickerUri = null).sharedUris())
    }

    @Test
    fun discardConfirmation_onlyWhenNeitherSavedNorShared() {
        assertTrue(needsDiscardConfirmation(saved = false, shared = false))
        assertFalse(needsDiscardConfirmation(saved = true, shared = false))
        assertFalse(needsDiscardConfirmation(saved = false, shared = true))
        assertFalse(needsDiscardConfirmation(saved = true, shared = true))
    }
}
