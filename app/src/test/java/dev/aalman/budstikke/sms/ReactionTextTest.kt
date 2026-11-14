package dev.aalman.budstikke.sms

import dev.aalman.budstikke.data.Message
import dev.aalman.budstikke.data.MessageState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionTextTest {

    private fun received(id: Long, body: String) = Message(id, body, id * 1000, MessageState.Received, 1)
    private fun sent(id: Long, body: String, state: MessageState = MessageState.Sent) = Message(id, body, id * 1000, state, 1)

    @Test
    fun writesReactionsInGoogleWording() {
        assertEquals("Reacted 👍 to \"See you at 6\"", reactionText("👍", "See you at 6", removed = false))
        assertEquals("Removed 👍 from \"See you at 6\"", reactionText("👍", "See you at 6", removed = true))
    }

    @Test
    fun quotesLongMessagesByTheirStart() {
        val body = "This is a long message that goes on well past forty characters"
        val text = reactionText("🎉", body, removed = false)
        assertEquals("Reacted 🎉 to \"This is a long message that goes on well…\"", text)
        assertTrue(quotes(parseReaction(text)!!.quote, body))
    }

    @Test
    fun neverCutsAnEmojiInHalf() {
        val body = "a".repeat(39) + "😀 and more"
        val quote = parseReaction(reactionText("👍", body, removed = false))!!.quote
        assertFalse(quote.indices.any { quote[it].isHighSurrogate() && quote.getOrNull(it + 1)?.isLowSurrogate() != true })
        assertTrue(quotes(quote, body))
    }

    @Test
    fun readsGoogleAndAppleReactions() {
        assertEquals(ReactionText("❤️", "Dinner?", false), parseReaction("Reacted ❤️ to \"Dinner?\""))
        assertEquals(ReactionText("❤️", "Dinner?", true), parseReaction("Removed ❤️ from “Dinner?”"))
        assertEquals(ReactionText("👍", "Dinner?", false), parseReaction("Liked “Dinner?”"))
        assertEquals(ReactionText("😂", "Dinner?", false), parseReaction("Laughed at \"Dinner?\""))
        assertEquals(ReactionText("❤️", "Dinner?", true), parseReaction("Removed a heart from “Dinner?”"))
    }

    @Test
    fun leavesOrdinaryMessagesAlone() {
        assertNull(parseReaction("Reacted badly to the news"))
        assertNull(parseReaction("Reacted 👍 from \"x\""))
        assertNull(parseReaction("I liked \"the film\""))
        assertNull(parseReaction("Loved it"))
    }

    @Test
    fun foldsReceivedReactionsIntoTheirMessage() {
        val folded = foldReactions(
            listOf(
                sent(1, "Dinner at 7?"),
                received(2, "Reacted 👍 to \"Dinner at 7?\""),
                received(3, "Sure"),
            ),
        )
        assertEquals(listOf(1L, 3L), folded.shown.map { it.id })
        assertEquals(mapOf(1L to "👍"), folded.theirs)
        assertEquals(mapOf(1L to listOf(2L)), folded.carriers)
    }

    @Test
    fun aLaterReactionReplacesAndARemovalClears() {
        val messages = listOf(
            sent(1, "Dinner at 7?"),
            received(2, "Reacted 👍 to \"Dinner at 7?\""),
            received(3, "Reacted ❤️ to \"Dinner at 7?\""),
        )
        assertEquals(mapOf(1L to "❤️"), foldReactions(messages).theirs)
        val removed = foldReactions(messages + received(4, "Removed ❤️ from \"Dinner at 7?\""))
        assertEquals(emptyMap<Long, String>(), removed.theirs)
        assertEquals(listOf(1L), removed.shown.map { it.id })
    }

    @Test
    fun picksTheNewestEarlierMessageQuoted() {
        val folded = foldReactions(
            listOf(
                sent(1, "ok"),
                sent(2, "ok"),
                received(3, "Reacted 🔥 to \"ok\""),
                sent(4, "ok"),
            ),
        )
        assertEquals(mapOf(2L to "🔥"), folded.theirs)
    }

    @Test
    fun hidesSentReactionsWithoutCountingThem() {
        val folded = foldReactions(listOf(received(1, "Home now"), sent(2, "Reacted 🙏 to \"Home now\"")))
        assertEquals(listOf(1L), folded.shown.map { it.id })
        assertEquals(emptyMap<Long, String>(), folded.theirs)
        assertEquals(mapOf(1L to listOf(2L)), folded.carriers)
    }

    @Test
    fun keepsReactionsThatQuoteNothingAndFailedOnes() {
        val folded = foldReactions(
            listOf(
                received(1, "Reacted 👍 to \"something from before\""),
                received(2, "Home now"),
                sent(3, "Reacted 🙏 to \"Home now\"", MessageState.Failed),
            ),
        )
        assertEquals(listOf(1L, 2L, 3L), folded.shown.map { it.id })
    }

    @Test
    fun onlySentReactionsLeaveTheListSnippet() {
        val reaction = "Reacted 👍 to \"Best. See you soon!\""
        assertTrue(isSentReaction(reaction, outgoing = true, failed = false))
        assertFalse(isSentReaction(reaction, outgoing = false, failed = false))
        assertFalse(isSentReaction(reaction, outgoing = true, failed = true))
        assertFalse(isSentReaction("Best. See you soon!", outgoing = true, failed = false))
    }

    @Test
    fun sendsReactionsOnlyToPhoneNumbers() {
        assertTrue(canSendReactionTo("+47 412 34 567"))
        assertTrue(canSendReactionTo("0412345678"))
        assertFalse(canSendReactionTo("BANK"))
        assertFalse(canSendReactionTo("12345"))
        assertFalse(canSendReactionTo("2210"))
    }
}
