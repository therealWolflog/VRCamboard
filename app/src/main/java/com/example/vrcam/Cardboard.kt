package com.example.vrcam

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Lens and screen geometry of a Cardboard viewer (the data inside its QR code). Lengths in metres. */
data class CardboardParams(
    val vendor: String = "Generic",
    val model: String = "Cardboard",
    val screenToLens: Float = 0.042f,
    val interLens: Float = 0.060f,
    /** Half field of view seen through the lens, in degrees. */
    val fovLeft: Float = 40f,
    val fovRight: Float = 40f,
    val fovBottom: Float = 40f,
    val fovTop: Float = 40f,
    val trayToLens: Float = 0.035f,
    /** 0 = bottom, 1 = centre, 2 = top. */
    val verticalAlignment: Int = 0,
    val k1: Float = 0.441f,
    val k2: Float = 0.156f
) {
    val label: String get() = listOf(vendor, model).filter { it.isNotBlank() }.joinToString(" ")

    companion object {
        val DEFAULT = CardboardParams()
    }
}

/** Reads the QR code in [bmp], or returns null if there isn't one in view. */
fun decodeQr(bmp: Bitmap): String? {
    val w = bmp.width
    val h = bmp.height
    val pixels = IntArray(w * h)
    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
    return try {
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, pixels)))).text
    } catch (e: Exception) {
        null
    }
}

/**
 * Turns a Cardboard QR code into viewer settings. The QR holds a link; the settings are in its
 * p= value, but some viewers use a short link that first has to be followed over the internet.
 */
suspend fun resolveCardboardUrl(url: String): CardboardParams? = withContext(Dispatchers.IO) {
    var current = url.trim().replace("http://", "https://")
    repeat(6) {
        parseCardboardUrl(current)?.let { return@withContext it }
        try {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (code in 300..399 && location != null) {
                current = URL(URL(current), location).toString().replace("http://", "https://")
            } else {
                return@withContext null
            }
        } catch (e: Exception) {
            return@withContext null
        }
    }
    null
}

/** Reads the p= value of a Cardboard link (URL-safe base64 of a protobuf message). */
fun parseCardboardUrl(url: String): CardboardParams? {
    return try {
        val p = Uri.parse(url).getQueryParameter("p") ?: return null
        val padded = p + "=".repeat((4 - p.length % 4) % 4)
        parseDeviceParams(Base64.decode(padded, Base64.URL_SAFE))
    } catch (e: Exception) {
        null
    }
}

private class PbReader(val bytes: ByteArray) {
    var pos = 0
    fun hasMore() = pos < bytes.size

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = bytes[pos++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }

    fun float32(): Float {
        val v = (bytes[pos].toInt() and 0xFF) or
            ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
            ((bytes[pos + 2].toInt() and 0xFF) shl 16) or
            ((bytes[pos + 3].toInt() and 0xFF) shl 24)
        pos += 4
        return Float.fromBits(v)
    }
}

/**
 * Minimal reader for the Cardboard "DeviceParams" message:
 * 1 vendor, 2 model, 3 screen_to_lens, 4 inter_lens, 5 left-eye FOV angles (4 floats),
 * 6 tray_to_lens, 7 distortion coefficients, 11 vertical alignment.
 */
private fun parseDeviceParams(bytes: ByteArray): CardboardParams? {
    val r = PbReader(bytes)
    var vendor = ""
    var model = ""
    var screenToLens = -1f
    var interLens = -1f
    var trayToLens = -1f
    var alignment = 0
    val fov = mutableListOf<Float>()
    val dist = mutableListOf<Float>()

    while (r.hasMore()) {
        val tag = r.varint().toInt()
        val field = tag ushr 3
        when (tag and 7) {
            0 -> {
                val v = r.varint()
                if (field == 11) alignment = v.toInt()
            }
            1 -> r.pos += 8
            2 -> {
                val len = r.varint().toInt()
                val end = r.pos + len
                when (field) {
                    1 -> vendor = String(r.bytes, r.pos, len, Charsets.UTF_8)
                    2 -> model = String(r.bytes, r.pos, len, Charsets.UTF_8)
                    5 -> while (r.pos < end) fov += r.float32()
                    7 -> while (r.pos < end) dist += r.float32()
                }
                r.pos = end
            }
            5 -> {
                val f = r.float32()
                when (field) {
                    3 -> screenToLens = f
                    4 -> interLens = f
                    6 -> trayToLens = f
                    5 -> fov += f
                    7 -> dist += f
                }
            }
            else -> return null
        }
    }

    // Anything missing or absurd falls back to the generic Cardboard values.
    val d = CardboardParams.DEFAULT
    fun ok(v: Float, lo: Float, hi: Float) = v in lo..hi
    return CardboardParams(
        vendor = vendor,
        model = model,
        screenToLens = if (ok(screenToLens, 0.02f, 0.08f)) screenToLens else d.screenToLens,
        interLens = if (ok(interLens, 0.04f, 0.09f)) interLens else d.interLens,
        fovLeft = fov.getOrNull(0)?.takeIf { ok(it, 10f, 80f) } ?: d.fovLeft,
        fovRight = fov.getOrNull(1)?.takeIf { ok(it, 10f, 80f) } ?: d.fovRight,
        fovBottom = fov.getOrNull(2)?.takeIf { ok(it, 10f, 80f) } ?: d.fovBottom,
        fovTop = fov.getOrNull(3)?.takeIf { ok(it, 10f, 80f) } ?: d.fovTop,
        trayToLens = if (ok(trayToLens, 0.005f, 0.08f)) trayToLens else d.trayToLens,
        verticalAlignment = alignment.coerceIn(0, 2),
        k1 = dist.getOrNull(0)?.takeIf { ok(it, -1f, 2f) } ?: d.k1,
        k2 = dist.getOrNull(1)?.takeIf { ok(it, -1f, 2f) } ?: d.k2
    )
}
