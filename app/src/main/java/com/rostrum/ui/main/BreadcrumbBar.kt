package com.rostrum.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * 面包屑导航栏（顶部路径导航）
 */
@Composable
fun BreadcrumbBar(
    currentPath: String,
    rootPath: String,
    onPathClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // 根目录按钮
            Row(
                modifier = Modifier
                    .clickable { onPathClick(rootPath) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "根目录",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // 路径片段
            val pathSegments = getPathSegments(currentPath, rootPath)
            pathSegments.forEachIndexed { index, segment ->
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                
                val segmentPath = pathSegments.take(index + 1).joinToString(File.separator)
                val fullPath = if (segmentPath.startsWith(rootPath)) {
                    segmentPath
                } else {
                    "$rootPath${File.separator}$segmentPath"
                }
                
                TextButton(
                    onClick = { onPathClick(fullPath) },
                    modifier = Modifier.padding(horizontal = 4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = segment,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 获取路径片段（相对于根路径）
 */
private fun getPathSegments(currentPath: String, rootPath: String): List<String> {
    val normalizedRoot = File(rootPath).normalize().absolutePath
    val normalizedCurrent = File(currentPath).normalize().absolutePath
    
    return if (normalizedCurrent.startsWith(normalizedRoot)) {
        val relativePath = normalizedCurrent.substring(normalizedRoot.length)
            .trimStart(File.separatorChar)
        
        if (relativePath.isEmpty()) {
            emptyList()
        } else {
            relativePath.split(File.separatorChar)
        }
    } else {
        // 如果当前路径不在根路径下，显示完整路径
        normalizedCurrent.split(File.separatorChar)
    }
}

