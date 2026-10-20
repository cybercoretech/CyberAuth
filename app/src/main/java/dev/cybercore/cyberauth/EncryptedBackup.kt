package dev.cybercore.cyberauth

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable backup envelope. The passphrase is independent of the device's Android Keystore key. */
internal object EncryptedBackup {
    private val magic = "CYBAKUP1".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 600_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BITS = 256
    const val MAX_FILE_BYTES = 5 * 1024 * 1024
    const val MIN_PASSPHRASE_CHARS = 12
    private const val HEADER_BYTES = 8 + 4 + 4 + SALT_BYTES + NONCE_BYTES

    fun encrypt(accounts: List<TotpAccount>, passphrase: CharArray, random: SecureRandom = SecureRandom()): ByteArray {
        require(passphrase.size >= MIN_PASSPHRASE_CHARS) { "Use a backup passphrase with at least $MIN_PASSPHRASE_CHARS characters" }
        require(passphrase.size <= 1024) { "Backup passphrase is too long" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = header(salt, nonce)
        val key = deriveKey(passphrase, salt)
        return try {
            val plaintext = AccountCodec.encode(accounts)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                cipher.updateAAD(header)
                val ciphertext = cipher.doFinal(plaintext)
                (header + ciphertext).also { require(it.size <= MAX_FILE_BYTES) { "Backup is too large" } }
            } finally {
                plaintext.fill(0)
            }
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(file: ByteArray, passphrase: CharArray): List<TotpAccount> {
        require(passphrase.size >= MIN_PASSPHRASE_CHARS) { "Backup passphrase is too short" }
        require(passphrase.size <= 1024) { "Backup passphrase is too long" }
        require(file.size in (HEADER_BYTES + 16)..MAX_FILE_BYTES) { "This is not a supported CyberAuth backup" }
        val buffer = ByteBuffer.wrap(file)
        val foundMagic = ByteArray(magic.size).also(buffer::get)
        require(foundMagic.contentEquals(magic)) { "This is not a supported CyberAuth backup" }
        val version = buffer.int
        require(version == 1) { "This backup version is not supported" }
        val iterations = buffer.int
        require(iterations == ITERATIONS) { "This backup uses unsupported key-derivation settings" }
        val salt = ByteArray(SALT_BYTES).also(buffer::get)
        val nonce = ByteArray(NONCE_BYTES).also(buffer::get)
        val header = file.copyOfRange(0, HEADER_BYTES)
        val ciphertext = file.copyOfRange(HEADER_BYTES, file.size)
        val key = deriveKey(passphrase, salt)
        val plaintext = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(header)
                doFinal(ciphertext)
            }
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("Wrong backup passphrase or damaged backup file")
        } finally {
            key.fill(0)
            ciphertext.fill(0)
        }
        return try {
            AccountCodec.decode(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun header(salt: ByteArray, nonce: ByteArray): ByteArray = ByteArrayOutputStream(HEADER_BYTES).also { bytes ->
        DataOutputStream(bytes).use { out ->
            out.write(magic)
            out.writeInt(1)
            out.writeInt(ITERATIONS)
            out.write(salt)
            out.write(nonce)
        }
    }.toByteArray()

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
