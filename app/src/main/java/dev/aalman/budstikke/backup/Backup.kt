package dev.aalman.budstikke.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.telephony.SubscriptionManager
import dev.aalman.budstikke.data.BlockList
import dev.aalman.budstikke.data.ConversationFlags
import dev.aalman.budstikke.data.Drafts
import dev.aalman.budstikke.data.Flags
import dev.aalman.budstikke.data.Locks
import dev.aalman.budstikke.data.Marks
import dev.aalman.budstikke.data.Outgoing
import dev.aalman.budstikke.data.restoredType
import dev.aalman.budstikke.sms.activeSims

/** Work done so far out of the whole, for a progress bar. [total] is 0 while it is not known yet. */
typealias Progress = (done: Int, total: Int) -> Unit

/** What writing messages into the system store did. */
data class InsertResult(val added: Int, val alreadyHere: Int)

/** What a restore did. */
data class RestoreResult(
    val added: Int,
    val alreadyHere: Int,
    /** Scheduled messages left out because their time passed while they were in the backup. */
    val scheduledPast: Int,
)

/**
 * Writes this phone's messages and the app's own data to a PIN-sealed file, and reads them
 * back. Files are reached through the system file picker's URIs, so they can sit on an SD
 * card or in a cloud provider's folder. Everything here does disk work; run it off the main
 * thread.
 */
class Backup(context: Context) {

    private val context = context.applicationContext
    private val resolver = this.context.contentResolver

    /** Saves a backup to [uri], then reads it back and opens it to check it was written whole. Returns the message count. */
    fun write(uri: Uri, pin: CharArray, progress: Progress): Int {
        val content = collect(progress)
        val plain = BackupFormat.write(content)
        val sealed = BackupCrypto.seal(plain, pin)
        resolver.openOutputStream(uri, "wt")?.use { it.write(sealed) } ?: throw java.io.IOException("No output stream")
        val back = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw java.io.IOException("No input stream")
        if (!BackupCrypto.open(back, pin).contentEquals(plain)) throw java.io.IOException("Backup did not read back the same")
        return content.messages.size
    }

    /**
     * Opens the backup at [uri] and adds what it holds. The whole file is opened and checked
     * before anything is written, so a wrong PIN or a damaged file changes nothing.
     */
    fun restore(uri: Uri, pin: CharArray, progress: Progress): RestoreResult {
        val sealed = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw java.io.IOException("No input stream")
        val content = BackupFormat.read(BackupCrypto.open(sealed, pin))
        return apply(content, progress)
    }

    private fun collect(progress: Progress): BackupContent {
        progress(0, 0)
        val locks = Locks.get(context).state()
        val marks = Marks.get(context).state()
        val threadAddress = HashMap<Long, String>()
        val messages = ArrayList<BackupSms>()
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                Telephony.Sms.DATE, Telephony.Sms.DATE_SENT, Telephony.Sms.TYPE, Telephony.Sms.READ,
                Telephony.Sms.SEEN, Telephony.Sms.STATUS, Telephony.Sms.SUBSCRIPTION_ID,
            ),
            null, null, "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val address = c.getString(2) ?: continue
                threadAddress.putIfAbsent(c.getLong(1), address)
                messages += BackupSms(
                    address = address,
                    body = c.getString(3).orEmpty(),
                    dateMillis = c.getLong(4),
                    dateSentMillis = c.getLong(5),
                    type = c.getInt(6),
                    read = c.getInt(7) != 0,
                    seen = c.getInt(8) != 0,
                    status = c.getInt(9),
                    subscriptionId = c.getInt(10),
                    locked = id in locks.messages,
                    starred = id in marks.starred,
                    reaction = marks.reactions[id],
                )
                if (messages.size % PROGRESS_STEP == 0) progress(messages.size, c.count)
            }
        }
        val flags = ConversationFlags.get(context).all()
        val drafts = Drafts.get(context).all()
        val outgoing = Outgoing.get(context).due(Long.MAX_VALUE).filter { !it.delayed }
        // A conversation known only by a draft or a schedule has no messages to name it.
        outgoing.forEach { threadAddress.putIfAbsent(it.threadId, it.address) }
        val conversations = (flags.keys + drafts.keys + locks.threads).mapNotNull { threadId ->
            val address = threadAddress[threadId] ?: return@mapNotNull null
            val f = flags[threadId] ?: Flags()
            BackupConversation(address, f.pinnedAt, f.archived, threadId in locks.threads, drafts[threadId])
        }
        return BackupContent(
            createdAtMillis = System.currentTimeMillis(),
            messages = messages,
            conversations = conversations,
            blocked = BlockList.get(context).all().map(::BackupBlocked),
            scheduled = outgoing.map { BackupScheduled(it.address, it.body, it.subscriptionId, it.sendAtMillis) },
        )
    }

    private fun apply(content: BackupContent, progress: Progress): RestoreResult {
        val (inserted, ids) = insertMessages(content.messages, progress)
        val marks = Marks.get(context)
        val locks = Locks.get(context)
        val markState = marks.state()
        content.messages.forEach { m ->
            val id = ids[m.key()] ?: return@forEach
            if (m.reaction != null && id !in markState.reactions) marks.setReaction(id, m.reaction)
        }
        marks.setStarred(content.messages.filter { it.starred }.mapNotNull { ids[it.key()] }, true)
        locks.setMessagesLocked(content.messages.filter { it.locked }.mapNotNull { ids[it.key()] }, true)

        val flagStore = ConversationFlags.get(context)
        val drafts = Drafts.get(context)
        val existingFlags = flagStore.all()
        for (c in content.conversations) {
            val threadId = runCatching { Telephony.Threads.getOrCreateThreadId(context, c.address) }.getOrNull() ?: continue
            // What is set on this phone already wins over the backup.
            if (existingFlags[threadId] == null && (c.pinnedAtMillis != null || c.archived)) {
                flagStore.restore(mapOf(threadId to Flags(c.pinnedAtMillis, c.archived)))
            }
            if (c.locked) locks.setThreadLocked(threadId, true)
            if (c.draft != null && drafts.get(threadId) == null) drafts.put(threadId, c.draft)
        }
        val blockList = BlockList.get(context)
        content.blocked.forEach { blockList.block(it.address) }

        // A scheduled message whose time has passed would go out the moment it was restored,
        // possibly long after it made sense, so it is left out.
        val now = System.currentTimeMillis()
        val outgoing = Outgoing.get(context)
        val waiting = outgoing.due(Long.MAX_VALUE).map { Triple(it.address, it.body, it.sendAtMillis) }.toSet()
        val sims = activeSims(context).map { it.subscriptionId }.toSet()
        var past = 0
        for (s in content.scheduled) {
            if (s.sendAtMillis <= now) {
                past++
                continue
            }
            if (Triple(s.address, s.body, s.sendAtMillis) in waiting) continue
            val threadId = runCatching { Telephony.Threads.getOrCreateThreadId(context, s.address) }.getOrNull() ?: continue
            // Another phone's SIMs have other ids; this one's default SIM sends it then.
            val sub = if (s.subscriptionId in sims) s.subscriptionId else SubscriptionManager.INVALID_SUBSCRIPTION_ID
            outgoing.add(threadId, s.address, s.body, sub, s.sendAtMillis, delayed = false)
        }
        return RestoreResult(inserted.added, inserted.alreadyHere, past)
    }

    /**
     * Writes [messages] into the system store, oldest first, skipping any that are there
     * already, so the same backup or import can be added twice without doubling anything.
     * Returns the counts and, for every message given, the id it now has in the store.
     */
    fun insertMessages(messages: List<BackupSms>, progress: Progress): Pair<InsertResult, Map<String, Long>> {
        progress(0, messages.size)
        val ids = HashMap<String, Long>()
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.BODY),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                ids[messageKey(c.getString(1).orEmpty(), c.getLong(2), isOutgoing(c.getInt(3)), c.getString(4).orEmpty())] = c.getLong(0)
            }
        }
        var added = 0
        var already = 0
        messages.sortedBy { it.dateMillis }.forEachIndexed { i, m ->
            val key = m.key()
            if (key in ids) {
                already++
            } else {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, m.address)
                    put(Telephony.Sms.BODY, m.body)
                    put(Telephony.Sms.DATE, m.dateMillis)
                    put(Telephony.Sms.DATE_SENT, m.dateSentMillis)
                    put(Telephony.Sms.TYPE, restoredType(m.type))
                    put(Telephony.Sms.READ, if (m.read) 1 else 0)
                    put(Telephony.Sms.SEEN, if (m.seen) 1 else 0)
                    put(Telephony.Sms.STATUS, m.status)
                    put(Telephony.Sms.SUBSCRIPTION_ID, m.subscriptionId)
                }
                val id = resolver.insert(Telephony.Sms.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull()
                if (id != null) {
                    ids[key] = id
                    added++
                }
            }
            if ((i + 1) % PROGRESS_STEP == 0 || i == messages.lastIndex) progress(i + 1, messages.size)
        }
        return InsertResult(added, already) to ids
    }

    private fun BackupSms.key() = messageKey(address, dateMillis, isOutgoing(type), body)

    private fun isOutgoing(type: Int) = type != Telephony.Sms.MESSAGE_TYPE_INBOX

    private companion object {
        const val PROGRESS_STEP = 25
    }
}
