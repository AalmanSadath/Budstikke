package dev.aalman.budstikke.sms

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * A whole MMS as JSON, attachments included as base64: how the trash keeps a deleted MMS
 * until it is restored, and how backups carry them.
 */
object MmsRecordJson {

    fun write(r: MmsRecord): JSONObject = JSONObject().apply {
        put("box", r.box)
        put("type", r.messageType)
        put("date", r.dateSeconds)
        put("date_sent", r.dateSentSeconds)
        put("read", r.read)
        put("seen", r.seen)
        put("sub", r.subscriptionId)
        r.from?.let { put("from", it) }
        put("to", JSONArray(r.to))
        put("cc", JSONArray(r.cc))
        r.subject?.let { put("subject", it) }
        r.messageId?.let { put("m_id", it) }
        r.transactionId?.let { put("tr_id", it) }
        r.contentType?.let { put("ct", it) }
        r.contentLocation?.let { put("ct_l", it) }
        if (r.messageSize > 0) put("size", r.messageSize)
        if (r.expirySeconds > 0) put("exp", r.expirySeconds)
        put(
            "parts",
            JSONArray(
                r.parts.map { p ->
                    JSONObject().apply {
                        put("ct", p.contentType)
                        p.name?.let { put("name", it) }
                        p.contentLocation?.let { put("cl", it) }
                        p.contentId?.let { put("cid", it) }
                        put("charset", p.charset)
                        p.text?.let { put("text", it) }
                        p.data?.let { put("data", Base64.getEncoder().encodeToString(it)) }
                    }
                },
            ),
        )
    }

    /** The record [o] holds, or null when it is not one. */
    fun read(o: JSONObject): MmsRecord? = runCatching {
        val parts = o.optJSONArray("parts") ?: JSONArray()
        MmsRecord(
            box = o.getInt("box"),
            messageType = o.getInt("type"),
            dateSeconds = o.getLong("date"),
            dateSentSeconds = o.optLong("date_sent"),
            read = o.optBoolean("read", true),
            seen = o.optBoolean("seen", true),
            subscriptionId = o.optInt("sub", -1),
            from = o.optStringOrNull("from"),
            to = o.optJSONArray("to").strings(),
            cc = o.optJSONArray("cc").strings(),
            subject = o.optStringOrNull("subject"),
            messageId = o.optStringOrNull("m_id"),
            transactionId = o.optStringOrNull("tr_id"),
            contentType = o.optStringOrNull("ct"),
            contentLocation = o.optStringOrNull("ct_l"),
            messageSize = o.optLong("size"),
            expirySeconds = o.optLong("exp"),
            parts = (0 until parts.length()).map { i ->
                val p = parts.getJSONObject(i)
                MmsPartRecord(
                    contentType = p.getString("ct"),
                    name = p.optStringOrNull("name"),
                    contentLocation = p.optStringOrNull("cl"),
                    contentId = p.optStringOrNull("cid"),
                    charset = p.optInt("charset", dev.aalman.budstikke.mms.pdu.CharacterSets.UTF_8),
                    text = p.optStringOrNull("text"),
                    data = p.optStringOrNull("data")?.let { Base64.getDecoder().decode(it) },
                )
            },
        )
    }.getOrNull()

    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private fun JSONObject.optStringOrNull(name: String): String? = if (has(name) && !isNull(name)) getString(name) else null
}
