package io.github.folioscan.core

import android.app.Activity
import android.content.IntentSender
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning

/**
 * Launches the on-device document scanner: live edge detection, auto-capture,
 * perspective crop, stain/finger cleanup, filters, multi-page and gallery import.
 */
object Scanner {
    fun start(activity: Activity, onIntent: (IntentSender) -> Unit, onError: (String) -> Unit) {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(100)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { onIntent(it) }
            .addOnFailureListener { onError(it.message ?: "Scanner is not available on this device") }
    }
}
