package com.example.vrcam

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

/** A live colour filter. Each one is a 4x5 colour matrix applied when drawing a frame. */
enum class CamFilter(val label: String, private val values: FloatArray?) {
    NORMAL("Normal", null),
    MONO("Mono", ColorMatrix().apply { setSaturation(0f) }.array),
    NOIR(
        "Noir", floatArrayOf(
            0.48f, 0.96f, 0.18f, 0f, -76f,
            0.48f, 0.96f, 0.18f, 0f, -76f,
            0.48f, 0.96f, 0.18f, 0f, -76f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    SEPIA(
        "Sepia", floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    VIVID("Vivid", ColorMatrix().apply { setSaturation(1.7f) }.array),
    COOL(
        "Cool", floatArrayOf(
            0.9f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1.15f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    WARM(
        "Warm", floatArrayOf(
            1.15f, 0f, 0f, 0f, 10f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 0.85f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    DREAM(
        "Dream", floatArrayOf(
            0.8f, 0f, 0f, 0f, 55f,
            0f, 0.8f, 0f, 0f, 40f,
            0f, 0f, 0.8f, 0f, 55f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    NIGHT(
        "Night vision", floatArrayOf(
            0f, 0f, 0f, 0f, 0f,
            0.45f, 0.9f, 0.15f, 0f, 20f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    INVERT(
        "Invert", floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    );

    fun colorFilter(): ColorMatrixColorFilter? = values?.let { ColorMatrixColorFilter(ColorMatrix(it)) }

    fun next(): CamFilter = entries[(ordinal + 1) % entries.size]
}
