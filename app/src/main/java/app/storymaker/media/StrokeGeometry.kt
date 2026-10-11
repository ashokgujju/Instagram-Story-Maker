package app.storymaker.media

import androidx.compose.ui.graphics.Path
import kotlin.math.min

/** Where the photo is drawn inside its container, in pixels. */
data class PhotoRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun toNormalizedX(x: Float) = (x - left) / width
    fun toNormalizedY(y: Float) = (y - top) / height
    fun fromNormalizedX(x: Float) = left + x * width
    fun fromNormalizedY(y: Float) = top + y * height
}

/** Largest rectangle with the image's aspect ratio that fits the container, centered (letterboxed). */
fun fitRect(imageWidth: Int, imageHeight: Int, containerWidth: Float, containerHeight: Float): PhotoRect {
    val scale = min(containerWidth / imageWidth, containerHeight / imageHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    return PhotoRect((containerWidth - width) / 2, (containerHeight - height) / 2, width, height)
}

/** Stroke width as a fraction of the image width, from a brush size in pixels at the current display size. */
fun strokeWidthFraction(brushSizePx: Float, rect: PhotoRect): Float = brushSizePx / rect.width

/** Rounds normalized coordinates to 4 decimals (~0.4px at 4096px) to keep snapshots compact. */
fun roundNormalized(points: List<Float>): List<Float> = points.map { Math.round(it * 10_000f) / 10_000f }

/** Path commands of a smoothed stroke, in normalized coordinates. */
sealed interface StrokeSegment {
    data class MoveTo(val x: Float, val y: Float) : StrokeSegment
    data class LineTo(val x: Float, val y: Float) : StrokeSegment
    data class QuadTo(val controlX: Float, val controlY: Float, val x: Float, val y: Float) : StrokeSegment

    /** A stroke of a single point (a tap), drawn as a filled circle. */
    data class Dot(val x: Float, val y: Float) : StrokeSegment
}

/**
 * Smooths flattened points (x0, y0, x1, y1, …) with quadratic curves through the midpoints
 * between consecutive points, using each point as the control point.
 */
fun smoothStroke(points: List<Float>): List<StrokeSegment> {
    val count = points.size / 2
    if (count == 0) return emptyList()
    fun x(i: Int) = points[2 * i]
    fun y(i: Int) = points[2 * i + 1]
    if (count == 1) return listOf(StrokeSegment.Dot(x(0), y(0)))

    val segments = mutableListOf<StrokeSegment>(StrokeSegment.MoveTo(x(0), y(0)))
    for (i in 1 until count - 1) {
        segments += StrokeSegment.QuadTo(x(i), y(i), (x(i) + x(i + 1)) / 2, (y(i) + y(i + 1)) / 2)
    }
    segments += StrokeSegment.LineTo(x(count - 1), y(count - 1))
    return segments
}

/** Builds the path for [segments] mapped into [rect]. Dots are not part of the path. */
fun strokePath(segments: List<StrokeSegment>, rect: PhotoRect): Path = Path().apply {
    for (segment in segments) {
        when (segment) {
            is StrokeSegment.MoveTo -> moveTo(rect.fromNormalizedX(segment.x), rect.fromNormalizedY(segment.y))
            is StrokeSegment.LineTo -> lineTo(rect.fromNormalizedX(segment.x), rect.fromNormalizedY(segment.y))
            is StrokeSegment.QuadTo -> quadraticTo(
                rect.fromNormalizedX(segment.controlX),
                rect.fromNormalizedY(segment.controlY),
                rect.fromNormalizedX(segment.x),
                rect.fromNormalizedY(segment.y),
            )
            is StrokeSegment.Dot -> Unit
        }
    }
}
