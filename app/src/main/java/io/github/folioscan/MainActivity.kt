package io.github.folioscan

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.folioscan.ui.AppViewModel
import io.github.folioscan.ui.FolioRoot
import io.github.folioscan.ui.FolioTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent { FolioTheme { FolioRoot(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Images or a PDF shared into the app from Gallery, Files, WhatsApp, etc. */
    private fun handleShare(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(streamExtra(intent))
            Intent.ACTION_SEND_MULTIPLE -> streamListExtra(intent)
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        if (intent.type == "application/pdf") vm.importPdf(uris.first()) else vm.onScanned(uris, null)
    }

    @Suppress("DEPRECATION")
    private fun streamExtra(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streamListExtra(i: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)) ?: emptyList()
}
