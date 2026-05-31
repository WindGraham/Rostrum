package com.rostrum.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * 过滤搜索模式
 */
enum class FilterMode {
    CURRENT_DIRECTORY,  // 当前目录过滤
    RECURSIVE_SEARCH    // 递归搜索所有文件
}

/**
 * 过滤对话框结果
 */
data class FilterResult(
    val query: String,
    val mode: FilterMode
)

/**
 * 过滤/搜索对话框
 * 支持选择过滤模式：当前目录过滤或递归搜索
 */
@Composable
fun FilterDialog(
    title: String = "过滤/搜索文件",
    initialQuery: String = "",
    initialMode: FilterMode = FilterMode.CURRENT_DIRECTORY,
    onConfirm: (FilterResult) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialQuery) }
    var selectedMode by remember { mutableStateOf(initialMode) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 输入框
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("关键字 (支持正则)") },
                    placeholder = { Text("输入文件名或正则表达式") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (text.isNotEmpty()) {
                            IconButton(onClick = { text = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "清除",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                )
                
                // 模式选择
                Column(modifier = Modifier.selectableGroup()) {
                    Text(
                        text = "搜索范围",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 当前目录选项
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedMode == FilterMode.CURRENT_DIRECTORY,
                                onClick = { selectedMode = FilterMode.CURRENT_DIRECTORY },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == FilterMode.CURRENT_DIRECTORY,
                            onClick = null // 处理由 Row 的 selectable 完成
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "当前目录",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "仅过滤当前目录下的文件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // 递归搜索选项
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedMode == FilterMode.RECURSIVE_SEARCH,
                                onClick = { selectedMode = FilterMode.RECURSIVE_SEARCH },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == FilterMode.RECURSIVE_SEARCH,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "递归搜索",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "搜索当前目录及所有子目录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // 正则提示
                if (text.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "提示: 使用 ! 前缀表示排除匹配，如 !.tmp",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    onConfirm(FilterResult(text, selectedMode))
                }
            ) {
                Text(if (text.isEmpty()) "清除过滤" else "应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
