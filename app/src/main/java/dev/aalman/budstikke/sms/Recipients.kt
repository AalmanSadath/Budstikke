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
