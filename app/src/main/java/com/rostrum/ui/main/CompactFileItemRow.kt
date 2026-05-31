package com.rostrum.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rostrum.core.domain.model.FileItem
import com.rostrum.ui.common.FileIconUtils
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color

/**
 * 紧凑的文件项行组件（用于双列布局，高度较小）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactFileItemRow(
    fileItem: FileItem,
    isSelected: Boolean,
    isHighlighted: Boolean = false,
    highlightVersion: Int = 0,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit = {},
    onSwipe: () -> Unit = {},
    onDragStart: ((FileItem) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    panePosition: PanePosition? = null
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    // 高亮动画：绿色闪烁后保持微弱绿色背景
    val highlightAlpha = remember { Animatable(0f) }
    LaunchedEffect(isHighlighted, highlightVersion) {
        if (isHighlighted) {
            // 快速亮起 -> 闪烁 -> 保持微弱高亮
            highlightAlpha.animateTo(0.35f, animationSpec = tween(200))
            highlightAlpha.animateTo(0.1f, animationSpec = tween(300))
            highlightAlpha.animateTo(0.3f, animationSpec = tween(200))
            highlightAlpha.animateTo(0.15f, animationSpec = tween(500))
        } else {
            highlightAlpha.animateTo(0f, animationSpec = tween(200))
        }
    }
    val highlightColor = Color(0xFF4CAF50) // 绿色高亮
    val isActiveHighlight = highlightAlpha.value > 0f
    
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val isDragging = DragState.isDragging && DragState.draggedFile?.path == fileItem.path
    val dragAlpha = if (isDragging) 0.5f else 1f
    
    // 保存组件的全局位置，用于计算拖动预览位置
    var componentGlobalPosition by remember { mutableStateOf(Offset.Zero) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .alpha(dragAlpha)
                .onGloballyPositioned { coordinates: LayoutCoordinates ->
                    // 保存组件的全局位置
                    componentGlobalPosition = coordinates.localToRoot(Offset.Zero)
                }
                .pointerInput(fileItem.path, panePosition) {
                    // 长按拖动检测
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            // offset 是相对于当前组件的坐标，转换为全局坐标
                            val globalStart = componentGlobalPosition + offset
                            // 使用传入的 panePosition，如果没有则使用默认值
                            val sourcePane = panePosition ?: PanePosition.TOP_LEFT
                            DragState.startDrag(fileItem, globalStart, sourcePane)
                            onDragStart?.invoke(fileItem)
                        },
                        onDragEnd = {
                            DragState.endDrag()
                            onDragEnd?.invoke()
                        },
                        onDragCancel = {
                            DragState.cancelDrag()
                            onDragEnd?.invoke()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // change.position 是相对于当前组件的坐标，转换为全局坐标
                            val globalPosition = componentGlobalPosition + change.position
                            DragState.updateDragPosition(globalPosition)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX.value.absoluteValue > 100f) {
                                onSwipe()
                            }
                            scope.launch {
                                offsetX.animateTo(0f)
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            // 如果正在拖动，不处理水平滑动
                            if (!DragState.isDragging) {
                                change.consume()
                                scope.launch {
                                    val newOffset = offsetX.value + dragAmount
                                    if (newOffset.absoluteValue < 300f) {
                                        offsetX.snapTo(newOffset)
                                    }
                                }
                            }
                        }
                    )
                }
                .clickable(
                    onClick = {
                        if (!DragState.isDragging) {
                            onClick()
                        }
                    }
                ),
            color = if (isActiveHighlight) {
                highlightColor.copy(alpha = highlightAlpha.value * 0.3f)
            } else {
                backgroundColor
            }
        ) {
            // 高亮时绿色左侧边框
            if (isActiveHighlight) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(highlightColor)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            // 文件图标
            Icon(
                imageVector = FileIconUtils.getFileIcon(fileItem),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = FileIconUtils.getFileIconColor(fileItem)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 文件信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 文件名
                Text(
                    text = fileItem.name,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isActiveHighlight) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isActiveHighlight) highlightColor else Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // 详细信息（日期 | 大小/项数）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = fileItem.getFormattedDate(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = fileItem.getFormattedSize(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
            }
            
            // 选中时显示三点菜单按钮
            if (isSelected) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多操作",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
    }
    
    Divider(modifier = Modifier.height(1.dp))
}

