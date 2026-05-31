package com.rostrum.ui.history

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import com.rostrum.core.data.repository.FileHistoryEntry
import com.rostrum.core.data.repository.FileHistoryRepository
import com.rostrum.core.data.repository.FileHistoryRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*

/**
 * 文件历史记录管理器 - 全局单例
 */
object FileHistoryManager {
    private val _history = MutableStateFlow<List<FileHistoryEntry>>(emptyList())
    val history: StateFlow<List<FileHistoryEntry>> = _history.asStateFlow()
    
    private var repository: FileHistoryRepository? = null
    
    /**
     * 初始化
     */
    fun init(context: Context) {
        if (repository == null) {
            repository = FileHistoryRepositoryImpl(context.applicationContext)
            _history.value = repository?.getAllHistory() ?: emptyList()
        }
    }
    
    /**
     * 添加文件访问记录
     */
    suspend fun addFileAccess(path: String, name: String, isDirectory: Boolean) {
        repository?.addPathAccess(path, isDirectory)
        refresh()
    }
    
    /**
     * 添加条目
     */
    suspend fun addEntry(entry: FileHistoryEntry) {
        repository?.addEntry(entry)
        refresh()
    }
    
    /**
     * 删除单条记录
     */
    suspend fun removeEntry(path: String) {
        repository?.removeEntry(path)
        refresh()
    }
    
    /**
     * 清空历史
     */
    suspend fun clearHistory() {
        repository?.clearHistory()
        _history.value = emptyList()
    }
    
    /**
     * 刷新数据
     */
    fun refresh() {
        _history.value = repository?.getAllHistory() ?: emptyList()
    }
    
    /**
     * 检查是否包含
     */
    fun contains(path: String): Boolean = repository?.contains(path) ?: false
}

/**
 * 历史记录面板
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryPanel(
    onNavigateTo: (String) -> Unit = {},
    onOpenFile: (String) -> Unit = {},
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val history by FileHistoryManager.history.collectAsState()
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var filterByType by remember { mutableStateOf<FilterType?>(null) }
    
    // 过滤后的历史记录
    val filteredHistory = remember(history, filterByType) {
        when (filterByType) {
            FilterType.FILES -> history.filter { !it.isDirectory }
            FilterType.DIRECTORIES -> history.filter { it.isDirectory }
            else -> history
        }
    }
    
    // 清空确认对话框
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("清空历史记录") },
            text = { Text("确定要清空所有历史记录吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        runBlocking {
                            FileHistoryManager.clearHistory()
                        }
                        selectedPaths = emptySet()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
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
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "历史记录",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        if (history.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge { Text("${history.size}") }
                        }
                    }
                    
                    Row {
                        // 关闭按钮
                        if (onClose != null) {
                            IconButton(onClick = onClose) {
                                Icon(
                                    Icons.Default.Close,
                                    "关闭",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        // 选择模式删除按钮
                        if (selectedPaths.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    runBlocking {
                                        selectedPaths.forEach { path ->
                                            FileHistoryManager.removeEntry(path)
                                        }
                                    }
                                    selectedPaths = emptySet()
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("删除(${selectedPaths.size})")
                            }
                        }
                        
                        IconButton(
                            onClick = { showClearConfirm = true },
                            enabled = history.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.ClearAll,
                                "清空",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                // 过滤选项
                if (history.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filterByType == null,
                            onClick = { filterByType = null },
                            label = { Text("全部") }
                        )
                        FilterChip(
                            selected = filterByType == FilterType.FILES,
                            onClick = { filterByType = FilterType.FILES },
                            label = { Text("文件") }
                        )
                        FilterChip(
                            selected = filterByType == FilterType.DIRECTORIES,
                            onClick = { filterByType = FilterType.DIRECTORIES },
                            label = { Text("目录") }
                        )
                    }
                }
            }
        }
        
        // 历史记录列表
        if (filteredHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (history.isEmpty()) "还没有历史记录" else "没有符合条件的记录",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (history.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "浏览文件时将自动记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredHistory, key = { it.path }) { entry ->
                    HistoryItem(
                        entry = entry,
                        isSelected = selectedPaths.contains(entry.path),
                        onClick = {
                            if (selectedPaths.isNotEmpty()) {
                                // 选择模式
                                selectedPaths = if (selectedPaths.contains(entry.path)) {
                                    selectedPaths - entry.path
                                } else {
                                    selectedPaths + entry.path
                                }
                            } else {
                                // 正常点击
                                if (entry.isDirectory) {
                                    onNavigateTo(entry.path)
                                } else {
                                    onOpenFile(entry.path)
                                }
                            }
                        },
                        onLongClick = {
                            selectedPaths = if (selectedPaths.contains(entry.path)) {
                                selectedPaths - entry.path
                            } else {
                                selectedPaths + entry.path
                            }
                        },
                        onDelete = {
                            runBlocking {
                                FileHistoryManager.removeEntry(entry.path)
                            }
                        }
                    )
                }
            }
        }
    }
}

private enum class FilterType {
    FILES, DIRECTORIES
}

/**
 * 历史记录项
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryItem(
    entry: FileHistoryEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 选择指示器
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row {
                    Checkbox(
                        checked = true,
                        onCheckedChange = { onLongClick() },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            
            // 图标
            Icon(
                if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (entry.isDirectory)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(36.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = entry.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = dateFormat.format(Date(entry.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )
                    
                    // 访问类型标签
                    val accessTypeLabel = when (entry.accessType) {
                        FileHistoryEntry.AccessType.VIEW -> "查看"
                        FileHistoryEntry.AccessType.EDIT -> "编辑"
                        FileHistoryEntry.AccessType.NAVIGATE -> "浏览"
                    }
                    val accessTypeColor = when (entry.accessType) {
                        FileHistoryEntry.AccessType.VIEW -> MaterialTheme.colorScheme.primary
                        FileHistoryEntry.AccessType.EDIT -> MaterialTheme.colorScheme.tertiary
                        FileHistoryEntry.AccessType.NAVIGATE -> MaterialTheme.colorScheme.secondary
                    }
                    
                    Surface(
                        color = accessTypeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = accessTypeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = accessTypeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            // 操作按钮
            if (!isSelected) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        "删除",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
