package io.github.folioscan.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.github.folioscan.core.Scanner

@Composable
fun FolioRoot(vm: AppViewModel) {
    val activity = LocalContext.current.findActivity()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val scanTarget = remember { mutableStateOf<String?>(null) }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
            val uris = result?.pages?.map { it.imageUri }.orEmpty()
            if (uris.isNotEmpty()) vm.onScanned(uris, scanTarget.value)
        }
    }
    val startScan: (String?) -> Unit = { target ->
        scanTarget.value = target
        Scanner.start(
            activity,
            onIntent = { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) },
            onError = { vm.toast(it) },
        )
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPdf(uri)
    }

    BackHandler(enabled = vm.canGoBack) { vm.back() }

    Box {
        when (val s = vm.screen) {
            Screen.Home -> HomeScreen(vm, snackbar, onScan = { startScan(null) }, onImportPdf = { pdfPicker.launch(arrayOf("application/pdf")) })
            Screen.Settings -> SettingsScreen(vm)
            is Screen.Doc -> DocumentScreen(vm, s.id, snackbar, onAddPages = { startScan(s.id) })
            is Screen.PageEdit -> PageEditScreen(vm, s.docId, s.pageId, snackbar)
            is Screen.Crop -> CropScreen(vm, s.docId, s.pageId)
            is Screen.Stamp -> StampScreen(vm, s.docId, s.pageId, s.kind)
            is Screen.SignPad -> SignPadScreen(vm, s.docId, s.pageId)
            is Screen.Text -> TextScreen(vm, s.docId, snackbar)
        }
        vm.busy?.let { BusyDialog(it) }
    }
}
