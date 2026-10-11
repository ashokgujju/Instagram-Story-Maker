package app.storymaker.editor

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

/** Photo editor with Draw and Stickers tabs. Placeholder until milestones 4 and 5. */
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    StoryScreen {
        StoryTopBar(onBack = onBack) {
            PrimaryButton(text = stringResource(R.string.action_next), onClick = onNext)
        }
        PlaceholderPanel(
            text = "Photo canvas, Draw and Stickers (milestones 4 and 5)",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EditorScreenPreview() {
    StoryMakerTheme { EditorScreen(onBack = {}, onNext = {}) }
}
