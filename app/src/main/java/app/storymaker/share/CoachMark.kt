package app.storymaker.share

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** What the first-share coach mark explains. */
enum class CoachMarkLine {
    /** The caption arrives as a sticker that can be moved and resized in Instagram. */
    MoveSticker,

    /** The caption is on the clipboard and can be pasted as Instagram text. */
    PasteCaption,
}

/** Lines to show for this share; empty means there is nothing to explain, so the coach mark is skipped. */
fun coachMarkLines(stickerShown: Boolean, captionCopied: Boolean): List<CoachMarkLine> = buildList {
    if (stickerShown) add(CoachMarkLine.MoveSticker)
    if (captionCopied) add(CoachMarkLine.PasteCaption)
}

/** Remembers whether the first-share coach mark was acknowledged. */
@Singleton
class CoachMarkStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("coach_marks", Context.MODE_PRIVATE)

    val seen: Boolean get() = preferences.getBoolean(KEY_FIRST_SHARE, false)

    fun markSeen() = preferences.edit { putBoolean(KEY_FIRST_SHARE, true) }

    private companion object {
        const val KEY_FIRST_SHARE = "first_share"
    }
}
