package com.rostrum.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.rostrum.core.util.FileShareUtils
import com.rostrum.core.util.SortOrder
import com.rostrum.core.util.SortType
import com.rostrum.ui.bookmarks.BookmarkManager

/**
 * 面板选项菜单 - 优化版
 * @param onClosePane 关闭当前面板的回调（将区域设为空白）
 */
@Composable
fun PaneOptionsMenuV2(
    viewModel: MainViewModel,
    isLeft: Boolean,
    panePosition: PanePosition = if (isLeft) PanePosition.TOP_LEFT else PanePosition.TOP_RIGHT,
    onFilterClick: () -> Unit,
    onClosePane: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showZipDialog by remember { mutableStateOf(false) }
    
    val hasClipboardContent = !ClipboardManager.isEmpty()
    val isCutMode = ClipboardManager.isCutMode.value
    
    // 获取当前区域的选中文件
    val selectedFiles = when (panePosition) {
        PanePosition.TOP_LEFT -> viewModel.leftSelectedFiles
        PanePosition.TOP_RIGHT -> viewModel.rightSelectedFiles
        PanePosition.BOTTOM -> viewModel.bottomSelectedFiles
    }
    val hasSelection = selectedFiles.isNotEmpty()
    
    Box(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 关闭按钮（如果提供了回调）
            if (onClosePane != null) {
                IconButton(
                    onClick = onClosePane,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭面板",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 更多选项按钮（三条横线菜单图标）
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "选项",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        // 自定义下拉菜单
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset((-8).dp, 0.dp),
            modifier = Modifier
                .widthIn(min = 220.dp)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // 头部：快捷操作区
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "快捷操作",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
                
                // 快捷操作按钮行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 粘贴
                    QuickActionButton(
                        icon = Icons.Default.ContentPaste,
                        label = if (isCutMode) "移动" else "粘贴",
                        enabled = hasClipboardContent,
                        color = if (hasClipboardContent) Color(0xFF10B981) else Color.Gray
                    ) {
                        val targetPath = when (panePosition) {
                            PanePosition.TOP_LEFT -> viewModel.leftCurrentPath
                            PanePosition.TOP_RIGHT -> viewModel.rightCurrentPath
                            PanePosition.BOTTOM -> viewModel.bottomCurrentPath
                        }
                        viewModel.pasteFiles(targetPath) { _, _ ->
                            viewModel.loadFileListForPane(panePosition)
                        }
                        expanded = false
                    }
                    
                    // 刷新
                    QuickActionButton(
                        icon = Icons.Default.Refresh,
                        label = "刷新",
                        color = Color(0xFF3B82F6)
                    ) {
                        viewModel.loadFileListForPane(panePosition)
                        expanded = false
                    }
                    
                    // 添加书签
                    QuickActionButton(
                        icon = Icons.Default.BookmarkAdd,
                        label = "收藏",
                        color = Color(0xFFF59E0B)
                    ) {
                        val path = when (panePosition) {
                            PanePosition.TOP_LEFT -> viewModel.leftCurrentPath
                            PanePosition.TOP_RIGHT -> viewModel.rightCurrentPath
                            PanePosition.BOTTOM -> viewModel.bottomCurrentPath
                        }
                        BookmarkManager.add(path)
                        expanded = false
                    }
                    
                    // 过滤
                    QuickActionButton(
                        icon = Icons.Default.FilterList,
                        label = "过滤",
                        color = Color(0xFF6366F1)
                    ) {
                        onFilterClick()
                        expanded = false
                    }
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            
            // 新建操作区
            Text(
                text = "新建",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            
            MenuItemRow(
                icon = Icons.Default.CreateNewFolder,
                text = "新建文件夹"
            ) {
                showCreateFolderDialog = true
                expanded = false
            }
            
            MenuItemRow(
                icon = Icons.Default.NoteAdd,
                text = "新建文件"
            ) {
                showCreateFileDialog = true
                expanded = false
            }
            
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            
            // 选择操作区
            Text(
                text = "选择",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            
            MenuItemRow(
                icon = Icons.Default.DoneAll,
                text = "全选"
            ) {
                viewModel.selectAllForPane(panePosition)
                expanded = false
            }
            
            MenuItemRow(
                icon = Icons.Default.SwapVert,
                text = "反选"
            ) {
                viewModel.invertSelectionForPane(panePosition)
                expanded = false
            }
            
            MenuItemRow(
                icon = Icons.Default.Clear,
                text = "取消选中"
            ) {
                viewModel.clearSelectionForPane(panePosition)
                expanded = false
            }
            
            // 如果有选中文件，显示文件操作
            if (hasSelection) {
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                
                Text(
                    text = "文件操作 (${selectedFiles.size}个)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                
                // 复制
                MenuItemRow(
                    icon = Icons.Default.ContentCopy,
                    text = "复制"
                ) {
                    ClipboardManager.copyFiles(selectedFiles.toList())
                    expanded = false
                }
                
                // 剪切
                MenuItemRow(
                    icon = Icons.Default.ContentCut,
                    text = "剪切"
                ) {
                    ClipboardManager.cutFiles(selectedFiles.toList())
                    expanded = false
                }
                
                // 压缩
                MenuItemRow(
                    icon = Icons.Default.FolderZip,
                    text = "压缩"
                ) {
                    showZipDialog = true
                    expanded = false
                }
                
                // 分享
                val context = LocalContext.current
                MenuItemRow(
                    icon = Icons.Default.Share,
                    text = "分享"
                ) {
                    val files = selectedFiles.map { java.io.File(it) }
                    FileShareUtils.shareFiles(context, files)
                    expanded = false
                }
                
                // 删除
                MenuItemRow(
                    icon = Icons.Default.Delete,
                    text = "删除",
                    tint = MaterialTheme.colorScheme.error
                ) {
                    showDeleteDialog = true
                    expanded = false
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            
            // 排序区
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSortMenu = !showSortMenu }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "排序方式",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = getSortTypeLabel(viewModel.sortType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (showSortMenu) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 展开的排序选项
            AnimatedVisibility(
                visible = showSortMenu,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    SortType.values().forEach { sortType ->
                        SortOptionItem(
                            text = getSortTypeLabel(sortType),
                            isSelected = viewModel.sortType == sortType
                        ) {
                            viewModel.updateSortType(sortType)
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    // 倒序选项
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.toggleSortOrder() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = viewModel.sortOrder == SortOrder.DESCENDING,
                            onCheckedChange = { viewModel.toggleSortOrder() },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "倒序排列",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除选中的 ${selectedFiles.size} 个文件吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFilesForPane(panePosition) { _, _ ->
                            viewModel.loadFileListForPane(panePosition)
                        }
                        showDeleteDialog = false
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 新建文件夹对话框
    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("新建文件夹") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("文件夹名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            val targetPath = when (panePosition) {
                                PanePosition.TOP_LEFT -> viewModel.leftCurrentPath
                                PanePosition.TOP_RIGHT -> viewModel.rightCurrentPath
                                PanePosition.BOTTOM -> viewModel.bottomCurrentPath
                            }
                            viewModel.createFolder(targetPath, folderName) { _, _ ->
                                viewModel.loadFileListForPane(panePosition)
                            }
                        }
                        showCreateFolderDialog = false
                    },
                    enabled = folderName.isNotBlank()
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 新建文件对话框
    if (showCreateFileDialog) {
        var fileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("新建文件") },
            text = {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("文件名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (fileName.isNotBlank()) {
                            val targetPath = when (panePosition) {
                                PanePosition.TOP_LEFT -> viewModel.leftCurrentPath
                                PanePosition.TOP_RIGHT -> viewModel.rightCurrentPath
                                PanePosition.BOTTOM -> viewModel.bottomCurrentPath
                            }
                            viewModel.createFile(targetPath, fileName) { _, _ ->
                                viewModel.loadFileListForPane(panePosition)
                            }
                        }
                        showCreateFileDialog = false
                    },
                    enabled = fileName.isNotBlank()
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 压缩对话框
    if (showZipDialog) {
        var zipName by remember { mutableStateOf("archive.zip") }
        AlertDialog(
            onDismissRequest = { showZipDialog = false },
            title = { Text("压缩文件") },
            text = {
                OutlinedTextField(
                    value = zipName,
                    onValueChange = { zipName = it },
                    label = { Text("压缩包名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (zipName.isNotBlank()) {
                            val targetPath = when (panePosition) {
                                PanePosition.TOP_LEFT -> viewModel.leftCurrentPath
                                PanePosition.TOP_RIGHT -> viewModel.rightCurrentPath
                                PanePosition.BOTTOM -> viewModel.bottomCurrentPath
                            }
                            viewModel.compressFiles(selectedFiles.toList(), targetPath, zipName) { _, _ ->
                                viewModel.loadFileListForPane(panePosition)
                            }
                        }
                        showZipDialog = false
                    },
                    enabled = zipName.isNotBlank()
                ) {
                    Text("压缩")
                }
            },
            dismissButton = {
                TextButton(onClick = { showZipDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 快捷操作按钮
 */
@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) color.copy(alpha = 0.12f)
                    else Color.Gray.copy(alpha = 0.08f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) color else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}

/**
 * 排序选项项
 */
@Composable
private fun SortOptionItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun getSortTypeLabel(sortType: SortType): String {
    return when (sortType) {
        SortType.NAME -> "按名称"
        SortType.SIZE -> "按大小"
        SortType.DATE -> "按日期"
        SortType.TYPE -> "按类型"
    }
}

/**
 * 带颜色的菜单项行
 */
@Composable
private fun MenuItemRow(
    icon: ImageVector,
    text: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (tint != MaterialTheme.colorScheme.onSurfaceVariant) tint 
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}
