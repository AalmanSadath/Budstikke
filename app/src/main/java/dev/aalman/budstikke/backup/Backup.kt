package dev.aalman.budstikke.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.telephony.SubscriptionManager
import dev.aalman.budstikke.data.AddressList
import dev.aalman.budstikke.data.BlockList
import dev.aalman.budstikke.data.ConversationFlags
import dev.aalman.budstikke.data.Drafts
import dev.aalman.budstikke.data.Flags
import dev.aalman.budstikke.data.Locks
import dev.aalman.budstikke.data.Marks
import dev.aalman.budstikke.data.Outgoing
import dev.aalman.budstikke.data.MessageKey
import dev.aalman.budstikke.data.MmsReader
import dev.aalman.budstikke.data.restoredType
import dev.aalman.budstikke.mms.pdu.PduHeaders
import dev.aalman.budstikke.sms.MmsStore
import dev.aalman.budstikke.sms.activeSims
import java.io.File
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

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

/** What an import did. */
data class ImportResult(val added: Int, val alreadyHere: Int, val mmsSkipped: Int, val unreadable: Int)

/**
 * Writes this phone's messages and the app's own data to a PIN-sealed file, and reads them
 * back. Files are reached through the system file picker's URIs, so they can sit on an SD
 * card or in a cloud provider's folder. Everything here does disk work; run it off the main
 * thread.
 */
class Backup(context: Context) {

    private val context = context.applicationContext
    private val resolver = this.context.contentResolver

    /**
     * Saves a backup to [uri], then reads it back and opens it to check it was written
     * whole. Without [withAttachments], MMS keep their text and lose their pictures and
     * files. Returns the message count.
     */
    fun write(uri: Uri, pin: CharArray, withAttachments: Boolean, progress: Progress): Int {
        val written = MessageDigest.getInstance("SHA-256")
        val count = resolver.openOutputStream(uri, "wt")?.use { raw ->
            BackupWriter(DigestOutputStream(BackupCrypto.sealing(raw, pin), written), System.currentTimeMillis()).use { out ->
                collect(out, withAttachments, progress)
            }
        } ?: throw java.io.IOException("No output stream")
        val read = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri)?.use { raw ->
            DigestInputStream(BackupCrypto.opening(raw, pin), read).use { input ->
                val buffer = ByteArray(BackupCrypto.CHUNK_BYTES)
                while (input.read(buffer) >= 0) Unit
            }
        } ?: throw java.io.IOException("No input stream")
        if (!read.digest().contentEquals(written.digest())) throw java.io.IOException("Backup did not read back the same")
        return count
    }

    /**
     * Opens the backup at [uri] and adds what it holds. The whole file is opened and checked
     * before anything is written, so a wrong PIN or a damaged file changes nothing.
     */
    fun restore(uri: Uri, pin: CharArray, progress: Progress): RestoreResult {
        val start = resolver.openInputStream(uri)?.use { input ->
            ByteArray(BackupCrypto.MAGIC_BYTES).let { it.copyOf(maxOf(0, java.io.DataInputStream(input).read(it))) }
        } ?: throw java.io.IOException("No input stream")
        if (!BackupCrypto.isChunked(start)) {
            // A backup from before chunking holds no MMS and opens whole.
            val sealed = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw java.io.IOException("No input stream")
            val content = BackupFormat.read(BackupCrypto.open(sealed, pin))
            return apply({ onMms -> content.mms.forEach(onMms); content }, content.mms.size, progress)
        }
        // The first read checks every chunk and line; only the second writes anything.
        val keys = HashMap<String, javax.crypto.spec.SecretKeySpec>()
        progress(0, 0)
        var mmsCount = 0
        readChunked(uri, pin, keys) { mmsCount++ }
        return apply({ onMms -> readChunked(uri, pin, keys, onMms) }, mmsCount, progress)
    }

    private fun readChunked(
        uri: Uri,
        pin: CharArray,
        keys: MutableMap<String, javax.crypto.spec.SecretKeySpec>,
        onMms: (BackupMms) -> Unit,
    ): BackupContent = resolver.openInputStream(uri)?.use { raw ->
        BackupFormat.read(BackupCrypto.opening(raw, pin, keys), onMms = onMms)
    } ?: throw java.io.IOException("No input stream")

    /**
     * Adds the messages in another app's backup at [uri]. MMS come in with their pictures
     * from SMS Import / Export's ZIP; those that cannot be read are counted and left out.
     */
    fun import(uri: Uri, source: ImportSource, progress: Progress): ImportResult {
        progress(0, 0)
        val scratch = File(context.cacheDir, "import").apply { deleteRecursively() }
        try {
            val mms = MmsInserter()
            val parsed = resolver.openInputStream(uri)?.use { input ->
                Importers.parse(source, input, scratch) { record ->
                    mms.add(BackupMms(record))
                    progress(mms.added + mms.alreadyHere, 0)
                }
            } ?: throw java.io.IOException("No input stream")
            val (inserted, _) = insertMessages(parsed.messages, progress)
            mms.finish()
            return ImportResult(inserted.added + mms.added, inserted.alreadyHere + mms.alreadyHere, parsed.mms, parsed.unreadable)
        } finally {
            scratch.deleteRecursively()
        }
    }

    /** Writes everything into [out], one record at a time. Returns the message count. */
    private fun collect(out: BackupWriter, withAttachments: Boolean, progress: Progress): Int {
        progress(0, 0)
        val locks = Locks.get(context).state()
        val marks = Marks.get(context).state()
        val threadAddress = HashMap<Long, String>()
        val mmsIds = resolver.query(
            Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms._ID),
            // Messages only: not drafts, delivery or read reports, or notifications of a
            // message still at the carrier, which will have expired by any restore.
            "${Telephony.Mms.MESSAGE_TYPE} IN (${PduHeaders.MESSAGE_TYPE_SEND_REQ}, ${PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF}) " +
                "AND ${Telephony.Mms.MESSAGE_BOX} != ${Telephony.Mms.MESSAGE_BOX_DRAFTS}",
            null, "${Telephony.Mms.DATE} ASC",
        )?.use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }.orEmpty()
        var done = 0
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                Telephony.Sms.DATE, Telephony.Sms.DATE_SENT, Telephony.Sms.TYPE, Telephony.Sms.READ,
                Telephony.Sms.SEEN, Telephony.Sms.STATUS, Telephony.Sms.SUBSCRIPTION_ID,
            ),
            null, null, "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val total = c.count + mmsIds.size
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val address = c.getString(2) ?: continue
                threadAddress.putIfAbsent(c.getLong(1), address)
                out.sms(
                    BackupSms(
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
                    ),
                )
                if (++done % PROGRESS_STEP == 0) progress(done, total)
            }
        }
        val store = MmsStore(context)
        val total = done + mmsIds.size
        for (rowId in mmsIds) {
            val record = store.read(rowId) ?: continue
            val key = MessageKey.mms(rowId)
            out.mms(
                BackupMms(
                    if (withAttachments) record else record.textOnly(),
                    locked = key in locks.messages,
                    starred = key in marks.starred,
                    reaction = marks.reactions[key],
                ),
            )
            if (++done % MMS_PROGRESS_STEP == 0) progress(done, total)
        }
        progress(done, total)
        // Threads with only MMS, groups among them, are named by the store's record of them.
        MmsReader(resolver).threadRecipients().forEach { (threadId, addresses) ->
            if (addresses.isNotEmpty()) threadAddress.putIfAbsent(threadId, AddressList.join(addresses))
        }
        val flags = ConversationFlags.get(context).all()
        val drafts = Drafts.get(context).all()
        val outgoing = Outgoing.get(context).due(Long.MAX_VALUE).filter { !it.delayed }
        // A conversation known only by a draft or a schedule has no messages to name it.
        outgoing.forEach { threadAddress.putIfAbsent(it.threadId, it.address) }
        (flags.keys + drafts.keys + locks.threads).forEach { threadId ->
            val address = threadAddress[threadId] ?: return@forEach
            val f = flags[threadId] ?: Flags()
            out.conversation(BackupConversation(address, f.pinnedAt, f.archived, threadId in locks.threads, drafts[threadId]))
        }
        BlockList.get(context).all().forEach { out.blocked(BackupBlocked(it)) }
        outgoing.forEach { out.scheduled(BackupScheduled(it.address, it.body, it.subscriptionId, it.sendAtMillis)) }
        return done
    }

    /**
     * [read] reads the content, handing each MMS to the function it is given; MMS are
     * written into the store as they come, the rest once the read is done.
     */
    private fun apply(read: ((BackupMms) -> Unit) -> BackupContent, mmsTotal: Int, progress: Progress): RestoreResult {
        val mms = MmsInserter()
        val content = read { m ->
            mms.add(m)
            val done = mms.added + mms.alreadyHere
            if (done % MMS_PROGRESS_STEP == 0 || done == mmsTotal) progress(done, mmsTotal)
        }
        mms.finish()
        return applyRest(content, mms, progress)
    }

    private fun applyRest(content: BackupContent, mms: MmsInserter, progress: Progress): RestoreResult {
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
            val threadId = runCatching { Telephony.Threads.getOrCreateThreadId(context, AddressList.split(c.address).toSet()) }.getOrNull() ?: continue
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
            // A group's addresses travel joined, as the app keeps them.
            val addresses = AddressList.split(s.address)
            val threadId = runCatching { Telephony.Threads.getOrCreateThreadId(context, addresses.toSet()) }.getOrNull() ?: continue
            // Another phone's SIMs have other ids; this one's default SIM sends it then.
            val sub = if (s.subscriptionId in sims) s.subscriptionId else SubscriptionManager.INVALID_SUBSCRIPTION_ID
            outgoing.add(threadId, addresses, s.body, sub, s.sendAtMillis, delayed = false)
        }
        return RestoreResult(inserted.added + mms.added, inserted.alreadyHere + mms.alreadyHere, past)
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

    /**
     * Writes MMS into the store, skipping any already there by date, direction and text, as
     * SMS are, then puts back their locks, stars and reactions in [finish].
     */
    private inner class MmsInserter {
        private val store = MmsStore(context)
        private val existing = existingMmsKeys()
        private val locked = ArrayList<Long>()
        private val starred = ArrayList<Long>()
        private val reactions = HashMap<Long, String>()
        var added = 0
            private set
        var alreadyHere = 0
            private set

        fun add(m: BackupMms) {
            val key = mmsKey(m.record)
            if (key in existing) {
                alreadyHere++
                return
            }
            // Something that was still going out when backed up did not go: it comes back failed.
            val record = if (m.record.box == Telephony.Mms.MESSAGE_BOX_OUTBOX) m.record.inBox(Telephony.Mms.MESSAGE_BOX_FAILED) else m.record
            val id = store.insert(record) ?: return
            existing += key
            added++
            val messageKey = MessageKey.mms(id)
            if (m.locked) locked += messageKey
            if (m.starred) starred += messageKey
            m.reaction?.let { reactions[messageKey] = it }
        }

        fun finish() {
            val marks = Marks.get(context)
            val have = marks.state().reactions
            reactions.forEach { (id, emoji) -> if (id !in have) marks.setReaction(id, emoji) }
            marks.setStarred(starred, true)
            Locks.get(context).setMessagesLocked(locked, true)
        }
    }

    /** What identifies each MMS already in the store, as [mmsKey] makes it. */
    private fun existingMmsKeys(): MutableSet<String> {
        val texts = HashMap<Long, StringBuilder>()
        resolver.query(
            Telephony.Mms.Part.CONTENT_URI, arrayOf(Telephony.Mms.Part.MSG_ID, Telephony.Mms.Part.TEXT),
            "${Telephony.Mms.Part.CONTENT_TYPE} = ?", arrayOf(MmsStore.TEXT_PLAIN), "${Telephony.Mms.Part.SEQ} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val text = texts.getOrPut(c.getLong(0)) { StringBuilder() }
                if (text.isNotEmpty()) text.append('\n')
                text.append(c.getString(1).orEmpty())
            }
        }
        val keys = HashSet<String>()
        resolver.query(
            Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                keys += mmsKey(c.getLong(1), c.getInt(2) != Telephony.Mms.MESSAGE_BOX_INBOX, texts[c.getLong(0)]?.toString().orEmpty())
            }
        }
        return keys
    }

    private fun isOutgoing(type: Int) = type != Telephony.Sms.MESSAGE_TYPE_INBOX

    private companion object {
        const val PROGRESS_STEP = 25
        const val MMS_PROGRESS_STEP = 5
    }
}
