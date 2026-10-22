package io.github.folioscan.ui

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignPadScreen(vm: AppViewModel, docId: String?, pageId: String?) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var blue by remember { mutableStateOf(false) }
    val strokePx = with(LocalDensity.current) { 3.dp.toPx() }
    val ink = if (blue) Color(0xFF1A3E9E) else Color(0xFF111111)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton { vm.back() } },
                title = { Text("Create signature") },
                actions = { TextButton(onClick = { strokes.clear(); current = emptyList() }) { Text("Clear") } },
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(Modifier.padding(start = 16.dp).weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !blue, onClick = { blue = false }, label = { Text("Black") })
                    FilterChip(selected = blue, onClick = { blue = true }, label = { Text("Blue") })
                }
                Button(
                    enabled = strokes.isNotEmpty(),
                    modifier = Modifier.padding(end = 16.dp),
                    onClick = {
                        vm.saveSignature(
                            strokes.map { s -> s.map { PointF(it.x, it.y) } },
                            strokePx,
                            if (blue) 0xFF1A3E9E.toInt() else 0xFF111111.toInt(),
                            docId, pageId,
                        )
                    },
                ) { Text("Save") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            Text(
                "Sign with your finger inside the box. Turn your phone sideways for more room.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { current = listOf(it) },
                            onDragEnd = {
                                if (current.size > 1) strokes.add(current)
                                current = emptyList()
                            },
                            onDragCancel = { current = emptyList() },
                        ) { change, _ ->
                            change.consume()
                            current = current + change.position
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    (strokes + listOf(current)).forEach { pts ->
                        if (pts.size > 1) {
                            val p = Path().apply {
                                moveTo(pts[0].x, pts[0].y)
                                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                            }
                            drawPath(p, ink, style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                    val y = size.height * 0.75f
                    drawLine(Color(0xFFBDBDBD), Offset(24f, y), Offset(size.width - 24f, y), strokeWidth = 1.5f)
                }
            }
        }
    }
}
