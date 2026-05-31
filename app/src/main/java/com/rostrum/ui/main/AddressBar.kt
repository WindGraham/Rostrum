package com.rostrum.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.rostrum.core.config.ConfigManagerImpl
import com.rostrum.core.config.QuickAccessPath
import kotlinx.coroutines.launch

/**
 * 地址栏组件
 * 
 * 功能：
 * 1. 显示当前路径（面包屑形式）
 * 2. 点击路径可点击跳转
 * 3. 点击路径栏可输入自定义路径
 * 4. 支持快捷地址选择
 * 
 * @param currentPath 当前路径
 * @param rootPath 根路径
 * @param quickAccessPaths 快捷地址列表
 * @param onNavigateTo 导航到指定路径
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBar(
    currentPath: String,
    rootPath: String,
    quickAccessPaths: List<QuickAccessPath>,
    onNavigateTo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var showQuickAccessMenu by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(currentPath)) }
    val focusRequester = remember { FocusRequester() }
    
    // 当路径改变时更新文本字段
    LaunchedEffect(currentPath, isEditing) {
        if (!isEditing) {
            textFieldValue = TextFieldValue(currentPath)
        }
    }
    
    // 进入编辑模式时获取焦点
    LaunchedEffect(isEditing) {
        if (isEditing) {
            kotlinx.coroutines.delay(50)
            focusRequester.requestFocus()
            // 选中所有文本
            textFieldValue = textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length))
        }
    }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 快捷地址按钮
            IconButton(
                onClick = { showQuickAccessMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = "快捷地址",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // 快捷地址下拉菜单
            DropdownMenu(
                expanded = showQuickAccessMenu,
                onDismissRequest = { showQuickAccessMenu = false },
                modifier = Modifier.width(240.dp)
            ) {
                Text(
                    text = "快捷地址",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                HorizontalDivider()
                
                quickAccessPaths.forEach { quickPath ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = quickPath.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = quickPath.path,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        },
                        onClick = {
                            onNavigateTo(quickPath.path)
                            showQuickAccessMenu = false
                        }
                    )
                }
            }
            
            // 路径显示或编辑区域
            if (isEditing) {
                // 编辑模式
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            innerTextField()
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            isEditing = false
                            onNavigateTo(textFieldValue.text.trim())
                        }
                    )
                )
                
                // 确认按钮
                IconButton(
                    onClick = {
                        isEditing = false
                        onNavigateTo(textFieldValue.text.trim())
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "确认",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                // 取消按钮
                IconButton(
                    onClick = { isEditing = false },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "取消",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                // 面包屑显示模式
                AddressBreadcrumb(
                    currentPath = currentPath,
                    rootPath = rootPath,
                    quickAccessPaths = quickAccessPaths,
                    onNavigateTo = onNavigateTo,
                    onStartEditing = { isEditing = true },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 地址面包屑组件
 */
@Composable
private fun AddressBreadcrumb(
    currentPath: String,
    rootPath: String,
    quickAccessPaths: List<QuickAccessPath>,
    onNavigateTo: (String) -> Unit,
    onStartEditing: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onStartEditing)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // 检查是否匹配某个快捷地址
            val matchingQuickPath = quickAccessPaths.find { 
                currentPath == it.path || currentPath.startsWith(it.path + "/")
            }
            
            if (matchingQuickPath != null && currentPath == matchingQuickPath.path) {
                // 当前正好在某个快捷地址
                Icon(
                    imageVector = when (matchingQuickPath.icon) {
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
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = matchingQuickPath.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                // 显示面包屑路径
                val displayPath = if (matchingQuickPath != null) {
                    // 用快捷地址名称替换路径前缀
                    val relativePath = currentPath.substring(matchingQuickPath.path.length)
                    if (relativePath.isEmpty() || relativePath == "/") {
                        matchingQuickPath.name
                    } else {
                        matchingQuickPath.name + relativePath
                    }
                } else {
                    currentPath
                }
                
                // 分割路径显示
                val pathSegments = displayPath.split("/").filter { it.isNotEmpty() }
                
                pathSegments.forEachIndexed { index, segment ->
                    if (index > 0) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    
                    // 计算点击时应该导航到的路径
                    val targetPath = if (matchingQuickPath != null && index == 0) {
                        matchingQuickPath.path
                    } else {
                        val prefix = matchingQuickPath?.path?.removeSuffix("/") ?: ""
                        val segmentPrefix = pathSegments.take(
                            if (matchingQuickPath != null) index + 1 else index + 1
                        ).joinToString("/", prefix = "")
                        if (prefix.isNotEmpty()) "$prefix/$segmentPrefix" else "/$segmentPrefix"
                    }
                    
                    TextButton(
                        onClick = { onNavigateTo(targetPath) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = segment,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                    }
                }
                
                if (pathSegments.isEmpty()) {
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 快捷地址管理对话框
 * 
 * 功能：
 * 1. 显示快捷地址列表
 * 2. 支持滑动编辑/删除
 * 3. 支持添加新地址
 * 4. 设置默认打开路径
 */
@Composable
fun QuickAccessManagerDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configManager = remember { ConfigManagerImpl.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    var quickPaths by remember { mutableStateOf<List<QuickAccessPath>>(emptyList()) }
    var defaultPath by remember { mutableStateOf("") }
    
    // 编辑对话框状态
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf(-1) }
    var editingName by remember { mutableStateOf("") }
    var editingPath by remember { mutableStateOf("") }
    
    // 加载配置
    LaunchedEffect(Unit) {
        val config = configManager.getAppConfig()
        quickPaths = config.fileManager.quickAccessPaths
        defaultPath = config.fileManager.defaultOpenPath
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("快捷地址管理") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                // 默认打开路径设置
                Text(
                    text = "默认打开目录",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                OutlinedTextField(
                    value = defaultPath,
                    onValueChange = { defaultPath = it },
                    label = { Text("留空表示使用根目录") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 快捷地址列表标题
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "快捷地址列表（左滑编辑，右滑删除）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    TextButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加")
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 快捷地址列表（可滚动）
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        count = quickPaths.size,
                        key = { index -> quickPaths[index].path + quickPaths[index].name }
                    ) { index ->
                        val path = quickPaths[index]
                        SwipeableQuickAccessItem(
                            path = path,
                            onEdit = {
                                editingIndex = index
                                editingName = path.name
                                editingPath = path.path
                                showEditDialog = true
                            },
                            onDelete = {
                                quickPaths = quickPaths.toMutableList().apply { removeAt(index) }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        configManager.updateAppConfig { config ->
                            config.copy(
                                fileManager = config.fileManager.copy(
                                    defaultOpenPath = defaultPath.trim(),
                                    quickAccessPaths = quickPaths
                                )
                            )
                        }
                        onDismiss()
                    }
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
    
    // 添加快捷地址对话框
    if (showAddDialog) {
        AddQuickAccessDialog(
            onAdd = { name, path ->
                quickPaths = quickPaths + QuickAccessPath(
                    name = name.trim(),
                    path = path.trim()
                )
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
    
    // 编辑快捷地址对话框
    if (showEditDialog && editingIndex >= 0) {
        EditQuickAccessDialog(
            initialName = editingName,
            initialPath = editingPath,
            onSave = { name, path ->
                quickPaths = quickPaths.toMutableList().apply {
                    set(editingIndex, QuickAccessPath(
                        name = name.trim(),
                        path = path.trim(),
                        icon = quickPaths[editingIndex].icon
                    ))
                }
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }
}

/**
 * 可滑动的快捷地址项
 * 
 * 手势：
 * - 向右滑：显示编辑按钮，松开后编辑
 * - 向左滑：显示删除按钮，松开后删除
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableQuickAccessItem(
    path: QuickAccessPath,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val maxSwipe = 120f // 最大滑动距离
    
    Box(modifier = Modifier.fillMaxWidth()) {
        // 背景层（根据滑动方向显示不同内容）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    when {
                        offsetX > 0 -> MaterialTheme.colorScheme.primaryContainer // 右滑 - 编辑
                        offsetX < 0 -> MaterialTheme.colorScheme.errorContainer   // 左滑 - 删除
                        else -> Color.Transparent
                    }
                )
                .padding(horizontal = 20.dp),
            contentAlignment = when {
                offsetX > 0 -> Alignment.CenterStart // 右滑 - 编辑在左边
                offsetX < 0 -> Alignment.CenterEnd   // 左滑 - 删除在右边
                else -> Alignment.Center
            }
        ) {
            if (offsetX > 20f) {
                // 右滑显示编辑
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "编辑",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (offsetX < -20f) {
                // 左滑显示删除
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "删除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        // 前景层（卡片内容）
        QuickAccessItemCard(
            path = path,
            modifier = Modifier
                .offset(x = offsetX.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                offsetX > maxSwipe * 0.5f -> {
                                    // 右滑超过阈值 - 编辑
                                    offsetX = 0f
                                    onEdit()
                                }
                                offsetX < -maxSwipe * 0.5f -> {
                                    // 左滑超过阈值 - 删除
                                    offsetX = 0f
                                    onDelete()
                                }
                                else -> {
                                    // 未达到阈值，回弹
                                    offsetX = 0f
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset = offsetX + dragAmount
                            offsetX = newOffset.coerceIn(-maxSwipe, maxSwipe)
                        }
                    )
                }
        )
    }
}

/**
 * 快捷地址卡片（不可滑动）
 */
@Composable
private fun QuickAccessItemCard(
    path: QuickAccessPath,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (path.icon) {
                    "download" -> Icons.Default.Download
                    "description" -> Icons.Default.Description
                    "image" -> Icons.Default.Image
                    "music_note" -> Icons.Default.MusicNote
                    "movie" -> Icons.Default.Movie
                    else -> Icons.Default.Folder
                },
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = path.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                Text(
                    text = path.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 添加快捷地址对话框
 */
@Composable
private fun AddQuickAccessDialog(
    onAdd: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加快捷地址") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（如：下载、文档）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("路径（如：/storage/emulated/0/Download）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && path.isNotBlank()) {
                        onAdd(name, path)
                        name = ""
                        path = ""
                    }
                },
                enabled = name.isNotBlank() && path.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 编辑快捷地址对话框
 */
@Composable
private fun EditQuickAccessDialog(
    initialName: String,
    initialPath: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var path by remember { mutableStateOf(initialPath) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑快捷地址") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("路径") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && path.isNotBlank()) {
                        onSave(name, path)
                    }
                },
                enabled = name.isNotBlank() && path.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
