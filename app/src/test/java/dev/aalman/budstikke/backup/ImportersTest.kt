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
    fun bringsInSmsImportExportMmsWithTheirFiles() {
        val group = """{"_id":"5","date":"1700000200","msg_box":"1","m_type":"132","read":"0","sub_id":"1","__sender_address":{"address":"+4741234567","type":"137"},"__recipient_addresses":[{"address":"+4790000000","type":"151"},{"address":"+4791111111","type":"130"}],"__parts":[{"ct":"application/smil","text":"<smil/>"},{"ct":"image/jpeg","name":"cat.jpg","_data":"/data/user_de/0/com.android.providers.telephony/app_parts/PART_1"},{"ct":"text/plain","chset":"106","text":"Se her"}]}"""
        val report = """{"_id":"6","date":"1700000300","msg_box":"1","m_type":"134","__sender_address":{"address":"+4741234567"},"__parts":[]}"""
        val scratch = java.nio.file.Files.createTempDirectory("import").toFile()
        val got = ArrayList<dev.aalman.budstikke.sms.MmsRecord>()
        try {
            val parsed = Importers.parse(
                ImportSource.SmsImportExport,
                zip("messages.ndjson" to "$ndjson\n$group\n$report", "data/PART_1" to "jpeg bytes").inputStream(),
                scratch,
            ) { got += it }
            assertEquals(2, parsed.messages.size)
            // The first MMS line names a file that is not in the ZIP and has no text; the report is no message.
            assertEquals(2, parsed.mms)
            assertEquals(1, got.size)
            val m = got.single()
            assertEquals(1_700_000_200, m.dateSeconds)
            assertEquals(false, m.read)
            assertEquals("+4741234567", m.from)
            assertEquals(listOf("+4790000000"), m.to)
            assertEquals(listOf("+4791111111"), m.cc)
            assertEquals("Se her", m.parts.single { it.contentType == "text/plain" }.text)
            assertEquals("jpeg bytes", m.parts.single { it.contentType == "image/jpeg" }.data?.toString(Charsets.UTF_8))
            assertEquals("cat.jpg", m.parts.single { it.contentType == "image/jpeg" }.name)
        } finally {
            scratch.deleteRecursively()
        }
    }

    @Test
    fun keepsPartFilesInsideItsFolder() {
        val scratch = java.nio.file.Files.createTempDirectory("import").toFile()
        try {
            Importers.parse(
                ImportSource.SmsImportExport,
                zip("messages.ndjson" to ndjson, "data/../../escape" to "x", "data/ok" to "y").inputStream(),
                scratch,
            ) {}
            assertEquals(false, java.io.File(scratch.parentFile, "escape").exists())
            assertEquals(true, java.io.File(scratch, "data/ok").exists())
        } finally {
            scratch.deleteRecursively()
        }
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
