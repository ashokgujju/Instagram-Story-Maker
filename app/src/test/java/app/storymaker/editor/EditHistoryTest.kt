package app.storymaker.editor

import app.storymaker.media.LayerChange
import app.storymaker.media.SessionSnapshot
import app.storymaker.media.StickerLayer
import app.storymaker.media.StrokeLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {

    private val empty = SessionSnapshot(sessionId = "s", createdAt = 0, photoWidth = 100, photoHeight = 200)

    private fun stroke(n: Int) = StrokeLayer(id = "stroke$n", color = n, width = 0.01f, points = listOf(0f, 0f, 1f, 1f))

    @Test
    fun addLayer_appendsAndRecordsHistory() {
        val result = empty.addLayer(stroke(1)).addLayer(stroke(2))

        assertEquals(listOf(stroke(1), stroke(2)), result.layers)
        assertEquals(2, result.undoStack.size)
        assertTrue(result.canUndo)
        assertFalse(result.canRedo)
        assertEquals(2L, result.revision)
    }

    @Test
    fun undoAndRedo_roundTrip() {
        val drawn = empty.addLayer(stroke(1)).addLayer(stroke(2))

        val undone = drawn.undo()
        assertEquals(listOf(stroke(1)), undone.layers)
        assertTrue(undone.canRedo)

        val redone = undone.redo()
        assertEquals(drawn.layers, redone.layers)
        assertFalse(redone.canRedo)
        assertEquals(4L, redone.revision)
    }

    @Test
    fun newEdit_clearsRedoStack() {
        val result = empty.addLayer(stroke(1)).undo().addLayer(stroke(2))

        assertEquals(listOf(stroke(2)), result.layers)
        assertFalse(result.canRedo)
    }

    @Test
    fun history_isCappedDroppingTheOldest() {
        var snapshot = empty
        repeat(SessionSnapshot.HISTORY_LIMIT + 5) { snapshot = snapshot.addLayer(stroke(it)) }

        assertEquals(SessionSnapshot.HISTORY_LIMIT, snapshot.undoStack.size)
        assertEquals(stroke(5), snapshot.undoStack.first().after)

        repeat(SessionSnapshot.HISTORY_LIMIT) { snapshot = snapshot.undo() }
        assertFalse(snapshot.canUndo)
        // The five oldest strokes can no longer be undone.
        assertEquals((0 until 5).map { stroke(it) }, snapshot.layers)
    }

    @Test
    fun undoAndRedo_onEmptyStacks_doNothing() {
        assertSame(empty, empty.undo())
        assertSame(empty, empty.redo())
    }

    @Test
    fun updateAndRemoveChanges_invertCorrectly() {
        val sticker = StickerLayer("st", "done", 0.5f, 0.5f, 0.3f, 0f)
        val moved = sticker.copy(centerX = 0.7f, rotation = 15f)
        val placed = empty.addLayer(stroke(1)).addLayer(sticker)

        val afterMove = placed.applyEdit(LayerChange(index = 1, before = sticker, after = moved))
        assertEquals(listOf(stroke(1), moved), afterMove.layers)
        assertEquals(listOf(stroke(1), sticker), afterMove.undo().layers)

        val afterRemove = placed.applyEdit(LayerChange(index = 0, before = stroke(1), after = null))
        assertEquals(listOf(sticker), afterRemove.layers)
        assertEquals(placed.layers, afterRemove.undo().layers)
        assertEquals(listOf(sticker), afterRemove.undo().redo().layers)
    }

    @Test
    fun stickerEdits_undoAndRedoStepByStep() {
        val placed = StickerLayer("st", "done", 0.5f, 0.5f, 0.35f, 0f)
        val moved = placed.copy(centerX = 0.3f, centerY = 0.2f)
        val resized = moved.copy(scale = 0.7f, rotation = 15f)

        val states = mutableListOf(empty.addLayer(stroke(1)))
        states += states.last().addLayer(placed)
        states += states.last().applyEdit(LayerChange(1, placed, moved))
        states += states.last().applyEdit(LayerChange(1, moved, resized))
        states += states.last().applyEdit(LayerChange(1, resized, null))
        assertEquals(listOf(stroke(1)), states.last().layers)

        var snapshot = states.last()
        for (expected in states.dropLast(1).reversed()) {
            snapshot = snapshot.undo()
            assertEquals(expected.layers, snapshot.layers)
        }
        for (expected in states.drop(1)) {
            snapshot = snapshot.redo()
            assertEquals(expected.layers, snapshot.layers)
        }
    }
}
