package app.storymaker.stickers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerCatalogTest {

    private val stickers = StickerCatalog.all

    @Test
    fun catalog_hasAllFortyStickers() {
        assertEquals(40, stickers.size)
    }

    @Test
    fun ids_areUniqueAndWellFormed() {
        assertEquals(stickers.size, stickers.map { it.id }.toSet().size)
        stickers.forEach { assertTrue("bad id ${it.id}", it.id.matches(Regex("[a-z_]+"))) }
    }

    @Test
    fun drawablesAndNames_areUnique() {
        assertEquals(stickers.size, stickers.map { it.drawable }.toSet().size)
        assertEquals(stickers.size, stickers.map { it.name }.toSet().size)
    }

    @Test
    fun everyCategory_hasStickers() {
        StickerCategory.entries.forEach { category ->
            assertTrue("$category is empty", stickers.any { it.category == category })
        }
    }

    @Test
    fun everySticker_hasLowerCaseTags() {
        stickers.forEach { sticker ->
            assertTrue("${sticker.id} has no tags", sticker.tags.isNotEmpty())
            sticker.tags.forEach { assertEquals("${sticker.id} tag $it", it.lowercase(), it) }
        }
    }

    @Test
    fun repository_findsStickersById() {
        val repository = BundledStickerRepository()
        assertEquals("done", repository.find("done")?.id)
        assertEquals(null, repository.find("missing"))
    }
}
