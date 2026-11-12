package dev.aalman.budstikke.backup

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
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
 * The file is the magic `BUDSTIKKE1`, a 16-byte random salt, the PBKDF2 iteration count as
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
