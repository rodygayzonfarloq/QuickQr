package com.quickqr.app.ui.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Feeds every camera frame to the given ML Kit [scanner]. [onBarcodeDetected] is invoked
 * on the main thread (Task listeners default to main thread) the first time a frame
 * decodes a non-empty value; further frames are dropped via [isProcessing] until the
 * current one finishes, so we never queue up stale work behind a slow frame.
 *
 * This class does NOT own [scanner]'s lifecycle - the caller creates it and is
 * responsible for closing it (see ScannerFragment, which shares one scanner between
 * live camera analysis and gallery-photo scanning, and closes it in onDestroyView).
 */
class BarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val onBarcodeDetected: (Barcode) -> Unit
) : ImageAnalysis.Analyzer {

    private val isProcessing = AtomicBoolean(false)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || !isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val barcode = barcodes.firstOrNull { !it.rawValue.isNullOrEmpty() }
                if (barcode != null) {
                    onBarcodeDetected(barcode)
                }
            }
            .addOnCompleteListener {
                isProcessing.set(false)
                imageProxy.close()
            }
    }
}
