package app.storymaker.share

import org.junit.Assert.assertEquals
import org.junit.Test

class CoachMarkTest {

    @Test
    fun linesFollowWhatTheShareIncludes() {
        assertEquals(
            listOf(CoachMarkLine.MoveSticker, CoachMarkLine.PasteCaption),
            coachMarkLines(stickerShown = true, captionCopied = true),
        )
        assertEquals(listOf(CoachMarkLine.MoveSticker), coachMarkLines(stickerShown = true, captionCopied = false))
        assertEquals(listOf(CoachMarkLine.PasteCaption), coachMarkLines(stickerShown = false, captionCopied = true))
    }

    @Test
    fun nothingToExplain_skipsTheCoachMark() {
        assertEquals(emptyList<CoachMarkLine>(), coachMarkLines(stickerShown = false, captionCopied = false))
    }
}
