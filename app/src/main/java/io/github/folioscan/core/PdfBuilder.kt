package io.github.folioscan.core

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import io.github.folioscan.data.OcrLine
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

enum class Quality(val label: String, val maxSide: Int, val jpeg: Float) {
    HIGH("High", 3200, 0.85f),
    MEDIUM("Medium", 2200, 0.70f),
    LOW("Small", 1500, 0.50f),
}

object PdfBuilder {
    private const val PAGE_WIDTH = 595f // A4 width in points; height follows the scan's aspect ratio

    /**
     * Builds a PDF: each page is the scan image with an invisible, selectable and
     * searchable OCR text layer on top (like a "searchable PDF").
     */
    fun build(pages: List<Pair<File, List<OcrLine>>>, quality: Quality, password: String?, out: File) {
        PDDocument().use { doc ->
            val font: PDFont = PDType1Font.HELVETICA
            for ((file, lines) in pages) {
                val bmp = ImageOps.decodeSampled(file, quality.maxSide)
                val pw = PAGE_WIDTH
                val ph = pw * bmp.height / bmp.width
                val page = PDPage(PDRectangle(pw, ph))
                doc.addPage(page)
                val img = JPEGFactory.createFromImage(doc, bmp, quality.jpeg)
                bmp.recycle()
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(img, 0f, 0f, pw, ph)
                    if (lines.isNotEmpty()) {
                        cs.beginText()
                        cs.setRenderingMode(RenderingMode.NEITHER)
                        for (line in lines) {
                            val s = sanitize(font, line.text)
                            if (s.isBlank()) continue
                            val boxW = (line.r - line.l) * pw
                            val boxH = (line.b - line.t) * ph
                            val size = max(boxH * 0.85f, 1f)
                            val textW = font.getStringWidth(s) / 1000f * size
                            cs.setFont(font, size)
                            cs.setHorizontalScaling(if (textW > 0f) boxW / textW * 100f else 100f)
                            cs.setTextMatrix(Matrix.getTranslateInstance(line.l * pw, ph - line.b * ph + boxH * 0.2f))
                            cs.showText(s)
                        }
                        cs.endText()
                    }
                }
            }
            if (!password.isNullOrEmpty()) {
                val policy = StandardProtectionPolicy(password, password, AccessPermission())
                policy.encryptionKeyLength = 128
                doc.protect(policy)
            }
            doc.save(out)
        }
    }

    /** The standard font only covers Latin-1; characters it cannot encode become spaces. */
    private fun sanitize(font: PDFont, text: String): String = buildString {
        for (ch in text) {
            val ok = try { font.encode(ch.toString()); true } catch (e: Exception) { false }
            append(if (ok) ch else ' ')
        }
    }

    fun exportJpegs(files: List<File>, quality: Quality, outDir: File, baseName: String): List<File> =
        files.mapIndexed { i, f ->
            val b = ImageOps.decodeSampled(f, quality.maxSide)
            val o = File(outDir, "${baseName}_${i + 1}.jpg")
            ImageOps.saveJpeg(b, o, (quality.jpeg * 100).roundToInt())
            b.recycle()
            o
        }
}
