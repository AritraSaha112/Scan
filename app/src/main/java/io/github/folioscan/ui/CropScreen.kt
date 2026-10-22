package io.github.folioscan.ui

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.folioscan.core.ImageOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val FULL = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f))

/** Re-crop with four free corners (perspective correction), like re-adjusting borders. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(vm: AppViewModel, docId: String, pageId: String) {
    val page = vm.doc(docId)?.pages?.firstOrNull { it.id == pageId }
    if (page == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val file = vm.file(docId, page)
    val bmp by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { ImageOps.decodeSampled(file, 1600).asImageBitmap() }
    }
    val corners = remember { mutableStateListOf<Offset>().apply { addAll(FULL) } }
    val primary = MaterialTheme.colorScheme.primary
    val handleRadius = with(LocalDensity.current) { 11.dp.toPx() }
    val touchSlop = with(LocalDensity.current) { 48.dp.toPx() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton { vm.back() } },
                title = { Text("Adjust borders") },
                actions = { TextButton(onClick = { corners.clear(); corners.addAll(FULL) }) { Text("Reset") } },
            )
        },
        bottomBar = {
            BottomAppBar {
                Text(
                    "Drag the corners to the page edges",
                    Modifier.padding(start = 16.dp).weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = { vm.crop(docId, pageId, corners.map { PointF(it.x, it.y) }) },
                    modifier = Modifier.padding(end = 16.dp),
                ) { Text("Apply") }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            val b = bmp
            if (b == null) {
                CircularProgressIndicator()
            } else {
                var size by remember { mutableStateOf(IntSize.Zero) }
                var active by remember { mutableIntStateOf(-1) }
                Box(
                    Modifier
                        .aspectRatio(b.width.toFloat() / b.height)
                        .onSizeChanged { size = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { o ->
                                    var best = -1
                                    var bestD = Float.MAX_VALUE
                                    corners.forEachIndexed { i, c ->
                                        val dx = c.x * size.width - o.x
                                        val dy = c.y * size.height - o.y
                                        val d = dx * dx + dy * dy
                                        if (d < bestD) { bestD = d; best = i }
                                    }
                                    active = if (bestD <= touchSlop * touchSlop) best else -1
                                },
                                onDragEnd = { active = -1 },
                                onDragCancel = { active = -1 },
                            ) { change, drag ->
                                change.consume()
                                val i = active
                                if (i >= 0 && size.width > 0 && size.height > 0) {
                                    val c = corners[i]
                                    corners[i] = Offset(
                                        (c.x + drag.x / size.width).coerceIn(0f, 1f),
                                        (c.y + drag.y / size.height).coerceIn(0f, 1f),
                                    )
                                }
                            }
                        },
                ) {
                    Image(b, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    Canvas(Modifier.fillMaxSize()) {
                        val pts = corners.map { Offset(it.x * this.size.width, it.y * this.size.height) }
                        val path = Path().apply {
                            moveTo(pts[0].x, pts[0].y)
                            for (k in 1..3) lineTo(pts[k].x, pts[k].y)
                            close()
                        }
                        drawPath(path, primary.copy(alpha = 0.14f))
                        drawPath(path, primary, style = Stroke(width = 2.dp.toPx()))
                        pts.forEachIndexed { k, p ->
                            drawCircle(Color.White, radius = handleRadius, center = p)
                            drawCircle(primary, radius = handleRadius, center = p, style = Stroke(width = if (k == active) 4.dp.toPx() else 2.5.dp.toPx()))
                        }
                    }
                }
            }
        }
    }
}
