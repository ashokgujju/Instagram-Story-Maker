package app.storymaker.share

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import app.storymaker.share.StoryShareRequest.Companion.ACTION_ADD_TO_STORY
import app.storymaker.share.StoryShareRequest.Companion.BACKGROUND_MIME_TYPE
import app.storymaker.share.StoryShareRequest.Companion.INSTAGRAM_PACKAGE

/** Launching Instagram's Stories composer, and the fallback when Instagram is missing. */
object InstagramStories {

    /** Whether Instagram is installed and can take a Stories share. */
    fun isAvailable(context: Context): Boolean {
        val probe = Intent(ACTION_ADD_TO_STORY).setPackage(INSTAGRAM_PACKAGE).setType(BACKGROUND_MIME_TYPE)
        return context.packageManager.resolveActivity(probe, 0) != null
    }

    /**
     * Grants Instagram read access to the share files and opens the Stories composer.
     * Returns false if Instagram can't be launched (e.g. it was uninstalled mid-flow).
     */
    fun launch(activity: Activity, request: StoryShareRequest): Boolean {
        // The sticker URI is an extra, so the intent flag doesn't cover it: grant it explicitly (and the background too).
        request.sharedUris().forEach { uri ->
            activity.grantUriPermission(INSTAGRAM_PACKAGE, uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            activity.startActivity(request.toIntent())
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** Opens Instagram's Play Store listing, falling back to the web page. */
    fun openPlayStore(context: Context) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$INSTAGRAM_PACKAGE".toUri()))
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$INSTAGRAM_PACKAGE".toUri()),
            )
        }
    }
}
