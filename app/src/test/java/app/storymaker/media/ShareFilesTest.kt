package app.storymaker.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShareFilesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val shareFiles by lazy { ShareFiles(tempFolder.root.resolve("share"), Dispatchers.Unconfined) }

    @Test
    fun fileName_combinesPrefixTimeAndRandom() {
        assertEquals("story_1700000000000_ab12cd34.jpg", shareFileName("story", "jpg", 1_700_000_000_000, "ab12cd34"))
    }

    @Test
    fun write_createsBackgroundAndStickerInShareFolder() = runBlocking {
        val images = shareFiles.write(writeBackground = { it.write(1) }, writeSticker = { it.write(2) })

        assertEquals("share", images.background.parentFile?.name)
        assertTrue(images.background.name.matches(Regex("story_\\d+_[0-9a-f]{8}\\.jpg")))
        assertTrue(images.sticker!!.name.matches(Regex("caption_\\d+_[0-9a-f]{8}\\.png")))
        assertEquals(listOf<Byte>(1), images.background.readBytes().toList())
        assertEquals(listOf<Byte>(2), images.sticker!!.readBytes().toList())
    }

    @Test
    fun write_withoutSticker_writesOnlyBackground() = runBlocking {
        val images = shareFiles.write(writeBackground = { it.write(1) }, writeSticker = null)
        assertNull(images.sticker)
        assertEquals(1, tempFolder.root.resolve("share").listFiles()!!.size)
    }

    @Test
    fun eachShare_getsNewFileNames() = runBlocking {
        val first = shareFiles.write({ it.write(1) }, { it.write(2) }, timeMillis = 1)
        val second = shareFiles.write({ it.write(1) }, { it.write(2) }, timeMillis = 1)
        assertNotEquals(first.background.name, second.background.name)
        assertNotEquals(first.sticker!!.name, second.sticker!!.name)
    }
}
