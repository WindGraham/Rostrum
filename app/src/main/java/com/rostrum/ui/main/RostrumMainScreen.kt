package com.rostrum.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rostrum.core.filesystem.ActiveFileSystemState
import com.rostrum.core.filesystem.FileSystemBackend
import com.rostrum.core.terminal.TerminalBackend
import com.rostrum.core.terminal.TerminalBackendState
import com.rostrum.core.server.RemoteServerPhase
import com.rostrum.core.server.RemoteServerState
import com.rostrum.core.plugin.models.FileInfo
import com.rostrum.core.ssh.connection.SshConfig
import com.rostrum.core.ssh.connection.SshConnectionState
import com.rostrum.ui.main.viewmodel.SshViewModel
import com.rostrum.ui.terminal.EnhancedTerminalView
import com.rostrum.ui.terminal.xterm.XtermTerminalPane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RostrumMainScreen() {
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val sshViewModel = remember {
        SshViewModel(
            scope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
            cacheDir = File(context.cacheDir, "ssh-file-cache")
        )
    }

    DisposableEffect(Unit) {
        sshViewModel.initRepository(context)
        onDispose { sshViewModel.cleanup() }
    }

    val savedConnections by sshViewModel.savedConnections.collectAsState()
    val activeConnections by sshViewModel.activeConnections.collectAsState()
    val currentConfig by sshViewModel.currentConnectionConfig.collectAsState()
    val currentTerminal by sshViewModel.currentTerminalSession.collectAsState()
    val currentTerminalBackend by sshViewModel.currentTerminalBackend.collectAsState()
    val currentTerminalBackendState = terminalBackendState(currentTerminalBackend)
    val currentBackend by sshViewModel.currentBackend.collectAsState()
    val backendState by com.rostrum.core.filesystem.ActiveFileSystemManager.backendState.collectAsState()
    val remoteServerState by sshViewModel.remoteServerState.collectAsState()
    val remotePath by sshViewModel.remoteCurrentPath.collectAsState()
    val remoteFiles by sshViewModel.remoteFileList.collectAsState()
    val fileBrowserLoading by sshViewModel.isFileBrowserLoading.collectAsState()
    val isLoading by sshViewModel.isLoading.collectAsState()

    var selectedTab by remember { mutableStateOf(BottomTab.HOSTS) }
    var showAddHost by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<FileInfo?>(null) }
    var fileContent by remember { mutableStateOf("") }
    var fileStatus by remember { mutableStateOf("未选择文件") }
    var isEditing by remember { mutableStateOf(false) }

    val connectedHosts = activeConnections
        .filterValues { it is SshConnectionState.Connected }
        .keys
        .mapNotNull { id -> savedConnections.find { it.id == id } ?: currentConfig?.takeIf { it.id == id } }

    LaunchedEffect(currentConfig?.id) {
        currentConfig?.let { selectedTab = BottomTab.HOSTS }
    }

    Scaffold(
        topBar = {
            if (connectedHosts.isNotEmpty()) {
                ConnectionTabBar(
                    connections = connectedHosts,
                    selectedId = currentConfig?.id,
                    onSelect = { sshViewModel.switchToSshFileSystem(it.id, remotePath) },
                    onClose = { sshViewModel.disconnect(it.id) }
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF101826)) {
                BottomTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        },
        containerColor = Color(0xFF0B1120)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                BottomTab.HOSTS -> {
                    if (currentConfig != null && activeConnections[currentConfig!!.id] is SshConnectionState.Connected) {
                        RemoteWorkspace(
                            config = currentConfig!!,
                            terminalSession = currentTerminal,
                            terminalBackend = currentTerminalBackend,
                            terminalBackendState = currentTerminalBackendState,
                            currentBackend = currentBackend,
                            backendState = backendState,
                            remotePath = remotePath,
                            remoteServerState = remoteServerState,
                            remoteFiles = remoteFiles,
                            isFileBrowserLoading = fileBrowserLoading,
                            selectedFile = selectedFile,
                            fileContent = fileContent,
                            fileStatus = fileStatus,
                            isEditing = isEditing,
                            onNavigate = { path ->
                                selectedFile = null
                                fileContent = ""
                                fileStatus = "未选择文件"
                                isEditing = false
                                sshViewModel.navigateToRemotePath(path)
                            },
                            onNavigateUp = {
                                selectedFile = null
                                fileContent = ""
                                fileStatus = "未选择文件"
                                isEditing = false
                                sshViewModel.navigateUpRemote()
                            },
                            onRefresh = { sshViewModel.refreshRemoteDirectory() },
                            onOpenFile = { file ->
                                selectedFile = file
                                isEditing = false
                                fileStatus = "读取中..."
                                uiScope.launch {
                                    val result = currentBackend?.readText(file.path)
                                    if (result == null) {
                                        fileStatus = "远程文件系统未就绪"
                                        fileContent = ""
                                    } else if (result.isSuccess) {
                                        fileContent = result.getOrThrow()
                                        fileStatus = "${file.name} · ${formatFileSize(file.size)}"
                                    } else {
                                        fileContent = ""
                                        fileStatus = "读取失败: ${result.exceptionOrNull()?.message ?: "未知错误"}"
                                    }
                                }
                            },
                            onToggleEdit = { isEditing = !isEditing },
                            onContentChange = { fileContent = it },
                            onSaveFile = {
                                val file = selectedFile ?: return@RemoteWorkspace
                                uiScope.launch {
                                    fileStatus = "保存中..."
                                    val result = currentBackend?.writeText(file.path, fileContent)
                                    fileStatus = if (result?.isSuccess == true) {
                                        sshViewModel.refreshRemoteDirectory()
                                        "已保存: ${file.name}"
                                    } else {
                                        "保存失败: ${result?.exceptionOrNull()?.message ?: "远程文件系统未就绪"}"
                                    }
                                }
                            }
                        )
                    } else {
                        HostsScreen(
                            hosts = savedConnections,
                            activeConnections = activeConnections,
                            isLoading = isLoading,
                            onAddHost = { showAddHost = true },
                            onConnect = { config -> sshViewModel.connect(config) },
                            onDelete = { sshViewModel.deleteConnection(it.id) }
                        )
                    }
                }
                BottomTab.SETTINGS -> RostrumSettingsScreen()
                BottomTab.CONNECTIONS -> ConnectionsScreen(
                    hosts = savedConnections,
                    activeConnections = activeConnections,
                    currentId = currentConfig?.id,
                    onOpen = { selectedTab = BottomTab.HOSTS },
                    onDisconnect = { sshViewModel.disconnect(it.id) }
                )
            }
        }
    }

    if (showAddHost) {
        AddHostDialog(
            onDismiss = { showAddHost = false },
            onSaveAndConnect = { config, shouldSave ->
                if (shouldSave) sshViewModel.saveConnection(config)
                sshViewModel.connect(config)
                showAddHost = false
                selectedTab = BottomTab.HOSTS
            }
        )
    }
}

private enum class BottomTab(val label: String, val icon: ImageVector) {
    HOSTS("主机", Icons.Default.Computer),
    SETTINGS("设置", Icons.Default.Settings),
    CONNECTIONS("连接", Icons.Default.Link)
}

@Composable
internal fun ConnectionTabBar(
    connections: List<SshConfig>,
    selectedId: String?,
    onSelect: (SshConfig) -> Unit,
    onClose: (SshConfig) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = connections.indexOfFirst { it.id == selectedId }.coerceAtLeast(0),
        containerColor = Color(0xFF0F172A),
        edgePadding = 8.dp
    ) {
        connections.forEach { config ->
            Tab(
                selected = config.id == selectedId,
                onClick = { onSelect(config) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(config.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { onClose(config) }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "关闭连接", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            )
        }
    }
}

@Composable
internal fun HostsScreen(
    hosts: List<SshConfig>,
    activeConnections: Map<String, SshConnectionState>,
    isLoading: Boolean,
    onAddHost: () -> Unit,
    onConnect: (SshConfig) -> Unit,
    onDelete: (SshConfig) -> Unit,
    onOpenWorkspace: (SshConfig) -> Unit = onConnect,
    showLocalHost: Boolean = false,
    localConnected: Boolean = false,
    onConnectLocal: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Rostrum", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("SSH 远程工作台", color = Color(0xFF94A3B8))
            }
            Button(onClick = onAddHost) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("添加主机")
            }
        }

        if (hosts.isEmpty() && !showLocalHost) {
            EmptyState(
                title = "还没有主机",
                message = "添加一台 SSH 主机后即可打开远程终端、文件和编辑器。",
                action = "添加主机",
                onAction = onAddHost
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showLocalHost) {
                    item("local-host") {
                        LocalHostCard(
                            connected = localConnected,
                            isLoading = isLoading,
                            onConnect = onConnectLocal
                        )
                    }
                }
                items(hosts, key = { it.id }) { host ->
                    HostCard(
                        config = host,
                        state = activeConnections[host.id],
                        isLoading = isLoading,
                        onConnect = { onConnect(host) },
                        onOpenWorkspace = { onOpenWorkspace(host) },
                        onDelete = { onDelete(host) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalHostCard(
    connected: Boolean,
    isLoading: Boolean,
    onConnect: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111C2E)),
        border = BorderStroke(1.dp, if (connected) Color(0xFF22C55E) else Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("本机", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("本地文件系统 · 本地终端", color = Color(0xFF94A3B8), fontSize = 13.sp)
                Text(if (connected) "已打开" else "未打开", color = if (connected) Color(0xFF22C55E) else Color(0xFF64748B), fontSize = 12.sp)
            }
            OutlinedButton(onClick = onConnect, enabled = !isLoading) {
                Text(if (connected) "打开" else "连接")
            }
        }
    }
}

@Composable
private fun HostCard(
    config: SshConfig,
    state: SshConnectionState?,
    isLoading: Boolean,
    onConnect: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onDelete: () -> Unit
) {
    val connected = state is SshConnectionState.Connected
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111C2E)),
        border = BorderStroke(1.dp, if (connected) Color(0xFF22C55E) else Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Computer, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(config.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("${config.username}@${config.host}:${config.port}", color = Color(0xFF94A3B8), fontSize = 13.sp)
                Text(if (connected) "已连接" else "未连接", color = if (connected) Color(0xFF22C55E) else Color(0xFF64748B), fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = if (connected) onOpenWorkspace else onConnect,
                enabled = !isLoading
            ) {
                Text(if (connected) "打开工作区" else "连接")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color(0xFF94A3B8))
            }
        }
    }
}

@Composable
private fun RemoteWorkspace(
    config: SshConfig,
    terminalSession: com.rostrum.core.ssh.session.SshTerminalSession?,
    terminalBackend: TerminalBackend?,
    terminalBackendState: TerminalBackendState?,
    currentBackend: FileSystemBackend?,
    backendState: ActiveFileSystemState,
    remotePath: String,
    remoteServerState: RemoteServerState,
    remoteFiles: List<FileInfo>,
    isFileBrowserLoading: Boolean,
    selectedFile: FileInfo?,
    fileContent: String,
    fileStatus: String,
    isEditing: Boolean,
    onNavigate: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFile: (FileInfo) -> Unit,
    onToggleEdit: () -> Unit,
    onContentChange: (String) -> Unit,
    onSaveFile: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(config.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("${config.username}@${config.host} · $remotePath", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Text(
                    text = "Server: ${remoteServerState.phase.label()} · ${remoteServerState.message}",
                    color = remoteServerState.phase.color(),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "FS: ${backendState.displayName} · ${backendState.backendKind} · ${backendState.status}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = Color.White)
            }
        }

        Row(Modifier.weight(1f)) {
            RemoteFileTree(
                remotePath = remotePath,
                files = remoteFiles,
                isLoading = isFileBrowserLoading,
                onNavigate = onNavigate,
                onNavigateUp = onNavigateUp,
                onOpenFile = onOpenFile,
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
            )

            FilePreviewEditor(
                selectedFile = selectedFile,
                content = fileContent,
                status = fileStatus,
                isEditing = isEditing,
                canEdit = currentBackend != null && selectedFile != null,
                onToggleEdit = onToggleEdit,
                onContentChange = onContentChange,
                onSave = onSaveFile,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        TerminalDock(
            terminalSession = terminalSession,
            terminalBackend = terminalBackend,
            terminalBackendState = terminalBackendState,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        )
    }
}

@Composable
private fun RemoteFileTree(
    remotePath: String,
    files: List<FileInfo>,
    isLoading: Boolean,
    onNavigate: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenFile: (FileInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.background(Color(0xFF0B1220))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateUp, enabled = remotePath != "/") {
                Icon(Icons.Default.ArrowUpward, contentDescription = "上级", tint = Color.White)
            }
            Text(remotePath, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = { onNavigate("/") }) {
                Icon(Icons.Default.Home, contentDescription = "根目录", tint = Color.White)
            }
        }
        Divider(color = Color(0xFF1E293B))
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                items(files, key = { it.path }) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (file.isDirectory) onNavigate(file.path) else onOpenFile(file) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = if (file.isDirectory) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                            if (!file.isDirectory) Text(formatFileSize(file.size), color = Color(0xFF64748B), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilePreviewEditor(
    selectedFile: FileInfo?,
    content: String,
    status: String,
    isEditing: Boolean,
    canEdit: Boolean,
    onToggleEdit: () -> Unit,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.background(Color(0xFF111827))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(selectedFile?.name ?: "预览", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(status, color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
            if (canEdit) {
                OutlinedButton(onClick = onToggleEdit) { Text(if (isEditing) "预览" else "编辑") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSave, enabled = isEditing) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("保存")
                }
            }
        }
        Divider(color = Color(0xFF1E293B))

        if (selectedFile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("选择远程文件进行预览或编辑", color = Color(0xFF64748B))
            }
        } else if (isEditing) {
            BasicTextField(
                value = content,
                onValueChange = onContentChange,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                textStyle = TextStyle(color = Color(0xFFE5E7EB), fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                cursorBrush = SolidColor(Color(0xFF38BDF8))
            )
        } else {
            Text(
                text = content.ifBlank { "文件为空或尚未读取内容。" },
                color = Color(0xFFE5E7EB),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            )
        }
    }
}

@Composable
private fun TerminalDock(
    terminalSession: com.rostrum.core.ssh.session.SshTerminalSession?,
    terminalBackend: TerminalBackend?,
    terminalBackendState: TerminalBackendState?,
    modifier: Modifier = Modifier
) {
    Row(modifier.background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (terminalBackend != null) {
                XtermTerminalPane(
                    backend = terminalBackend,
                    title = "SSH Terminal",
                    subtitle = terminalBackend.backendId,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (terminalSession == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在初始化远程终端...", color = Color(0xFF94A3B8))
                }
            } else {
                EnhancedTerminalView(shellSession = terminalSession, modifier = Modifier.fillMaxSize())
            }
        }
        Column(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .background(Color(0xFF020617)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF38BDF8))
            Text("终端", color = Color(0xFF94A3B8), fontSize = 11.sp)
            Text(
                text = terminalBackend?.kind?.name ?: "NONE",
                color = Color(0xFF64748B),
                fontSize = 9.sp,
                maxLines = 1
            )
            Text(
                text = terminalBackendState?.label() ?: "idle",
                color = if (terminalBackendState is TerminalBackendState.Running) Color(0xFF22C55E) else Color(0xFF94A3B8),
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun terminalBackendState(backend: TerminalBackend?): TerminalBackendState? {
    if (backend == null) return null
    val state by backend.state.collectAsState()
    return state
}

private fun TerminalBackendState.label(): String {
    return when (this) {
        TerminalBackendState.Idle -> "idle"
        TerminalBackendState.Starting -> "starting"
        TerminalBackendState.Running -> "running"
        TerminalBackendState.Closing -> "closing"
        TerminalBackendState.Closed -> "closed"
        is TerminalBackendState.Failed -> "failed"
    }
}

private fun RemoteServerPhase.label(): String {
    return when (this) {
        RemoteServerPhase.DISCONNECTED -> "disconnected"
        RemoteServerPhase.CHECKING -> "checking"
        RemoteServerPhase.INSTALLING -> "installing"
        RemoteServerPhase.STARTING -> "starting"
        RemoteServerPhase.TUNNELING -> "tunneling"
        RemoteServerPhase.READY -> "ready"
        RemoteServerPhase.FALLBACK -> "fallback"
        RemoteServerPhase.ERROR -> "error"
    }
}

private fun RemoteServerPhase.color(): Color {
    return when (this) {
        RemoteServerPhase.READY -> Color(0xFF22C55E)
        RemoteServerPhase.FALLBACK -> Color(0xFFF59E0B)
        RemoteServerPhase.ERROR -> Color(0xFFF87171)
        RemoteServerPhase.DISCONNECTED -> Color(0xFF94A3B8)
        else -> Color(0xFF38BDF8)
    }
}

@Composable
internal fun ConnectionsScreen(
    hosts: List<SshConfig>,
    activeConnections: Map<String, SshConnectionState>,
    currentId: String?,
    onOpen: () -> Unit,
    onDisconnect: (SshConfig) -> Unit
) {
    val active = hosts.filter { activeConnections[it.id] is SshConnectionState.Connected }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("连接", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (active.isEmpty()) {
            EmptyState("暂无活跃连接", "连接主机后会在这里显示当前会话。", null, null)
        } else {
            active.forEach { host ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111C2E))) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = if (host.id == currentId) Color(0xFF22C55E) else Color(0xFF38BDF8))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(host.displayName, color = Color.White)
                            Text("${host.username}@${host.host}:${host.port}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                        TextButton(onClick = onOpen) { Text("打开") }
                        IconButton(onClick = { onDisconnect(host) }) {
                            Icon(Icons.Default.LinkOff, contentDescription = "断开", tint = Color(0xFFF87171))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RostrumSettingsScreen(
    showWorkspaceStatusBar: Boolean = true,
    onShowWorkspaceStatusBarChange: (Boolean) -> Unit = {},
    useXtermTerminal: Boolean = true,
    onUseXtermTerminalChange: (Boolean) -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("设置", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("远程工作台", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelLarge)

        SettingSwitchCard(
            title = "工作区状态栏",
            message = "在工作区顶部显示文件 backend、server 和终端状态。",
            checked = showWorkspaceStatusBar,
            onCheckedChange = onShowWorkspaceStatusBarChange
        )

        SettingSwitchCard(
            title = "xterm 远程终端",
            message = "远程终端使用 WebView/xterm 渲染；关闭后回退旧终端视图。",
            checked = useXtermTerminal,
            onCheckedChange = onUseXtermTerminalChange
        )

        SettingInfoCard("远端 server", "优先上传 APK 内置 rostrum-server 到远端；失败后尝试远端下载；仍失败则自动降级 SFTP。")
        SettingInfoCard("安全边界", "rostrum-server 默认限制在远端 HOME 工作区，并且只通过 SSH tunnel 访问。")
    }
}

@Composable
private fun SettingSwitchCard(
    title: String,
    message: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111C2E))) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(message, color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingInfoCard(title: String, message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111C2E))) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(message, color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyState(title: String, message: String, action: String?, onAction: (() -> Unit)?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Computer, contentDescription = null, tint = Color(0xFF334155), modifier = Modifier.size(56.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(message, color = Color(0xFF94A3B8))
            if (action != null && onAction != null) Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
internal fun AddHostDialog(
    onDismiss: () -> Unit,
    onSaveAndConnect: (SshConfig, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var shouldSave by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加主机") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("主机地址") }, singleLine = true)
                OutlinedTextField(value = port, onValueChange = { port = it.filter(Char::isDigit) }, label = { Text("端口") }, singleLine = true)
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("用户名") }, singleLine = true)
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("密码") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = shouldSave, onCheckedChange = { shouldSave = it })
                    Text("保存主机")
                }
                Text("密钥认证会在下一轮接入；当前 demo 先保证密码 SSH 主链路稳定。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                enabled = host.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                onClick = {
                    onSaveAndConnect(
                        SshConfig(
                            name = name.ifBlank { "$username@$host" },
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 22,
                            username = username.trim(),
                            authMethod = SshConfig.AuthMethod.Password(password)
                        ),
                        shouldSave
                    )
                }
            ) { Text("连接") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun formatFileSize(size: Long): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "${size / 1024} KB"
    size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
    else -> "${size / (1024 * 1024 * 1024)} GB"
}
