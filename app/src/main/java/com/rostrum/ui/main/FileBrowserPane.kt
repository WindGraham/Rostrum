package com.rostrum.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rostrum.core.config.QuickAccessPath
import com.rostrum.core.domain.model.FileItem

/**
 * 文件浏览器面板（双窗口之一）
 * 
 * 更新：使用新的 AddressBar 组件替换简单路径显示
 * 
 * @param currentPath 当前路径
 * @param fileList 文件列表
 * @param selectedFiles 选中的文件
 * @param isLeft 是否为左窗口
 * @param quickAccessPaths 快捷地址列表
 * @param rootPath 根路径
 * @param onFileClick 文件点击回调
 * @param onFileLongClick 文件长按回调
 * @param onNavigateUp 返回上级回调
 * @param onNavigateTo 导航到指定路径
 * @param modifier 修饰符
 */
@Composable
fun FileBrowserPane(
    currentPath: String,
    fileList: List<FileItem>,
    selectedFiles: Set<String>,
    isLeft: Boolean,
    quickAccessPaths: List<QuickAccessPath> = emptyList(),
    rootPath: String = "/storage/emulated/0",
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateTo: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 使用新的地址栏组件
        AddressBar(
            currentPath = currentPath,
            rootPath = rootPath,
            quickAccessPaths = quickAccessPaths.ifEmpty { 
                // 如果没有提供快捷地址，使用默认列表
                getDefaultQuickAccessPaths(rootPath)
            },
            onNavigateTo = { path ->
                onNavigateTo?.invoke(path) ?: onNavigateUp()
            }
        )
        
        // 文件列表
        if (fileList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "文件夹为空",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(fileList, key = { it.path }) { fileItem ->
                    FileItemRow(
                        fileItem = fileItem,
                        isSelected = selectedFiles.contains(fileItem.path),
                        onClick = { onFileClick(fileItem) },
                        onLongClick = { onFileLongClick(fileItem) }
                    )
                }
            }
        }
    }
}

/**
 * 文件项行组件
 */
@Composable
fun FileItemRow(
    fileItem: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clickable(onClick = onLongClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 文件图标
            Icon(
                imageVector = if (fileItem.isDirectory) {
                    Icons.Default.Folder
                } else {
                    Icons.Default.InsertDriveFile
                },
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (fileItem.isDirectory) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 文件信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = fileItem.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                
                if (!fileItem.isDirectory) {
                    Text(
                        text = fileItem.getFormattedSize(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
    
    Divider()
}

/**
 * 获取默认快捷地址列表
 */
private fun getDefaultQuickAccessPaths(rootPath: String): List<QuickAccessPath> {
    return listOf(
        QuickAccessPath("根目录", rootPath, "folder"),
        QuickAccessPath("下载", "$rootPath/Download", "download"),
        QuickAccessPath("文档", "$rootPath/Documents", "description"),
        QuickAccessPath("图片", "$rootPath/Pictures", "image"),
        QuickAccessPath("音乐", "$rootPath/Music", "music_note"),
        QuickAccessPath("视频", "$rootPath/Movies", "movie")
    )
}
