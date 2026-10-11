package app.storymaker.share

import android.content.Intent
import androidx.core.net.toUri

/**
 * Everything Instagram's "add to story" intent needs (docs/ARCHITECTURE.md section 15).
 * URIs are strings so the request can be built and tested without Android classes;
 * [toIntent] is the single place that turns it into an [Intent].
 */
data class StoryShareRequest(
    val backgroundUri: String,
    val stickerUri: String?,
    val topColor: String,
    val bottomColor: String,
    val facebookAppId: String,
) {
    /** String extras: the App ID and the letterbox colors. */
    fun stringExtras(): Map<String, String> = mapOf(
        EXTRA_SOURCE_APPLICATION to facebookAppId,
        EXTRA_TOP_BACKGROUND_COLOR to topColor,
        EXTRA_BOTTOM_BACKGROUND_COLOR to bottomColor,
    )

    /** URIs Instagram must be allowed to read. */
    fun sharedUris(): List<String> = listOfNotNull(backgroundUri, stickerUri)

    fun toIntent(): Intent = Intent(ACTION_ADD_TO_STORY).apply {
        // Explicit package: opens the Stories composer directly, never the system share sheet (D2).
        setPackage(INSTAGRAM_PACKAGE)
        setDataAndType(backgroundUri.toUri(), BACKGROUND_MIME_TYPE)
        stringExtras().forEach { (key, value) -> putExtra(key, value) }
        stickerUri?.let { putExtra(EXTRA_INTERACTIVE_ASSET_URI, it.toUri()) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    companion object {
        const val ACTION_ADD_TO_STORY = "com.instagram.share.ADD_TO_STORY"
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val BACKGROUND_MIME_TYPE = "image/jpeg"
        const val EXTRA_SOURCE_APPLICATION = "source_application"
        const val EXTRA_INTERACTIVE_ASSET_URI = "interactive_asset_uri"
        const val EXTRA_TOP_BACKGROUND_COLOR = "top_background_color"
        const val EXTRA_BOTTOM_BACKGROUND_COLOR = "bottom_background_color"
    }
}
