package com.rostrum.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rostrum.core.config.FileViewMode
import com.rostrum.core.config.GridIconSize
import com.rostrum.core.domain.model.FileItem
import com.rostrum.core.filesystem.ActiveFileSystemMode
import com.rostrum.core.filesystem.ActiveFileSystemState
import com.rostrum.ui.common.InputDialog
import kotlinx.coroutines.launch

/**
 * 紧凑的文件浏览面板（用于双列布局）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactFileBrowserPane(
    currentPath: String,
    fileList: List<FileItem>,
    selectedFiles: Set<String>,
    isLeft: Boolean,
    panePosition: PanePosition = if (isLeft) PanePosition.TOP_LEFT else PanePosition.TOP_RIGHT,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onFileMenuClick: (FileItem) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenDrawer: () -> Unit,
    onClosePane: (() -> Unit)? = null,
    canNavigateUp: Boolean,
    viewModel: MainViewModel,
    backendState: ActiveFileSystemState? = null,
    // 视图模式相关参数
    viewMode: FileViewMode = FileViewMode.LIST,
    gridIconSize: GridIconSize = GridIconSize.MEDIUM,
    onGridIconSizeChange: ((GridIconSize) -> Unit)? = null,
    // 滚动定位相关参数
    scrollToIndex: Int = -1,
    onScrollComplete: () -> Unit = {},
    // 高亮文件路径（操作后定位到的文件）
    highlightedFile: String? = null,
    highlightVersion: Int = 0,
    onHighlightConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showPathDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showSelectionMenu by remember { mutableStateOf(false) }  // 选中模式下的操作菜单
    var showRenameDialog by remember { mutableStateOf(false) }
    var showQuickAccessMenu by remember { mutableStateOf(false) }  // 快捷地址菜单
    var renameTargetFile by remember { mutableStateOf<FileItem?>(null) }
    
    // 根据区域获取过滤关键字
    val filterQuery = when (panePosition) {
        PanePosition.TOP_LEFT -> viewModel.leftFilterQuery
        PanePosition.TOP_RIGHT -> viewModel.rightFilterQuery
        PanePosition.BOTTOM -> viewModel.bottomFilterQuery
    }
    
    // 获取递归搜索状态
    val isRecursiveSearch = viewModel.isRecursiveSearchEnabled(panePosition)

    if (showPathDialog) {
        InputDialog(
            title = "跳转到路径",
            initialValue = currentPath,
            onConfirm = { path ->
                viewModel.navigateToPane(path, panePosition)
                showPathDialog = false
            },
            onDismiss = { showPathDialog = false },
            label = "路径"
        )
    }
    
    if (showFilterDialog) {
        com.rostrum.ui.common.FilterDialog(
            title = "过滤/搜索文件",
            initialQuery = filterQuery,
            initialMode = if (isRecursiveSearch) 
                com.rostrum.ui.common.FilterMode.RECURSIVE_SEARCH 
            else 
                com.rostrum.ui.common.FilterMode.CURRENT_DIRECTORY,
            onConfirm = { result ->
                viewModel.updateFilterForPane(
                    result.query, 
                    panePosition,
                    result.mode == com.rostrum.ui.common.FilterMode.RECURSIVE_SEARCH
                )
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false }
        )
    }
    
    // 重命名对话框
    if (showRenameDialog && renameTargetFile != null) {
        RenameDialog(
            currentName = renameTargetFile!!.name,
            onConfirm = { newName ->
                viewModel.renameFile(renameTargetFile!!.path, newName, panePosition == PanePosition.TOP_LEFT) { success, _ ->
                    if (success) {
                        viewModel.loadFileListForPane(panePosition)
                        viewModel.clearSelectionForPane(panePosition)
                    }
                }
                showRenameDialog = false
                renameTargetFile = null
            },
            onDismiss = { 
                showRenameDialog = false
                renameTargetFile = null
            }
        )
    }

    // 拖动状态
    val isDragging = DragState.isDragging
    val draggedFile = DragState.draggedFile
    val dragTargetPath = DragState.dragTargetPath
    
    // 当前区域是否是拖动来源
    val isSourcePane = isDragging && DragState.dragSourcePane == panePosition
    // 当前区域是否是拖动目标
    val isTargetPane = isDragging && DragState.dragTargetPane == panePosition
    
    // 获取当前区域的绑定状态
    val boundPreviewPane = PaneBindingManager.getBoundPreviewPane(panePosition)
    val bindingVersion = PaneBindingManager.bindingVersion // 强制重组
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isDragging, currentPath) {
                if (isDragging) {
                    // 检测拖动目标
                    // 这里简化处理，直接使用当前路径作为目标
                    DragState.setDragTarget(currentPath)
                }
            }
    ) {
        // 顶部路径栏（紧凑）- 固定高度，支持水平滚动
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 返回按钮（小尺寸）
                // 使用 Box + combinedClickable 支持长按
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .combinedClickable(
                            onClick = {
                                if (canNavigateUp) onNavigateUp() else onOpenDrawer()
                            },
                            onLongClick = {
                                showPathDialog = true
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (canNavigateUp) Icons.Default.ArrowBack else Icons.Default.Menu,
                        contentDescription = if (canNavigateUp) "返回上级" else "打开菜单",
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // Home 按钮（回到默认目录）— 始终显示
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .combinedClickable(
                            onClick = {
                                viewModel.navigateToPane(viewModel.rootPath, panePosition)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val isAtHome = currentPath == viewModel.rootPath
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "回到主目录",
                        modifier = Modifier.size(18.dp),
                        tint = if (isAtHome)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 快捷地址按钮
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .combinedClickable(
                            onClick = { showQuickAccessMenu = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "快捷地址",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    // 快捷地址下拉菜单
                    DropdownMenu(
                        expanded = showQuickAccessMenu,
                        onDismissRequest = { showQuickAccessMenu = false },
                        modifier = Modifier.width(200.dp)
                    ) {
                        val quickPaths = viewModel.quickAccessPaths.ifEmpty {
                            // 使用默认快捷地址
                            listOf(
                                com.rostrum.core.config.QuickAccessPath("根目录", viewModel.rootPath, "folder"),
                                com.rostrum.core.config.QuickAccessPath("下载", "${viewModel.rootPath}/Download", "download"),
                                com.rostrum.core.config.QuickAccessPath("文档", "${viewModel.rootPath}/Documents", "description"),
                                com.rostrum.core.config.QuickAccessPath("图片", "${viewModel.rootPath}/Pictures", "image")
                            )
                        }
                        
                        quickPaths.forEach { quickPath ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (quickPath.icon) {
                                                "download" -> Icons.Default.Download
                                                "description" -> Icons.Default.Description
                                                "image" -> Icons.Default.Image
                                                "music_note" -> Icons.Default.MusicNote
                                                "movie" -> Icons.Default.Movie
                                                else -> Icons.Default.Folder
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = quickPath.name,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.navigateToPane(quickPath.path, panePosition)
                                    showQuickAccessMenu = false
                                }
                            )
                        }
                    }
                }
                
                // 绑定指示器
                if (boundPreviewPane != null) {
                    Surface(
                        onClick = { PaneBindingManager.unbind(panePosition) },
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "已绑定",
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = boundPreviewPane.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                // 文件系统后端指示器
                val activeBackendState = backendState
                if (activeBackendState?.isRemote == true || viewModel.isRemoteFileSystem) {
                    val backendMode = activeBackendState?.mode
                    val backendText = when (backendMode) {
                        ActiveFileSystemMode.REMOTE_SERVER -> "Server"
                        ActiveFileSystemMode.SSH_SFTP -> "SFTP"
                        ActiveFileSystemMode.LOCAL, null -> viewModel.fileSystemDisplayName
                    }
                    val backendColor = when (backendMode) {
                        ActiveFileSystemMode.REMOTE_SERVER -> MaterialTheme.colorScheme.primaryContainer
                        ActiveFileSystemMode.SSH_SFTP -> MaterialTheme.colorScheme.tertiaryContainer
                        ActiveFileSystemMode.LOCAL, null -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val backendContentColor = when (backendMode) {
                        ActiveFileSystemMode.REMOTE_SERVER -> MaterialTheme.colorScheme.onPrimaryContainer
                        ActiveFileSystemMode.SSH_SFTP -> MaterialTheme.colorScheme.onTertiaryContainer
                        ActiveFileSystemMode.LOCAL, null -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        color = backendColor,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = backendText,
                                modifier = Modifier.size(12.dp),
                                tint = backendContentColor
                            )
                            Text(
                                text = backendText,
                                style = MaterialTheme.typography.labelSmall,
                                color = backendContentColor,
                                maxLines = 1
                            )
                        }
                    }
                }
                
                // 路径显示（单行，溢出省略）
                // 如果有过滤，显示过滤图标和内容
                if (filterQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "已过滤",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    // 递归搜索指示
                    if (isRecursiveSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "递归搜索",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                
                // 构建显示文本
                val displayText = when {
                    filterQuery.isNotEmpty() && isRecursiveSearch -> "搜索: $filterQuery"
                    filterQuery.isNotEmpty() -> "$currentPath [$filterQuery]"
                    else -> currentPath
                }
                
                Text(
                    text = displayText,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // 选中模式UI - 当有选中文件时显示
                if (selectedFiles.isNotEmpty()) {
                    // 显示选中数量
                    Text(
                        text = "${selectedFiles.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    
                    // 取消选中按钮（勾号）
                    androidx.compose.foundation.layout.Box {
                        androidx.compose.material3.IconButton(
                            onClick = { viewModel.clearSelectionForPane(panePosition) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "取消选中",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    // 操作菜单按钮（三个点）
                    androidx.compose.foundation.layout.Box {
                        androidx.compose.material3.IconButton(
                            onClick = { showSelectionMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "操作",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        // 选中文件操作下拉菜单
                        androidx.compose.material3.DropdownMenu(
                            expanded = showSelectionMenu,
                            onDismissRequest = { showSelectionMenu = false }
                        ) {
                            // 复制
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("复制") },
                                onClick = {
                                    ClipboardManager.copyFiles(selectedFiles.toList())
                                    showSelectionMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, null)
                                }
                            )
                            // 剪切
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("剪切") },
                                onClick = {
                                    ClipboardManager.cutFiles(selectedFiles.toList())
                                    showSelectionMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCut, null)
                                }
                            )
                            // 重命名（仅单个文件时可用）
                            if (selectedFiles.size == 1) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("重命名") },
                                    onClick = {
                                        val file = fileList.find { it.path == selectedFiles.first() }
                                        if (file != null) {
                                            renameTargetFile = file
                                            showRenameDialog = true
                                        }
                                        showSelectionMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, null)
                                    }
                                )
                                // 更多（打开完整文件菜单）
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("更多") },
                                    onClick = {
                                        val file = fileList.find { it.path == selectedFiles.first() }
                                        if (file != null) {
                                            onFileMenuClick(file)
                                        }
                                        showSelectionMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.MoreHoriz, null)
                                    }
                                )
                            }
                            // 压缩
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("压缩") },
                                onClick = {
                                    // TODO: 打开压缩对话框
                                    showSelectionMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FolderZip, null)
                                }
                            )
                            androidx.compose.material3.HorizontalDivider()
                            // 删除
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    viewModel.deleteFilesForPane(panePosition) { _, _ ->
                                        viewModel.loadFileListForPane(panePosition)
                                    }
                                    showSelectionMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                }
                            )
                        }
                    }
                } else {
                    // 菜单 (使用优化版本)
                    PaneOptionsMenuV2(
                        viewModel = viewModel,
                        isLeft = isLeft,
                        panePosition = panePosition,
                        onFilterClick = { showFilterDialog = true },
                        onClosePane = onClosePane
                    )
                }
            }
        }
        
        Divider(modifier = Modifier.height(1.dp))
        
        // 判断是否显示目标区域蒙版
        // 条件：正在拖动 && 当前区域是目标区域 && 没有悬停在具体文件夹上
        val showTargetOverlay = isDragging && 
            draggedFile != null && 
            isTargetPane &&
            (dragTargetPath == null || dragTargetPath == currentPath)
        
        // 保存整个文件列表区域的边界
        var listAreaBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        
        // 追踪当前是否有任何文件夹被悬停
        var anyFolderHovered by remember { mutableStateOf(false) }
        
        // 当进入此区域但没有悬停在任何文件夹上时，重置为当前目录
        LaunchedEffect(isTargetPane, anyFolderHovered, currentPath) {
            if (isTargetPane && !anyFolderHovered) {
                // 稍微延迟，确保文件夹悬停检测有机会执行
                kotlinx.coroutines.delay(50)
                if (dragTargetPath != currentPath && !anyFolderHovered) {
                    DragState.setDragTarget(currentPath)
                }
            }
        }
        
        // 文件列表
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    val topLeft = coordinates.localToRoot(Offset.Zero)
                    val bottomRight = coordinates.localToRoot(
                        Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
                    )
                    listAreaBounds = Rect(
                        left = topLeft.x,
                        top = topLeft.y,
                        right = bottomRight.x,
                        bottom = bottomRight.y
                    )
                }
        ) {
            if (fileList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "文件夹为空",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                // 根据视图模式显示不同的布局
                when (viewMode) {
                    FileViewMode.GRID -> {
                        // 网格视图（简洁模式）
                        FileGridView(
                            files = fileList,
                            iconSize = gridIconSize,
                            selectedFiles = selectedFiles,
                            onFileTap = { file ->
                                if (!isDragging) {
                                    onFileClick(file)
                                }
                            },
                            onFileLongPress = { file ->
                                // 长按弹出菜单（当没有onToggleSelection回调时）
                                onFileMenuClick(file)
                            },
                            onIconSizeChange = { newSize ->
                                onGridIconSizeChange?.invoke(newSize)
                            },
                            panePosition = panePosition,
                            onToggleSelection = { file ->
                                // 长按切换选中状态
                                viewModel.toggleFileSelectionForPane(file.path, panePosition)
                            },
                            scrollToIndex = scrollToIndex,
                            onScrollComplete = onScrollComplete,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        // 列表视图（开发者模式 / 默认）
                        val listState = rememberLazyListState()
                        
                        // 处理程序化滚动定位
                        LaunchedEffect(scrollToIndex) {
                            if (scrollToIndex >= 0 && scrollToIndex < fileList.size) {
                                listState.animateScrollToItem(scrollToIndex)
                                onScrollComplete()
                            }
                        }
                        
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(fileList, key = { it.path }) { fileItem ->
                                // 仅在拖动中且此窗格是目标且是目录时，才启用位置追踪和悬停检测
                                val needsHoverDetection = isDragging && isTargetPane && 
                                    fileItem.isDirectory && draggedFile != null && 
                                    draggedFile!!.path != fileItem.path
                                
                                var itemBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                                
                                // 仅在需要时计算悬停状态
                                val isBeingHovered by remember {
                                    derivedStateOf {
                                        if (!needsHoverDetection || itemBounds == null) {
                                            false
                                        } else {
                                            val dragPos = DragState.dragCurrentPosition
                                            val bounds = itemBounds!!
                                            dragPos.x >= bounds.left && dragPos.x <= bounds.right &&
                                                dragPos.y >= bounds.top && dragPos.y <= bounds.bottom
                                        }
                                    }
                                }
                                
                                // 当悬停状态变化时，更新 DragState 和 anyFolderHovered
                                LaunchedEffect(isBeingHovered, fileItem.path, currentPath) {
                                    if (isBeingHovered) {
                                        anyFolderHovered = true
                                        DragState.setDragTarget(fileItem.path)
                                    } else if (anyFolderHovered && dragTargetPath == fileItem.path) {
                                        anyFolderHovered = false
                                    }
                                }
                                
                                // 检测是否为拖动目标（用于视觉高亮）
                                val isHoverTarget = isDragging && 
                                    draggedFile != null &&
                                    draggedFile!!.path != fileItem.path && 
                                    fileItem.isDirectory &&
                                    dragTargetPath == fileItem.path
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        // 仅在拖动到目录项时才追踪位置，避免不拖动时150+次/帧的坐标计算
                                        .then(
                                            if (needsHoverDetection) {
                                                Modifier.onGloballyPositioned { coordinates ->
                                                    val topLeft = coordinates.localToRoot(Offset.Zero)
                                                    itemBounds = Rect(
                                                        left = topLeft.x,
                                                        top = topLeft.y,
                                                        right = topLeft.x + coordinates.size.width.toFloat(),
                                                        bottom = topLeft.y + coordinates.size.height.toFloat()
                                                    )
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .then(
                                            if (isHoverTarget) {
                                                Modifier.background(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                ) {
                                    CompactFileItemRow(
                                        fileItem = fileItem,
                                        isSelected = selectedFiles.contains(fileItem.path),
                                        isHighlighted = highlightedFile == fileItem.path,
                                        highlightVersion = highlightVersion,
                                        onClick = { 
                                            if (!isDragging) {
                                                onHighlightConsumed()
                                                onFileClick(fileItem)
                                            }
                                        },
                                        onLongClick = { },
                                        onMenuClick = { onFileMenuClick(fileItem) },
                                        onSwipe = { viewModel.onFileSwipedForPane(fileItem.path, panePosition) },
                                        onDragStart = { file ->
                                            // 开始拖动时，记录来源区域
                                            DragState.dragSourcePane = panePosition
                                        },
                                        onDragEnd = {
                                            // 拖动结束后刷新所有区域
                                            viewModel.loadFileListForPane(PanePosition.TOP_LEFT)
                                            viewModel.loadFileListForPane(PanePosition.TOP_RIGHT)
                                            viewModel.loadFileListForPane(PanePosition.BOTTOM)
                                        },
                                        panePosition = panePosition
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // 目标区域蒙版
            if (showTargetOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "放开以移动到此目录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
