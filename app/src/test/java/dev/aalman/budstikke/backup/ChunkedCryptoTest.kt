package dev.aalman.budstikke.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class ChunkedCryptoTest {

    private val iterations = 1_000
    private val pin = "123456".toCharArray()

    private fun seal(plain: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.sealing(out, pin, iterations).use { it.write(plain) }
        return out.toByteArray()
    }

    private fun open(sealed: ByteArray, with: CharArray = pin): ByteArray =
        BackupCrypto.opening(sealed.inputStream(), with).use { it.readBytes() }

    @Test
    fun opensWhatItSealedAcrossManyChunks() {
        val plain = Random(7).nextBytes(BackupCrypto.CHUNK_BYTES * 3 + 123)
        val sealed = seal(plain)
        assertTrue(BackupCrypto.isChunked(sealed))
        assertArrayEquals(plain, open(sealed))
    }

    @Test
    fun opensAnEmptyOrExactlyFullLastChunk() {
        assertArrayEquals(ByteArray(0), open(seal(ByteArray(0))))
        val full = Random(1).nextBytes(BackupCrypto.CHUNK_BYTES * 2)
        assertArrayEquals(full, open(seal(full)))
    }

    @Test(expected = WrongPinException::class)
    fun refusesAWrongPin() {
        open(seal("hello".toByteArray()), "654321".toCharArray())
    }

    @Test
    fun refusesAFileCutShortAtAChunkBoundary() {
        val plain = Random(3).nextBytes(BackupCrypto.CHUNK_BYTES * 2 + 10)
        val sealed = seal(plain)
        // Header, then the first two chunks whole: each is a length and the plain bytes plus a tag.
        val header = 10 + 16 + 4 + 7
        val chunk = 4 + BackupCrypto.CHUNK_BYTES + 16
        assertRefused(sealed.copyOf(header + chunk * 2))
    }

    @Test
    fun refusesSwappedChunks() {
        val plain = Random(4).nextBytes(BackupCrypto.CHUNK_BYTES * 2 + 10)
        val sealed = seal(plain)
        val header = 10 + 16 + 4 + 7
        val chunk = 4 + BackupCrypto.CHUNK_BYTES + 16
        val swapped = sealed.copyOf()
        System.arraycopy(sealed, header + chunk, swapped, header, chunk)
        System.arraycopy(sealed, header, swapped, header + chunk, chunk)
        assertRefused(swapped)
    }

    @Test
    fun refusesAChangedByte() {
        val sealed = seal(Random(5).nextBytes(1000))
        for (i in listOf(12, 40, sealed.size / 2, sealed.lastIndex)) {
            assertRefused(sealed.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() })
        }
    }

    private fun assertRefused(bytes: ByteArray) {
        val refused = try {
            open(bytes)
            false
        } catch (_: WrongPinException) {
            true
        } catch (_: NotABackupException) {
            true
        }
        assertTrue(refused)
    }
}
