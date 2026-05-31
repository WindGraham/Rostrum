package com.rostrum.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rostrum.core.domain.model.FileItem
import java.util.regex.PatternSyntaxException

/**
 * 批量重命名对话框
 */
@Composable
fun BatchRenameDialog(
    files: List<FileItem>,
    onConfirm: (List<Pair<String, String>>) -> Unit, // List<OldPath, NewName>
    onDismiss: () -> Unit
) {
    var searchPattern by remember { mutableStateOf("") }
    var replacePattern by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }
    var useExtension by remember { mutableStateOf(false) } // 是否包含扩展名一起重命名
    
    // 预览结果: List<Pair<OldName, NewName>>
    val previewList = remember(files, searchPattern, replacePattern, useRegex, useExtension) {
        if (searchPattern.isEmpty()) {
            files.map { it.name to it.name }
        } else {
            files.map { file ->
                var newName = file.name
                try {
                    val actualFile = file.file
                    val nameToProcess = if (useExtension) file.name else (actualFile?.nameWithoutExtension ?: file.name.substringBeforeLast('.'))
                    val extension = if (useExtension) "" else (actualFile?.extension?.let { ".$it" } ?: file.name.substringAfterLast('.', "").let { if (it.isNotEmpty()) ".$it" else "" })
                    
                    val processedName = if (useRegex) {
                        try {
                            nameToProcess.replace(Regex(searchPattern), replacePattern)
                        } catch (e: PatternSyntaxException) {
                            nameToProcess // 正则错误时不替换
                        }
                    } else {
                        nameToProcess.replace(searchPattern, replacePattern)
                    }
                    
                    newName = if (useExtension) processedName else "$processedName$extension"
                    // 如果扩展名为空，处理一下点
                    if (!useExtension && file.extension.isEmpty()) {
                         newName = processedName
                    }
                    
                    file.name to newName
                } catch (e: Exception) {
                    file.name to file.name
                }
            }
        }
    }
    
    // 计算变更数量
    val changedCount = previewList.count { it.first != it.second }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "批量重命名 (${files.size}个文件)",
                    style = MaterialTheme.typography.titleLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 输入区域
                OutlinedTextField(
                    value = searchPattern,
                    onValueChange = { searchPattern = it },
                    label = { Text("查找内容") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = replacePattern,
                    onValueChange = { replacePattern = it },
                    label = { Text("替换为") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useRegex,
                            onCheckedChange = { useRegex = it }
                        )
                        Text("正则表达式")
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useExtension,
                            onCheckedChange = { useExtension = it }
                        )
                        Text("包含扩展名")
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = "预览 (将变更 $changedCount 个文件)",
                    style = MaterialTheme.typography.titleMedium
                )
                
                // 预览列表
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(previewList) { (oldName, newName) ->
                        if (oldName != newName) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = oldName,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "→",
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                Text(
                                    text = newName,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            // 构造结果：List<OldPath, NewName>
                            val result = files.zip(previewList)
                                .filter { it.second.first != it.second.second } // 只处理有变化的文件
                                .map { (file, namePair) ->
                                    file.path to namePair.second
                                }
                            onConfirm(result)
                        },
                        enabled = changedCount > 0
                    ) {
                        Text("执行重命名")
                    }
                }
            }
        }
    }
}