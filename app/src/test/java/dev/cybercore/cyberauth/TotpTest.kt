package dev.cybercore.cyberauth

import org.junit.Assert.assertEquals
import org.junit.Test

class TotpTest {
    @Test fun rfc6238Sha1Vectors() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" // RFC 4226 test key, Base32
        val expected = mapOf(59L to "287082", 1_111_111_109L to "081804", 1_111_111_111L to "050471", 1_234_567_890L to "005924", 2_000_000_000L to "279037", 20_000_000_000L to "353130")
        expected.forEach { (time, code) -> assertEquals("time=$time", code, Totp.code(secret, time)) }
    }

    @Test fun parsesOtpAuthUrl() {
        val account = Totp.parseOtpAuth("otpauth://totp/Example%3Aalice%40example.com?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Example&digits=6&period=30")
        assertEquals("Example", account.issuer)
        assertEquals("alice@example.com", account.label)
        assertEquals(6, account.digits)
    }

    @Test fun rfc6238Sha256AndSha512Vectors() {
        val secret256 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA"
        val secret512 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNA"
        assertEquals("46119246", Totp.code(secret256, 59, TotpAlgorithm.SHA256, 8))
        assertEquals("90693936", Totp.code(secret512, 59, TotpAlgorithm.SHA512, 8))
    }
}
