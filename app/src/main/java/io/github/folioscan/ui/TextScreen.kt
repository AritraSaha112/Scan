package io.github.folioscan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.folioscan.core.Exporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextScreen(vm: AppViewModel, docId: String, snackbar: SnackbarHostState) {
    val doc = vm.doc(docId)
    if (doc == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val activity = LocalContext.current.findActivity()
    val running = docId in vm.ocrRunning

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton { vm.back() } },
                title = { Text("Text in scan") },
                actions = {
                    IconButton(onClick = { vm.copyText(doc) }) { Icon(Icons.Outlined.ContentCopy, "Copy all") }
                    IconButton(onClick = { Exporter.shareText(activity, doc.name, doc.fullText) }) { Icon(Icons.Outlined.Share, "Share text") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!doc.ocrComplete) {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (running) "Recognising text…" else "Some pages haven't been read yet.",
                            Modifier.weight(1f),
                        )
                        if (running) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else TextButton(onClick = { vm.launchOcr(docId, notify = true) }) { Text("Recognise") }
                    }
                }
            }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    doc.pages.forEachIndexed { i, p ->
                        Text("Page ${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(
                            p.text.ifBlank { if (p.ocrDone) "(no text found)" else "…" },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                }
            }
        }
    }
}
