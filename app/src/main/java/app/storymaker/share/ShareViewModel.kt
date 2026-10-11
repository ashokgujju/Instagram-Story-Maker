package app.storymaker.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.storymaker.BuildConfig
import app.storymaker.media.CaptionRenderer
import app.storymaker.media.CaptionStickerSpec
import app.storymaker.media.ExportException
import app.storymaker.media.ExportTarget
import app.storymaker.media.GallerySaver
import app.storymaker.media.ImageExporter
import app.storymaker.media.LoadResult
import app.storymaker.media.SaveResult
import app.storymaker.media.SessionStore
import app.storymaker.media.ShareFiles
import app.storymaker.media.edgeColors
import app.storymaker.media.isSavedToGallery
import app.storymaker.media.normalizeCaption
import app.storymaker.media.writeJpeg
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SaveState { Idle, Saving, Saved }

enum class ShareButtonState {
    /** "Share to Instagram Stories". */
    Ready,

    /** "Preparing…" while the share files are written. */
    Preparing,

    /** "Share again" after a share was launched. */
    SharedBefore,

    /** "Get Instagram": Instagram is missing; opens the Play Store. */
    InstagramMissing,
}

data class ShareUiState(
    /** The share render (photo + drawings + stickers), or null while rendering. */
    val preview: ImageBitmap? = null,
    /** The caption sticker exactly as Instagram receives it, or null when it isn't shown. */
    val captionSticker: CaptionStickerPreview? = null,
    val saveState: SaveState = SaveState.Idle,
    val shareButton: ShareButtonState = ShareButtonState.Ready,
    val shared: Boolean = false,
    /** The first-share coach mark, shown before Instagram opens; null when hidden. */
    val coachMark: List<CoachMarkLine>? = null,
) {
    /** Leaving would lose a photo that was neither saved nor shared, so ask first. */
    val needsDiscardConfirmation: Boolean get() = needsDiscardConfirmation(saved = saveState == SaveState.Saved, shared = shared)
}

/** New photo asks for confirmation only if the photo was neither saved to the gallery nor shared. */
fun needsDiscardConfirmation(saved: Boolean, shared: Boolean): Boolean = !saved && !shared

/** The rendered caption sticker and its width as a fraction of the Story width. */
data class CaptionStickerPreview(val image: ImageBitmap, val widthFraction: Float)

sealed interface ShareEvent {
    data object PhotoUnavailable : ShareEvent
    data object EditsReset : ShareEvent
    data object SavedToGallery : ShareEvent
    data object SaveFailed : ShareEvent
    data object Discarded : ShareEvent

    /** Start the Stories composer (needs an Activity, so the screen does it). */
    data class Launch(val request: StoryShareRequest) : ShareEvent

    data object InstagramNotInstalled : ShareEvent
    data object ShareFailed : ShareEvent
    data object MissingFacebookAppId : ShareEvent
}

/** Transient flags of the screen. */
private data class Flags(
    val saving: Boolean = false,
    val preparingShare: Boolean = false,
    val instagramAvailable: Boolean = true,
    val coachMark: List<CoachMarkLine>? = null,
)

@HiltViewModel
class ShareViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val sessionStore: SessionStore,
    private val exporter: ImageExporter,
    private val gallerySaver: GallerySaver,
    private val captionRenderer: CaptionRenderer,
    private val shareFiles: ShareFiles,
    private val coachMarkStore: CoachMarkStore,
) : ViewModel() {

    // Navigation stores route properties under their names (ShareRoute.sessionId).
    private val sessionId: String = checkNotNull(savedStateHandle[SESSION_ID_ARG])

    private val preview = MutableStateFlow<ImageBitmap?>(null)
    private val captionSticker = MutableStateFlow<CaptionStickerPreview?>(null)
    private val flags = MutableStateFlow(Flags())

    val uiState: StateFlow<ShareUiState> = combine(
        preview,
        captionSticker,
        flags,
        sessionStore.state,
    ) { preview, captionSticker, flags, snapshot ->
        val session = snapshot?.takeIf { it.sessionId == sessionId }
        val shared = session?.sharedAt != null
        ShareUiState(
            preview = preview,
            captionSticker = captionSticker,
            saveState = when {
                flags.saving -> SaveState.Saving
                session?.isSavedToGallery == true -> SaveState.Saved
                else -> SaveState.Idle
            },
            shareButton = when {
                !flags.instagramAvailable -> ShareButtonState.InstagramMissing
                flags.preparingShare -> ShareButtonState.Preparing
                shared -> ShareButtonState.SharedBefore
                else -> ShareButtonState.Ready
            },
            shared = shared,
            coachMark = flags.coachMark,
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
                        // Shown at the width Instagram uses for interactive stickers, so the preview matches.
                        widthFraction = CaptionStickerSpec.INSTAGRAM_DISPLAY_WIDTH_FRACTION,
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

    /** Re-checks whether Instagram can take a Stories share (called whenever the screen resumes). */
    fun refreshAvailability() {
        flags.update { it.copy(instagramAvailable = InstagramStories.isAvailable(context)) }
    }

    /**
     * Prepares a Stories share from the preview renders (no re-rendering): writes the share files,
     * copies the caption if requested, and asks the screen to launch Instagram.
     */
    fun share() {
        val current = uiState.value
        if (current.shareButton == ShareButtonState.Preparing || current.shareButton == ShareButtonState.InstagramMissing) return
        val background = current.preview?.asAndroidBitmap() ?: return
        val session = sessionStore.state.value?.takeIf { it.sessionId == sessionId } ?: return
        if (BuildConfig.FACEBOOK_APP_ID.isBlank()) {
            viewModelScope.launch { _events.send(ShareEvent.MissingFacebookAppId) }
            return
        }
        // First share ever: explain the caption sticker and clipboard before Instagram opens.
        if (!coachMarkStore.seen) {
            val lines = coachMarkLines(
                stickerShown = current.captionSticker != null,
                captionCopied = session.copyToClipboard && normalizeCaption(session.caption).isNotEmpty(),
            )
            if (lines.isNotEmpty()) {
                flags.update { it.copy(coachMark = lines) }
                return
            }
        }
        val sticker = current.captionSticker?.image?.asAndroidBitmap()
        flags.update { it.copy(preparingShare = true) }
        viewModelScope.launch {
            try {
                val files = shareFiles.write(
                    writeBackground = { background.writeJpeg(it, ExportTarget.Share.jpegQuality) },
                    writeSticker = sticker?.let { bitmap -> { out -> if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("PNG encoding failed") } },
                )
                val colors = background.edgeColors()
                val caption = normalizeCaption(session.caption)
                if (session.copyToClipboard && caption.isNotEmpty()) copyToClipboard(caption)
                _events.send(
                    ShareEvent.Launch(
                        StoryShareRequest(
                            backgroundUri = files.background.contentUri(),
                            stickerUri = files.sticker?.contentUri(),
                            topColor = colors.top,
                            bottomColor = colors.bottom,
                            facebookAppId = BuildConfig.FACEBOOK_APP_ID,
                        ),
                    ),
                )
            } catch (e: IOException) {
                _events.send(ShareEvent.ShareFailed)
            } finally {
                flags.update { it.copy(preparingShare = false) }
            }
        }
    }

    /** "Got it" on the coach mark: never show it again, and continue the share. */
    fun confirmCoachMark() {
        coachMarkStore.markSeen()
        flags.update { it.copy(coachMark = null) }
        share()
    }

    /** Coach mark dismissed without "Got it": no share; it shows again next time. */
    fun dismissCoachMark() {
        flags.update { it.copy(coachMark = null) }
    }

    /** Instagram opened: record the share (we can't know whether the user posts). */
    fun onLaunched() {
        sessionStore.update { it.copy(sharedAt = System.currentTimeMillis()) }
    }

    /** Instagram couldn't be launched (e.g. uninstalled mid-flow). */
    fun onLaunchFailed() {
        flags.update { it.copy(instagramAvailable = false) }
        viewModelScope.launch { _events.send(ShareEvent.InstagramNotInstalled) }
    }

    /** Renders the gallery version and saves it. The caller handles the API 26–28 storage permission. */
    fun save() {
        if (flags.value.saving || uiState.value.saveState == SaveState.Saved) return
        val snapshot = sessionStore.state.value?.takeIf { it.sessionId == sessionId } ?: return
        flags.update { it.copy(saving = true) }
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
                flags.update { it.copy(saving = false) }
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

    private fun copyToClipboard(caption: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, caption))
    }

    private fun File.contentUri(): String =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", this).toString()

    private companion object {
        const val SESSION_ID_ARG = "sessionId"
        const val CLIP_LABEL = "Caption"
    }
}
