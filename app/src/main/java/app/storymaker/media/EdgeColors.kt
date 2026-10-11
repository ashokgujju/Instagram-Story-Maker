package app.storymaker.media

import android.graphics.Bitmap
import kotlin.math.max

/** Colors for Instagram's letterbox bands above and below the background, as `#RRGGBB`. */
data class EdgeColors(val top: String, val bottom: String)

/**
 * Averages the top and bottom [rows] of an ARGB [pixels] image (row-major, [width] × [height]),
 * so letterboxing in Instagram matches the photo instead of Instagram's default.
 */
fun edgeColors(pixels: IntArray, width: Int, height: Int, rows: Int = edgeRows(height)): EdgeColors {
    val bandSize = width * rows.coerceIn(1, height)
    return EdgeColors(
        top = averageHex(pixels, 0, bandSize),
        bottom = averageHex(pixels, pixels.size - bandSize, bandSize),
    )
}

/** Edge colors of this bitmap, reading only the edge rows. */
fun Bitmap.edgeColors(): EdgeColors {
    val rows = edgeRows(height)
    val top = IntArray(width * rows).also { getPixels(it, 0, width, 0, 0, width, rows) }
    val bottom = IntArray(width * rows).also { getPixels(it, 0, width, 0, height - rows, width, rows) }
    return EdgeColors(top = averageHex(top, 0, top.size), bottom = averageHex(bottom, 0, bottom.size))
}

/** About 2% of the height, at least one row. */
private fun edgeRows(height: Int) = max(1, height / 50)

private fun averageHex(pixels: IntArray, from: Int, count: Int): String {
    var red = 0L
    var green = 0L
    var blue = 0L
    for (i in from until from + count) {
        val pixel = pixels[i]
        red += (pixel shr 16) and 0xFF
        green += (pixel shr 8) and 0xFF
        blue += pixel and 0xFF
    }
    return "#%02X%02X%02X".format(red / count, green / count, blue / count)
}
