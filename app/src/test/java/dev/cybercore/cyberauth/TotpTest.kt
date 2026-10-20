package dev.cybercore.cyberauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.qrcode.QRCodeWriter
import java.util.EnumMap

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

    @Test fun decodesAnAuthenticatorSetupQr() {
        val payload = "otpauth://totp/GitHub%3Araven?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=GitHub"
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 360, 360)
        val luminance = ByteArray(matrix.width * matrix.height) { index ->
            val x = index % matrix.width
            val y = index / matrix.width
            if (matrix[x, y]) 0.toByte() else 255.toByte()
        }
        val source = PlanarYUVLuminanceSource(luminance, matrix.width, matrix.height, 0, 0, matrix.width, matrix.height, false)
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
            put(DecodeHintType.TRY_HARDER, true)
        }
        assertEquals(payload, decodeQr(source, hints))
        assertEquals("GitHub", Totp.parseOtpAuth(payload).issuer)
    }

    @Test fun compactsPaddedCameraRowsAndHandlesNonUnitPixelStride() {
        val paddedRows = byteArrayOf(1, 2, 3, 99, 99, 4, 5, 6)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), compactYPlane(paddedRows, 3, 2, 5, 1)!!)
        val interleavedPixels = byteArrayOf(7, 0, 8, 0, 99, 9, 0, 10, 0)
        assertArrayEquals(byteArrayOf(7, 8, 9, 10), compactYPlane(interleavedPixels, 2, 2, 5, 2)!!)
        assertNull(compactYPlane(byteArrayOf(1, 2), 3, 1, 3, 1))
    }

    @Test fun rfc6238Sha256AndSha512Vectors() {
        val secret256 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA"
        val secret512 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNA"
        assertEquals("46119246", Totp.code(secret256, 59, TotpAlgorithm.SHA256, 8))
        assertEquals("90693936", Totp.code(secret512, 59, TotpAlgorithm.SHA512, 8))
    }
}
