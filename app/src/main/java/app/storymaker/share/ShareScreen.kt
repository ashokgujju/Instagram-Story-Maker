package app.storymaker.share

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.storymaker.R
import app.storymaker.ui.components.AccentButton
import app.storymaker.ui.components.RoundIconButton
import app.storymaker.ui.components.SecondaryButton
import app.storymaker.ui.components.StoryDialog
import app.storymaker.ui.components.StoryScreen
import app.storymaker.ui.components.StoryTopBar
import app.storymaker.ui.theme.Muted
import app.storymaker.ui.theme.StoryMakerTheme

/**
 * Final preview (design: `Share`) with Share to Instagram Stories, Save to gallery, and New photo. The preview is rendered by the exporter, so it matches the saved image.
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
    val notInstalledMessage = stringResource(R.string.message_instagram_not_installed)
    val shareFailedMessage = stringResource(R.string.message_share_failed)
    val missingAppIdMessage = stringResource(R.string.message_missing_facebook_app_id)
    val activity = LocalActivity.current
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ShareEvent.PhotoUnavailable -> currentOnPhotoUnavailable()
                ShareEvent.EditsReset -> currentOnMessage(editsResetMessage)
                ShareEvent.SavedToGallery -> currentOnMessage(savedMessage)
                ShareEvent.SaveFailed -> currentOnMessage(failedMessage)
                ShareEvent.Discarded -> currentOnDiscarded()
                is ShareEvent.Launch -> {
                    val launched = activity != null && InstagramStories.launch(activity, event.request)
                    if (launched) viewModel.onLaunched() else viewModel.onLaunchFailed()
                }
                ShareEvent.InstagramNotInstalled -> currentOnMessage(notInstalledMessage)
                ShareEvent.ShareFailed -> currentOnMessage(shareFailedMessage)
                ShareEvent.MissingFacebookAppId -> currentOnMessage(missingAppIdMessage)
            }
        }
    }
    // Instagram may be installed or removed while the app is in the background (e.g. via "Get Instagram").
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshAvailability()
        onPauseOrDispose {}
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
        captionSticker = uiState.captionSticker,
        saveState = uiState.saveState,
        shareButton = uiState.shareButton,
        onBack = onBack,
        onNewPhoto = onNewPhoto,
        onShare = {
            if (uiState.shareButton == ShareButtonState.InstagramMissing) InstagramStories.openPlayStore(context) else viewModel.share()
        },
        onSave = onSave,
    )

    uiState.coachMark?.let { lines ->
        StoryDialog(
            title = stringResource(R.string.coach_title),
            body = lines.map {
                stringResource(
                    when (it) {
                        CoachMarkLine.MoveSticker -> R.string.coach_move_sticker
                        CoachMarkLine.PasteCaption -> R.string.coach_paste_caption
                    },
                )
            },
            confirmText = stringResource(R.string.coach_got_it),
            onConfirm = viewModel::confirmCoachMark,
            onDismissRequest = viewModel::dismissCoachMark,
        )
    }

    if (confirmDiscard) {
        StoryDialog(
            title = stringResource(R.string.share_discard_title),
            body = listOf(stringResource(R.string.share_discard_body)),
            confirmText = stringResource(R.string.editor_discard_confirm),
            onConfirm = {
                confirmDiscard = false
                viewModel.discard()
            },
            dismissText = stringResource(R.string.share_discard_keep),
            onDismissRequest = { confirmDiscard = false },
        )
    }
}

@Composable
internal fun ShareContent(
    preview: ImageBitmap?,
    captionSticker: CaptionStickerPreview?,
    saveState: SaveState,
    shareButton: ShareButtonState,
    onBack: () -> Unit,
    onNewPhoto: () -> Unit,
    onShare: () -> Unit,
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
                    // Centered, where Instagram places the sticker, at its size on a 1080px-wide Story.
                    captionSticker?.let {
                        Image(
                            bitmap = it.image,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth(it.widthFraction),
                        )
                    }
                }
            }
        }
        if (captionSticker != null) {
            Text(
                text = stringResource(R.string.share_move_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        AccentButton(
            text = stringResource(
                when (shareButton) {
                    ShareButtonState.Ready -> R.string.share_to_stories
                    ShareButtonState.Preparing -> R.string.share_preparing
                    ShareButtonState.SharedBefore -> R.string.share_again
                    ShareButtonState.InstagramMissing -> R.string.share_get_instagram
                },
            ),
            icon = if (shareButton == ShareButtonState.InstagramMissing) null else R.drawable.ic_add_circle,
            onClick = onShare,
            enabled = preview != null && shareButton != ShareButtonState.Preparing,
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
        ShareContent(
            preview = null,
            captionSticker = null,
            saveState = SaveState.Idle,
            shareButton = ShareButtonState.Ready,
            onBack = {},
            onNewPhoto = {},
            onShare = {},
            onSave = {},
        )
    }
}
