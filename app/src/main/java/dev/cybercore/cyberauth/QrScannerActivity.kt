package dev.cybercore.cyberauth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.EnumMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class QrScannerActivity : ComponentActivity() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val delivered = AtomicBoolean(false)
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else finishCanceled(getString(R.string.qr_camera_permission_needed))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScannerView()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else requestCamera.launch(Manifest.permission.CAMERA)
    }

    private fun buildScannerView() {
        previewView = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        status = TextView(this).apply {
            text = getString(R.string.qr_scanner_prompt)
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(28, 26, 28, 26)
            setBackgroundColor(0xCC101722.toInt())
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(previewView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            addView(Button(this@QrScannerActivity).apply { text = getString(R.string.cancel); setOnClickListener { finishCanceled(getString(R.string.qr_scan_canceled)) } },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = 40 })
        }
        setContentView(root)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
                    .build()
                val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                    put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
                    put(DecodeHintType.TRY_HARDER, true)
                }
                analysis.setAnalyzer(cameraExecutor) { image -> analyze(image, hints) }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.onFailure { finishCanceled(getString(R.string.qr_camera_start_failed)) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy, hints: Map<DecodeHintType, Any>) {
        try {
            if (delivered.get()) return
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer.duplicate()
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val yBytes = ByteArray(buffer.remaining()).also(buffer::get)
            val fullyPaddedPlaneBytes = rowStride.toLong() * image.height
            val luminance = if (pixelStride == 1 && yBytes.size >= fullyPaddedPlaneBytes) {
                PlanarYUVLuminanceSource(yBytes, rowStride, image.height, 0, 0, image.width, image.height, false)
            } else {
                val compact = compactYPlane(yBytes, image.width, image.height, rowStride, pixelStride) ?: return
                PlanarYUVLuminanceSource(compact, image.width, image.height, 0, 0, image.width, image.height, false)
            }
            val rotated = when (image.imageInfo.rotationDegrees) {
                90 -> luminance.rotateCounterClockwise().rotateCounterClockwise().rotateCounterClockwise()
                180 -> luminance.rotateCounterClockwise().rotateCounterClockwise()
                270 -> luminance.rotateCounterClockwise()
                else -> luminance
            }
            val decoded = decodeQr(rotated, hints) ?: return
            if (decoded.startsWith("otpauth://totp/", ignoreCase = true) && delivered.compareAndSet(false, true)) {
                runOnUiThread {
                    setResult(RESULT_OK, intent.putExtra(EXTRA_OTP_URI, decoded))
                    finish()
                }
            }
        } catch (_: Exception) {
            // Ignore undecodable frames; the user can keep the camera pointed at the QR.
        } finally {
            image.close()
        }
    }

    private fun finishCanceled(reason: String) {
        setResult(RESULT_CANCELED, intent.putExtra(EXTRA_SCAN_ERROR, reason))
        finish()
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OTP_URI = "cyberauth.extra.otp_uri"
        const val EXTRA_SCAN_ERROR = "cyberauth.extra.scan_error"
    }
}

internal fun compactYPlane(data: ByteArray, width: Int, height: Int, rowStride: Int, pixelStride: Int): ByteArray? {
    if (width <= 0 || height <= 0 || rowStride <= 0 || pixelStride <= 0) return null
    val lastSample = (height - 1L) * rowStride + (width - 1L) * pixelStride
    if (lastSample >= data.size) return null
    return ByteArray(width * height).also { compact ->
        for (y in 0 until height) {
            val row = y * rowStride
            val output = y * width
            for (x in 0 until width) compact[output + x] = data[row + x * pixelStride]
        }
    }
}

internal fun decodeQr(source: LuminanceSource, hints: Map<DecodeHintType, Any>): String? {
    try {
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    } catch (_: ReaderException) {
        // Displays with uneven brightness can defeat adaptive thresholding; retry globally.
    }
    return try {
        QRCodeReader().decode(BinaryBitmap(GlobalHistogramBinarizer(source)), hints).text
    } catch (_: ReaderException) {
        null
    }
}
