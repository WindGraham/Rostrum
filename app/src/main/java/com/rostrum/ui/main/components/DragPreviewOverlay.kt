package com.rostrum.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.rostrum.core.domain.model.FileItem
import com.rostrum.ui.main.DragState
import com.rostrum.ui.main.PaneContentType
import com.rostrum.ui.main.PanePosition

/**
 * 拖动预览效果 - 跟随手指移动
 */
@Composable
fun DragPreviewOverlay(
    draggedFile: FileItem,
    dragPosition: Offset,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    Box(
        modifier = modifier
            .offset(
                x = with(density) { dragPosition.x.toDp() - 24.dp },
                y = with(density) { dragPosition.y.toDp() - 24.dp }
            )
            .zIndex(1000f)
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                shape = RoundedCornerShape(12.dp)
            )
            .shadow(8.dp, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (draggedFile.isDirectory) 
                    Icons.Default.Folder 
                else 
                    Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = draggedFile.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 区域拖动目标高亮效果
 * 只在非文件管理区域时显示半透明遮罩
 */
@Composable
fun DragTargetHighlight(
    targetPane: PanePosition,
    targetContentType: PaneContentType?,
    containerGlobalPosition: Offset,
    containerWidth: Int,
    containerHeight: Int,
    currentVerticalWeight: Float,
    currentHorizontalWeight: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    // 使用实际的区域比例
    val topHeight = containerHeight * currentVerticalWeight
    val leftWidth = containerWidth * currentHorizontalWeight
    
    // 只有目标不是文件管理区域时，才显示遮罩效果
    if (targetContentType != PaneContentType.FILE_BROWSER) {
        val targetX: Float
        val targetY: Float
        val targetWidth: Float
        val targetHeight: Float
        
        when (targetPane) {
            PanePosition.TOP_LEFT -> {
                targetX = containerGlobalPosition.x
                targetY = containerGlobalPosition.y
                targetWidth = leftWidth
                targetHeight = topHeight
            }
            PanePosition.TOP_RIGHT -> {
                targetX = containerGlobalPosition.x + leftWidth
                targetY = containerGlobalPosition.y
                targetWidth = (containerWidth - leftWidth)
                targetHeight = topHeight
            }
            PanePosition.BOTTOM -> {
                targetX = containerGlobalPosition.x
                targetY = containerGlobalPosition.y + topHeight
                targetWidth = containerWidth.toFloat()
                targetHeight = (containerHeight - topHeight)
            }
        }
        
        // 半透明遮罩效果
        Box(
            modifier = modifier
                .offset(
                    x = with(density) { targetX.toDp() },
                    y = with(density) { targetY.toDp() }
                )
                .size(
                    width = with(density) { targetWidth.toDp() },
                    height = with(density) { targetHeight.toDp() }
                )
                .zIndex(999f)
                .background(Color.Black.copy(alpha = 0.25f))
        )
    }
}

/**
 * 拖动取消区域 - 显示在右下角
 * 当拖动文件进入此区域时，释放将取消操作
 */
@Composable
fun DragCancelZone(
    dragPosition: Offset,
    containerGlobalPosition: Offset,
    containerWidth: Int,
    containerHeight: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cancelZoneSize = 100.dp
    val cancelZonePadding = 24.dp
    
    // 计算取消区域的位置（右下角）
    val cancelZoneX = containerGlobalPosition.x + containerWidth - with(density) { (cancelZoneSize + cancelZonePadding).toPx() }
    val cancelZoneY = containerGlobalPosition.y + containerHeight - with(density) { (cancelZoneSize + cancelZonePadding).toPx() }
    
    // 检查拖动位置是否在取消区域内
    val cancelZoneRect = Rect(
        left = cancelZoneX,
        top = cancelZoneY,
        right = cancelZoneX + with(density) { cancelZoneSize.toPx() },
        bottom = cancelZoneY + with(density) { cancelZoneSize.toPx() }
    )
    val isInCancelZone = cancelZoneRect.contains(dragPosition)
    
    // 更新 DragState 中的取消区域状态
    LaunchedEffect(isInCancelZone) {
        DragState.updateCancelZone(isInCancelZone)
    }
    
    // 显示取消区域
    Box(
        modifier = modifier
            .offset(
                x = with(density) { cancelZoneX.toDp() },
                y = with(density) { cancelZoneY.toDp() }
            )
            .size(cancelZoneSize)
            .zIndex(998f)
            .background(
                color = if (isInCancelZone) {
                    Color.Red.copy(alpha = 0.7f)
                } else {
                    Color.Gray.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = "取消拖动",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "取消",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
