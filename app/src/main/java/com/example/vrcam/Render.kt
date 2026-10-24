package com.example.vrcam

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Environment
import android.provider.MediaStore
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Rotates the sensor bitmap upright and mirrors it for the front camera. */
fun orient(src: Bitmap, degrees: Int, mirror: Boolean): Bitmap {
    if (degrees == 0 && !mirror) return src
    val m = Matrix()
    if (degrees != 0) m.postRotate(degrees.toFloat())
    if (mirror) m.postScale(-1f, 1f)
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

/** How the picture sits inside each eye. */
data class ViewParams(val offsetX: Float = 0f, val scale: Float = 1f, val flip180: Boolean = false)

/** Draws [bmp] into [canvas], once (normal) or twice side by side (VR), centre-cropped. */
fun renderFrame(
    canvas: Canvas,
    width: Float,
    height: Float,
    bmp: Bitmap,
    filter: CamFilter,
    vr: Boolean,
    params: ViewParams,
    paint: Paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = filter.colorFilter() }
) {
    canvas.drawColor(android.graphics.Color.BLACK)

    // Pixelate and blur work by drawing a tiny copy of the frame scaled back up.
    val small = when (filter.effect) {
        Effect.PIXELATE -> Bitmap.createScaledBitmap(bmp, max(1, bmp.width / 24), max(1, bmp.height / 24), true)
        Effect.BLUR -> Bitmap.createScaledBitmap(bmp, max(1, bmp.width / 16), max(1, bmp.height / 16), true)
        else -> null
    }
    val source = small ?: bmp
    val p = if (filter.effect == Effect.PIXELATE) Paint(paint).apply { isFilterBitmap = false } else paint

    canvas.save()
    if (params.flip180) canvas.rotate(180f, width / 2f, height / 2f)
    val eyes = if (vr) {
        val half = width / 2f
        listOf(RectF(0f, 0f, half, height), RectF(half, 0f, width, height))
    } else {
        listOf(RectF(0f, 0f, width, height))
    }
    for (eye in eyes) {
        canvas.save()
        canvas.clipRect(eye)
        canvas.translate(params.offsetX * eye.width(), 0f)
        canvas.scale(params.scale, params.scale, eye.centerX(), eye.centerY())
        drawEye(canvas, source, eye, p, filter.effect)
        canvas.restore()
    }
    canvas.restore()
}

/** The part of [bmp] that fills [dst] without stretching (centre crop). */
private fun cropRect(bmp: Bitmap, dst: RectF): Rect {
    val dstAspect = dst.width() / dst.height()
    val srcAspect = bmp.width.toFloat() / bmp.height
    return if (srcAspect > dstAspect) {
        val w = (bmp.height * dstAspect).toInt()
        val x = (bmp.width - w) / 2
        Rect(x, 0, x + w, bmp.height)
    } else {
        val h = (bmp.width / dstAspect).toInt()
        val y = (bmp.height - h) / 2
        Rect(0, y, bmp.width, y + h)
    }
}

private fun drawEye(canvas: Canvas, bmp: Bitmap, dst: RectF, paint: Paint, effect: Effect) {
    val src = cropRect(bmp, dst)
    val cx = dst.centerX()
    val cy = dst.centerY()
    when (effect) {
        Effect.MIRROR -> {
            val left = Rect(src.left, src.top, src.centerX(), src.bottom)
            val dstLeft = RectF(dst.left, dst.top, cx, dst.bottom)
            canvas.drawBitmap(bmp, left, dstLeft, paint)
            canvas.save()
            canvas.scale(-1f, 1f, cx, 0f)
            canvas.drawBitmap(bmp, left, dstLeft, paint)
            canvas.restore()
        }
        Effect.QUAD -> {
            val quad = Rect(src.left, src.top, src.centerX(), src.centerY())
            val dstQuad = RectF(dst.left, dst.top, cx, cy)
            canvas.drawBitmap(bmp, quad, dstQuad, paint)
            for ((sx, sy) in listOf(-1f to 1f, 1f to -1f, -1f to -1f)) {
                canvas.save()
                canvas.scale(sx, sy, cx, cy)
                canvas.drawBitmap(bmp, quad, dstQuad, paint)
                canvas.restore()
            }
        }
        Effect.TILES -> {
            for (r in 0..2) for (c in 0..2) {
                val tile = RectF(
                    dst.left + dst.width() * c / 3f, dst.top + dst.height() * r / 3f,
                    dst.left + dst.width() * (c + 1) / 3f, dst.top + dst.height() * (r + 1) / 3f
                )
                canvas.drawBitmap(bmp, cropRect(bmp, tile), tile, paint)
            }
        }
        Effect.ZOOM -> {
            val inner = Rect(
                src.left + src.width() / 4, src.top + src.height() / 4,
                src.right - src.width() / 4, src.bottom - src.height() / 4
            )
            canvas.drawBitmap(bmp, inner, dst, paint)
        }
        Effect.MIRROR_V -> {
            val top = Rect(src.left, src.top, src.right, src.centerY())
            val dstTop = RectF(dst.left, dst.top, dst.right, cy)
            canvas.drawBitmap(bmp, top, dstTop, paint)
            canvas.save()
            canvas.scale(1f, -1f, 0f, cy)
            canvas.drawBitmap(bmp, top, dstTop, paint)
            canvas.restore()
        }
        Effect.GHOST -> {
            canvas.drawBitmap(bmp, src, dst, paint)
            val faint = Paint(paint).apply { alpha = 110 }
            val dx = dst.width() * 0.03f
            canvas.save()
            canvas.clipRect(dst)
            canvas.drawBitmap(bmp, src, RectF(dst.left + dx, dst.top, dst.right + dx, dst.bottom), faint)
            canvas.restore()
        }
        Effect.GLITCH -> {
            val rnd = java.util.Random(System.currentTimeMillis() / 120)
            val strips = 14
            canvas.save()
            canvas.clipRect(dst)
            for (k in 0 until strips) {
                val s0 = src.top + k * src.height() / strips
                val s1 = src.top + (k + 1) * src.height() / strips
                val d0 = dst.top + dst.height() * k / strips
                val d1 = dst.top + dst.height() * (k + 1) / strips
                val shift = if (rnd.nextInt(4) == 0) (rnd.nextFloat() - 0.5f) * dst.width() * 0.12f else 0f
                canvas.drawBitmap(
                    bmp, Rect(src.left, s0, src.right, s1),
                    RectF(dst.left + shift, d0, dst.right + shift, d1), paint
                )
            }
            canvas.restore()
        }
        Effect.RGB_SPLIT -> {
            val dx = dst.width() * 0.012f
            val masks = arrayOf(
                floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f),
                floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f),
                floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
            )
            canvas.save()
            canvas.clipRect(dst)
            for (i in 0..2) {
                val channel = Paint(paint).apply {
                    colorFilter = ColorMatrixColorFilter(ColorMatrix(masks[i]))
                    blendMode = BlendMode.PLUS
                }
                val o = dx * (i - 1)
                canvas.drawBitmap(bmp, src, RectF(dst.left + o, dst.top, dst.right + o, dst.bottom), channel)
            }
            canvas.restore()
        }
        Effect.CRT -> {
            canvas.drawBitmap(bmp, src, dst, paint)
            val lines = Paint().apply { color = android.graphics.Color.argb(110, 0, 0, 0) }
            val step = max(3f, dst.height() / 120f)
            var y = dst.top
            while (y < dst.bottom) {
                canvas.drawRect(dst.left, y, dst.right, y + step * 0.5f, lines)
                y += step
            }
        }
        Effect.VIGNETTE -> {
            canvas.drawBitmap(bmp, src, dst, paint)
            val shader = RadialGradient(
                cx, cy, hypot(dst.width(), dst.height()) / 2f,
                intArrayOf(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.argb(235, 0, 0, 0)
                ),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawRect(dst, Paint().apply { this.shader = shader })
        }
        Effect.EDGES -> {
            val boost = ColorMatrixColorFilter(
                ColorMatrix(floatArrayOf(3f, 0f, 0f, 0f, 0f, 0f, 3f, 0f, 0f, 0f, 0f, 0f, 3f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
            )
            val base = Paint(paint).apply { colorFilter = boost }
            val diff = Paint(base).apply { blendMode = BlendMode.DIFFERENCE }
            val o = dst.width() * 0.004f
            canvas.save()
            canvas.clipRect(RectF(dst.left + o, dst.top + o, dst.right, dst.bottom))
            canvas.drawBitmap(bmp, src, dst, base)
            canvas.drawBitmap(bmp, src, RectF(dst.left + o, dst.top + o, dst.right + o, dst.bottom + o), diff)
            canvas.restore()
        }
        Effect.FISHEYE, Effect.PINCH, Effect.SWIRL, Effect.WAVE, Effect.RIPPLE, Effect.WIDE ->
            drawWarped(canvas, bmp, dst, paint, effect)
        else -> canvas.drawBitmap(bmp, src, dst, paint)
    }
}

private const val GRID = 24

/** Bends the frame with a mesh: fisheye bulge, pinch, or swirl around the centre. */
private fun drawWarped(canvas: Canvas, bmp: Bitmap, dst: RectF, paint: Paint, effect: Effect) {
    val scale = max(dst.width() / bmp.width, dst.height() / bmp.height) *
        (if (effect == Effect.WAVE || effect == Effect.RIPPLE) 1.08f else 1f)
    val phase = ((System.nanoTime() / 1_000_000L) % 100_000L) / 300f
    val w = bmp.width * scale
    val h = bmp.height * scale
    val cx = dst.centerX()
    val cy = dst.centerY()
    val halfW = dst.width() / 2f
    val halfH = dst.height() / 2f
    val verts = FloatArray((GRID + 1) * (GRID + 1) * 2)
    var i = 0
    for (row in 0..GRID) {
        for (col in 0..GRID) {
            val px = (col / GRID.toFloat() - 0.5f) * w
            val py = (row / GRID.toFloat() - 0.5f) * h
            val r = hypot(px / halfW, py / halfH)
            var x = px
            var y = py
            when (effect) {
                Effect.FISHEYE -> {
                    val rc = min(r, 1.45f)
                    val g = 1.4f - 0.2f * rc * rc
                    x = px * g; y = py * g
                }
                Effect.PINCH -> {
                    val rc = min(r, 1.6f)
                    val g = 0.6f + 0.4f * rc * rc
                    x = px * g; y = py * g
                }
                Effect.SWIRL -> {
                    val t = max(0f, 1f - r)
                    val a = 2.5f * t * t
                    x = px * cos(a) - py * sin(a)
                    y = px * sin(a) + py * cos(a)
                }
                Effect.RIPPLE -> {
                    val g = 1f + 0.05f * sin(r * 10f - phase * 2f)
                    x = px * g; y = py * g
                }
                Effect.WIDE -> {
                    val rc = min(r, 1.45f)
                    x = px * (1.4f - 0.2f * rc * rc)
                }
                Effect.WAVE -> {
                    x = px + 0.03f * dst.width() * sin(py / halfH * 4f + phase)
                }
                else -> {}
            }
            verts[i++] = cx + x
            verts[i++] = cy + y
        }
    }
    canvas.save()
    canvas.clipRect(dst)
    canvas.drawBitmapMesh(bmp, GRID, GRID, verts, 0, null, 0, paint)
    canvas.restore()
}

fun saveToGallery(context: Context, bmp: Bitmap): Boolean = try {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "VRCam_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VRCam")
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    if (uri == null) false else {
        resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) } ?: false
    }
} catch (e: Exception) {
    false
}
