package app.storymaker.share

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class InstagramIntentTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val request = StoryShareRequest(
        backgroundUri = "content://app.storymaker.fileprovider/share/story_1.jpg",
        stickerUri = "content://app.storymaker.fileprovider/share/caption_1.png",
        topColor = "#F4C7A1",
        bottomColor = "#26392E",
        facebookAppId = "1234",
    )

    @Test
    fun intent_opensStoriesComposerDirectly() {
        val intent = request.toIntent()

        assertEquals("com.instagram.share.ADD_TO_STORY", intent.action)
        assertEquals("com.instagram.android", intent.`package`)
        assertEquals(Uri.parse(request.backgroundUri), intent.data)
        assertEquals("image/jpeg", intent.type)
        assertEquals("1234", intent.getStringExtra("source_application"))
        assertEquals("#F4C7A1", intent.getStringExtra("top_background_color"))
        assertEquals("#26392E", intent.getStringExtra("bottom_background_color"))
        @Suppress("DEPRECATION")
        assertEquals(Uri.parse(request.stickerUri), intent.getParcelableExtra<Uri>("interactive_asset_uri"))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun intent_withoutSticker_hasNoStickerExtra() {
        @Suppress("DEPRECATION")
        assertNull(request.copy(stickerUri = null).toIntent().getParcelableExtra<Uri>("interactive_asset_uri"))
    }

    @Test
    fun availability_followsWhetherInstagramHandlesTheIntent() {
        assertFalse(InstagramStories.isAvailable(context))

        val component = ComponentName("com.instagram.android", "com.instagram.share.AddToStoryActivity")
        val packageManager = shadowOf(context.packageManager)
        packageManager.addActivityIfNotPresent(component)
        packageManager.addIntentFilterForActivity(
            component,
            IntentFilter("com.instagram.share.ADD_TO_STORY").apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataType("image/jpeg")
            },
        )

        assertTrue(InstagramStories.isAvailable(context))
    }
}
