package dev.aalman.budstikke.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

/** WCAG's bar for text in high-contrast mode, and its usual bar for body text. */
const val HIGH_CONTRAST = 7.0
const val NORMAL_CONTRAST = 4.5

internal fun Color.rgb(): Int = toArgb() and 0xFFFFFF

fun contrast(a: Color, b: Color): Double = contrast(a.rgb(), b.rgb())

/**
 * [colour] moved, a step at a time, towards [towards] until it stands out by at least
 * [min] against every one of [against], or as far as it can go.
 */
internal fun strengthen(colour: Color, towards: Color, against: List<Color>, min: Double): Color {
    var c = colour
    var step = 0
    while (against.any { contrast(c, it) < min } && step < 20) {
        step++
        c = lerp(colour, towards, step / 20f)
    }
    return c
}

/**
 * The scheme with every text and control colour pushed to at least 7:1 against the
 * surfaces it is drawn on: plain text to pure white or black, quieter text, the accent and
 * the danger colour towards them, and the colour written on the accent and danger chosen
 * again for what they became.
 */
fun ColorScheme.highContrast(dark: Boolean): ColorScheme {
    val ink = if (dark) Color.White else Color.Black
    val surfaces = listOf(background, surface, surfaceContainerLowest, surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest)
    val accent = strengthen(primary, ink, surfaces, HIGH_CONTRAST)
    val danger = strengthen(error, ink, surfaces, HIGH_CONTRAST)
    val quiet = strengthen(onSurfaceVariant, ink, surfaces, HIGH_CONTRAST)
    val onAccentColour = Color(0xFF000000 or onAccent(accent.rgb()).toLong())
    val onDangerColour = Color(0xFF000000 or onAccent(danger.rgb()).toLong())
    return copy(
        primary = accent,
        onPrimary = onAccentColour,
        secondary = accent,
        onSecondary = onAccentColour,
        primaryContainer = surfaceContainerHigh,
        onPrimaryContainer = ink,
        secondaryContainer = surfaceContainerHighest,
        onSecondaryContainer = ink,
        tertiaryContainer = accent,
        onTertiaryContainer = onAccentColour,
        onBackground = ink,
        onSurface = ink,
        onSurfaceVariant = quiet,
        outline = quiet,
        outlineVariant = quiet,
        error = danger,
        onError = onDangerColour,
        errorContainer = surfaceContainerHigh,
        onErrorContainer = danger,
        inversePrimary = accent,
        surfaceTint = accent,
    )
}
