package app.storymaker.media

import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeColorsTest {

    private fun image(width: Int, height: Int, color: (x: Int, y: Int) -> Int) =
        IntArray(width * height) { i -> color(i % width, i / width) }

    @Test
    fun solidImage_hasSameTopAndBottom() {
        val pixels = image(10, 100) { _, _ -> 0xFF3B82F6.toInt() }
        assertEquals(EdgeColors("#3B82F6", "#3B82F6"), edgeColors(pixels, 10, 100))
    }

    @Test
    fun splitImage_usesTopAndBottomRows() {
        val pixels = image(10, 100) { _, y -> if (y < 50) 0xFFFF0000.toInt() else 0xFF00FF00.toInt() }
        assertEquals(EdgeColors("#FF0000", "#00FF00"), edgeColors(pixels, 10, 100))
    }

    @Test
    fun colors_areAveragedAcrossTheBand() {
        // Alternating black and white columns average to mid gray.
        val pixels = image(4, 100) { x, _ -> if (x % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        assertEquals(EdgeColors("#7F7F7F", "#7F7F7F"), edgeColors(pixels, 4, 100, rows = 2))
    }

    @Test
    fun onlyTheEdgeRowsCount() {
        // A red middle doesn't affect the edges.
        val pixels = image(2, 100) { _, y -> if (y in 10..90) 0xFFFF0000.toInt() else 0xFF000000.toInt() }
        assertEquals(EdgeColors("#000000", "#000000"), edgeColors(pixels, 2, 100, rows = 2))
    }
}
