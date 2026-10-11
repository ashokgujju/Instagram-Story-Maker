package app.storymaker.caption

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.storymaker.R
import app.storymaker.media.CAPTION_MAX_LENGTH
import app.storymaker.media.captionLength
import app.storymaker.media.limitCaption
import app.storymaker.ui.components.PrimaryButton
import app.storymaker.ui.components.SettingsCard
import app.storymaker.ui.components.SettingsDivider
import app.storymaker.ui.components.SettingsSwitchRow
import app.storymaker.ui.components.StoryScreen
import app.storymaker.ui.components.StoryTopBar
import app.storymaker.ui.theme.Muted
import app.storymaker.ui.theme.StoryMakerTheme
import app.storymaker.ui.theme.Surface

/**
 * Caption entry (design: `Caption`) with the "Show on photo" (caption sticker) and
 * "Copy to clipboard" switches. The accurate caption preview is on the Share screen.
 */
@Composable
fun CaptionScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onPhotoUnavailable: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: CaptionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnPhotoUnavailable by rememberUpdatedState(onPhotoUnavailable)
    val currentOnMessage by rememberUpdatedState(onMessage)
    val editsResetMessage = stringResource(R.string.message_edits_reset)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CaptionEvent.PhotoUnavailable -> currentOnPhotoUnavailable()
                CaptionEvent.EditsReset -> currentOnMessage(editsResetMessage)
            }
        }
    }

    // The field owns the text while typing; it is filled once from the session, then every change is stored.
    val fieldState = rememberTextFieldState()
    var filled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.loaded) {
        if (uiState.loaded && !filled) {
            fieldState.setTextAndPlaceCursorAtEnd(uiState.caption)
            filled = true
        }
    }
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }.collect { if (filled) viewModel.setCaption(it) }
    }

    CaptionContent(
        uiState = uiState,
        fieldState = fieldState,
        onBack = onBack,
        onContinue = onContinue,
        onShowOnPhotoChange = viewModel::setShowOnPhoto,
        onCopyToClipboardChange = viewModel::setCopyToClipboard,
    )
}

@Composable
internal fun CaptionContent(
    uiState: CaptionUiState,
    fieldState: TextFieldState,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onShowOnPhotoChange: (Boolean) -> Unit,
    onCopyToClipboardChange: (Boolean) -> Unit,
) {
    StoryScreen {
        StoryTopBar(
            onBack = onBack,
            title = stringResource(R.string.caption_title),
            backContentDescription = stringResource(R.string.caption_back_to_editing),
        )
        // Scrolls so the field and switches stay reachable with the keyboard open; Continue stays pinned.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Thumbnail(uiState.thumbnail)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.caption_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CaptionField(fieldState)
                    CharacterCounter(uiState.length, modifier = Modifier.align(Alignment.End))
                }
            }
            SettingsCard {
                SettingsSwitchRow(
                    title = stringResource(R.string.caption_show_on_photo),
                    subtitle = stringResource(R.string.caption_show_on_photo_subtitle),
                    checked = uiState.showOnPhoto,
                    onCheckedChange = onShowOnPhotoChange,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.caption_copy),
                    subtitle = stringResource(
                        if (uiState.showsMentionsHint) R.string.caption_copy_subtitle_mentions else R.string.caption_copy_subtitle,
                    ),
                    checked = uiState.copyToClipboard,
                    onCheckedChange = onCopyToClipboardChange,
                )
            }
        }
        PrimaryButton(
            text = stringResource(R.string.action_continue),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Thumbnail(thumbnail: ImageBitmap?) {
    Box(
        modifier = Modifier
            .size(width = 104.dp, height = 185.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface),
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CaptionField(state: TextFieldState) {
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, lineHeight = 23.sp)
    BasicTextField(
        state = state,
        inputTransformation = CaptionLengthLimit,
        textStyle = textStyle,
        cursorBrush = SolidColor(Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        decorator = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Surface, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                if (state.text.isEmpty()) {
                    Text(text = stringResource(R.string.caption_placeholder), style = textStyle, color = Muted)
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun CharacterCounter(length: Int, modifier: Modifier = Modifier) {
    val description = pluralStringResource(R.plurals.caption_counter_description, length, length, CAPTION_MAX_LENGTH)
    Text(
        text = stringResource(R.string.caption_counter, length, CAPTION_MAX_LENGTH),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal),
        color = Muted,
        modifier = modifier.semantics {
            contentDescription = description
            // Announce the count as the limit approaches.
            if (length >= CAPTION_MAX_LENGTH - 10) liveRegion = LiveRegionMode.Polite
        },
    )
}

/** Cuts typed or pasted text to the maximum caption length. */
private val CaptionLengthLimit = InputTransformation {
    val text = toString()
    if (captionLength(text) > CAPTION_MAX_LENGTH) replace(0, length, limitCaption(text))
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CaptionContentPreview() {
    StoryMakerTheme {
        CaptionContent(
            uiState = CaptionUiState(loaded = true),
            fieldState = rememberTextFieldState(),
            onBack = {},
            onContinue = {},
            onShowOnPhotoChange = {},
            onCopyToClipboardChange = {},
        )
    }
}
