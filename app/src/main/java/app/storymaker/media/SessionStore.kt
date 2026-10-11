package app.storymaker.media

import app.storymaker.ApplicationScope
import app.storymaker.IoDispatcher
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Reads the pixel size of an image file without decoding it. */
fun interface PhotoSizeReader {
    fun read(file: File): PhotoSize?
}

data class PhotoSize(val width: Int, val height: Int)

data class NewSession(val id: String, val captureFile: File)

sealed interface LoadResult {
    data class Restored(val snapshot: SessionSnapshot) : LoadResult

    /** The snapshot was unreadable or unsupported; the photo was kept and edits were reset. */
    data class RestoredWithoutEdits(val snapshot: SessionSnapshot) : LoadResult

    /** The photo is gone; the session was deleted. */
    data object PhotoMissing : LoadResult
}

enum class SessionError { SaveFailed }

/**
 * The single owner of session state and the only code that touches session files
 * (docs/ARCHITECTURE.md section 9).
 *
 * State lives in memory as a [StateFlow]; changes are saved ~300ms after the last [update],
 * and immediately on [flush]. File operations are serialized behind one lock and writes are atomic.
 */
@Singleton
class SessionStore @Inject constructor(
    private val files: SessionFiles,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val photoSizeReader: PhotoSizeReader,
) {
    private val lock = Mutex()
    private val saveJobLock = Any()
    private var saveJob: Job? = null

    @Volatile
    private var dirty = false

    private val _state = MutableStateFlow<SessionSnapshot?>(null)
    val state: StateFlow<SessionSnapshot?> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<SessionError>(extraBufferCapacity = 1)
    val errors: SharedFlow<SessionError> = _errors.asSharedFlow()

    /** Replaces any previous session with a new, empty one. Throws [IOException] if storage fails. */
    suspend fun createSession(): NewSession = lock.withLock {
        cancelPendingSave()
        _state.value = null
        withContext(io) {
            deleteActiveSessionFiles()
            val id = UUID.randomUUID().toString()
            if (!files.sessionDir(id).mkdirs()) throw IOException("Could not create session folder")
            files.activeSessionFile.writeAtomically { it.write(id.toByteArray()) }
            NewSession(id, files.captureFile(id))
        }
    }

    /** Writes the first snapshot once the capture is normalized. Throws [IOException] if storage fails. */
    suspend fun completeCapture(id: String, photoSize: PhotoSize) = lock.withLock {
        val snapshot = SessionSnapshot(
            sessionId = id,
            createdAt = System.currentTimeMillis(),
            photoWidth = photoSize.width,
            photoHeight = photoSize.height,
        )
        withContext(io) { writeSnapshot(snapshot) }
        _state.value = snapshot
        dirty = false
    }

    /** Loads session [id] (e.g. after process death), handling unreadable snapshots and missing photos. */
    suspend fun load(id: String): LoadResult = lock.withLock {
        _state.value?.takeIf { it.sessionId == id }?.let { return@withLock LoadResult.Restored(it) }

        withContext(io) {
            val captureFile = files.captureFile(id)
            if (!captureFile.exists()) {
                files.sessionDir(id).deleteRecursively()
                if (readActiveId() == id) files.activeSessionFile.delete()
                return@withContext LoadResult.PhotoMissing
            }
            if (readActiveId() != id) files.activeSessionFile.writeAtomically { it.write(id.toByteArray()) }

            val restored = readSnapshot(id)
            if (restored != null) {
                _state.value = restored
                LoadResult.Restored(restored)
            } else {
                val size = photoSizeReader.read(captureFile) ?: PhotoSize(0, 0)
                val reset = SessionSnapshot(
                    sessionId = id,
                    createdAt = System.currentTimeMillis(),
                    photoWidth = size.width,
                    photoHeight = size.height,
                )
                _state.value = reset
                runCatching { writeSnapshot(reset) }
                LoadResult.RestoredWithoutEdits(reset)
            }
        }
    }

    /** The normalized photo of session [id]. */
    fun captureFile(id: String): File = files.captureFile(id)

    /** Applies [transform] to the current session in memory and schedules a save. No-op without a session. */
    fun update(transform: (SessionSnapshot) -> SessionSnapshot) {
        var changed = false
        while (true) {
            val current = _state.value ?: return
            val next = transform(current)
            if (_state.compareAndSet(current, next)) {
                changed = next != current
                break
            }
        }
        if (changed) {
            dirty = true
            scheduleSave()
        }
    }

    /** Saves pending changes immediately. */
    suspend fun flush() {
        cancelPendingSave()
        if (dirty) persist()
    }

    /** Fire-and-forget [flush], e.g. when the app goes to the background. */
    fun flushAsync() {
        scope.launch { flush() }
    }

    /** Deletes the active session (on a fresh launch, discard, or New photo). */
    suspend fun discardActive() = lock.withLock {
        cancelPendingSave()
        _state.value = null
        dirty = false
        withContext(io) { deleteActiveSessionFiles() }
    }

    /** Deletes session folders left behind by crashes or interrupted flows: everything except the active session. */
    suspend fun cleanUpOrphans() = lock.withLock {
        withContext(io) {
            val active = readActiveId()
            files.sessionsDir.listFiles()
                ?.filter { it.isDirectory && it.name != active }
                ?.forEach { it.deleteRecursively() }
        }
    }

    private fun scheduleSave() = synchronized(saveJobLock) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            persist()
        }
    }

    private fun cancelPendingSave() = synchronized(saveJobLock) {
        saveJob?.cancel()
        saveJob = null
    }

    private suspend fun persist() = lock.withLock {
        val snapshot = _state.value ?: return@withLock
        val saved = withContext(io) {
            // The session may have been discarded while the save was pending.
            if (!files.sessionDir(snapshot.sessionId).exists()) return@withContext true
            try {
                writeSnapshot(snapshot)
                true
            } catch (e: IOException) {
                false
            }
        }
        if (!saved) {
            _errors.tryEmit(SessionError.SaveFailed)
        } else if (_state.value === snapshot) {
            dirty = false
        }
    }

    private fun writeSnapshot(snapshot: SessionSnapshot) {
        val encoded = SessionSnapshot.json.encodeToString(SessionSnapshot.serializer(), snapshot)
        files.snapshotFile(snapshot.sessionId).writeAtomically { it.write(encoded.toByteArray()) }
    }

    private fun readSnapshot(id: String): SessionSnapshot? = runCatching {
        SessionSnapshot.json.decodeFromString(SessionSnapshot.serializer(), files.snapshotFile(id).readText())
    }.getOrNull()?.takeIf { it.version <= SessionSnapshot.CURRENT_VERSION && it.sessionId == id }

    private fun readActiveId(): String? =
        files.activeSessionFile.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }

    private fun deleteActiveSessionFiles() {
        readActiveId()?.let { files.sessionDir(it).deleteRecursively() }
        files.activeSessionFile.delete()
    }

    companion object {
        const val SAVE_DEBOUNCE_MILLIS = 300L
    }
}
