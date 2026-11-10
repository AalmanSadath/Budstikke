package dev.aalman.budstikke.ui.theme

import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * A chosen accent colour and the colours it implies, ported from BifroSSH's accent.ts.
 *
 * The accent is only part of it: every palette also hand-picks the colour written on top
 * of it. Once the accent is whatever the user picks, that has to be derived, or a pale
 * accent gets white text on it and the text disappears.
 */

/** Parses "#rgb" or "#rrggbb", with or without the "#", into 0xRRGGBB. Null for anything else. */
fun parseHex(hex: String): Int? {
    val digits = hex.trim().removePrefix("#")
    if (!digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    val full = when (digits.length) {
        3 -> digits.map { "$it$it" }.joinToString("")
        6 -> digits
        else -> return null
    }
    return full.toInt(16)
}

fun toHex(rgb: Int): String = "#%06x".format(rgb and 0xFFFFFF)

/** WCAG relative luminance of 0xRRGGBB. */
fun luminance(rgb: Int): Double {
    fun channel(shift: Int): Double {
        val s = ((rgb shr shift) and 0xFF) / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

fun contrast(a: Int, b: Int): Double {
    val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
    return (hi + 0.05) / (lo + 0.05)
}

/** The near-black every BifroSSH palette uses for text on a light field, and white. */
const val INK = 0x0D1117
const val PAPER = 0xFFFFFF

/**
 * What to write on [accent]: whichever of near-black and white stands out further against
 * it. A mid green and a mid blue of the same lightness do not want the same text on them,
 * so this compares contrast rather than using a lightness threshold.
 */
fun onAccent(accent: Int): Int = if (contrast(accent, INK) >= contrast(accent, PAPER)) INK else PAPER

/**
 * A shade 12% of the way from [accent] away from its text colour: a dark accent lightens
 * and a light one darkens. Used for the accent's container colour.
 */
fun accentShade(accent: Int): Int {
    val towards = if (onAccent(accent) == INK) PAPER else INK
    fun mix(shift: Int): Int {
        val a = (accent shr shift) and 0xFF
        val t = (towards shr shift) and 0xFF
        return (a + (t - a) * 0.12).roundToInt().coerceIn(0, 255) shl shift
    }
    return mix(16) or mix(8) or mix(0)
}
