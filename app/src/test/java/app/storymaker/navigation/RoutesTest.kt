package app.storymaker.navigation

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {

    @Test
    fun startRoute_withCameraPermission_isCapture() {
        assertEquals(CaptureRoute, startRoute(hasCameraPermission = true))
    }

    @Test
    fun startRoute_withoutCameraPermission_isPermission() {
        assertEquals(PermissionRoute, startRoute(hasCameraPermission = false))
    }

    @Test
    fun routes_surviveSerializationRoundTrip() {
        val routes = listOf(
            PermissionRoute,
            PermissionDeniedRoute,
            CaptureRoute,
            EditorRoute("session-1"),
            CaptionRoute("session-1"),
            ShareRoute("session-1"),
        )
        for (route in routes) {
            val encoded = Json.encodeToString(Route.serializer(), route)
            assertEquals(route, Json.decodeFromString(Route.serializer(), encoded))
        }
    }
}
