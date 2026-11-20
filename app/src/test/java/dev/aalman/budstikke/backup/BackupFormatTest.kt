package dev.aalman.budstikke.backup

import dev.aalman.budstikke.sms.MmsPartRecord
import dev.aalman.budstikke.sms.MmsRecord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BackupFormatTest {

    private val content = BackupContent(
        createdAtMillis = 1_700_000_000_000,
        messages = listOf(
            BackupSms("+4741234567", "Hei! Line two\nand \"quotes\" 👍", 1_000, 900, 1, read = true, seen = true, status = -1, subscriptionId = 2),
            BackupSms("+4741234567", "Sent one", 2_000, 0, 2, read = true, seen = true, status = 0, subscriptionId = 2, locked = true, starred = true, reaction = "❤️"),
        ),
        conversations = listOf(
            BackupConversation("+4741234567", pinnedAtMillis = 5_000, archived = false, locked = true, draft = "half typed"),
            BackupConversation("BANK", pinnedAtMillis = null, archived = true, locked = false, draft = null),
        ),
        blocked = listOf(BackupBlocked("+15550001111")),
        scheduled = listOf(BackupScheduled("+4741234567", "Happy birthday", 2, 9_999_999_999_999)),
    )

    @Test
    fun readsWhatItWrote() {
        assertEquals(content, BackupFormat.read(BackupFormat.write(content)))
    }

    @Test
    fun roundTripsThroughTheSealedFile() {
        val sealed = BackupCrypto.seal(BackupFormat.write(content), "letmein1".toCharArray(), iterations = 1_000)
        assertEquals(content, BackupFormat.read(BackupCrypto.open(sealed, "letmein1".toCharArray())))
    }

    @Test
    fun carriesMmsWithTheirPartsAndMarks() {
        val record = MmsRecord(
            box = 1, messageType = 132, dateSeconds = 1_700_000_000, read = true, seen = true, subscriptionId = 1,
            from = "+4741234567", to = listOf("+4790000000", "+4791111111"),
            parts = listOf(
                MmsPartRecord("image/jpeg", name = "a.jpg", data = byteArrayOf(1, 2, 3)),
                MmsPartRecord("text/plain", text = "Bilde"),
            ),
        )
        val back = BackupFormat.read(BackupFormat.write(content.copy(mms = listOf(BackupMms(record, starred = true, reaction = "👍")))))
        val m = back.mms.single()
        assertEquals(true, m.starred)
        assertEquals("👍", m.reaction)
        assertEquals(listOf("+4790000000", "+4791111111"), m.record.to)
        assertEquals(listOf<Byte>(1, 2, 3), m.record.parts[0].data?.toList())
        assertEquals(mmsKey(record), mmsKey(m.record))
        assertEquals(content.messages, back.messages)
    }

    @Test
    fun withoutAttachmentsAnMmsKeepsItsTextAndPlaceholders() {
        val record = MmsRecord(
            box = 2, messageType = 128, dateSeconds = 1, read = true, seen = true, subscriptionId = 1,
            from = null, to = listOf("+4790000000"),
            parts = listOf(
                MmsPartRecord("image/jpeg", name = "a.jpg", data = byteArrayOf(1, 2, 3)),
                MmsPartRecord("text/plain", text = "Bilde"),
            ),
        ).textOnly()
        assertEquals(null, record.parts[0].data)
        assertEquals("a.jpg", record.parts[0].name)
        assertEquals("Bilde", record.parts[1].text)
    }

    @Test(expected = NotABackupException::class)
    fun refusesContentThatIsNotABackup() {
        BackupFormat.read("not gzip".toByteArray())
    }

    @Test
    fun matchesTheSameMessageStoredWithAnotherNumberFormat() {
        assertEquals(messageKey("+47 412 34 567", 1_000, false, "Hi"), messageKey("41234567", 1_000, false, "Hi"))
        assertNotEquals(messageKey("41234567", 1_000, false, "Hi"), messageKey("41234567", 1_000, true, "Hi"))
        assertNotEquals(messageKey("41234567", 1_000, false, "Hi"), messageKey("41234567", 1_001, false, "Hi"))
        assertEquals(messageKey("BANK", 1_000, false, "Hi"), messageKey("bank", 1_000, false, "Hi"))
    }
}
