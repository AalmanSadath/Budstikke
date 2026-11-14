package dev.aalman.budstikke.sms

import dev.aalman.budstikke.data.Message
import dev.aalman.budstikke.data.MessageState

/** The emoji offered when a message is long-pressed, in the order the bar shows them. */
val REACTION_EMOJI = listOf("👍", "😂", "❤️", "🔥", "😢", "😮", "🙏", "🎉")

/** A reaction sent as a text message: [emoji] put on, or taken off, the message quoted. */
data class ReactionText(val emoji: String, val quote: String, val removed: Boolean)

/**
 * The SMS that tells the other phone about a reaction, in the wording Google Messages uses
 * with phones that do not have RCS: `Reacted 👍 to "…"`, or `Removed 👍 from "…"`. Long
 * messages are quoted by their start, so the reaction stays one SMS.
 */
fun reactionText(emoji: String, body: String, removed: Boolean): String {
    val quote = quoteOf(body)
    return if (removed) "Removed $emoji from \"$quote\"" else "Reacted $emoji to \"$quote\""
}

private fun quoteOf(body: String): String {
    val flat = flatten(body)
    if (flat.length <= QUOTE_LENGTH) return flat
    // Never cut an emoji or other character made of two halves in two.
    val end = if (flat[QUOTE_LENGTH - 1].isHighSurrogate()) QUOTE_LENGTH - 1 else QUOTE_LENGTH
    return flat.substring(0, end).trimEnd() + "…"
}

private const val QUOTE_LENGTH = 40

private fun flatten(text: String) = text.replace(Regex("\\s+"), " ").trim()

private val GOOGLE = Regex("""^(Reacted|Removed) (\S+) (to|from) ["“](.*)["”]$""", RegexOption.DOT_MATCHES_ALL)
private val APPLE = Regex(
    """^(Liked|Loved|Disliked|Laughed at|Emphasized|Questioned) ["“](.*)["”]$""",
    RegexOption.DOT_MATCHES_ALL,
)
private val APPLE_REMOVED = Regex(
    """^Removed (a like|a heart|a dislike|a laugh|an exclamation|a question mark) from ["“](.*)["”]$""",
    RegexOption.DOT_MATCHES_ALL,
)

/** iPhones send their tapbacks as words; these are the emoji closest to each. */
private val APPLE_EMOJI = mapOf(
    "Liked" to "👍", "a like" to "👍",
    "Loved" to "❤️", "a heart" to "❤️",
    "Disliked" to "👎", "a dislike" to "👎",
    "Laughed at" to "😂", "a laugh" to "😂",
    "Emphasized" to "‼️", "an exclamation" to "‼️",
    "Questioned" to "❓", "a question mark" to "❓",
)

/**
 * Reads a reaction sent as text, by this app, Google Messages or an iPhone, or null when
 * [body] is an ordinary message.
 */
fun parseReaction(body: String): ReactionText? {
    val text = body.trim()
    GOOGLE.matchEntire(text)?.let { m ->
        val (verb, emoji, preposition, quote) = m.destructured
        val removed = verb == "Removed"
        if (removed != (preposition == "from")) return null
        return ReactionText(emoji, quote, removed)
    }
    APPLE.matchEntire(text)?.let { m ->
        val (verb, quote) = m.destructured
        return ReactionText(APPLE_EMOJI.getValue(verb), quote, removed = false)
    }
    APPLE_REMOVED.matchEntire(text)?.let { m ->
        val (what, quote) = m.destructured
        return ReactionText(APPLE_EMOJI.getValue(what), quote, removed = true)
    }
    return null
}

/** Whether [quote], as a reaction carries it, names a message with this [body]. */
fun quotes(quote: String, body: String): Boolean {
    val q = flatten(quote)
    val b = flatten(body)
    if (q.isEmpty()) return false
    // A long message is quoted by its start and an ellipsis.
    val prefix = q.removeSuffix("…").removeSuffix("...")
    return if (prefix.length < q.length) prefix.isNotEmpty() && b.startsWith(prefix.trimEnd()) else b == q
}

/**
 * A conversation with its reaction texts folded into the messages they react to.
 *
 * [shown] are the messages to draw as bubbles. [theirs] holds, by message id, the emoji the
 * other person has on each message. [carriers] holds, by message id, the reaction texts
 * that point at it, so they can be deleted along with it.
 */
data class FoldedMessages(
    val shown: List<Message>,
    val theirs: Map<Long, String>,
    val carriers: Map<Long, List<Long>>,
)

/**
 * Folds reaction texts, oldest first in [messages], into the newest earlier message each
 * one quotes. A received one sets or clears the other person's emoji there. A sent one
 * only disappears, since this phone's own reactions are kept by the app. A reaction text
 * that quotes nothing in the conversation, and a sent one that failed, stay as messages.
 */
fun foldReactions(messages: List<Message>): FoldedMessages {
    val shown = ArrayList<Message>(messages.size)
    val theirs = HashMap<Long, String>()
    val carriers = HashMap<Long, MutableList<Long>>()
    for (message in messages) {
        val reaction = if (message.state == MessageState.Failed) null else parseReaction(message.body)
        val target = reaction?.let { r -> shown.lastOrNull { quotes(r.quote, it.body) } }
        if (reaction == null || target == null) {
            shown += message
            continue
        }
        carriers.getOrPut(target.id) { mutableListOf() } += message.id
        if (message.isOutgoing) continue
        if (reaction.removed) {
            if (theirs[target.id] == reaction.emoji) theirs.remove(target.id)
        } else {
            theirs[target.id] = reaction.emoji
        }
    }
    return FoldedMessages(shown, theirs, carriers)
}

/**
 * Whether a message is a reaction this phone sent, which the conversation list passes over
 * for its snippet. A failed one stays, as the conversation still shows it.
 */
fun isSentReaction(body: String, outgoing: Boolean, failed: Boolean): Boolean =
    outgoing && !failed && parseReaction(body) != null

/**
 * Whether a reaction may go out as an SMS to [address]: only a full phone number. A sender
 * name cannot be replied to, and a short code is a service that reads what it is sent as a
 * command and may charge for it.
 */
fun canSendReactionTo(address: String): Boolean =
    address.all { it.isDigit() || it in "+ -()." } && address.count(Char::isDigit) >= MIN_PHONE_DIGITS

private const val MIN_PHONE_DIGITS = 7
