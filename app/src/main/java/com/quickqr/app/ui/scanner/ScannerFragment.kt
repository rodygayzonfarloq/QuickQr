package com.quickqr.app.ui.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.quickqr.app.databinding.FragmentScannerBinding
import com.quickqr.app.ui.result.ResultActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerFragment : Fragment() {

    private var _binding: FragmentScannerBinding? = null
    private val binding get() = _binding!!

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService

    // Shared between live-camera analysis and gallery scanning, and explicitly closed
    // in onDestroyView - each call site previously created (and leaked) its own client.
    private val barcodeScanner: BarcodeScanner by lazy { BarcodeScanning.getClient() }

    private var hasNavigated = false
    private var torchOn = false
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var toneGenerator: ToneGenerator? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            binding.permissionOverlay.visibility = View.GONE
            startCamera()
        }
        // If denied, the permission overlay (with its own "Scan a Photo Instead" button)
        // stays visible - no extra toast needed.
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) scanImageFromUri(uri) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnGallery.setOnClickListener { launchGalleryPicker() }
        binding.btnGalleryFromOverlay.setOnClickListener { launchGalleryPicker() }
        binding.btnFlash.setOnClickListener { toggleTorch() }
        binding.btnSwitchCamera.setOnClickListener { switchCamera() }
        binding.btnGrantPermission.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        val hasCameraHardware = requireContext().packageManager
            .hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

        when {
            !hasCameraHardware -> {
                binding.previewView.visibility = View.GONE
                binding.scanFrame.visibility = View.GONE
                binding.btnFlash.visibility = View.GONE
                binding.btnSwitchCamera.visibility = View.GONE
                binding.textHint.text = "No camera on this device \u2014 use the gallery button to scan a photo"
            }
            hasCameraPermission() -> startCamera()
            else -> binding.permissionOverlay.visibility = View.VISIBLE
        }

        // Hide the flip button entirely if the device only exposes one camera.
        val hasFrontCamera = requireContext().packageManager
            .hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
        if (!hasFrontCamera) {
            binding.btnSwitchCamera.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        hasNavigated = false
    }

    private fun launchGalleryPicker() {
        pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        // Guards against ProcessCameraProvider's async callback landing after the user has
        // already switched tabs (onDestroyView already ran and cleared the binding).
        if (!isAdded || _binding == null) return

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = binding.previewView.surfaceProvider
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, BarcodeAnalyzer(barcodeScanner) { barcode -> handleDetectedBarcode(barcode) })
            }

        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        try {
            provider.unbindAll()
            torchOn = false
            camera = provider.bindToLifecycle(
                viewLifecycleOwner,
                selector,
                preview,
                analysis
            )
            binding.btnFlash.visibility =
                if (camera?.cameraInfo?.hasFlashUnit() == true) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Unable to start camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bindCameraUseCases()
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        if (cam.cameraInfo.hasFlashUnit()) {
            torchOn = !torchOn
            cam.cameraControl.enableTorch(torchOn)
        } else {
            Toast.makeText(requireContext(), "No flash available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleDetectedBarcode(barcode: Barcode) {
        if (hasNavigated || !isAdded) return
        val value = barcode.rawValue ?: return
        hasNavigated = true
        playSuccessFeedback()
        val intent = Intent(requireContext(), ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_CONTENT, value)
            putExtra(ResultActivity.EXTRA_FORMAT, barcode.format)
        }
        startActivity(intent)
    }

    /** Short vibration + beep so a successful scan is felt/heard, not just seen. */
    private fun playSuccessFeedback() {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = ContextCompat.getSystemService(requireContext(), VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ContextCompat.getSystemService(requireContext(), Vibrator::class.java)
            }
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            // Non-critical - a missing vibrator motor shouldn't block the scan result.
        }

        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneGenerator = tone
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            // Non-critical - some devices/streams can refuse to allocate a ToneGenerator.
        }
    }

    private fun scanImageFromUri(uri: Uri) {
        val image = try {
            InputImage.fromFilePath(requireContext(), uri)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Couldn't read that image", Toast.LENGTH_SHORT).show()
            return
        }
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                val barcode = barcodes.firstOrNull { !it.rawValue.isNullOrEmpty() }
                if (barcode != null) {
                    handleDetectedBarcode(barcode)
                } else {
                    Toast.makeText(requireContext(), "No code found in that image", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Couldn't scan that image", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        barcodeScanner.close()
        toneGenerator?.release()
        toneGenerator = null
        _binding = null
    }
}
