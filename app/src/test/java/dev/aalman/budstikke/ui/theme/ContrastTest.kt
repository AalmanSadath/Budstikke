package dev.aalman.budstikke.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import dev.aalman.budstikke.ui.accentSwatches
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every BifroSSH palette, with its own accent and with every offered swatch, checked
 * against WCAG contrast: text on the accent and danger colours at 4.5:1 always (R22), and
 * all text and controls at 7:1 in high-contrast mode (R20). Wallpaper colours come from
 * the phone at run time and cannot be listed here; a picked accent and high contrast go
 * through the same functions on them.
 */
class ContrastTest {

    private val palettes = listOf(
        Triple("dark", BifrosshDark, true),
        Triple("light", BifrosshLight, false),
        Triple("amoled", BifrosshAmoled, true),
    )

    private fun schemes(): List<Triple<String, ColorScheme, Boolean>> = palettes.flatMap { (name, scheme, dark) ->
        listOf(Triple(name, scheme, dark)) + accentSwatches.map { Triple("$name+${toHex(it)}", scheme.withAccent(it), dark) }
    }

    private fun check(failures: MutableList<String>, what: String, fg: Color, bg: Color, min: Double) {
        val c = contrast(fg, bg)
        if (c < min) failures += "$what: %.2f < $min".format(c)
    }

    @Test
    fun textOnAccentAndDangerIsReadableInEveryTheme() {
        val failures = mutableListOf<String>()
        for ((name, s, _) in schemes()) {
            check(failures, "$name onPrimary/primary", s.onPrimary, s.primary, NORMAL_CONTRAST)
            check(failures, "$name onError/error", s.onError, s.error, NORMAL_CONTRAST)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun anyPickedAccentGetsReadableText() {
        val failures = mutableListOf<String>()
        for (rgb in 0..0xFFFFFF step 0x010307) {
            val s = BifrosshDark.withAccent(rgb)
            check(failures, toHex(rgb), s.onPrimary, s.primary, NORMAL_CONTRAST)
        }
        assertTrue(failures.take(20).joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun highContrastReachesSevenToOne() {
        val failures = mutableListOf<String>()
        for ((name, base, dark) in schemes()) {
            val s = base.highContrast(dark)
            val surfaces = mapOf(
                "background" to s.background, "surface" to s.surface, "container" to s.surfaceContainer,
                "containerHigh" to s.surfaceContainerHigh, "containerHighest" to s.surfaceContainerHighest,
            )
            for ((surface, bg) in surfaces) {
                check(failures, "$name onSurface/$surface", s.onSurface, bg, HIGH_CONTRAST)
                check(failures, "$name onSurfaceVariant/$surface", s.onSurfaceVariant, bg, HIGH_CONTRAST)
                check(failures, "$name primary/$surface", s.primary, bg, HIGH_CONTRAST)
                check(failures, "$name error/$surface", s.error, bg, HIGH_CONTRAST)
            }
            check(failures, "$name onPrimary/primary", s.onPrimary, s.primary, HIGH_CONTRAST)
            check(failures, "$name onError/error", s.onError, s.error, HIGH_CONTRAST)
            check(failures, "$name onErrorContainer/errorContainer", s.onErrorContainer, s.errorContainer, HIGH_CONTRAST)
            check(failures, "$name onTertiaryContainer/tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer, HIGH_CONTRAST)
            check(failures, "$name onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer, HIGH_CONTRAST)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
