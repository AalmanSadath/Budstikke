package dev.aalman.budstikke.backup

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Another app's backup that messages can be imported from. */
enum class ImportSource { SmsImportExport, Qksms }

/** What a file held: its SMS, and how many entries were MMS or could not be read. */
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
 * adds, such as `__parts` and `__sender_address`. Its 1.x versions saved one JSON file.
 *
 * QKSMS saves one JSON file with a list of messages. Its layout is not published, so this
 * reads any list of message objects in the file, accepting the usual spellings of each
 * field (`date_sent` or `dateSent`, `sub_id` or `subId`) and numbers or text for values.
 */
object Importers {

    /** The most read into memory at once for a file that is not read line by line. */
    const val MAX_WHOLE_BYTES = 100L * 1024 * 1024

    fun parse(source: ImportSource, input: InputStream): ParsedImport {
        val stream = BufferedInputStream(input)
        stream.mark(4)
        val start = ByteArray(2).let { if (stream.read(it) == 2) it else ByteArray(0) }
        stream.reset()
        val zipped = start.size == 2 && start[0] == 'P'.code.toByte() && start[1] == 'K'.code.toByte()
        val out = Collector()
        // Either source may come zipped; SMS Import / Export's always is.
        if (zipped) readZip(stream, out) else readText(readLimited(stream), out)
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

        fun add(o: JSONObject, mmsList: Boolean = false) {
            sawAny = true
            when (val r = record(o, mmsList)) {
                Record.Mms -> mms++
                Record.Unreadable -> unreadable++
                is Record.Sms -> messages += r.sms
            }
        }
    }

    private fun readZip(input: InputStream, out: Collector) {
        val zip = ZipInputStream(input)
        var found = false
        while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name.substringAfterLast('/')
            when {
                entry.isDirectory -> Unit
                name.endsWith(".ndjson") -> {
                    found = true
                    // Not closed: closing would close the ZIP with it.
                    val reader = zip.bufferedReader(Charsets.UTF_8)
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
                name.endsWith(".json") -> {
                    found = true
                    readText(readLimited(zip), out)
                }
            }
        }
        if (!found) throw NotABackupException()
    }

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
