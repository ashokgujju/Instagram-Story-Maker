package app.storymaker.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.storymaker.media.PhotoNormalizer
import app.storymaker.media.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

enum class FlashMode(val imageCaptureMode: Int) {
    Off(ImageCapture.FLASH_MODE_OFF),
    Auto(ImageCapture.FLASH_MODE_AUTO),
    On(ImageCapture.FLASH_MODE_ON);

    fun next(): FlashMode = entries[(ordinal + 1) % entries.size]
}

enum class CaptureError { CameraUnavailable, CaptureFailed, StorageFailed }

data class CaptureUiState(
    val status: Status = Status.Ready,
    val useFrontCamera: Boolean = false,
    val flashMode: FlashMode = FlashMode.Off,
    val hasFlashUnit: Boolean = false,
) {
    sealed interface Status {
        data object Ready : Status
        data object Capturing : Status
        data object Processing : Status
        data class Error(val error: CaptureError) : Status
    }

    val isBusy: Boolean get() = status == Status.Capturing || status == Status.Processing
}

sealed interface CaptureEvent {
    data class Captured(val sessionId: String) : CaptureEvent
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionStore: SessionStore,
    private val normalizer: PhotoNormalizer,
) : ViewModel() {

    // Portrait 16:9 for preview and capture (D11), capped at ~4K instead of full sensor resolution.
    private val resolutionSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(
            ResolutionStrategy(Size(3840, 2160), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
        )
        .build()

    private val preview = Preview.Builder()
        .setResolutionSelector(resolutionSelector)
        .build()
        .apply { setSurfaceProvider { request -> _surfaceRequest.value = request } }

    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setResolutionSelector(resolutionSelector)
        .build()

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _events = Channel<CaptureEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val bindRequests = MutableStateFlow(0)

    /** Binds the camera to [lifecycleOwner] until cancelled, rebinding when the lens changes or on retry. */
    suspend fun bindToCamera(lifecycleOwner: LifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        combine(_uiState, bindRequests) { state, attempt -> state.useFrontCamera to attempt }
            .distinctUntilChanged()
            .collectLatest { (useFront, _) ->
                provider.unbindAll()
                val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val camera = try {
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                } catch (e: IllegalArgumentException) {
                    setError(CaptureError.CameraUnavailable)
                    return@collectLatest
                } catch (e: IllegalStateException) {
                    setError(CaptureError.CameraUnavailable)
                    return@collectLatest
                }
                _uiState.update { it.copy(hasFlashUnit = camera.cameraInfo.hasFlashUnit()) }
                imageCapture.flashMode = _uiState.value.flashMode.imageCaptureMode
                // Camera in use by another app, disabled by policy, etc.
                val cameraState = camera.cameraInfo.cameraState
                val observer = Observer<CameraState> { state ->
                    if (state.error != null && state.type == CameraState.Type.CLOSED) {
                        setError(CaptureError.CameraUnavailable)
                    }
                }
                cameraState.observe(lifecycleOwner, observer)
                try {
                    awaitCancellation()
                } finally {
                    cameraState.removeObserver(observer)
                    provider.unbindAll()
                }
            }
    }

    /** Physical device rotation (a `Surface.ROTATION_*` value); the UI is portrait-only, so photos follow the device. */
    fun setTargetRotation(rotation: Int) {
        imageCapture.targetRotation = rotation
    }

    fun switchCamera() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(useFrontCamera = !it.useFrontCamera, status = CaptureUiState.Status.Ready) }
    }

    fun cycleFlash() {
        val next = _uiState.value.flashMode.next()
        imageCapture.flashMode = next.imageCaptureMode
        _uiState.update { it.copy(flashMode = next) }
    }

    fun retry() {
        _uiState.update { it.copy(status = CaptureUiState.Status.Ready) }
        bindRequests.update { it + 1 }
    }

    fun capture() {
        if (_uiState.value.status != CaptureUiState.Status.Ready) return
        val isFrontCamera = _uiState.value.useFrontCamera
        _uiState.update { it.copy(status = CaptureUiState.Status.Capturing) }
        viewModelScope.launch {
            val session = try {
                sessionStore.createSession()
            } catch (e: IOException) {
                setError(CaptureError.StorageFailed)
                return@launch
            }
            try {
                takePicture(session.captureFile)
                _uiState.update { it.copy(status = CaptureUiState.Status.Processing) }
                val size = normalizer.normalize(session.captureFile, isFrontCamera)
                sessionStore.completeCapture(session.id, size)
            } catch (e: ImageCaptureException) {
                sessionStore.discardActive()
                setError(CaptureError.CaptureFailed)
                return@launch
            } catch (e: IOException) {
                sessionStore.discardActive()
                setError(CaptureError.StorageFailed)
                return@launch
            } catch (e: IllegalStateException) {
                sessionStore.discardActive()
                setError(CaptureError.CaptureFailed)
                return@launch
            }
            _uiState.update { it.copy(status = CaptureUiState.Status.Ready) }
            _events.send(CaptureEvent.Captured(session.id))
        }
    }

    private suspend fun takePicture(file: File) = suspendCancellableCoroutine { continuation ->
        // Ask CameraX not to mirror; PhotoNormalizer mirrors front-camera photos exactly once.
        val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = false }
        val options = ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build()
        imageCapture.takePicture(
            options,
            Dispatchers.IO.asExecutor(),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    continuation.resume(Unit)
                }

                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            },
        )
    }

    private fun setError(error: CaptureError) {
        _uiState.update { it.copy(status = CaptureUiState.Status.Error(error)) }
    }
}
