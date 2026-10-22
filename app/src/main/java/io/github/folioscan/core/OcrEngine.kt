package io.github.folioscan.core

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.folioscan.data.OcrLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/** On-device OCR (ML Kit, bundled models — works offline). */
class OcrEngine {
    suspend fun recognize(file: File, devanagari: Boolean): List<OcrLine> {
        val bmp = withContext(Dispatchers.IO) { ImageOps.decodeSampled(file, 2400) }
        val client = if (devanagari) {
            TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        } else {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
        try {
            val result = client.process(InputImage.fromBitmap(bmp, 0)).await()
            val w = bmp.width.toFloat()
            val h = bmp.height.toFloat()
            return result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                line.boundingBox?.let { b -> OcrLine(line.text, b.left / w, b.top / h, b.right / w, b.bottom / h) }
            }
        } finally {
            client.close()
            bmp.recycle()
        }
    }
}
