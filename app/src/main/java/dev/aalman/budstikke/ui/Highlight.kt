package dev.aalman.budstikke.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import dev.aalman.budstikke.data.matchRanges

/** [text] with every occurrence of [query] in [style], as find-in-page marks them. */
internal fun markMatches(text: String, query: String?, style: SpanStyle): AnnotatedString {
    val q = query?.trim()
    if (q.isNullOrEmpty()) return AnnotatedString(text)
    val ranges = matchRanges(text, q)
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        ranges.forEach { addStyle(style, it.first, it.last + 1) }
    }
}
