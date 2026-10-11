package app.storymaker.editor

import app.storymaker.media.Layer
import app.storymaker.media.LayerChange
import app.storymaker.media.SessionSnapshot

// Undo/redo for editor layers. Every edit is recorded as a LayerChange; undo applies its inverse.
// Each function returns a new snapshot and increments the revision when the layers change.

/** Adds [layer] on top of the others. */
fun SessionSnapshot.addLayer(layer: Layer): SessionSnapshot =
    applyEdit(LayerChange(index = layers.size, before = null, after = layer))

/** Applies a new edit: records it for undo and clears the redo stack. */
fun SessionSnapshot.applyEdit(change: LayerChange): SessionSnapshot = copy(
    layers = layers.apply(change),
    undoStack = (undoStack + change).takeLast(SessionSnapshot.HISTORY_LIMIT),
    redoStack = emptyList(),
    revision = revision + 1,
)

val SessionSnapshot.canUndo: Boolean get() = undoStack.isNotEmpty()
val SessionSnapshot.canRedo: Boolean get() = redoStack.isNotEmpty()

fun SessionSnapshot.undo(): SessionSnapshot {
    val change = undoStack.lastOrNull() ?: return this
    return copy(
        layers = layers.apply(change.inverted()),
        undoStack = undoStack.dropLast(1),
        redoStack = (redoStack + change).takeLast(SessionSnapshot.HISTORY_LIMIT),
        revision = revision + 1,
    )
}

fun SessionSnapshot.redo(): SessionSnapshot {
    val change = redoStack.lastOrNull() ?: return this
    return copy(
        layers = layers.apply(change),
        undoStack = (undoStack + change).takeLast(SessionSnapshot.HISTORY_LIMIT),
        redoStack = redoStack.dropLast(1),
        revision = revision + 1,
    )
}

private fun List<Layer>.apply(change: LayerChange): List<Layer> = toMutableList().apply {
    val before = change.before
    val after = change.after
    when {
        before == null && after != null -> add(change.index, after)
        before != null && after == null -> removeAt(change.index)
        after != null -> set(change.index, after)
    }
}
