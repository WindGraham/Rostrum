package com.termux.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termux.app.TerminalTab
import com.termux.app.data.ActiveFileSystemManager
import com.termux.app.data.FileItem
import com.termux.app.ssh.SshConfig
import com.termux.app.ssh.SshConnectionState
import com.termux.app.ssh.SshFileSystem
import com.termux.app.ssh.SshViewModel
import kotlinx.coroutines.launch

private val Indigo = Color(0xFF6366F1)
private val DarkBg = Color(0xFF0B1120)
private val PanelBg = Color(0xFF0F172A)
private val SurfaceBg = Color(0xFF1E293B)
private val BorderColor = Color(0xFF1F2937)
private val MutedText = Color(0xFF94A3B8)
private val BrightText = Color(0xFFE5E7EB)

@Composable
fun ThreePaneWorkspace(
    currentPath: String,
    fileList: List<FileItem>,
    isLoading: Boolean,
    selectedFile: String?,
    previewContent: String?,
    previewImageBytes: ByteArray?,
    isPreviewImage: Boolean,
    terminalTabs: List<TerminalTab>,
    activeTerminalTabId: String?,
    onTerminalTabSelect: (String) -> Unit,
    onCreateNewTerminalTab: (String) -> Unit,
    onCloseTerminalTab: (String) -> Unit,
    topLeftContentType: PaneContentType,
    topRightContentType: PaneContentType,
    bottomContentType: PaneContentType,
    pendingSshCommand: String?,
    onTopLeftContentTypeChange: (PaneContentType) -> Unit,
    onTopRightContentTypeChange: (PaneContentType) -> Unit,
    onBottomContentTypeChange: (PaneContentType) -> Unit,
    onNavigateUp: () -> Unit,
    onFileClickFromPane: (FileItem, PanePosition) -> Unit,
    onRefresh: () -> Unit,
    onPreviewClose: () -> Unit,
    selectedFiles: Set<String>,
    isSelectionMode: Boolean,
    onFileLongClick: (FileItem) -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelection: () -> Unit,
    onRenameRequest: (String) -> Unit,
    isRemoteFile: Boolean = false,
    remoteHost: String? = null,
    remoteUser: String? = null,
    isBinaryFile: Boolean = false,
    pdfPreviewBitmap: Bitmap? = null,
    onCopyContent: (() -> Unit)? = null,
    onOpenExternally: (() -> Unit)? = null
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B1120)) {
        ThreeWaySplitter(
            modifier = Modifier.fillMaxSize(),
            initialVerticalWeight = 0.55f,
            initialHorizontalWeight = 0.50f,
            topLeftContent = {
                MainPaneContent(
                    pane = PanePosition.TOP_LEFT,
                    contentType = topLeftContentType,
                    currentPath = currentPath,
                    fileList = fileList,
                    isLoading = isLoading,
                    selectedFile = selectedFile,
                    previewContent = previewContent,
                    previewImageBytes = previewImageBytes,
                    isPreviewImage = isPreviewImage,
                    terminalTabs = terminalTabs,
                    activeTerminalTabId = activeTerminalTabId,
                    onTerminalTabSelect = onTerminalTabSelect,
                    onCreateNewTerminalTab = onCreateNewTerminalTab,
                    onCloseTerminalTab = onCloseTerminalTab,
                    pendingSshCommand = pendingSshCommand,
                    onContentTypeChange = onTopLeftContentTypeChange,
                    onNavigateUp = onNavigateUp,
                    onFileClickFromPane = onFileClickFromPane,
                    onRefresh = onRefresh,
                    onPreviewClose = onPreviewClose,
                    selectedFiles = selectedFiles,
                    isSelectionMode = isSelectionMode,
                    onFileLongClick = onFileLongClick,
                    onNewFile = onNewFile,
                    onNewFolder = onNewFolder,
                    onSelectAll = onSelectAll,
                    onDeleteSelection = onDeleteSelection,
                    onRenameRequest = onRenameRequest,
                    isRemoteFile = isRemoteFile,
                    remoteHost = remoteHost,
                    remoteUser = remoteUser,
                    isBinaryFile = isBinaryFile,
                    pdfPreviewBitmap = pdfPreviewBitmap,
                    onCopyContent = onCopyContent,
                    onOpenExternally = onOpenExternally
                )
            },
            topRightContent = {
                MainPaneContent(
                    pane = PanePosition.TOP_RIGHT,
                    contentType = topRightContentType,
                    currentPath = currentPath,
                    fileList = fileList,
                    isLoading = isLoading,
                    selectedFile = selectedFile,
                    previewContent = previewContent,
                    previewImageBytes = previewImageBytes,
                    isPreviewImage = isPreviewImage,
                    terminalTabs = terminalTabs,
                    activeTerminalTabId = activeTerminalTabId,
                    onTerminalTabSelect = onTerminalTabSelect,
                    onCreateNewTerminalTab = onCreateNewTerminalTab,
                    onCloseTerminalTab = onCloseTerminalTab,
                    pendingSshCommand = pendingSshCommand,
                    onContentTypeChange = onTopRightContentTypeChange,
                    onNavigateUp = onNavigateUp,
                    onFileClickFromPane = onFileClickFromPane,
                    onRefresh = onRefresh,
                    onPreviewClose = onPreviewClose,
                    selectedFiles = selectedFiles,
                    isSelectionMode = isSelectionMode,
                    onFileLongClick = onFileLongClick,
                    onNewFile = onNewFile,
                    onNewFolder = onNewFolder,
                    onSelectAll = onSelectAll,
                    onDeleteSelection = onDeleteSelection,
                    onRenameRequest = onRenameRequest,
                    isRemoteFile = isRemoteFile,
                    remoteHost = remoteHost,
                    remoteUser = remoteUser,
                    isBinaryFile = isBinaryFile,
                    pdfPreviewBitmap = pdfPreviewBitmap,
                    onCopyContent = onCopyContent,
                    onOpenExternally = onOpenExternally
                )
            },
            bottomContent = {
                MainPaneContent(
                    pane = PanePosition.BOTTOM,
                    contentType = bottomContentType,
                    currentPath = currentPath,
                    fileList = fileList,
                    isLoading = isLoading,
                    selectedFile = selectedFile,
                    previewContent = previewContent,
                    previewImageBytes = previewImageBytes,
                    isPreviewImage = isPreviewImage,
                    terminalTabs = terminalTabs,
                    activeTerminalTabId = activeTerminalTabId,
                    onTerminalTabSelect = onTerminalTabSelect,
                    onCreateNewTerminalTab = onCreateNewTerminalTab,
                    onCloseTerminalTab = onCloseTerminalTab,
                    pendingSshCommand = pendingSshCommand,
                    onContentTypeChange = onBottomContentTypeChange,
                    onNavigateUp = onNavigateUp,
                    onFileClickFromPane = onFileClickFromPane,
                    onRefresh = onRefresh,
                    onPreviewClose = onPreviewClose,
                    selectedFiles = selectedFiles,
                    isSelectionMode = isSelectionMode,
                    onFileLongClick = onFileLongClick,
                    onNewFile = onNewFile,
                    onNewFolder = onNewFolder,
                    onSelectAll = onSelectAll,
                    onDeleteSelection = onDeleteSelection,
                    onRenameRequest = onRenameRequest,
                    isRemoteFile = isRemoteFile,
                    remoteHost = remoteHost,
                    remoteUser = remoteUser,
                    isBinaryFile = isBinaryFile,
                    pdfPreviewBitmap = pdfPreviewBitmap,
                    onCopyContent = onCopyContent,
                    onOpenExternally = onOpenExternally
                )
            },
            onPaneContentChange = { pane, type ->
                when (pane) {
                    PanePosition.TOP_LEFT -> onTopLeftContentTypeChange(type)
                    PanePosition.TOP_RIGHT -> onTopRightContentTypeChange(type)
                    PanePosition.BOTTOM -> onBottomContentTypeChange(type)
                }
            }
        )
    }
}

@Composable
fun MainPaneContent(
    pane: PanePosition,
    contentType: PaneContentType,
    currentPath: String,
    fileList: List<FileItem>,
    isLoading: Boolean,
    selectedFile: String?,
    previewContent: String?,
    previewImageBytes: ByteArray?,
    isPreviewImage: Boolean,
    terminalTabs: List<TerminalTab>,
    activeTerminalTabId: String?,
    onTerminalTabSelect: (String) -> Unit,
    onCreateNewTerminalTab: (String) -> Unit,
    onCloseTerminalTab: (String) -> Unit,
    pendingSshCommand: String? = null,
    onContentTypeChange: (PaneContentType) -> Unit,
    onNavigateUp: () -> Unit,
    onFileClickFromPane: (FileItem, PanePosition) -> Unit,
    onRefresh: () -> Unit,
    onPreviewClose: () -> Unit,
    selectedFiles: Set<String>,
    isSelectionMode: Boolean,
    onFileLongClick: (FileItem) -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelection: () -> Unit,
    onRenameRequest: (String) -> Unit,
    isRemoteFile: Boolean = false,
    remoteHost: String? = null,
    remoteUser: String? = null,
    isBinaryFile: Boolean = false,
    pdfPreviewBitmap: Bitmap? = null,
    onCopyContent: (() -> Unit)? = null,
    onOpenExternally: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        PaneTitleBar(
            pane = pane,
            contentType = contentType,
            onClose = { onContentTypeChange(PaneContentType.EMPTY) },
            hasSelection = selectedFiles.isNotEmpty(),
            onNewFile = onNewFile,
            onNewFolder = onNewFolder,
            onSelectAll = onSelectAll,
            onDelete = onDeleteSelection
        )
        HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
        Box(Modifier.weight(1f).border(0.5.dp, BorderColor)) {
            when (contentType) {
                PaneContentType.EMPTY -> EmptyPane(pane)
                PaneContentType.FILE_BROWSER -> FileBrowserPane(
                    currentPath = currentPath,
                    fileList = fileList,
                    isLoading = isLoading,
                    onNavigateUp = onNavigateUp,
                    onFileClick = { file -> onFileClickFromPane(file, pane) },
                    onRefresh = onRefresh,
                    selectedFiles = selectedFiles,
                    isSelectionMode = isSelectionMode,
                    onFileLongClick = onFileLongClick
                )
                PaneContentType.PREVIEW -> FilePreviewPane(
                    filePath = selectedFile,
                    fileContent = previewContent,
                    imageBytes = previewImageBytes,
                    isImage = isPreviewImage,
                    onClose = onPreviewClose,
                    isRemote = isRemoteFile,
                    remoteHost = remoteHost,
                    remoteUser = remoteUser,
                    isBinary = isBinaryFile,
                    pdfPreviewBitmap = pdfPreviewBitmap,
                    onCopyContent = onCopyContent,
                    onOpenExternally = onOpenExternally
                )
                PaneContentType.TERMINAL -> {
                    val activeSession = terminalTabs.find { it.id == activeTerminalTabId }?.session
                    Column(Modifier.fillMaxSize()) {
                        if (terminalTabs.isNotEmpty()) {
                            TerminalTabBar(
                                tabs = terminalTabs,
                                activeTabId = activeTerminalTabId,
                                onSelect = { onTerminalTabSelect(it) },
                                onAdd = { onCreateNewTerminalTab("终端 ${terminalTabs.size + 1}") },
                                onClose = { onCloseTerminalTab(it) }
                            )
                            HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                        }
                        if (activeSession != null) {
                            key(activeTerminalTabId) {
                                val activity = androidx.compose.ui.platform.LocalContext.current as? com.termux.app.TermuxWorkspaceActivity
                                val existingView = activity?.terminalViews?.get(activeTerminalTabId)
                                TerminalPane(session = activeSession, pendingCommand = pendingSshCommand,
                                    existingView = existingView,
                                    onCommandWritten = { activity?.pendingSshCommand = null },
                                    modifier = Modifier.fillMaxSize())
                            }
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("没有终端会话", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                else -> PlaceholderPane(contentType)
            }
        }
    }
}

@Composable
fun TerminalTabBar(
    tabs: List<TerminalTab>,
    activeTabId: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onClose: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(DarkBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val isActive = tab.id == activeTabId
            val isSsh = tab.label.contains("@")
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .widthIn(max = 160.dp)
                    .background(if (isActive) Color(0xFF1E293B) else Color.Transparent)
                    .drawBehind {
                        if (isActive) {
                            drawLine(
                                color = Indigo,
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                    .clickable { onSelect(tab.id) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = tab.label,
                        color = if (isSsh) Color(0xFF22C55E) else if (isActive) Color.White else Color(0xFF94A3B8),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(2.dp))
                    IconButton(
                        onClick = { onClose(tab.id) },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onAdd,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "新建终端",
                tint = Indigo,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun PaneTitleBar(
    pane: PanePosition,
    contentType: PaneContentType,
    onClose: () -> Unit,
    hasSelection: Boolean = false,
    onNewFile: (() -> Unit)? = null,
    onNewFolder: (() -> Unit)? = null,
    onSelectAll: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val isFileBrowser = contentType == PaneContentType.FILE_BROWSER

    Row(
        modifier = Modifier.fillMaxWidth().height(34.dp).background(Color(0xFF111827)).drawBehind { drawLine(Color(0xFF1F2937), Offset(0f, size.height), Offset(size.width, size.height), 1f) }.padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(contentType.icon, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
        Text(
            text = "${pane.label} · ${contentType.label}",
            color = MutedText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )

        if (isFileBrowser) {
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "更多操作",
                        tint = MutedText,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.NoteAdd, null, tint = MutedText, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("新建文件")
                            }
                        },
                        onClick = { showMenu = false; onNewFile?.invoke() }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CreateNewFolder, null, tint = MutedText, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("新建文件夹")
                            }
                        },
                        onClick = { showMenu = false; onNewFolder?.invoke() }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SelectAll, null, tint = MutedText, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("全选")
                            }
                        },
                        onClick = { showMenu = false; onSelectAll?.invoke() }
                    )
                    if (hasSelection) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("删除选中", color = Color(0xFFEF4444))
                                }
                            },
                            onClick = { showMenu = false; onDelete?.invoke() }
                        )
                    }
                }
            }
        }

        if (contentType == PaneContentType.PREVIEW) {
            val boundFilePane = PaneBindingManager.getBoundFilePane(pane)
            if (boundFilePane != null) {
                Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
                Text(boundFilePane.label, color = Color(0xFF38BDF8), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(4.dp))
            }
        }

        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "关闭", tint = MutedText, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun EmptyPane(pane: PanePosition) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF334155), modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(8.dp))
            Text("${pane.label} 窗格为空", color = MutedText, modifier = Modifier.alpha(0.4f))
            Spacer(Modifier.height(4.dp))
            Text("拖动分割点圆盘可切换内容类型", color = MutedText, modifier = Modifier.alpha(0.4f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PlaceholderPane(type: PaneContentType) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(type.icon, contentDescription = null, tint = Color(0xFF334155), modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(8.dp))
            Text("${type.label} 功能暂未接入", color = MutedText)
        }
    }
}

@Composable
fun NoWorkspaceSelected(onOpenHosts: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )
    Box(modifier = Modifier.fillMaxSize().background(DarkBg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Computer, contentDescription = null, tint = Color(0xFF334155).copy(alpha = pulseAlpha), modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(12.dp))
            Text("还没有打开工作区", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("在主机页连接本机或 SSH 主机后，文件、预览和终端会绑定到该目标", color = MutedText)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onOpenHosts) { Text("前往主机") }
        }
    }
}

@Composable
fun WorkspaceTabBar(
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
            WorkspaceTab("本机", "Local", selectedId == "local", onSelectLocal, onCloseLocal)
        }
        connections.forEach { connection ->
            WorkspaceTab(connection.displayName, connection.host, selectedId == connection.id, { onSelectRemote(connection) }, { onCloseRemote(connection) })
        }
    }
}

@Composable
fun WorkspaceTab(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(116.dp)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                    Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MutedText, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(14.dp), tint = MutedText)
                }
            }
        }
    )
}

@Composable
fun DirectoryPickerDialog(
    config: SshConfig,
    sshViewModel: SshViewModel,
    onDismiss: () -> Unit,
    onSelectDirectory: (String) -> Unit
) {
    var currentDir by remember { mutableStateOf("/") }
    var dirListing by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoadingDir by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val connectionStates by sshViewModel.connectionStates.collectAsState()
    val state = connectionStates[config.id]

    LaunchedEffect(state, currentDir) {
        if (state is SshConnectionState.Connected) {
            isLoadingDir = true
            error = null
            val conn = sshViewModel.getConnection(config.id)
            if (conn != null) {
                val result = SshFileSystem(conn).listDirectory(currentDir)
                result.fold(
                    onSuccess = { dirListing = it.filter { f -> f.isDirectory } },
                    onFailure = { error = it.message }
                )
            }
            isLoadingDir = false
        } else if (state is SshConnectionState.Error) {
            isLoadingDir = false
            error = (state as SshConnectionState.Error).message
        }
    }

    val breadcrumbs = buildList {
        add("/" to "/")
        val parts = currentDir.split("/").filter { it.isNotEmpty() }
        var path = ""
        for (part in parts) {
            path = "$path/$part"
            add(part to path)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择远程目录", color = BrightText) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Start
                ) {
                    breadcrumbs.forEachIndexed { index, (name, path) ->
                        Text(
                            text = name,
                            color = if (index == breadcrumbs.lastIndex) Indigo else MutedText,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable { currentDir = path }.padding(2.dp)
                        )
                        if (index < breadcrumbs.lastIndex) {
                            Text(" / ", color = Color(0xFF475569), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = BorderColor)
                Spacer(Modifier.height(8.dp))

                if (isLoadingDir) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Indigo)
                    }
                } else if (error != null) {
                    Text("加载失败: $error", color = Color(0xFFEF4444))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        if (currentDir != "/") {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        val parent = java.io.File(currentDir).parent ?: "/"
                                        currentDir = parent
                                    }.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, null, tint = MutedText, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("..", color = MutedText)
                                }
                            }
                        }
                        items(dirListing) { dir ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { currentDir = dir.path }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, null, tint = Indigo, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(dir.name, color = BrightText)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelectDirectory(currentDir) },
                colors = ButtonDefaults.buttonColors(containerColor = Indigo)
            ) {
                Text("选择此目录")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MutedText)
            }
        },
        containerColor = Color(0xFF0F172A),
        shape = MaterialTheme.shapes.medium
    )
}
