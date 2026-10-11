package app.storymaker.stickers

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.storymaker.R

enum class StickerCategory(@StringRes val label: Int) {
    Status(R.string.sticker_category_status),
    Reactions(R.string.sticker_category_reactions),
    Vibes(R.string.sticker_category_vibes),
}

/**
 * A bundled sticker.
 *
 * @param id stable identifier stored in session snapshots; never change it once shipped
 * @param name display name, also used as the content description
 * @param tags lower-case search keywords in addition to the name
 */
data class Sticker(
    val id: String,
    @StringRes val name: Int,
    @DrawableRes val drawable: Int,
    val category: StickerCategory,
    val tags: List<String>,
)

/**
 * Stickers matching [query] (case-insensitive, against the display name and tags) within
 * [category] (`null` = all). A blank query matches everything.
 */
fun filterStickers(
    stickers: List<Sticker>,
    query: String,
    category: StickerCategory?,
    nameOf: (Sticker) -> String,
): List<Sticker> {
    val needle = query.trim().lowercase()
    return stickers.filter { sticker ->
        (category == null || sticker.category == category) &&
            (needle.isEmpty() ||
                nameOf(sticker).lowercase().contains(needle) ||
                sticker.tags.any { it.contains(needle) })
    }
}
