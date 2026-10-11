package app.storymaker.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.storymaker.R
import app.storymaker.media.EditorTab
import app.storymaker.ui.components.PrimaryButton
import app.storymaker.ui.components.RoundIconButton
import app.storymaker.ui.components.StoryScreen
import app.storymaker.ui.theme.Muted
import app.storymaker.ui.theme.StoryMakerTheme

/**
 * Photo editor (design: `Draw`, `Stickers`). Draw tab: freehand drawing with colors, brush size,
 * and undo/redo. Back discards the photo, asking first when there are edits.
 *
 * @param onDiscarded the session was deleted; return to the camera
 * @param onPhotoUnavailable the session's photo is gone (e.g. deleted while the app was in the background)
 * @param onMessage shows a short app-level message
 */
@Composable
fun EditorScreen(
    onDiscarded: () -> Unit,
    onNext: () -> Unit,
    onPhotoUnavailable: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDiscarded by rememberUpdatedState(onDiscarded)
    val currentOnPhotoUnavailable by rememberUpdatedState(onPhotoUnavailable)
    val currentOnMessage by rememberUpdatedState(onMessage)
    val editsResetMessage = stringResource(R.string.message_edits_reset)
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                EditorEvent.PhotoUnavailable -> currentOnPhotoUnavailable()
                EditorEvent.EditsReset -> currentOnMessage(editsResetMessage)
                EditorEvent.Discarded -> currentOnDiscarded()
            }
        }
    }

    val requestDiscard = {
        if (uiState.hasEdits) confirmDiscard = true else viewModel.discard()
    }
    BackHandler(onBack = requestDiscard)

    EditorContent(
        uiState = uiState,
        onBack = requestDiscard,
        onNext = onNext,
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
        onSelectTab = viewModel::selectTab,
        onSelectColor = viewModel::selectColor,
        onBrushSizeChange = viewModel::setBrushSize,
        onStrokeFinished = viewModel::commitStroke,
    )

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    viewModel.discard()
                }) { Text(stringResource(R.string.editor_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.editor_discard_keep))
                }
            },
        )
    }
}

@Composable
private fun EditorContent(
    uiState: EditorUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSelectTab: (EditorTab) -> Unit,
    onSelectColor: (Int) -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onStrokeFinished: (List<Float>, Float) -> Unit,
) {
    StoryScreen {
        Box(Modifier.fillMaxWidth()) {
            RoundIconButton(
                icon = R.drawable.ic_arrow_back,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            if (uiState.tab == EditorTab.Draw) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RoundIconButton(
                        icon = R.drawable.ic_undo,
                        contentDescription = stringResource(R.string.editor_undo),
                        onClick = onUndo,
                        enabled = uiState.canUndo,
                    )
                    RoundIconButton(
                        icon = R.drawable.ic_redo,
                        contentDescription = stringResource(R.string.editor_redo),
                        onClick = onRedo,
                        enabled = uiState.canRedo,
                    )
                }
            }
            PrimaryButton(
                text = stringResource(R.string.action_next),
                onClick = onNext,
                compact = true,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        DrawingCanvas(
            photo = uiState.photo,
            layers = uiState.layers,
            drawingEnabled = uiState.tab == EditorTab.Draw,
            brushColor = Color(uiState.brushColor),
            brushSizeDp = uiState.brushSizeDp,
            onStrokeFinished = onStrokeFinished,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        EditorTabBar(selected = uiState.tab, onSelect = onSelectTab)
        when (uiState.tab) {
            EditorTab.Draw -> {
                BrushSwatches(selectedColor = uiState.brushColor, onSelect = onSelectColor)
                BrushSizeSlider(sizeDp = uiState.brushSizeDp, onSizeChange = onBrushSizeChange)
            }
            EditorTab.Stickers -> {
                // Sticker sheet arrives in milestone 5; keep the controls area the same height.
                Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.editor_stickers_coming),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EditorContentPreview() {
    StoryMakerTheme {
        EditorContent(
            uiState = EditorUiState(canUndo = true),
            onBack = {},
            onNext = {},
            onUndo = {},
            onRedo = {},
            onSelectTab = {},
            onSelectColor = {},
            onBrushSizeChange = {},
            onStrokeFinished = { _, _ -> },
        )
    }
}
