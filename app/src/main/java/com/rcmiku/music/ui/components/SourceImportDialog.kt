package com.rcmiku.music.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rcmiku.music.source.MusicSource
import com.rcmiku.music.source.SourceImporter
import kotlinx.coroutines.launch

@Composable
fun SourceImportDialog(
    onDismiss: () -> Unit,
    onImported: (MusicSource) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("导入音源") },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        error = null
                    },
                    label = { Text("音源地址") },
                    placeholder = { Text("https://example.com 或 https://example.com/source.json") },
                    minLines = 2,
                    modifier = Modifier,
                )
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && input.isNotBlank(),
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        SourceImporter.import(context, input)
                            .onSuccess { onImported(it) }
                            .onFailure { error = it.message ?: "导入失败" }
                        loading = false
                    }
                }
            ) {
                Text(if (loading) "导入中…" else "导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text("取消")
            }
        },
    )
}
