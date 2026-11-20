package dev.aalman.budstikke.ui

/** What a link in a message opens: a web page, a new email, or the dialer. */
enum class LinkKind { Web, Email, Phone }

/** A link found in message text: where it is, what it is, and the URI it opens. */
data class TextLink(val range: IntRange, val kind: LinkKind, val target: String)

/**
 * The web addresses, email addresses and phone numbers in [text], in order, none
 * overlapping another or [avoid], the one-time code, which keeps its own tap. Written
 * without Android's Linkify, so plain unit tests can run it.
 *
 * Web addresses are found with `http://` or `https://`, with `www.`, or bare when they end
 * in a known top-level domain, so `posten.no/track` links and `report.pdf` does not.
 */
fun findLinks(text: String, avoid: IntRange? = null): List<TextLink> {
    val found = ArrayList<TextLink>()
    fun free(range: IntRange) = (avoid == null || !overlaps(range, avoid)) && found.none { overlaps(it.range, range) }
    fun web(regex: Regex, target: (String) -> String) = regex.findAll(text).forEach { m ->
        val trimmed = trimWeb(m.value)
        if (trimmed.isEmpty()) return@forEach
        val range = m.range.first until m.range.first + trimmed.length
        if (free(range)) found += TextLink(range, LinkKind.Web, target(trimmed))
    }
    web(SCHEME) { it }
    web(WWW) { "https://$it" }
    web(BARE) { "https://$it" }
    EMAIL.findAll(text).forEach { m ->
        if (free(m.range)) found += TextLink(m.range, LinkKind.Email, "mailto:${m.value}")
    }
    PHONE.findAll(text).forEach { m ->
        val value = m.value
        if (!looksLikePhone(value) || !free(m.range)) return@forEach
        found += TextLink(m.range, LinkKind.Phone, "tel:" + value.filter { it.isDigit() || it == '+' })
    }
    return found.sortedBy { it.range.first }
}

/**
 * The host a web link really goes to, for showing before it opens. Anything before an `@`
 * in the address is a user name, not the site: `https://bank.no@evil.example` goes to
 * `evil.example`.
 */
fun hostOf(target: String): String =
    target.substringAfter("://")
        .substringBefore('/').substringBefore('?').substringBefore('#')
        .substringAfterLast('@')
        .substringBefore(':')

/** Where in [target] the host of [hostOf] is, or null when it has none. */
fun hostRange(target: String): IntRange? {
    val start = target.indexOf("://").let { if (it < 0) return null else it + 3 }
    val authorityEnd = target.indexOfAny(charArrayOf('/', '?', '#'), start).let { if (it < 0) target.length else it }
    val hostStart = target.lastIndexOf('@', authorityEnd - 1).let { if (it >= start) it + 1 else start }
    val hostEnd = target.indexOf(':', hostStart).let { if (it in hostStart until authorityEnd) it else authorityEnd }
    return if (hostEnd > hostStart) hostStart until hostEnd else null
}

private fun overlaps(a: IntRange, b: IntRange) = a.first <= b.last && b.first <= a.last

/**
 * A web address without what follows it in the sentence: trailing punctuation, quotes, and
 * a closing bracket that has no opening one inside the address.
 */
internal fun trimWeb(raw: String): String {
    var s = raw
    while (s.isNotEmpty()) {
        val last = s.last()
        s = when {
            last in TRAILING -> s.dropLast(1)
            last == ')' && s.count { it == '(' } < s.count { it == ')' } -> s.dropLast(1)
            last == ']' && s.count { it == '[' } < s.count { it == ']' } -> s.dropLast(1)
            else -> return s
        }
    }
    return s
}

/**
 * Whether a run of digits and separators reads as a phone number: 7 to 15 digits, no two
 * separators together but a bracket and a space, balanced brackets, and not a date, a
 * decimal number or an amount grouped in thousands.
 */
internal fun looksLikePhone(value: String): Boolean {
    val digits = value.count(Char::isDigit)
    if (digits !in 7..15) return false
    if (value.count { it == '(' } != value.count { it == ')' }) return false
    if (Regex("[ .-][ .-]|\\([ .-]|[.-]\\(|\\)[.-]").containsMatchIn(value)) return false
    if (DATE.matches(value)) return false
    if (Regex("\\d+\\.\\d+").matches(value)) return false
    if (Regex("\\d{1,3}([ .]\\d{3})+").matches(value)) return false
    return true
}

private const val TRAILING = ".,;:!?'\"*"

private val TLDS = listOf(
    "com", "net", "org", "info", "biz", "io", "co", "me", "ly", "app", "dev", "edu", "gov", "xyz", "shop",
    "store", "online", "site", "link", "page", "tv", "gl", "gg", "ai",
    "no", "se", "dk", "fi", "is", "de", "uk", "nl", "fr", "es", "it", "pl", "eu", "us", "ca", "au", "nz",
    "in", "ch", "at", "be", "ie", "pt", "jp", "br", "mx", "za", "ru", "cz",
).sortedByDescending { it.length }.joinToString("|")

private val SCHEME = Regex("(?i)\\bhttps?://[^\\s<>\"]+")
private val WWW = Regex("(?i)(?<![\\w.@/-])www\\.[^\\s<>\"]+")
private val BARE = Regex(
    "(?i)(?<![\\w.@/-])(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+(?:$TLDS)(?![\\w-])(?:[/?#][^\\s<>\"]*)?",
)
private val EMAIL = Regex("(?i)(?<![\\w.+-])[a-z0-9._%+-]+@(?:[a-z0-9-]+\\.)+[a-z]{2,}(?![\\w-])")
private val PHONE = Regex("(?<![\\w+./-])\\+?[\\d(][\\d ().-]{5,24}\\d(?![\\w])")
private val DATE = Regex("\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}|\\d{1,2}[-./]\\d{1,2}[-./]\\d{2,4}")
