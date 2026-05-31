package com.rostrum.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rostrum.core.domain.model.FileItem
import java.io.File

/**
 * 文件树节点数据类
 */
data class FileTreeNode(
    val fileItem: FileItem,
    val children: MutableList<FileTreeNode> = mutableListOf(),
    var isExpanded: Boolean = false,
    val level: Int = 0
)

/**
 * 文件树视图（左侧边栏，VSCode风格）
 */
@Composable
fun FileTreeView(
    rootPath: String,
    currentPath: String,
    expandedPaths: Set<String>,
    onPathClick: (String) -> Unit,
    onExpandToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 构建文件树结构并展平为列表
    val flattenedNodes = remember(rootPath, expandedPaths) {
        try {
            val treeNodes = buildFileTree(rootPath, expandedPaths)
            if (treeNodes.isEmpty()) {
                emptyList()
            } else {
                flattenTree(treeNodes.first(), expandedPaths)
            }
        } catch (e: Exception) {
            com.rostrum.core.error.ErrorHandler.handle(e, "FileTreeView.flattenedNodes")
            emptyList()
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 标题栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "文件树",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Divider()
        
        // 文件树列表（使用展平后的列表）
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(flattenedNodes.size, key = { index -> flattenedNodes[index].fileItem.path }) { index ->
                val node = flattenedNodes[index]
                FileTreeNodeItem(
                    node = node,
                    currentPath = currentPath,
                    onPathClick = onPathClick,
                    onExpandToggle = onExpandToggle
                )
            }
        }
    }
}

/**
 * 文件树节点项
 */
@Composable
private fun FileTreeNodeItem(
    node: FileTreeNode,
    currentPath: String,
    onPathClick: (String) -> Unit,
    onExpandToggle: (String) -> Unit
) {
    val isSelected = node.fileItem.path == currentPath
    val hasChildren = node.children.isNotEmpty()
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = (node.level * 24).dp,
                    end = 8.dp,
                    top = 4.dp,
                    bottom = 4.dp
                )
                .clickable {
                    if (node.fileItem.isDirectory) {
                        if (hasChildren) {
                            onExpandToggle(node.fileItem.path)
                        }
                        onPathClick(node.fileItem.path)
                    } else {
                        onPathClick(node.fileItem.path)
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 展开/折叠图标
            if (hasChildren) {
                Icon(
                    imageVector = if (node.isExpanded) {
                        Icons.Default.ArrowDropDown
                    } else {
                        Icons.Default.ArrowRight
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.width(20.dp))
            }
            
            Spacer(modifier = Modifier.width(4.dp))
            
            // 文件/文件夹图标
            Icon(
                imageVector = if (node.fileItem.isDirectory) {
                    Icons.Default.Folder
                } else {
                    Icons.Default.InsertDriveFile
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (node.fileItem.isDirectory) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 文件名
            Text(
                text = node.fileItem.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
    
    Divider()
}

/**
 * 构建文件树结构
 */
private fun buildFileTree(rootPath: String, expandedPaths: Set<String>): List<FileTreeNode> {
    return try {
        val rootFile = File(rootPath)
        if (!rootFile.exists() || !rootFile.isDirectory || !rootFile.canRead()) {
            return emptyList()
        }
        
        val rootNode = FileTreeNode(
            fileItem = FileItem(rootFile),
            isExpanded = expandedPaths.contains(rootPath),
            level = 0
        )
        
        // 递归加载子节点（只加载一级，懒加载其他层级）
        loadChildren(rootNode, expandedPaths)
        
        listOf(rootNode)
    } catch (e: Exception) {
        com.rostrum.core.error.ErrorHandler.handle(e, "FileTreeView.buildFileTree")
        emptyList()
    }
}

/**
 * 递归加载子节点
 */
private fun loadChildren(node: FileTreeNode, expandedPaths: Set<String>) {
    try {
        val dir = File(node.fileItem.path)
        if (!dir.isDirectory || !dir.canRead()) return
        
        val children = dir.listFiles()
            ?.filter { it.canRead() } // 只显示可读的文件
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name })
            ?.map { file ->
                val childNode = FileTreeNode(
                    fileItem = FileItem(file),
                    isExpanded = expandedPaths.contains(file.absolutePath),
                    level = node.level + 1
                )
                
                // 如果已展开，加载子节点
                if (childNode.isExpanded && file.isDirectory) {
                    loadChildren(childNode, expandedPaths)
                }
                
                childNode
            } ?: emptyList()
        
        node.children.clear()
        node.children.addAll(children)
    } catch (e: Exception) {
        // 权限错误等异常只记录调试日志
        com.rostrum.core.error.ErrorHandler.debug(e, "FileTreeView.loadChildren")
    }
}

/**
 * 将树形结构展平为列表（用于 LazyColumn）
 */
private fun flattenTree(node: FileTreeNode, expandedPaths: Set<String>): List<FileTreeNode> {
    val result = mutableListOf<FileTreeNode>()
    
    fun traverse(n: FileTreeNode) {
        result.add(n)
        // 如果节点已展开且有子节点，递归添加子节点
        if (n.isExpanded && n.children.isNotEmpty()) {
            n.children.forEach { child ->
                traverse(child)
            }
        }
    }
    
    traverse(node)
    return result
}

