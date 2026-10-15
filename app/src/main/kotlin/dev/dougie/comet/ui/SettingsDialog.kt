package dev.dougie.comet.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import dev.dougie.comet.network.PublicIpLookup
import dev.dougie.comet.speedtest.parseServerUrl

@Composable
fun SettingsDialog(serverUrl: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(serverUrl) }
    val valid = parseServerUrl(text) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("LibreSpeed server") },
                    singleLine = true,
                    isError = !valid,
                    supportingText = { Text(if (valid) "Where empty.php and garbage.php live" else "Enter a full http(s):// URL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Text("Privacy", modifier = Modifier.padding(top = 16.dp))
                Text(
                    "Speed tests send traffic to the server above, which sees your IP address. " +
                        "Comet only contacts ${PublicIpLookup.HOST} when you tap “Look up public IP” in the " +
                        "network panel; that sends your IP address to them.",
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(text); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
