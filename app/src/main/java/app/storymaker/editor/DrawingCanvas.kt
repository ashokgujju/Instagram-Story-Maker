package app.storymaker.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.storymaker.media.Layer
import app.storymaker.media.PhotoRect
import app.storymaker.media.StrokeLayer
import app.storymaker.media.StrokeSegment
import app.storymaker.media.fitRect
import app.storymaker.media.smoothStroke
import app.storymaker.media.strokePath
import app.storymaker.media.strokeWidthFraction

/**
 * The photo with its layers, letterboxed and clipped to rounded corners. When [drawingEnabled],
 * a finger drag draws a stroke that is reported on release; a tap draws a dot.
 */
@Composable
fun DrawingCanvas(
    photo: ImageBitmap?,
    layers: List<Layer>,
    drawingEnabled: Boolean,
    brushColor: Color,
    brushSizeDp: Float,
    onStrokeFinished: (points: List<Float>, widthFraction: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val rect = if (photo != null && size != IntSize.Zero) {
        fitRect(photo.width, photo.height, size.width.toFloat(), size.height.toFloat())
    } else {
        null
    }
    // The stroke being drawn, in normalized coordinates; committed on release.
    val currentPoints = remember { mutableStateListOf<Float>() }
    var currentWidth by remember { mutableFloatStateOf(0f) }
    val currentOnStrokeFinished by rememberUpdatedState(onStrokeFinished)
    val strokes = remember(layers, rect) {
        if (rect == null) emptyList() else layers.filterIsInstance<StrokeLayer>().map { it.toDrawable(rect) }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(drawingEnabled, rect, brushSizeDp) {
                if (!drawingEnabled || rect == null) return@pointerInput
                val minDistance = MIN_POINT_DISTANCE.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val width = strokeWidthFraction(brushSizeDp.dp.toPx(), rect)
                    currentWidth = width
                    currentPoints.clear()
                    currentPoints.addPoint(down.position, rect)
                    var last = down.position
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if ((change.position - last).getDistance() >= minDistance) {
                            currentPoints.addPoint(change.position, rect)
                            last = change.position
                        }
                        change.consume()
                    }
                    currentOnStrokeFinished(currentPoints.toList(), width)
                    currentPoints.clear()
                }
            },
    ) {
        if (photo == null || rect == null) return@Canvas
        val clip = Path().apply {
            addRoundRect(
                RoundRect(rect.left, rect.top, rect.left + rect.width, rect.top + rect.height, CornerRadius(CORNER_RADIUS.toPx())),
            )
        }
        clipPath(clip) {
            drawImage(
                image = photo,
                dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt()),
                dstSize = IntSize(rect.width.toInt(), rect.height.toInt()),
                filterQuality = FilterQuality.Medium,
            )
            strokes.forEach { drawStroke(it) }
            if (currentPoints.isNotEmpty()) {
                val current = StrokeLayer("current", brushColor.toArgb(), currentWidth, currentPoints.toList())
                drawStroke(current.toDrawable(rect))
            }
        }
    }
}

/** A stroke ready to draw at the current display size. */
private class DrawableStroke(val color: Color, val widthPx: Float, val path: Path?, val dot: Offset?)

private fun StrokeLayer.toDrawable(rect: PhotoRect): DrawableStroke {
    val segments = smoothStroke(points)
    val dot = (segments.singleOrNull() as? StrokeSegment.Dot)?.let {
        Offset(rect.fromNormalizedX(it.x), rect.fromNormalizedY(it.y))
    }
    return DrawableStroke(
        color = Color(color),
        widthPx = width * rect.width,
        path = if (dot == null) strokePath(segments, rect) else null,
        dot = dot,
    )
}

private fun DrawScope.drawStroke(stroke: DrawableStroke) {
    stroke.dot?.let { drawCircle(stroke.color, radius = stroke.widthPx / 2, center = it) }
    stroke.path?.let {
        drawPath(it, stroke.color, style = Stroke(width = stroke.widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun MutableList<Float>.addPoint(position: Offset, rect: PhotoRect) {
    add(rect.toNormalizedX(position.x))
    add(rect.toNormalizedY(position.y))
}

private val MIN_POINT_DISTANCE = 1.5.dp
private val CORNER_RADIUS = 20.dp
