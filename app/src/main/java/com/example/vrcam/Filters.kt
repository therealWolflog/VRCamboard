package com.example.vrcam

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

/** Pixel/geometry effects, drawn in CameraScreen.kt. */
enum class Effect { NONE, PIXELATE, BLUR, MIRROR, QUAD, FISHEYE, PINCH, SWIRL, TILES, ZOOM, MIRROR_V, GHOST, WAVE, GLITCH, RIPPLE, WIDE, RGB_SPLIT, CRT, VIGNETTE, EDGES }

/** A live filter: a colour matrix and/or a pixel effect. */
enum class CamFilter(val label: String, private val values: FloatArray?, val effect: Effect = Effect.NONE) {
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
    ),
    CYANOTYPE("Cyanotype", floatArrayOf(
            0.06f, 0.12f, 0.02f, 0f, 0f,
            0.21f, 0.41f, 0.08f, 0f, 10f,
            0.33f, 0.65f, 0.12f, 0f, 45f,
            0f, 0f, 0f, 1f, 0f
        )),
    AMBER("Amber", floatArrayOf(
            0.36f, 0.71f, 0.13f, 0f, 0f,
            0.21f, 0.41f, 0.08f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    POP("Pop", floatArrayOf(
            1.4f, 0f, 0f, 0f, -51f,
            0f, 1.4f, 0f, 0f, -51f,
            0f, 0f, 1.4f, 0f, -51f,
            0f, 0f, 0f, 1f, 0f
        )),
    MOODY("Moody", floatArrayOf(
            0.75f, 0f, 0f, 0f, 0f,
            0f, 0.8f, 0f, 0f, 5f,
            0f, 0f, 0.95f, 0f, 15f,
            0f, 0f, 0f, 1f, 0f
        )),
    SUNSET("Sunset", floatArrayOf(
            1.25f, 0f, 0f, 0f, 25f,
            0f, 0.85f, 0f, 0f, 0f,
            0f, 0f, 0.9f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )),
    ICE("Ice", floatArrayOf(
            0.8f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 10f,
            0f, 0f, 1.3f, 0f, 30f,
            0f, 0f, 0f, 1f, 0f
        )),
    SYNTHWAVE("Synthwave", floatArrayOf(
            0.8f, 0f, 0.5f, 0f, 0f,
            0f, 0.4f, 0f, 0f, 0f,
            0.2f, 0f, 1.1f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        )),
    HEAT("Heat", floatArrayOf(
            0.6f, 1.2f, 0.2f, 0f, -30f,
            0.3f, 0.6f, 0.1f, 0f, -40f,
            -0.3f, -0.6f, -0.1f, 0f, 60f,
            0f, 0f, 0f, 1f, 0f
        )),
    ALIEN("Alien", floatArrayOf(
            0f, 1f, 0f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    BGR("Swap R/B", floatArrayOf(
            0f, 0f, 1f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    POLAROID("Polaroid", floatArrayOf(
            1.438f, -0.062f, -0.062f, 0f, 0f,
            -0.122f, 1.378f, -0.122f, 0f, 0f,
            -0.016f, -0.016f, 1.483f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    KODACHROME("Kodachrome", floatArrayOf(
            1.1286f, -0.3967f, -0.0399f, 0f, 63.7f,
            -0.164f, 1.0835f, -0.055f, 0f, 24.7f,
            -0.1679f, -0.5603f, 1.6015f, 0f, 35.6f,
            0f, 0f, 0f, 1f, 0f
        )),
    TECHNICOLOR("Technicolor", floatArrayOf(
            1.9125f, -0.8545f, -0.0916f, 0f, 11.8f,
            -0.3088f, 1.7659f, -0.106f, 0f, -70.4f,
            -0.2311f, -0.7502f, 1.8476f, 0f, 31f,
            0f, 0f, 0f, 1f, 0f
        )),
    VINTAGE("Vintage", floatArrayOf(
            0.6279f, 0.3202f, -0.0397f, 0f, 9.7f,
            0.0258f, 0.6441f, 0.0326f, 0f, 7.5f,
            0.0466f, -0.0851f, 0.5242f, 0f, 5.2f,
            0f, 0f, 0f, 1f, 0f
        )),
    BROWNI("Browni", floatArrayOf(
            0.5997f, 0.3455f, -0.2708f, 0f, 47.4f,
            -0.0377f, 0.861f, 0.1506f, 0f, -37f,
            0.2411f, -0.0744f, 0.4497f, 0f, -7.6f,
            0f, 0f, 0f, 1f, 0f
        )),
    FADED("Faded", ColorMatrix().apply { setSaturation(0.45f) }.array),
    PIXELATE("Pixelate", null, Effect.PIXELATE),
    BLUR("Blur", null, Effect.BLUR),
    MIRROR("Mirror", null, Effect.MIRROR),
    QUAD("Kaleidoscope", null, Effect.QUAD),
    FISHEYE("Fisheye", null, Effect.FISHEYE),
    PINCH("Pinch", null, Effect.PINCH),
    SWIRL("Swirl", null, Effect.SWIRL),
    LOMO("Lomo", floatArrayOf(
            1.3f, 0f, 0f, 0f, -20f,
            0f, 1.3f, 0f, 0f, -10f,
            0f, 0f, 1.0f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )),
    CROSS("Cross-process", floatArrayOf(
            1.2f, 0.1f, 0f, 0f, -10f,
            0f, 1.0f, 0.1f, 0f, 0f,
            0f, 0.1f, 0.8f, 0f, 30f,
            0f, 0f, 0f, 1f, 0f
        )),
    FOREST("Forest", floatArrayOf(
            0.8f, 0.2f, 0f, 0f, 0f,
            0f, 1.15f, 0f, 0f, 5f,
            0f, 0.1f, 0.8f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    ROSE("Rose", floatArrayOf(
            1.1f, 0f, 0.1f, 0f, 15f,
            0f, 0.85f, 0f, 0f, 0f,
            0.1f, 0f, 1.0f, 0f, 15f,
            0f, 0f, 0f, 1f, 0f
        )),
    GOLD("Gold", floatArrayOf(
            1.2f, 0.2f, 0f, 0f, 10f,
            0f, 1.0f, 0f, 0f, 5f,
            0f, 0f, 0.5f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    LIME("Lime", floatArrayOf(
            0.8f, 0.3f, 0f, 0f, 0f,
            0f, 1.3f, 0f, 0f, 0f,
            0f, 0f, 0.4f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    RED_ONLY("Red only", floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    GREEN_ONLY("Green only", floatArrayOf(
            0f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    BLUE_ONLY("Blue only", floatArrayOf(
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    VIOLET("Violet", floatArrayOf(
            0.25f, 0.5f, 0.09f, 0f, 40f,
            0.1f, 0.2f, 0.04f, 0f, 0f,
            0.35f, 0.7f, 0.13f, 0f, 60f,
            0f, 0f, 0f, 1f, 0f
        )),
    MINT("Mint", floatArrayOf(
            0.5f, 0.2f, 0f, 0f, 0f,
            0f, 1f, 0.1f, 0f, 25f,
            0.1f, 0.3f, 0.9f, 0f, 25f,
            0f, 0f, 0f, 1f, 0f
        )),
    DUSK("Dusk", floatArrayOf(
            0.9f, 0f, 0.2f, 0f, 10f,
            0f, 0.75f, 0.1f, 0f, 0f,
            0.2f, 0f, 0.9f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        )),
    BRIGHT("Bright", floatArrayOf(
            1.15f, 0f, 0f, 0f, 35f,
            0f, 1.15f, 0f, 0f, 35f,
            0f, 0f, 1.15f, 0f, 35f,
            0f, 0f, 0f, 1f, 0f
        )),
    LOWKEY("Low key", floatArrayOf(
            1.2f, 0f, 0f, 0f, -60f,
            0f, 1.2f, 0f, 0f, -60f,
            0f, 0f, 1.2f, 0f, -60f,
            0f, 0f, 0f, 1f, 0f
        )),
    NEGATIVE_MONO("Negative mono", floatArrayOf(
            -0.3f, -0.59f, -0.11f, 0f, 255f,
            -0.3f, -0.59f, -0.11f, 0f, 255f,
            -0.3f, -0.59f, -0.11f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )),
    BLUEPRINT("Blueprint", floatArrayOf(
            -0.05f, -0.1f, -0.02f, 0f, 30f,
            -0.12f, -0.24f, -0.04f, 0f, 110f,
            -0.3f, -0.59f, -0.11f, 0f, 230f,
            0f, 0f, 0f, 1f, 0f
        )),
    BLEACH("Bleach", ColorMatrix().apply {
        setSaturation(0.5f)
        postConcat(ColorMatrix(floatArrayOf(
            1.3f, 0f, 0f, 0f, -38f,
            0f, 1.3f, 0f, 0f, -38f,
            0f, 0f, 1.3f, 0f, -38f,
            0f, 0f, 0f, 1f, 0f
        )))
    }.array),
    TILES("Tiles", null, Effect.TILES),
    ZOOM("Zoom 2x", null, Effect.ZOOM),
    MIRROR_UP("Mirror up", null, Effect.MIRROR_V),
    GHOST("Ghost", null, Effect.GHOST),
    WAVE("Wave", null, Effect.WAVE),
    GLITCH("Glitch", null, Effect.GLITCH),
    GOTHAM("Gotham", floatArrayOf(
            0.25f, 0.5f, 0.09f, 0f, 0f,
            0.25f, 0.5f, 0.09f, 0f, 0f,
            0.32f, 0.6f, 0.12f, 0f, 15f,
            0f, 0f, 0f, 1f, 0f
        )),
    TOXIC("Toxic", floatArrayOf(
            0.6f, 0.4f, 0f, 0f, 0f,
            0f, 1.2f, 0f, 0f, 10f,
            0f, 0.2f, 0.4f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    CANDY("Candy", floatArrayOf(
            1.2f, 0f, 0.1f, 0f, 10f,
            0f, 0.9f, 0.1f, 0f, 0f,
            0.1f, 0f, 1.2f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        )),
    OCEAN("Ocean", floatArrayOf(
            0.6f, 0.2f, 0.1f, 0f, 0f,
            0f, 0.95f, 0.2f, 0f, 10f,
            0.1f, 0.2f, 1.3f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )),
    AUTUMN("Autumn", floatArrayOf(
            1.15f, 0.15f, 0f, 0f, 5f,
            0f, 0.95f, 0f, 0f, 0f,
            0f, 0f, 0.6f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    ARCTIC("Arctic", floatArrayOf(
            0.85f, 0.1f, 0.1f, 0f, 15f,
            0.05f, 0.95f, 0.15f, 0f, 15f,
            0.1f, 0.1f, 1.2f, 0f, 30f,
            0f, 0f, 0f, 1f, 0f
        )),
    LAVA("Lava", floatArrayOf(
            1.5f, 0.3f, 0f, 0f, 0f,
            0f, 0.7f, 0f, 0f, 0f,
            0f, 0f, 0.3f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    MATRIX("Matrix", floatArrayOf(
            0f, 0f, 0f, 0f, 0f,
            0.42f, 0.83f, 0.15f, 0f, -30f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    CINEMA("Cinema", floatArrayOf(
            1.15f, 0f, -0.1f, 0f, 0f,
            -0.05f, 1.0f, 0.05f, 0f, 0f,
            -0.15f, 0.2f, 1.1f, 0f, 5f,
            0f, 0f, 0f, 1f, 0f
        )),
    PASTEL("Pastel", floatArrayOf(
            0.9f, 0.05f, 0.05f, 0f, 40f,
            0.05f, 0.9f, 0.05f, 0f, 40f,
            0.05f, 0.05f, 0.9f, 0f, 40f,
            0f, 0f, 0f, 1f, 0f
        )),
    MOONLIGHT("Moonlight", floatArrayOf(
            0.4f, 0.2f, 0f, 0f, 0f,
            0f, 0.5f, 0.2f, 0f, 5f,
            0.1f, 0.3f, 0.9f, 0f, 25f,
            0f, 0f, 0f, 1f, 0f
        )),
    INFRARED("Infrared", floatArrayOf(
            0.2f, 1.3f, 0f, 0f, 0f,
            0.5f, 0.3f, 0.4f, 0f, 20f,
            0.4f, 0.4f, 0.6f, 0f, 40f,
            0f, 0f, 0f, 1f, 0f
        )),
    RED_NIGHT("Red night", floatArrayOf(
            0.3f, 0.59f, 0.11f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    RIPPLE("Ripple", null, Effect.RIPPLE),
    STRETCH("Stretch", null, Effect.WIDE),
    RGB_SPLIT("RGB split", null, Effect.RGB_SPLIT),
    CRT("CRT", null, Effect.CRT),
    VIGNETTE("Vignette", null, Effect.VIGNETTE),
    EDGES("Edges", null, Effect.EDGES);

    fun colorFilter(): ColorMatrixColorFilter? = values?.let { ColorMatrixColorFilter(ColorMatrix(it)) }

    fun next(): CamFilter = entries[(ordinal + 1) % entries.size]
}
