package app.storymaker.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.storymaker.R
import app.storymaker.media.EditorTab
import app.storymaker.media.Layer
import app.storymaker.media.PhotoRect
import app.storymaker.media.StickerFrame
import app.storymaker.media.StickerLayer
import app.storymaker.media.StrokeLayer
import app.storymaker.media.StrokeSegment
import app.storymaker.media.fitRect
import app.storymaker.media.handleDragged
import app.storymaker.media.hitTestSticker
import app.storymaker.media.smoothStroke
import app.storymaker.media.stickerFrame
import app.storymaker.media.strokePath
import app.storymaker.media.strokeWidthFraction
import app.storymaker.media.transformed
import kotlin.math.roundToInt

/**
 * The photo with its layers, letterboxed and clipped to rounded corners.
 *
 * - Draw tab: a finger drag draws a stroke that is reported on release; a tap draws a dot.
 * - Stickers tab: tap selects the topmost sticker (or deselects); drag moves it, pinch/twist
 *   resizes and rotates it, the corner handle resizes and rotates with one finger. Each finished
 *   gesture is reported once through [onStickerChanged].
 *
 * @param stickerPainters artwork for the sticker IDs used in [layers]
 */
@Composable
fun EditorCanvas(
    photo: ImageBitmap?,
    layers: List<Layer>,
    tab: EditorTab,
    brushColor: Color,
    brushSizeDp: Float,
    stickerPainters: Map<String, Painter>,
    selectedLayerId: String?,
    onStrokeFinished: (points: List<Float>, widthFraction: Float) -> Unit,
    onSelectLayer: (String?) -> Unit,
    onStickerChanged: (before: StickerLayer, after: StickerLayer) -> Unit,
    onRemoveSticker: (String) -> Unit,
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
    // The sticker being moved or transformed; committed on release.
    var liveSticker by remember { mutableStateOf<StickerLayer?>(null) }

    val currentLayers by rememberUpdatedState(layers)
    val currentSelectedId by rememberUpdatedState(selectedLayerId)
    val currentPainters by rememberUpdatedState(stickerPainters)
    val currentOnStrokeFinished by rememberUpdatedState(onStrokeFinished)
    val currentOnSelectLayer by rememberUpdatedState(onSelectLayer)
    val currentOnStickerChanged by rememberUpdatedState(onStickerChanged)

    val strokes = remember(layers, rect) {
        if (rect == null) emptyMap() else layers.filterIsInstance<StrokeLayer>().associate { it.id to it.toDrawable(rect) }
    }
    fun frameOf(layer: StickerLayer, rect: PhotoRect): StickerFrame? =
        currentPainters[layer.stickerId]?.let { stickerFrame(layer, rect, it.aspectRatio()) }

    // The selected sticker as saved. While a gesture runs, liveSticker holds its in-progress state;
    // that is read only in the draw and layout phases, so each gesture frame redraws without recomposing.
    val selectedSticker = (layers.firstOrNull { it.id == selectedLayerId } as? StickerLayer)
        ?.takeIf { tab == EditorTab.Stickers }
    fun shownSelectedFrame(rect: PhotoRect): StickerFrame? {
        val shown = liveSticker?.takeIf { it.id == selectedSticker?.id } ?: selectedSticker ?: return null
        return frameOf(shown, rect)
    }

    Box(modifier = modifier.onSizeChanged { size = it }) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tab, rect, brushSizeDp) {
                    if (rect == null) return@pointerInput
                    when (tab) {
                        EditorTab.Draw -> {
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
                        }
                        EditorTab.Stickers -> {
                            val handleRadius = HANDLE_TOUCH_SIZE.toPx() / 2
                            val hitSlop = STICKER_HIT_SLOP.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val selected = currentLayers.firstOrNull { it.id == currentSelectedId } as? StickerLayer
                                val handle = selected?.let { frameOf(it, rect) }?.corners()?.get(2)
                                val onHandle = handle != null && (down.position - handle).getDistance() <= handleRadius
                                val target = if (onHandle) {
                                    selected
                                } else {
                                    hitTestSticker(currentLayers, down.position, hitSlop) { frameOf(it, rect) }
                                }
                                if (target == null) {
                                    currentOnSelectLayer(null)
                                    return@awaitEachGesture
                                }
                                down.consume()
                                if (target.id != currentSelectedId) currentOnSelectLayer(target.id)
                                var live: StickerLayer = target
                                liveSticker = live
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.none { it.pressed }) break
                                    live = if (onHandle) {
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        target.handleDragged(down.position, change.position, rect)
                                    } else {
                                        live.transformed(event.calculatePan(), event.calculateZoom(), event.calculateRotation(), rect)
                                    }
                                    liveSticker = live
                                    event.changes.forEach { it.consume() }
                                }
                                if (live != target) currentOnStickerChanged(target, live)
                                liveSticker = null
                            }
                        }
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
                for (layer in layers) {
                    when (layer) {
                        is StrokeLayer -> strokes[layer.id]?.let { drawStroke(it) }
                        is StickerLayer -> {
                            val painter = stickerPainters[layer.stickerId] ?: continue
                            val saved = stickerFrame(layer, rect, painter.aspectRatio())
                            val live = liveSticker?.takeIf { it.id == layer.id }
                            if (live == null) {
                                drawSticker(painter, saved)
                            } else {
                                // Keep rendering the artwork at its saved size and scale it, so the
                                // vector isn't re-rasterized on every frame of a resize.
                                drawSticker(painter, stickerFrame(live, rect, painter.aspectRatio()), rasterWidth = saved.width)
                            }
                        }
                    }
                }
                if (currentPoints.isNotEmpty()) {
                    val current = StrokeLayer("current", brushColor.toArgb(), currentWidth, currentPoints.toList())
                    drawStroke(current.toDrawable(rect))
                }
            }
            shownSelectedFrame(rect)?.let { drawSelection(it) }
        }

        if (rect != null && selectedSticker != null && frameOf(selectedSticker, rect) != null) {
            RemoveStickerButton(
                onClick = { onRemoveSticker(selectedSticker.id) },
                modifier = Modifier.offset {
                    val corner = shownSelectedFrame(rect)?.corners()?.get(0) ?: Offset.Zero
                    val half = (HANDLE_TOUCH_SIZE / 2).roundToPx()
                    IntOffset(corner.x.roundToInt() - half, corner.y.roundToInt() - half)
                },
            )
        }
    }
}

/** The ✕ shown at the selected sticker's top-left corner; 28dp visually, 48dp to touch. */
@Composable
private fun RemoveStickerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.sticker_remove)
    Box(
        modifier = modifier
            .size(HANDLE_TOUCH_SIZE)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(RemoveButtonColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
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

/**
 * Draws [painter] into [frame]. The artwork is rendered at [rasterWidth] and scaled to the frame,
 * which lets an in-progress resize reuse the painter's cached rendering.
 */
private fun DrawScope.drawSticker(painter: Painter, frame: StickerFrame, rasterWidth: Float = frame.width) {
    val factor = frame.width / rasterWidth
    rotate(frame.rotation, pivot = frame.center) {
        translate(frame.centerX - frame.width / 2, frame.centerY - frame.height / 2) {
            scale(factor, pivot = Offset.Zero) {
                with(painter) { draw(Size(rasterWidth, rasterWidth * frame.height / frame.width)) }
            }
        }
    }
}

/** Dashed box around the selected sticker plus the resize/rotate handle at its bottom-right corner. */
private fun DrawScope.drawSelection(frame: StickerFrame) {
    rotate(frame.rotation, pivot = frame.center) {
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(frame.centerX - frame.width / 2, frame.centerY - frame.height / 2),
            size = Size(frame.width, frame.height),
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            ),
        )
    }
    drawCircle(Color.White, radius = 6.dp.toPx(), center = frame.corners()[2])
}

private fun Painter.aspectRatio(): Float {
    val size = intrinsicSize
    return if (size.isSpecified && size.width > 0f) size.height / size.width else 1f
}

private fun MutableList<Float>.addPoint(position: Offset, rect: PhotoRect) {
    add(rect.toNormalizedX(position.x))
    add(rect.toNormalizedY(position.y))
}

private val MIN_POINT_DISTANCE = 1.5.dp
private val CORNER_RADIUS = 20.dp
private val HANDLE_TOUCH_SIZE = 48.dp
private val STICKER_HIT_SLOP = 4.dp
private val RemoveButtonColor = Color(0xFF111111)
