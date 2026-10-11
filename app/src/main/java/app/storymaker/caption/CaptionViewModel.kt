package app.storymaker.caption

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.storymaker.media.ExportException
import app.storymaker.media.ExportTarget
import app.storymaker.media.ImageExporter
import app.storymaker.media.LoadResult
import app.storymaker.media.SessionStore
import app.storymaker.media.captionLength
import app.storymaker.media.hasMentionsOrHashtags
import app.storymaker.media.limitCaption
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CaptionUiState(
    /** True once the session is loaded; the caption field is filled from [caption] at that point. */
    val loaded: Boolean = false,
    /** Photo + drawings + stickers, without the caption. */
    val thumbnail: ImageBitmap? = null,
    val caption: String = "",
    val showOnPhoto: Boolean = true,
    val copyToClipboard: Boolean = false,
) {
    val length: Int get() = captionLength(caption)
    val showsMentionsHint: Boolean get() = hasMentionsOrHashtags(caption)
}

sealed interface CaptionEvent {
    data object PhotoUnavailable : CaptionEvent
    data object EditsReset : CaptionEvent
}

@HiltViewModel
class CaptionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionStore: SessionStore,
    private val exporter: ImageExporter,
) : ViewModel() {

    // Navigation stores route properties under their names (CaptionRoute.sessionId).
    private val sessionId: String = checkNotNull(savedStateHandle[SESSION_ID_ARG])

    private val loaded = MutableStateFlow(false)
    private val thumbnail = MutableStateFlow<ImageBitmap?>(null)

    val uiState: StateFlow<CaptionUiState> = combine(loaded, thumbnail, sessionStore.state) { loaded, thumbnail, snapshot ->
        val session = snapshot?.takeIf { it.sessionId == sessionId }
        CaptionUiState(
            loaded = loaded && session != null,
            thumbnail = thumbnail,
            caption = session?.caption.orEmpty(),
            showOnPhoto = session?.showOnPhoto ?: true,
            copyToClipboard = session?.copyToClipboard ?: false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaptionUiState())

    private val _events = Channel<CaptionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val snapshot = when (val result = sessionStore.load(sessionId)) {
                LoadResult.PhotoMissing -> {
                    _events.send(CaptionEvent.PhotoUnavailable)
                    return@launch
                }
                is LoadResult.RestoredWithoutEdits -> {
                    _events.send(CaptionEvent.EditsReset)
                    result.snapshot
                }
                is LoadResult.Restored -> result.snapshot
            }
            loaded.value = true
            try {
                thumbnail.value = exporter.render(snapshot, sessionStore.captureFile(sessionId), ExportTarget.Thumbnail)
                    .asImageBitmap()
            } catch (e: ExportException) {
                _events.send(CaptionEvent.PhotoUnavailable)
            }
        }
    }

    /** Stores the caption, cut to the maximum length. Not a layer change, so the gallery-saved state is kept. */
    fun setCaption(text: String) {
        val limited = limitCaption(text)
        sessionStore.update { it.copy(caption = limited) }
    }

    fun setShowOnPhoto(show: Boolean) = sessionStore.update { it.copy(showOnPhoto = show) }

    fun setCopyToClipboard(copy: Boolean) = sessionStore.update { it.copy(copyToClipboard = copy) }

    private companion object {
        const val SESSION_ID_ARG = "sessionId"
    }
}
