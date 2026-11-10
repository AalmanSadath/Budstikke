package dev.aalman.budstikke.notify

/**
 * The conversation on screen right now, if any. A message arriving in it is seen as it
 * lands, so it gets no notification.
 */
object ActiveConversation {
    @Volatile
    var threadId: Long? = null
}
