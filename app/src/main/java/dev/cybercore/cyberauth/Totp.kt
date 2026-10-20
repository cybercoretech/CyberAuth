package dev.cybercore.cyberauth

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class TotpAlgorithm(val jcaName: String) { SHA1("HmacSHA1"), SHA256("HmacSHA256"), SHA512("HmacSHA512") }

data class TotpAccount(
    val id: String,
    val issuer: String,
    val label: String,
    val secret: String,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
)

object Totp {
    private const val BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decodeBase32(input: String): ByteArray {
        val clean = input.uppercase().filterNot { it == '=' || it.isWhitespace() || it == '-' }
        require(clean.isNotEmpty()) { "Secret is required" }
        require(clean.all { it in BASE32 }) { "Secret must use Base32 characters A–Z and 2–7" }
        val output = ByteArray(clean.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var index = 0
        for (char in clean) {
            buffer = (buffer shl 5) or BASE32.indexOf(char)
            bits += 5
            if (bits >= 8) {
                bits -= 8
                output[index++] = (buffer shr bits).toByte()
            }
        }
        require(output.size >= 10) { "Secret is too short (minimum 80 bits)" }
        return output
    }

    fun code(secret: String, epochSeconds: Long, algorithm: TotpAlgorithm = TotpAlgorithm.SHA1, digits: Int = 6, period: Int = 30): String {
        require(digits in 6..8) { "Digits must be 6, 7, or 8" }
        require(period > 0) { "Period must be positive" }
        val counter = epochSeconds / period
        val mac = Mac.getInstance(algorithm.jcaName)
        mac.init(SecretKeySpec(decodeBase32(secret), algorithm.jcaName))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        val modulus = when (digits) { 6 -> 1_000_000; 7 -> 10_000_000; else -> 100_000_000 }
        return (binary % modulus).toString().padStart(digits, '0')
    }

    fun parseOtpAuth(uri: String): TotpAccount {
        val parsed = java.net.URI(uri.trim())
        require(parsed.scheme == "otpauth" && parsed.host == "totp") { "Only TOTP otpauth:// links are supported" }
        val query = parsed.rawQuery.orEmpty().split('&').mapNotNull { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.size == 2) decode(parts[0]) to decode(parts[1]) else null
        }.toMap()
        val secret = query["secret"] ?: error("Link does not include a secret")
        val path = decode(parsed.rawPath.orEmpty().removePrefix("/").replace("+", "%20"))
        val issuer = query["issuer"]?.takeIf(String::isNotBlank) ?: path.substringBefore(':', "").takeIf(String::isNotBlank).orEmpty()
        val label = path.substringAfter(':', path).ifBlank { issuer }
        val algorithm = when (query["algorithm"]?.uppercase()) {
            null, "SHA1" -> TotpAlgorithm.SHA1
            "SHA256" -> TotpAlgorithm.SHA256
            "SHA512" -> TotpAlgorithm.SHA512
            else -> error("Unsupported TOTP algorithm")
        }
        return TotpAccount(
            id = java.util.UUID.randomUUID().toString(),
            issuer = issuer,
            label = label,
            secret = secret,
            algorithm = algorithm,
            digits = query["digits"]?.toIntOrNull() ?: 6,
            period = query["period"]?.toIntOrNull() ?: 30,
        ).also { decodeBase32(it.secret); require(it.digits in 6..8 && it.period > 0) }
    }

    private fun decode(value: String): String = java.net.URLDecoder.decode(value, Charsets.UTF_8.name())
}
