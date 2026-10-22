package io.github.folioscan.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.findActivity(): Activity {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    error("No activity")
}

fun formatDate(ms: Long): String = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
}

@Composable
fun ActionItem(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
fun TextInputDialog(title: String, initial: String, confirm: String = "Save", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true) },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun BusyDialog(label: String) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Card {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
