package dev.aalman.budstikke.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupCryptoTest {

    // A low count keeps the tests quick; the file carries its own count.
    private val iterations = 1_000
    private val plain = "Hello from the backup, æøå 👍".toByteArray()

    @Test
    fun opensWhatItSealed() {
        val sealed = BackupCrypto.seal(plain, "123456".toCharArray(), iterations)
        assertArrayEquals(plain, BackupCrypto.open(sealed, "123456".toCharArray()))
    }

    @Test
    fun cannotBeReadAsText() {
        val sealed = BackupCrypto.seal(plain, "123456".toCharArray(), iterations)
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("Hello"))
    }

    @Test(expected = WrongPinException::class)
    fun refusesAWrongPin() {
        val sealed = BackupCrypto.seal(plain, "123456".toCharArray(), iterations)
        BackupCrypto.open(sealed, "123457".toCharArray())
    }

    @Test
    fun refusesAnyChangedByte() {
        val sealed = BackupCrypto.seal(plain, "123456".toCharArray(), iterations)
        // Every byte after the magic: salt, iteration count, nonce, ciphertext and tag.
        for (i in 10 until sealed.size) {
            val tampered = sealed.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            try {
                BackupCrypto.open(tampered, "123456".toCharArray())
                throw AssertionError("byte $i changed and the file still opened")
            } catch (_: WrongPinException) {
            } catch (_: NotABackupException) {
            }
        }
    }

    @Test(expected = NotABackupException::class)
    fun refusesOtherFiles() {
        BackupCrypto.open("PK\u0003\u0004 not a backup at all, just some zip".toByteArray(), "123456".toCharArray())
    }
}
