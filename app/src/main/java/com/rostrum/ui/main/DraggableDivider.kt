package com.rostrum.ui.main

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * 可拖动的分割线（用于调整上下区域高度）
 */
@Composable
fun DraggableDivider(
    onDragDelta: (Float) -> Unit, // 拖动距离（dp）
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        // 使用 dragAmount.y 获取拖动距离（像素）
                        val deltaDp = with(density) { dragAmount.y.toDp().value }
                        onDragDelta(deltaDp)
                    }
                )
            }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (isDragging) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            // 显示拖动提示
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(2.dp)
                                .padding(horizontal = 2.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Divider(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                thickness = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

