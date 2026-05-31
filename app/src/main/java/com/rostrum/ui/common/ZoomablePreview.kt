package com.rostrum.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 可缩放的预览容器
 * 
 * 为预览组件提供统一的缩放和滚动功能
 */
@Composable
fun ZoomablePreview(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    showZoomControls: Boolean = true,
    minScale: Float = 0.5f,
    maxScale: Float = 3.0f,
    initialScale: Float = 1.0f
) {
    var scale by remember { mutableFloatStateOf(initialScale) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val scrollState = rememberScrollState()
    
    Column(modifier = modifier.fillMaxSize()) {
        // 缩放控制栏
        if (showZoomControls) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        scale = (scale - 0.25f).coerceAtLeast(minScale)
                    },
                    enabled = scale > minScale
                ) {
                    Icon(Icons.Filled.ZoomOut, contentDescription = "缩小")
                }
                
                Text(
                    text = "${(scale * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                
                IconButton(
                    onClick = {
                        scale = (scale + 0.25f).coerceAtMost(maxScale)
                    },
                    enabled = scale < maxScale
                ) {
                    Icon(Icons.Filled.ZoomIn, contentDescription = "放大")
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                IconButton(
                    onClick = {
                        scale = 1.0f
                        offset = Offset.Zero
                    }
                ) {
                    Icon(Icons.Filled.FitScreen, contentDescription = "重置")
                }
            }
        }
        
        // 可缩放的内容区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(minScale, maxScale)
                        offset += pan
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    content()
                }
            }
        }
    }
}

