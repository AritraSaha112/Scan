package io.github.folioscan.core

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object Exporter {
    private const val FOLDER = "FolioScan"

    private fun uriFor(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)

    fun share(activity: Activity, files: List<File>, mime: String) {
        val uris = ArrayList(files.map { uriFor(activity, it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uris[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = mime; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri("", uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        activity.startActivity(Intent.createChooser(intent, "Share"))
    }

    private fun insert(ctx: Context, src: File, mime: String, collection: Uri, relativeDir: String) {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, src.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativeDir/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("Could not create file")
        resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
            ?: error("Could not write file")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    fun saveToDownloads(ctx: Context, src: File, mime: String) =
        insert(ctx, src, mime, MediaStore.Downloads.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_DOWNLOADS)

    fun saveToPictures(ctx: Context, src: File) =
        insert(ctx, src, "image/jpeg", MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_PICTURES)

    fun shareText(activity: Activity, title: String, text: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(i, "Share text"))
    }

    fun copyText(ctx: Context, label: String, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun print(activity: Activity, pdf: File, jobName: String) {
        val pm = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print(jobName, PdfPrintAdapter(pdf), null)
    }

    private class PdfPrintAdapter(private val file: File) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) { callback?.onLayoutCancelled(); return }
            val info = PrintDocumentInfo.Builder(file.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback?.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor?,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback?,
        ) {
            try {
                FileInputStream(file).use { input ->
                    FileOutputStream(destination!!.fileDescriptor).use { input.copyTo(it) }
                }
                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback?.onWriteFailed(e.message)
            }
        }
    }
}
