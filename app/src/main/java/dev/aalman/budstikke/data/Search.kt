package dev.aalman.budstikke.data

/** A conversation found by search. */
data class SearchResult(
    val conversation: Conversation,
    /** The newest of its messages whose text matched, when that is why it was found. */
    val message: MatchedMessage?,
)

data class MatchedMessage(val id: Long, val body: String, val dateMillis: Long, val fromMe: Boolean)

/**
 * Escapes [text] for a LIKE pattern with `ESCAPE '\'`, so "%" and "_" typed into the
 * search match themselves instead of anything.
 */
internal fun escapeLike(text: String): String = buildString {
    for (c in text) {
        if (c == '\\' || c == '%' || c == '_') append('\\')
        append(c)
    }
}

/**
 * Whether [query] names the conversation: its contact name or sender name holds it, or,
 * for a query of three or more digits, the number does once spacing and punctuation are
 * ignored on both sides ("0412 345" finds "0412345678").
 */
internal fun matchesConversation(title: String, address: String, query: String): Boolean {
    if (title.contains(query, ignoreCase = true) || address.contains(query, ignoreCase = true)) return true
    val digits = query.filter(Char::isDigit)
    val numberLike = digits.length >= 3 && query.all { it.isDigit() || it in NUMBER_PUNCTUATION }
    return numberLike && address.filter(Char::isDigit).contains(digits)
}

private const val NUMBER_PUNCTUATION = "+ -()."

/**
 * The part of [body] to show for a match on [query]: the whole text when the match is near
 * the start, otherwise from a little before the match with a leading ellipsis, so the match
 * is not cut off when the row shows one line.
 */
internal fun excerpt(body: String, query: String, lead: Int = EXCERPT_LEAD): String {
    val flat = body.replace('\n', ' ')
    val at = flat.indexOf(query, ignoreCase = true)
    if (at <= lead) return flat
    // Start at a word boundary within the lead, when there is one.
    var start = at - lead
    val space = flat.indexOf(' ', start)
    if (space in start until at) start = space + 1
    return "…" + flat.substring(start)
}

private const val EXCERPT_LEAD = 24

/** Where [query] occurs in [text], ignoring case, without overlaps. */
internal fun matchRanges(text: String, query: String): List<IntRange> {
    if (query.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var from = 0
    while (true) {
        val at = text.indexOf(query, from, ignoreCase = true)
        if (at < 0) return ranges
        ranges += at until at + query.length
        from = at + query.length
    }
}
