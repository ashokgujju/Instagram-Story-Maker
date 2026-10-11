package app.storymaker.media

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Sticker width as a fraction of the photo width when first placed. */
const val DEFAULT_STICKER_SCALE = 0.35f
const val MIN_STICKER_SCALE = 0.08f
const val MAX_STICKER_SCALE = 1.5f

/**
 * A sticker's box in screen pixels: centered at ([centerX], [centerY]), [width] × [height]
 * before rotation, rotated clockwise by [rotation] degrees around its center.
 */
data class StickerFrame(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotation: Float,
) {
    val center: Offset get() = Offset(centerX, centerY)

    /** Corners after rotation: top-left, top-right, bottom-right, bottom-left. */
    fun corners(): List<Offset> = listOf(
        rotated(-width / 2, -height / 2),
        rotated(width / 2, -height / 2),
        rotated(width / 2, height / 2),
        rotated(-width / 2, height / 2),
    )

    /** Whether [point] lies inside the rotated box, extended by [slop] on every side. */
    fun contains(point: Offset, slop: Float = 0f): Boolean {
        val radians = Math.toRadians(-rotation.toDouble())
        val dx = point.x - centerX
        val dy = point.y - centerY
        val localX = dx * cos(radians) - dy * sin(radians)
        val localY = dx * sin(radians) + dy * cos(radians)
        return abs(localX) <= width / 2 + slop && abs(localY) <= height / 2 + slop
    }

    private fun rotated(x: Float, y: Float): Offset {
        val radians = Math.toRadians(rotation.toDouble())
        return Offset(
            (centerX + x * cos(radians) - y * sin(radians)).toFloat(),
            (centerY + x * sin(radians) + y * cos(radians)).toFloat(),
        )
    }
}

/**
 * Screen frame of [layer] in [rect]. [aspectRatio] is the sticker artwork's height / width.
 */
fun stickerFrame(layer: StickerLayer, rect: PhotoRect, aspectRatio: Float): StickerFrame {
    val width = layer.scale * rect.width
    return StickerFrame(
        centerX = rect.fromNormalizedX(layer.centerX),
        centerY = rect.fromNormalizedY(layer.centerY),
        width = width,
        height = width * aspectRatio,
        rotation = layer.rotation,
    )
}

/** The topmost sticker under [point], or null. [frameOf] returns null for stickers that can't be drawn. */
fun hitTestSticker(
    layers: List<Layer>,
    point: Offset,
    slop: Float = 0f,
    frameOf: (StickerLayer) -> StickerFrame?,
): StickerLayer? = layers.asReversed()
    .filterIsInstance<StickerLayer>()
    .firstOrNull { frameOf(it)?.contains(point, slop) == true }

/**
 * Applies a gesture step: [pan] in pixels, [zoom] as a scale factor, [rotationDelta] in degrees.
 * Size stays within [MIN_STICKER_SCALE]..[MAX_STICKER_SCALE] and the center stays on the photo.
 */
fun StickerLayer.transformed(pan: Offset, zoom: Float, rotationDelta: Float, rect: PhotoRect): StickerLayer = copy(
    centerX = (centerX + pan.x / rect.width).coerceIn(0f, 1f),
    centerY = (centerY + pan.y / rect.height).coerceIn(0f, 1f),
    scale = (scale * zoom).coerceIn(MIN_STICKER_SCALE, MAX_STICKER_SCALE),
    rotation = normalizedDegrees(rotation + rotationDelta),
)

/**
 * Resize-and-rotate from the corner handle: the sticker scales with the finger's distance from
 * its center and rotates with the finger's angle around it, relative to where the drag started.
 */
fun StickerLayer.handleDragged(start: Offset, current: Offset, rect: PhotoRect): StickerLayer {
    val center = Offset(rect.fromNormalizedX(centerX), rect.fromNormalizedY(centerY))
    val startVector = start - center
    val currentVector = current - center
    val startDistance = hypot(startVector.x, startVector.y)
    if (startDistance < 1f) return this
    val distanceRatio = hypot(currentVector.x, currentVector.y) / startDistance
    val angleDelta = Math.toDegrees(
        (atan2(currentVector.y, currentVector.x) - atan2(startVector.y, startVector.x)).toDouble(),
    ).toFloat()
    return copy(
        scale = (scale * distanceRatio).coerceIn(MIN_STICKER_SCALE, MAX_STICKER_SCALE),
        rotation = normalizedDegrees(rotation + angleDelta),
    )
}

/** Degrees in -180..180. */
internal fun normalizedDegrees(degrees: Float): Float {
    var value = degrees % 360f
    if (value > 180f) value -= 360f
    if (value < -180f) value += 360f
    return value
}
