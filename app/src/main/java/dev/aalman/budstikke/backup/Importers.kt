package dev.aalman.budstikke.backup

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import dev.aalman.budstikke.mms.pdu.CharacterSets
import dev.aalman.budstikke.mms.pdu.PduHeaders
import dev.aalman.budstikke.sms.MmsPartRecord
import dev.aalman.budstikke.sms.MmsRecord
import dev.aalman.budstikke.sms.MmsStore
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Another app's backup that messages can be imported from. */
enum class ImportSource { SmsImportExport, Qksms }

/**
 * What a file held: its SMS, how many MMS were left out because they could not be brought
 * in, and how many entries could not be read at all.
 */
data class ParsedImport(val messages: List<BackupSms>, val mms: Int, val unreadable: Int)

/** The file is too big to read whole; only SMS Import / Export's ZIP is read a line at a time. */
class ImportTooLargeException : Exception()

/**
 * Reads other apps' message backups, written from each format's public description and
 * the files they produce, not from their code.
 *
 * SMS Import / Export (2.0 and later) saves a ZIP holding `messages.ndjson`: one JSON object
 * per line, SMS first, then MMS, with Android's own message column names (`address`,
 * `body`, `date`, `type` and so on) and values that may be strings. MMS lines carry keys it
 * adds: `__sender_address`, an address row; `__recipient_addresses`, a list of them; and
 * `__parts`, the part rows. A part's bytes are a file in the ZIP's `data/` folder, named as
 * the last segment of the part's `_data` path. Its 1.x versions saved one JSON file, whose
 * MMS are left out.
 *
 * QKSMS saves one JSON file with a list of messages. Its layout is not published, so this
 * reads any list of message objects in the file, accepting the usual spellings of each
 * field (`date_sent` or `dateSent`, `sub_id` or `subId`) and numbers or text for values.
 */
object Importers {

    /** The most read into memory at once for a file that is not read line by line. */
    const val MAX_WHOLE_BYTES = 100L * 1024 * 1024

    /** The largest part file brought in from an import; larger ones are left out of their message. */
    const val MAX_PART_BYTES = 20L * 1024 * 1024

    /**
     * Reads [input]. With [scratch], an empty folder to work in, and [onMms], MMS in a ZIP
     * come in with their files and are handed to [onMms] one at a time; otherwise every
     * MMS is counted and left out.
     */
    fun parse(source: ImportSource, input: InputStream, scratch: File? = null, onMms: ((MmsRecord) -> Unit)? = null): ParsedImport {
        val stream = BufferedInputStream(input)
        stream.mark(4)
        val start = ByteArray(2).let { if (stream.read(it) == 2) it else ByteArray(0) }
        stream.reset()
        val zipped = start.size == 2 && start[0] == 'P'.code.toByte() && start[1] == 'K'.code.toByte()
        val out = Collector()
        // Either source may come zipped; SMS Import / Export's always is.
        if (zipped) readZip(stream, out, scratch?.takeIf { onMms != null }, onMms) else readText(readLimited(stream), out)
        if (source == ImportSource.Qksms && out.messages.isEmpty() && out.mms == 0 && out.unreadable == 0) {
            throw NotABackupException()
        }
        return ParsedImport(out.messages, out.mms, out.unreadable)
    }

    private class Collector {
        val messages = ArrayList<BackupSms>()
        var mms = 0
        var unreadable = 0
        var sawAny = false
        /** Turns an MMS line into a message, when its files are at hand. */
        var mmsReader: ((JSONObject) -> Boolean)? = null

        fun add(o: JSONObject, mmsList: Boolean = false) {
            sawAny = true
            when (val r = record(o, mmsList)) {
                Record.Mms -> if (mmsReader?.invoke(o) != true) mms++
                Record.Unreadable -> unreadable++
                is Record.Sms -> messages += r.sms
            }
        }
    }

    /**
     * Reads a ZIP. With [scratch], the message lines and the `data/` files are copied out
     * first, since the ZIP may hold them in either order, and read from there.
     */
    private fun readZip(input: InputStream, out: Collector, scratch: File?, onMms: ((MmsRecord) -> Unit)?) {
        val zip = ZipInputStream(input)
        var found = false
        val lines = ArrayList<File>()
        val data = scratch?.let { File(it, "data") }
        while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name.substringAfterLast('/')
            when {
                entry.isDirectory -> Unit
                data != null && entry.name.trimStart('/').startsWith("data/") -> {
                    // Only a plain file name is used, so nothing lands outside the folder.
                    if (safeName(name)) copyLimited(zip, File(data.apply { mkdirs() }, name), MAX_PART_BYTES)
                }
                name.endsWith(".ndjson") && scratch != null -> {
                    found = true
                    val copy = File(scratch.apply { mkdirs() }, "messages${lines.size}.ndjson")
                    copy.outputStream().use { zip.copyTo(it) }
                    lines += copy
                }
                name.endsWith(".ndjson") -> {
                    found = true
                    // Not closed: closing would close the ZIP with it.
                    readLines(zip.bufferedReader(Charsets.UTF_8), out)
                }
                name.endsWith(".json") -> {
                    found = true
                    readText(readLimited(zip), out)
                }
            }
        }
        if (!found) throw NotABackupException()
        if (data != null && onMms != null) {
            out.mmsReader = { o -> smsIeMms(o, data)?.let(onMms) != null }
        }
        lines.forEach { file -> file.bufferedReader(Charsets.UTF_8).use { readLines(it, out) } }
    }

    private fun readLines(reader: java.io.BufferedReader, out: Collector) {
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue
            val o = try {
                JSONObject(line)
            } catch (_: JSONException) {
                out.unreadable++
                continue
            }
            out.add(o)
        }
    }

    private fun safeName(name: String) = name.matches(Regex("[A-Za-z0-9._-]{1,128}")) && name != "." && name != ".."

    /** Copies at most [limit] bytes; a larger file is not kept. */
    private fun copyLimited(input: InputStream, to: File, limit: Long) {
        var written = 0L
        to.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                written += n
                if (written > limit) break
                output.write(buffer, 0, n)
            }
        }
        if (written > limit) to.delete()
    }

    /**
     * One SMS Import / Export MMS line as a message, its part bytes read from [data], or
     * null when it is not a message that can be brought in: a report, a notification of one
     * never downloaded, or one with nothing in it.
     */
    internal fun smsIeMms(o: JSONObject, data: File): MmsRecord? {
        val box = o.long("msg_box")?.toInt() ?: return null
        if (box !in MMS_BOXES) return null
        val inbox = box == MMS_BOX_INBOX
        val type = o.long("m_type")?.toInt() ?: if (inbox) PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF else PduHeaders.MESSAGE_TYPE_SEND_REQ
        if (type != PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF && type != PduHeaders.MESSAGE_TYPE_SEND_REQ) return null
        // MMS dates are seconds in Android's store; take milliseconds too, should a file hold them.
        fun seconds(v: Long?) = v?.let { if (it > 100_000_000_000L) it / 1000 else it }
        val date = seconds(o.long("date")) ?: return null
        val from = o.optJSONObject("__sender_address")?.text("address")?.takeIf { it.isNotBlank() && it != INSERT_ADDRESS_TOKEN }
        val to = ArrayList<String>()
        val cc = ArrayList<String>()
        o.optJSONArray("__recipient_addresses")?.let { list ->
            for (i in 0 until list.length()) {
                val a = list.optJSONObject(i) ?: continue
                val address = a.text("address")?.takeIf { it.isNotBlank() && it != INSERT_ADDRESS_TOKEN } ?: continue
                if (a.long("type")?.toInt() == PduHeaders.CC || a.long("type")?.toInt() == PduHeaders.BCC) cc += address else to += address
            }
        }
        if (from == null && to.isEmpty() && cc.isEmpty()) return null
        val parts = ArrayList<MmsPartRecord>()
        o.optJSONArray("__parts")?.let { list ->
            for (i in 0 until list.length()) {
                val p = list.optJSONObject(i) ?: continue
                val contentType = p.text("ct")?.lowercase()?.takeIf { it.isNotBlank() } ?: continue
                val file = p.text("_data")?.substringAfterLast('/')?.takeIf(::safeName)?.let { File(data, it) }?.takeIf { it.isFile }
                val charset = p.long("chset")?.toInt()?.takeIf { it > 0 } ?: CharacterSets.UTF_8
                val isText = contentType == MmsStore.TEXT_PLAIN
                parts += MmsPartRecord(
                    contentType = contentType,
                    name = p.text("name") ?: p.text("fn"),
                    contentLocation = p.text("cl"),
                    contentId = p.text("cid"),
                    charset = charset,
                    text = if (isText) p.text("text") ?: file?.let { MmsStore.decode(it.readBytes(), charset) } else null,
                    data = when {
                        isText -> null
                        contentType == MmsStore.SMIL -> (p.text("text") ?: file?.readText())?.toByteArray()
                        else -> file?.readBytes()
                    },
                )
            }
        }
        if (parts.none { it.text != null || it.data != null }) return null
        return MmsRecord(
            box = box,
            messageType = type,
            dateSeconds = date,
            dateSentSeconds = seconds(o.long("date_sent")) ?: 0,
            read = o.flag("read") ?: true,
            seen = o.flag("seen") ?: true,
            subscriptionId = (o.long("sub_id") ?: -1).toInt(),
            from = from,
            to = to,
            cc = cc,
            subject = o.text("sub"),
            messageId = o.text("m_id"),
            transactionId = o.text("tr_id"),
            contentType = o.text("ct_t"),
            parts = parts,
        )
    }

    private const val MMS_BOX_INBOX = 1
    /** Inbox, sent, outbox and failed; drafts are left out. */
    private val MMS_BOXES = setOf(1, 2, 4, 5)
    private const val INSERT_ADDRESS_TOKEN = "insert-address-token"

    private fun readLimited(input: InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_WHOLE_BYTES) throw ImportTooLargeException()
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /** One JSON document holding messages, or JSON lines of them. */
    private fun readText(text: String, out: Collector) {
        val trimmed = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        when {
            trimmed.startsWith("[") -> readArray(parseOrNull { JSONArray(trimmed) } ?: throw NotABackupException(), out, false)
            trimmed.startsWith("{") -> {
                val whole = parseOrNull { JSONObject(trimmed) }
                if (whole != null && !looksLikeMessage(whole)) {
                    readContainer(whole, out)
                } else {
                    // JSON lines, or a single message.
                    trimmed.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                        parseOrNull { JSONObject(line) }?.let(out::add) ?: out.unreadable++
                    }
                }
            }
            else -> throw NotABackupException()
        }
        if (!out.sawAny && out.unreadable == 0) throw NotABackupException()
    }

    /** An object wrapping lists of messages, such as `{"messages": [...]}` or `{"sms": [...], "mms": [...]}`. */
    private fun readContainer(o: JSONObject, out: Collector) {
        for (key in o.keys()) {
            val value = o.opt(key)
            if (value is JSONArray) readArray(value, out, mmsList = key.contains("mms", ignoreCase = true))
        }
    }

    private fun readArray(array: JSONArray, out: Collector, mmsList: Boolean) {
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is JSONObject) out.add(item, mmsList) else out.unreadable++
        }
    }

    private inline fun <T> parseOrNull(parse: () -> T): T? = try {
        parse()
    } catch (_: JSONException) {
        null
    }

    private sealed interface Record {
        data class Sms(val sms: BackupSms) : Record
        data object Mms : Record
        data object Unreadable : Record
    }

    private val MMS_KEYS = listOf("__parts", "__sender_address", "__recipient_addresses", "m_type", "msg_box", "parts", "addresses")

    private fun looksLikeMessage(o: JSONObject) = o.has("address") || o.has("body") || MMS_KEYS.any(o::has)

    private fun record(o: JSONObject, mmsList: Boolean): Record {
        if (mmsList || MMS_KEYS.any(o::has) || o.optString("mms").let { it == "true" || it == "1" }) return Record.Mms
        val address = o.text("address")?.takeIf { it.isNotBlank() } ?: return Record.Unreadable
        val date = o.long("date") ?: return Record.Unreadable
        val type = o.long("type")?.toInt()?.takeIf { it in 1..6 } ?: return Record.Unreadable
        val read = o.flag("read") ?: true
        return Record.Sms(
            BackupSms(
                address = address,
                body = o.text("body").orEmpty(),
                dateMillis = date,
                dateSentMillis = o.long("date_sent") ?: o.long("dateSent") ?: 0,
                type = type,
                read = read,
                seen = o.flag("seen") ?: read,
                status = (o.long("status") ?: -1).toInt(),
                subscriptionId = (o.long("sub_id") ?: o.long("subId") ?: o.long("subscription_id") ?: -1).toInt(),
            ),
        )
    }

    private fun JSONObject.text(name: String): String? = if (has(name) && !isNull(name)) opt(name)?.toString() else null

    private fun JSONObject.long(name: String): Long? = when (val v = if (has(name) && !isNull(name)) opt(name) else null) {
        is Number -> v.toLong()
        is String -> v.trim().toLongOrNull()
        else -> null
    }

    private fun JSONObject.flag(name: String): Boolean? = when (val v = if (has(name) && !isNull(name)) opt(name) else null) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> when (v.trim().lowercase()) {
            "1", "true" -> true
            "0", "false" -> false
            else -> null
        }
        else -> null
    }
}
