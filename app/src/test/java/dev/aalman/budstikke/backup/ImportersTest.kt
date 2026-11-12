package dev.aalman.budstikke.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportersTest {

    // Shaped like SMS Import / Export's messages.ndjson: Android's column names, values as
    // text, SMS lines then MMS lines.
    private val ndjson = """
        {"_id":"12","thread_id":"3","address":"+4741234567","date":"1700000000000","date_sent":"1699999999000","read":"1","status":"-1","type":"1","body":"Hei, kommer du?","sub_id":"1","__display_name":"Kari"}
        {"_id":"13","thread_id":"3","address":"+4741234567","date":"1700000060000","date_sent":"0","read":"1","status":"-1","type":"2","body":"Ja, om ti minutter","sub_id":"1"}
        {"_id":"4","thread_id":"3","date":"1700000100","msg_box":"1","__sender_address":{"address":"+4741234567"},"__parts":[{"ct":"image/jpeg","_data":"/data/part1"}]}
    """.trimIndent()

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z ->
            entries.forEach { (name, text) ->
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    @Test
    fun readsSmsImportExportZip() {
        val parsed = Importers.parse(ImportSource.SmsImportExport, zip("messages.ndjson" to ndjson, "data/part1" to "jpeg").inputStream())
        assertEquals(2, parsed.messages.size)
        assertEquals(1, parsed.mms)
        assertEquals(0, parsed.unreadable)
        val first = parsed.messages[0]
        assertEquals("+4741234567", first.address)
        assertEquals("Hei, kommer du?", first.body)
        assertEquals(1_700_000_000_000, first.dateMillis)
        assertEquals(1_699_999_999_000, first.dateSentMillis)
        assertEquals(1, first.type)
        assertEquals(true, first.read)
        assertEquals(2, parsed.messages[1].type)
    }

    @Test
    fun readsPlainJsonLines() {
        val parsed = Importers.parse(ImportSource.SmsImportExport, ndjson.byteInputStream())
        assertEquals(2, parsed.messages.size)
        assertEquals(1, parsed.mms)
    }

    @Test
    fun readsAJsonDocumentWithSmsAndMmsLists() {
        val v1 = """{"sms": [{"address": "5551234567", "date": "1700000000000", "type": "1", "body": "old one", "read": "0"}],
            "mms": [{"date": "1700000000"}, {"date": "1700000001"}]}"""
        val parsed = Importers.parse(ImportSource.SmsImportExport, v1.byteInputStream())
        assertEquals(listOf("old one"), parsed.messages.map { it.body })
        assertEquals(false, parsed.messages[0].read)
        assertEquals(2, parsed.mms)
    }

    @Test
    fun readsAQksmsStyleListWithOtherSpellings() {
        val qksms = """{"messageCount": 2, "messages": [
            {"type": 2, "address": "+15550001111", "date": 1700000000000, "dateSent": 1700000000000, "read": true, "status": -1, "body": "Sent from QKSMS", "subId": 1},
            {"type": 1, "address": "+15550001111", "date": 1700000005000, "dateSent": 1700000004000, "read": false, "status": -1, "body": "Reply", "subId": -1}
        ]}"""
        val parsed = Importers.parse(ImportSource.Qksms, qksms.byteInputStream())
        assertEquals(listOf("Sent from QKSMS", "Reply"), parsed.messages.map { it.body })
        assertEquals(1_700_000_004_000, parsed.messages[1].dateSentMillis)
        assertEquals(1, parsed.messages[0].subscriptionId)
        assertEquals(false, parsed.messages[1].read)
        assertEquals(0, parsed.mms)
    }

    @Test
    fun countsEntriesItCannotRead() {
        val text = """[{"address": "+15550001111", "body": "no date", "type": 1}, "not an object",
            {"address": "+15550001111", "date": 1, "type": 1, "body": "fine"}]"""
        val parsed = Importers.parse(ImportSource.Qksms, text.byteInputStream())
        assertEquals(1, parsed.messages.size)
        assertEquals(2, parsed.unreadable)
    }

    @Test(expected = NotABackupException::class)
    fun refusesOtherFiles() {
        Importers.parse(ImportSource.Qksms, "Hello, this is a text file".byteInputStream())
    }

    @Test(expected = NotABackupException::class)
    fun refusesAZipWithoutMessages() {
        Importers.parse(ImportSource.SmsImportExport, zip("photo.jpg" to "jpeg").inputStream())
    }
}
