package com.killswitch.tally

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The two colour sets as plain ARGB numbers, so the readability of every text and background pair can be tested without Android.
 * The UI turns these into Compose colours (see Aura in ui/Theme.kt).
 */
object Palettes {
    class P(
        val ink: Int,         // the scene's base colour
        val lift: Int,        // the soft glow top left
        val lift2: Int,       // the soft glow bottom right, also what small glass controls refract when they sit on the scene
        val card: Int,
        val sheet: Int,       // sheets (editor, city picker)
        val text: Int,
        val muted: Int,
        val accent: Int,      // shapes: ring, toggle track, button fill
        val accentText: Int,  // the accent when it has to be read as text
        val onAccent: Int,    // text on top of an accent fill
        val mint: Int,        // on / good, as text
        val rose: Int,        // off / danger, as text
    )

    private fun c(rgb: Int) = 0xFF000000.toInt() or rgb

    val DARK = P(
        ink = c(0x090B10), lift = c(0x1A2030), lift2 = c(0x121722), card = c(0x181D2A), sheet = c(0x151A26),
        text = c(0xECEEF3), muted = c(0x9AA0AE), accent = c(0xFFB07A), accentText = c(0xFFB07A), onAccent = c(0x1C1208),
        mint = c(0x6FE3A0), rose = c(0xFF8A80),
    )

    /**
     * Light mode, built for light and not turned over from dark: the grey is iOS's own grouped-list grey (F2F2F7) and the accent is the
     * blue of iOS 26, 0088FF, the value the glass library itself uses for light mode (its dark mode uses 0091FF). Dark mode keeps its orange.
     * 0088FF is 3.1 on this grey, so it clears the 3:1 that shapes need. It is NOT readable as small text (2.9 on the grey), so blue
     * words use the deeper 0066D6 (4.6). White text on a tinted glass button is lighter than white on flat blue, see [glassFill].
     */
    val LIGHT = P(
        ink = c(0xF2F2F7), lift = c(0xFBFBFD), lift2 = c(0xF2F4F8), card = c(0xFFFFFF), sheet = c(0xF7F8FB),
        text = c(0x13161D), muted = c(0x586070), accent = c(0x0088FF), accentText = c(0x0066D6), onAccent = c(0xFFFFFF),
        mint = c(0x0B7A41), rose = c(0xB8322A),
    )

    /**
     * What a tinted glass button really shows. The library draws the tint at 75% over whatever is behind the button, so the button is
     * lighter than the flat accent. This is the colour the button text has to be readable on. An estimate: the real thing also blurs
     * and refracts the backdrop, which does not change a plain light or dark scene much.
     */
    fun glassFill(tint: Int, behind: Int): Int {
        fun mix(shift: Int): Int = (((tint shr shift) and 255) * 0.75 + ((behind shr shift) and 255) * 0.25).roundToInt().coerceIn(0, 255)
        return c((mix(16) shl 16) or (mix(8) shl 8) or mix(0))
    }

    private fun channel(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }

    fun luminance(argb: Int): Double =
        0.2126 * channel((argb shr 16) and 255) + 0.7152 * channel((argb shr 8) and 255) + 0.0722 * channel(argb and 255)

    /** WCAG contrast ratio, 1 (identical) to 21 (black on white). Body text should reach 4.5, shapes 3. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /**
     * Which palette to show. [mode] is the person's choice: 0 follows the phone, 1 dark, 2 light.
     * Every screen, the ring included, uses the same answer. A per-screen override cannot work: the theme is one shared object.
     */
    fun useDark(mode: Int, systemDark: Boolean): Boolean = mode == 1 || (mode != 2 && systemDark)
}
