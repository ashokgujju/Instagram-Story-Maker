package app.storymaker.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import app.storymaker.media.CaptionRenderer
import app.storymaker.media.ExportTarget
import app.storymaker.media.ImageExporter
import app.storymaker.media.SessionSnapshot
import app.storymaker.stickers.BundledStickerRepository
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the caption sticker renderer and the image exporter output.
 * Robolectric may lack some glyphs (emoji, some scripts); those images are regression baselines,
 * and the visual check of those cases happens on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class RenderScreenshotTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val captionRenderer = CaptionRenderer(context, Dispatchers.Unconfined)

    private fun captionSticker(name: String, caption: String) {
        checkNotNull(captionRenderer.renderNow(caption)).captureRoboImage("$SCREENSHOTS/caption_$name.png")
    }

    @Test
    fun captionShort() = captionSticker("short", "Done!")

    @Test
    fun captionLong() = captionSticker("long", "Shipped the new onboarding flow today, huge thanks to everyone who reviewed it")

    @Test
    fun captionMaxLength() = captionSticker("max_length", "a".repeat(10).let { word -> List(15) { word }.joinToString(" ") }.take(150))

    @Test
    fun captionMultiLine() = captionSticker("multi_line", "Sprint 12\nAll tickets closed\n\n\n\nRetro on Friday")

    @Test
    fun captionEmojiOnly() = captionSticker("emoji_only", "🚀🎉✅")

    @Test
    fun captionRightToLeft() = captionSticker("rtl", "تم الإطلاق اليوم")

    @Test
    fun exporterOutput() = runBlocking {
        val photo = File(tempFolder.root, "capture.jpg")
        photo.outputStream().use { fixturePhoto(1080, 1920).compress(Bitmap.CompressFormat.JPEG, 95, it) }
        val snapshot = SessionSnapshot(sessionId = "s", createdAt = 0, photoWidth = 1080, photoHeight = 1920, layers = fixtureLayers)
        val exporter = ImageExporter(context, Dispatchers.Unconfined, BundledStickerRepository())

        exporter.render(snapshot, photo, ExportTarget.Thumbnail).captureRoboImage("$SCREENSHOTS/export_thumbnail.png")
    }
}
