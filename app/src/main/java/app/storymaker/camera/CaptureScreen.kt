package app.storymaker.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.storymaker.R
import app.storymaker.ui.components.PlaceholderPanel
import app.storymaker.ui.components.RoundIconButton
import app.storymaker.ui.components.StoryScreen
import app.storymaker.ui.theme.StoryMakerTheme
import java.util.UUID

/**
 * Camera capture. Placeholder until milestone 2: the shutter skips the camera and
 * reports a generated session ID.
 */
@Composable
fun CaptureScreen(
    onPhotoCaptured: (sessionId: String) -> Unit,
    onClose: () -> Unit,
) {
    StoryScreen {
        RoundIconButton(
            icon = R.drawable.ic_close,
            contentDescription = stringResource(R.string.action_close),
            onClick = onClose,
        )
        PlaceholderPanel(
            text = "Camera preview (milestone 2)",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        val shutterLabel = stringResource(R.string.capture_shutter)
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp)
                .size(80.dp)
                .border(4.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.onBackground, CircleShape)
                .semantics {
                    contentDescription = shutterLabel
                    role = Role.Button
                }
                .clickable { onPhotoCaptured(UUID.randomUUID().toString()) },
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CaptureScreenPreview() {
    StoryMakerTheme { CaptureScreen(onPhotoCaptured = {}, onClose = {}) }
}
