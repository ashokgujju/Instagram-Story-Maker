package app.storymaker.editor

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import app.storymaker.R

data class BrushColor(val color: Color, @StringRes val name: Int)

/** Brush swatches from the design, in display order; white is the default. */
val BrushPalette = listOf(
    BrushColor(Color(0xFFFFFFFF), R.string.color_white),
    BrushColor(Color(0xFF111111), R.string.color_black),
    BrushColor(Color(0xFFFF5A36), R.string.color_coral),
    BrushColor(Color(0xFFFFD23F), R.string.color_yellow),
    BrushColor(Color(0xFF4ADE80), R.string.color_green),
    BrushColor(Color(0xFF3B82F6), R.string.color_blue),
    BrushColor(Color(0xFF8C7BFF), R.string.color_violet),
)

const val MIN_BRUSH_SIZE_DP = 2f
const val MAX_BRUSH_SIZE_DP = 24f
