package com.example.vrcam

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Effects that have to look at real pixels (edges, relief, cartoon, trails, halftone...).
 * Called once per camera frame on the camera thread, on a reduced-size copy for speed.
 */
object PixelFx {
    private const val W = 640

    // State kept between frames (trails, motion, stutter)
    private var acc: Bitmap? = null
    private var prevLum: IntArray? = null
    private var held: Bitmap? = null
    private var heldAt = 0L

    private val thermalLut = buildLut(
        listOf(
            0f to 0x000014, 0.2f to 0x280078, 0.4f to 0xB40078,
            0.6f to 0xFF5000, 0.8f to 0xFFC800, 1f to 0xFFFFE6
        )
    )
    private val rainbowLut = IntArray(256) { Color.HSVToColor(floatArrayOf(it / 255f * 270f, 1f, 1f)) }
    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
    private val asciiChars = " .:-=+*#%@".map { it.toString() }

    fun process(src: Bitmap, effect: Effect): Bitmap {
        if (effect != Effect.TRAIL && effect != Effect.AFTERIMAGE &&
            effect != Effect.LONG_TRAIL && effect != Effect.LIGHT_PAINT
        ) acc = null
        if (effect != Effect.MOTION && effect != Effect.MOTION_COLOR) prevLum = null
        if (effect != Effect.STUTTER) held = null
        return when (effect) {
            Effect.EDGES -> sobelEdges(src)
            Effect.EMBOSS -> emboss(src)
            Effect.CELL -> cartoon(src, comic = false)
            Effect.COMIC -> cartoon(src, comic = true)
            Effect.TRAIL -> trail(src, 45)
            Effect.LONG_TRAIL -> trail(src, 16)
            Effect.AFTERIMAGE -> afterImage(src)
            Effect.LIGHT_PAINT -> lightPaint(src)
            Effect.STUTTER -> stutter(src)
            Effect.SKETCH -> sketch(src)
            Effect.NEON_EDGES -> neonEdges(src)
            Effect.THERMAL -> lutMap(src, thermalLut)
            Effect.RAINBOW -> lutMap(src, rainbowLut)
            Effect.NIGHT_SCOPE -> nightScope(src)
            Effect.MOTION -> motion(src, colour = false)
            Effect.MOTION_COLOR -> motion(src, colour = true)
            Effect.HALFTONE -> halftone(src, colour = true)
            Effect.HALFTONE_BW -> halftone(src, colour = false)
            Effect.PIXEL8 -> pixelArt(src, gameboy = false)
            Effect.GAMEBOY_FX -> pixelArt(src, gameboy = true)
            Effect.ASCII -> ascii(src)
            Effect.BLOOM -> glow(src, bloom = true)
            Effect.SOFT_GLOW -> glow(src, bloom = false)
            Effect.TILT_SHIFT -> tiltShift(src)
            Effect.SHARPEN -> sharpen(src)
            Effect.POSTERIZE -> posterize(src)
            Effect.DITHER -> dither(src)
            Effect.PAINTING -> painting(src)
            else -> src
        }
    }

    // ---- helpers ----

    private fun small(src: Bitmap, width: Int = W): Bitmap =
        Bitmap.createScaledBitmap(src, width, max(2, src.height * width / src.width), true)

    private fun clamp(v: Int) = if (v < 0) 0 else if (v > 255) 255 else v

    private fun pack(r: Int, g: Int, b: Int) = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b

    private fun pixels(b: Bitmap): IntArray {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return px
    }

    private fun make(px: IntArray, w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)

    private fun luminance(px: IntArray) = IntArray(px.size) {
        val c = px[it]
        (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
    }

    private fun sobel(lum: IntArray, w: Int, i: Int): Int {
        val gx = -lum[i - w - 1] + lum[i - w + 1] - 2 * lum[i - 1] + 2 * lum[i + 1] -
            lum[i + w - 1] + lum[i + w + 1]
        val gy = -lum[i - w - 1] - 2 * lum[i - w] - lum[i - w + 1] +
            lum[i + w - 1] + 2 * lum[i + w] + lum[i + w + 1]
        return abs(gx) + abs(gy)
    }

    private fun buildLut(stops: List<Pair<Float, Int>>): IntArray = IntArray(256) { i ->
        val t = i / 255f
        var k = 0
        while (k < stops.size - 2 && t > stops[k + 1].first) k++
        val (t0, c0) = stops[k]
        val (t1, c1) = stops[k + 1]
        val f = ((t - t0) / (t1 - t0)).coerceIn(0f, 1f)
        val r = ((c0 shr 16) and 255) + ((((c1 shr 16) and 255) - ((c0 shr 16) and 255)) * f).toInt()
        val g = ((c0 shr 8) and 255) + ((((c1 shr 8) and 255) - ((c0 shr 8) and 255)) * f).toInt()
        val b = (c0 and 255) + (((c1 and 255) - (c0 and 255)) * f).toInt()
        pack(r, g, b)
    }

    /** A blurred copy of [s] (shrink then enlarge). */
    private fun blurred(s: Bitmap, divisor: Int): Bitmap {
        val tiny = Bitmap.createScaledBitmap(s, max(2, s.width / divisor), max(2, s.height / divisor), true)
        return Bitmap.createScaledBitmap(tiny, s.width, s.height, true)
    }

    // ---- effects ----

    /** Embossed: the picture looks pressed into clay, lit from the top left. */
    private fun emboss(src: Bitmap): Bitmap {
        val s = small(src)
        val w = s.width
        val h = s.height
        val px = pixels(s)
        val lum = luminance(px)
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val y0 = max(y - 1, 0)
            val y1 = min(y + 1, h - 1)
            for (x in 0 until w) {
                val e = (lum[y0 * w + max(x - 1, 0)] - lum[y1 * w + min(x + 1, w - 1)]) * 3
                val c = px[y * w + x]
                out[y * w + x] = pack(
                    clamp((((c shr 16) and 255) shr 1) + 64 + e),
                    clamp((((c shr 8) and 255) shr 1) + 64 + e),
                    clamp(((c and 255) shr 1) + 64 + e)
                )
            }
        }
        return make(out, w, h)
    }

    /** Cell shading (flat colours + ink outlines); comic adds heavier ink and shadow dots. */
    private fun cartoon(src: Bitmap, comic: Boolean): Bitmap {
        val s = small(src)
        val w = s.width
        val h = s.height
        val px = pixels(s)
        val lum = luminance(px)
        val levels = if (comic) 3 else 5
        val step = 255 / (levels - 1)
        val threshold = if (comic) 90 else 140
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val c = px[i]
                val l = lum[i]
                var r = clamp(l + (((c shr 16) and 255) - l) * 14 / 10)
                var g = clamp(l + (((c shr 8) and 255) - l) * 14 / 10)
                var b = clamp(l + ((c and 255) - l) * 14 / 10)
                r = (r * levels shr 8).coerceAtMost(levels - 1) * step
                g = (g * levels shr 8).coerceAtMost(levels - 1) * step
                b = (b * levels shr 8).coerceAtMost(levels - 1) * step
                if (comic && l < 110 && (x + y) % 5 == 0) {
                    r /= 3; g /= 3; b /= 3
                }
                if (x in 1 until w - 1 && y in 1 until h - 1 && sobel(lum, w, i) > threshold) {
                    r = 0; g = 0; b = 0
                }
                out[i] = pack(r, g, b)
            }
        }
        return make(out, w, h)
    }

    /** Motion trail: every frame is blended into a running average, so moving things smear. */
    private fun trail(src: Bitmap, alpha: Int): Bitmap {
        val s = small(src)
        val a = acc
        if (a == null || a.width != s.width || a.height != s.height) {
            acc = s.copy(Bitmap.Config.ARGB_8888, true)
            return s
        }
        Canvas(a).drawBitmap(s, 0f, 0f, Paint().apply { this.alpha = alpha })
        return a.copy(Bitmap.Config.ARGB_8888, false)
    }

    /** After image: where the picture changed, the old picture lingers in negative colours. */
    private fun afterImage(src: Bitmap): Bitmap {
        val s = small(src)
        val a = acc
        if (a == null || a.width != s.width || a.height != s.height) {
            acc = s.copy(Bitmap.Config.ARGB_8888, true)
            return s
        }
        Canvas(a).drawBitmap(s, 0f, 0f, Paint().apply { alpha = 14 })
        val w = s.width
        val h = s.height
        val now = pixels(s)
        val old = pixels(a)
        for (i in now.indices) {
            val c = now[i]
            val d = old[i]
            val cr = (c shr 16) and 255
            val cg = (c shr 8) and 255
            val cb = c and 255
            val dr = (d shr 16) and 255
            val dg = (d shr 8) and 255
            val db = d and 255
            val diff = min(255, abs(cr - dr) + abs(cg - dg) + abs(cb - db))
            val mix = diff * 150 / 255
            now[i] = pack(
                (cr * (255 - mix) + (255 - dr) * mix) / 255,
                (cg * (255 - mix) + (255 - dg) * mix) / 255,
                (cb * (255 - mix) + (255 - db) * mix) / 255
            )
        }
        return make(now, w, h)
    }

    /** Light painting: bright things leave streaks that slowly fade. */
    private fun lightPaint(src: Bitmap): Bitmap {
        val s = small(src)
        val a = acc
        if (a == null || a.width != s.width || a.height != s.height) {
            acc = s.copy(Bitmap.Config.ARGB_8888, true)
            return s
        }
        val canvas = Canvas(a)
        canvas.drawColor(Color.argb(10, 0, 0, 0))
        canvas.drawBitmap(s, 0f, 0f, Paint().apply { blendMode = BlendMode.LIGHTEN })
        return a.copy(Bitmap.Config.ARGB_8888, false)
    }

    /** Stutter: the picture only updates a couple of times a second. */
    private fun stutter(src: Bitmap): Bitmap {
        val now = System.currentTimeMillis()
        val h = held
        if (h == null || now - heldAt > 450) {
            val s = small(src)
            held = s.copy(Bitmap.Config.ARGB_8888, false)
            heldAt = now
            return s
        }
        return h
    }

    /** Pencil sketch: dark lines on white paper. */
    private fun sketch(src: Bitmap): Bitmap {
        val s = small(src)
        val w = s.width
        val h = s.height
        val lum = luminance(pixels(s))
        val out = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val v = 255 - min(255, sobel(lum, w, i) * 2)
                out[i] = pack(v, v, v)
            }
        }
        return make(out, w, h)
    }

    /** Neon edges: outlines glowing in the colours of the scene on black. */
    private fun neonEdges(src: Bitmap): Bitmap {
        val s = small(src)
        val w = s.width
        val h = s.height
        val px = pixels(s)
        val lum = luminance(px)
        val out = IntArray(w * h) { 0xFF000000.toInt() }
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val m = min(255, sobel(lum, w, i) * 3)
                val c = px[i]
                // push the colour towards full brightness so thin lines glow
                val r = min(255, ((c shr 16) and 255) * 2)
                val g = min(255, ((c shr 8) and 255) * 2)
                val b = min(255, (c and 255) * 2)
                out[i] = pack(r * m / 255, g * m / 255, b * m / 255)
            }
        }
        return make(out, w, h)
    }

    /** Maps brightness to a colour ramp (thermal camera, rainbow). */
    private fun lutMap(src: Bitmap, lut: IntArray): Bitmap {
        val s = small(src)
        val px = pixels(s)
        val lum = luminance(px)
        for (i in px.indices) px[i] = lut[lum[i]]
        return make(px, s.width, s.height)
    }

    /** Night scope: amplified green image with grain and a round field of view. */
    private fun nightScope(src: Bitmap): Bitmap {
        val s = small(src)
        val w = s.width
        val h = s.height
        val lum = luminance(pixels(s))
        val out = IntArray(w * h)
        var seed = (System.nanoTime() and 0x7FFFFFFF).toInt() or 1
        for (y in 0 until h) {
            val dy = (y - h / 2f) / (h / 2f)
            for (x in 0 until w) {
                seed = seed xor (seed shl 13)
                seed = seed xor (seed ushr 17)
                seed = seed xor (seed shl 5)
                val noise = (seed and 63) - 32
                val dx = (x - w / 2f) / (h / 2f)
                val fade = (1.3f - (dx * dx + dy * dy) * 0.55f).coerceIn(0f, 1f)
                val g = clamp(((lum[y * w + x] * 2 + noise) * fade).toInt())
                out[y * w + x] = pack(g / 8, g, g / 5)
            }
        }
        return make(out, w, h)
    }

    /** Motion: only what moves shows up (grey diff, or colour on a dark scene). */
    private fun motion(src: Bitmap, colour: Boolean): Bitmap {
        val s = small(src)
        val px = pixels(s)
        val lum = luminance(px)
        val prev = prevLum
        prevLum = lum
        if (prev == null || prev.size != lum.size) return s
        val out = IntArray(px.size)
        for (i in px.indices) {
            val m = min(255, abs(lum[i] - prev[i]) * 10)
            if (colour) {
                val c = px[i]
                val dim = lum[i] / 5
                out[i] = pack(
                    (((c shr 16) and 255) * m + dim * (255 - m)) / 255,
                    (((c shr 8) and 255) * m + dim * (255 - m)) / 255,
                    ((c and 255) * m + dim * (255 - m)) / 255
                )
            } else {
                out[i] = pack(m, m, m)
            }
        }
        return make(out, s.width, s.height)
    }

    /** Halftone: the picture made of dots, like a comic book print. */
    private fun halftone(src: Bitmap, colour: Boolean): Bitmap {
        val w = W
        val h = max(2, src.height * W / src.width)
        val cell = 10
        val cols = w / cell
        val rows = h / cell
        val grid = Bitmap.createScaledBitmap(src, cols, rows, true)
        val px = pixels(grid)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(if (colour) Color.BLACK else Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (cy in 0 until rows) {
            for (cx in 0 until cols) {
                val c = px[cy * cols + cx]
                val r = (c shr 16) and 255
                val g = (c shr 8) and 255
                val b = c and 255
                val l = (r * 299 + g * 587 + b * 114) / 1000 / 255f
                val radius = cell * 0.75f * sqrt(if (colour) l else 1f - l)
                if (radius < 0.5f) continue
                paint.color = if (colour) {
                    // saturate so the dots are vivid
                    Color.rgb(clamp(r * 5 / 4), clamp(g * 5 / 4), clamp(b * 5 / 4))
                } else {
                    Color.BLACK
                }
                canvas.drawCircle(cx * cell + cell / 2f, cy * cell + cell / 2f, radius, paint)
            }
        }
        return out
    }

    /** Pixel art: big square pixels with few colours (or the four greens of an old handheld). */
    private fun pixelArt(src: Bitmap, gameboy: Boolean): Bitmap {
        val tiny = small(src, 160)
        val px = pixels(tiny)
        if (gameboy) {
            val palette = intArrayOf(0x0F380F, 0x306230, 0x8BAC0F, 0x9BBC0F)
            val lum = luminance(px)
            for (i in px.indices) px[i] = 0xFF000000.toInt() or palette[min(3, lum[i] shr 6)]
        } else {
            for (i in px.indices) {
                val c = px[i]
                px[i] = pack(
                    (((c shr 16) and 255) * 4 shr 8) * 85,
                    (((c shr 8) and 255) * 4 shr 8) * 85,
                    ((c and 255) * 4 shr 8) * 85
                )
            }
        }
        val low = make(px, tiny.width, tiny.height)
        return Bitmap.createScaledBitmap(low, W, tiny.height * W / tiny.width, false)
    }

    /** ASCII art: the scene drawn with text characters. */
    private fun ascii(src: Bitmap): Bitmap {
        val outW = 1280
        val outH = max(2, src.height * outW / src.width)
        val cellW = 10
        val cellH = 20
        val cols = outW / cellW
        val rows = outH / cellH
        val grid = Bitmap.createScaledBitmap(src, cols, rows, true)
        val lum = luminance(pixels(grid))
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 18f
            color = Color.rgb(60, 255, 90)
        }
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val idx = lum[r * cols + c] * (asciiChars.size - 1) / 255
                if (idx > 0) canvas.drawText(asciiChars[idx], c * cellW.toFloat(), (r + 1) * cellH - 4f, paint)
            }
        }
        return out
    }

    /** Bloom (bright parts glow) or soft glow (dreamy soft focus). */
    private fun glow(src: Bitmap, bloom: Boolean): Bitmap {
        val s = small(src)
        val out = s.copy(Bitmap.Config.ARGB_8888, true)
        val blur = blurred(s, 12)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        if (bloom) {
            paint.blendMode = BlendMode.PLUS
            paint.alpha = 170
            paint.colorFilter = ColorMatrixColorFilter(
                ColorMatrix(floatArrayOf(
                    1.8f, 0f, 0f, 0f, -110f,
                    0f, 1.8f, 0f, 0f, -110f,
                    0f, 0f, 1.8f, 0f, -110f,
                    0f, 0f, 0f, 1f, 0f
                ))
            )
        } else {
            paint.alpha = 120
        }
        Canvas(out).drawBitmap(blur, 0f, 0f, paint)
        return out
    }

    /** Tilt-shift: only a band in the middle is sharp, which makes everything look like a miniature. */
    private fun tiltShift(src: Bitmap): Bitmap {
        val s = small(src)
        val w = s.width
        val h = s.height
        val sharp = pixels(s)
        val soft = pixels(blurred(s, 8))
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val t = abs(y / h.toFloat() - 0.5f) * 2f
            val mix = ((t - 0.25f) / 0.5f).coerceIn(0f, 1f)
            val m = (mix * 256).toInt()
            for (x in 0 until w) {
                val a = sharp[y * w + x]
                val b = soft[y * w + x]
                // a little extra saturation: miniatures look like toys
                val r = clamp((((a shr 16) and 255) * (256 - m) + ((b shr 16) and 255) * m) shr 8)
                val g = clamp((((a shr 8) and 255) * (256 - m) + ((b shr 8) and 255) * m) shr 8)
                val bl = clamp(((a and 255) * (256 - m) + (b and 255) * m) shr 8)
                val l = (r * 299 + g * 587 + bl * 114) / 1000
                out[y * w + x] = pack(
                    clamp(l + (r - l) * 13 / 10), clamp(l + (g - l) * 13 / 10), clamp(l + (bl - l) * 13 / 10)
                )
            }
        }
        return make(out, w, h)
    }

    /** Sharpen / HDR look (unsharp mask). */
    private fun sharpen(src: Bitmap): Bitmap {
        val s = small(src)
        val px = pixels(s)
        val soft = pixels(blurred(s, 4))
        for (i in px.indices) {
            val a = px[i]
            val b = soft[i]
            px[i] = pack(
                clamp(((a shr 16) and 255) + (((a shr 16) and 255) - ((b shr 16) and 255)) * 2),
                clamp(((a shr 8) and 255) + (((a shr 8) and 255) - ((b shr 8) and 255)) * 2),
                clamp((a and 255) + ((a and 255) - (b and 255)) * 2)
            )
        }
        return make(px, s.width, s.height)
    }

    /** Posterize: four tones per colour channel. */
    private fun posterize(src: Bitmap): Bitmap {
        val s = small(src)
        val px = pixels(s)
        for (i in px.indices) {
            val c = px[i]
            px[i] = pack(
                (((c shr 16) and 255) * 4 shr 8) * 85,
                (((c shr 8) and 255) * 4 shr 8) * 85,
                ((c and 255) * 4 shr 8) * 85
            )
        }
        return make(px, s.width, s.height)
    }

    /** 1-bit dithering, like an old newspaper or a first-generation Mac. */
    private fun dither(src: Bitmap): Bitmap {
        val s = small(src)
        val w = s.width
        val h = s.height
        val lum = luminance(pixels(s))
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val threshold = bayer[(y and 3) * 4 + (x and 3)] * 16 + 8
                out[y * w + x] = if (lum[y * w + x] > threshold) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            }
        }
        return make(out, w, h)
    }

    /** Painting: few colours, soft brush-like blobs. */
    private fun painting(src: Bitmap): Bitmap {
        val tiny = small(src, 140)
        val px = pixels(tiny)
        for (i in px.indices) {
            val c = px[i]
            px[i] = pack(
                (((c shr 16) and 255) * 6 shr 8) * 51,
                (((c shr 8) and 255) * 6 shr 8) * 51,
                ((c and 255) * 6 shr 8) * 51
            )
        }
        val low = make(px, tiny.width, tiny.height)
        return Bitmap.createScaledBitmap(low, W, tiny.height * W / tiny.width, true)
    }
}
