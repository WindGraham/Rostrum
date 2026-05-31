package com.rostrum.ui.bookmarks

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
import com.rostrum.core.data.repository.BookmarkRepositoryImpl
import com.rostrum.core.domain.model.Bookmark
import com.rostrum.core.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 书签管理器 - 全局单例，使用 BookmarkRepository 进行持久化
 * 
 * 注：已将数据层委托给 BookmarkRepository，消除双轨制问题
 */
object BookmarkManager {
    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()
    
    private var repository: BookmarkRepository? = null
    
    /**
     * 初始化（需要在 Application 或 Activity 中调用）
     */
    fun init(context: Context) {
        if (repository == null) {
            repository = BookmarkRepositoryImpl(context.applicationContext)
            loadBookmarks()
        }
    }
    
    /**
     * 设置自定义 Repository（用于测试或依赖注入）
     */
    fun setRepository(repo: BookmarkRepository) {
        repository = repo
        loadBookmarks()
    }
    
    private fun loadBookmarks() {
        repository?.let { repo ->
            _bookmarks.value = repo.getBookmarks()
        }
    }
    
    fun add(path: String, note: String = "") {
        if (path.isBlank()) return
        if (_bookmarks.value.any { it.path == path }) return
        
        val file = File(path)
        // 允许添加不存在的文件（可能是网络路径或后续会创建的）
        
        val bookmark = Bookmark(
            path = path,
            name = if (file.name.isNotEmpty()) file.name else path,
            isDirectory = file.isDirectory,
            note = note
        )
        
        repository?.addBookmark(bookmark)
        loadBookmarks() // 重新加载以确保同步
    }
    
    fun remove(id: String) {
        val bookmark = _bookmarks.value.find { it.id == id }
        bookmark?.let {
            repository?.removeBookmark(it.path)
            loadBookmarks()
        }
    }
    
    fun clear() {
        _bookmarks.value.forEach { bookmark ->
            repository?.removeBookmark(bookmark.path)
        }
        _bookmarks.value = emptyList()
    }
    
    fun updateNote(id: String, note: String) {
        val bookmark = _bookmarks.value.find { it.id == id }
        bookmark?.let { oldBookmark ->
            val newBookmark = oldBookmark.copy(note = note)
            repository?.updateBookmark(oldBookmark.path, newBookmark)
            loadBookmarks()
        }
    }
    
    fun contains(path: String): Boolean = _bookmarks.value.any { it.path == path }
    
    fun getAll(): List<Bookmark> = _bookmarks.value
}

/**
 * 书签面板
 * 
 * 功能：
 * - 查看所有标记的文件/文件夹
 * - 快速导航到书签位置
 * - 添加备注
 * - 批量操作
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BookmarksPanel(
    onNavigateTo: (String) -> Unit = {},
    onOpenFile: (String) -> Unit = {},
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val bookmarks by BookmarkManager.bookmarks.collectAsState()
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var editingBookmark by remember { mutableStateOf<Bookmark?>(null) }
    
    // 清空确认对话框
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("清空书签") },
            text = { Text("确定要清空所有书签吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        BookmarkManager.clear()
                        selectedIds = emptySet()
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
    
    // 编辑备注对话框
    editingBookmark?.let { bookmark ->
        var noteText by remember { mutableStateOf(bookmark.note) }
        AlertDialog(
            onDismissRequest = { editingBookmark = null },
            title = { Text("编辑备注") },
            text = {
                Column {
                    Text(
                        bookmark.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("备注") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        BookmarkManager.updateNote(bookmark.id, noteText)
                        editingBookmark = null
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingBookmark = null }) { Text("取消") }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Bookmarks,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "书签",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (bookmarks.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Badge { Text("${bookmarks.size}") }
                    }
                }
                
                Row {
                    // 选择模式切换
                    if (selectedIds.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selectedIds.forEach { BookmarkManager.remove(it) }
                                selectedIds = emptySet()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("删除(${selectedIds.size})")
                        }
                    }
                    
                    IconButton(
                        onClick = { showClearConfirm = true },
                        enabled = bookmarks.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.ClearAll,
                            "清空",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
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
                }
            }
        }
        
        // 书签列表
        if (bookmarks.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "还没有书签",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "长按文件或文件夹可添加书签",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    BookmarkItem(
                        bookmark = bookmark,
                        isSelected = selectedIds.contains(bookmark.id),
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                // 选择模式
                                selectedIds = if (selectedIds.contains(bookmark.id)) {
                                    selectedIds - bookmark.id
                                } else {
                                    selectedIds + bookmark.id
                                }
                            } else {
                                // 正常点击
                                if (bookmark.isDirectory) {
                                    onNavigateTo(bookmark.path)
                                } else {
                                    onOpenFile(bookmark.path)
                                }
                            }
                        },
                        onLongClick = {
                            selectedIds = if (selectedIds.contains(bookmark.id)) {
                                selectedIds - bookmark.id
                            } else {
                                selectedIds + bookmark.id
                            }
                        },
                        onEdit = { editingBookmark = bookmark },
                        onDelete = { BookmarkManager.remove(bookmark.id) }
                    )
                }
            }
        }
    }
}

/**
 * 书签项
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkItem(
    bookmark: Bookmark,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
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
                if (bookmark.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (bookmark.isDirectory)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(36.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = bookmark.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (bookmark.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "📝 ${bookmark.note}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Text(
                    text = dateFormat.format(Date(bookmark.dateAdded)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                )
            }
            
            // 操作按钮
            if (!isSelected) {
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            "编辑备注",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
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
}
