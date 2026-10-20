package dev.cybercore.cyberauth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class QrScannerActivity : ComponentActivity() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val delivered = AtomicBoolean(false)
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private lateinit var torchButton: Button
    private lateinit var scanner: BarcodeScanner
    private var camera: Camera? = null
    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else finishCanceled(getString(R.string.qr_camera_permission_needed))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
        buildScannerView()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else requestCamera.launch(Manifest.permission.CAMERA)
    }

    private fun buildScannerView() {
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        status = TextView(this).apply {
            text = getString(R.string.qr_scanner_prompt)
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(28, 26, 28, 26)
            setBackgroundColor(0xCC101722.toInt())
        }
        torchButton = Button(this).apply {
            text = getString(R.string.qr_torch_on)
            visibility = View.GONE
            setOnClickListener { camera?.let { activeCamera ->
                val enable = activeCamera.cameraInfo.torchState.value != androidx.camera.core.TorchState.ON
                activeCamera.cameraControl.enableTorch(enable)
                text = getString(if (enable) R.string.qr_torch_off else R.string.qr_torch_on)
            } }
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(previewView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            addView(View(this@QrScannerActivity).apply {
                isClickable = false
                isFocusable = false
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(0x00000000)
                    setStroke(dp(3), 0xFF35E4EC.toInt())
                    cornerRadius = dp(18).toFloat()
                }
            }, FrameLayout.LayoutParams(dp(264), dp(264), Gravity.CENTER))
            addView(torchButton, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = dp(24); bottomMargin = dp(48)
            })
            addView(Button(this@QrScannerActivity).apply { text = getString(R.string.cancel); setOnClickListener { finishCanceled(getString(R.string.qr_scan_canceled)) } },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(48) })
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
                analysis.setAnalyzer(cameraExecutor, ::analyze)
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                torchButton.visibility = if (camera?.cameraInfo?.hasFlashUnit() == true) View.VISIBLE else View.GONE
                runOnUiThread { status.text = getString(R.string.qr_scanner_ready) }
            }.onFailure { finishCanceled(getString(R.string.qr_camera_start_failed)) }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
    private fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || delivered.get()) {
            imageProxy.close()
            return
        }
        try {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    val value = barcodes.firstNotNullOfOrNull { it.rawValue } ?: return@addOnSuccessListener
                    if (isTotpQrValue(value) && delivered.compareAndSet(false, true)) {
                        runOnUiThread {
                            setResult(RESULT_OK, intent.putExtra(EXTRA_OTP_URI, value))
                            finish()
                        }
                    } else if (!delivered.get()) {
                        runOnUiThread { status.text = getString(R.string.qr_not_totp) }
                    }
                }
                .addOnFailureListener {
                    if (!delivered.get()) runOnUiThread { status.text = getString(R.string.qr_scanner_ready) }
                }
                .addOnCompleteListener { imageProxy.close() }
        } catch (_: Exception) {
            imageProxy.close()
        }
    }

    private fun finishCanceled(reason: String) {
        setResult(RESULT_CANCELED, intent.putExtra(EXTRA_SCAN_ERROR, reason))
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        cameraExecutor.shutdown()
        if (::scanner.isInitialized) scanner.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OTP_URI = "cyberauth.extra.otp_uri"
        const val EXTRA_SCAN_ERROR = "cyberauth.extra.scan_error"
    }
}

internal fun isTotpQrValue(value: String): Boolean = value.startsWith("otpauth://totp/", ignoreCase = true)
