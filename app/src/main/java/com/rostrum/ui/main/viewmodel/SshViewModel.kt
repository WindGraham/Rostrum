package com.rostrum.ui.main.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import com.rostrum.core.filesystem.ActiveFileSystemManager
import com.rostrum.core.network.NetworkDiscoveryService
import com.rostrum.core.server.RemoteFileEditor
import com.rostrum.core.server.ServerInstaller
import com.rostrum.core.server.ServerInfo
import com.rostrum.core.ssh.connection.*
import com.rostrum.core.ssh.filesystem.SshFileCache
import com.rostrum.core.ssh.filesystem.SshFileSystem
import com.rostrum.core.ssh.session.*
import com.rostrum.core.ssh.terminal.DefaultForwardingStrategySelector
import com.rostrum.core.ssh.terminal.SshTerminalProxy
import com.rostrum.core.ssh.terminal.TerminalForwardingStrategy
import com.rostrum.data.repository.SshConnectionRepository
import com.rostrum.data.repository.SshConnectionRepositoryImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/**
 * SSH 相关功能的 ViewModel
 * 
 * 负责管理SSH连接、会话、文件传输等功能
 * 作为 MainViewModel 的内部委托使用
 * 
 * @author OmniMaster
 * @license BSD-2-Clause
 */
class SshViewModel(
    private val scope: CoroutineScope,
    private val keyStoreProvider: SshKeyStoreProvider? = null,
    cacheDir: File? = null,
    connectionRepository: SshConnectionRepository? = null
) {
    companion object {
        private const val TAG = "SshViewModel"
    }
    
    // Repository（可以通过 initRepository 重新赋值）
    private var connectionRepository: SshConnectionRepository? = connectionRepository
    
    // 连接池
    private val connectionPool = SshConnectionPool(keyStoreProvider)
    
    // 文件缓存
    private val fileCache = cacheDir?.let { SshFileCache(it) }
    
    // 网络发现服务
    private val discoveryService = NetworkDiscoveryService()
    
    // 策略选择器
    private val strategySelector = DefaultForwardingStrategySelector()
    
    // ==================== 状态 ====================
    
    // 保存的连接配置
    private val _savedConnections = MutableStateFlow<List<SshConfig>>(emptyList())
    val savedConnections: StateFlow<List<SshConfig>> = _savedConnections.asStateFlow()
    
    // 活跃连接状态
    val activeConnections: StateFlow<Map<String, SshConnectionState>> = connectionPool.connectionStates
    
    // 当前会话
    private val _currentSession = MutableStateFlow<ISshSession?>(null)
    val currentSession: StateFlow<ISshSession?> = _currentSession.asStateFlow()
    
    // 当前终端会话
    private val _currentTerminalSession = MutableStateFlow<SshTerminalSession?>(null)
    val currentTerminalSession: StateFlow<SshTerminalSession?> = _currentTerminalSession.asStateFlow()
    
    // 当前连接配置
    private val _currentConnectionConfig = MutableStateFlow<SshConfig?>(null)
    val currentConnectionConfig: StateFlow<SshConfig?> = _currentConnectionConfig.asStateFlow()

    // 当前文件系统
    private val _currentFileSystem = MutableStateFlow<SshFileSystem?>(null)
    val currentFileSystem: StateFlow<SshFileSystem?> = _currentFileSystem.asStateFlow()
    
    // 发现的SSH服务
    val discoveredServices: StateFlow<List<NetworkDiscoveryService.DiscoveredService>> = 
        discoveryService.discoveredServices
    
    // 传输进度
    private val _transferProgress = MutableStateFlow<TransferState?>(null)
    val transferProgress: StateFlow<TransferState?> = _transferProgress.asStateFlow()
    
    // 错误状态
    private val _error = MutableSharedFlow<SshError>(replay = 0, extraBufferCapacity = 5)
    val error: SharedFlow<SshError> = _error.asSharedFlow()
    
    // 是否正在加载
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 远程文件浏览状态
    private val _remoteCurrentPath = MutableStateFlow("/")
    val remoteCurrentPath: StateFlow<String> = _remoteCurrentPath.asStateFlow()
    
    private val _remoteFileList = MutableStateFlow<List<com.rostrum.core.plugin.models.FileInfo>>(emptyList())
    val remoteFileList: StateFlow<List<com.rostrum.core.plugin.models.FileInfo>> = _remoteFileList.asStateFlow()
    
    private val _isFileBrowserLoading = MutableStateFlow(false)
    val isFileBrowserLoading: StateFlow<Boolean> = _isFileBrowserLoading.asStateFlow()

    private val _remoteServerInfo = MutableStateFlow<ServerInfo?>(null)
    val remoteServerInfo: StateFlow<ServerInfo?> = _remoteServerInfo.asStateFlow()

    private val _remoteServerStatus = MutableStateFlow("未连接")
    val remoteServerStatus: StateFlow<String> = _remoteServerStatus.asStateFlow()

    private val serverForwardedPorts = mutableMapOf<String, Int>()
    
    // ==================== 连接管理 ====================
    
    /**
     * 连接到SSH服务器
     */
    fun connect(config: SshConfig) {
        // 立即设置当前连接配置（在协程之前，确保UI可以立即看到）
        _currentConnectionConfig.value = config
        _isLoading.value = true
        
        scope.launch {
            try {
                Log.i(TAG, "连接到: ${config.displayName}")
                
                val result = connectionPool.getConnection(config)
                
                if (result.isSuccess) {
                    Log.i(TAG, "连接成功: ${config.displayName}")
                     
                    // 连接成功后立即准备远程工作区：终端 + SFTP 文件系统。
                    openTerminal(config.id)
                    openFileBrowser(
                        connectionId = config.id,
                        initialPath = "/",
                        switchToMainFileSystem = true
                    )
                    ensureRemoteServer(config.id)
                } else {
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "连接失败: ${config.displayName}", error)
                    _currentConnectionConfig.value = null
                    _error.emit(SshError.ConnectionFailed(config.displayName, error?.message ?: "未知错误"))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "连接异常: ${config.displayName}", e)
                _currentConnectionConfig.value = null
                _error.emit(SshError.ConnectionFailed(config.displayName, e.message ?: "连接异常"))
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect(connectionId: String) {
        scope.launch {
            try {
                // 关闭当前会话
                if (_currentSession.value?.connection?.connectionId == connectionId) {
                    _currentSession.value?.close()
                    _currentSession.value = null
                    _currentTerminalSession.value = null
                    _currentConnectionConfig.value = null
                    _currentFileSystem.value?.close()
                    _currentFileSystem.value = null
                    
                    // 如果当前使用的是远程文件系统，切换回本地
                    if (ActiveFileSystemManager.isUsingRemote()) {
                        ActiveFileSystemManager.switchToLocal()
                        Log.i(TAG, "已切换回本地文件系统")
                    }
                }

                serverForwardedPorts.remove(connectionId)?.let { port ->
                    connectionPool.getExistingConnection(connectionId)?.removeLocalPortForwarding(port)
                }
                
                connectionPool.removeConnection(connectionId)
                Log.i(TAG, "已断开连接: $connectionId")
                
            } catch (e: Exception) {
                Log.e(TAG, "断开连接失败: $connectionId", e)
            }
        }
    }
    
    /**
     * 断开所有连接
     */
    fun disconnectAll() {
        scope.launch {
            _currentSession.value?.close()
            _currentSession.value = null
            _currentTerminalSession.value = null
            _currentConnectionConfig.value = null
            _currentFileSystem.value?.close()
            _currentFileSystem.value = null
            
            // 切换回本地文件系统
            if (ActiveFileSystemManager.isUsingRemote()) {
                ActiveFileSystemManager.switchToLocal()
                Log.i(TAG, "已切换回本地文件系统")
            }
            
            connectionPool.disconnectAll()
            serverForwardedPorts.clear()
            _remoteServerInfo.value = null
            _remoteServerStatus.value = "未连接"
        }
    }

    private fun ensureRemoteServer(connectionId: String) {
        scope.launch {
            val connection = connectionPool.getExistingConnection(connectionId) ?: return@launch
            val commandSession = SshCommandSession(connection)
            commandSession.start()
            _remoteServerStatus.value = "正在检测远端服务..."

            try {
                val installed = ServerInstaller.isServerInstalled(commandSession)
                val remotePorts = listOf(8080, 18080, 28080)
                var remoteInfo: ServerInfo? = null
                var lastError: Throwable? = null

                for (port in remotePorts) {
                    val result = if (installed) {
                        ServerInstaller.startServer(commandSession, port)
                    } else {
                        ServerInstaller.installServer(commandSession, port, workspace = "/")
                    }

                    if (result.isSuccess) {
                        remoteInfo = result.getOrThrow()
                        break
                    }
                    lastError = result.exceptionOrNull()
                }

                if (remoteInfo == null) {
                    _remoteServerStatus.value = "远端服务不可用，已使用 SFTP 兜底: ${lastError?.message ?: "未知错误"}"
                    return@launch
                }

                serverForwardedPorts.remove(connectionId)?.let { oldPort ->
                    connection.removeLocalPortForwarding(oldPort)
                }

                val forwardResult = connection.setLocalPortForwarding(
                    localPort = 0,
                    remoteHost = "127.0.0.1",
                    remotePort = remoteInfo.port
                )
                if (forwardResult.isFailure) {
                    _remoteServerStatus.value = "远端服务已启动，但 SSH 隧道失败: ${forwardResult.exceptionOrNull()?.message}"
                    return@launch
                }

                val localPort = forwardResult.getOrThrow()
                val editor = RemoteFileEditor("http://127.0.0.1:$localPort", remoteInfo.token)
                val ping = editor.ping().getOrDefault(false)
                if (!ping) {
                    _remoteServerStatus.value = "远端服务隧道已建立，但健康检查失败"
                    return@launch
                }

                serverForwardedPorts[connectionId] = localPort
                _remoteServerInfo.value = remoteInfo.copy(host = "127.0.0.1", port = localPort)
                _remoteServerStatus.value = "远端服务已就绪（SSH 隧道 127.0.0.1:$localPort）"
            } catch (e: Exception) {
                Log.w(TAG, "远端服务启动失败，继续使用 SFTP", e)
                _remoteServerStatus.value = "远端服务不可用，已使用 SFTP 兜底: ${e.message}"
            } finally {
                commandSession.close()
            }
        }
    }
    
    /**
     * 测试连接
     */
    fun testConnection(config: SshConfig, onResult: (Boolean, Long?) -> Unit) {
        scope.launch {
            _isLoading.value = true
            
            try {
                val connection = SshConnectionImpl(config, keyStoreProvider)
                val connectResult = connection.connect()
                
                if (connectResult.isSuccess) {
                    val testResult = connection.testConnection()
                    connection.close()
                    
                    if (testResult.isSuccess) {
                        onResult(true, testResult.getOrThrow().pingMs)
                    } else {
                        onResult(false, null)
                    }
                } else {
                    onResult(false, null)
                }
                
            } catch (e: Exception) {
                onResult(false, null)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // ==================== 配置管理 ====================
    
    init {
        // 初始化时从Repository加载保存的连接
        connectionRepository?.let { repo ->
            scope.launch {
                repo.observeConnections().collect { connections ->
                    _savedConnections.value = connections
                    Log.d(TAG, "从仓库加载了 ${connections.size} 个SSH连接配置")
                }
            }
        }
    }
    
    /**
     * 保存连接配置（持久化）
     * 
     * @param config SSH配置
     * @param onResult 保存结果回调，参数为保存结果类型
     */
    fun saveConnection(
        config: SshConfig, 
        onResult: ((com.rostrum.data.repository.SshSaveResult) -> Unit)? = null
    ) {
        scope.launch {
            try {
                val result = connectionRepository?.saveConnection(config)
                    ?: run {
                        // 如果没有repository，仅保存在内存中
                        val currentList = _savedConnections.value.toMutableList()
                        val existingIndex = currentList.indexOfFirst { it.id == config.id }
                        val duplicateIndex = currentList.indexOfFirst { 
                            it.host == config.host && 
                            it.port == config.port && 
                            it.username == config.username 
                        }
                        
                        val saveResult = when {
                            existingIndex >= 0 -> {
                                currentList[existingIndex] = config
                                com.rostrum.data.repository.SshSaveResult.UPDATED_SAME_ID
                            }
                            duplicateIndex >= 0 -> {
                                currentList[duplicateIndex] = config
                                com.rostrum.data.repository.SshSaveResult.UPDATED_DUPLICATE
                            }
                            else -> {
                                currentList.add(config)
                                com.rostrum.data.repository.SshSaveResult.ADDED
                            }
                        }
                        _savedConnections.value = currentList
                        saveResult
                    }
                Log.d(TAG, "保存连接配置: ${config.displayName}, 结果: $result")
                onResult?.invoke(result)
            } catch (e: Exception) {
                Log.e(TAG, "保存连接配置失败", e)
                _error.emit(SshError.ConnectionFailed(config.displayName, "保存配置失败: ${e.message}"))
            }
        }
    }
    
    /**
     * 删除连接配置
     */
    fun deleteConnection(configId: String) {
        scope.launch {
            try {
                connectionRepository?.deleteConnection(configId)
                    ?: run {
                        _savedConnections.value = _savedConnections.value.filter { it.id != configId }
                    }
                Log.d(TAG, "删除连接配置: $configId")
            } catch (e: Exception) {
                Log.e(TAG, "删除连接配置失败", e)
            }
        }
    }
    
    /**
     * 加载保存的连接配置（从仓库）
     */
    fun loadSavedConnections(connections: List<SshConfig>) {
        _savedConnections.value = connections
    }
    
    /**
     * 初始化Repository（在ViewModel创建后调用）
     */
    fun initRepository(context: Context) {
        if (connectionRepository == null) {
            val repo = SshConnectionRepositoryImpl(context.applicationContext)
            connectionRepository = repo
            // 加载已保存的连接
            scope.launch {
                repo.observeConnections().collect { connections ->
                    _savedConnections.value = connections
                }
            }
        }
    }
    
    // ==================== 会话管理 ====================
    
    /**
     * 打开终端会话
     */
    fun openTerminal(connectionId: String) {
        scope.launch {
            try {
                val connection = connectionPool.getExistingConnection(connectionId)
                    ?: run {
                        _error.emit(SshError.SessionFailed("连接不存在"))
                        return@launch
                    }
                
                // 关闭现有终端会话
                _currentTerminalSession.value?.close()
                
                // 创建新的终端会话
                val session = SshTerminalSession(connection)
                val result = session.start()
                
                if (result.isSuccess) {
                    _currentTerminalSession.value = session
                    _currentSession.value = session
                    Log.i(TAG, "终端会话已打开: $connectionId")
                } else {
                    _error.emit(SshError.SessionFailed(result.exceptionOrNull()?.message ?: "打开终端失败"))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "打开终端失败", e)
                _error.emit(SshError.SessionFailed(e.message ?: "打开终端异常"))
            }
        }
    }
    
    /**
     * 打开文件浏览器
     * 
     * @param connectionId 连接ID
     * @param initialPath 初始路径
     * @param switchToMainFileSystem 是否切换主文件系统（三窗口页面）
     */
    fun openFileBrowser(
        connectionId: String, 
        initialPath: String = "/",
        switchToMainFileSystem: Boolean = false
    ) {
        scope.launch {
            try {
                _isFileBrowserLoading.value = true
                
                val connection = connectionPool.getExistingConnection(connectionId)
                    ?: run {
                        _error.emit(SshError.SessionFailed("连接不存在"))
                        _isFileBrowserLoading.value = false
                        return@launch
                    }
                
                // 关闭现有文件系统
                _currentFileSystem.value?.close()
                
                // 创建新的文件系统
                val fileSystem = SshFileSystem(connection, fileCache)
                _currentFileSystem.value = fileSystem
                
                // 加载初始目录
                _remoteCurrentPath.value = initialPath
                loadRemoteDirectory(initialPath)
                
                // 如果需要，切换主文件系统（三窗口页面将使用SSH文件系统）
                if (switchToMainFileSystem) {
                    // 获取连接配置信息
                    val config = _savedConnections.value.find { it.id == connectionId }
                    ActiveFileSystemManager.switchToSsh(
                        sshFileSystem = fileSystem,
                        rootPath = initialPath,
                        host = config?.host ?: connection.config.host,
                        user = config?.username ?: connection.config.username
                    )
                    Log.i(TAG, "已切换到SSH文件系统: ${config?.displayName ?: connectionId}")
                }
                
                Log.i(TAG, "文件浏览器已打开: $connectionId")
                
            } catch (e: Exception) {
                Log.e(TAG, "打开文件浏览器失败", e)
                _error.emit(SshError.SessionFailed(e.message ?: "打开文件浏览器异常"))
            } finally {
                _isFileBrowserLoading.value = false
            }
        }
    }
    
    /**
     * 切换到SSH文件系统（三窗口页面）
     * 
     * 在SSH连接后调用此方法，将三窗口页面的文件浏览器切换到SSH服务器
     * 这个方法是同步的，会立即切换文件系统
     */
    fun switchToSshFileSystem(connectionId: String, initialPath: String = "/home") {
        val connection = connectionPool.getExistingConnection(connectionId)
        _currentConnectionConfig.value = _savedConnections.value.find { it.id == connectionId } ?: connection?.config

        // 如果当前已经有 SSH 文件系统，直接使用它
        val existingFileSystem = _currentFileSystem.value
        if (existingFileSystem != null) {
            val config = _currentConnectionConfig.value
            Log.e(TAG, "switchToSshFileSystem: 使用现有文件系统, connectionId=$connectionId, path=$initialPath, config=${config?.host}")
            ActiveFileSystemManager.switchToSsh(
                sshFileSystem = existingFileSystem,
                rootPath = initialPath,
                host = config?.host,
                user = config?.username
            )
            Log.e(TAG, "switchToSshFileSystem: ActiveFileSystemManager已切换, isRemote=${ActiveFileSystemManager.isUsingRemote()}")
            return
        }
        Log.e(TAG, "switchToSshFileSystem: 没有现有文件系统，需要打开新的")
        
        // 否则打开新的文件浏览器并切换
        openFileBrowser(connectionId, initialPath, switchToMainFileSystem = true)
    }
    
    /**
     * 切换回本地文件系统
     */
    fun switchToLocalFileSystem() {
        ActiveFileSystemManager.switchToLocal()
        Log.i(TAG, "已切换回本地文件系统")
    }
    
    /**
     * 导航到远程目录
     */
    fun navigateToRemotePath(path: String) {
        scope.launch {
            _isFileBrowserLoading.value = true
            _remoteCurrentPath.value = path
            loadRemoteDirectory(path)
            _isFileBrowserLoading.value = false
        }
    }
    
    /**
     * 返回上级目录
     */
    fun navigateUpRemote() {
        val current = _remoteCurrentPath.value
        if (current == "/" || current.isEmpty()) return
        
        val parent = current.trimEnd('/').substringBeforeLast('/', "/")
        val finalParent = if (parent.isEmpty()) "/" else parent
        navigateToRemotePath(finalParent)
    }
    
    /**
     * 刷新当前远程目录
     */
    fun refreshRemoteDirectory() {
        navigateToRemotePath(_remoteCurrentPath.value)
    }
    
    /**
     * 加载远程目录内容
     */
    private suspend fun loadRemoteDirectory(path: String) {
        val fileSystem = _currentFileSystem.value
        if (fileSystem == null) {
            _remoteFileList.value = emptyList()
            return
        }
        
        try {
            val result = fileSystem.listDirectory(path)
            if (result.isSuccess) {
                // 排序：目录在前，然后按名称排序
                val files = result.getOrThrow().sortedWith(
                    compareByDescending<com.rostrum.core.plugin.models.FileInfo> { it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )
                _remoteFileList.value = files
            } else {
                Log.e(TAG, "加载目录失败: ${result.exceptionOrNull()?.message}")
                _remoteFileList.value = emptyList()
                _error.emit(SshError.SessionFailed("加载目录失败: ${result.exceptionOrNull()?.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载目录异常", e)
            _remoteFileList.value = emptyList()
            _error.emit(SshError.SessionFailed("加载目录异常: ${e.message}"))
        }
    }
    
    /**
     * 发送命令到当前终端
     */
    fun sendCommand(command: String) {
        _currentTerminalSession.value?.sendCommand(command)
    }
    
    /**
     * 调整终端大小
     */
    fun resizeTerminal(columns: Int, rows: Int) {
        _currentTerminalSession.value?.resize(columns, rows)
    }
    
    // ==================== 文件操作 ====================
    
    /**
     * 上传文件
     */
    fun uploadFile(
        connectionId: String,
        localFile: File,
        remotePath: String
    ) {
        scope.launch {
            try {
                val connection = connectionPool.getExistingConnection(connectionId)
                    ?: run {
                        _error.emit(SshError.TransferFailed("连接不存在"))
                        return@launch
                    }
                
                val fileSession = SshFileSession(connection)
                fileSession.start()
                
                _transferProgress.value = TransferState(
                    type = TransferType.UPLOAD,
                    fileName = localFile.name,
                    progress = TransferProgress(0, localFile.length(), 0)
                )
                
                val result = fileSession.upload(localFile, remotePath) { progress ->
                    _transferProgress.value = TransferState(
                        type = TransferType.UPLOAD,
                        fileName = localFile.name,
                        progress = progress
                    )
                }
                
                fileSession.close()
                _transferProgress.value = null
                
                if (result.isFailure) {
                    _error.emit(SshError.TransferFailed(result.exceptionOrNull()?.message ?: "上传失败"))
                } else {
                    Log.i(TAG, "文件上传成功: ${localFile.name}")
                }
                
            } catch (e: Exception) {
                _transferProgress.value = null
                _error.emit(SshError.TransferFailed(e.message ?: "上传异常"))
            }
        }
    }
    
    /**
     * 上传文件（从Uri）
     */
    fun uploadFile(
        context: Context,
        connectionId: String,
        localUri: Uri,
        remotePath: String
    ) {
        scope.launch {
            try {
                val connection = connectionPool.getExistingConnection(connectionId)
                    ?: run {
                        _error.emit(SshError.TransferFailed("连接不存在"))
                        return@launch
                    }
                
                val fileName = localUri.lastPathSegment ?: "unknown"
                val fileSession = SshFileSession(connection)
                fileSession.start()
                
                _transferProgress.value = TransferState(
                    type = TransferType.UPLOAD,
                    fileName = fileName,
                    progress = TransferProgress(0, 0, 0)
                )
                
                val result = fileSession.upload(context, localUri, remotePath) { progress ->
                    _transferProgress.value = TransferState(
                        type = TransferType.UPLOAD,
                        fileName = fileName,
                        progress = progress
                    )
                }
                
                fileSession.close()
                _transferProgress.value = null
                
                if (result.isFailure) {
                    _error.emit(SshError.TransferFailed(result.exceptionOrNull()?.message ?: "上传失败"))
                }
                
            } catch (e: Exception) {
                _transferProgress.value = null
                _error.emit(SshError.TransferFailed(e.message ?: "上传异常"))
            }
        }
    }
    
    /**
     * 下载文件
     */
    fun downloadFile(
        connectionId: String,
        remotePath: String,
        localFile: File
    ) {
        scope.launch {
            try {
                val connection = connectionPool.getExistingConnection(connectionId)
                    ?: run {
                        _error.emit(SshError.TransferFailed("连接不存在"))
                        return@launch
                    }
                
                val fileName = remotePath.substringAfterLast("/")
                val fileSession = SshFileSession(connection)
                fileSession.start()
                
                _transferProgress.value = TransferState(
                    type = TransferType.DOWNLOAD,
                    fileName = fileName,
                    progress = TransferProgress(0, 0, 0)
                )
                
                val result = fileSession.download(remotePath, localFile) { progress ->
                    _transferProgress.value = TransferState(
                        type = TransferType.DOWNLOAD,
                        fileName = fileName,
                        progress = progress
                    )
                }
                
                fileSession.close()
                _transferProgress.value = null
                
                if (result.isFailure) {
                    _error.emit(SshError.TransferFailed(result.exceptionOrNull()?.message ?: "下载失败"))
                } else {
                    Log.i(TAG, "文件下载成功: $fileName")
                }
                
            } catch (e: Exception) {
                _transferProgress.value = null
                _error.emit(SshError.TransferFailed(e.message ?: "下载异常"))
            }
        }
    }
    
    /**
     * 下载文件（到Uri）
     */
    fun downloadFile(
        context: Context,
        connectionId: String,
        remotePath: String,
        localUri: Uri
    ) {
        scope.launch {
            try {
                val connection = connectionPool.getExistingConnection(connectionId)
                    ?: run {
                        _error.emit(SshError.TransferFailed("连接不存在"))
                        return@launch
                    }
                
                val fileName = remotePath.substringAfterLast("/")
                val fileSession = SshFileSession(connection)
                fileSession.start()
                
                _transferProgress.value = TransferState(
                    type = TransferType.DOWNLOAD,
                    fileName = fileName,
                    progress = TransferProgress(0, 0, 0)
                )
                
                val result = fileSession.download(context, remotePath, localUri) { progress ->
                    _transferProgress.value = TransferState(
                        type = TransferType.DOWNLOAD,
                        fileName = fileName,
                        progress = progress
                    )
                }
                
                fileSession.close()
                _transferProgress.value = null
                
                if (result.isFailure) {
                    _error.emit(SshError.TransferFailed(result.exceptionOrNull()?.message ?: "下载失败"))
                }
                
            } catch (e: Exception) {
                _transferProgress.value = null
                _error.emit(SshError.TransferFailed(e.message ?: "下载异常"))
            }
        }
    }
    
    // ==================== 服务发现 ====================
    
    /**
     * 开始发现SSH服务
     */
    fun startDiscovery() {
        scope.launch {
            try {
                discoveryService.start()
                discoveryService.discoverService(NetworkDiscoveryService.SERVICE_SSH)
                discoveryService.discoverService(NetworkDiscoveryService.SERVICE_SFTP)
                Log.i(TAG, "SSH服务发现已启动")
            } catch (e: Exception) {
                Log.e(TAG, "启动服务发现失败", e)
            }
        }
    }
    
    /**
     * 停止服务发现
     */
    fun stopDiscovery() {
        scope.launch {
            try {
                discoveryService.stop()
                Log.i(TAG, "SSH服务发现已停止")
            } catch (e: Exception) {
                Log.e(TAG, "停止服务发现失败", e)
            }
        }
    }
    
    /**
     * 刷新服务发现
     */
    fun refreshDiscovery() {
        scope.launch {
            try {
                discoveryService.refresh()
            } catch (e: Exception) {
                Log.e(TAG, "刷新服务发现失败", e)
            }
        }
    }
    
    /**
     * 从发现的服务创建配置
     */
    fun createConfigFromDiscoveredService(
        service: NetworkDiscoveryService.DiscoveredService,
        username: String,
        password: String
    ): SshConfig {
        return SshConfig(
            name = service.name,
            host = service.primaryAddress,
            port = service.port,
            username = username,
            authMethod = SshConfig.AuthMethod.Password(password)
        )
    }
    
    // ==================== 清理 ====================
    
    /**
     * 清理资源
     */
    fun cleanup() {
        scope.launch {
            _currentSession.value?.close()
            _currentSession.value = null
            _currentTerminalSession.value = null
            _currentFileSystem.value?.close()
            _currentFileSystem.value = null
            
            // 切换回本地文件系统
            if (ActiveFileSystemManager.isUsingRemote()) {
                ActiveFileSystemManager.switchToLocal()
            }
            
            connectionPool.shutdown()
            discoveryService.stop()
            fileCache?.close()
        }
    }
}

/**
 * SSH错误类型
 */
sealed class SshError {
    abstract val message: String
    
    data class ConnectionFailed(
        val host: String,
        override val message: String
    ) : SshError()
    
    data class SessionFailed(override val message: String) : SshError()
    
    data class TransferFailed(override val message: String) : SshError()
    
    data class AuthenticationFailed(override val message: String) : SshError()
}

/**
 * 传输状态
 */
data class TransferState(
    val type: TransferType,
    val fileName: String,
    val progress: TransferProgress
)

/**
 * 传输类型
 */
enum class TransferType {
    UPLOAD,
    DOWNLOAD
}
