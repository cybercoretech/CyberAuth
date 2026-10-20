package dev.cybercore.cyberauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class EncryptedBackupTest {
    private val entries = listOf(TotpAccount("test-id", "Example", "alice@example.com", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"))

    @Test fun encryptedBackupRoundTripsAcrossStoreCodec() {
        val password = "correct horse battery staple".toCharArray()
        val file = EncryptedBackup.encrypt(entries, password)
        assertFalse(String(file, Charsets.ISO_8859_1).contains(entries.single().secret))
        assertEquals(entries, EncryptedBackup.decrypt(file, password))
        password.fill('\u0000')
    }

    @Test fun wrongPassphraseDoesNotDecrypt() {
        val file = EncryptedBackup.encrypt(entries, "backup phrase number one".toCharArray())
        val error = assertThrows(IllegalArgumentException::class.java) {
            EncryptedBackup.decrypt(file, "backup phrase number two".toCharArray())
        }
        assertEquals("Wrong backup passphrase or damaged backup file", error.message)
    }

    @Test fun tamperingIsDetected() {
        val file = EncryptedBackup.encrypt(entries, "correct horse battery staple".toCharArray())
        file[file.lastIndex] = (file.last().toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedBackup.decrypt(file, "correct horse battery staple".toCharArray())
        }
    }
}
