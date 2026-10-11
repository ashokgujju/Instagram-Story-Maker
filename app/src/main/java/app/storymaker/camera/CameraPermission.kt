package app.storymaker.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

enum class PermissionOutcome { Granted, AskAgain, PermanentlyDenied }

/**
 * Interprets a permission request result. After a denial, the system only offers the prompt
 * again while [shouldShowRationale] is true; otherwise the denial is permanent.
 */
fun permissionOutcome(granted: Boolean, shouldShowRationale: Boolean): PermissionOutcome = when {
    granted -> PermissionOutcome.Granted
    shouldShowRationale -> PermissionOutcome.AskAgain
    else -> PermissionOutcome.PermanentlyDenied
}

fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Returns a function that shows the system camera permission prompt and reports the outcome. */
@Composable
internal fun rememberCameraPermissionRequest(onOutcome: (PermissionOutcome) -> Unit): () -> Unit {
    val activity = LocalActivity.current
    val currentOnOutcome = rememberUpdatedState(onOutcome)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val rationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } ?: false
        currentOnOutcome.value(permissionOutcome(granted, rationale))
    }
    return { launcher.launch(Manifest.permission.CAMERA) }
}

/** Calls [onChange] on every resume with the current camera permission state. */
@Composable
internal fun CameraPermissionOnResume(onChange: (granted: Boolean) -> Unit) {
    val context = LocalContext.current
    val currentOnChange = rememberUpdatedState(onChange)
    LifecycleResumeEffect(Unit) {
        currentOnChange.value(hasCameraPermission(context))
        onPauseOrDispose {}
    }
}

internal fun Activity.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    )
}
