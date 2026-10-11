package app.storymaker.camera

import android.view.OrientationEventListener
import android.view.Surface
import androidx.annotation.StringRes
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.storymaker.R
import app.storymaker.ui.components.PrimaryButton
import app.storymaker.ui.components.RoundIconButton
import app.storymaker.ui.theme.StoryMakerTheme
import app.storymaker.ui.theme.SurfaceHigher

/** Camera capture (design: `Main`). Reports the new session once the photo is captured and normalized. */
@Composable
fun CaptureScreen(
    onPhotoCaptured: (sessionId: String) -> Unit,
    onPermissionRevoked: () -> Unit,
    onClose: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPhotoCaptured by rememberUpdatedState(onPhotoCaptured)

    CameraPermissionOnResume { granted -> if (!granted) onPermissionRevoked() }
    DeviceRotationEffect(onRotation = viewModel::setTargetRotation)
    LaunchedEffect(lifecycleOwner) { viewModel.bindToCamera(lifecycleOwner) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CaptureEvent.Captured -> currentOnPhotoCaptured(event.sessionId)
            }
        }
    }

    CaptureContent(
        uiState = uiState,
        onClose = onClose,
        onFlash = viewModel::cycleFlash,
        onShutter = viewModel::capture,
        onSwitchCamera = viewModel::switchCamera,
        onRetry = viewModel::retry,
        viewfinder = {
            surfaceRequest?.let { request ->
                CameraXViewfinder(surfaceRequest = request, modifier = Modifier.fillMaxSize())
            }
        },
    )
}

@Composable
internal fun CaptureContent(
    uiState: CaptureUiState,
    onClose: () -> Unit,
    onFlash: () -> Unit,
    onShutter: () -> Unit,
    onSwitchCamera: () -> Unit,
    onRetry: () -> Unit,
    viewfinder: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopCenter,
        ) {
            // 9:16 preview matching the 16:9 portrait capture, with rounded bottom corners.
            Box(
                modifier = Modifier
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(Color.Black),
            ) {
                viewfinder()
                ThirdsGrid(Modifier.fillMaxSize())
                when (val status = uiState.status) {
                    CaptureUiState.Status.Processing -> ProcessingOverlay()
                    is CaptureUiState.Status.Error -> ErrorOverlay(status.error, onRetry)
                    else -> Unit
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RoundIconButton(
                    icon = R.drawable.ic_close,
                    contentDescription = stringResource(R.string.action_close),
                    onClick = onClose,
                    containerColor = OverlayButtonColor,
                )
                if (uiState.hasFlashUnit) {
                    RoundIconButton(
                        icon = uiState.flashMode.icon,
                        contentDescription = stringResource(uiState.flashMode.label),
                        onClick = onFlash,
                        containerColor = OverlayButtonColor,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 40.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(48.dp))
            ShutterButton(enabled = uiState.status == CaptureUiState.Status.Ready, onClick = onShutter)
            RoundIconButton(
                icon = R.drawable.ic_cameraswitch,
                contentDescription = stringResource(R.string.capture_switch_camera),
                onClick = onSwitchCamera,
                enabled = !uiState.isBusy,
                containerColor = SurfaceHigher,
            )
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.5f)
    val label = stringResource(R.string.capture_shutter)
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .border(4.dp, color, CircleShape)
            .padding(9.dp)
            .background(color, CircleShape),
    )
}

/** Reports the device's physical rotation, so landscape photos come out upright although the UI stays portrait. */
@Composable
private fun DeviceRotationEffect(onRotation: (Int) -> Unit) {
    val context = LocalContext.current
    val currentOnRotation by rememberUpdatedState(onRotation)
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                currentOnRotation(surfaceRotationFor(orientation))
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }
}

/**
 * Camera target rotation for a physical device angle (0–359°, clockwise, from
 * OrientationEventListener): turned 90° clockwise means the display's natural top points right.
 */
internal fun surfaceRotationFor(orientationDegrees: Int): Int = when (orientationDegrees) {
    in 45 until 135 -> Surface.ROTATION_270
    in 135 until 225 -> Surface.ROTATION_180
    in 225 until 315 -> Surface.ROTATION_90
    else -> Surface.ROTATION_0
}

/** Rule-of-thirds guide lines. */
@Composable
private fun ThirdsGrid(modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 1.dp.toPx()
        for (i in 1..2) {
            val x = size.width * i / 3
            val y = size.height * i / 3
            drawLine(GridLineColor, Offset(x, 0f), Offset(x, size.height), stroke)
            drawLine(GridLineColor, Offset(0f, y), Offset(size.width, y), stroke)
        }
    }
}

@Composable
private fun ProcessingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun ErrorOverlay(error: CaptureError, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(error.message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(text = stringResource(R.string.action_try_again), onClick = onRetry)
    }
}

private val OverlayButtonColor = Color.Black.copy(alpha = 0.4f)
private val GridLineColor = Color.White.copy(alpha = 0.35f)

private val FlashMode.icon: Int
    get() = when (this) {
        FlashMode.Off -> R.drawable.ic_flash_off
        FlashMode.Auto -> R.drawable.ic_flash_auto
        FlashMode.On -> R.drawable.ic_flash_on
    }

@get:StringRes
private val FlashMode.label: Int
    get() = when (this) {
        FlashMode.Off -> R.string.capture_flash_off
        FlashMode.Auto -> R.string.capture_flash_auto
        FlashMode.On -> R.string.capture_flash_on
    }

@get:StringRes
private val CaptureError.message: Int
    get() = when (this) {
        CaptureError.CameraUnavailable -> R.string.capture_error_camera_unavailable
        CaptureError.CaptureFailed -> R.string.capture_error_capture_failed
        CaptureError.StorageFailed -> R.string.capture_error_storage
    }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CaptureContentPreview() {
    StoryMakerTheme {
        CaptureContent(
            uiState = CaptureUiState(hasFlashUnit = true),
            onClose = {},
            onFlash = {},
            onShutter = {},
            onSwitchCamera = {},
            onRetry = {},
            viewfinder = { Box(Modifier.fillMaxSize().background(Color(0xFFF4C7A1))) },
        )
    }
}
