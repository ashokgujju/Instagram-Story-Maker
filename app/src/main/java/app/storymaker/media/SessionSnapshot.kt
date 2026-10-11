package app.storymaker.media

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Persisted state of one editing session (`session.json`).
 *
 * Schema policy: new fields get default values so older files stay readable. Bump
 * [CURRENT_VERSION] only for incompatible changes; files with a newer version are treated as unreadable.
 */
@Serializable
data class SessionSnapshot(
    val version: Int = CURRENT_VERSION,
    val sessionId: String,
    val createdAt: Long,
    val photoWidth: Int,
    val photoHeight: Int,
    val caption: String = "",
    val showOnPhoto: Boolean = true,
    val copyToClipboard: Boolean = false,
    /** Incremented on every layer change. */
    val revision: Long = 0,
    /** [revision] at the last successful gallery save, or null if never saved. */
    val exportedRevision: Long? = null,
    /** Time of the last Instagram share launch, or null if never shared. */
    val sharedAt: Long? = null,
    val layers: List<Layer> = emptyList(),
    /** Most recent change last; capped at [HISTORY_LIMIT]. */
    val undoStack: List<LayerChange> = emptyList(),
    /** Most recently undone change last. */
    val redoStack: List<LayerChange> = emptyList(),
    val editorTab: EditorTab = EditorTab.Draw,
    /** ARGB color of the brush. */
    val brushColor: Int = DEFAULT_BRUSH_COLOR,
    val brushSizeDp: Float = DEFAULT_BRUSH_SIZE_DP,
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val HISTORY_LIMIT = 50
        const val DEFAULT_BRUSH_COLOR = 0xFFFFFFFF.toInt()
        const val DEFAULT_BRUSH_SIZE_DP = 8f

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
