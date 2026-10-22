package io.github.folioscan.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.folioscan.core.ImageOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val INK_BLACK = 0xFF111111.toInt()
private const val INK_BLUE = 0xFF1A3E9E.toInt()

/** Place a signature or a text label on a page: drag to move, pinch to resize. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StampScreen(vm: AppViewModel, docId: String, pageId: String, kind: StampKind) {
    val page = vm.doc(docId)?.pages?.firstOrNull { it.id == pageId }
    if (page == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val file = vm.file(docId, page)
    val base by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { ImageOps.decodeSampled(file, 1600).asImageBitmap() }
    }
    var text by remember { mutableStateOf("") }
    var blue by remember { mutableStateOf(false) }
    val sigVersion = vm.signatureVersion
    val stampBmp = remember(kind, sigVersion, text, blue) {
        when (kind) {
            StampKind.SIGNATURE -> vm.loadSignature()
            StampKind.TEXT -> if (text.isBlank()) null else ImageOps.textBitmap(text, if (blue) INK_BLUE else INK_BLACK)
        }
    }
    val stampImg = remember(stampBmp) { stampBmp?.asImageBitmap() }
    var cx by remember { mutableFloatStateOf(0.5f) }
    var cy by remember { mutableFloatStateOf(if (kind == StampKind.SIGNATURE) 0.82f else 0.5f) }
    var wf by remember { mutableFloatStateOf(if (kind == StampKind.SIGNATURE) 0.35f else 0.45f) }
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton { vm.back() } },
                title = { Text(if (kind == StampKind.SIGNATURE) "Sign" else "Add text") },
                actions = {
                    if (kind == StampKind.SIGNATURE) {
                        TextButton(onClick = { vm.navigate(Screen.SignPad(docId, pageId)) }) { Text("New signature") }
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Text(
                    "Drag to move · pinch to resize",
                    Modifier.padding(start = 16.dp).weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    enabled = stampBmp != null,
                    onClick = { stampBmp?.let { vm.stamp(docId, pageId, it, cx, cy, wf) } },
                    modifier = Modifier.padding(end = 16.dp),
                ) { Text("Apply") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                val b = base
                if (b == null) {
                    CircularProgressIndicator()
                } else {
                    var boxSize by remember { mutableStateOf(IntSize.Zero) }
                    Box(
                        Modifier
                            .aspectRatio(b.width.toFloat() / b.height)
                            .onSizeChanged { boxSize = it }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    if (boxSize.width > 0 && boxSize.height > 0) {
                                        cx = (cx + pan.x / boxSize.width).coerceIn(0f, 1f)
                                        cy = (cy + pan.y / boxSize.height).coerceIn(0f, 1f)
                                        wf = (wf * zoom).coerceIn(0.05f, 1f)
                                    }
                                }
                            },
                    ) {
                        Image(b, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        if (stampImg != null && boxSize.width > 0) {
                            val wPx = wf * boxSize.width
                            val hPx = wPx * stampImg.height / stampImg.width
                            Image(
                                stampImg,
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier
                                    .offset { IntOffset((cx * boxSize.width - wPx / 2).roundToInt(), (cy * boxSize.height - hPx / 2).roundToInt()) }
                                    .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
                                    .border(1.dp, primary.copy(alpha = 0.7f)),
                            )
                        }
                    }
                }
            }
            if (kind == StampKind.TEXT) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Text (name, date, initials…)") },
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !blue, onClick = { blue = false }, label = { Text("Black ink") })
                        FilterChip(selected = blue, onClick = { blue = true }, label = { Text("Blue ink") })
                    }
                }
            }
        }
    }
}
