package io.github.folioscan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.folioscan.core.Quality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(vm: AppViewModel, docId: String, snackbar: SnackbarHostState, onAddPages: () -> Unit) {
    val doc = vm.doc(docId)
    if (doc == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val activity = LocalContext.current.findActivity()
    var rename by remember { mutableStateOf(false) }
    var exportDest by remember { mutableStateOf<ExportDest?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton { vm.back() } },
                title = {
                    Column(Modifier.clickable { rename = true }) {
                        Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${doc.pages.size} page${if (doc.pages.size == 1) "" else "s"}" +
                                if (docId in vm.ocrRunning) " · recognising text…" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { rename = true }) { Icon(Icons.Outlined.Edit, "Rename") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Re-run text recognition") }, onClick = { menu = false; vm.rerunOcr(docId) })
                            DropdownMenuItem(text = { Text("Delete document") }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ActionItem(Icons.Outlined.AddAPhoto, "Add pages", onClick = onAddPages)
                    ActionItem(Icons.Outlined.Share, "Share") { exportDest = ExportDest.SHARE }
                    ActionItem(Icons.Outlined.FileDownload, "Save") { exportDest = ExportDest.SAVE }
                    ActionItem(Icons.Outlined.TextFields, "Text") { vm.navigate(Screen.Text(docId)) }
                    ActionItem(Icons.Outlined.Print, "Print") { vm.print(activity, docId) }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(doc.pages, key = { _, p -> p.id }) { i, page ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { vm.navigate(Screen.PageEdit(docId, page.id)) },
                    ) {
                        AsyncImage(
                            model = vm.file(docId, page),
                            contentDescription = "Page ${i + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("${i + 1}", Modifier.padding(horizontal = 10.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    if (rename) {
        TextInputDialog("Rename", doc.name, onDismiss = { rename = false }) { vm.rename(docId, it); rename = false }
    }
    if (confirmDelete) {
        ConfirmDialog("Delete document?", "This can't be undone.", "Delete", onDismiss = { confirmDelete = false }) {
            confirmDelete = false; vm.delete(listOf(docId))
        }
    }
    exportDest?.let { dest ->
        ExportDialog(dest, vm.prefs.quality, onDismiss = { exportDest = null }) { format, quality, password ->
            exportDest = null
            vm.export(activity, docId, format, quality, password, dest)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDialog(
    dest: ExportDest,
    defaultQuality: Quality,
    onDismiss: () -> Unit,
    onConfirm: (ExportFormat, Quality, String?) -> Unit,
) {
    var format by remember { mutableStateOf(ExportFormat.PDF) }
    var quality by remember { mutableStateOf(defaultQuality) }
    var protect by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (dest == ExportDest.SHARE) "Share" else "Save to phone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Format", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = format == ExportFormat.PDF, onClick = { format = ExportFormat.PDF }, label = { Text("PDF") })
                    FilterChip(selected = format == ExportFormat.JPEG, onClick = { format = ExportFormat.JPEG }, label = { Text("JPEG images") })
                }
                Text("Quality (file size)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Quality.entries.forEach { q ->
                        FilterChip(selected = quality == q, onClick = { quality = q }, label = { Text(q.label) })
                    }
                }
                if (format == ExportFormat.PDF) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = protect, onCheckedChange = { protect = it })
                        Spacer(Modifier.width(12.dp))
                        Text("Password protect")
                    }
                    if (protect) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password (min 4 characters)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = format == ExportFormat.JPEG || !protect || password.length >= 4,
                onClick = { onConfirm(format, quality, if (format == ExportFormat.PDF && protect) password else null) },
            ) { Text(if (dest == ExportDest.SHARE) "Share" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
