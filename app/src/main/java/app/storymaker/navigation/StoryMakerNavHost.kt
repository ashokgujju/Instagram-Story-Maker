package app.storymaker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.storymaker.caption.CaptionScreen
import app.storymaker.camera.CaptureScreen
import app.storymaker.camera.PermissionDeniedScreen
import app.storymaker.camera.PermissionScreen
import app.storymaker.editor.EditorScreen
import app.storymaker.share.ShareScreen

/**
 * Wires the feature screens together. Feature packages never navigate directly;
 * they report events through callbacks and this graph decides where to go.
 *
 * @param onExit closes the app (Close / Not now). There is no home screen to return to.
 */
@Composable
fun StoryMakerNavHost(
    startRoute: Route,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startRoute, modifier = modifier) {
        composable<PermissionRoute> {
            PermissionScreen(
                onContinue = {
                    navController.navigate(CaptureRoute) {
                        popUpTo<PermissionRoute> { inclusive = true }
                    }
                },
                onNotNow = onExit,
                onClose = onExit,
            )
        }
        composable<PermissionDeniedRoute> {
            PermissionDeniedScreen(onOpenSettings = {}, onNotNow = onExit, onClose = onExit)
        }
        composable<CaptureRoute> {
            CaptureScreen(
                onPhotoCaptured = { sessionId -> navController.navigate(EditorRoute(sessionId)) },
                onClose = onExit,
            )
        }
        composable<EditorRoute> { entry ->
            val route = entry.toRoute<EditorRoute>()
            EditorScreen(
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(CaptionRoute(route.sessionId)) },
            )
        }
        composable<CaptionRoute> { entry ->
            val route = entry.toRoute<CaptionRoute>()
            CaptionScreen(
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigate(ShareRoute(route.sessionId)) },
            )
        }
        composable<ShareRoute> {
            ShareScreen(
                onBack = { navController.popBackStack() },
                onNewPhoto = { navController.popBackStack<CaptureRoute>(inclusive = false) },
            )
        }
    }
}
