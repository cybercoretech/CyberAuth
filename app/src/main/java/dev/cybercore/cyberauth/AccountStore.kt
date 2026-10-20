package dev.cybercore.cyberauth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-local encrypted storage. Master-password based portable vault sync is a later milestone. */
class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("cyberauth_local", Context.MODE_PRIVATE)

    fun load(): List<TotpAccount> {
        val encoded = prefs.getString(DATA_KEY, null) ?: return emptyList()
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val input = DataInputStream(bytes.inputStream())
            val nonce = ByteArray(input.readInt().also { require(it in 12..16) })
            input.readFully(nonce)
            val ciphertext = ByteArray(input.available())
            input.readFully(ciphertext)
            AccountCodec.decode(Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, nonce))
                doFinal(ciphertext)
            })
        }.getOrElse { throw IllegalStateException("CyberAuth could not unlock its local account store", it) }
    }

    @Suppress("UseKtx")
    fun save(accounts: List<TotpAccount>) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val packed = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(cipher.iv.size)
                out.write(cipher.iv)
                out.write(cipher.doFinal(AccountCodec.encode(accounts)))
            }
        }.toByteArray()
        check(prefs.edit().putString(DATA_KEY, Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) { "Could not save encrypted accounts" }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build())
            generateKey()
        }
    }

    private companion object {
        const val DATA_KEY = "encrypted_accounts_v1"
        const val KEY_ALIAS = "cyberauth.device-storage.v1"
    }
}

internal object AccountCodec {
    private const val FORMAT_VERSION = 1

    fun encode(accounts: List<TotpAccount>): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(FORMAT_VERSION)
            out.writeInt(accounts.size)
            accounts.forEach { account ->
                out.writeUTF(account.id); out.writeUTF(account.issuer); out.writeUTF(account.label)
                out.writeUTF(account.secret); out.writeUTF(account.algorithm.name)
                out.writeInt(account.digits); out.writeInt(account.period)
            }
        }
    }.toByteArray()

    fun decode(bytes: ByteArray): List<TotpAccount> = DataInputStream(bytes.inputStream()).use { input ->
        require(input.readInt() == FORMAT_VERSION) { "Unsupported account data version" }
        val size = input.readInt().also { require(it in 0..1000) { "Account count is outside the supported range" } }
        List(size) {
            val id = input.readUTF()
            val issuer = input.readUTF()
            val label = input.readUTF()
            val secret = input.readUTF()
            val algorithm = TotpAlgorithm.valueOf(input.readUTF())
            val digits = input.readInt()
            val period = input.readInt()
            require(id.length <= 80 && issuer.length <= 256 && label.length <= 256 && secret.length <= 256) { "An account field is too long" }
            require(digits in 6..8 && period in 1..86400) { "An account has invalid TOTP settings" }
            Totp.decodeBase32(secret).fill(0)
            TotpAccount(id, issuer, label, secret, algorithm, digits, period)
        }.also { require(input.available() == 0) { "Unexpected data after account list" } }
    }
}
