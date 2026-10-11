package app.storymaker.editor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.storymaker.media.LoadResult
import app.storymaker.media.PhotoDecoder
import app.storymaker.media.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class EditorUiState(val photo: ImageBitmap? = null)

sealed interface EditorEvent {
    /** The session's photo is gone; the user has to capture a new one. */
    data object PhotoUnavailable : EditorEvent

    /** The photo was restored but its edits were not. */
    data object EditsReset : EditorEvent
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionStore: SessionStore,
    private val photoDecoder: PhotoDecoder,
) : ViewModel() {

    // Navigation stores route properties under their names (EditorRoute.sessionId).
    private val sessionId: String = checkNotNull(savedStateHandle[SESSION_ID_ARG])

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

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
                _uiState.value = EditorUiState(photo = bitmap.asImageBitmap())
            }
        }
    }

    private companion object {
        const val SESSION_ID_ARG = "sessionId"
        const val DISPLAY_LONG_SIDE = 1920
    }
}
