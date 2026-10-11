package app.storymaker.share

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.storymaker.media.CaptionRenderer
import app.storymaker.media.CaptionStickerSpec
import app.storymaker.media.ExportException
import app.storymaker.media.ExportTarget
import app.storymaker.media.GallerySaver
import app.storymaker.media.ImageExporter
import app.storymaker.media.LoadResult
import app.storymaker.media.SaveResult
import app.storymaker.media.SessionStore
import app.storymaker.media.isSavedToGallery
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

enum class SaveState { Idle, Saving, Saved }

data class ShareUiState(
    /** The share render (photo + drawings + stickers), or null while rendering. */
    val preview: ImageBitmap? = null,
    /** The caption sticker exactly as Instagram receives it, or null when it isn't shown. */
    val captionSticker: CaptionStickerPreview? = null,
    val saveState: SaveState = SaveState.Idle,
) {
    /** Leaving would lose a photo that was never saved, so ask first. */
    val needsDiscardConfirmation: Boolean get() = saveState != SaveState.Saved
}

/** The rendered caption sticker and its width as a fraction of the Story width. */
data class CaptionStickerPreview(val image: ImageBitmap, val widthFraction: Float)

sealed interface ShareEvent {
    data object PhotoUnavailable : ShareEvent
    data object EditsReset : ShareEvent
    data object SavedToGallery : ShareEvent
    data object SaveFailed : ShareEvent
    data object Discarded : ShareEvent
}

@HiltViewModel
class ShareViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionStore: SessionStore,
    private val exporter: ImageExporter,
    private val gallerySaver: GallerySaver,
    private val captionRenderer: CaptionRenderer,
) : ViewModel() {

    // Navigation stores route properties under their names (ShareRoute.sessionId).
    private val sessionId: String = checkNotNull(savedStateHandle[SESSION_ID_ARG])

    private val preview = MutableStateFlow<ImageBitmap?>(null)
    private val captionSticker = MutableStateFlow<CaptionStickerPreview?>(null)
    private val saving = MutableStateFlow(false)

    val uiState: StateFlow<ShareUiState> = combine(
        preview,
        captionSticker,
        saving,
        sessionStore.state,
    ) { preview, captionSticker, saving, snapshot ->
        val session = snapshot?.takeIf { it.sessionId == sessionId }
        ShareUiState(
            preview = preview,
            captionSticker = captionSticker,
            saveState = when {
                saving -> SaveState.Saving
                session?.isSavedToGallery == true -> SaveState.Saved
                else -> SaveState.Idle
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShareUiState())

    private val _events = Channel<ShareEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val snapshot = when (val result = sessionStore.load(sessionId)) {
                LoadResult.PhotoMissing -> {
                    _events.send(ShareEvent.PhotoUnavailable)
                    return@launch
                }
                is LoadResult.RestoredWithoutEdits -> {
                    _events.send(ShareEvent.EditsReset)
                    result.snapshot
                }
                is LoadResult.Restored -> result.snapshot
            }
            if (snapshot.showOnPhoto) {
                captionRenderer.render(snapshot.caption)?.let { sticker ->
                    captionSticker.value = CaptionStickerPreview(
                        image = sticker.asImageBitmap(),
                        widthFraction = sticker.width.toFloat() / CaptionStickerSpec.STORY_WIDTH,
                    )
                }
            }
            try {
                preview.value = exporter.render(snapshot, sessionStore.captureFile(sessionId), ExportTarget.Share)
                    .asImageBitmap()
            } catch (e: ExportException) {
                _events.send(ShareEvent.PhotoUnavailable)
            }
        }
    }

    /** Renders the gallery version and saves it. The caller handles the API 26–28 storage permission. */
    fun save() {
        if (saving.value || uiState.value.saveState == SaveState.Saved) return
        val snapshot = sessionStore.state.value?.takeIf { it.sessionId == sessionId } ?: return
        saving.value = true
        viewModelScope.launch {
            var bitmap: Bitmap? = null
            try {
                bitmap = exporter.render(snapshot, sessionStore.captureFile(sessionId), ExportTarget.Gallery)
                when (gallerySaver.save(bitmap, ExportTarget.Gallery.jpegQuality)) {
                    is SaveResult.Saved -> {
                        sessionStore.update { it.copy(exportedRevision = snapshot.revision) }
                        _events.send(ShareEvent.SavedToGallery)
                    }
                    SaveResult.Failed -> _events.send(ShareEvent.SaveFailed)
                }
            } catch (e: ExportException) {
                _events.send(ShareEvent.SaveFailed)
            } finally {
                bitmap?.recycle()
                saving.value = false
            }
        }
    }

    /** Deletes the session; the screen then returns to the camera. */
    fun discard() {
        viewModelScope.launch {
            sessionStore.discardActive()
            _events.send(ShareEvent.Discarded)
        }
    }

    private companion object {
        const val SESSION_ID_ARG = "sessionId"
    }
}
