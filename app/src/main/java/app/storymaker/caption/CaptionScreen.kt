package app.storymaker.caption

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.storymaker.R
import app.storymaker.ui.components.PlaceholderPanel
import app.storymaker.ui.components.PrimaryButton
import app.storymaker.ui.components.StoryScreen
import app.storymaker.ui.components.StoryTopBar
import app.storymaker.ui.theme.StoryMakerTheme

/** Caption entry with the "Show on photo" and "Copy to clipboard" switches. Placeholder until milestone 7. */
@Composable
fun CaptionScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    StoryScreen {
        StoryTopBar(onBack = onBack, title = stringResource(R.string.caption_title))
        PlaceholderPanel(
            text = "Caption field and switches (milestone 7)",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        PrimaryButton(
            text = stringResource(R.string.action_continue),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CaptionScreenPreview() {
    StoryMakerTheme { CaptionScreen(onBack = {}, onContinue = {}) }
}
