package io.github.folioscan.data

/** One recognised line of text; box coordinates are fractions (0..1) of the page image. */
data class OcrLine(val text: String, val l: Float, val t: Float, val r: Float, val b: Float)

/**
 * A page. [file] is the current (edited) image, [original] the untouched capture,
 * kept so any edit can be reverted.
 */
data class Page(
    val id: String,
    val file: String,
    val original: String,
    val ocrDone: Boolean = false,
    val ocr: List<OcrLine> = emptyList(),
) {
    val text: String get() = ocr.joinToString("\n") { it.text }
}

data class ScanDoc(
    val id: String,
    val name: String,
    val created: Long,
    val modified: Long,
    val pages: List<Page>,
) {
    val fullText: String get() = pages.joinToString("\n\n") { it.text }
    val ocrComplete: Boolean get() = pages.all { it.ocrDone }
}
