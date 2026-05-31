package com.rostrum.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rostrum.core.domain.model.FileItem
import com.rostrum.core.util.FileShareUtils
import com.rostrum.ui.bookmarks.BookmarkManager
import com.rostrum.ui.common.FileIconUtils
import java.io.File

/**
 * 文件操作数据类
 */
data class FileAction(
    val icon: ImageVector,
    val label: String,
    val description: String,
    val color: Color,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * 文件菜单底部弹窗 - 优化UI版本
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileMenuBottomSheet(
    fileItem: FileItem,
    isLeft: Boolean,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onRenameComplete: () -> Unit = {}
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    
    val isBookmarked = remember(fileItem.path) { BookmarkManager.contains(fileItem.path) }
    
    // 重命名对话框
    if (showRenameDialog) {
        RenameDialog(
            currentName = fileItem.name,
            onConfirm = { newName ->
                viewModel.renameFile(fileItem.path, newName, isLeft) { success, message ->
                    onRenameComplete()
                }
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
        return
    }
    
    // 属性对话框
    if (showPropertiesDialog) {
        FilePropertiesDialog(
            fileItem = fileItem,
            onDismiss = { showPropertiesDialog = false }
        )
        return
    }
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("确认删除") },
            text = { 
                Text(
                    "确定要删除 \"${fileItem.name}\" 吗？\n此操作无法撤销。",
                    textAlign = TextAlign.Center
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleFileSelection(fileItem.path, isLeft)
                        viewModel.deleteFiles(isLeft) { success, message ->
                            onRenameComplete()
                        }
                        showDeleteDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
        return
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // 文件信息头部
            FileInfoHeader(fileItem = fileItem)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 主要操作按钮网格
            Text(
                text = "快捷操作",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            
            // 定义操作列表
            val context = LocalContext.current
            val primaryActions = listOf(
                FileAction(
                    icon = Icons.Default.ContentCopy,
                    label = "复制",
                    description = "复制到剪贴板",
                    color = Color(0xFF6366F1)
                ) {
                    ClipboardManager.copyFiles(listOf(fileItem.path))
                    onDismiss()
                },
                FileAction(
                    icon = Icons.Default.ContentCut,
                    label = "剪切",
                    description = "剪切文件",
                    color = Color(0xFFF59E0B)
                ) {
                    ClipboardManager.cutFiles(listOf(fileItem.path))
                    onDismiss()
                },
                FileAction(
                    icon = Icons.Default.Share,
                    label = "分享",
                    description = "分享到其他应用",
                    color = Color(0xFF8B5CF6)
                ) {
                    FileShareUtils.shareFile(context, File(fileItem.path))
                    onDismiss()
                },
                FileAction(
                    icon = Icons.Default.OpenInNew,
                    label = "打开方式",
                    description = "用其他应用打开",
                    color = Color(0xFF06B6D4),
                    enabled = !fileItem.isDirectory
                ) {
                    FileShareUtils.openWith(context, File(fileItem.path))
                    onDismiss()
                },
                FileAction(
                    icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    label = if (isBookmarked) "已收藏" else "收藏",
                    description = if (isBookmarked) "从书签移除" else "添加到书签",
                    color = Color(0xFF10B981)
                ) {
                    if (isBookmarked) {
                        val bookmark = BookmarkManager.bookmarks.value.find { it.path == fileItem.path }
                        bookmark?.let { BookmarkManager.remove(it.id) }
                    } else {
                        BookmarkManager.add(fileItem.path)
                    }
                    onDismiss()
                },
                FileAction(
                    icon = Icons.Default.Edit,
                    label = "重命名",
                    description = "修改名称",
                    color = Color(0xFF3B82F6)
                ) {
                    onDismiss()
                    showRenameDialog = true
                }
            )
            
            // 使用网格布局显示主要操作
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(primaryActions) { action ->
                    ActionGridItem(action = action)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 更多操作
            Text(
                text = "更多操作",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            
            // 选择相同类型
            ActionListItem(
                icon = Icons.Default.Category,
                title = "选择相同类型",
                subtitle = "批量选择相同扩展名的文件"
            ) {
                viewModel.selectSameType(fileItem, isLeft)
                onDismiss()
            }
            
            // ZIP 解压
            if (fileItem.extension.equals("zip", ignoreCase = true)) {
                ActionListItem(
                    icon = Icons.Default.FolderZip,
                    title = "解压到当前目录",
                    subtitle = "解压 ZIP 文件内容"
                ) {
                    viewModel.unzipFile(fileItem.path, isLeft) { _, _ -> }
                    onDismiss()
                }
            }
            
            // 属性
            ActionListItem(
                icon = Icons.Default.Info,
                title = "属性",
                subtitle = "查看详细信息"
            ) {
                onDismiss()
                showPropertiesDialog = true
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Divider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))
            
            // 危险操作
            ActionListItem(
                icon = Icons.Default.Delete,
                title = "删除",
                subtitle = "永久删除此文件",
                isDanger = true
            ) {
                onDismiss()
                showDeleteDialog = true
            }
        }
    }
}

/**
 * 文件信息头部
 */
@Composable
private fun FileInfoHeader(fileItem: FileItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 文件图标
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = FileIconUtils.getFileIcon(fileItem),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = FileIconUtils.getFileIconColor(fileItem)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileItem.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!fileItem.isDirectory) {
                    Text(
                        text = fileItem.getFormattedSize(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = fileItem.getFormattedDate(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 网格操作项
 */
@Composable
private fun ActionGridItem(action: FileAction) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = action.enabled, onClick = action.onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(action.color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.label,
                tint = action.color,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (action.enabled) 
                MaterialTheme.colorScheme.onSurface 
            else 
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 列表操作项
 */
@Composable
private fun ActionListItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (isDanger) 
        MaterialTheme.colorScheme.error 
    else 
        MaterialTheme.colorScheme.onSurface
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDanger) 
                MaterialTheme.colorScheme.error 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}
