package app.storymaker.editor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.storymaker.media.EditorTab
import app.storymaker.media.Layer
import app.storymaker.media.LoadResult
import app.storymaker.media.PhotoDecoder
import app.storymaker.media.SessionSnapshot
import app.storymaker.media.SessionStore
import app.storymaker.media.StrokeLayer
import app.storymaker.media.roundNormalized
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EditorUiState(
    val photo: ImageBitmap? = null,
    val layers: List<Layer> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val tab: EditorTab = EditorTab.Draw,
    val brushColor: Int = SessionSnapshot.DEFAULT_BRUSH_COLOR,
    val brushSizeDp: Float = SessionSnapshot.DEFAULT_BRUSH_SIZE_DP,
) {
    val hasEdits: Boolean get() = layers.isNotEmpty()
}

sealed interface EditorEvent {
    /** The session's photo is gone; the user has to capture a new one. */
    data object PhotoUnavailable : EditorEvent

    /** The photo was restored but its edits were not. */
    data object EditsReset : EditorEvent

    /** The photo and its edits were discarded. */
    data object Discarded : EditorEvent
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionStore: SessionStore,
    private val photoDecoder: PhotoDecoder,
) : ViewModel() {

    // Navigation stores route properties under their names (EditorRoute.sessionId).
    private val sessionId: String = checkNotNull(savedStateHandle[SESSION_ID_ARG])

    private val photo = MutableStateFlow<ImageBitmap?>(null)

    val uiState: StateFlow<EditorUiState> = combine(photo, sessionStore.state) { photo, snapshot ->
        val session = snapshot?.takeIf { it.sessionId == sessionId }
        if (session == null) {
            EditorUiState(photo = photo)
        } else {
            EditorUiState(
                photo = photo,
                layers = session.layers,
                canUndo = session.canUndo,
                canRedo = session.canRedo,
                tab = session.editorTab,
                brushColor = session.brushColor,
                brushSizeDp = session.brushSizeDp,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUiState())

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            when (sessionStore.load(sessionId)) {
                LoadResult.PhotoMissing -> {
                    _events.send(EditorEvent.PhotoUnavailable)
                    return@launch
                }
                is LoadResult.RestoredWithoutEdits -> _events.send(EditorEvent.EditsReset)
                is LoadResult.Restored -> Unit
            }
            val bitmap = photoDecoder.decode(sessionStore.captureFile(sessionId), DISPLAY_LONG_SIDE)
            if (bitmap == null) {
                _events.send(EditorEvent.PhotoUnavailable)
            } else {
                photo.value = bitmap.asImageBitmap()
            }
        }
    }

    /** Commits a finished stroke ([points] are flattened normalized coordinates). */
    fun commitStroke(points: List<Float>, widthFraction: Float) {
        if (points.size < 2) return
        sessionStore.update { session ->
            session.addLayer(
                StrokeLayer(
                    id = UUID.randomUUID().toString(),
                    color = session.brushColor,
                    width = widthFraction,
                    points = roundNormalized(points),
                ),
            )
        }
    }

    fun undo() = sessionStore.update { it.undo() }

    fun redo() = sessionStore.update { it.redo() }

    fun selectColor(color: Int) = sessionStore.update { it.copy(brushColor = color) }

    fun setBrushSize(sizeDp: Float) = sessionStore.update { it.copy(brushSizeDp = sizeDp) }

    fun selectTab(tab: EditorTab) = sessionStore.update { it.copy(editorTab = tab) }

    fun discard() {
        viewModelScope.launch {
            sessionStore.discardActive()
            _events.send(EditorEvent.Discarded)
        }
    }

    private companion object {
        const val SESSION_ID_ARG = "sessionId"
        const val DISPLAY_LONG_SIDE = 1920
    }
}
