package dev.aalman.budstikke.backup

import dev.aalman.budstikke.sms.MmsRecord
import dev.aalman.budstikke.sms.MmsRecordJson
import dev.aalman.budstikke.sms.MmsStore
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * One SMS as a backup or an import carries it, with what this app keeps about it. Nothing
 * refers to the system store's ids, which a restore cannot keep: messages are matched by
 * address, date, direction and text, and conversations by address.
 */
data class BackupSms(
    val address: String,
    val body: String,
    val dateMillis: Long,
    val dateSentMillis: Long,
    /** A `Telephony.Sms` message type: received, sent, failed and the rest. */
    val type: Int,
    val read: Boolean,
    val seen: Boolean,
    val status: Int,
    val subscriptionId: Int,
    val locked: Boolean = false,
    val starred: Boolean = false,
    /** This phone's own reaction on it. */
    val reaction: String? = null,
)

/** What this app keeps about a conversation, by the address it is with. */
data class BackupConversation(
    val address: String,
    val pinnedAtMillis: Long?,
    val archived: Boolean,
    val locked: Boolean,
    val draft: String?,
)

data class BackupScheduled(val address: String, val body: String, val subscriptionId: Int, val sendAtMillis: Long)

data class BackupBlocked(val address: String)

/** One MMS as a backup carries it: the whole message, and what this app keeps about it. */
data class BackupMms(
    val record: MmsRecord,
    val locked: Boolean = false,
    val starred: Boolean = false,
    val reaction: String? = null,
)

/**
 * Everything in one backup file. [mms] is filled only when reading a whole file into
 * memory; [BackupWriter] and [BackupFormat.read] with a handler pass them one at a time.
 */
data class BackupContent(
    val createdAtMillis: Long,
    val messages: List<BackupSms>,
    val conversations: List<BackupConversation>,
    val blocked: List<BackupBlocked>,
    val scheduled: List<BackupScheduled>,
    val mms: List<BackupMms> = emptyList(),
)

/**
 * Writes backup content to [out] one record at a time, so a backup full of pictures is
 * never held in memory whole. [close] finishes the file and closes [out].
 */
class BackupWriter(out: OutputStream, createdAtMillis: Long) : java.io.Closeable {
    private val writer = GZIPOutputStream(out).bufferedWriter(Charsets.UTF_8)

    init {
        line(JSONObject().put("format", BackupFormat.FORMAT).put("version", BackupFormat.VERSION).put("created", createdAtMillis))
    }

    fun sms(m: BackupSms) = line(
        JSONObject()
            .put("kind", "sms")
            .put("address", m.address)
            .put("body", m.body)
            .put("date", m.dateMillis)
            .put("date_sent", m.dateSentMillis)
            .put("type", m.type)
            .put("read", m.read)
            .put("seen", m.seen)
            .put("status", m.status)
            .put("sub_id", m.subscriptionId)
            .putTrue("locked", m.locked)
            .putTrue("starred", m.starred)
            .putOpt("reaction", m.reaction),
    )

    fun mms(m: BackupMms) = line(
        JSONObject()
            .put("kind", "mms")
            .put("message", MmsRecordJson.write(m.record))
            .putTrue("locked", m.locked)
            .putTrue("starred", m.starred)
            .putOpt("reaction", m.reaction),
    )

    fun conversation(c: BackupConversation) = line(
        JSONObject()
            .put("kind", "conversation")
            .put("address", c.address)
            .putOpt("pinned_at", c.pinnedAtMillis)
            .putTrue("archived", c.archived)
            .putTrue("locked", c.locked)
            .putOpt("draft", c.draft),
    )

    fun blocked(b: BackupBlocked) = line(JSONObject().put("kind", "blocked").put("address", b.address))

    fun scheduled(s: BackupScheduled) = line(
        JSONObject()
            .put("kind", "scheduled")
            .put("address", s.address)
            .put("body", s.body)
            .put("sub_id", s.subscriptionId)
            .put("send_at", s.sendAtMillis),
    )

    override fun close() = writer.close()

    private fun line(o: JSONObject) {
        writer.write(o.toString())
        writer.write("\n")
    }

    /** Leaves false flags out, which keeps most lines short. */
    private fun JSONObject.putTrue(name: String, value: Boolean): JSONObject = if (value) put(name, true) else this
}

/**
 * The plain content of a backup, before it is sealed: gzip-compressed JSON, one object per
 * line. The first line names the format and its version; each line after is one record
 * with a "kind". A reader skips kinds it does not know, so later versions can add some.
 */
object BackupFormat {

    internal const val FORMAT = "budstikke-backup"

    /** 2 added MMS. */
    const val VERSION = 2

    /** The whole content in memory, MMS included. */
    fun write(content: BackupContent): ByteArray {
        val bytes = ByteArrayOutputStream()
        BackupWriter(bytes, content.createdAtMillis).use { out ->
            content.messages.forEach(out::sms)
            content.mms.forEach(out::mms)
            content.conversations.forEach(out::conversation)
            content.blocked.forEach(out::blocked)
            content.scheduled.forEach(out::scheduled)
        }
        return bytes.toByteArray()
    }

    /**
     * Reads plain backup content, MMS included, into memory. Throws [NotABackupException]
     * when it is not one this version can read.
     */
    fun read(plain: ByteArray): BackupContent {
        val mms = ArrayList<BackupMms>()
        return read(plain.inputStream()) { mms += it }.copy(mms = mms)
    }

    /**
     * Reads plain backup content from [input], handing each MMS to [onMms] as it is read
     * rather than keeping them, since their pictures can be large. An MMS line that cannot
     * be read is counted in [onUnreadableMms].
     */
    fun read(input: InputStream, onUnreadableMms: () -> Unit = {}, onMms: (BackupMms) -> Unit): BackupContent {
        val reader: BufferedReader = try {
            GZIPInputStream(input).bufferedReader(Charsets.UTF_8)
        } catch (_: java.io.IOException) {
            throw NotABackupException()
        }
        reader.use {
            val header = runCatching { JSONObject(it.readLine() ?: "") }.getOrNull() ?: throw NotABackupException()
            if (header.optString("format") != FORMAT || header.optInt("version") !in 1..VERSION) throw NotABackupException()
            val messages = ArrayList<BackupSms>()
            val conversations = ArrayList<BackupConversation>()
            val blocked = ArrayList<BackupBlocked>()
            val scheduled = ArrayList<BackupScheduled>()
            while (true) {
                val line = it.readLine() ?: break
                if (line.isBlank()) continue
                val o = JSONObject(line)
                when (o.optString("kind")) {
                    "sms" -> messages += BackupSms(
                        address = o.getString("address"),
                        body = o.getString("body"),
                        dateMillis = o.getLong("date"),
                        dateSentMillis = o.optLong("date_sent"),
                        type = o.getInt("type"),
                        read = o.optBoolean("read", true),
                        seen = o.optBoolean("seen", true),
                        status = o.optInt("status", -1),
                        subscriptionId = o.optInt("sub_id", -1),
                        locked = o.optBoolean("locked"),
                        starred = o.optBoolean("starred"),
                        reaction = o.optStringOrNull("reaction"),
                    )
                    "conversation" -> conversations += BackupConversation(
                        address = o.getString("address"),
                        pinnedAtMillis = if (o.has("pinned_at")) o.getLong("pinned_at") else null,
                        archived = o.optBoolean("archived"),
                        locked = o.optBoolean("locked"),
                        draft = o.optStringOrNull("draft"),
                    )
                    "mms" -> {
                        val record = o.optJSONObject("message")?.let(MmsRecordJson::read)
                        if (record == null) {
                            onUnreadableMms()
                        } else {
                            onMms(BackupMms(record, o.optBoolean("locked"), o.optBoolean("starred"), o.optStringOrNull("reaction")))
                        }
                    }
                    "blocked" -> blocked += BackupBlocked(o.getString("address"))
                    "scheduled" -> scheduled += BackupScheduled(
                        address = o.getString("address"),
                        body = o.getString("body"),
                        subscriptionId = o.optInt("sub_id", -1),
                        sendAtMillis = o.getLong("send_at"),
                    )
                }
            }
            return BackupContent(header.optLong("created"), messages, conversations, blocked, scheduled)
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? = if (has(name) && !isNull(name)) getString(name) else null
}

/**
 * What identifies a message across phones and restores: when it was sent or received, which
 * way it went, its text, and the last digits of the number, since the same number is often
 * stored as "+47 412 34 567" on one phone and "41234567" on another.
 */
fun messageKey(address: String, dateMillis: Long, outgoing: Boolean, body: String): String {
    val digits = address.filter(Char::isDigit)
    val who = if (digits.length >= 7) digits.takeLast(7) else address.lowercase()
    return "$dateMillis|${if (outgoing) 1 else 0}|$who|$body"
}

/**
 * What identifies an MMS across restores, as [messageKey] does an SMS: its date in seconds,
 * which way it went, and its text. Its pictures are left out, as the store's copy of a
 * restored one is the same bytes.
 */
fun mmsKey(dateSeconds: Long, outgoing: Boolean, text: String): String = "$dateSeconds|${if (outgoing) 1 else 0}|$text"

fun mmsKey(record: MmsRecord): String = mmsKey(
    record.dateSeconds,
    record.box != android.provider.Telephony.Mms.MESSAGE_BOX_INBOX,
    record.parts.filter { it.contentType == MmsStore.TEXT_PLAIN }.mapNotNull { it.text }.joinToString("\n"),
)
