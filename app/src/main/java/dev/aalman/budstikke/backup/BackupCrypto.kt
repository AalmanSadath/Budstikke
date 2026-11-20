package dev.aalman.budstikke.backup

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The file does not start like a Budstikke backup. */
class NotABackupException : Exception()

/** The PIN is wrong, or the file was changed or damaged after it was written; nothing can tell these apart. */
class WrongPinException : Exception()

/**
 * Seals backups with a PIN, using only the platform's own cryptography.
 *
 * Backups are written in the chunked format [sealing] describes, which never holds a whole
 * backup in memory; pictures and attachments make them too large for that. Files from
 * before it, in the single-block format below, are still opened by [open].
 *
 * The single-block file is the magic `BUDSTIKKE1`, a 16-byte random salt, the PBKDF2 iteration count as
 * a 4-byte big-endian integer, a 12-byte random nonce, then the AES-256-GCM ciphertext with
 * its 16-byte tag. The key is PBKDF2-HMAC-SHA256 of the PIN and salt. Everything before the
 * ciphertext is bound in as associated data, so changing any byte of the file, header
 * included, fails the tag check, and a wrong PIN fails it the same way.
 */
object BackupCrypto {

    private val MAGIC = "BUDSTIKKE1".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private val HEADER_BYTES = MAGIC.size + SALT_BYTES + 4 + NONCE_BYTES

    /** OWASP's 2023 count for PBKDF2-HMAC-SHA256: about a second on a phone, per guess. */
    const val ITERATIONS = 600_000

    /** Shorter PINs are refused: a backup can be copied anywhere and guessed at offline. */
    const val MIN_PIN_LENGTH = 6

    /** Refuses files that ask for so many iterations that opening them would hang the app. */
    private const val MAX_ITERATIONS = 10_000_000

    fun seal(plain: ByteArray, pin: CharArray, iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = ByteArrayOutputStream(HEADER_BYTES).also { out ->
            DataOutputStream(out).apply {
                write(MAGIC)
                write(salt)
                writeInt(iterations)
                write(nonce)
            }
        }.toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(pin, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    /**
     * The plain bytes of a sealed backup. Nothing is returned until the whole file has been
     * checked, so a damaged or tampered file never yields part of its content.
     */
    fun open(sealed: ByteArray, pin: CharArray): ByteArray {
        if (sealed.size < HEADER_BYTES + TAG_BITS / 8 || !sealed.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw NotABackupException()
        }
        val input = DataInputStream(sealed.inputStream(MAGIC.size, HEADER_BYTES - MAGIC.size))
        val salt = ByteArray(SALT_BYTES).also(input::readFully)
        val iterations = input.readInt()
        val nonce = ByteArray(NONCE_BYTES).also(input::readFully)
        if (iterations !in 1..MAX_ITERATIONS) throw NotABackupException()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(pin, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(sealed, 0, HEADER_BYTES)
        return try {
            cipher.doFinal(sealed, HEADER_BYTES, sealed.size - HEADER_BYTES)
        } catch (_: AEADBadTagException) {
            throw WrongPinException()
        } catch (_: GeneralSecurityException) {
            throw WrongPinException()
        }
    }

    private val MAGIC_CHUNKED = "BUDSTIKKE2".toByteArray(Charsets.US_ASCII)
    private const val PREFIX_BYTES = 7
    private val CHUNKED_HEADER_BYTES = MAGIC_CHUNKED.size + SALT_BYTES + 4 + PREFIX_BYTES

    /** Plain bytes per chunk; each is sealed and checked on its own. */
    const val CHUNK_BYTES = 64 * 1024

    /** Whether [start], the first bytes of a file, are a chunked backup's. */
    fun isChunked(start: ByteArray): Boolean =
        start.size >= MAGIC_CHUNKED.size && start.copyOfRange(0, MAGIC_CHUNKED.size).contentEquals(MAGIC_CHUNKED)

    /** How many bytes of a file [isChunked] needs to tell. */
    val MAGIC_BYTES: Int get() = MAGIC_CHUNKED.size

    /**
     * A stream that seals what is written to it into [out], chunk by chunk; closing it
     * writes the last chunk and closes [out].
     *
     * The file is the magic `BUDSTIKKE2`, a 16-byte random salt, the PBKDF2 iteration count
     * as a 4-byte big-endian integer and a 7-byte random nonce prefix. Then come the chunks,
     * each a 4-byte big-endian length and that many bytes of AES-256-GCM ciphertext and tag,
     * sealing up to [CHUNK_BYTES] plain bytes. A chunk's nonce is the prefix, its 4-byte
     * number from 0 and a byte that is 1 for the last chunk and 0 for the rest, so chunks
     * cannot be reordered, repeated or cut off at the end without failing a check. The whole
     * header is associated data of every chunk.
     */
    fun sealing(out: OutputStream, pin: CharArray, iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom()): OutputStream {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val prefix = ByteArray(PREFIX_BYTES).also(random::nextBytes)
        val header = ByteArrayOutputStream(CHUNKED_HEADER_BYTES).also { bytes ->
            DataOutputStream(bytes).apply {
                write(MAGIC_CHUNKED)
                write(salt)
                writeInt(iterations)
                write(prefix)
            }
        }.toByteArray()
        out.write(header)
        return SealingStream(DataOutputStream(out), key(pin, salt, iterations), header, prefix)
    }

    /**
     * The plain bytes of a chunked backup read from [input], checked chunk by chunk as they
     * are read. A wrong PIN or a changed, reordered or shortened file throws
     * [WrongPinException] at the chunk where it shows, so a caller that must not act on a
     * damaged file reads it through once before acting on it. [keys] keeps the key from the
     * PIN between reads of the same file, as working it out takes about a second.
     */
    fun opening(input: InputStream, pin: CharArray, keys: MutableMap<String, SecretKeySpec> = HashMap()): InputStream {
        val data = DataInputStream(input)
        val header = ByteArray(CHUNKED_HEADER_BYTES)
        try {
            data.readFully(header)
        } catch (_: EOFException) {
            throw NotABackupException()
        }
        if (!isChunked(header)) throw NotABackupException()
        val fields = DataInputStream(header.inputStream(MAGIC_CHUNKED.size, CHUNKED_HEADER_BYTES - MAGIC_CHUNKED.size))
        val salt = ByteArray(SALT_BYTES).also(fields::readFully)
        val iterations = fields.readInt()
        val prefix = ByteArray(PREFIX_BYTES).also(fields::readFully)
        if (iterations !in 1..MAX_ITERATIONS) throw NotABackupException()
        val id = salt.joinToString("") { "%02x".format(it) } + ":" + iterations
        val key = keys.getOrPut(id) { key(pin, salt, iterations) }
        return OpeningStream(data, key, header, prefix)
    }

    private fun nonce(prefix: ByteArray, index: Int, last: Boolean): ByteArray =
        ByteBuffer.allocate(NONCE_BYTES).put(prefix).putInt(index).put(if (last) 1 else 0).array()

    private class SealingStream(
        private val out: DataOutputStream,
        private val key: SecretKeySpec,
        private val header: ByteArray,
        private val prefix: ByteArray,
    ) : OutputStream() {
        private val buffer = ByteArray(CHUNK_BYTES)
        private var filled = 0
        private var index = 0
        private var closed = false

        override fun write(b: Int) {
            if (filled == CHUNK_BYTES) seal(last = false)
            buffer[filled++] = b.toByte()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var at = off
            val end = off + len
            while (at < end) {
                // A full chunk is sealed only once more follows, so the last one is known.
                if (filled == CHUNK_BYTES) seal(last = false)
                val n = minOf(CHUNK_BYTES - filled, end - at)
                System.arraycopy(b, at, buffer, filled, n)
                filled += n
                at += n
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            seal(last = true)
            out.close()
        }

        private fun seal(last: Boolean) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, index++, last)))
            cipher.updateAAD(header)
            val sealed = cipher.doFinal(buffer, 0, filled)
            out.writeInt(sealed.size)
            out.write(sealed)
            filled = 0
        }
    }

    private class OpeningStream(
        private val input: DataInputStream,
        private val key: SecretKeySpec,
        private val header: ByteArray,
        private val prefix: ByteArray,
    ) : InputStream() {
        private var plain = ByteArray(0)
        private var at = 0
        private var index = 0
        private var done = false
        // The next chunk's length, read ahead to know whether the current one is the last.
        private var nextLength: Int? = readLength()

        override fun read(): Int {
            if (!fill()) return -1
            return plain[at++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (!fill()) return -1
            val n = minOf(len, plain.size - at)
            System.arraycopy(plain, at, b, off, n)
            at += n
            return n
        }

        override fun close() = input.close()

        private fun fill(): Boolean {
            while (at == plain.size) {
                if (done) return false
                val length = nextLength ?: throw WrongPinException()
                if (length < TAG_BITS / 8 || length > CHUNK_BYTES + TAG_BITS / 8) throw WrongPinException()
                val sealed = ByteArray(length)
                try {
                    input.readFully(sealed)
                } catch (_: EOFException) {
                    throw WrongPinException()
                }
                nextLength = readLength()
                val last = nextLength == null
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, index++, last)))
                cipher.updateAAD(header)
                plain = try {
                    cipher.doFinal(sealed)
                } catch (_: GeneralSecurityException) {
                    throw WrongPinException()
                }
                at = 0
                done = last
            }
            return true
        }

        /** The next chunk's length, or null at the end of the file. */
        private fun readLength(): Int? {
            val first = input.read()
            if (first < 0) return null
            val rest = ByteArray(3)
            try {
                input.readFully(rest)
            } catch (_: EOFException) {
                throw WrongPinException()
            }
            return (first shl 24) or ((rest[0].toInt() and 0xFF) shl 16) or ((rest[1].toInt() and 0xFF) shl 8) or (rest[2].toInt() and 0xFF)
        }
    }

    private fun key(pin: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }
}
