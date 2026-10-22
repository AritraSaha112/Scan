package io.github.folioscan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.folioscan.data.ScanDoc

private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, snackbar: SnackbarHostState, onScan: () -> Unit, onImportPdf: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var renameTarget by remember { mutableStateOf<ScanDoc?>(null) }
    var confirmDelete by remember { mutableStateOf<Set<String>?>(null) }
    val docs = vm.docs
    val filtered = remember(docs, query) {
        if (query.isBlank()) docs
        else docs.filter { it.name.contains(query, ignoreCase = true) || it.fullText.contains(query, ignoreCase = true) }
    }

    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    Scaffold(
        topBar = {
            if (selected.isEmpty()) {
                TopAppBar(
                    title = { Text("Folio Scan", fontWeight = FontWeight.SemiBold) },
                    actions = {
                        IconButton(onClick = onImportPdf) { Icon(Icons.Outlined.PictureAsPdf, "Import PDF") }
                        IconButton(onClick = { vm.navigate(Screen.Settings) }) { Icon(Icons.Outlined.Settings, "Settings") }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Cancel") } },
                    actions = {
                        if (selected.size >= 2) {
                            TextButton(onClick = { vm.merge(selected.toList()); selected = emptySet() }) { Text("Merge") }
                        }
                        IconButton(onClick = { confirmDelete = selected }) { Icon(Icons.Outlined.Delete, "Delete") }
                    },
                )
            }
        },
        floatingActionButton = {
            if (selected.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onScan,
                    icon = { Icon(Icons.Outlined.CameraAlt, null) },
                    text = { Text("Scan") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search names and text inside scans") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )
            if (filtered.isEmpty()) {
                EmptyState(searching = query.isNotBlank())
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.id }) { doc ->
                        DocRow(
                            vm = vm,
                            doc = doc,
                            selected = doc.id in selected,
                            selectionMode = selected.isNotEmpty(),
                            onClick = {
                                if (selected.isNotEmpty()) selected = selected.toggle(doc.id)
                                else vm.navigate(Screen.Doc(doc.id))
                            },
                            onLongClick = { selected = selected.toggle(doc.id) },
                            onRename = { renameTarget = doc },
                            onDelete = { confirmDelete = setOf(doc.id) },
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { d ->
        TextInputDialog("Rename", d.name, onDismiss = { renameTarget = null }) { vm.rename(d.id, it); renameTarget = null }
    }
    confirmDelete?.let { ids ->
        ConfirmDialog(
            title = if (ids.size == 1) "Delete document?" else "Delete ${ids.size} documents?",
            text = "This can't be undone.",
            confirm = "Delete",
            onDismiss = { confirmDelete = null },
        ) {
            vm.delete(ids); confirmDelete = null; selected = emptySet()
        }
    }
}

@Composable
private fun EmptyState(searching: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Description, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            if (searching) "No scans match your search" else "No scans yet",
            style = MaterialTheme.typography.titleMedium,
        )
        if (!searching) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap Scan to capture documents, receipts, whiteboards or ID cards. Text is recognised on your phone, so every scan becomes searchable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocRow(
    vm: AppViewModel,
    doc: ScanDoc,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val first = doc.pages.firstOrNull()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 56.dp, height = 72.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (first != null) {
                    AsyncImage(
                        model = vm.file(doc.id, first),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(doc.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatDate(doc.modified)} · ${doc.pages.size} page${if (doc.pages.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (doc.id in vm.ocrRunning) {
                    Text("Recognising text…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            } else {
                Box {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}
