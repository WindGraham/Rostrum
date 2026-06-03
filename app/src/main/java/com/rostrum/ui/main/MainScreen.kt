package com.rostrum.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Tab
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rostrum.core.config.FileViewMode
import com.rostrum.core.config.GridIconSize
import com.rostrum.core.domain.model.FileItem
import com.rostrum.core.filesystem.ActiveFileSystemManager
import com.rostrum.core.filesystem.ActiveFileSystemMode
import com.rostrum.core.filesystem.ActiveFileSystemState
import com.rostrum.core.server.RemoteServerPhase
import com.rostrum.core.server.RemoteServerState
import com.rostrum.core.shell.IShellSession
import com.rostrum.core.ssh.connection.SshConnectionState
import com.rostrum.core.ssh.connection.SshConfig
import com.rostrum.core.terminal.TerminalBackend
import com.rostrum.core.terminal.TerminalBackendState
import com.rostrum.core.workspace.WorkspaceTarget
import com.rostrum.core.workspace.WorkspaceRuntimeState
import com.rostrum.ui.main.viewmodel.SshViewModel
import com.rostrum.ui.terminal.xterm.XtermTerminalPane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * 三窗口主界面。
 *
 * 默认布局保留原有工作流：左上文件管理、右上预览、下方终端。
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
    val currentFileSystem by sshViewModel.currentFileSystem.collectAsState()
    val remoteCurrentPath by sshViewModel.remoteCurrentPath.collectAsState()
    val currentTerminalSession by sshViewModel.currentTerminalSession.collectAsState()
    val currentTerminalBackend by sshViewModel.currentTerminalBackend.collectAsState()
    val currentTerminalBackendState = terminalBackendState(currentTerminalBackend)
    val backendState by ActiveFileSystemManager.backendState.collectAsState()
    val remoteServerState by sshViewModel.remoteServerState.collectAsState()
    val errorMessage = viewModel.errorMessage
    val connectedHosts = activeConnections
        .filterValues { it is SshConnectionState.Connected }
        .keys
        .mapNotNull { id -> savedConnections.find { it.id == id } ?: currentConfig?.takeIf { it.id == id } }

    val localWorkspaceId = "local"
    var localWorkspaceOpen by remember { mutableStateOf(false) }
    var selectedWorkspaceId by remember { mutableStateOf<String?>(null) }
    var topLeftContentType by remember { mutableStateOf(PaneContentType.FILE_BROWSER) }
    var topRightContentType by remember { mutableStateOf(PaneContentType.PREVIEW) }
    var bottomContentType by remember { mutableStateOf(PaneContentType.TERMINAL) }
    var selectedTab by remember { mutableStateOf(AppTab.HOSTS) }
    var showAddHost by remember { mutableStateOf(false) }
    var pendingCreateTarget by remember { mutableStateOf<Pair<PanePosition, CreateKind>?>(null) }
    val pendingExternalFile = MainActivity.pendingFilePath.value
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val uiSettings = remember(context) { context.getSharedPreferences("rostrum_ui_settings", android.content.Context.MODE_PRIVATE) }
    var showWorkspaceStatusBar by remember { mutableStateOf(uiSettings.getBoolean("show_workspace_status_bar", true)) }
    var useXtermTerminal by remember { mutableStateOf(uiSettings.getBoolean("use_xterm_terminal_v3", true)) }
    val workspaceTarget = when (val id = selectedWorkspaceId) {
        localWorkspaceId -> if (localWorkspaceOpen) WorkspaceTarget.Local else null
        null -> null
        else -> connectedHosts.firstOrNull { it.id == id }?.let { config ->
            WorkspaceTarget.Remote(
                connectionId = config.id,
                displayName = config.displayName,
                username = config.username,
                host = config.host,
                port = config.port
            )
        }
    }
    val workspaceRuntimeState = workspaceTarget?.let { target ->
        WorkspaceRuntimeState(
            target = target,
            fileSystem = backendState,
            remoteServer = if (target is WorkspaceTarget.Remote) remoteServerState else null,
            terminal = if (target is WorkspaceTarget.Remote) currentTerminalBackendState else null
        )
    }

    LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearErrorMessage()
    }

    LaunchedEffect(workspaceTarget, topLeftContentType, topRightContentType, bottomContentType) {
        if (topLeftContentType == PaneContentType.TERMINAL ||
            topRightContentType == PaneContentType.TERMINAL ||
            bottomContentType == PaneContentType.TERMINAL
        ) {
            if (workspaceTarget is WorkspaceTarget.Local) {
                viewModel.onTerminalTabSelected()
            }
        }
    }

    LaunchedEffect(pendingExternalFile) {
        val path = pendingExternalFile ?: return@LaunchedEffect
        viewModel.navigateToFileAndHighlight(path, PanePosition.TOP_LEFT)
        viewModel.openFileForViewingInPane(path, PanePosition.TOP_RIGHT)
        topLeftContentType = PaneContentType.FILE_BROWSER
        topRightContentType = PaneContentType.PREVIEW
        MainActivity.clearPendingFile()
    }

    LaunchedEffect(currentFileSystem, currentConfig?.id, remoteCurrentPath) {
        val fileSystem = currentFileSystem ?: return@LaunchedEffect
        val config = currentConfig
        viewModel.switchToSshFileSystem(
            sshFileSystem = fileSystem,
            rootPath = remoteCurrentPath,
            host = config?.host,
            user = config?.username
        )
        selectedWorkspaceId = config?.id
        selectedTab = AppTab.WORKSPACE
        topLeftContentType = PaneContentType.FILE_BROWSER
        topRightContentType = PaneContentType.PREVIEW
        bottomContentType = PaneContentType.TERMINAL
    }

    Scaffold(
        topBar = {
            if (selectedTab == AppTab.WORKSPACE && (localWorkspaceOpen || connectedHosts.isNotEmpty())) {
                WorkspaceTabBar(
                    localOpen = localWorkspaceOpen,
                    connections = connectedHosts,
                    selectedId = selectedWorkspaceId,
                    onSelectLocal = {
                        localWorkspaceOpen = true
                        selectedWorkspaceId = localWorkspaceId
                        viewModel.switchToLocalFileSystem()
                        viewModel.onTerminalTabSelected()
                        selectedTab = AppTab.WORKSPACE
                    },
                    onSelectRemote = { config ->
                        selectedWorkspaceId = config.id
                        sshViewModel.openTerminal(config.id)
                        sshViewModel.switchToSshFileSystem(config.id)
                        selectedTab = AppTab.WORKSPACE
                    },
                    onCloseLocal = {
                        localWorkspaceOpen = false
                        if (selectedWorkspaceId == localWorkspaceId) {
                            selectedWorkspaceId = connectedHosts.firstOrNull()?.id
                            if (selectedWorkspaceId == null) selectedTab = AppTab.HOSTS
                        }
                    },
                    onCloseRemote = { config ->
                        sshViewModel.disconnect(config.id)
                        if (selectedWorkspaceId == config.id) {
                            selectedWorkspaceId = if (localWorkspaceOpen) localWorkspaceId else connectedHosts.firstOrNull { it.id != config.id }?.id
                            if (selectedWorkspaceId == null) selectedTab = AppTab.HOSTS
                        }
                    }
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF101826)) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0B1120)
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            when (selectedTab) {
                AppTab.HOSTS -> HostsScreen(
                    hosts = savedConnections,
                    activeConnections = activeConnections,
                    showLocalHost = true,
                    localConnected = localWorkspaceOpen,
                    isLoading = false,
                    onConnectLocal = {
                        localWorkspaceOpen = true
                        selectedWorkspaceId = localWorkspaceId
                        viewModel.switchToLocalFileSystem()
                        viewModel.onTerminalTabSelected()
                        selectedTab = AppTab.WORKSPACE
                    },
                    onAddHost = { showAddHost = true },
                    onConnect = { config ->
                        selectedWorkspaceId = config.id
                        sshViewModel.connect(config)
                    },
                    onOpenWorkspace = { config ->
                        selectedWorkspaceId = config.id
                        sshViewModel.openTerminal(config.id)
                        sshViewModel.switchToSshFileSystem(config.id)
                        selectedTab = AppTab.WORKSPACE
                    },
                    onDelete = { config -> sshViewModel.deleteConnection(config.id) }
                )
                AppTab.WORKSPACE -> {
                    if (workspaceTarget == null) {
                        NoWorkspaceSelected(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            onOpenHosts = { selectedTab = AppTab.HOSTS }
                        )
                    } else {
                        ThreePaneWorkspace(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            viewModel = viewModel,
                            workspaceTarget = workspaceTarget,
                            runtimeState = workspaceRuntimeState,
                            showWorkspaceStatusBar = showWorkspaceStatusBar,
                            remoteTerminalSession = if (workspaceTarget is WorkspaceTarget.Remote) currentTerminalSession else null,
                            remoteTerminalBackend = if (workspaceTarget is WorkspaceTarget.Remote) currentTerminalBackend else null,
                            useXtermTerminal = useXtermTerminal,
                            onRetryRemoteServer = {
                                val target = workspaceTarget as? WorkspaceTarget.Remote ?: return@ThreePaneWorkspace
                                sshViewModel.retryRemoteServer(target.connectionId)
                            },
                            onRestartTerminal = {
                                val target = workspaceTarget as? WorkspaceTarget.Remote ?: return@ThreePaneWorkspace
                                sshViewModel.restartTerminal(target.connectionId)
                            },
                            topLeftContentType = topLeftContentType,
                            topRightContentType = topRightContentType,
                            bottomContentType = bottomContentType,
                            onTopLeftContentTypeChange = { topLeftContentType = it },
                            onTopRightContentTypeChange = { topRightContentType = it },
                            onBottomContentTypeChange = { bottomContentType = it },
                            onCreateRequested = { pane, kind -> pendingCreateTarget = pane to kind }
                        )
                    }
                }
                AppTab.CONNECTIONS -> ConnectionsScreen(
                    hosts = savedConnections,
                    activeConnections = activeConnections,
                    currentId = currentConfig?.id,
                    onOpen = {
                        currentConfig?.id?.let { selectedWorkspaceId = it }
                        selectedTab = AppTab.WORKSPACE
                    },
                    onDisconnect = { config -> sshViewModel.disconnect(config.id) }
                )
                AppTab.SETTINGS -> RostrumSettingsScreen(
                    showWorkspaceStatusBar = showWorkspaceStatusBar,
                    onShowWorkspaceStatusBarChange = { enabled ->
                        showWorkspaceStatusBar = enabled
                        uiSettings.edit().putBoolean("show_workspace_status_bar", enabled).apply()
                    },
                    useXtermTerminal = useXtermTerminal,
                    onUseXtermTerminalChange = { enabled ->
                        useXtermTerminal = enabled
                        uiSettings.edit().putBoolean("use_xterm_terminal_v3", enabled).apply()
                    }
                )
            }
        }
    }

    if (showAddHost) {
        AddHostDialog(
            onDismiss = { showAddHost = false },
            onSaveAndConnect = { config, shouldSave ->
                if (shouldSave) sshViewModel.saveConnection(config)
                selectedWorkspaceId = config.id
                sshViewModel.connect(config)
                showAddHost = false
            }
        )
    }

    pendingCreateTarget?.let { (pane, kind) ->
        CreateEntryDialog(
            kind = kind,
            onDismiss = { pendingCreateTarget = null },
            onConfirm = { name ->
                val targetPath = viewModel.getCurrentPathForPane(pane)
                val callback: (Boolean, String?) -> Unit = { success, message ->
                    scope.launch {
                        snackbarHostState.showSnackbar(message ?: if (success) "创建成功" else "创建失败")
                    }
                    if (success) viewModel.loadFileListForPane(pane)
                }
                if (kind == CreateKind.FILE) {
                    viewModel.createFile(targetPath, name, callback)
                } else {
                    viewModel.createFolder(targetPath, name, callback)
                }
                pendingCreateTarget = null
            }
        )
    }
}

@Composable
private fun ThreePaneWorkspace(
    modifier: Modifier,
    viewModel: MainViewModel,
    workspaceTarget: WorkspaceTarget,
    runtimeState: WorkspaceRuntimeState?,
    showWorkspaceStatusBar: Boolean,
    remoteTerminalSession: IShellSession?,
    remoteTerminalBackend: TerminalBackend?,
    useXtermTerminal: Boolean,
    onRetryRemoteServer: () -> Unit,
    onRestartTerminal: () -> Unit,
    topLeftContentType: PaneContentType,
    topRightContentType: PaneContentType,
    bottomContentType: PaneContentType,
    onTopLeftContentTypeChange: (PaneContentType) -> Unit,
    onTopRightContentTypeChange: (PaneContentType) -> Unit,
    onBottomContentTypeChange: (PaneContentType) -> Unit,
    onCreateRequested: (PanePosition, CreateKind) -> Unit
    ) {
    Surface(
        modifier = modifier,
        color = Color(0xFF0B1120)
    ) {
        Column(Modifier.fillMaxSize()) {
            if (showWorkspaceStatusBar && runtimeState != null) {
                WorkspaceStatusBar(
                    state = runtimeState,
                    onRetryRemoteServer = onRetryRemoteServer
                )
            }
            ThreeWaySplitter(
            modifier = Modifier.weight(1f),
            initialVerticalWeight = 0.64f,
            initialHorizontalWeight = 0.42f,
            topLeftContent = {
                MainPaneContent(
                    pane = PanePosition.TOP_LEFT,
                    contentType = topLeftContentType,
                    viewModel = viewModel,
                    workspaceTarget = workspaceTarget,
                    runtimeState = runtimeState,
                    remoteTerminalSession = remoteTerminalSession,
                    remoteTerminalBackend = remoteTerminalBackend,
                    useXtermTerminal = useXtermTerminal,
                    onRestartTerminal = onRestartTerminal,
                    onContentTypeChange = onTopLeftContentTypeChange,
                    allContentTypes = { listOf(topLeftContentType, topRightContentType, bottomContentType) },
                    setContentTypeForPane = { targetPane, type ->
                        when (targetPane) {
                            PanePosition.TOP_LEFT -> onTopLeftContentTypeChange(type)
                            PanePosition.TOP_RIGHT -> onTopRightContentTypeChange(type)
                            PanePosition.BOTTOM -> onBottomContentTypeChange(type)
                        }
                    }
                )
            },
            topRightContent = {
                MainPaneContent(
                    pane = PanePosition.TOP_RIGHT,
                    contentType = topRightContentType,
                    viewModel = viewModel,
                    workspaceTarget = workspaceTarget,
                    runtimeState = runtimeState,
                    remoteTerminalSession = remoteTerminalSession,
                    remoteTerminalBackend = remoteTerminalBackend,
                    useXtermTerminal = useXtermTerminal,
                    onRestartTerminal = onRestartTerminal,
                    onContentTypeChange = onTopRightContentTypeChange,
                    allContentTypes = { listOf(topLeftContentType, topRightContentType, bottomContentType) },
                    setContentTypeForPane = { targetPane, type ->
                        when (targetPane) {
                            PanePosition.TOP_LEFT -> onTopLeftContentTypeChange(type)
                            PanePosition.TOP_RIGHT -> onTopRightContentTypeChange(type)
                            PanePosition.BOTTOM -> onBottomContentTypeChange(type)
                        }
                    }
                )
            },
            bottomContent = {
                MainPaneContent(
                    pane = PanePosition.BOTTOM,
                    contentType = bottomContentType,
                    viewModel = viewModel,
                    workspaceTarget = workspaceTarget,
                    runtimeState = runtimeState,
                    remoteTerminalSession = remoteTerminalSession,
                    remoteTerminalBackend = remoteTerminalBackend,
                    useXtermTerminal = useXtermTerminal,
                    onRestartTerminal = onRestartTerminal,
                    onContentTypeChange = onBottomContentTypeChange,
                    allContentTypes = { listOf(topLeftContentType, topRightContentType, bottomContentType) },
                    setContentTypeForPane = { targetPane, type ->
                        when (targetPane) {
                            PanePosition.TOP_LEFT -> onTopLeftContentTypeChange(type)
                            PanePosition.TOP_RIGHT -> onTopRightContentTypeChange(type)
                            PanePosition.BOTTOM -> onBottomContentTypeChange(type)
                        }
                    }
                )
            },
            onPaneContentChange = { pane, type ->
                when (pane) {
                    PanePosition.TOP_LEFT -> onTopLeftContentTypeChange(type)
                    PanePosition.TOP_RIGHT -> onTopRightContentTypeChange(type)
                    PanePosition.BOTTOM -> onBottomContentTypeChange(type)
                }
                if (type == PaneContentType.TERMINAL) viewModel.onTerminalTabSelected()
            },
            onRefresh = { pane -> viewModel.loadFileListForPane(pane) },
            onOpenTerminal = { onBottomContentTypeChange(PaneContentType.TERMINAL) },
            onNewFile = { pane -> onCreateRequested(pane, CreateKind.FILE) },
            onNewFolder = { pane -> onCreateRequested(pane, CreateKind.FOLDER) },
            onPaste = { pane -> viewModel.pasteFilesFromClipboard(viewModel.getCurrentPathForPane(pane)) },
            onDelete = { pane -> viewModel.deleteFilesForPane(pane) { _, _ -> } },
            onCopy = { pane -> viewModel.copyFilesToClipboard(viewModel.getSelectedFileItemsForPane(pane).map { it.path }) },
            onCut = { pane -> viewModel.cutFilesToClipboard(viewModel.getSelectedFileItemsForPane(pane).map { it.path }) },
            onBookmark = { pane -> viewModel.addCurrentPathAsBookmark(pane == PanePosition.TOP_LEFT) }
            )
        }
    }
}

private enum class AppTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOSTS("主机", Icons.Default.Computer),
    WORKSPACE("工作区", Icons.Default.Folder),
    CONNECTIONS("连接", Icons.Default.Link),
    SETTINGS("设置", Icons.Default.Settings)
}

@Composable
private fun WorkspaceStatusBar(
    state: WorkspaceRuntimeState,
    onRetryRemoteServer: () -> Unit
) {
    val fileSystem = state.fileSystem
    val remoteServer = state.remoteServer
    val terminal = state.terminal
    var detailsExpanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().background(Color(0xFF08111F))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.target.title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(state.target.subtitle, fileSystem?.rootPath).joinToString(" · "),
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            fileSystem?.let {
                StatusPill(
                    text = it.mode.fileSystemLabel(),
                    color = it.mode.fileSystemColor()
                )
            }
            remoteServer?.let {
                StatusPill(
                    text = "Server ${it.phase.remoteServerLabel()}",
                    color = it.phase.remoteServerColor()
                )
            }
            terminal?.let {
                StatusPill(
                    text = "TTY ${it.terminalLabel()}",
                    color = it.terminalColor()
                )
            }
            if (remoteServer != null || fileSystem != null || terminal != null) {
                TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                    Text(if (detailsExpanded) "收起" else "详情")
                }
            }
        }

        if (detailsExpanded) {
            WorkspaceStatusDetails(
                fileSystem = fileSystem,
                remoteServer = remoteServer,
                terminal = terminal,
                onRetryRemoteServer = onRetryRemoteServer
            )
        }
        HorizontalDivider(color = Color(0xFF1E293B))
    }
}

@Composable
private fun WorkspaceStatusDetails(
    fileSystem: ActiveFileSystemState?,
    remoteServer: RemoteServerState?,
    terminal: TerminalBackendState?,
    onRetryRemoteServer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        fileSystem?.let {
            DetailLine("文件系统", "${it.mode.fileSystemLabel()} · ${it.status} · ${it.rootPath}")
        }
        remoteServer?.let {
            DetailLine("远端服务", it.message)
            it.endpoint?.let { endpoint -> DetailLine("隧道", endpoint) }
            it.fallback?.let { fallback -> DetailLine("兜底", fallback.name) }
            it.error?.let { error -> DetailLine("错误", error) }
            if (it.phase == RemoteServerPhase.FALLBACK || it.phase == RemoteServerPhase.ERROR) {
                TextButton(onClick = onRetryRemoteServer) { Text("重试 server") }
            }
        }
        terminal?.let {
            val message = if (it is TerminalBackendState.Failed) it.message else it.terminalLabel()
            DetailLine("终端", message)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
        Text(
            value,
            color = Color(0xFFE5E7EB),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun ActiveFileSystemMode.fileSystemLabel(): String {
    return when (this) {
        ActiveFileSystemMode.LOCAL -> "Local"
        ActiveFileSystemMode.SSH_SFTP -> "SFTP fallback"
        ActiveFileSystemMode.REMOTE_SERVER -> "Server FS"
    }
}

private fun ActiveFileSystemMode.fileSystemColor(): Color {
    return when (this) {
        ActiveFileSystemMode.LOCAL -> Color(0xFF94A3B8)
        ActiveFileSystemMode.SSH_SFTP -> Color(0xFFF59E0B)
        ActiveFileSystemMode.REMOTE_SERVER -> Color(0xFF22C55E)
    }
}

private fun RemoteServerPhase.remoteServerLabel(): String {
    return when (this) {
        RemoteServerPhase.DISCONNECTED -> "idle"
        RemoteServerPhase.CHECKING -> "checking"
        RemoteServerPhase.INSTALLING -> "installing"
        RemoteServerPhase.STARTING -> "starting"
        RemoteServerPhase.TUNNELING -> "tunnel"
        RemoteServerPhase.READY -> "ready"
        RemoteServerPhase.FALLBACK -> "fallback"
        RemoteServerPhase.ERROR -> "error"
    }
}

private fun RemoteServerPhase.remoteServerColor(): Color {
    return when (this) {
        RemoteServerPhase.READY -> Color(0xFF22C55E)
        RemoteServerPhase.FALLBACK -> Color(0xFFF59E0B)
        RemoteServerPhase.ERROR -> Color(0xFFF87171)
        RemoteServerPhase.DISCONNECTED -> Color(0xFF94A3B8)
        else -> Color(0xFF38BDF8)
    }
}

private fun TerminalBackendState.terminalLabel(): String {
    return when (this) {
        TerminalBackendState.Idle -> "idle"
        TerminalBackendState.Starting -> "starting"
        TerminalBackendState.Running -> "running"
        TerminalBackendState.Closing -> "closing"
        TerminalBackendState.Closed -> "closed"
        is TerminalBackendState.Failed -> "failed"
    }
}

private fun TerminalBackendState.terminalColor(): Color {
    return when (this) {
        TerminalBackendState.Running -> Color(0xFF22C55E)
        is TerminalBackendState.Failed -> Color(0xFFF87171)
        TerminalBackendState.Starting -> Color(0xFF38BDF8)
        else -> Color(0xFF94A3B8)
    }
}

@Composable
private fun WorkspaceTabBar(
    localOpen: Boolean,
    connections: List<SshConfig>,
    selectedId: String?,
    onSelectLocal: () -> Unit,
    onSelectRemote: (SshConfig) -> Unit,
    onCloseLocal: () -> Unit,
    onCloseRemote: (SshConfig) -> Unit
) {
    val tabs = buildList {
        if (localOpen) add("local")
        addAll(connections.map { it.id })
    }
    ScrollableTabRow(
        selectedTabIndex = tabs.indexOf(selectedId).coerceAtLeast(0),
        containerColor = Color(0xFF0B1220),
        edgePadding = 8.dp
    ) {
        if (localOpen) {
            WorkspaceTab(
                title = "本机",
                subtitle = "Local",
                selected = selectedId == "local",
                onClick = onSelectLocal,
                onClose = onCloseLocal
            )
        }
        connections.forEach { config ->
            WorkspaceTab(
                title = config.displayName,
                subtitle = config.host,
                selected = selectedId == config.id,
                onClick = { onSelectRemote(config) },
                onClose = { onCloseRemote(config) }
            )
        }
    }
}

@Composable
private fun WorkspaceTab(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(116.dp)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                    Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭工作区", modifier = Modifier.size(14.dp), tint = Color(0xFF94A3B8))
                }
            }
        }
    )
}

@Composable
private fun NoWorkspaceSelected(
    modifier: Modifier,
    onOpenHosts: () -> Unit
) {
    Box(
        modifier = modifier.background(Color(0xFF0B1120)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Computer, contentDescription = null, tint = Color(0xFF334155), modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text("还没有打开工作区", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("在主机页连接“本机”或 SSH 主机后，文件、预览和终端会绑定到该目标。", color = Color(0xFF94A3B8))
            Spacer(Modifier.height(18.dp))
            Button(onClick = onOpenHosts) { Text("前往主机") }
        }
    }
}

@Composable
private fun BoxScope.MainPaneContent(
    pane: PanePosition,
    contentType: PaneContentType,
    viewModel: MainViewModel,
    workspaceTarget: WorkspaceTarget,
    runtimeState: WorkspaceRuntimeState?,
    remoteTerminalSession: IShellSession?,
    remoteTerminalBackend: TerminalBackend?,
    useXtermTerminal: Boolean,
    onRestartTerminal: () -> Unit,
    onContentTypeChange: (PaneContentType) -> Unit,
    allContentTypes: () -> List<PaneContentType>,
    setContentTypeForPane: (PanePosition, PaneContentType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        PaneTitleBar(
            pane = pane,
            contentType = contentType,
            onClose = {
                onContentTypeChange(PaneContentType.EMPTY)
                if (contentType == PaneContentType.PREVIEW || contentType == PaneContentType.EDITOR) {
                    viewModel.clearViewingFileForPane(pane)
                }
            }
        )

        Box(Modifier.weight(1f)) {
            when (contentType) {
                PaneContentType.EMPTY -> EmptyPane(pane)
                PaneContentType.FILE_BROWSER -> FileBrowserPaneContent(
                    pane = pane,
                    viewModel = viewModel,
                    runtimeState = runtimeState,
                    allContentTypes = allContentTypes,
                    setContentTypeForPane = setContentTypeForPane
                )
                PaneContentType.PREVIEW -> FilePreviewPanel(
                    filePath = viewModel.getViewingFileForPane(pane) ?: viewModel.currentViewingFile,
                    panePosition = pane,
                    onSaveFile = { path, content ->
                        viewModel.saveFileContent(path, content) { _, _ -> }
                    },
                    onClose = { viewModel.clearViewingFileForPane(pane) },
                    onOpenInEditor = { path, targetPane ->
                        viewModel.openFileForViewingInPane(path, targetPane)
                        setContentTypeForPane(targetPane, PaneContentType.EDITOR)
                    },
                    modifier = Modifier.fillMaxSize()
                )
                PaneContentType.EDITOR -> FileEditor(
                    filePath = viewModel.getViewingFileForPane(pane) ?: viewModel.currentViewingFile,
                    onSave = { path, content ->
                        viewModel.saveFileContent(path, content) { _, _ -> }
                    },
                    onClose = {
                        viewModel.clearViewingFileForPane(pane)
                        onContentTypeChange(PaneContentType.PREVIEW)
                    },
                    modifier = Modifier.fillMaxSize()
                )
                PaneContentType.TERMINAL -> {
                    if (workspaceTarget is WorkspaceTarget.Remote) {
                        Column(Modifier.fillMaxSize()) {
                            TerminalControlBar(
                                backend = remoteTerminalBackend,
                                state = runtimeState?.terminal,
                                usingXterm = remoteTerminalBackend != null && useXtermTerminal,
                                onRestartTerminal = onRestartTerminal
                            )
                            if (remoteTerminalBackend != null && useXtermTerminal) {
                                XtermTerminalPane(
                                    backend = remoteTerminalBackend,
                                    title = workspaceTarget.title,
                                    subtitle = workspaceTarget.subtitle,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .heightIn(min = 220.dp)
                                )
                            } else if (remoteTerminalSession == null) {
                                RemoteTerminalPendingPane(workspaceTarget)
                            } else {
                                TerminalView(
                                    shellSession = viewModel.shellSession,
                                    shellMode = viewModel.shellMode,
                                    directSession = remoteTerminalSession,
                                    title = workspaceTarget.title,
                                    subtitle = workspaceTarget.subtitle,
                                    onSwitchShellMode = viewModel::switchShellMode,
                                    onExecutePythonScript = { script, args -> viewModel.executePythonScript(script, args) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        TerminalView(
                            shellSession = viewModel.shellSession,
                            shellMode = viewModel.shellMode,
                            directSession = remoteTerminalSession,
                            title = workspaceTarget.title,
                            subtitle = workspaceTarget.subtitle,
                            onSwitchShellMode = viewModel::switchShellMode,
                            onExecutePythonScript = { script, args -> viewModel.executePythonScript(script, args) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                PaneContentType.REMOTE -> RostrumMainScreen()
                PaneContentType.BUILD -> BuildPanel(
                    currentPath = viewModel.getCurrentPathForPane(pane),
                    viewModel = viewModel,
                    panePosition = pane,
                    snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                    scope = androidx.compose.runtime.rememberCoroutineScope(),
                    onSwitchPaneType = { targetPane, type -> setContentTypeForPane(targetPane, type) },
                    modifier = Modifier.fillMaxSize()
                )
                PaneContentType.HEX_VIEW -> HexEditor(
                    filePath = viewModel.getViewingFileForPane(pane) ?: viewModel.currentViewingFile,
                    onClose = { onContentTypeChange(PaneContentType.PREVIEW) },
                    modifier = Modifier.fillMaxSize()
                )
                PaneContentType.BOOKMARKS -> BookmarksSheetContent(viewModel)
                PaneContentType.PROPERTIES -> PropertiesPane(viewModel.getViewingFileForPane(pane) ?: viewModel.currentViewingFile)
                PaneContentType.MIGRATE,
                PaneContentType.HISTORY,
                PaneContentType.PLUGINS,
                PaneContentType.QUICK_ACTIONS -> PlaceholderPane(contentType)
            }
        }
    }
}

@Composable
private fun TerminalControlBar(
    backend: TerminalBackend?,
    state: TerminalBackendState?,
    usingXterm: Boolean,
    onRestartTerminal: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Color(0xFF0B1220))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (usingXterm) "xterm" else "legacy terminal",
            color = Color(0xFFE5E7EB),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = backend?.kind?.name ?: "SSH",
            color = Color(0xFF94A3B8),
            style = MaterialTheme.typography.labelSmall
        )
        state?.let { StatusPill(text = it.terminalLabel(), color = it.terminalColor()) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onRestartTerminal) { Text("重启终端") }
    }
}

private enum class CreateKind(val title: String, val label: String) {
    FILE("新建文件", "文件名"),
    FOLDER("新建文件夹", "文件夹名")
}

@Composable
private fun CreateEntryDialog(
    kind: CreateKind,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(kind.title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(kind.label) },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim()) }
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun PaneTitleBar(
    pane: PanePosition,
    contentType: PaneContentType,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(Color(0xFF111827))
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(contentType.icon, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
        Text(
            text = "${pane.label} · ${contentType.label}",
            color = Color(0xFFE5E7EB),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
        }
    }
    HorizontalDivider(color = Color(0xFF1F2937))
}

@Composable
private fun FileBrowserPaneContent(
    pane: PanePosition,
    viewModel: MainViewModel,
    runtimeState: WorkspaceRuntimeState?,
    allContentTypes: () -> List<PaneContentType>,
    setContentTypeForPane: (PanePosition, PaneContentType) -> Unit
) {
    CompactFileBrowserPane(
        currentPath = viewModel.getCurrentPathForPane(pane),
        fileList = fileListForPane(viewModel, pane),
        selectedFiles = selectedFilesForPane(viewModel, pane),
        isLeft = pane == PanePosition.TOP_LEFT,
        panePosition = pane,
        onFileClick = { file ->
            if (file.isDirectory) {
                viewModel.navigateToPane(file.path, pane)
            } else {
                val targetPane = choosePreviewPane(pane, allContentTypes())
                viewModel.handleFilePreviewWithBinding(file.path, pane, listOf(targetPane))
                setContentTypeForPane(targetPane, PaneContentType.PREVIEW)
            }
        },
        onFileLongClick = { file -> viewModel.toggleFileSelectionForPane(file.path, pane) },
        onFileMenuClick = { file -> viewModel.toggleFileSelectionForPane(file.path, pane) },
        onNavigateUp = { viewModel.navigateUpForPane(pane) },
        onOpenDrawer = {},
        canNavigateUp = viewModel.getCurrentPathForPane(pane) != viewModel.rootPath,
        viewModel = viewModel,
        backendState = runtimeState?.fileSystem,
        viewMode = FileViewMode.LIST,
        gridIconSize = GridIconSize.MEDIUM,
        scrollToIndex = if (viewModel.pendingScrollPane == pane) viewModel.pendingScrollToIndex else -1,
        onScrollComplete = viewModel::clearPendingScroll,
        highlightedFile = viewModel.pendingHighlightFile,
        highlightVersion = viewModel.highlightVersion,
        onHighlightConsumed = viewModel::clearHighlight,
        modifier = Modifier.fillMaxSize()
    )
}

private fun choosePreviewPane(sourcePane: PanePosition, types: List<PaneContentType>): PanePosition {
    val panes = listOf(PanePosition.TOP_LEFT, PanePosition.TOP_RIGHT, PanePosition.BOTTOM)
    val previewPane = panes.firstOrNull { pane ->
        pane != sourcePane && types.getOrNull(panes.indexOf(pane)) == PaneContentType.PREVIEW
    }
    return previewPane ?: panes.first { it != sourcePane }
}

private fun fileListForPane(viewModel: MainViewModel, pane: PanePosition): List<FileItem> = when (pane) {
    PanePosition.TOP_LEFT -> viewModel.leftFileList
    PanePosition.TOP_RIGHT -> viewModel.rightFileList
    PanePosition.BOTTOM -> viewModel.bottomFileList
}

private fun selectedFilesForPane(viewModel: MainViewModel, pane: PanePosition): Set<String> = when (pane) {
    PanePosition.TOP_LEFT -> viewModel.leftSelectedFiles
    PanePosition.TOP_RIGHT -> viewModel.rightSelectedFiles
    PanePosition.BOTTOM -> viewModel.bottomSelectedFiles
}

@Composable
private fun terminalBackendState(backend: TerminalBackend?): TerminalBackendState? {
    if (backend == null) return null
    val state by backend.state.collectAsState()
    return state
}

@Composable
private fun EmptyPane(pane: PanePosition) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF334155), modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(8.dp))
            Text("${pane.label} 窗格为空", color = Color(0xFF94A3B8))
            Text("拖动分割点圆盘可切换内容类型", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RemoteTerminalPendingPane(target: WorkspaceTarget.Remote) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF060B12)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFF38BDF8), strokeWidth = 2.dp)
            Spacer(Modifier.height(12.dp))
            Text("正在打开远程终端", color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(target.subtitle, color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlaceholderPane(type: PaneContentType) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(type.icon, contentDescription = null, tint = Color(0xFF334155), modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(8.dp))
            Text("${type.label} 功能暂未接入当前窗格", color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun PropertiesPane(filePath: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(filePath ?: "尚未选择文件", color = Color(0xFF94A3B8))
    }
}

@Composable
private fun BookmarksSheetContent(viewModel: MainViewModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("书签", color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("当前共有 ${viewModel.bookmarks.size} 个书签", color = Color(0xFF94A3B8))
        }
    }
}
