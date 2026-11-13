package dev.aalman.budstikke.ui

import androidx.compose.runtime.Composable
import dev.aalman.budstikke.ui.theme.LocalReducedMotion
import dev.aalman.budstikke.data.Message
import java.util.concurrent.TimeUnit

/** Where a message sits in its run of messages from the same side. */
data class BubblePlace(
    /** A time label goes above it: it starts the conversation or follows a long pause. */
    val timeLabel: Boolean,
    /** It follows a message from the same side, so the two are drawn as one group. */
    val joinedAbove: Boolean,
    /** The next message joins it, so its time and state are left to the last of the group. */
    val joinedBelow: Boolean,
)

/** A pause this long between two messages starts a new group under a time label. */
val GROUP_PAUSE_MILLIS: Long = TimeUnit.HOURS.toMillis(1)

/** The place of each of [messages], given oldest first, in the same order. */
fun bubblePlaces(messages: List<Message>): List<BubblePlace> {
    val labels = messages.mapIndexed { i, m -> i == 0 || m.dateMillis - messages[i - 1].dateMillis >= GROUP_PAUSE_MILLIS }
    val joined = messages.mapIndexed { i, m -> i > 0 && !labels[i] && m.isOutgoing == messages[i - 1].isOutgoing }
    return messages.indices.map { i -> BubblePlace(labels[i], joined[i], joined.getOrElse(i + 1) { false }) }
}

/** Whether motion is turned down: accessibility mode's reduced motion, or animations off in the system. */
@Composable
fun rememberReducedMotion(): Boolean = LocalReducedMotion.current
