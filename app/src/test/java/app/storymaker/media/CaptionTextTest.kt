package app.storymaker.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionTextTest {

    @Test
    fun normalize_trimsAndCollapsesBlankLines() {
        assertEquals("Hello", normalizeCaption("  Hello \n "))
        assertEquals("a\n\nb", normalizeCaption("a\n\n\n\nb"))
        assertEquals("a\n\nb", normalizeCaption("a\n  \n \n\nb"))
        // A single blank line is kept.
        assertEquals("a\n\nb", normalizeCaption("a\n\nb"))
        assertEquals("a\nb", normalizeCaption("a\nb"))
    }

    @Test
    fun normalize_whitespaceOnly_isEmpty() {
        assertEquals("", normalizeCaption("   \n\t  "))
    }

    @Test
    fun length_countsEmojiOnce() {
        assertEquals(3, captionLength("🚀🎉✅"))
        assertEquals(5, captionLength("hello"))
    }

    @Test
    fun limit_cutsToMaximumWithoutSplittingEmoji() {
        assertEquals(CAPTION_MAX_LENGTH, captionLength(limitCaption("a".repeat(200))))
        val text = "a".repeat(CAPTION_MAX_LENGTH - 1) + "🚀🚀"
        val limited = limitCaption(text)
        assertEquals(CAPTION_MAX_LENGTH, captionLength(limited))
        assertTrue(limited.endsWith("🚀"))
        assertFalse(Character.isHighSurrogate(limited.last()))
    }

    @Test
    fun limit_keepsShortTextUnchanged() {
        assertEquals("Launch day", limitCaption("Launch day"))
    }

    @Test
    fun mentionsAndHashtags_areDetected() {
        assertTrue(hasMentionsOrHashtags("Thanks @team"))
        assertTrue(hasMentionsOrHashtags("#shipit"))
        assertTrue(hasMentionsOrHashtags("Done (@alex)"))
        assertFalse(hasMentionsOrHashtags("mail me at a@b.com"))
        assertFalse(hasMentionsOrHashtags("Issue # 4"))
        assertFalse(hasMentionsOrHashtags("Plain caption"))
    }
}
