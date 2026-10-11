package app.storymaker.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import app.storymaker.media.StickerLayer
import app.storymaker.media.StrokeLayer

/** Deterministic stand-in for a captured photo: a sky-to-ground gradient with a sun and hills. */
fun fixturePhoto(width: Int = 540, height: Int = 960): Bitmap {
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    val sky = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, height.toFloat(), Color.rgb(244, 199, 161), Color.rgb(95, 127, 106), Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), sky)
    canvas.drawCircle(width * 0.7f, height * 0.35f, width * 0.15f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 138, 91) })
    canvas.drawRect(0f, height * 0.7f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.rgb(38, 57, 46) })
    return bitmap
}

/** A wave stroke, a coral dot, and a rotated sticker, in normalized coordinates. */
val fixtureLayers = listOf(
    StrokeLayer(
        id = "wave",
        color = Color.WHITE,
        width = 0.03f,
        points = (0..20).flatMap { i -> listOf(0.1f + i * 0.04f, 0.2f + 0.04f * kotlin.math.sin(i / 3f)) },
    ),
    StrokeLayer(id = "dot", color = 0xFFFF5A36.toInt(), width = 0.05f, points = listOf(0.25f, 0.8f)),
    StickerLayer(id = "sticker", stickerId = "done", centerX = 0.6f, centerY = 0.5f, scale = 0.4f, rotation = -12f),
)
