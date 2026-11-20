package dev.aalman.budstikke.data

/** One person in a conversation besides this phone: their number, and their contact when saved. */
data class Member(val address: String, val contact: Contact? = null) {
    /** The contact's name, or the number or sender name when there is no saved contact. */
    val name: String get() = contact?.name ?: address

    /** What a group's title calls them: a contact's first name, or the number. */
    val shortName: String get() = contact?.name?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() } ?: address
}

/** What a conversation with [members] is called: one person's name, or a group's first names. */
fun membersTitle(members: List<Member>): String = when (members.size) {
    0 -> ""
    1 -> members.single().name
    else -> members.joinToString(", ") { it.shortName }
}

/** The member of [members] who is [address], however the number is written. */
fun List<Member>.memberFor(address: String): Member? = firstOrNull { sameSender(it.address, address) }

/**
 * Who a conversation is with, as one string, for the app's own tables, intents and saved
 * state. One person is their address unchanged, so everything saved before groups still
 * reads the same; a group's addresses are joined by a line break, which no address holds.
 */
object AddressList {
    private const val SEPARATOR = "\n"

    fun join(addresses: List<String>): String = addresses.joinToString(SEPARATOR)

    fun split(joined: String): List<String> = joined.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
}
