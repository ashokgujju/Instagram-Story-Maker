package app.storymaker.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OutOfMemoryFallbackTest {

    @Test
    fun success_rendersAtFullSize() {
        val sizes = mutableListOf<Int>()
        val result = withOutOfMemoryFallback(4096) { sizes += it; "ok" }
        assertEquals("ok", result)
        assertEquals(listOf(4096), sizes)
    }

    @Test
    fun outOfMemory_retriesOnceAtHalfSize() {
        val sizes = mutableListOf<Int>()
        val result = withOutOfMemoryFallback(4096) { size ->
            sizes += size
            if (size == 4096) throw OutOfMemoryError("too big")
            "half"
        }
        assertEquals("half", result)
        assertEquals(listOf(4096, 2048), sizes)
    }

    @Test
    fun outOfMemoryTwice_becomesExportException() {
        assertThrows(ExportException::class.java) {
            withOutOfMemoryFallback(4096) { throw OutOfMemoryError("still too big") }
        }
    }
}
