package com.rostrum.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment

@Composable
fun ResizeDialog(
    currentWidth: Int,
    currentHeight: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var widthStr by remember { mutableStateOf(currentWidth.toString()) }
    var heightStr by remember { mutableStateOf(currentHeight.toString()) }
    var keepRatio by remember { mutableStateOf(true) }
    val aspectRatio = if (currentHeight > 0) currentWidth.toFloat() / currentHeight.toFloat() else 1f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整大小") },
        text = {
            Column {
                OutlinedTextField(
                    value = widthStr,
                    onValueChange = { 
                        widthStr = it
                        if (keepRatio && it.isNotEmpty()) {
                             it.toIntOrNull()?.let { w ->
                                 heightStr = (w / aspectRatio).toInt().toString()
                             }
                        }
                    },
                    label = { Text("宽度") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = heightStr,
                    onValueChange = { 
                         heightStr = it
                         if (keepRatio && it.isNotEmpty()) {
                             it.toIntOrNull()?.let { h ->
                                 widthStr = (h * aspectRatio).toInt().toString()
                             }
                        }
                    },
                    label = { Text("高度") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Checkbox(checked = keepRatio, onCheckedChange = { keepRatio = it })
                    Text("保持长宽比")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val w = widthStr.toIntOrNull() ?: currentWidth
                val h = heightStr.toIntOrNull() ?: currentHeight
                if (w > 0 && h > 0) {
                    onConfirm(w, h)
                }
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun ConvertDialog(
    currentExt: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedFormat by remember { 
        mutableStateOf(
            when(currentExt.lowercase()) {
                "png" -> "PNG"
                "webp" -> "WEBP"
                else -> "JPEG"
            }
        ) 
    }
    val formats = listOf("JPEG", "PNG", "WEBP")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("转换并保存为") },
        text = {
            Column {
                formats.forEach { format ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = (format == selectedFormat),
                            onClick = { selectedFormat = format }
                        )
                        Text(
                            text = format,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedFormat) }) { Text("转换") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
