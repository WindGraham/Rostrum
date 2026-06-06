package com.termux.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SelectionActionBar(
    selectedCount: Int,
    clipboardCount: Int,
    cutMode: Boolean,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onClearSelection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111827))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "已选 $selectedCount 项",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.labelMedium
            )
            if (clipboardCount > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "| 剪贴板: ${clipboardCount}项${if (cutMode) " (剪切)" else " (复制)"}",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Row {
            if (clipboardCount > 0) {
                IconButton(onClick = onPaste, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = "粘贴",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "复制",
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onCut, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.ContentCut,
                    contentDescription = "剪切",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onClearSelection, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "清除选择",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
