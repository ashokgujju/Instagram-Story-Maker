package app.storymaker.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. Routes carry only identifiers, never bitmaps. */
@Serializable
sealed interface Route

@Serializable
data object PermissionRoute : Route

@Serializable
data object PermissionDeniedRoute : Route

@Serializable
data object CaptureRoute : Route

@Serializable
data class EditorRoute(val sessionId: String) : Route

@Serializable
data class CaptionRoute(val sessionId: String) : Route

@Serializable
data class ShareRoute(val sessionId: String) : Route

/** The camera is the only way in: start at Capture when access is granted, otherwise explain first. */
fun startRoute(hasCameraPermission: Boolean): Route =
    if (hasCameraPermission) CaptureRoute else PermissionRoute
