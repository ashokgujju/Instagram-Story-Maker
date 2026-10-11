package app.storymaker.media

/** Maximum caption length, in characters (Unicode code points). */
const val CAPTION_MAX_LENGTH = 150

/** Number of characters in [text], counting each emoji or other supplementary character once. */
fun captionLength(text: String): Int = text.codePointCount(0, text.length)

/** Cuts [text] to [CAPTION_MAX_LENGTH] characters without splitting a surrogate pair. */
fun limitCaption(text: String): String =
    if (captionLength(text) <= CAPTION_MAX_LENGTH) text else text.substring(0, text.offsetByCodePoints(0, CAPTION_MAX_LENGTH))

/**
 * Caption as rendered and copied: trimmed, with runs of blank lines collapsed to one.
 * An empty result means there is no caption (no sticker, no clipboard copy).
 */
fun normalizeCaption(text: String): String =
    text.trim().replace(BLANK_LINES, "\n\n")

/** Whether [text] contains an @mention or #hashtag (which become tappable when pasted as Instagram text). */
fun hasMentionsOrHashtags(text: String): Boolean = MENTION_OR_HASHTAG.containsMatchIn(text)

// Two or more blank lines (possibly containing spaces), i.e. three or more line breaks.
private val BLANK_LINES = Regex("\\n[ \\t]*\\n(?:[ \\t]*\\n)+")

// @ or # at the start or after a non-word character, followed by a word: "@team", "#launch", but not "a@b.com".
private val MENTION_OR_HASHTAG = Regex("(?:^|[^\\w])[@#]\\w")
