package app.storymaker.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.storymaker.R
import app.storymaker.ui.components.PrimaryButton
import app.storymaker.ui.components.StoryScreen
import app.storymaker.ui.components.StoryTopBar
import app.storymaker.ui.theme.StoryMakerTheme

/**
 * Photo editor with Draw and Stickers tabs. Until milestones 4 and 5 it shows the captured photo only.
 *
 * @param onPhotoUnavailable the session's photo is gone (e.g. deleted while the app was in the background)
 * @param onMessage shows a short app-level message
 */
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
    onPhotoUnavailable: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnPhotoUnavailable by rememberUpdatedState(onPhotoUnavailable)
    val currentOnMessage by rememberUpdatedState(onMessage)
    val editsResetMessage = stringResource(R.string.message_edits_reset)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                EditorEvent.PhotoUnavailable -> currentOnPhotoUnavailable()
                EditorEvent.EditsReset -> currentOnMessage(editsResetMessage)
            }
        }
    }

    EditorContent(photo = uiState.photo, onBack = onBack, onNext = onNext)
}

@Composable
private fun EditorContent(
    photo: ImageBitmap?,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    StoryScreen {
        StoryTopBar(onBack = onBack) {
            PrimaryButton(text = stringResource(R.string.action_next), onClick = onNext)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                Image(
                    bitmap = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.clip(MaterialTheme.shapes.large),
                )
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EditorContentPreview() {
    StoryMakerTheme { EditorContent(photo = null, onBack = {}, onNext = {}) }
}
