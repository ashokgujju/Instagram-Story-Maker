package app.storymaker.camera

import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.storymaker.R
import app.storymaker.ui.components.PrimaryButton
import app.storymaker.ui.components.QuietButton
import app.storymaker.ui.components.RoundIconButton
import app.storymaker.ui.components.StoryScreen
import app.storymaker.ui.theme.Muted
import app.storymaker.ui.theme.StoryMakerTheme
import app.storymaker.ui.theme.SurfaceHigh

/**
 * In-app explanation shown before the system camera permission prompt. Continue shows the
 * prompt; a denial the system can ask about again keeps the user here.
 */
@Composable
fun PermissionScreen(
    onGranted: () -> Unit,
    onPermanentlyDenied: () -> Unit,
    onNotNow: () -> Unit,
    onClose: () -> Unit,
) {
    val requestPermission = rememberCameraPermissionRequest { outcome ->
        when (outcome) {
            PermissionOutcome.Granted -> onGranted()
            PermissionOutcome.PermanentlyDenied -> onPermanentlyDenied()
            PermissionOutcome.AskAgain -> Unit
        }
    }
    CameraPermissionOnResume { granted -> if (granted) onGranted() }
    PermissionContent(
        onContinue = requestPermission,
        onNotNow = onNotNow,
        onClose = onClose,
    )
}

/** Shown when the camera permission is permanently denied. Returning from Settings with access granted continues. */
@Composable
fun PermissionDeniedScreen(
    onGranted: () -> Unit,
    onNotNow: () -> Unit,
    onClose: () -> Unit,
) {
    val activity = LocalActivity.current
    CameraPermissionOnResume { granted -> if (granted) onGranted() }
    PermissionDeniedContent(
        onOpenSettings = { activity?.openAppSettings() },
        onNotNow = onNotNow,
        onClose = onClose,
    )
}

@Composable
private fun PermissionContent(
    onContinue: () -> Unit,
    onNotNow: () -> Unit,
    onClose: () -> Unit,
) = PermissionLayout(
    icon = R.drawable.ic_photo_camera,
    title = stringResource(R.string.permission_title),
    body = stringResource(R.string.permission_body),
    primaryAction = stringResource(R.string.action_continue),
    onPrimary = onContinue,
    onNotNow = onNotNow,
    onClose = onClose,
    footer = stringResource(R.string.permission_footer),
)

@Composable
private fun PermissionDeniedContent(
    onOpenSettings: () -> Unit,
    onNotNow: () -> Unit,
    onClose: () -> Unit,
) = PermissionLayout(
    icon = R.drawable.ic_no_photography,
    title = stringResource(R.string.permission_denied_title),
    body = stringResource(R.string.permission_denied_body),
    primaryAction = stringResource(R.string.permission_denied_open_settings),
    onPrimary = onOpenSettings,
    onNotNow = onNotNow,
    onClose = onClose,
    footer = null,
)

@Composable
private fun PermissionLayout(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    primaryAction: String,
    onPrimary: () -> Unit,
    onNotNow: () -> Unit,
    onClose: () -> Unit,
    footer: String?,
) {
    StoryScreen(horizontalPadding = 24.dp) {
        RoundIconButton(
            icon = R.drawable.ic_close,
            contentDescription = stringResource(R.string.action_close),
            onClick = onClose,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .background(SurfaceHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(44.dp))
            }
            Text(text = title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )
        }
        PrimaryButton(text = primaryAction, onClick = onPrimary, modifier = Modifier.fillMaxWidth())
        QuietButton(
            text = stringResource(R.string.action_not_now),
            onClick = onNotNow,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PermissionScreenPreview() {
    StoryMakerTheme { PermissionContent(onContinue = {}, onNotNow = {}, onClose = {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PermissionDeniedScreenPreview() {
    StoryMakerTheme { PermissionDeniedContent(onOpenSettings = {}, onNotNow = {}, onClose = {}) }
}
