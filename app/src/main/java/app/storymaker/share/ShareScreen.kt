package app.storymaker.share

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.storymaker.R
import app.storymaker.ui.components.AccentButton
import app.storymaker.ui.components.RoundIconButton
import app.storymaker.ui.components.SecondaryButton
import app.storymaker.ui.components.StoryScreen
import app.storymaker.ui.components.StoryTopBar
import app.storymaker.ui.theme.StoryMakerTheme

/**
 * Final preview (design: `Share`) with Share to Instagram Stories (milestone 8), Save to gallery,
 * and New photo. The preview is rendered by the exporter, so it matches the saved image.
 *
 * @param onDiscarded the session was deleted (New photo); return to the camera
 * @param onPhotoUnavailable the session's photo is gone
 * @param onMessage shows a short app-level message
 */
@Composable
fun ShareScreen(
    onBack: () -> Unit,
    onDiscarded: () -> Unit,
    onPhotoUnavailable: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: ShareViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentOnDiscarded by rememberUpdatedState(onDiscarded)
    val currentOnPhotoUnavailable by rememberUpdatedState(onPhotoUnavailable)
    val currentOnMessage by rememberUpdatedState(onMessage)
    val savedMessage = stringResource(R.string.message_saved_to_gallery)
    val failedMessage = stringResource(R.string.message_save_photo_failed)
    val editsResetMessage = stringResource(R.string.message_edits_reset)
    val permissionMessage = stringResource(R.string.message_storage_permission)
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ShareEvent.PhotoUnavailable -> currentOnPhotoUnavailable()
                ShareEvent.EditsReset -> currentOnMessage(editsResetMessage)
                ShareEvent.SavedToGallery -> currentOnMessage(savedMessage)
                ShareEvent.SaveFailed -> currentOnMessage(failedMessage)
                ShareEvent.Discarded -> currentOnDiscarded()
            }
        }
    }

    // API 26–28 need the storage permission to write to the public Pictures folder.
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.save() else currentOnMessage(permissionMessage)
    }
    val onSave = {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else viewModel.save()
    }
    val onNewPhoto = {
        if (uiState.needsDiscardConfirmation) confirmDiscard = true else viewModel.discard()
    }

    ShareContent(
        preview = uiState.preview,
        saveState = uiState.saveState,
        onBack = onBack,
        onNewPhoto = onNewPhoto,
        onSave = onSave,
    )

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.share_discard_title)) },
            text = { Text(stringResource(R.string.share_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    viewModel.discard()
                }) { Text(stringResource(R.string.editor_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.share_discard_keep))
                }
            },
        )
    }
}

@Composable
private fun ShareContent(
    preview: ImageBitmap?,
    saveState: SaveState,
    onBack: () -> Unit,
    onNewPhoto: () -> Unit,
    onSave: () -> Unit,
) {
    StoryScreen {
        StoryTopBar(onBack = onBack, title = stringResource(R.string.share_title)) {
            RoundIconButton(
                icon = R.drawable.ic_photo_camera,
                contentDescription = stringResource(R.string.action_new_photo),
                onClick = onNewPhoto,
            )
        }
        // 9:16 preview, as large as the space allows.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(9f / 16f)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (preview == null) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                } else {
                    Image(
                        bitmap = preview,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        AccentButton(
            text = stringResource(R.string.share_to_stories),
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            text = stringResource(
                when (saveState) {
                    SaveState.Idle -> R.string.action_save
                    SaveState.Saving -> R.string.share_saving
                    SaveState.Saved -> R.string.share_saved
                },
            ),
            icon = when (saveState) {
                SaveState.Idle -> R.drawable.ic_download
                SaveState.Saving -> null
                SaveState.Saved -> R.drawable.ic_check
            },
            onClick = onSave,
            enabled = saveState == SaveState.Idle && preview != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ShareContentPreview() {
    StoryMakerTheme {
        ShareContent(preview = null, saveState = SaveState.Idle, onBack = {}, onNewPhoto = {}, onSave = {})
    }
}
