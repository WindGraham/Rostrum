package com.rostrum.ui.migrate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rostrum.core.domain.model.Bookmark
import com.rostrum.ui.bookmarks.BookmarkManager
import java.io.File

/**
 * 迁移操作类型
 */
enum class MigrateAction {
    COPY,      // 复制
    MOVE,      // 移动
    LINK       // 创建链接（符号链接）
}

/**
 * 迁移面板
 * 
 * 功能：
 * - 选择书签中的文件/文件夹
 * - 选择目标目录
 * - 执行复制/移动/链接操作
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigratePanel(
    currentPath: String = "",
    onRequestTargetPath: () -> Unit = {},
    onMigrateComplete: (success: Int, failed: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val bookmarks by remember { derivedStateOf { BookmarkManager.bookmarks.value } }
    var selectedBookmarks by remember { mutableStateOf(setOf<String>()) }
    var targetPath by remember { mutableStateOf(currentPath) }
    var action by remember { mutableStateOf(MigrateAction.COPY) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var processResult by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    
    // 更新目标路径
    LaunchedEffect(currentPath) {
        if (currentPath.isNotBlank()) {
            targetPath = currentPath
        }
    }
    
    // 执行确认对话框
    if (showConfirmDialog) {
        val selectedCount = selectedBookmarks.size
        val actionText = when (action) {
            MigrateAction.COPY -> "复制"
            MigrateAction.MOVE -> "移动"
            MigrateAction.LINK -> "创建链接"
        }
        
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = { 
                Icon(
                    when (action) {
                        MigrateAction.COPY -> Icons.Default.ContentCopy
                        MigrateAction.MOVE -> Icons.Default.DriveFileMove
                        MigrateAction.LINK -> Icons.Default.Link
                    },
                    null
                )
            },
            title = { Text("确认${actionText}") },
            text = { 
                Column {
                    Text("将 $selectedCount 个项目${actionText}到:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                targetPath.ifBlank { "未选择目标" },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    if (action == MigrateAction.MOVE) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "⚠️ 移动操作会从原位置删除文件",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        isProcessing = true
                        // 执行迁移
                        val result = executeMigration(
                            bookmarks.filter { selectedBookmarks.contains(it.id) },
                            targetPath,
                            action
                        )
                        processResult = result
                        isProcessing = false
                        onMigrateComplete(result.first, result.second)
                        
                        // 如果是移动操作，移除成功迁移的书签
                        if (action == MigrateAction.MOVE && result.first > 0) {
                            selectedBookmarks.forEach { BookmarkManager.remove(it) }
                            selectedBookmarks = emptySet()
                        }
                    },
                    enabled = targetPath.isNotBlank()
                ) { 
                    Text(actionText) 
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("取消") }
            }
        )
    }
    
    // 结果提示
    processResult?.let { (success, failed) ->
        LaunchedEffect(processResult) {
            kotlinx.coroutines.delay(3000)
            processResult = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 头部
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DriveFileMove,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "迁移",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 目标路径
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRequestTargetPath() },
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "目标位置",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                targetPath.ifBlank { "点击选择目标目录" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (targetPath.isBlank())
                                    MaterialTheme.colorScheme.outline
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 操作类型选择
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MigrateAction.values().forEach { actionType ->
                        FilterChip(
                            selected = action == actionType,
                            onClick = { action = actionType },
                            label = {
                                Text(
                                    when (actionType) {
                                        MigrateAction.COPY -> "复制"
                                        MigrateAction.MOVE -> "移动"
                                        MigrateAction.LINK -> "链接"
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    when (actionType) {
                                        MigrateAction.COPY -> Icons.Default.ContentCopy
                                        MigrateAction.MOVE -> Icons.Default.DriveFileMove
                                        MigrateAction.LINK -> Icons.Default.Link
                                    },
                                    null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        
        // 结果提示
        AnimatedVisibility(
            visible = processResult != null,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            processResult?.let { (success, failed) ->
                Surface(
                    color = if (failed == 0)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✅ 成功: $success  ${if (failed > 0) "❌ 失败: $failed" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        
        // 处理中指示器
        if (isProcessing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        
        // 书签列表
        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "没有可迁移的项目",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "先在书签中添加文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // 列表头部
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selectedBookmarks.size == bookmarks.size && bookmarks.isNotEmpty(),
                            onCheckedChange = {
                                selectedBookmarks = if (it) {
                                    bookmarks.map { b -> b.id }.toSet()
                                } else {
                                    emptySet()
                                }
                            }
                        )
                        Text(
                            "全选 (${selectedBookmarks.size}/${bookmarks.size})",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                
                // 列表
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(bookmarks, key = { it.id }) { bookmark ->
                        MigrateItem(
                            bookmark = bookmark,
                            isSelected = selectedBookmarks.contains(bookmark.id),
                            onToggle = {
                                selectedBookmarks = if (selectedBookmarks.contains(bookmark.id)) {
                                    selectedBookmarks - bookmark.id
                                } else {
                                    selectedBookmarks + bookmark.id
                                }
                            }
                        )
                    }
                }
                
                // 执行按钮
                AnimatedVisibility(
                    visible = selectedBookmarks.isNotEmpty() && targetPath.isNotBlank(),
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { showConfirmDialog = true },
                            enabled = !isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Icon(
                                when (action) {
                                    MigrateAction.COPY -> Icons.Default.ContentCopy
                                    MigrateAction.MOVE -> Icons.Default.DriveFileMove
                                    MigrateAction.LINK -> Icons.Default.Link
                                },
                                null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "${when (action) {
                                    MigrateAction.COPY -> "复制"
                                    MigrateAction.MOVE -> "移动"
                                    MigrateAction.LINK -> "链接"
                                }} ${selectedBookmarks.size} 个项目"
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 迁移项
 */
@Composable
private fun MigrateItem(
    bookmark: Bookmark,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
            
            Icon(
                if (bookmark.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (bookmark.isDirectory)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(28.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = bookmark.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 执行迁移操作
 */
private fun executeMigration(
    items: List<Bookmark>,
    targetPath: String,
    action: MigrateAction
): Pair<Int, Int> {
    var success = 0
    var failed = 0
    
    val targetDir = File(targetPath)
    if (!targetDir.exists() || !targetDir.isDirectory) {
        return Pair(0, items.size)
    }
    
    items.forEach { bookmark ->
        try {
            val sourceFile = File(bookmark.path)
            val targetFile = File(targetDir, sourceFile.name)
            
            when (action) {
                MigrateAction.COPY -> {
                    if (sourceFile.isDirectory) {
                        sourceFile.copyRecursively(targetFile, overwrite = false)
                    } else {
                        sourceFile.copyTo(targetFile, overwrite = false)
                    }
                    success++
                }
                MigrateAction.MOVE -> {
                    if (sourceFile.renameTo(targetFile)) {
                        success++
                    } else {
                        // 尝试复制后删除
                        if (sourceFile.isDirectory) {
                            sourceFile.copyRecursively(targetFile, overwrite = false)
                            sourceFile.deleteRecursively()
                        } else {
                            sourceFile.copyTo(targetFile, overwrite = false)
                            sourceFile.delete()
                        }
                        success++
                    }
                }
                MigrateAction.LINK -> {
                    // 创建符号链接（需要root权限）
                    val process = Runtime.getRuntime().exec(
                        arrayOf("ln", "-s", sourceFile.absolutePath, targetFile.absolutePath)
                    )
                    if (process.waitFor() == 0) {
                        success++
                    } else {
                        failed++
                    }
                }
            }
        } catch (e: Exception) {
            failed++
        }
    }
    
    return Pair(success, failed)
}
