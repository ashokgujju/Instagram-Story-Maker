package app.storymaker.media

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTest {

    @Test
    fun exportSize_neverEnlarges() {
        assertEquals(PhotoSize(2160, 3840), exportSize(2160, 3840, maxLongSide = 4096))
        assertEquals(PhotoSize(1296, 2304), exportSize(1296, 2304, maxLongSide = 4096))
    }

    @Test
    fun exportSize_capsTheLongSideKeepingAspectRatio() {
        assertEquals(PhotoSize(1080, 1920), exportSize(2160, 3840, maxLongSide = 1920))
        assertEquals(PhotoSize(1920, 1080), exportSize(3840, 2160, maxLongSide = 1920))
        assertEquals(PhotoSize(4096, 3072), exportSize(8000, 6000, maxLongSide = 4096))
    }

    @Test
    fun exportSize_atExactLimit_isUnchanged() {
        assertEquals(PhotoSize(1080, 1920), exportSize(1080, 1920, maxLongSide = 1920))
    }

    @Test
    fun exportTargets_matchTheArchitecture() {
        assertEquals(1920, ExportTarget.Share.maxLongSide)
        assertEquals(4096, ExportTarget.Gallery.maxLongSide)
    }

    @Test
    fun galleryFileName_usesTimestamp() {
        assertEquals("StoryMaker_20261011_153012.jpg", galleryFileName(LocalDateTime.of(2026, 10, 11, 15, 30, 12)))
    }

    @Test
    fun savedState_followsRevision() {
        val fresh = SessionSnapshot(sessionId = "s", createdAt = 0, photoWidth = 1, photoHeight = 1)
        assertFalse(fresh.isSavedToGallery)
        val saved = fresh.copy(revision = 3, exportedRevision = 3)
        assertTrue(saved.isSavedToGallery)
        // Any further layer change re-enables Save.
        assertFalse(saved.copy(revision = 4).isSavedToGallery)
    }
}
