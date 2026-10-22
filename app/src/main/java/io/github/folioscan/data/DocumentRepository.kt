package io.github.folioscan.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import io.github.folioscan.core.ImageOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Stores every document as a folder: files/docs/<id>/meta.json plus one JPEG per page.
 * Everything stays on the device; nothing is uploaded anywhere.
 */
class DocumentRepository(private val context: Context) {
    private val root = File(context.filesDir, "docs").apply { mkdirs() }
    private val mutex = Mutex()

    fun dir(id: String) = File(root, id)
    fun pageFile(docId: String, page: Page) = File(dir(docId), page.file)

    suspend fun list(): List<ScanDoc> = withContext(Dispatchers.IO) {
        root.listFiles()?.mapNotNull { read(it.name) }?.sortedByDescending { it.modified } ?: emptyList()
    }

    fun read(id: String): ScanDoc? = runCatching {
        fromJson(JSONObject(File(dir(id), "meta.json").readText()))
    }.getOrNull()

    private fun write(doc: ScanDoc) {
        val d = dir(doc.id).apply { mkdirs() }
        val tmp = File(d, "meta.json.tmp")
        tmp.writeText(toJson(doc).toString())
        tmp.renameTo(File(d, "meta.json"))
    }

    private fun newPageName() = "p_${UUID.randomUUID()}.jpg"

    private fun importUri(d: File, uri: Uri): Page {
        val name = newPageName()
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open image")
        input.use { i -> File(d, name).outputStream().use { i.copyTo(it) } }
        return Page(UUID.randomUUID().toString(), name, name)
    }

    suspend fun create(name: String, uris: List<Uri>): ScanDoc = withContext(Dispatchers.IO) {
        mutex.withLock {
            val id = UUID.randomUUID().toString()
            val d = dir(id).apply { mkdirs() }
            val pages = uris.map { importUri(d, it) }
            val now = System.currentTimeMillis()
            ScanDoc(id, name, now, now, pages).also { write(it) }
        }
    }

    /** Renders every page of a PDF into JPEG pages (encrypted PDFs throw SecurityException). */
    suspend fun createFromPdf(name: String, uri: Uri): ScanDoc = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val d = dir(id).apply { mkdirs() }
        val pages = mutableListOf<Page>()
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Cannot open PDF")
            pfd.use {
                val renderer = PdfRenderer(it)
                try {
                    for (i in 0 until renderer.pageCount) {
                        val pg = renderer.openPage(i)
                        try {
                            // ~200 dpi, capped at 3000 px on the long side
                            var scale = 200f / 72f
                            val longSide = max(pg.width, pg.height) * scale
                            if (longSide > 3000f) scale *= 3000f / longSide
                            val w = (pg.width * scale).roundToInt().coerceAtLeast(1)
                            val h = (pg.height * scale).roundToInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val n = newPageName()
                            ImageOps.saveJpeg(bmp, File(d, n), 90)
                            bmp.recycle()
                            pages += Page(UUID.randomUUID().toString(), n, n)
                        } finally {
                            pg.close()
                        }
                    }
                } finally {
                    renderer.close()
                }
            }
        } catch (e: Exception) {
            d.deleteRecursively()
            throw if (e is SecurityException) IllegalStateException("This PDF is password-protected") else e
        }
        if (pages.isEmpty()) { d.deleteRecursively(); error("The PDF has no pages") }
        val now = System.currentTimeMillis()
        mutex.withLock { ScanDoc(id, name, now, now, pages).also { write(it) } }
    }

    suspend fun append(id: String, uris: List<Uri>): ScanDoc? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cur = read(id) ?: return@withLock null
            val added = uris.map { importUri(dir(id), it) }
            cur.copy(pages = cur.pages + added, modified = System.currentTimeMillis()).also { write(it) }
        }
    }

    /** Atomic read-modify-write of a document's metadata. */
    suspend fun update(id: String, touch: Boolean = true, f: (ScanDoc) -> ScanDoc): ScanDoc? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val cur = read(id) ?: return@withLock null
                val next = f(cur).let { if (touch) it.copy(modified = System.currentTimeMillis()) else it }
                write(next)
                next
            }
        }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock { dir(id).deleteRecursively() }
    }

    suspend fun replacePageImage(docId: String, pageId: String, bmp: Bitmap) = withContext(Dispatchers.IO) {
        val newName = newPageName()
        ImageOps.saveJpeg(bmp, File(dir(docId), newName), 92)
        var old: Page? = null
        update(docId) { d ->
            d.copy(pages = d.pages.map {
                if (it.id == pageId) { old = it; it.copy(file = newName, ocr = emptyList(), ocrDone = false) } else it
            })
        }
        old?.let { if (it.file != it.original) File(dir(docId), it.file).delete() }
    }

    suspend fun revertPage(docId: String, pageId: String) = withContext(Dispatchers.IO) {
        var old: Page? = null
        update(docId) { d ->
            d.copy(pages = d.pages.map {
                if (it.id == pageId && it.file != it.original) {
                    old = it; it.copy(file = it.original, ocr = emptyList(), ocrDone = false)
                } else it
            })
        }
        old?.let { File(dir(docId), it.file).delete() }
    }

    /** Returns true if the document became empty and was removed. */
    suspend fun deletePage(docId: String, pageId: String): Boolean = withContext(Dispatchers.IO) {
        var removed: Page? = null
        val doc = update(docId) { d ->
            removed = d.pages.firstOrNull { it.id == pageId }
            d.copy(pages = d.pages.filterNot { it.id == pageId })
        }
        removed?.let {
            File(dir(docId), it.file).delete()
            File(dir(docId), it.original).delete()
        }
        if (doc != null && doc.pages.isEmpty()) { delete(docId); true } else false
    }

    suspend fun movePage(docId: String, pageId: String, delta: Int) {
        update(docId) { d ->
            val i = d.pages.indexOfFirst { it.id == pageId }
            val j = i + delta
            if (i < 0 || j !in d.pages.indices) d
            else d.copy(pages = d.pages.toMutableList().apply { add(j, removeAt(i)) })
        }
    }

    suspend fun merge(name: String, ids: List<String>): ScanDoc = withContext(Dispatchers.IO) {
        mutex.withLock {
            val newId = UUID.randomUUID().toString()
            val d = dir(newId).apply { mkdirs() }
            val pages = ids.mapNotNull { read(it) }.flatMap { src ->
                src.pages.map { p ->
                    val n = newPageName()
                    File(dir(src.id), p.file).copyTo(File(d, n))
                    p.copy(id = UUID.randomUUID().toString(), file = n, original = n)
                }
            }
            val now = System.currentTimeMillis()
            ScanDoc(newId, name, now, now, pages).also { write(it) }
        }
    }

    // ---------- JSON ----------

    private fun toJson(d: ScanDoc) = JSONObject().apply {
        put("id", d.id)
        put("name", d.name)
        put("created", d.created)
        put("modified", d.modified)
        put("pages", JSONArray().apply {
            d.pages.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("file", p.file)
                    put("original", p.original)
                    put("ocrDone", p.ocrDone)
                    put("ocr", JSONArray().apply {
                        p.ocr.forEach { l ->
                            put(JSONArray().apply {
                                put(l.text); put(l.l.toDouble()); put(l.t.toDouble())
                                put(l.r.toDouble()); put(l.b.toDouble())
                            })
                        }
                    })
                })
            }
        })
    }

    private fun fromJson(o: JSONObject): ScanDoc {
        val pa = o.getJSONArray("pages")
        val pages = (0 until pa.length()).map { i ->
            val po = pa.getJSONObject(i)
            val oa = po.optJSONArray("ocr") ?: JSONArray()
            val file = po.getString("file")
            Page(
                id = po.getString("id"),
                file = file,
                original = po.optString("original", file),
                ocrDone = po.optBoolean("ocrDone", false),
                ocr = (0 until oa.length()).map { j ->
                    val a = oa.getJSONArray(j)
                    OcrLine(
                        a.getString(0), a.getDouble(1).toFloat(), a.getDouble(2).toFloat(),
                        a.getDouble(3).toFloat(), a.getDouble(4).toFloat(),
                    )
                },
            )
        }
        return ScanDoc(o.getString("id"), o.getString("name"), o.getLong("created"), o.getLong("modified"), pages)
    }
}
