package app.storymaker.stickers

import org.junit.Assert.assertEquals
import org.junit.Test

class StickerFilterTest {

    private val done = Sticker("done", 1, 11, StickerCategory.Status, listOf("complete", "check"))
    private val yes = Sticker("yes", 2, 12, StickerCategory.Reactions, listOf("agree"))
    private val chill = Sticker("chill", 3, 13, StickerCategory.Vibes, listOf("cool", "relax"))
    private val stickers = listOf(done, yes, chill)
    private val names = mapOf(done to "Done", yes to "Yes", chill to "Chill")

    private fun filter(query: String, category: StickerCategory? = null) =
        filterStickers(stickers, query, category) { names.getValue(it) }

    @Test
    fun blankQuery_withoutCategory_returnsEverything() {
        assertEquals(stickers, filter(""))
        assertEquals(stickers, filter("   "))
    }

    @Test
    fun query_matchesDisplayName() {
        assertEquals(listOf(chill), filter("chi"))
    }

    @Test
    fun query_matchesTags() {
        assertEquals(listOf(done), filter("complete"))
    }

    @Test
    fun query_isCaseInsensitiveAndTrimmed() {
        assertEquals(listOf(yes), filter("  AGREE "))
        assertEquals(listOf(done), filter("DONE"))
    }

    @Test
    fun category_filtersStickers() {
        assertEquals(listOf(yes), filter("", StickerCategory.Reactions))
    }

    @Test
    fun queryAndCategory_areCombined() {
        assertEquals(emptyList<Sticker>(), filter("cool", StickerCategory.Status))
        assertEquals(listOf(chill), filter("cool", StickerCategory.Vibes))
    }

    @Test
    fun noMatch_returnsEmpty() {
        assertEquals(emptyList<Sticker>(), filter("zebra"))
    }
}
