package app.storymaker.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.storymaker.R
import app.storymaker.caption.CaptionScreen
import app.storymaker.camera.CaptureScreen
import app.storymaker.camera.PermissionDeniedScreen
import app.storymaker.camera.PermissionScreen
import app.storymaker.editor.EditorScreen
import app.storymaker.media.SessionError
import app.storymaker.share.ShareScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Wires the feature screens together. Feature packages never navigate directly;
 * they report events through callbacks and this graph decides where to go.
 * Also hosts the app-level snackbar used for short messages.
 *
 * @param onExit closes the app (Close / Not now). There is no home screen to return to.
 */
@Composable
fun StoryMakerNavHost(
    startRoute: Route,
    sessionErrors: Flow<SessionError>,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }

    val saveFailedMessage = stringResource(R.string.message_save_failed)
    LaunchedEffect(sessionErrors) {
        sessionErrors.collect { error ->
            when (error) {
                SessionError.SaveFailed -> showMessage(saveFailedMessage)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = startRoute) {
            composable<PermissionRoute> {
                // Permission results arrive while the system prompt still covers the app (not resumed),
                // so these navigations are guarded by the current destination instead of the lifecycle.
                PermissionScreen(
                    onGranted = {
                        navController.navigateFrom<PermissionRoute>(CaptureRoute) {
                            popUpTo<PermissionRoute> { inclusive = true }
                        }
                    },
                    onPermanentlyDenied = {
                        navController.navigateFrom<PermissionRoute>(PermissionDeniedRoute) {
                            popUpTo<PermissionRoute> { inclusive = true }
                        }
                    },
                    onNotNow = onExit,
                    onClose = onExit,
                )
            }
            composable<PermissionDeniedRoute> {
                PermissionDeniedScreen(
                    onGranted = {
                        navController.navigateFrom<PermissionDeniedRoute>(CaptureRoute) {
                            popUpTo<PermissionDeniedRoute> { inclusive = true }
                        }
                    },
                    onNotNow = onExit,
                    onClose = onExit,
                )
            }
            composable<CaptureRoute> {
                CaptureScreen(
                    onPhotoCaptured = { sessionId -> navController.navigate(EditorRoute(sessionId)) },
                    onPermissionRevoked = {
                        navController.navigateFrom<CaptureRoute>(PermissionRoute) {
                            popUpTo<CaptureRoute> { inclusive = true }
                        }
                    },
                    onClose = onExit,
                )
            }
            composable<EditorRoute> { entry ->
                val route = entry.toRoute<EditorRoute>()
                val photoNotRestoredMessage = stringResource(R.string.message_photo_not_restored)
                EditorScreen(
                    onDiscarded = { navController.popBackStack<CaptureRoute>(inclusive = false) },
                    onNext = dropUnlessResumed { navController.navigate(CaptionRoute(route.sessionId)) },
                    onPhotoUnavailable = {
                        navController.navigate(CaptureRoute) {
                            popUpTo<CaptureRoute> { inclusive = true }
                        }
                        showMessage(photoNotRestoredMessage)
                    },
                    onMessage = showMessage,
                )
            }
            composable<CaptionRoute> { entry ->
                val route = entry.toRoute<CaptionRoute>()
                CaptionScreen(
                    onBack = { navController.popBackStack() },
                    onContinue = dropUnlessResumed { navController.navigate(ShareRoute(route.sessionId)) },
                )
            }
            composable<ShareRoute> {
                val photoNotRestoredMessage = stringResource(R.string.message_photo_not_restored)
                ShareScreen(
                    onBack = { navController.popBackStack() },
                    onDiscarded = { navController.popBackStack<CaptureRoute>(inclusive = false) },
                    onPhotoUnavailable = {
                        navController.navigate(CaptureRoute) {
                            popUpTo<CaptureRoute> { inclusive = true }
                        }
                        showMessage(photoNotRestoredMessage)
                    },
                    onMessage = showMessage,
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
    }
}

/** Navigates only if [From] is still the current destination, so a repeated callback can't navigate twice. */
private inline fun <reified From : Route> NavController.navigateFrom(
    route: Route,
    noinline builder: NavOptionsBuilder.() -> Unit,
) {
    if (currentDestination?.hasRoute<From>() == true) navigate(route, builder)
}
