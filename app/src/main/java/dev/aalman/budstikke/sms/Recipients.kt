package dev.aalman.budstikke.sms

import android.net.Uri

/** The numbers in an sms:, smsto:, mms: or mmsto: URI, e.g. "sms:+4712345678;+4787654321?body=hi". */
fun recipientsOf(uri: Uri): List<String> = parseRecipients(uri.schemeSpecificPart.orEmpty())

/** Splits the part after the scheme into numbers, dropping any query. */
internal fun parseRecipients(schemeSpecificPart: String): List<String> =
    schemeSpecificPart
        .substringBefore('?')
        .split(',', ';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/** The body= query of an sms: URI, if any. */
internal fun bodyOf(schemeSpecificPart: String): String? =
    schemeSpecificPart.substringAfter('?', "")
        .split('&')
        .firstOrNull { it.startsWith("body=") }
        ?.removePrefix("body=")
        ?.let { Uri.decode(it) }

/** The recipient being typed into a "To" field: whatever follows the last separator. */
internal fun typedRecipient(field: String): String =
    field.substring(maxOf(field.lastIndexOf(','), field.lastIndexOf(';')) + 1).trim()

/**
 * The "To" field with the recipient being typed replaced by [number], and a separator after
 * it so the next one can be typed straight away.
 */
internal fun withRecipient(field: String, number: String): String {
    val cut = maxOf(field.lastIndexOf(','), field.lastIndexOf(';'))
    val before = if (cut >= 0) field.substring(0, cut + 1).trimEnd() + " " else ""
    return "$before$number, "
}

/**
 * Whether [text] can be sent to as it is: a phone number, possibly written with spaces,
 * dashes, dots or brackets. A name typed and not picked from the suggestions is not.
 */
internal fun looksLikeNumber(text: String): Boolean =
    text.any(Char::isDigit) && text.all { it.isDigit() || it in "+ -()." }
