package app.storymaker.media

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An editor layer in normalized image coordinates (0..1 of the photo's width and height),
 * independent of display size (D3).
 */
@Serializable
sealed interface Layer {
    val id: String
}

/**
 * A freehand stroke.
 *
 * @param color ARGB color
 * @param width stroke width as a fraction of the image width
 * @param points flattened normalized coordinates: x0, y0, x1, y1, …
 */
@Serializable
@SerialName("stroke")
data class StrokeLayer(
    override val id: String,
    val color: Int,
    val width: Float,
    val points: List<Float>,
) : Layer

/**
 * A placed sticker.
 *
 * @param scale sticker width as a fraction of the image width
 * @param rotation clockwise rotation in degrees
 */
@Serializable
@SerialName("sticker")
data class StickerLayer(
    override val id: String,
    val stickerId: String,
    val centerX: Float,
    val centerY: Float,
    val scale: Float,
    val rotation: Float,
) : Layer

/**
 * One undo/redo entry: the layer at [index] changed from [before] to [after].
 * Adding a layer has no [before], removing one has no [after]. Only the change is stored,
 * so history size grows with the edits, not with the number of layers.
 */
@Serializable
data class LayerChange(
    val index: Int,
    val before: Layer? = null,
    val after: Layer? = null,
) {
    fun inverted() = LayerChange(index, before = after, after = before)
}

@Serializable
enum class EditorTab { Draw, Stickers }
