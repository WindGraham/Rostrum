package com.rostrum.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rostrum.core.plugin.models.FileInfo
import com.rostrum.ui.main.viewmodel.SshViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * SSH 文件浏览器全屏界面
 * 
 * @param sshViewModel SSH ViewModel
 * @param onDismiss 关闭界面回调
 * @param onOpenTerminal 打开终端回调
 * @param onSwitchToMainWithSsh 返回主页并使用SSH文件系统的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshFileBrowserScreen(
    sshViewModel: SshViewModel,
    onDismiss: () -> Unit,
    onOpenTerminal: () -> Unit = {},
    onSwitchToMainWithSsh: ((String) -> Unit)? = null
) {
    val currentPath by sshViewModel.remoteCurrentPath.collectAsState()
    val fileList by sshViewModel.remoteFileList.collectAsState()
    val isLoading by sshViewModel.isFileBrowserLoading.collectAsState()
    val savedConnections by sshViewModel.savedConnections.collectAsState()
    
    val currentConfig = savedConnections.firstOrNull()
    
    // 显示更多菜单
    var showMoreMenu by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SSH 文件浏览")
                        if (currentConfig != null) {
                            Text(
                                text = "${currentConfig.username}@${currentConfig.host}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 打开终端按钮
                    IconButton(onClick = onOpenTerminal) {
                        Icon(Icons.Default.Terminal, contentDescription = "打开终端")
                    }
                    
                    // 刷新按钮
                    IconButton(onClick = { sshViewModel.refreshRemoteDirectory() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    
                    // 更多菜单
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            // 返回主页并使用SSH文件系统
                            DropdownMenuItem(
                                text = { Text("在主页浏览SSH文件") },
                                onClick = {
                                    showMoreMenu = false
                                    if (currentConfig != null && onSwitchToMainWithSsh != null) {
                                        // 切换到SSH文件系统
                                        sshViewModel.switchToSshFileSystem(currentConfig.id, currentPath)
                                        onSwitchToMainWithSsh(currentPath)
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Home, contentDescription = null)
                                },
                                enabled = currentConfig != null && onSwitchToMainWithSsh != null
                            )
                            
                            // 断开SSH连接
                            DropdownMenuItem(
                                text = { Text("断开连接") },
                                onClick = {
                                    showMoreMenu = false
                                    if (currentConfig != null) {
                                        sshViewModel.disconnect(currentConfig.id)
                                        onDismiss()
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.LinkOff, contentDescription = null)
                                },
                                enabled = currentConfig != null
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 路径导航栏
            PathNavigationBar(
                currentPath = currentPath,
                onNavigateUp = { sshViewModel.navigateUpRemote() },
                onNavigateToPath = { sshViewModel.navigateToRemotePath(it) }
            )
            
            // 文件列表
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (fileList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "空目录",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(fileList) { fileInfo ->
                        RemoteFileItem(
                            fileInfo = fileInfo,
                            onClick = {
                                if (fileInfo.isDirectory) {
                                    sshViewModel.navigateToRemotePath(fileInfo.path)
                                } else {
                                    // TODO: 可以添加文件预览或下载功能
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 路径导航栏
 */
@Composable
private fun PathNavigationBar(
    currentPath: String,
    onNavigateUp: () -> Unit,
    onNavigateToPath: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回上级按钮
            IconButton(
                onClick = onNavigateUp,
                enabled = currentPath != "/"
            ) {
                Icon(
                    Icons.Default.ArrowUpward,
                    contentDescription = "上级目录",
                    tint = if (currentPath != "/") 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 路径显示
            Text(
                text = currentPath,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            // 根目录按钮
            if (currentPath != "/") {
                IconButton(onClick = { onNavigateToPath("/") }) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = "根目录",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 远程文件项
 */
@Composable
private fun RemoteFileItem(
    fileInfo: FileInfo,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Icon(
                imageVector = if (fileInfo.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (fileInfo.isDirectory) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 文件信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileInfo.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row {
                    // 权限
                    Text(
                        text = fileInfo.permissions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // 大小
                    if (!fileInfo.isDirectory) {
                        Text(
                            text = formatFileSize(fileInfo.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // 修改时间
                    Text(
                        text = formatDate(fileInfo.lastModified),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 箭头（目录）
            if (fileInfo.isDirectory) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    Divider(modifier = Modifier.padding(start = 56.dp))
}

/**
 * 格式化文件大小
 */
private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
        else -> "${size / (1024 * 1024 * 1024)} GB"
    }
}

/**
 * 格式化日期
 */
private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
