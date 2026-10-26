package com.example.vrcam

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

/** Pixel/geometry effects, drawn in CameraScreen.kt. */
enum class Effect { NONE, PIXELATE, BLUR, MIRROR, QUAD, FISHEYE, PINCH, SWIRL, TILES, ZOOM, MIRROR_V, GHOST, WAVE, GLITCH, RIPPLE, WIDE, RGB_SPLIT, CRT, VIGNETTE, EDGES,
    EMBOSS, CELL, COMIC, TRAIL, AFTERIMAGE, HUE_CYCLE, DRUNK, MELT, GLITCH_RGB, SPOTLIGHT, CIRCLE, LETTERBOX,
    MIRROR_R, MIRROR_D, KALEIDO6, KALEIDO8, TILES2, TILES4, ZOOM3, ZOOM4, PULSE, SPIN, DUTCH, SHAKE, DOUBLE, INFINITY, 
    FLIP_BLEND, WARHOL, HOLOGRAM, VHS, SECURITY, FISHEYE2, PINCH2, WHIRL, WAVE_V, TALL, TINY_PLANET, SKETCH, NEON_EDGES, 
    THERMAL, RAINBOW, NIGHT_SCOPE, MOTION, MOTION_COLOR, LONG_TRAIL, LIGHT_PAINT, STUTTER, HALFTONE, HALFTONE_BW, 
    PIXEL8, GAMEBOY_FX, ASCII, BLOOM, SOFT_GLOW, TILT_SHIFT, SHARPEN, POSTERIZE, DITHER, PAINTING }

/** Colour matrix that rotates every hue by [degrees]. */
fun hueMatrix(degrees: Float): FloatArray {
    val r = Math.toRadians(degrees.toDouble())
    val c = Math.cos(r).toFloat()
    val s = Math.sin(r).toFloat()
    return floatArrayOf(
        0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
        0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
        0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}

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
    EDGES("Edges", null, Effect.EDGES),
    EMBOSS("Embossed", null, Effect.EMBOSS),
    CELL("Cell shading", null, Effect.CELL),
    COMIC("Comic", null, Effect.COMIC),
    TRAIL("Trail", null, Effect.TRAIL),
    AFTERIMAGE("After image", null, Effect.AFTERIMAGE),
    HUE_CYCLE("Hue cycle", null, Effect.HUE_CYCLE),
    DRUNK("Drunk", null, Effect.DRUNK),
    MELT("Melt", null, Effect.MELT),
    GLITCH_RGB("Glitcher", null, Effect.GLITCH_RGB),
    SPOTLIGHT("Spotlight", null, Effect.SPOTLIGHT),
    CIRCLE("Circle mask", null, Effect.CIRCLE),
    LETTERBOX("Letterbox", null, Effect.LETTERBOX),
    ROTATE_RGB(
        "Rotate RGB", floatArrayOf(
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    HUE_120("Hue 120°", hueMatrix(120f)),
    HUE_180("Hue 180°", hueMatrix(180f)),
    HUE_240("Hue 240°", hueMatrix(240f)),
    DUO_VAPOR("Vaporwave", floatArrayOf(
            0.2755f, 0.541f, 0.1051f, 0f, 20f,
            0.129f, 0.2532f, 0.0492f, 0f, 10f,
            0.1642f, 0.3223f, 0.0626f, 0f, 80f,
            0f, 0f, 0f, 1f, 0f
        )),
    DUO_TEAL_ORANGE("Teal and orange", floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.1173f, 0.2302f, 0.0447f, 0f, 50f,
            -0.0117f, -0.023f, -0.0045f, 0f, 70f,
            0f, 0f, 0f, 1f, 0f
        )),
    DUO_ROYAL("Royal", floatArrayOf(
            0.2873f, 0.564f, 0.1095f, 0f, 10f,
            0.2286f, 0.4489f, 0.0872f, 0f, 20f,
            -0.0117f, -0.023f, -0.0045f, 0f, 90f,
            0f, 0f, 0f, 1f, 0f
        )),
    DUO_GAMEBOY("Gameboy tint", floatArrayOf(
            0.1642f, 0.3223f, 0.0626f, 0f, 15f,
            0.1548f, 0.3039f, 0.059f, 0f, 56f,
            0f, 0f, 0f, 0f, 15f,
            0f, 0f, 0f, 1f, 0f
        )),
    DUO_PARCHMENT("Parchment", floatArrayOf(
            0.2169f, 0.4259f, 0.0827f, 0f, 60f,
            0.2228f, 0.4374f, 0.0849f, 0f, 40f,
            0.1993f, 0.3913f, 0.076f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        )),
    DUO_BLOOD("Blood", floatArrayOf(
            0.2697f, 0.5295f, 0.1028f, 0f, 0f,
            0.0235f, 0.046f, 0.0089f, 0f, 0f,
            0.0352f, 0.0691f, 0.0134f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    DUO_CYAN("Cyan ink", floatArrayOf(
            0f, 0f, 0f, 0f, 0f,
            0.2697f, 0.5295f, 0.1028f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    DUO_MAGENTA("Magenta ink", floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0.2345f, 0.4604f, 0.0894f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    DUO_LIME("Lime ink", floatArrayOf(
            0.1876f, 0.3683f, 0.0715f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    DUO_SUNBURN("Sunburn", floatArrayOf(
            0.1583f, 0.3108f, 0.0604f, 0f, 120f,
            0.2345f, 0.4604f, 0.0894f, 0f, 0f,
            0.129f, 0.2532f, 0.0492f, 0f, 40f,
            0f, 0f, 0f, 1f, 0f
        )),
    DUO_AQUA("Aqua night", floatArrayOf(
            0.1407f, 0.2762f, 0.0536f, 0f, 0f,
            0.2873f, 0.564f, 0.1095f, 0f, 10f,
            0.2228f, 0.4374f, 0.0849f, 0f, 40f,
            0f, 0f, 0f, 1f, 0f
        )),
    DUO_VIOLET_GOLD("Violet gold", floatArrayOf(
            0.2404f, 0.4719f, 0.0916f, 0f, 50f,
            0.258f, 0.5064f, 0.0984f, 0f, 0f,
            -0.0352f, -0.0691f, -0.0134f, 0f, 90f,
            0f, 0f, 0f, 1f, 0f
        )),
    DUO_ICE_FIRE("Ice and fire", floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.0352f, 0.0691f, 0.0134f, 0f, 60f,
            -0.1876f, -0.3683f, -0.0715f, 0f, 160f,
            0f, 0f, 0f, 1f, 0f
        )),
    THRESHOLD("Threshold", floatArrayOf(
            2.99f, 5.87f, 1.14f, 0f, -1150f,
            2.99f, 5.87f, 1.14f, 0f, -1150f,
            2.99f, 5.87f, 1.14f, 0f, -1150f,
            0f, 0f, 0f, 1f, 0f
        )),
    OVEREXPOSED("Overexposed", floatArrayOf(
            1.6f, 0f, 0f, 0f, 20f,
            0f, 1.6f, 0f, 0f, 20f,
            0f, 0f, 1.6f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        )),
    UNDEREXPOSED("Underexposed", floatArrayOf(
            0.55f, 0f, 0f, 0f, 0f,
            0f, 0.55f, 0f, 0f, 0f,
            0f, 0f, 0.55f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    HAZE("Haze", floatArrayOf(
            0.75f, 0.1f, 0.1f, 0f, 55f,
            0.1f, 0.75f, 0.1f, 0f, 55f,
            0.1f, 0.1f, 0.75f, 0f, 60f,
            0f, 0f, 0f, 1f, 0f
        )),
    FADED_FILM("Faded film", floatArrayOf(
            0.8f, 0.1f, 0f, 0f, 35f,
            0.05f, 0.8f, 0.05f, 0f, 35f,
            0f, 0.1f, 0.75f, 0f, 40f,
            0f, 0f, 0f, 1f, 0f
        )),
    CRUSHED("Crushed", floatArrayOf(
            1.3f, 0f, 0f, 0f, -45f,
            0f, 1.3f, 0f, 0f, -45f,
            0f, 0f, 1.3f, 0f, -45f,
            0f, 0f, 0f, 1f, 0f
        )),
    DAY_FOR_NIGHT("Day for night", floatArrayOf(
            0.4f, 0f, 0f, 0f, 0f,
            0f, 0.5f, 0f, 0f, 5f,
            0f, 0f, 0.9f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        )),
    GOLDEN_HOUR("Golden hour", floatArrayOf(
            1.25f, 0.1f, 0f, 0f, 15f,
            0f, 1.05f, 0f, 0f, 5f,
            0f, 0f, 0.7f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    BLUE_HOUR("Blue hour", floatArrayOf(
            0.7f, 0f, 0f, 0f, 0f,
            0f, 0.85f, 0.1f, 0f, 5f,
            0.1f, 0.1f, 1.3f, 0f, 30f,
            0f, 0f, 0f, 1f, 0f
        )),
    NOIR_RED("Noir red", floatArrayOf(
            0.5f, 0.9f, 0.17f, 0f, 0f,
            0.2f, 0.35f, 0.07f, 0f, 0f,
            0.15f, 0.3f, 0.05f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    DAGUERREOTYPE("Daguerreotype", floatArrayOf(
            0.36f, 0.71f, 0.13f, 0f, -10f,
            0.35f, 0.7f, 0.13f, 0f, -10f,
            0.31f, 0.62f, 0.12f, 0f, -15f,
            0f, 0f, 0f, 1f, 0f
        )),
    CYBERPUNK("Cyberpunk", floatArrayOf(
            1.2f, 0f, 0.3f, 0f, 0f,
            0f, 0.6f, 0.2f, 0f, 0f,
            0.3f, 0.1f, 1.4f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )),
    RADIOACTIVE("Radioactive", floatArrayOf(
            0.8f, 0.6f, 0f, 0f, 0f,
            0.2f, 1.3f, 0f, 0f, 10f,
            0f, 0.2f, 0.2f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    UNDERWATER("Underwater", floatArrayOf(
            0.4f, 0.1f, 0f, 0f, 0f,
            0f, 0.9f, 0.2f, 0f, 10f,
            0f, 0.2f, 1.1f, 0f, 30f,
            0f, 0f, 0f, 1f, 0f
        )),
    DESERT("Desert", floatArrayOf(
            1.2f, 0.2f, 0f, 0f, 10f,
            0.1f, 1f, 0f, 0f, 10f,
            0f, 0.1f, 0.7f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    WINTER("Winter", floatArrayOf(
            0.9f, 0.05f, 0.1f, 0f, 15f,
            0.05f, 0.95f, 0.1f, 0f, 20f,
            0.1f, 0.1f, 1.1f, 0f, 35f,
            0f, 0f, 0f, 1f, 0f
        )),
    SPRING("Spring", floatArrayOf(
            0.95f, 0.15f, 0f, 0f, 5f,
            0f, 1.15f, 0f, 0f, 10f,
            0f, 0.1f, 0.8f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )),
    SUMMER("Summer", floatArrayOf(
            1.3f, 0.1f, 0f, 0f, 5f,
            0f, 1.15f, 0f, 0f, 5f,
            0f, 0f, 0.85f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    NOSTALGIA("Nostalgia", floatArrayOf(
            0.9f, 0.2f, 0f, 0f, 25f,
            0.1f, 0.85f, 0f, 0f, 15f,
            0.05f, 0.1f, 0.6f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )),
    XRAY("X-ray", floatArrayOf(
            -0.2f, -0.4f, -0.07f, 0f, 170f,
            -0.25f, -0.5f, -0.09f, 0f, 200f,
            -0.3f, -0.6f, -0.1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )),
    PROTANOPIA("Protanopia (red-blind)", floatArrayOf(
            0.567f, 0.433f, 0f, 0f, 0f,
            0.558f, 0.442f, 0f, 0f, 0f,
            0f, 0.242f, 0.758f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    DEUTERANOPIA("Deuteranopia (green-blind)", floatArrayOf(
            0.625f, 0.375f, 0f, 0f, 0f,
            0.7f, 0.3f, 0f, 0f, 0f,
            0f, 0.3f, 0.7f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    TRITANOPIA("Tritanopia (blue-blind)", floatArrayOf(
            0.95f, 0.05f, 0f, 0f, 0f,
            0f, 0.433f, 0.567f, 0f, 0f,
            0f, 0.475f, 0.525f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    SWAP_GB("Swap G/B", floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    ROTATE_BRG("Rotate BRG", floatArrayOf(
            0f, 0f, 1f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    INVERT_RED("Invert red", floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    INVERT_GREEN("Invert green", floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    INVERT_BLUE("Invert blue", floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )),
    VERY_BRIGHT("Very bright", floatArrayOf(
            1f, 0f, 0f, 0f, 70f,
            0f, 1f, 0f, 0f, 70f,
            0f, 0f, 1f, 0f, 70f,
            0f, 0f, 0f, 1f, 0f
        )),
    DARK("Dark", floatArrayOf(
            0.6f, 0f, 0f, 0f, 0f,
            0f, 0.6f, 0f, 0f, 0f,
            0f, 0f, 0.6f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
    DESATURATED("Desaturated", ColorMatrix().apply { setSaturation(0.3f) }.array),
    HYPER_SAT("Hyper saturated", ColorMatrix().apply { setSaturation(2.5f) }.array),
    ULTRA_SAT("Ultra saturated", ColorMatrix().apply { setSaturation(4.0f) }.array),
    HUE_60("Hue 60°", hueMatrix(60f)),
    HUE_90("Hue 90°", hueMatrix(90f)),
    HUE_150("Hue 150°", hueMatrix(150f)),
    HUE_210("Hue 210°", hueMatrix(210f)),
    HUE_270("Hue 270°", hueMatrix(270f)),
    HUE_300("Hue 300°", hueMatrix(300f)),
    HUE_330("Hue 330°", hueMatrix(330f)),
    MIRROR_RIGHT("Mirror right", null, Effect.MIRROR_R),
    MIRROR_DOWN("Mirror down", null, Effect.MIRROR_D),
    KALEIDO_SPIN("Kaleidoscope spin", null, Effect.KALEIDO6),
    KALEIDO_8("Kaleidoscope 8", null, Effect.KALEIDO8),
    TILES_2("Tiles 2x2", null, Effect.TILES2),
    TILES_4("Tiles 4x4", null, Effect.TILES4),
    ZOOM_3("Zoom 3x", null, Effect.ZOOM3),
    ZOOM_4("Zoom 4x", null, Effect.ZOOM4),
    PULSE("Pulse", null, Effect.PULSE),
    SPIN("Spin", null, Effect.SPIN),
    DUTCH("Dutch angle", null, Effect.DUTCH),
    SHAKE("Shake", null, Effect.SHAKE),
    DOUBLE_VISION("Double vision", null, Effect.DOUBLE),
    INFINITY("Infinity tunnel", null, Effect.INFINITY),
    FLIP_BLEND("Upside-down blend", null, Effect.FLIP_BLEND),
    WARHOL("Pop art 2x2", null, Effect.WARHOL),
    HOLOGRAM("Hologram", null, Effect.HOLOGRAM),
    VHS("VHS tape", null, Effect.VHS),
    SECURITY("Security cam", null, Effect.SECURITY),
    FISHEYE_XL("Fisheye XL", null, Effect.FISHEYE2),
    PINCH_XL("Pinch XL", null, Effect.PINCH2),
    WHIRLPOOL("Whirlpool", null, Effect.WHIRL),
    WAVE_UP("Wave vertical", null, Effect.WAVE_V),
    TALL("Tall", null, Effect.TALL),
    TINY_PLANET("Tiny planet", null, Effect.TINY_PLANET),
    SKETCH("Sketch", null, Effect.SKETCH),
    NEON_EDGES("Neon edges", null, Effect.NEON_EDGES),
    THERMAL("Thermal", null, Effect.THERMAL),
    RAINBOW("Rainbow", null, Effect.RAINBOW),
    NIGHT_SCOPE("Night scope", null, Effect.NIGHT_SCOPE),
    MOTION("Motion", null, Effect.MOTION),
    MOTION_COLOR("Motion colour", null, Effect.MOTION_COLOR),
    LONG_TRAIL("Long trail", null, Effect.LONG_TRAIL),
    LIGHT_PAINT("Light painting", null, Effect.LIGHT_PAINT),
    STUTTER("Stutter", null, Effect.STUTTER),
    HALFTONE("Halftone", null, Effect.HALFTONE),
    HALFTONE_BW("Halftone black and white", null, Effect.HALFTONE_BW),
    PIXEL_8BIT("8-bit pixels", null, Effect.PIXEL8),
    GAMEBOY("Game Boy", null, Effect.GAMEBOY_FX),
    ASCII("ASCII", null, Effect.ASCII),
    BLOOM("Bloom", null, Effect.BLOOM),
    SOFT_GLOW("Soft glow", null, Effect.SOFT_GLOW),
    TILT_SHIFT("Tilt-shift miniature", null, Effect.TILT_SHIFT),
    SHARPEN("Sharpen / HDR", null, Effect.SHARPEN),
    POSTERIZE("Posterize", null, Effect.POSTERIZE),
    DITHER("1-bit dither", null, Effect.DITHER),
    PAINTING("Painting", null, Effect.PAINTING);

    fun colorFilter(): ColorMatrixColorFilter? = values?.let { ColorMatrixColorFilter(ColorMatrix(it)) }

    fun next(): CamFilter = entries[(ordinal + 1) % entries.size]
}
