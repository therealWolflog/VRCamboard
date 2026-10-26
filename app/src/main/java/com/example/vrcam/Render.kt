package com.example.vrcam

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Environment
import android.provider.MediaStore
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

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
        Effect.GLITCH -> glitchStrips(canvas, bmp, src, dst, paint, 0f)
        Effect.GLITCH_RGB -> {
            val dx = dst.width() * 0.015f
            for (i in 0..2) {
                val channel = Paint(paint).apply {
                    colorFilter = ColorMatrixColorFilter(ColorMatrix(CHANNEL_MASKS[i]))
                    blendMode = BlendMode.PLUS
                }
                glitchStrips(canvas, bmp, src, dst, channel, dx * (i - 1))
            }
        }
        Effect.HUE_CYCLE -> {
            val degrees = (System.currentTimeMillis() % 6000L) / 6000f * 360f
            val shifted = Paint(paint).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix(hueMatrix(degrees)))
            }
            canvas.drawBitmap(bmp, src, dst, shifted)
        }
        Effect.SPOTLIGHT, Effect.CIRCLE -> {
            canvas.drawBitmap(bmp, src, dst, paint)
            val radius = min(dst.width(), dst.height()) / 2f
            val stops = if (effect == Effect.SPOTLIGHT) floatArrayOf(0f, 0.35f, 0.8f, 1f)
            else floatArrayOf(0f, 0.9f, 0.92f, 1f)
            val shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(
                    android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.BLACK, android.graphics.Color.BLACK
                ),
                stops, Shader.TileMode.CLAMP
            )
            canvas.drawRect(dst, Paint().apply { this.shader = shader })
        }
        Effect.LETTERBOX -> {
            canvas.drawBitmap(bmp, src, dst, paint)
            val bar = dst.height() * 0.14f
            val black = Paint().apply { color = android.graphics.Color.BLACK }
            canvas.drawRect(dst.left, dst.top, dst.right, dst.top + bar, black)
            canvas.drawRect(dst.left, dst.bottom - bar, dst.right, dst.bottom, black)
        }
        Effect.RGB_SPLIT -> {
            val dx = dst.width() * 0.012f
            canvas.save()
            canvas.clipRect(dst)
            for (i in 0..2) {
                val channel = Paint(paint).apply {
                    colorFilter = ColorMatrixColorFilter(ColorMatrix(CHANNEL_MASKS[i]))
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
        Effect.MIRROR_R -> {
            val right = Rect(src.centerX(), src.top, src.right, src.bottom)
            val dstRight = RectF(cx, dst.top, dst.right, dst.bottom)
            canvas.drawBitmap(bmp, right, dstRight, paint)
            canvas.save()
            canvas.scale(-1f, 1f, cx, 0f)
            canvas.drawBitmap(bmp, right, dstRight, paint)
            canvas.restore()
        }
        Effect.MIRROR_D -> {
            val bottom = Rect(src.left, src.centerY(), src.right, src.bottom)
            val dstBottom = RectF(dst.left, cy, dst.right, dst.bottom)
            canvas.drawBitmap(bmp, bottom, dstBottom, paint)
            canvas.save()
            canvas.scale(1f, -1f, 0f, cy)
            canvas.drawBitmap(bmp, bottom, dstBottom, paint)
            canvas.restore()
        }
        Effect.KALEIDO6 -> drawKaleido(canvas, bmp, src, dst, paint, 6, true)
        Effect.KALEIDO8 -> drawKaleido(canvas, bmp, src, dst, paint, 8, false)
        Effect.TILES2 -> drawTiles(canvas, bmp, dst, paint, 2)
        Effect.TILES4 -> drawTiles(canvas, bmp, dst, paint, 4)
        Effect.ZOOM3, Effect.ZOOM4 -> {
            val inset = if (effect == Effect.ZOOM3) 1f / 3f else 0.375f
            val inner = Rect(
                src.left + (src.width() * inset).toInt(), src.top + (src.height() * inset).toInt(),
                src.right - (src.width() * inset).toInt(), src.bottom - (src.height() * inset).toInt()
            )
            canvas.drawBitmap(bmp, inner, dst, paint)
        }
        Effect.PULSE -> {
            val t = (System.currentTimeMillis() % 3000L) / 3000f * 6.2831855f
            val sc = 1.15f + 0.12f * sin(t)
            canvas.save()
            canvas.clipRect(dst)
            canvas.scale(sc, sc, cx, cy)
            canvas.drawBitmap(bmp, src, dst, paint)
            canvas.restore()
        }
        Effect.SPIN, Effect.DUTCH -> {
            val degrees = if (effect == Effect.SPIN) (System.currentTimeMillis() % 8000L) / 8000f * 360f else 15f
            val sc = if (effect == Effect.SPIN) 1.9f else 1.45f
            canvas.save()
            canvas.clipRect(dst)
            canvas.rotate(degrees, cx, cy)
            canvas.scale(sc, sc, cx, cy)
            canvas.drawBitmap(bmp, src, dst, paint)
            canvas.restore()
        }
        Effect.SHAKE -> {
            val rnd = java.util.Random(System.currentTimeMillis() / 40)
            val dx = (rnd.nextFloat() - 0.5f) * dst.width() * 0.04f
            val dy = (rnd.nextFloat() - 0.5f) * dst.height() * 0.04f
            canvas.save()
            canvas.clipRect(dst)
            canvas.translate(dx, dy)
            canvas.scale(1.1f, 1.1f, cx, cy)
            canvas.drawBitmap(bmp, src, dst, paint)
            canvas.restore()
        }
        Effect.DOUBLE -> {
            val half = Paint(paint).apply { alpha = 128 }
            val dx = dst.width() * 0.025f
            canvas.save()
            canvas.clipRect(dst)
            canvas.drawBitmap(bmp, src, RectF(dst.left - dx, dst.top, dst.right - dx, dst.bottom), half)
            canvas.drawBitmap(bmp, src, RectF(dst.left + dx, dst.top, dst.right + dx, dst.bottom), half)
            canvas.restore()
        }
        Effect.INFINITY -> {
            canvas.save()
            canvas.clipRect(dst)
            val spin = (System.currentTimeMillis() % 12000L) / 12000f * 360f
            for (i in 0..7) {
                canvas.save()
                canvas.rotate(i * 7f + spin * 0.2f, cx, cy)
                val sc = 0.78f.pow(i)
                canvas.scale(sc, sc, cx, cy)
                canvas.drawBitmap(bmp, src, dst, paint)
                canvas.restore()
            }
            canvas.restore()
        }
        Effect.FLIP_BLEND -> {
            canvas.drawBitmap(bmp, src, dst, paint)
            val faint = Paint(paint).apply { alpha = 110 }
            canvas.save()
            canvas.rotate(180f, cx, cy)
            canvas.drawBitmap(bmp, src, dst, faint)
            canvas.restore()
        }
        Effect.WARHOL -> {
            val hues = floatArrayOf(0f, 90f, 200f, 290f)
            for (k in 0..3) {
                val tile = RectF(
                    dst.left + dst.width() * (k % 2) / 2f, dst.top + dst.height() * (k / 2) / 2f,
                    dst.left + dst.width() * (k % 2 + 1) / 2f, dst.top + dst.height() * (k / 2 + 1) / 2f
                )
                val tinted = Paint(paint).apply {
                    colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                        setSaturation(1.8f)
                        postConcat(ColorMatrix(hueMatrix(hues[k])))
                    })
                }
                canvas.drawBitmap(bmp, cropRect(bmp, tile), tile, tinted)
            }
        }
        Effect.HOLOGRAM -> {
            val cyan = ColorMatrixColorFilter(
                ColorMatrix(floatArrayOf(
                    0f, 0f, 0f, 0f, 0f,
                    0.3f, 0.59f, 0.11f, 0f, 20f,
                    0.35f, 0.7f, 0.13f, 0f, 50f,
                    0f, 0f, 0f, 1f, 0f
                ))
            )
            val main = Paint(paint).apply { colorFilter = cyan }
            val ghost = Paint(main).apply { alpha = 90 }
            val dx = dst.width() * 0.012f
            canvas.save()
            canvas.clipRect(dst)
            canvas.drawBitmap(bmp, src, dst, main)
            canvas.drawBitmap(bmp, src, RectF(dst.left + dx, dst.top, dst.right + dx, dst.bottom), ghost)
            canvas.restore()
            val lines = Paint().apply { color = android.graphics.Color.argb(90, 0, 0, 0) }
            val step = max(3f, dst.height() / 100f)
            var y = dst.top
            while (y < dst.bottom) {
                canvas.drawRect(dst.left, y, dst.right, y + step * 0.5f, lines)
                y += step
            }
        }
        Effect.VHS -> {
            val soft = ColorMatrix().apply { setSaturation(0.75f) }
            val dx = dst.width() * 0.006f
            canvas.save()
            canvas.clipRect(dst)
            for (i in 0..2) {
                val channel = Paint(paint).apply {
                    colorFilter = ColorMatrixColorFilter(ColorMatrix(CHANNEL_MASKS[i]).apply { postConcat(soft) })
                    blendMode = BlendMode.PLUS
                }
                val o = dx * (i - 1)
                canvas.drawBitmap(bmp, src, RectF(dst.left + o, dst.top, dst.right + o, dst.bottom), channel)
            }
            val lines = Paint().apply { color = android.graphics.Color.argb(60, 0, 0, 0) }
            val step = max(3f, dst.height() / 90f)
            var y = dst.top
            while (y < dst.bottom) {
                canvas.drawRect(dst.left, y, dst.right, y + step * 0.5f, lines)
                y += step
            }
            // tracking band sliding down the picture
            val band = dst.top + dst.height() * ((System.currentTimeMillis() % 3000L) / 3000f)
            canvas.drawRect(
                dst.left, band, dst.right, band + dst.height() * 0.05f,
                Paint().apply { color = android.graphics.Color.argb(50, 255, 255, 255) }
            )
            canvas.restore()
        }
        Effect.SECURITY -> {
            val grey = Paint(paint).apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix(floatArrayOf(
                        0.2f, 0.4f, 0.08f, 0f, 0f,
                        0.3f, 0.6f, 0.11f, 0f, 10f,
                        0.2f, 0.4f, 0.08f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                )
            }
            canvas.drawBitmap(bmp, src, dst, grey)
            val lines = Paint().apply { color = android.graphics.Color.argb(70, 0, 0, 0) }
            val step = max(3f, dst.height() / 110f)
            var y = dst.top
            while (y < dst.bottom) {
                canvas.drawRect(dst.left, y, dst.right, y + step * 0.5f, lines)
                y += step
            }
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = max(12f, dst.height() * 0.05f)
                typeface = android.graphics.Typeface.MONOSPACE
            }
            val margin = dst.height() * 0.04f
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date())
            canvas.drawText("CAM 01", dst.left + margin * 2.2f, dst.top + margin + text.textSize, text)
            canvas.drawText(stamp, dst.left + margin, dst.bottom - margin, text)
            if ((System.currentTimeMillis() / 600L) % 2L == 0L) {
                canvas.drawCircle(
                    dst.left + margin * 1.1f, dst.top + margin + text.textSize * 0.65f, text.textSize * 0.3f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.RED }
                )
            }
        }
        Effect.TINY_PLANET -> drawPolar(canvas, bmp, dst, paint)
        Effect.FISHEYE, Effect.PINCH, Effect.SWIRL, Effect.WAVE, Effect.RIPPLE, Effect.WIDE,
        Effect.DRUNK, Effect.MELT, Effect.FISHEYE2, Effect.PINCH2, Effect.WHIRL, Effect.WAVE_V, Effect.TALL ->
            drawWarped(canvas, bmp, dst, paint, effect)
        else -> canvas.drawBitmap(bmp, src, dst, paint)
    }
}

private val CHANNEL_MASKS = arrayOf(
    floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f),
    floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f),
    floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
)

private fun glitchStrips(canvas: Canvas, bmp: Bitmap, src: Rect, dst: RectF, paint: Paint, extra: Float) {
    val rnd = java.util.Random(System.currentTimeMillis() / 120)
    val strips = 14
    canvas.save()
    canvas.clipRect(dst)
    for (k in 0 until strips) {
        val s0 = src.top + k * src.height() / strips
        val s1 = src.top + (k + 1) * src.height() / strips
        val d0 = dst.top + dst.height() * k / strips
        val d1 = dst.top + dst.height() * (k + 1) / strips
        val shift = (if (rnd.nextInt(4) == 0) (rnd.nextFloat() - 0.5f) * dst.width() * 0.12f else 0f) + extra
        canvas.drawBitmap(
            bmp, Rect(src.left, s0, src.right, s1),
            RectF(dst.left + shift, d0, dst.right + shift, d1), paint
        )
    }
    canvas.restore()
}

private fun drawTiles(canvas: Canvas, bmp: Bitmap, dst: RectF, paint: Paint, n: Int) {
    for (row in 0 until n) {
        for (col in 0 until n) {
            val tile = RectF(
                dst.left + dst.width() * col / n, dst.top + dst.height() * row / n,
                dst.left + dst.width() * (col + 1) / n, dst.top + dst.height() * (row + 1) / n
            )
            canvas.drawBitmap(bmp, cropRect(bmp, tile), tile, paint)
        }
    }
}

/** Kaleidoscope: [n] mirrored wedges around the centre, optionally slowly spinning. */
private fun drawKaleido(canvas: Canvas, bmp: Bitmap, src: Rect, dst: RectF, paint: Paint, n: Int, spin: Boolean) {
    val cx = dst.centerX()
    val cy = dst.centerY()
    val angle = 360f / n
    val rotation = if (spin) (System.currentTimeMillis() % 20000L) / 20000f * 360f else 0f
    val radius = hypot(dst.width(), dst.height())
    val rad = Math.toRadians(angle.toDouble())
    val wedge = Path().apply {
        moveTo(0f, 0f)
        lineTo(radius, 0f)
        lineTo((radius * Math.cos(rad)).toFloat(), (radius * Math.sin(rad)).toFloat())
        close()
    }
    canvas.save()
    canvas.clipRect(dst)
    canvas.translate(cx, cy)
    for (k in 0 until n) {
        canvas.save()
        canvas.rotate(k * angle)
        canvas.clipPath(wedge)
        if (k % 2 == 1) {
            canvas.rotate(angle)
            canvas.scale(1f, -1f)
        }
        canvas.rotate(-rotation)
        canvas.scale(1.7f, 1.7f)
        canvas.translate(-cx, -cy)
        canvas.drawBitmap(bmp, src, dst, paint)
        canvas.restore()
    }
    canvas.restore()
}

/** Tiny planet: the picture wrapped around a circle. */
private fun drawPolar(canvas: Canvas, bmp: Bitmap, dst: RectF, paint: Paint) {
    val g = 64
    val cx = dst.centerX()
    val cy = dst.centerY()
    val radius = hypot(dst.width(), dst.height()) / 2f
    val verts = FloatArray((g + 1) * (g + 1) * 2)
    var i = 0
    for (row in 0..g) {
        val r = (1f - row / g.toFloat()) * radius
        for (col in 0..g) {
            val theta = col / g.toFloat() * 6.2831855f - 1.5707964f
            verts[i++] = cx + r * cos(theta)
            verts[i++] = cy + r * sin(theta)
        }
    }
    canvas.save()
    canvas.clipRect(dst)
    canvas.drawBitmapMesh(bmp, g, g, verts, 0, null, 0, paint)
    canvas.restore()
}

private const val GRID = 24

/** Bends the frame with a mesh: fisheye bulge, pinch, or swirl around the centre. */
private fun drawWarped(canvas: Canvas, bmp: Bitmap, dst: RectF, paint: Paint, effect: Effect) {
    val scale = max(dst.width() / bmp.width, dst.height() / bmp.height) *
        (when (effect) {
            Effect.WAVE, Effect.RIPPLE -> 1.08f
            Effect.DRUNK, Effect.MELT, Effect.WAVE_V -> 1.15f
            else -> 1f
        })
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
                Effect.FISHEYE2 -> {
                    val rc = min(r, 1.2f)
                    val g = 1.8f - 0.4f * rc * rc
                    x = px * g; y = py * g
                }
                Effect.PINCH2 -> {
                    val rc = min(r, 1.5f)
                    val g = 0.35f + 0.65f * rc * rc
                    x = px * g; y = py * g
                }
                Effect.WHIRL -> {
                    val t = max(0f, 1f - r)
                    val a = 5f * t * t * sin(phase * 0.35f)
                    x = px * cos(a) - py * sin(a)
                    y = px * sin(a) + py * cos(a)
                }
                Effect.WAVE_V -> {
                    y = py + 0.04f * dst.height() * sin(px / halfW * 4f + phase)
                }
                Effect.TALL -> {
                    val rc = min(r, 1.45f)
                    y = py * (1.4f - 0.2f * rc * rc)
                }
                Effect.DRUNK -> {
                    x = px + 0.04f * dst.width() * sin(py / halfH * 2.5f + phase * 0.6f)
                    y = py + 0.04f * dst.height() * sin(px / halfW * 2.5f + phase * 0.8f)
                }
                Effect.MELT -> {
                    val depth = (py / halfH + 1f) / 2f
                    y = py + 0.08f * dst.height() * depth * (0.5f + 0.5f * sin(px / halfW * 5f + phase * 0.5f))
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

/** Edge detection (Sobel): bright outlines on black, like the Edges filter in the reference video. */
fun sobelEdges(src: Bitmap): Bitmap {
    val w = 640
    val h = max(2, src.height * w / src.width)
    val small = Bitmap.createScaledBitmap(src, w, h, true)
    val px = IntArray(w * h)
    small.getPixels(px, 0, w, 0, 0, w, h)
    val g = IntArray(w * h) {
        val c = px[it]
        (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
    }
    val out = IntArray(w * h) { 0xFF000000.toInt() }
    for (y in 1 until h - 1) {
        val row = y * w
        for (x in 1 until w - 1) {
            val i = row + x
            val gx = -g[i - w - 1] + g[i - w + 1] - 2 * g[i - 1] + 2 * g[i + 1] - g[i + w - 1] + g[i + w + 1]
            val gy = -g[i - w - 1] - 2 * g[i - w] - g[i - w + 1] + g[i + w - 1] + 2 * g[i + w] + g[i + w + 1]
            val m = min(255, (abs(gx) + abs(gy)) * 3 / 2)
            out[i] = 0xFF000000.toInt() or (m shl 16) or (m shl 8) or m
        }
    }
    if (small !== src) small.recycle()
    return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
}

// ---- Cardboard viewer correction ----

private var eyeBuffer: Bitmap? = null
private const val CB_GRID = 32

/**
 * Draws the picture for a Cardboard viewer: each eye's image is centred on its lens
 * (from the viewer's lens spacing and the phone's real screen size) and pre-distorted
 * so the lens' magnification straightens it out again.
 */
fun renderCardboard(
    canvas: Canvas,
    width: Float,
    height: Float,
    bmp: Bitmap,
    filter: CamFilter,
    cb: CardboardParams,
    xdpi: Float,
    ydpi: Float,
    flip180: Boolean
) {
    canvas.drawColor(android.graphics.Color.BLACK)

    // Visible field of view through the lens, as tan(angle).
    val tanW = tan(Math.toRadians(((cb.fovLeft + cb.fovRight) / 2f).toDouble())).toFloat()
    val tanH = tan(Math.toRadians(((cb.fovBottom + cb.fovTop) / 2f).toDouble())).toFloat()

    // One eye's picture (with filter/effect) in a small offscreen buffer shaped like that field of view.
    val ch = 640
    val cw = (ch * tanW / tanH).toInt().coerceIn(320, 1280)
    var buf = eyeBuffer
    if (buf == null || buf.width != cw || buf.height != ch) {
        buf = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        eyeBuffer = buf
    }
    renderFrame(Canvas(buf), cw.toFloat(), ch.toFloat(), bmp, filter, false, ViewParams())

    val pxPerMeterX = xdpi / 0.0254f
    val pxPerMeterY = ydpi / 0.0254f
    val lensY = when (cb.verticalAlignment) {
        2 -> cb.trayToLens * pxPerMeterY
        1 -> height / 2f
        else -> height - cb.trayToLens * pxPerMeterY
    }
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    canvas.save()
    if (flip180) canvas.rotate(180f, width / 2f, height / 2f)
    for (side in intArrayOf(-1, 1)) {
        val lensX = width / 2f + side * cb.interLens / 2f * pxPerMeterX
        val clip = if (side < 0) RectF(0f, 0f, width / 2f, height) else RectF(width / 2f, 0f, width, height)

        val verts = FloatArray((CB_GRID + 1) * (CB_GRID + 1) * 2)
        var i = 0
        for (row in 0..CB_GRID) {
            for (col in 0..CB_GRID) {
                // Where this part of the picture should appear to be, as tan(angle) from the lens axis...
                val ax = (col / CB_GRID.toFloat() - 0.5f) * 2f * tanW
                val ay = (row / CB_GRID.toFloat() - 0.5f) * 2f * tanH
                val ar = hypot(ax, ay)
                // ...and where on the screen it has to be drawn so the lens puts it there.
                var s = ar
                repeat(14) {
                    val next = ar / (1f + cb.k1 * s * s + cb.k2 * s * s * s * s)
                    s = 0.5f * s + 0.5f * next
                }
                val k = if (ar > 1e-6f) s / ar else 1f
                verts[i++] = lensX + ax * k * cb.screenToLens * pxPerMeterX
                verts[i++] = lensY + ay * k * cb.screenToLens * pxPerMeterY
            }
        }
        canvas.save()
        canvas.clipRect(clip)
        canvas.drawBitmapMesh(buf, CB_GRID, CB_GRID, verts, 0, null, 0, paint)
        canvas.restore()
    }
    canvas.restore()
}
