package com.termux.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.termux.app.data.ActiveFileSystemManager
import com.termux.app.data.FileItem
import com.termux.app.settings.SettingsStore
import com.termux.app.ssh.SshConfig
import com.termux.app.ssh.SshConnectionState
import com.termux.app.ssh.SshFileSystem
import com.termux.app.ssh.SshKeyManager
import com.termux.app.ssh.SshViewModel
import com.termux.app.ui.AddHostDialog
import com.termux.app.ui.ConnectionsScreen
import com.termux.app.ui.DirectoryPickerDialog
import com.termux.app.ui.HostsScreen
import com.termux.app.ui.NoWorkspaceSelected
import com.termux.app.ui.PaneBindingManager
import com.termux.app.ui.PaneContentType
import com.termux.app.ui.PanePosition
import com.termux.app.ui.SelectionActionBar
import com.termux.app.ui.SettingsScreen
import com.termux.app.ui.ThreePaneWorkspace
import com.termux.app.ui.WorkspaceTabBar
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

private const val TAG = "TermuxWorkspace"

private val NavBarBg = Color(0xFF101826)
private val Indigo = Color(0xFF6366F1)
private val MutedText = Color(0xFF64748B)
private val BrightText = Color(0xFFE5E7EB)

data class TerminalTab(
    val id: String,
    val label: String,
    val session: TerminalSession
)

class TermuxWorkspaceActivity : AppCompatActivity(), ServiceConnection {

    private var termuxService: TermuxService? = null
    private var isBound = false
    var terminalTabs by mutableStateOf<List<TerminalTab>>(emptyList())
    var activeTerminalTabId by mutableStateOf<String?>(null)
    var pendingSshCommand by mutableStateOf<String?>(null)
    
    /** Cache TerminalView instances to survive Compose recomposition across tab switches */
    val terminalViews = mutableMapOf<String, TerminalView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.termux.app.settings.SettingsStore.init(this)
        bindService()

        val composeView = ComposeView(this).apply {
            setContent {
                MainScreen()
            }
        }
        setContentView(composeView)
    }

    private fun bindService() {
        val intent = Intent(this, TermuxService::class.java)
        startService(intent)
        bindService(intent, this, Context.BIND_AUTO_CREATE)
        isBound = true
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        termuxService = (service as TermuxService.LocalBinder).service
        createNewTerminalTab("本地")
    }

    fun createNewTerminalTab(label: String, initialCommand: String? = null) {
        try {
            val service = termuxService ?: return
            val workingDir = com.termux.shared.termux.settings.properties.TermuxAppSharedProperties.getProperties().defaultWorkingDirectory
            val termuxSession = service.createTermuxSession(
                null, null, null, workingDir, false, label
            )
            if (termuxSession != null) {
                val tab = TerminalTab(UUID.randomUUID().toString(), label, termuxSession.terminalSession)
                terminalTabs = terminalTabs + tab
                activeTerminalTabId = tab.id
                // Pre-create TerminalView so it survives tab switches
                val tv = TerminalView(this, null).apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setTextSize(SettingsStore.terminalFontSize)
                }
                terminalViews[tab.id] = tv
                Log.d(TAG, "Created terminal tab: $label (id=${tab.id})")
                if (initialCommand != null) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            termuxSession.terminalSession.write(initialCommand + "\n")
                            Log.d(TAG, "Wrote initial command to tab $label: $initialCommand")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to write initial command to tab $label", e)
                        }
                    }, 800)
                }
            } else {
                Log.e(TAG, "Failed to create terminal tab: $label")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create terminal tab", e)
        }
    }

    fun closeTerminalTab(tabId: String) {
        val index = terminalTabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val remaining = terminalTabs.filter { it.id != tabId }
        terminalTabs = remaining
        if (activeTerminalTabId == tabId) {
            activeTerminalTabId = when {
                remaining.isEmpty() -> null
                index >= remaining.size -> remaining.last().id
                else -> remaining[index].id
            }
        }
        terminalViews.remove(tabId)
        Log.d(TAG, "Closed terminal tab: $tabId, remaining: ${remaining.size}")
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        termuxService = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(this)
            isBound = false
        }
    }

    @Composable
    fun MainScreen() {
        val sshViewModel = remember { SshViewModel() }

        var selectedTab by remember { mutableStateOf(AppTab.HOSTS) }
        var localWorkspaceOpen by remember { mutableStateOf(false) }
        var selectedWorkspaceId by remember { mutableStateOf<String?>(null) }
        var showAddHost by remember { mutableStateOf(false) }
        var editingConfig by remember { mutableStateOf<SshConfig?>(null) }
        var isConnecting by remember { mutableStateOf(false) }
        var connectionError by remember { mutableStateOf<String?>(null) }
        var showDirPicker by remember { mutableStateOf(false) }
        var pendingDirPickerConfig by remember { mutableStateOf<SshConfig?>(null) }

        val savedConnections by sshViewModel.savedConnections.collectAsState()
        val connectionStates by sshViewModel.connectionStates.collectAsState()
        val activeConnectionId by sshViewModel.activeConnectionId.collectAsState()

        val hasOpenWorkspace = localWorkspaceOpen || activeConnectionId != null

        var topLeftContentType by remember { mutableStateOf(PaneContentType.FILE_BROWSER) }
        var topRightContentType by remember { mutableStateOf(PaneContentType.PREVIEW) }
        var bottomContentType by remember { mutableStateOf(PaneContentType.TERMINAL) }

        var currentPath by remember { mutableStateOf("/sdcard") }
        var fileList by remember { mutableStateOf<List<FileItem>>(emptyList()) }
        var isLoading by remember { mutableStateOf(false) }
        var selectedFile by remember { mutableStateOf<String?>(null) }
        var previewContent by remember { mutableStateOf<String?>(null) }
        var previewImageBytes by remember { mutableStateOf<ByteArray?>(null) }
        var isPreviewImage by remember { mutableStateOf(false) }
        var isBinaryFile by remember { mutableStateOf(false) }
        var pdfPreviewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        var refreshTrigger by remember { mutableStateOf(0) }

        var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
        var isSelectionMode by remember { mutableStateOf(false) }
        var showRenameDialog by remember { mutableStateOf(false) }
        var showNewItemDialog by remember { mutableStateOf(false) }
        var newItemIsFolder by remember { mutableStateOf(false) }
        var renameTargetPath by remember { mutableStateOf<String?>(null) }
        var showDeleteConfirmDialog by remember { mutableStateOf(false) }

        val clipboardPaths = remember { mutableStateListOf<String>() }
        var isCutMode by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(currentPath, ActiveFileSystemManager.fileSystemChanged.value, refreshTrigger) {
            isLoading = true
            try {
                val fs = ActiveFileSystemManager.getActiveFileSystem()
                val result = withContext(Dispatchers.IO) { fs.listDirectory(currentPath) }
                fileList = result.getOrDefault(emptyList())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load directory", e)
            } finally {
                isLoading = false
            }
        }

        LaunchedEffect(selectedFile) {
            val path = selectedFile
            previewContent = null
            previewImageBytes = null
            isPreviewImage = false
            isBinaryFile = false
            pdfPreviewBitmap = null
            if (path != null) {
                try {
                    val fs = ActiveFileSystemManager.getActiveFileSystem()
                    val isRemote = ActiveFileSystemManager.isRemote.value
                    if (isBinaryExtension(path)) {
                        isBinaryFile = true
                        if (path.lowercase().endsWith(".pdf")) {
                            try {
                                val result = withContext(Dispatchers.IO) { fs.readFile(path).getOrNull() }
                                if (result != null) {
                                    val cacheFile = File(cacheDir, "preview_${File(path).name}")
                                    cacheFile.writeBytes(result)
                                    pdfPreviewBitmap = renderPdfFirstPage(cacheFile)
                                    previewImageBytes = null
                                } else {
                                    previewImageBytes = result
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "PDF preview failed", e)
                                previewImageBytes = null
                            }
                        } else {
                            try {
                                previewImageBytes = withContext(Dispatchers.IO) { fs.readFile(path).getOrNull() }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to read binary for metadata", e)
                            }
                        }
                    } else if (path.endsWith(".jpg") || path.endsWith(".png") || path.endsWith(".jpeg") || path.endsWith(".gif") || path.endsWith(".webp")) {
                        isPreviewImage = true
                        val result = withContext(Dispatchers.IO) { fs.readFile(path) }
                        previewImageBytes = result.getOrNull()
                    } else {
                        isPreviewImage = false
                        val result = withContext(Dispatchers.IO) { fs.readTextFile(path) }
                        previewContent = result.getOrDefault("无法读取文件")
                    }
                } catch (e: Exception) {
                    previewContent = "预览失败: ${e.message}"
                    isPreviewImage = false
                }
            }
        }

        fun handleDelete() {
            if (selectedFiles.isEmpty()) return
            showDeleteConfirmDialog = true
        }

        fun confirmDelete() {
            scope.launch {
                try {
                    val fs = ActiveFileSystemManager.getActiveFileSystem()
                    for (path in selectedFiles) {
                        try { fs.delete(path); Log.d(TAG, "Deleted: $path") }
                        catch (e: Exception) { Log.e(TAG, "Failed to delete $path", e) }
                    }
                } finally {
                    selectedFiles = emptySet()
                    isSelectionMode = false
                    refreshTrigger++
                }
            }
        }

        fun handleCopy() {
            clipboardPaths.clear(); clipboardPaths.addAll(selectedFiles)
            isCutMode = false; selectedFiles = emptySet(); isSelectionMode = false
        }

        fun handleCut() {
            clipboardPaths.clear(); clipboardPaths.addAll(selectedFiles)
            isCutMode = true; selectedFiles = emptySet(); isSelectionMode = false
        }

        fun handlePaste(targetDir: String) {
            scope.launch {
                val fs = ActiveFileSystemManager.getActiveFileSystem()
                for (sourcePath in clipboardPaths) {
                    val sourceName = File(sourcePath).name
                    val targetPath = File(targetDir, sourceName).absolutePath
                    try {
                        if (isCutMode) fs.move(sourcePath, targetPath) else fs.copy(sourcePath, targetPath)
                        Log.d(TAG, "${if (isCutMode) "Moved" else "Copied"}: $sourcePath -> $targetPath")
                    } catch (e: Exception) { Log.e(TAG, "Failed to paste $sourcePath", e) }
                }
                clipboardPaths.clear(); isCutMode = false; refreshTrigger++
            }
        }

        fun handleRename(path: String, newName: String) {
            scope.launch {
                val parent = File(path).parent ?: return@launch
                val newPath = File(parent, newName).absolutePath
                try { ActiveFileSystemManager.getActiveFileSystem().move(path, newPath) }
                catch (e: Exception) { Log.e(TAG, "Failed to rename $path", e) }
                refreshTrigger++
            }
        }

        fun handleNewItem(name: String, isFolder: Boolean) {
            scope.launch {
                val targetPath = File(currentPath, name).absolutePath
                try {
                    val fs = ActiveFileSystemManager.getActiveFileSystem()
                    if (isFolder) fs.createDirectory(targetPath) else fs.writeFile(targetPath, byteArrayOf())
                    Log.d(TAG, "Created: $targetPath (folder=$isFolder)")
                } catch (e: Exception) { Log.e(TAG, "Failed to create $targetPath", e) }
                refreshTrigger++
            }
        }

        fun handleCopyContent() {
            val content = previewContent ?: return
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("file_content", content)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this@TermuxWorkspaceActivity, "内容已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }

        fun handleOpenExternally() {
            val path = selectedFile ?: return
            scope.launch {
                try {
                    val cacheFile = withContext(Dispatchers.IO) {
                        val name = File(path).name
                        val dest = File(cacheDir, "open_external_$name")
                        if (!dest.exists()) {
                            val fs = ActiveFileSystemManager.getActiveFileSystem()
                            val bytes = fs.readFile(path).getOrThrow()
                            dest.writeBytes(bytes)
                        }
                        dest
                    }
                    val mimeType = android.webkit.MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(path.substringAfterLast('.', ""))
                        ?: "*/*"
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(androidx.core.content.FileProvider.getUriForFile(
                            this@TermuxWorkspaceActivity,
                            "${packageName}.fileprovider",
                            cacheFile
                        ), mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@TermuxWorkspaceActivity, "无法打开文件: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        val onFileLongClick: (FileItem) -> Unit = { file ->
            isSelectionMode = true
            selectedFiles = if (file.path in selectedFiles) selectedFiles - file.path else selectedFiles + file.path
        }

        val onNewFileClick: () -> Unit = { showNewItemDialog = true; newItemIsFolder = false }
        val onNewFolderClick: () -> Unit = { showNewItemDialog = true; newItemIsFolder = true }
        val onSelectAllClick: () -> Unit = { isSelectionMode = true; selectedFiles = fileList.map { it.path }.toSet() }
        val onPaneMenuDelete: () -> Unit = { handleDelete() }

        Scaffold(
            topBar = {
                if (hasOpenWorkspace && selectedTab == AppTab.WORKSPACE) {
                    WorkspaceTabBar(
                        localOpen = localWorkspaceOpen,
                        connections = savedConnections.filter {
                            it.id in connectionStates.keys && connectionStates[it.id] is SshConnectionState.Connected
                        },
                        selectedId = selectedWorkspaceId,
                        onSelectLocal = {
                            localWorkspaceOpen = true; selectedWorkspaceId = "local"
                            ActiveFileSystemManager.switchToLocal(); currentPath = "/sdcard"
                        },
                        onSelectRemote = { config ->
                            selectedWorkspaceId = config.id
                            val conn = sshViewModel.getConnection(config.id)
                            if (conn != null) {
                                val fs = SshFileSystem(conn)
                                ActiveFileSystemManager.switchToSsh(
                                    sshFileSystem = fs, rootPath = "/home/${config.username}",
                                    host = config.host, user = config.username
                                )
                                currentPath = "/home/${config.username}"
                            }
                            val label = "${config.username}@${config.host}"
                            val existingTab = terminalTabs.find { it.label == label }
                            if (existingTab == null) {
                                val portPart = if (config.port != 22) " -p ${config.port}" else ""
                                val sshCommand = "ssh -o StrictHostKeyChecking=no$portPart ${config.username}@${config.host}"
                                createNewTerminalTab(label, sshCommand)
                            } else { activeTerminalTabId = existingTab.id }
                        },
                        onCloseLocal = {
                            localWorkspaceOpen = false
                            if (selectedWorkspaceId == "local") selectedWorkspaceId = activeConnectionId
                        },
                        onCloseRemote = { config ->
                            sshViewModel.disconnect(config.id)
                            if (selectedWorkspaceId == config.id)
                                selectedWorkspaceId = if (localWorkspaceOpen) "local" else null
                        }
                    )
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = NavBarBg,
                    tonalElevation = 8.dp
                ) {
                    AppTab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BrightText,
                                selectedTextColor = BrightText,
                                unselectedIconColor = MutedText,
                                unselectedTextColor = MutedText,
                                indicatorColor = Indigo
                            )
                        )
                    }
                }
            },
            containerColor = Color(0xFF0B1120)
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (selectedTab) {
                    AppTab.HOSTS -> HostsScreen(
                        connections = savedConnections,
                        connectionStates = connectionStates,
                        localConnected = localWorkspaceOpen,
                        onConnectLocal = {
                            localWorkspaceOpen = true; selectedWorkspaceId = "local"
                            ActiveFileSystemManager.switchToLocal(); currentPath = "/sdcard"
                            selectedTab = AppTab.WORKSPACE
                        },
                        onAddHost = { showAddHost = true },
                        onConnect = { config ->
                            connectionError = null
                            sshViewModel.connect(config)
                            pendingDirPickerConfig = config; showDirPicker = true
                        },
                        onDelete = { config -> sshViewModel.deleteConnection(config.id) },
                        onEdit = { config -> editingConfig = config; showAddHost = true }
                    )
                    AppTab.WORKSPACE -> {
                        val onFileClickHandler: (FileItem, PanePosition) -> Unit = { file, pane ->
                            if (file.isDirectory) currentPath = file.path
                            else {
                                selectedFile = file.path
                                val tp = PaneBindingManager.getBoundPreviewPane(pane) ?: PanePosition.TOP_RIGHT
                                when (tp) {
                                    PanePosition.TOP_LEFT -> topLeftContentType = PaneContentType.PREVIEW
                                    PanePosition.TOP_RIGHT -> topRightContentType = PaneContentType.PREVIEW
                                    PanePosition.BOTTOM -> bottomContentType = PaneContentType.PREVIEW
                                }
                            }
                        }
                        if (!hasOpenWorkspace) {
                            NoWorkspaceSelected(onOpenHosts = { selectedTab = AppTab.HOSTS })
                        } else {
                            ThreePaneWorkspace(
                                currentPath = currentPath,
                                fileList = fileList,
                                isLoading = isLoading,
                                selectedFile = selectedFile,
                                previewContent = previewContent,
                                previewImageBytes = if (pdfPreviewBitmap != null) {
                                    val baos = ByteArrayOutputStream()
                                    pdfPreviewBitmap!!.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, baos)
                                    baos.toByteArray()
                                } else previewImageBytes,
                                isPreviewImage = isPreviewImage || pdfPreviewBitmap != null,
                                terminalTabs = terminalTabs,
                                activeTerminalTabId = activeTerminalTabId,
                                onTerminalTabSelect = { activeTerminalTabId = it },
                                onCreateNewTerminalTab = { label -> createNewTerminalTab(label) },
                                onCloseTerminalTab = { tabId -> closeTerminalTab(tabId) },
                                topLeftContentType = topLeftContentType,
                                topRightContentType = topRightContentType,
                                bottomContentType = bottomContentType,
                                pendingSshCommand = pendingSshCommand,
                                onTopLeftContentTypeChange = { topLeftContentType = it },
                                onTopRightContentTypeChange = { topRightContentType = it },
                                onBottomContentTypeChange = { bottomContentType = it },
                                onNavigateUp = { val p = File(currentPath).parent; if (p != null) currentPath = p },
                                onFileClickFromPane = onFileClickHandler,
                                onRefresh = { refreshTrigger++ },
                                onPreviewClose = { selectedFile = null; previewContent = null },
                                selectedFiles = selectedFiles,
                                isSelectionMode = isSelectionMode,
                                onFileLongClick = onFileLongClick,
                                onNewFile = onNewFileClick,
                                onNewFolder = onNewFolderClick,
                                onSelectAll = onSelectAllClick,
                                onDeleteSelection = onPaneMenuDelete,
                                onRenameRequest = { path -> renameTargetPath = path; showRenameDialog = true }
                            )
                        }
                        if (isSelectionMode) {
                            SelectionActionBar(
                                selectedCount = selectedFiles.size,
                                clipboardCount = clipboardPaths.size,
                                cutMode = isCutMode,
                                onDelete = { handleDelete() },
                                onCopy = { handleCopy() },
                                onCut = { handleCut() },
                                onPaste = { handlePaste(currentPath) },
                                onClearSelection = { selectedFiles = emptySet(); isSelectionMode = false }
                            )
                        }
                    }
                    AppTab.CONNECTIONS -> ConnectionsScreen(
                        connections = savedConnections,
                        connectionStates = connectionStates,
                        onDisconnect = { sshViewModel.disconnect(it.id) }
                    )
                    AppTab.SETTINGS -> SettingsScreen()
                }
            }
        }

        if (showAddHost) {
            AddHostDialog(
                editConfig = editingConfig,
                onDismiss = { showAddHost = false; editingConfig = null },
                onSaveAndConnect = { config ->
                    sshViewModel.saveConnection(config)
                    if (editingConfig == null) {
                        // New connection: connect and show dir picker
                        sshViewModel.connect(config)
                        showAddHost = false; editingConfig = null
                        pendingDirPickerConfig = config; showDirPicker = true
                    } else {
                        // Editing: just save, don't reconnect
                        showAddHost = false; editingConfig = null
                    }
                }
            )
        }

        if (showDirPicker && pendingDirPickerConfig != null) {
            DirectoryPickerDialog(
                config = pendingDirPickerConfig!!,
                sshViewModel = sshViewModel,
                onDismiss = { showDirPicker = false; pendingDirPickerConfig = null },
                onSelectDirectory = { path ->
                    val config = pendingDirPickerConfig!!
                    currentPath = path; selectedWorkspaceId = config.id
                    // Generate key and upload for passwordless SSH (use IO dispatcher since not in composable context)
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        if (!SshKeyManager.hasKeyPair()) {
                            Log.d(TAG, "Generating SSH key pair...")
                            SshKeyManager.generateKeyPair("rostrum")
                        }
                        val pubKey = SshKeyManager.getPublicKey()
                        if (pubKey != null) {
                            val conn = sshViewModel.getConnection(config.id)
                            if (conn != null) {
                                Log.d(TAG, "Uploading public key to remote...")
                                val fs = SshFileSystem(conn)
                                fs.setupAuthorizedKey(pubKey, config.username)
                                Log.d(TAG, "Public key uploaded successfully")
                            }
                        }
                        // Write SSH command with key-based auth
                        val portPart = if (config.port != 22) " -p ${config.port}" else ""
                        val keyArg = if (SshKeyManager.hasKeyPair()) " -i ~/.ssh/id_rsa" else ""
                        val escapedDir = path.replace("'", "'\\''")
                        val sshCommand = "ssh -o StrictHostKeyChecking=no${portPart}${keyArg} ${config.username}@${config.host} -t 'cd ${escapedDir} && exec bash -l'\n"
                        pendingSshCommand = sshCommand
                        val label = "${config.username}@${config.host}"
                        val existingTab = terminalTabs.find { it.label == label }
                        if (existingTab == null) createNewTerminalTab(label, sshCommand.trimEnd())
                        else activeTerminalTabId = existingTab.id
                        selectedTab = AppTab.WORKSPACE
                        showDirPicker = false; pendingDirPickerConfig = null
                    }
                }
            )
        }

        if (connectionError != null) {
            AlertDialog(
                onDismissRequest = { connectionError = null },
                title = { Text("连接失败") },
                text = { Text(connectionError!!) },
                confirmButton = { TextButton(onClick = { connectionError = null }) { Text("确定") } }
            )
        }

        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("确认删除") },
                text = { Text("确定要删除选中的 ${selectedFiles.size} 个文件/文件夹吗？此操作不可撤销。") },
                confirmButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false; confirmDelete() }) {
                        Text("删除", color = Color(0xFFEF4444))
                    }
                },
                dismissButton = { TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("取消") } }
            )
        }

        if (showRenameDialog) {
            var newName by remember { mutableStateOf(File(renameTargetPath ?: "").name) }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false; renameTargetPath = null },
                title = { Text("重命名") },
                text = {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        label = { Text("新名称") }, singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val path = renameTargetPath
                            if (path != null && newName.isNotBlank()) handleRename(path, newName)
                            showRenameDialog = false; renameTargetPath = null
                        },
                        enabled = newName.isNotBlank()
                    ) { Text("确定") }
                },
                dismissButton = { TextButton(onClick = { showRenameDialog = false; renameTargetPath = null }) { Text("取消") } }
            )
        }

        if (showNewItemDialog) {
            var newItemName by remember { mutableStateOf(if (newItemIsFolder) "新建文件夹" else "新建文件.txt") }
            AlertDialog(
                onDismissRequest = { showNewItemDialog = false },
                title = { Text(if (newItemIsFolder) "新建文件夹" else "新建文件") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = newItemName, onValueChange = { newItemName = it },
                            label = { Text("名称") }, singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newItemName.isNotBlank()) handleNewItem(newItemName, newItemIsFolder)
                            showNewItemDialog = false
                        },
                        enabled = newItemName.isNotBlank()
                    ) { Text("创建") }
                },
                dismissButton = { TextButton(onClick = { showNewItemDialog = false }) { Text("取消") } }
            )
        }
    }

    private fun renderPdfFirstPage(file: File): android.graphics.Bitmap? {
        return try {
            val fd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = android.graphics.pdf.PdfRenderer(fd)
            val page = renderer.openPage(0)
            val bitmap = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bitmap).apply {
                drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
            page.close()
            renderer.close()
            fd.close()
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "renderPdfFirstPage failed", e)
            null
        }
    }

    private fun isBinaryExtension(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in setOf("pdf", "zip", "rar", "7z", "tar", "gz", "bz2", "xz",
            "apk", "dex", "so", "jar", "class", "exe", "dll", "dmg", "iso",
            "mp3", "wav", "ogg", "flac", "aac",
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm",
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "heic",
            "ttf", "otf", "woff", "woff2", "eot",
            "db", "sqlite", "dat", "bin")
    }
}

private enum class AppTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOSTS("主机", Icons.Default.Computer),
    WORKSPACE("工作区", Icons.Default.Folder),
    CONNECTIONS("连接", Icons.Default.Link),
    SETTINGS("设置", Icons.Default.Settings)
}
