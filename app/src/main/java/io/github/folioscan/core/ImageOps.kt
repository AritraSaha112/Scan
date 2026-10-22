package io.github.folioscan.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

enum class Filter(val label: String) {
    AUTO("Auto colour"),
    GRAY("Grayscale"),
    BW("B&W document"),
    WHITEBOARD("Whiteboard"),
    LIGHTEN("Lighten"),
}

object ImageOps {

    fun decodeSampled(file: File, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Cannot read image ${file.name}")
        val longSide = max(bmp.width, bmp.height)
        if (longSide <= maxSide) return bmp
        val s = maxSide.toFloat() / longSide
        val out = Bitmap.createScaledBitmap(
            bmp, (bmp.width * s).roundToInt().coerceAtLeast(1), (bmp.height * s).roundToInt().coerceAtLeast(1), true,
        )
        if (out !== bmp) bmp.recycle()
        return out
    }

    fun saveJpeg(bmp: Bitmap, file: File, quality: Int) {
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    fun rotate(b: Bitmap, degrees: Float): Bitmap {
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    // ---------- filters ----------

    private fun contrast(c: Float, brightness: Float): ColorMatrix {
        val t = (1f - c) * 128f + brightness
        return ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }

    private fun withMatrix(b: Bitmap, cm: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(b, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }

    fun applyFilter(b: Bitmap, f: Filter): Bitmap = when (f) {
        Filter.AUTO -> withMatrix(b, ColorMatrix().apply { setSaturation(1.25f); postConcat(contrast(1.25f, 12f)) })
        Filter.GRAY -> withMatrix(b, ColorMatrix().apply { setSaturation(0f); postConcat(contrast(1.15f, 8f)) })
        Filter.WHITEBOARD -> withMatrix(b, ColorMatrix().apply { setSaturation(1.1f); postConcat(contrast(1.6f, 48f)) })
        Filter.LIGHTEN -> withMatrix(b, contrast(1.05f, 30f))
        Filter.BW -> adaptiveThreshold(b)
    }

    /**
     * Crisp black-and-white "document" look: each pixel is compared with the mean
     * of its neighbourhood (separable box filter), which removes shadows and uneven lighting.
     */
    private fun adaptiveThreshold(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            gray[i] = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        }
        val r = (max(w, h) / 40).coerceIn(8, 60)
        val tmp = IntArray(w * h)
        // horizontal mean
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            var count = 0
            for (x in 0..minOf(r, w - 1)) { sum += gray[row + x]; count++ }
            for (x in 0 until w) {
                tmp[row + x] = sum / count
                val add = x + r + 1
                val sub = x - r
                if (add < w) { sum += gray[row + add]; count++ }
                if (sub >= 0) { sum -= gray[row + sub]; count-- }
            }
        }
        // vertical mean -> threshold
        for (x in 0 until w) {
            var sum = 0
            var count = 0
            for (y in 0..minOf(r, h - 1)) { sum += tmp[y * w + x]; count++ }
            for (y in 0 until h) {
                val mean = sum / count
                val i = y * w + x
                px[i] = if (gray[i] * 100 < mean * 88) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
                val add = y + r + 1
                val sub = y - r
                if (add < h) { sum += tmp[add * w + x]; count++ }
                if (sub >= 0) { sum -= tmp[sub * w + x]; count-- }
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    // ---------- geometry ----------

    /** Perspective crop. [corners] = TL, TR, BR, BL as fractions (0..1) of the image. */
    fun perspectiveCrop(b: Bitmap, corners: List<PointF>): Bitmap {
        val p = corners.map { PointF(it.x * b.width, it.y * b.height) }
        val outW = max(dist(p[0], p[1]), dist(p[3], p[2])).roundToInt().coerceAtLeast(1)
        val outH = max(dist(p[0], p[3]), dist(p[1], p[2])).roundToInt().coerceAtLeast(1)
        val src = floatArrayOf(p[0].x, p[0].y, p[1].x, p[1].y, p[2].x, p[2].y, p[3].x, p[3].y)
        val dst = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
        val m = Matrix()
        m.setPolyToPoly(src, 0, dst, 0, 4)
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(b, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun dist(a: PointF, b: PointF) = hypot(a.x - b.x, a.y - b.y)

    /** Draws [stamp] centred at (cx, cy) with width = wf × page width (all fractions). */
    fun stamp(base: Bitmap, stamp: Bitmap, cx: Float, cy: Float, wf: Float): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val w = wf * out.width
        val h = w * stamp.height / stamp.width
        val left = cx * out.width - w / 2
        val top = cy * out.height - h / 2
        Canvas(out).drawBitmap(stamp, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** Renders hand-drawn strokes into a tightly trimmed transparent bitmap. */
    fun renderStrokes(strokes: List<List<PointF>>, strokeWidth: Float, color: Int): Bitmap {
        val all = strokes.flatten()
        val pad = strokeWidth * 2
        val minX = all.minOf { it.x } - pad
        val minY = all.minOf { it.y } - pad
        val maxX = all.maxOf { it.x } + pad
        val maxY = all.maxOf { it.y } + pad
        val scale = 2f
        val bmp = Bitmap.createBitmap(
            ((maxX - minX) * scale).roundToInt().coerceAtLeast(1),
            ((maxY - minY) * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth * scale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.filter { it.size > 1 }.forEach { s ->
            val path = Path()
            path.moveTo((s[0].x - minX) * scale, (s[0].y - minY) * scale)
            for (i in 1 until s.size) path.lineTo((s[i].x - minX) * scale, (s[i].y - minY) * scale)
            c.drawPath(path, paint)
        }
        return bmp
    }

    fun textBitmap(text: String, color: Int): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = 110f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val lines = text.lines()
        val fm = paint.fontMetrics
        val lineH = fm.descent - fm.ascent
        val w = (lines.maxOf { paint.measureText(it) } + 8f).roundToInt().coerceAtLeast(1)
        val h = (lineH * lines.size + 8f).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        lines.forEachIndexed { i, l -> c.drawText(l, 4f, 4f - fm.ascent + i * lineH, paint) }
        return bmp
    }
}
