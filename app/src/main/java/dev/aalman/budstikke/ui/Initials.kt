package dev.aalman.budstikke.ui

/**
 * One or two letters for a contact's avatar: the first letters of the first and last words
 * of the name ("Carlos Diaz" gives "CD", "Dad" gives "D"). Words that do not start with a
 * letter or digit, such as "(Work)", are skipped. Null when nothing usable is left.
 */
fun initialsOf(name: String): String? {
    val words = name.trim()
        .split(Regex("\\s+"))
        .filter { it.firstOrNull()?.isLetterOrDigit() == true }
    val first = words.firstOrNull() ?: return null
    val last = words.drop(1).lastOrNull()
    return buildString {
        append(first.codePointAt(0).let(Character::toChars))
        if (last != null) append(last.codePointAt(0).let(Character::toChars))
    }.uppercase()
}
