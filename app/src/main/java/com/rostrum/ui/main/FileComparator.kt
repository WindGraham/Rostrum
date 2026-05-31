package com.rostrum.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rostrum.core.util.DiffLine
import com.rostrum.core.util.DiffType
import com.rostrum.core.util.DiffUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FileComparatorDialog(
    fileA: File,
    fileB: File,
    onDismiss: () -> Unit
) {
    var diffs by remember { mutableStateOf<List<DiffLine>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(fileA, fileB) {
        isLoading = true
        try {
            val result = withContext(Dispatchers.IO) {
                DiffUtils.computeDiff(fileA, fileB)
            }
            diffs = result
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "文件对比 / File Comparator",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "A: ${fileA.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "B: ${fileB.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Text("✕") // Simple close icon
                    }
                }

                Divider()

                // Content
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (isLoading) {
                        Text("加载中...", modifier = Modifier.align(Alignment.Center))
                    } else if (error != null) {
                        Text(
                            text = "Error: $error",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        DiffListAndStats(diffs ?: emptyList())
                    }
                }
            }
        }
    }
}

@Composable
fun DiffListAndStats(diffs: List<DiffLine>) {
    val addedCount = diffs.count { it.type == DiffType.ADDED }
    val removedCount = diffs.count { it.type == DiffType.REMOVED }
    val sameCount = diffs.count { it.type == DiffType.SAME }

    Column(modifier = Modifier.fillMaxSize()) {
        // Stats bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text("相同: $sameCount", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("新增: $addedCount", color = Color(0xFF2E7D32)) // Green
            Text("删除: $removedCount", color = Color(0xFFC62828)) // Red
        }

        // List
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(diffs) { line ->
                DiffLineItem(line)
            }
        }
    }
}

@Composable
fun DiffLineItem(line: DiffLine) {
    val backgroundColor = when (line.type) {
        DiffType.ADDED -> Color(0xFFE8F5E9)   // Light Green
        DiffType.REMOVED -> Color(0xFFFFEBEE) // Light Red
        DiffType.SAME -> Color.Transparent
        DiffType.MODIFIED -> Color(0xFFFFF3E0) // Light Orange (Not used yet)
    }
    
    val textColor = when (line.type) {
        DiffType.ADDED -> Color(0xFF1B5E20)
        DiffType.REMOVED -> Color(0xFFB71C1C)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val symbol = when (line.type) {
        DiffType.ADDED -> "+"
        DiffType.REMOVED -> "-"
        DiffType.SAME -> " "
        else -> "~"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(vertical = 2.dp)
    ) {
        // Line Numbers (A | B)
        Row(modifier = Modifier.width(60.dp)) {
            Text(
                text = line.lineNoA?.toString() ?: "",
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "|",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = line.lineNoB?.toString() ?: "",
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Symbol
        Text(
            text = symbol,
            modifier = Modifier.width(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = textColor
        )

        // Content
        Text(
            text = line.content,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = textColor
        )
    }
}