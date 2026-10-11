package app.storymaker.share

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.storymaker.R
import app.storymaker.ui.components.AccentButton
import app.storymaker.ui.components.PlaceholderPanel
import app.storymaker.ui.components.RoundIconButton
import app.storymaker.ui.components.SecondaryButton
import app.storymaker.ui.components.StoryScreen
import app.storymaker.ui.components.StoryTopBar
import app.storymaker.ui.theme.StoryMakerTheme

/** Final preview with Share to Instagram Stories, Save, and New photo. Placeholder until milestones 6–8. */
@Composable
fun ShareScreen(
    onBack: () -> Unit,
    onNewPhoto: () -> Unit,
) {
    StoryScreen {
        StoryTopBar(onBack = onBack, title = stringResource(R.string.share_title)) {
            RoundIconButton(
                icon = R.drawable.ic_photo_camera,
                contentDescription = stringResource(R.string.action_new_photo),
                onClick = onNewPhoto,
            )
        }
        PlaceholderPanel(
            text = "Story preview (milestone 7)",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        AccentButton(
            text = stringResource(R.string.share_to_stories),
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            text = stringResource(R.string.action_save),
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ShareScreenPreview() {
    StoryMakerTheme { ShareScreen(onBack = {}, onNewPhoto = {}) }
}
