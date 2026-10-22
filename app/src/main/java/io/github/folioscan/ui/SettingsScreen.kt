package io.github.folioscan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.folioscan.core.Quality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    var autoOcr by remember { mutableStateOf(vm.prefs.autoOcr) }
    var devanagari by remember { mutableStateOf(vm.prefs.devanagari) }
    var quality by remember { mutableStateOf(vm.prefs.quality) }
    val hasSig = remember(vm.signatureVersion) { vm.hasSignature() }

    Scaffold(
        topBar = { TopAppBar(navigationIcon = { BackButton { vm.back() } }, title = { Text("Settings") }) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text("Recognise text automatically") },
                supportingContent = { Text("Makes every scan searchable and copyable. Runs on your phone, offline.") },
                trailingContent = { Switch(checked = autoOcr, onCheckedChange = { autoOcr = it; vm.prefs.autoOcr = it }) },
            )
            HorizontalDivider()
            Text("Text recognition script", Modifier.padding(start = 16.dp, top = 16.dp), style = MaterialTheme.typography.titleSmall)
            listOf(false to "Latin (English and other Latin-script languages)", true to "Devanagari (Hindi, Marathi, Nepali, Sanskrit) + English")
                .forEach { (value, label) ->
                    ListItem(
                        modifier = Modifier.selectable(selected = devanagari == value) { devanagari = value; vm.prefs.devanagari = value },
                        headlineContent = { Text(label) },
                        leadingContent = { RadioButton(selected = devanagari == value, onClick = null) },
                    )
                }
            HorizontalDivider()
            Text("Default export quality", Modifier.padding(start = 16.dp, top = 16.dp), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Quality.entries.forEach { q ->
                    FilterChip(selected = quality == q, onClick = { quality = q; vm.prefs.quality = q }, label = { Text(q.label) })
                }
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Saved signature") },
                supportingContent = { Text(if (hasSig) "Used when you tap Sign on a page" else "None yet") },
                trailingContent = {
                    Row {
                        if (hasSig) TextButton(onClick = { vm.deleteSignature() }) { Text("Remove") }
                        TextButton(onClick = { vm.navigate(Screen.SignPad(null, null)) }) { Text(if (hasSig) "Replace" else "Create") }
                    }
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("About Scan 1.0") },
                supportingContent = {
                    Text(
                        "Open-source (Apache-2.0). Scans, text recognition and PDFs are made on your phone; nothing is uploaded. " +
                            "Uses Google ML Kit for scanning and OCR, and PdfBox-Android for PDF files.",
                    )
                },
            )
        }
    }
}
