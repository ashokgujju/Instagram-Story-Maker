package app.storymaker.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.storymaker.R
import app.storymaker.media.EditorTab
import app.storymaker.media.StickerLayer
import app.storymaker.stickers.StickerCatalog
import app.storymaker.ui.components.PrimaryButton
import app.storymaker.ui.components.RoundIconButton
import app.storymaker.ui.components.StoryScreen
import app.storymaker.ui.theme.StoryMakerTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Photo editor (design: `Draw`, `Stickers`).
 *
 * - Draw tab: freehand drawing with colors, brush size, and undo/redo. Back discards the photo,
 *   asking first when there are edits.
 * - Stickers tab: the sticker sheet; stickers on the canvas can be selected and edited.
 *   Back returns to the Draw tab.
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

    val onBack = {
        when {
            uiState.tab == EditorTab.Stickers -> viewModel.selectTab(EditorTab.Draw)
            uiState.hasEdits -> confirmDiscard = true
            else -> viewModel.discard()
        }
    }
    BackHandler(onBack = onBack)

    EditorContent(
        uiState = uiState,
        actions = EditorActions(
            onBack = onBack,
            onNext = onNext,
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
            onSelectTab = viewModel::selectTab,
            onSelectColor = viewModel::selectColor,
            onBrushSizeChange = viewModel::setBrushSize,
            onStrokeFinished = viewModel::commitStroke,
            onPlaceSticker = viewModel::placeSticker,
            onSelectLayer = viewModel::selectLayer,
            onStickerChanged = viewModel::commitStickerChange,
            onRemoveSticker = viewModel::removeSticker,
        ),
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

internal class EditorActions(
    val onBack: () -> Unit,
    val onNext: () -> Unit,
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
    val onSelectTab: (EditorTab) -> Unit,
    val onSelectColor: (Int) -> Unit,
    val onBrushSizeChange: (Float) -> Unit,
    val onStrokeFinished: (List<Float>, Float) -> Unit,
    val onPlaceSticker: (String) -> Unit,
    val onSelectLayer: (String?) -> Unit,
    val onStickerChanged: (StickerLayer, StickerLayer) -> Unit,
    val onRemoveSticker: (String) -> Unit,
)

@Composable
internal fun EditorContent(uiState: EditorUiState, actions: EditorActions) {
    var rootHeight by remember { mutableFloatStateOf(0f) }
    var controlsTop by remember { mutableFloatStateOf(0f) }
    val stickersTab = uiState.tab == EditorTab.Stickers

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { rootHeight = it.height.toFloat() },
    ) {
        StoryScreen {
            Box(Modifier.fillMaxWidth()) {
                RoundIconButton(
                    icon = R.drawable.ic_arrow_back,
                    contentDescription = stringResource(if (stickersTab) R.string.editor_back_to_drawing else R.string.action_back),
                    onClick = actions.onBack,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                if (!stickersTab) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RoundIconButton(
                            icon = R.drawable.ic_undo,
                            contentDescription = stringResource(R.string.editor_undo),
                            onClick = actions.onUndo,
                            enabled = uiState.canUndo,
                        )
                        RoundIconButton(
                            icon = R.drawable.ic_redo,
                            contentDescription = stringResource(R.string.editor_redo),
                            onClick = actions.onRedo,
                            enabled = uiState.canRedo,
                        )
                    }
                }
                PrimaryButton(
                    text = stringResource(R.string.action_next),
                    onClick = actions.onNext,
                    compact = true,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            EditorCanvas(
                photo = uiState.photo,
                layers = uiState.layers,
                tab = uiState.tab,
                brushColor = Color(uiState.brushColor),
                brushSizeDp = uiState.brushSizeDp,
                stickerPainters = rememberStickerPainters(uiState),
                selectedLayerId = uiState.selectedLayerId,
                onStrokeFinished = actions.onStrokeFinished,
                onSelectLayer = actions.onSelectLayer,
                onStickerChanged = actions.onStickerChanged,
                onRemoveSticker = actions.onRemoveSticker,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            // The Draw controls keep their space on the Stickers tab (hidden under the sheet),
            // so the photo stays the same size on both tabs.
            Column(
                modifier = Modifier
                    .onGloballyPositioned { controlsTop = it.positionInRoot().y }
                    .then(if (stickersTab) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EditorTabBar(selected = uiState.tab, onSelect = actions.onSelectTab)
                BrushSwatches(selectedColor = uiState.brushColor, onSelect = actions.onSelectColor)
                BrushSizeSlider(sizeDp = uiState.brushSizeDp, onSizeChange = actions.onBrushSizeChange)
            }
        }
        if (stickersTab && rootHeight > 0f) {
            StickerSheetHost(
                uiState = uiState,
                collapsedHeightPx = rootHeight - controlsTop,
                onPick = { actions.onPlaceSticker(it.id) },
            )
        }
    }
}

/**
 * The sticker sheet over the bottom of the editor. It snaps between expanded (the design's
 * ~400dp, covering part of the canvas) and collapsed (covering only the Draw controls).
 */
@Composable
private fun BoxScope.StickerSheetHost(
    uiState: EditorUiState,
    collapsedHeightPx: Float,
    onPick: (app.storymaker.stickers.Sticker) -> Unit,
) {
    val density = LocalDensity.current
    val expandedHeightPx = maxOf(with(density) { EXPANDED_SHEET_HEIGHT.toPx() }, collapsedHeightPx)
    val maxOffset = expandedHeightPx - collapsedHeightPx
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .imePadding()
            .fillMaxWidth()
            .height(with(density) { expandedHeightPx.toDp() })
            .offset { IntOffset(0, offset.value.coerceIn(0f, maxOffset).roundToInt()) },
    ) {
        StickerSheet(
            stickers = uiState.stickers,
            onPick = onPick,
            onDrag = { delta -> scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, maxOffset)) } },
            onDragStopped = { velocity ->
                val collapse = when {
                    velocity > FLING_VELOCITY -> true
                    velocity < -FLING_VELOCITY -> false
                    else -> offset.value > maxOffset / 2
                }
                scope.launch { offset.animateTo(if (collapse) maxOffset else 0f) }
            },
            onSearchFocused = { scope.launch { offset.animateTo(0f) } },
        )
    }
}

/** Artwork for the stickers placed in the current layers. */
@Composable
private fun rememberStickerPainters(uiState: EditorUiState): Map<String, Painter> {
    val drawables = uiState.stickers.associate { it.id to it.drawable }
    return uiState.layers
        .filterIsInstance<StickerLayer>()
        .map { it.stickerId }
        .distinct()
        .mapNotNull { id -> drawables[id]?.let { drawable -> key(id) { id to painterResource(drawable) } } }
        .toMap()
}

private val EXPANDED_SHEET_HEIGHT = 400.dp
private const val FLING_VELOCITY = 1_000f

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EditorContentPreview() {
    StoryMakerTheme {
        EditorContent(
            uiState = EditorUiState(canUndo = true, stickers = StickerCatalog.all, tab = EditorTab.Stickers),
            actions = EditorActions({}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, { _, _ -> }, {}),
        )
    }
}
