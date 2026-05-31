package com.rostrum.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rostrum.utils.TempFileManager
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 临时文件变化提示横幅
 * 
 * 当有文件被修改时显示在屏幕顶部
 */
@Composable
fun TempFileChangeBanner(
    pendingChanges: List<TempFileManager.PendingFileChange>,
    onSaveAll: () -> Unit,
    onViewDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = pendingChanges.isNotEmpty(),
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${pendingChanges.size} 个文件已修改",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        pendingChanges.first().fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                TextButton(onClick = onViewDetails) {
                    Text("详情", color = MaterialTheme.colorScheme.primary)
                }
                
                FilledTonalButton(
                    onClick = onSaveAll,
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("全部保存")
                }
            }
        }
    }
}

/**
 * 临时文件管理对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TempFileManagerDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    tempFileManager: TempFileManager,
    onNavigateToFile: ((String) -> Unit)? = null
) {
    if (!show) return
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val tempFiles = remember { tempFileManager.getAllTempFiles() }
    val pendingChanges by tempFileManager.pendingChanges.collectAsState()
    
    var showClearConfirm by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("临时文件管理")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 统计信息
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${tempFiles.size}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("临时文件", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${pendingChanges.size}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (pendingChanges.isNotEmpty()) 
                                MaterialTheme.colorScheme.error 
                            else MaterialTheme.colorScheme.outline
                        )
                        Text("待保存", style = MaterialTheme.typography.bodySmall)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 文件列表
                if (tempFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无临时文件",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(tempFiles) { metadata ->
                            TempFileItem(
                                metadata = metadata,
                                onSave = {
                                    scope.launch {
                                        tempFileManager.saveBackToOriginal(metadata.tempPath)
                                    }
                                },
                                onDiscard = {
                                    scope.launch {
                                        tempFileManager.discardChanges(metadata.tempPath)
                                    }
                                },
                                onDelete = {
                                    tempFileManager.deleteTempFile(metadata.tempPath)
                                },
                                onNavigate = {
                                    onNavigateToFile?.invoke(metadata.tempPath)
                                    onDismiss()
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (tempFiles.isNotEmpty()) {
                TextButton(
                    onClick = { showClearConfirm = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("清空全部")
                }
            }
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
    
    // 清空确认对话框
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("确认清空") },
            text = { 
                Text(
                    if (pendingChanges.isNotEmpty()) 
                        "有 ${pendingChanges.size} 个文件尚未保存，确定要清空所有临时文件吗？未保存的更改将丢失。"
                    else 
                        "确定要清空所有临时文件吗？"
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        tempFileManager.clearAllTempFiles()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 临时文件项
 */
@Composable
private fun TempFileItem(
    metadata: TempFileManager.TempFileMetadata,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDelete: () -> Unit,
    onNavigate: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            Icon(
                imageVector = if (metadata.isModified) Icons.Default.Edit else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (metadata.isModified) 
                    MaterialTheme.colorScheme.error 
                else 
                    MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    metadata.sourceName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        if (metadata.sourceApp != null) {
                            append("来自: ${metadata.sourceApp} · ")
                        }
                        append(dateFormat.format(Date(metadata.createdAt)))
                        if (metadata.isModified) {
                            append(" · 已修改")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        
        // 操作按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            // 定位按钮
            TextButton(
                onClick = onNavigate,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("定位", fontSize = 12.sp)
            }
            
            // 保存按钮（仅已修改时显示）
            if (metadata.isModified && (metadata.originalPath != null || metadata.originalUri != null)) {
                TextButton(
                    onClick = onSave,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("保存", fontSize = 12.sp)
                }
                
                // 放弃更改（仅有原始文件时）
                if (metadata.originalPath != null) {
                    TextButton(
                        onClick = onDiscard,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("还原", fontSize = 12.sp)
                    }
                }
            }
            
            // 删除按钮
            TextButton(
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 8.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("删除", fontSize = 12.sp)
            }
        }
    }
}

/**
 * 保存确认对话框
 * 
 * 当编辑临时文件后关闭时显示
 */
@Composable
fun SaveChangesDialog(
    show: Boolean,
    fileName: String,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    if (!show) return
    
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Default.Save, contentDescription = null) },
        title = { Text("保存更改") },
        text = { 
            Text("文件 \"$fileName\" 已被修改。是否保存更改？") 
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard) {
                    Text("不保存")
                }
                TextButton(onClick = onCancel) {
                    Text("取消")
                }
            }
        }
    )
}
