package com.rostrum.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rostrum.core.config.GridIconSize
import com.rostrum.core.domain.model.FileItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

/**
 * 文件图标网格视图
 * 简洁模式下的文件浏览器，使用图标网格代替列表
 * 
 * @param onFileTap 单击文件回调
 * @param onFileLongPress 长按文件回调 - 现在用于切换选中状态
 * @param onToggleSelection 切换选中状态回调（如果为null，则使用onFileLongPress）
 * @param scrollToIndex 滚动到指定索引（-1 表示不滚动）
 * @param onScrollComplete 滚动完成回调
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridView(
    files: List<FileItem>,
    iconSize: GridIconSize,
    selectedFiles: Set<String>,
    onFileTap: (FileItem) -> Unit,
    onFileLongPress: (FileItem) -> Unit,
    onIconSizeChange: (GridIconSize) -> Unit,
    panePosition: PanePosition? = null,
    onToggleSelection: ((FileItem) -> Unit)? = null,
    scrollToIndex: Int = -1,
    onScrollComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentIconSize by remember(iconSize) { mutableStateOf(iconSize) }
    var accumulatedScale by remember { mutableFloatStateOf(1f) }
    // 多指触控追踪 - 用于在缩放时禁止触发选中
    var pointerCount by remember { mutableIntStateOf(0) }
    var wasMultiTouch by remember { mutableStateOf(false) }  // 记录本次手势是否有过多指触控
    
    // 双指缩放手势检测
    val gridState = rememberLazyGridState()
    
    // 处理滚动到指定索引
    LaunchedEffect(scrollToIndex) {
        if (scrollToIndex >= 0 && scrollToIndex < files.size) {
            gridState.animateScrollToItem(scrollToIndex)
            onScrollComplete()
        }
    }
    
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // 监控触控点数量
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val currentPointers = event.changes.count { it.pressed }
                        pointerCount = currentPointers
                        
                        // 检测到多指触控
                        if (currentPointers > 1) {
                            wasMultiTouch = true
                        }
                        // 所有手指抬起时重置
                        if (currentPointers == 0) {
                            wasMultiTouch = false
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    accumulatedScale *= zoom
                    
                    // 当累积缩放超过阈值时切换图标大小
                    when {
                        accumulatedScale > 1.5f -> {
                            // 放大 -> 更大的图标
                            val newSize = when (currentIconSize) {
                                GridIconSize.TINY -> GridIconSize.SMALL
                                GridIconSize.SMALL -> GridIconSize.MEDIUM
                                GridIconSize.MEDIUM -> GridIconSize.LARGE
                                GridIconSize.LARGE -> GridIconSize.LARGE
                            }
                            if (newSize != currentIconSize) {
                                currentIconSize = newSize
                                onIconSizeChange(newSize)
                            }
                            accumulatedScale = 1f
                        }
                        accumulatedScale < 0.67f -> {
                            // 缩小 -> 更小的图标
                            val newSize = when (currentIconSize) {
                                GridIconSize.LARGE -> GridIconSize.MEDIUM
                                GridIconSize.MEDIUM -> GridIconSize.SMALL
                                GridIconSize.SMALL -> GridIconSize.TINY
                                GridIconSize.TINY -> GridIconSize.TINY
                            }
                            if (newSize != currentIconSize) {
                                currentIconSize = newSize
                                onIconSizeChange(newSize)
                            }
                        accumulatedScale = 1f
                        }
                    }
                }
            }
    ) {
        val iconDp = currentIconSize.iconDp.dp
        val contentPadding: Dp
        val horizontalSpacing: Dp
        val verticalSpacing: Dp
        val extraItemWidth: Dp
        val itemPadding: Dp
        when (currentIconSize) {
            GridIconSize.TINY -> {
                contentPadding = 6.dp
                horizontalSpacing = 6.dp
                verticalSpacing = 10.dp
                extraItemWidth = 8.dp
                itemPadding = 4.dp
            }
            GridIconSize.SMALL -> {
                contentPadding = 8.dp
                horizontalSpacing = 8.dp
                verticalSpacing = 12.dp
                extraItemWidth = 12.dp
                itemPadding = 6.dp
            }
            GridIconSize.MEDIUM -> {
                contentPadding = 8.dp
                horizontalSpacing = 8.dp
                verticalSpacing = 12.dp
                extraItemWidth = 16.dp
                itemPadding = 8.dp
            }
            GridIconSize.LARGE -> {
                contentPadding = 8.dp
                horizontalSpacing = 10.dp
                verticalSpacing = 14.dp
                extraItemWidth = 20.dp
                itemPadding = 8.dp
            }
        }
        val availableWidth = maxWidth - (contentPadding * 2)
        val minCellWidth = iconDp + extraItemWidth
        val columns = max(1, ((availableWidth + horizontalSpacing) / (minCellWidth + horizontalSpacing)).toInt())

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            contentPadding = PaddingValues(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            items(
                items = files,
                key = { it.path }
            ) { file ->
                FileGridItem(
                    file = file,
                    iconSize = currentIconSize,
                    isSelected = selectedFiles.contains(file.path),
                    onTap = { onFileTap(file) },
                    onLongPress = { 
                        // 长按默认切换选中状态
                        onToggleSelection?.invoke(file) ?: onFileLongPress(file)
                    },
                    panePosition = panePosition,
                    itemPadding = itemPadding,
                    pointerCount = pointerCount,
                    wasMultiTouch = wasMultiTouch
                )
            }
        }
    }
}

/**
 * 文件网格项
 * 可爱亲民的图标设计
 * 支持分阶段长按：300ms弹菜单，继续按住500ms开始拖拽
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    file: FileItem,
    iconSize: GridIconSize,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    panePosition: PanePosition? = null,
    itemPadding: Dp = 8.dp,
    pointerCount: Int = 0,  // 当前触控点数量
    wasMultiTouch: Boolean = false,  // 本次手势是否有过多指触控
    modifier: Modifier = Modifier
) {
    val iconDp = iconSize.iconDp.dp
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 根据文件类型获取图标和颜色
    val (icon, iconColor, bgColor) = getFileIconInfo(file)
    
    // 是否是图片文件
    val isImage = file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    
    // 拖拽相关状态
    var componentPosition by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var menuShown by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                componentPosition = coordinates.positionInRoot()
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) 
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else 
                    Color.Transparent
            )
            .pointerInput(file.path, panePosition) {
                var pressStartPosition = Offset.Zero
                var currentPosition = Offset.Zero
                
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        
                        // 如果检测到多指触控（缩放手势），取消长按
                        if (pointerCount > 1 || wasMultiTouch) {
                            longPressJob?.cancel()
                            longPressJob = null
                            menuShown = false
                            continue
                        }
                        
                        when {
                            change.pressed && !change.previousPressed -> {
                                // 按下开始
                                pressStartPosition = change.position
                                currentPosition = change.position
                                menuShown = false
                                isDragging = false
                                
                                // 如果是多指触控，不启动长按
                                if (pointerCount > 1 || wasMultiTouch) continue
                                
                                longPressJob = scope.launch {
                                    // 500ms 后触发长按回调（选中文件）- 延长选中时间
                                    delay(500)
                                    // 再次检查是否是多指触控
                                    if (pointerCount > 1 || wasMultiTouch) return@launch
                                    menuShown = true
                                    onLongPress()  // 现在这个回调用于切换选中状态
                                    
                                    // 再等 800ms，如果还在按着就开始拖拽 - 延长拖动触发时间
                                    delay(800)
                                    if (panePosition != null) {
                                        isDragging = true
                                        val globalPos = componentPosition + currentPosition
                                        DragState.startDrag(file, globalPos, panePosition)
                                    }
                                }
                            }
                            change.pressed && change.previousPressed -> {
                                // 按住移动中
                                currentPosition = change.position
                                if (isDragging) {
                                    val globalPos = componentPosition + currentPosition
                                    DragState.updateDragPosition(globalPos)
                                    change.consume()
                                }
                            }
                            !change.pressed && change.previousPressed -> {
                                // 松开
                                longPressJob?.cancel()
                                longPressJob = null
                                
                                // 计算移动距离
                                val distance = (currentPosition - pressStartPosition).getDistance()
                                val tapThreshold = 20f // 20像素以内算点击
                                
                                if (isDragging) {
                                    DragState.endDrag()
                                    isDragging = false
                                } else if (!menuShown && distance < tapThreshold) {
                                    // 短按且没有移动 = 点击
                                    onTap()
                                }
                                menuShown = false
                            }
                        }
                    }
                }
            }
            .padding(itemPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 图标/缩略图区域
        Box(
            modifier = Modifier
                .size(iconDp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isImage) Color.Transparent else bgColor),
            contentAlignment = Alignment.Center
        ) {
            if (isImage) {
                // 图片文件显示缩略图
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(file.path))
                        .crossfade(true)
                        .size(iconSize.iconDp * 2) // 2x for better quality
                        .build(),
                    contentDescription = file.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 其他文件显示图标
                Icon(
                    imageVector = icon,
                    contentDescription = file.name,
                    modifier = Modifier.size(iconDp * 0.5f),
                    tint = iconColor
                )
            }
            
            // 选中标记 - 可点击取消选择
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { 
                            // 点击勾取消选择
                            onLongPress()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "点击取消选择",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // 文件名
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodySmall,
            fontSize = when (iconSize) {
                GridIconSize.TINY -> 9.sp
                GridIconSize.SMALL -> 10.sp
                GridIconSize.MEDIUM -> 11.sp
                GridIconSize.LARGE -> 12.sp
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 根据文件类型获取图标、图标颜色和背景颜色
 */
@Composable
private fun getFileIconInfo(file: FileItem): Triple<ImageVector, Color, Color> {
    return when {
        file.isDirectory -> Triple(
            Icons.Default.Folder,
            Color(0xFF5C6BC0),  // 柔和的靛蓝色
            Color(0xFFE8EAF6)   // 浅靛蓝背景
        )
        else -> when (file.extension.lowercase()) {
            // 文档类
            "pdf" -> Triple(
                Icons.Default.PictureAsPdf,
                Color(0xFFE53935),  // 红色
                Color(0xFFFFEBEE)   // 浅红背景
            )
            "doc", "docx" -> Triple(
                Icons.Default.Description,
                Color(0xFF1565C0),  // 蓝色
                Color(0xFFE3F2FD)   // 浅蓝背景
            )
            "xls", "xlsx" -> Triple(
                Icons.Default.TableChart,
                Color(0xFF2E7D32),  // 绿色
                Color(0xFFE8F5E9)   // 浅绿背景
            )
            "ppt", "pptx" -> Triple(
                Icons.Default.Slideshow,
                Color(0xFFE65100),  // 橙色
                Color(0xFFFFF3E0)   // 浅橙背景
            )
            "txt", "md", "markdown" -> Triple(
                Icons.Default.Article,
                Color(0xFF546E7A),  // 灰蓝色
                Color(0xFFECEFF1)   // 浅灰背景
            )
            
            // 代码类
            "kt", "java", "py", "js", "ts", "cpp", "c", "h", "go", "rs", "swift" -> Triple(
                Icons.Default.Code,
                Color(0xFF7E57C2),  // 紫色
                Color(0xFFEDE7F6)   // 浅紫背景
            )
            "html", "htm", "css", "scss" -> Triple(
                Icons.Default.Language,
                Color(0xFFFF7043),  // 深橙色
                Color(0xFFFBE9E7)   // 浅橙背景
            )
            "json", "xml", "yaml", "yml" -> Triple(
                Icons.Default.DataObject,
                Color(0xFF26A69A),  // 青绿色
                Color(0xFFE0F2F1)   // 浅青绿背景
            )
            
            // 图片类 (不会在这里显示图标，但保留定义)
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> Triple(
                Icons.Default.Image,
                Color(0xFF42A5F5),  // 蓝色
                Color(0xFFE3F2FD)   // 浅蓝背景
            )
            
            // 视频类
            "mp4", "avi", "mkv", "mov", "wmv", "flv" -> Triple(
                Icons.Default.VideoFile,
                Color(0xFFAB47BC),  // 紫色
                Color(0xFFF3E5F5)   // 浅紫背景
            )
            
            // 音频类
            "mp3", "wav", "flac", "aac", "ogg", "m4a" -> Triple(
                Icons.Default.AudioFile,
                Color(0xFFEC407A),  // 粉色
                Color(0xFFFCE4EC)   // 浅粉背景
            )
            
            // 压缩包类
            "zip", "rar", "7z", "tar", "gz", "xz" -> Triple(
                Icons.Default.FolderZip,
                Color(0xFF8D6E63),  // 棕色
                Color(0xFFEFEBE9)   // 浅棕背景
            )
            
            // APK
            "apk" -> Triple(
                Icons.Default.Android,
                Color(0xFF4CAF50),  // 绿色
                Color(0xFFE8F5E9)   // 浅绿背景
            )
            
            // 默认
            else -> Triple(
                Icons.Default.InsertDriveFile,
                Color(0xFF78909C),  // 灰色
                Color(0xFFECEFF1)   // 浅灰背景
            )
        }
    }
}
