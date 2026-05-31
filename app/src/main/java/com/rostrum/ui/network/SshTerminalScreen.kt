package com.rostrum.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rostrum.core.ssh.connection.SshConnectionState
import com.rostrum.ui.main.viewmodel.SshViewModel
import com.rostrum.ui.terminal.EnhancedTerminalView
import com.rostrum.ui.terminal.TerminalTheme
import kotlinx.coroutines.flow.collectLatest

/**
 * SSH 终端全屏界面
 * 
 * 复用 EnhancedTerminalView 组件显示终端，支持 ANSI 颜色
 * 
 * @param sshViewModel SSH ViewModel
 * @param onDismiss 关闭界面回调
 * @param onBrowseFiles 打开文件浏览器回调
 * @param onSwitchToMainWithSsh 返回主页并使用SSH文件系统的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshTerminalScreen(
    sshViewModel: SshViewModel,
    onDismiss: () -> Unit,
    onBrowseFiles: () -> Unit = {},
    onSwitchToMainWithSsh: (() -> Unit)? = null
) {
    val terminalSession by sshViewModel.currentTerminalSession.collectAsState()
    val activeConnections by sshViewModel.activeConnections.collectAsState()
    val connectionConfig by sshViewModel.currentConnectionConfig.collectAsState()
    val isLoading by sshViewModel.isLoading.collectAsState()
    
    // 使用局部变量避免智能类型转换问题
    val currentConfig = connectionConfig
    
    // 获取当前连接状态
    val connectionState = currentConfig?.let { activeConnections[it.id] }
    val isConnected = connectionState is SshConnectionState.Connected
    
    // 显示更多菜单
    var showMoreMenu by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "SSH 终端",
                            color = TerminalTheme.text
                        )
                        if (currentConfig != null) {
                            Text(
                                text = "${currentConfig.username}@${currentConfig.host}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TerminalTheme.textMuted
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.ArrowBack, 
                            contentDescription = "返回",
                            tint = TerminalTheme.text
                        )
                    }
                },
                actions = {
                    // 连接状态指示
                    ConnectionStatusIndicator(
                        state = connectionState,
                        isLoading = isLoading
                    )
                    
                    // 浏览文件按钮
                    if (isConnected) {
                        IconButton(onClick = onBrowseFiles) {
                            Icon(
                                Icons.Default.Folder, 
                                contentDescription = "浏览文件",
                                tint = TerminalTheme.cyan
                            )
                        }
                    }
                    
                    // 更多菜单
                    if (isConnected && currentConfig != null) {
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(
                                    Icons.Default.MoreVert, 
                                    contentDescription = "更多",
                                    tint = TerminalTheme.text
                                )
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
                                        // 切换到SSH文件系统
                                        sshViewModel.switchToSshFileSystem(currentConfig.id, "/home")
                                        onSwitchToMainWithSsh?.invoke()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Home, contentDescription = null)
                                    },
                                    enabled = onSwitchToMainWithSsh != null
                                )
                                
                                // 断开SSH连接
                                DropdownMenuItem(
                                    text = { Text("断开连接") },
                                    onClick = {
                                        showMoreMenu = false
                                        sshViewModel.disconnect(currentConfig.id)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.LinkOff, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TerminalTheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(TerminalTheme.background)
        ) {
            when {
                // 未连接且不在加载中
                !isConnected && !isLoading -> {
                    NotConnectedView()
                }
                // 正在加载/连接中
                isLoading -> {
                    LoadingView()
                }
                // 已连接，显示终端
                terminalSession != null -> {
                    EnhancedTerminalView(
                        shellSession = terminalSession!!,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // 已连接但会话为空（理论上不应该发生）
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "正在初始化终端...",
                            color = TerminalTheme.textMuted
                        )
                    }
                }
            }
        }
    }
    
    // 错误处理
    LaunchedEffect(Unit) {
        sshViewModel.error.collectLatest { error ->
            // 错误已在ViewModel中处理
        }
    }
}

/**
 * 连接状态指示器
 */
@Composable
private fun ConnectionStatusIndicator(
    state: SshConnectionState?,
    isLoading: Boolean
) {
    val (color, text) = when {
        isLoading -> TerminalTheme.orange to "连接中"
        state is SshConnectionState.Connected -> TerminalTheme.green to "已连接"
        state is SshConnectionState.Error -> TerminalTheme.red to "错误"
        state is SshConnectionState.Reconnecting -> TerminalTheme.orange to "重连中"
        else -> TerminalTheme.textMuted to "未连接"
    }
    
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, shape = MaterialTheme.shapes.small)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = TerminalTheme.text
        )
    }
}

/**
 * 未连接视图
 */
@Composable
private fun NotConnectedView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = TerminalTheme.textMuted
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "未连接到服务器",
                color = TerminalTheme.text,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请先建立SSH连接",
                color = TerminalTheme.textMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * 加载中视图
 */
@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = TerminalTheme.accent,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "正在连接...",
                color = TerminalTheme.text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
