package dev.aalman.budstikke.sms

/** One PDU of an incoming SMS, reduced to the fields the app stores. */
data class SmsPart(
    val address: String?,
    val body: String?,
    val sentAtMillis: Long,
)

/** A whole incoming SMS, after its parts have been joined. */
data class IncomingSms(
    val address: String,
    val body: String,
    val sentAtMillis: Long,
)

/**
 * Joins the parts of one incoming SMS in the order the radio delivered them.
 *
 * A long message arrives as several PDUs in a single SMS_DELIVER broadcast, already in
 * order, all from the same sender. The sender and the sent time are taken from the first
 * part. Returns null when there is nothing to store: no parts, or no sender.
 */
fun joinParts(parts: List<SmsPart>): IncomingSms? {
    val first = parts.firstOrNull() ?: return null
    val address = first.address?.takeIf { it.isNotBlank() } ?: return null
    val body = parts.joinToString(separator = "") { it.body.orEmpty() }
    return IncomingSms(address = address, body = body, sentAtMillis = first.sentAtMillis)
}
