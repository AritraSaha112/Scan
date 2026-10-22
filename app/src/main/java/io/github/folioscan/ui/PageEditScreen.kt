package io.github.folioscan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.FilterBAndW
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.folioscan.core.Filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditScreen(vm: AppViewModel, docId: String, pageId: String, snackbar: SnackbarHostState) {
    val doc = vm.doc(docId)
    val idx = doc?.pages?.indexOfFirst { it.id == pageId } ?: -1
    if (doc == null || idx < 0) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val page = doc.pages[idx]
    var showFilters by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton { vm.back() } },
                title = { Text("Page ${idx + 1} of ${doc.pages.size}") },
                actions = {
                    IconButton(enabled = idx > 0, onClick = { vm.replaceTop(Screen.PageEdit(docId, doc.pages[idx - 1].id)) }) {
                        Icon(Icons.Outlined.ChevronLeft, "Previous page")
                    }
                    IconButton(enabled = idx < doc.pages.lastIndex, onClick = { vm.replaceTop(Screen.PageEdit(docId, doc.pages[idx + 1].id)) }) {
                        Icon(Icons.Outlined.ChevronRight, "Next page")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Move earlier") }, enabled = idx > 0,
                                onClick = { menu = false; vm.movePage(docId, pageId, -1) },
                            )
                            DropdownMenuItem(
                                text = { Text("Move later") }, enabled = idx < doc.pages.lastIndex,
                                onClick = { menu = false; vm.movePage(docId, pageId, 1) },
                            )
                            DropdownMenuItem(
                                text = { Text("Undo all edits (original)") }, enabled = page.file != page.original,
                                onClick = { menu = false; vm.revert(docId, pageId) },
                            )
                            DropdownMenuItem(text = { Text("Delete page") }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column {
                if (showFilters) {
                    Surface(tonalElevation = 3.dp) {
                        LazyRow(
                            Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                AssistChip(
                                    onClick = { vm.revert(docId, pageId) },
                                    label = { Text("Original") },
                                    enabled = page.file != page.original,
                                )
                            }
                            items(Filter.entries) { f ->
                                AssistChip(onClick = { vm.filter(docId, pageId, f) }, label = { Text(f.label) })
                            }
                        }
                    }
                }
                BottomAppBar {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ActionItem(Icons.Outlined.Crop, "Crop") { vm.navigate(Screen.Crop(docId, pageId)) }
                        ActionItem(Icons.Outlined.RotateRight, "Rotate") { vm.rotate(docId, pageId, 90f) }
                        ActionItem(Icons.Outlined.FilterBAndW, "Filters") { showFilters = !showFilters }
                        ActionItem(Icons.Outlined.Gesture, "Sign") {
                            if (vm.hasSignature()) vm.navigate(Screen.Stamp(docId, pageId, StampKind.SIGNATURE))
                            else vm.navigate(Screen.SignPad(docId, pageId))
                        }
                        ActionItem(Icons.Outlined.TextFields, "Add text") { vm.navigate(Screen.Stamp(docId, pageId, StampKind.TEXT)) }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = vm.file(docId, page),
                contentDescription = "Page ${idx + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog("Delete this page?", "This can't be undone.", "Delete", onDismiss = { confirmDelete = false }) {
            confirmDelete = false; vm.deletePage(docId, pageId)
        }
    }
}
