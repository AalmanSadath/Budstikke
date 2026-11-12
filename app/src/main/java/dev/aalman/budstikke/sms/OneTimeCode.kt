package dev.aalman.budstikke.sms

/** A one-time code found in a message: where it sits in the text, and the code to copy. */
data class OneTimeCode(val range: IntRange, val code: String)

/**
 * The one-time code in [text], or null when there is none.
 *
 * A message counts only when it says what the code is for: a word such as "code", "OTP",
 * "PIN", "verification" or "passcode", in English or a few other languages. The code itself
 * is 4 to 8 digits, two groups of 3 digits split by a space or a dash ("384-112"), or 4 to 8
 * capital letters and digits with at least one of each ("A7K2Q9").
 *
 * Numbers that are something else are skipped: amounts (next to a currency sign or code,
 * or with decimals), dates and times (joined to other numbers by "/", ".", ":" or ","),
 * percentages, order numbers after "#", and pieces of phone numbers (after "+", or next to
 * more digits across a single space or dash). When several codes remain, the one nearest a
 * keyword wins.
 */
fun findOneTimeCode(text: String): OneTimeCode? {
    val keywords = KEYWORD.findAll(text).map { it.range }.toList()
    if (keywords.isEmpty()) return null
    return CANDIDATE.findAll(text)
        .filter { isCode(text, it.range) }
        .minByOrNull { candidate -> keywords.minOf { distance(it, candidate.range) } }
        ?.let { OneTimeCode(it.range, it.value.filter(Char::isLetterOrDigit)) }
}

private val KEYWORD = Regex(
    "(?i)(?<![\\p{L}])(" +
        "code|codes|otp|one[- ]?time|passcode|password|pin|verification|verify|auth|authentication|" +
        "2fa|login|log in|sign[- ]?in|security|token|" +
        // Norwegian, Swedish, Danish, German, Spanish, French, Portuguese.
        "kode|engangskode|kod|engångskod|bekræftelseskode|bestätigungscode|código|codigo|clave|" +
        "mot de passe|senha" +
        ")(?![\\p{L}])",
)

private val CANDIDATE = Regex(
    "(?<![\\p{L}\\p{N}])(" +
        "\\d{3}[ -]\\d{3}|" +
        "\\d{4,8}|" +
        "(?=[A-Z0-9]*\\d)(?=[A-Z0-9]*[A-Z])[A-Z0-9]{4,8}" +
        ")(?![\\p{L}\\p{N}])",
)

private const val CURRENCY_SIGNS = "$€£¥₹₩₽¢"
private const val CURRENCIES = "rs|inr|usd|eur|gbp|nok|sek|dkk|kr|aud|cad|chf|jpy|rp|php"
private val CURRENCY_BEFORE = Regex("(?i)(?:^|[^\\p{L}])($CURRENCIES)\\.?\\s*$")
private val CURRENCY_AFTER = Regex("(?i)^\\s*($CURRENCIES)(?![\\p{L}])")

private fun isCode(text: String, range: IntRange): Boolean {
    val before = text.getOrNull(range.first - 1)
    val after = text.getOrNull(range.last + 1)
    // An order number, a phone number, an amount or a percentage.
    if (before == '#' || before == '+' || (before != null && before in CURRENCY_SIGNS)) return false
    if (after == '%' || (after != null && after in CURRENCY_SIGNS)) return false
    if (CURRENCY_BEFORE.containsMatchIn(text.substring(maxOf(0, range.first - 6), range.first))) return false
    if (CURRENCY_AFTER.containsMatchIn(text.substring(range.last + 1, minOf(text.length, range.last + 7)))) return false
    // Joined to another number: a date, a time, a decimal, a thousands group.
    if (before != null && before in JOINERS && text.getOrNull(range.first - 2)?.isDigit() == true) return false
    if (after != null && after in JOINERS && text.getOrNull(range.last + 2)?.isDigit() == true) return false
    // Part of a longer number written in groups, such as "0412 345 678".
    if (before != null && before in GROUPERS && text.getOrNull(range.first - 2)?.isDigit() == true) return false
    if (after != null && after in GROUPERS && text.getOrNull(range.last + 2)?.isDigit() == true) return false
    return true
}

private const val JOINERS = "/.:,"
private const val GROUPERS = " -"

private fun distance(a: IntRange, b: IntRange): Int = when {
    a.last < b.first -> b.first - a.last
    b.last < a.first -> a.first - b.last
    else -> 0
}
