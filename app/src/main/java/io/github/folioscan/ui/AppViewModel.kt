package io.github.folioscan.ui

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.folioscan.core.Exporter
import io.github.folioscan.core.Filter
import io.github.folioscan.core.ImageOps
import io.github.folioscan.core.OcrEngine
import io.github.folioscan.core.PdfBuilder
import io.github.folioscan.core.Quality
import io.github.folioscan.data.DocumentRepository
import io.github.folioscan.data.Page
import io.github.folioscan.data.Prefs
import io.github.folioscan.data.ScanDoc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Doc(val id: String) : Screen
    data class PageEdit(val docId: String, val pageId: String) : Screen
    data class Crop(val docId: String, val pageId: String) : Screen
    data class Stamp(val docId: String, val pageId: String, val kind: StampKind) : Screen
    data class SignPad(val docId: String?, val pageId: String?) : Screen
    data class Text(val docId: String) : Screen
}

enum class StampKind { SIGNATURE, TEXT }
enum class ExportFormat { PDF, JPEG }
enum class ExportDest { SHARE, SAVE }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DocumentRepository(app)
    val prefs = Prefs(app)
    private val ocr = OcrEngine()
    private val signatureFile = File(app.filesDir, "signature.png")

    var docs by mutableStateOf<List<ScanDoc>>(emptyList())
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var ocrRunning by mutableStateOf<Set<String>>(emptySet())
        private set
    var signatureVersion by mutableIntStateOf(0)
        private set

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    // ---------- navigation ----------
    private val stack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1
    fun navigate(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replaceTop(s: Screen) { stack[stack.lastIndex] = s }
    private fun backToHome() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }

    init { refresh() }

    fun refresh() { viewModelScope.launch { docs = repo.list() } }
    fun doc(id: String): ScanDoc? = docs.firstOrNull { it.id == id }
    fun file(docId: String, page: Page): File = repo.pageFile(docId, page)
    fun toast(msg: String) { _messages.trySend(msg) }

    private fun work(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = label
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                toast(e.message ?: "Something went wrong")
            } finally {
                busy = null
                docs = repo.list()
            }
        }
    }

    private fun defaultName(prefix: String = "Scan") =
        "$prefix " + SimpleDateFormat("d MMM yyyy HH.mm", Locale.getDefault()).format(Date())

    // ---------- create ----------
    fun onScanned(uris: List<Uri>, appendTo: String?) = work("Saving scan…") {
        val doc = if (appendTo == null) repo.create(defaultName(), uris) else repo.append(appendTo, uris)
        if (doc != null) {
            if (appendTo == null) navigate(Screen.Doc(doc.id)) else toast("Added ${uris.size} page(s)")
            if (prefs.autoOcr) launchOcr(doc.id)
        }
    }

    fun importPdf(uri: Uri) = work("Importing PDF…") {
        val doc = repo.createFromPdf(defaultName("Imported"), uri)
        navigate(Screen.Doc(doc.id))
        if (prefs.autoOcr) launchOcr(doc.id)
    }

    // ---------- OCR ----------
    fun launchOcr(docId: String, notify: Boolean = false) {
        if (docId in ocrRunning) return
        ocrRunning = ocrRunning + docId
        viewModelScope.launch {
            try {
                runOcr(docId)
                if (notify) toast("Text recognised")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                toast("Text recognition failed: ${e.message}")
            } finally {
                ocrRunning = ocrRunning - docId
                docs = repo.list()
            }
        }
    }

    fun rerunOcr(docId: String) {
        viewModelScope.launch {
            repo.update(docId, touch = false) { d -> d.copy(pages = d.pages.map { it.copy(ocr = emptyList(), ocrDone = false) }) }
            docs = repo.list()
            launchOcr(docId, notify = true)
        }
    }

    private suspend fun runOcr(docId: String) {
        val doc = withContext(Dispatchers.IO) { repo.read(docId) } ?: return
        for (p in doc.pages.filter { !it.ocrDone }) {
            val f = repo.pageFile(docId, p)
            if (!f.exists()) continue
            val lines = ocr.recognize(f, prefs.devanagari)
            repo.update(docId, touch = false) { d ->
                d.copy(pages = d.pages.map { if (it.id == p.id && it.file == p.file) it.copy(ocr = lines, ocrDone = true) else it })
            }
            docs = repo.list()
        }
    }

    // ---------- document ops ----------
    fun rename(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch { repo.update(id) { it.copy(name = n) }; docs = repo.list() }
    }

    fun delete(ids: Collection<String>) = work("Deleting…") {
        ids.forEach { repo.delete(it) }
        if (screen is Screen.Doc && (screen as Screen.Doc).id in ids) backToHome()
    }

    fun merge(ids: List<String>) = work("Merging…") {
        val d = repo.merge(defaultName("Merged"), ids)
        navigate(Screen.Doc(d.id))
        if (prefs.autoOcr) launchOcr(d.id)
    }

    // ---------- page ops ----------
    private fun editImage(docId: String, pageId: String, label: String, after: () -> Unit = {}, op: (Bitmap) -> Bitmap) =
        work(label) {
            val doc = withContext(Dispatchers.IO) { repo.read(docId) } ?: return@work
            val page = doc.pages.firstOrNull { it.id == pageId } ?: return@work
            val out = withContext(Dispatchers.Default) {
                val src = ImageOps.decodeSampled(repo.pageFile(docId, page), 4096)
                val r = op(src)
                if (r !== src) src.recycle()
                r
            }
            repo.replacePageImage(docId, pageId, out)
            out.recycle()
            after()
            if (prefs.autoOcr) launchOcr(docId)
        }

    fun rotate(docId: String, pageId: String, deg: Float) = editImage(docId, pageId, "Rotating…") { ImageOps.rotate(it, deg) }
    fun filter(docId: String, pageId: String, f: Filter) = editImage(docId, pageId, "Applying ${f.label}…") { ImageOps.applyFilter(it, f) }
    fun crop(docId: String, pageId: String, corners: List<PointF>) =
        editImage(docId, pageId, "Cropping…", after = { back() }) { ImageOps.perspectiveCrop(it, corners) }
    fun stamp(docId: String, pageId: String, stamp: Bitmap, cx: Float, cy: Float, wf: Float) =
        editImage(docId, pageId, "Placing…", after = { back() }) { ImageOps.stamp(it, stamp, cx, cy, wf) }

    fun revert(docId: String, pageId: String) = work("Restoring original…") {
        repo.revertPage(docId, pageId)
        if (prefs.autoOcr) launchOcr(docId)
    }

    fun deletePage(docId: String, pageId: String) = work("Deleting page…") {
        val docGone = repo.deletePage(docId, pageId)
        if (docGone) backToHome() else back()
    }

    fun movePage(docId: String, pageId: String, delta: Int) {
        viewModelScope.launch { repo.movePage(docId, pageId, delta); docs = repo.list() }
    }

    // ---------- signature ----------
    fun hasSignature() = signatureFile.exists()
    fun loadSignature(): Bitmap? = if (signatureFile.exists()) BitmapFactory.decodeFile(signatureFile.path) else null
    fun deleteSignature() { signatureFile.delete(); signatureVersion++; toast("Signature removed") }

    fun saveSignature(strokes: List<List<PointF>>, strokeWidth: Float, color: Int, docId: String?, pageId: String?) =
        work("Saving signature…") {
            withContext(Dispatchers.Default) {
                val bmp = ImageOps.renderStrokes(strokes, strokeWidth, color)
                signatureFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
            }
            signatureVersion++
            val below = stack.getOrNull(stack.lastIndex - 1)
            when {
                below is Screen.Stamp -> back()
                docId != null && pageId != null -> replaceTop(Screen.Stamp(docId, pageId, StampKind.SIGNATURE))
                else -> back()
            }
        }

    // ---------- export ----------
    private fun safeName(doc: ScanDoc) =
        doc.name.replace(Regex("[^A-Za-z0-9 ._-]"), "_").trim().ifEmpty { "scan" }

    private fun exportDir() = File(getApplication<Application>().cacheDir, "exports").apply {
        deleteRecursively(); mkdirs()
    }

    private suspend fun buildPdf(docId: String, quality: Quality, password: String?): Pair<ScanDoc, File> {
        val needOcr = withContext(Dispatchers.IO) { repo.read(docId) }?.ocrComplete == false
        if (needOcr && docId !in ocrRunning) { busy = "Recognising text…"; runOcr(docId) }
        val doc = withContext(Dispatchers.IO) { repo.read(docId) } ?: error("Document not found")
        busy = "Building PDF…"
        val out = File(exportDir(), "${safeName(doc)}.pdf")
        withContext(Dispatchers.Default) {
            PdfBuilder.build(doc.pages.map { repo.pageFile(docId, it) to it.ocr }, quality, password, out)
        }
        return doc to out
    }

    fun export(activity: Activity, docId: String, format: ExportFormat, quality: Quality, password: String?, dest: ExportDest) =
        work("Preparing…") {
            val app = getApplication<Application>()
            when (format) {
                ExportFormat.PDF -> {
                    val (_, pdf) = buildPdf(docId, quality, password)
                    if (dest == ExportDest.SHARE) Exporter.share(activity, listOf(pdf), "application/pdf")
                    else withContext(Dispatchers.IO) { Exporter.saveToDownloads(app, pdf, "application/pdf") }
                        .also { toast("Saved to Downloads/FolioScan") }
                }
                ExportFormat.JPEG -> {
                    val doc = withContext(Dispatchers.IO) { repo.read(docId) } ?: error("Document not found")
                    busy = "Exporting images…"
                    val files = withContext(Dispatchers.Default) {
                        PdfBuilder.exportJpegs(doc.pages.map { repo.pageFile(docId, it) }, quality, exportDir(), safeName(doc))
                    }
                    if (dest == ExportDest.SHARE) Exporter.share(activity, files, "image/jpeg")
                    else {
                        withContext(Dispatchers.IO) { files.forEach { Exporter.saveToPictures(app, it) } }
                        toast("Saved ${files.size} image(s) to Pictures/FolioScan")
                    }
                }
            }
        }

    fun print(activity: Activity, docId: String) = work("Preparing to print…") {
        val (doc, pdf) = buildPdf(docId, Quality.HIGH, null)
        Exporter.print(activity, pdf, doc.name)
    }

    fun copyText(doc: ScanDoc) {
        Exporter.copyText(getApplication(), doc.name, doc.fullText)
        toast("Text copied")
    }
}
