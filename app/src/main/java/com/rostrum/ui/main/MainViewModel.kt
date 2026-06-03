package com.rostrum.ui.main

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rostrum.core.config.ConfigManagerImpl
import com.rostrum.core.config.SortBy
import com.rostrum.core.di.ShellManagerFactory
import com.rostrum.core.filesystem.ActiveFileSystemManager
import com.rostrum.core.plugin.models.FileInfo
import com.rostrum.core.shell.IShellSession
import com.rostrum.core.ssh.filesystem.SshFileSystem
import com.rostrum.core.util.FileSortUtils
import com.rostrum.core.util.FileUtils
import com.rostrum.core.util.RestrictedPathHelper
import com.rostrum.core.util.SafAccessManager
import com.rostrum.core.util.SortOrder
import com.rostrum.core.util.SortType
import com.rostrum.core.domain.model.Bookmark
import com.rostrum.core.domain.model.FileItem
import com.rostrum.ui.main.viewmodel.BookmarkViewModel
import com.rostrum.ui.main.viewmodel.FileOperationsViewModel
import com.rostrum.ui.main.viewmodel.ShellViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 主界面 ViewModel（协调器）
 * 
 * 职责：
 * - 文件列表管理和导航
 * - 文件选择状态管理
 * - 预览窗口绑定
 * - 协调子 ViewModel
 * 
 * 委托职责：
 * - Shell 管理 → ShellViewModel
 * - 书签管理 → BookmarkViewModel
 * - 文件操作 → FileOperationsViewModel
 * 
 * 通过 Hilt 注入：
 * - BookmarkViewModel（单例，书签管理）
 * - ShellManagerFactory（用于创建 ShellManager）
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val bookmarkDelegate: BookmarkViewModel,
    shellManagerFactory: ShellManagerFactory
) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "MainViewModel"
    }
    
    // ==================== 子 ViewModel 委托 ====================
    
    private val shellDelegate = ShellViewModel(
        shellManager = shellManagerFactory.create(viewModelScope),
        scope = viewModelScope
    )
    private val fileOpsDelegate = FileOperationsViewModel(viewModelScope)
    private val remoteListMutex = Mutex()
    
    // ==================== Shell 相关（委托到 ShellViewModel）====================
    
    /** Shell管理器（公开以供终端屏幕使用） */
    val shellManager get() = shellDelegate.shellManager
    
    /** Terminal Shell Session */
    val shellSession: StateFlow<IShellSession> get() = shellDelegate.shellSession
    
    
    /** 当前 Shell 模式状态 */
    val shellMode: StateFlow<ShellViewModel.ShellMode> get() = shellDelegate.shellMode
    
    /** 切换 Shell 模式 */
    fun switchShellMode(
        mode: ShellViewModel.ShellMode, 
        onSwitched: ((IShellSession) -> Unit)? = null
    ) = shellDelegate.switchShellMode(mode, onSwitched)
    
    /** 执行 Python 脚本 */
    fun executePythonScript(
        scriptPath: String,
        args: List<String> = emptyList(),
        onStarted: ((IShellSession) -> Unit)? = null
    ) = shellDelegate.executePythonScript(scriptPath, args, onStarted)
    
    fun onTerminalTabSelected() {
        shellDelegate.startSessionIfNeeded()
        val path = if (lastActiveIsLeft) leftCurrentPath else rightCurrentPath
        shellDelegate.cdAndList(path)
    }
    
    // ==================== 书签相关（委托到 BookmarkViewModel）====================
    
    /** 书签列表 */
    val bookmarks: List<Bookmark> get() = bookmarkDelegate.bookmarks
    
    /** 添加书签 */
    fun addBookmark(fileItem: FileItem) = bookmarkDelegate.addBookmark(fileItem)
    
    /** 添加当前路径为书签 */
    fun addCurrentPathAsBookmark(isLeft: Boolean) {
        val path = if (isLeft) leftCurrentPath else rightCurrentPath
        bookmarkDelegate.addPathAsBookmark(path)
    }
    
    /** 删除书签 */
    fun removeBookmark(path: String) = bookmarkDelegate.removeBookmark(path)
    
    /** 更新书签 */
    fun updateBookmark(oldPath: String, newName: String, newPath: String) = 
        bookmarkDelegate.updateBookmark(oldPath, newName, newPath)
    
    // ==================== 文件操作进度（委托到 FileOperationsViewModel）====================
    
    /** 操作进度状态 */
    val isOperationRunning: Boolean get() = fileOpsDelegate.isOperationRunning
    val operationProgress: Float get() = fileOpsDelegate.operationProgress
    val operationStatus: String get() = fileOpsDelegate.operationStatus
    
    // ==================== 文件系统状态 ====================
    
    /** 是否使用远程文件系统（SSH） */
    var isRemoteFileSystem by mutableStateOf(false)
        private set
    
    /** 当前文件系统显示名称 */
    var fileSystemDisplayName by mutableStateOf("本地存储")
        private set
    
    /** 根路径（动态，根据当前文件系统） */
    val rootPath: String
        get() = ActiveFileSystemManager.getCurrentRootPath()
    
    // ==================== 文件列表和路径状态 ====================
    
    // 各区域当前路径
    var leftCurrentPath by mutableStateOf(ActiveFileSystemManager.getCurrentRootPath())
        private set
    
    var rightCurrentPath by mutableStateOf(ActiveFileSystemManager.getCurrentRootPath())
        private set
    
    var bottomCurrentPath by mutableStateOf(ActiveFileSystemManager.getCurrentRootPath())
        private set
    
    // 各区域文件列表
    var leftFileList by mutableStateOf<List<FileItem>>(emptyList())
        private set
    
    var rightFileList by mutableStateOf<List<FileItem>>(emptyList())
        private set
    
    var bottomFileList by mutableStateOf<List<FileItem>>(emptyList())
        private set
    
    // 各区域选中的文件
    var leftSelectedFiles by mutableStateOf<Set<String>>(emptySet())
        private set
    
    var rightSelectedFiles by mutableStateOf<Set<String>>(emptySet())
        private set
    
    var bottomSelectedFiles by mutableStateOf<Set<String>>(emptySet())
        private set
    
    // 待高亮文件路径（用于外部打开时定位）
    var pendingHighlightFile by mutableStateOf<String?>(null)
        private set
    
    // 高亮版本号（每次触发高亮时自增，确保即使同一文件也能重新触发动画）
    var highlightVersion by mutableStateOf(0)
        private set
    
    // 待滚动到的文件索引
    var pendingScrollToIndex by mutableStateOf<Int>(-1)
        private set
    
    // 滚动目标的面板
    var pendingScrollPane by mutableStateOf<PanePosition?>(null)
        private set
    
    // 过滤关键字
    var leftFilterQuery by mutableStateOf("")
        private set
    
    var rightFilterQuery by mutableStateOf("")
        private set
    
    var bottomFilterQuery by mutableStateOf("")
        private set
    
    // 递归搜索模式
    var leftRecursiveSearch by mutableStateOf(false)
        private set
    
    var rightRecursiveSearch by mutableStateOf(false)
        private set
    
    var bottomRecursiveSearch by mutableStateOf(false)
        private set
    
    // 排序设置
    var sortType by mutableStateOf(SortType.NAME)
        private set
    
    var sortOrder by mutableStateOf(SortOrder.ASCENDING)
        private set
    
    // 显示隐藏文件设置
    var showHiddenFiles by mutableStateOf(false)
        private set
    
    // 记录最后一次选中的文件（用于范围选择）
    private var leftLastSelectedPath: String? = null
    private var rightLastSelectedPath: String? = null
    private var bottomLastSelectedPath: String? = null
    
    // 记录最后一次操作的面板
    private var lastActiveIsLeft = true

    // ==================== 预览状态 ====================
    
    var currentViewingFile by mutableStateOf<String?>(null)
        private set
    
    var topLeftViewingFile by mutableStateOf<String?>(null)
        private set
    var topRightViewingFile by mutableStateOf<String?>(null)
        private set
    var bottomViewingFile by mutableStateOf<String?>(null)
        private set

    // ==================== 文件对比 ====================
    
    var showFileComparator by mutableStateOf(false)
        private set
    var compareFileA by mutableStateOf<File?>(null)
        private set
    var compareFileB by mutableStateOf<File?>(null)
        private set
    
    // ==================== 错误/提示状态 ====================
    
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    var pendingSafPermissionRequest by mutableStateOf<SafPermissionRequest?>(null)
        private set
    
    data class SafPermissionRequest(
        val type: SafAccessManager.PermissionType,
        val targetPath: String,
        val pane: PanePosition
    )
    
    // ==================== 快捷地址 ====================
    
    /** 快捷地址列表 */
    var quickAccessPaths by mutableStateOf<List<com.rostrum.core.config.QuickAccessPath>>(emptyList())
        private set
    
    /** 默认打开路径 */
    var defaultOpenPath by mutableStateOf("")
        private set
    
    // ==================== 终端上下文 ====================
    
    /** 终端工作目录 */
    var terminalWorkingDir by mutableStateOf("")
        internal set
    
    // ==================== 初始化 ====================
    
    init {
        // 从配置加载文件管理器设置
        viewModelScope.launch {
            try {
                val configManager = ConfigManagerImpl.getInstance(getApplication())
                val appConfig = configManager.getAppConfig()
                
                // 应用文件管理器配置
                showHiddenFiles = appConfig.fileManager.showHiddenFiles
                sortType = when (appConfig.fileManager.sortBy) {
                    SortBy.NAME -> SortType.NAME
                    SortBy.SIZE -> SortType.SIZE
                    SortBy.DATE -> SortType.DATE
                    SortBy.TYPE -> SortType.TYPE
                }
                sortOrder = when (appConfig.fileManager.sortOrder) {
                    com.rostrum.core.config.SortOrder.ASCENDING -> SortOrder.ASCENDING
                    com.rostrum.core.config.SortOrder.DESCENDING -> SortOrder.DESCENDING
                }
                
                // 加载快捷地址和默认路径
                quickAccessPaths = appConfig.fileManager.quickAccessPaths
                defaultOpenPath = appConfig.fileManager.defaultOpenPath
                
                // 应用默认打开路径
                val initialPath = if (defaultOpenPath.isNotBlank() && File(defaultOpenPath).exists()) {
                    defaultOpenPath
                } else {
                    ActiveFileSystemManager.getCurrentRootPath()
                }
                
                leftCurrentPath = initialPath
                rightCurrentPath = initialPath
                
                Log.d(TAG, "Loaded file manager config: showHidden=$showHiddenFiles, defaultPath=$defaultOpenPath, initialPath=$initialPath")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load file manager config", e)
            }
            
        }
        
        loadFileList(true)
        loadFileList(false)
        
        // 监听文件系统切换事件
        viewModelScope.launch {
            ActiveFileSystemManager.fileSystemChanged.collect {
                onFileSystemChanged()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        shellDelegate.destroy()
    }
    
    // ==================== 文件系统切换 ====================
    
    /**
     * 切换到SSH文件系统
     * 
     * @param sshFileSystem SSH文件系统实例
     * @param rootPath SSH服务器根路径
     * @param host SSH主机地址
     * @param user SSH用户名
     */
    fun switchToSshFileSystem(
        sshFileSystem: SshFileSystem,
        rootPath: String,
        host: String? = null,
        user: String? = null
    ) {
        Log.i(TAG, "切换到SSH文件系统: $rootPath")
        ActiveFileSystemManager.switchToSsh(sshFileSystem, rootPath, host, user)
    }
    
    /**
     * 切换到本地文件系统
     */
    fun switchToLocalFileSystem() {
        Log.i(TAG, "切换到本地文件系统")
        ActiveFileSystemManager.switchToLocal()
    }
    
    /**
     * 文件系统变更时的处理
     */
    private fun onFileSystemChanged() {
        isRemoteFileSystem = ActiveFileSystemManager.isUsingRemote()
        fileSystemDisplayName = ActiveFileSystemManager.getDisplayName()
        
        val newRootPath = ActiveFileSystemManager.getCurrentRootPath()
        
        // 重置所有窗口的路径到新的根路径
        leftCurrentPath = newRootPath
        rightCurrentPath = newRootPath
        bottomCurrentPath = newRootPath
        
        // 清除选择状态
        leftSelectedFiles = emptySet()
        rightSelectedFiles = emptySet()
        bottomSelectedFiles = emptySet()
        
        // 清除过滤器
        leftFilterQuery = ""
        rightFilterQuery = ""
        bottomFilterQuery = ""
        
        // 重新加载文件列表
        loadFileList(true)
        loadFileList(false)
        loadFileListForPane(PanePosition.BOTTOM)
        
        Log.i(TAG, "文件系统已切换到: $fileSystemDisplayName, 根路径: $newRootPath")
    }
    
    /**
     * 检查当前是否使用远程文件系统
     */
    fun isUsingRemoteFileSystem(): Boolean = ActiveFileSystemManager.isUsingRemote()
    
    // ==================== 公共方法：错误处理 ====================
    
    fun clearErrorMessage() { errorMessage = null }
    fun clearSafPermissionRequest() { pendingSafPermissionRequest = null }
    
    fun handleSafPermissionResult(success: Boolean) {
        val request = pendingSafPermissionRequest
        if (request != null && success) {
            navigateToPaneInternal(request.targetPath, request.pane)
        }
        pendingSafPermissionRequest = null
    }
    
    // ==================== 公共方法：文件列表 ====================
    
    fun loadFileList(isLeft: Boolean) {
        loadFileListForPane(if (isLeft) PanePosition.TOP_LEFT else PanePosition.TOP_RIGHT)
    }
    
    fun loadFileListForPane(pane: PanePosition) {
        viewModelScope.launch {
            val (path, filter, recursive) = when (pane) {
                PanePosition.TOP_LEFT -> Triple(leftCurrentPath, leftFilterQuery, leftRecursiveSearch)
                PanePosition.TOP_RIGHT -> Triple(rightCurrentPath, rightFilterQuery, rightRecursiveSearch)
                PanePosition.BOTTOM -> Triple(bottomCurrentPath, bottomFilterQuery, bottomRecursiveSearch)
            }
            
            val files = withContext(Dispatchers.IO) {
                if (ActiveFileSystemManager.isUsingRemote()) {
                    // 远程文件系统
                    getRemoteFileList(path, filter)
                } else if (recursive && filter.isNotEmpty()) {
                    getFileListRecursive(path, filter)
                } else {
                    getFileList(path, filter)
                }
            }
            
            when (pane) {
                PanePosition.TOP_LEFT -> leftFileList = files
                PanePosition.TOP_RIGHT -> rightFileList = files
                PanePosition.BOTTOM -> bottomFileList = files
            }
        }
    }
    
    // ==================== 公共方法：导航 ====================
    
    fun navigateTo(path: String, isLeft: Boolean) {
        navigateToPane(path, if (isLeft) PanePosition.TOP_LEFT else PanePosition.TOP_RIGHT)
    }
    
    fun navigateToPane(path: String, pane: PanePosition) {
        val normalizedPathForCheck = path.replace("/sdcard/", "/storage/emulated/0/")
        val isRestrictedPath = RestrictedPathHelper.isRestrictedPath(path) ||
                               normalizedPathForCheck.contains("/Android/data") ||
                               normalizedPathForCheck.contains("/Android/obb")
        
        if (isRestrictedPath) {
            Log.d(TAG, "navigateToPane: Restricted path detected, allowing access with zero-width space method for: $path")
        }
        
        // 记录导航历史
        viewModelScope.launch {
            val file = java.io.File(path)
            com.rostrum.ui.history.FileHistoryManager.addFileAccess(
                path = path,
                name = file.name,
                isDirectory = file.isDirectory
            )
        }
        
        navigateToPaneInternal(path, pane)
    }
    
    private fun navigateToPaneInternal(path: String, pane: PanePosition) {
        when (pane) {
            PanePosition.TOP_LEFT -> {
                lastActiveIsLeft = true
                leftCurrentPath = path
                leftSelectedFiles = emptySet()
                leftFilterQuery = ""
                leftRecursiveSearch = false
            }
            PanePosition.TOP_RIGHT -> {
                lastActiveIsLeft = false
                rightCurrentPath = path
                rightSelectedFiles = emptySet()
                rightFilterQuery = ""
                rightRecursiveSearch = false
            }
            PanePosition.BOTTOM -> {
                bottomCurrentPath = path
                bottomSelectedFiles = emptySet()
                bottomFilterQuery = ""
                bottomRecursiveSearch = false
            }
        }
        loadFileListForPane(pane)
    }
    
    /**
     * 导航到文件所在目录并高亮选中该文件
     * 类似 MT 文件管理器的"定位"功能
     * 
     * @param filePath 要定位的文件路径
     * @param pane 目标面板
     */
    fun navigateToFileAndHighlight(filePath: String, pane: PanePosition = PanePosition.TOP_LEFT) {
        val file = java.io.File(filePath)
        // 对目录本身，定位到该目录，不高亮
        val isDirectory = file.isDirectory
        val targetDir = if (isDirectory) filePath else (file.parent ?: return)
        
        // 设置待高亮文件，自增版本号确保每次都触发动画
        pendingHighlightFile = if (isDirectory) null else filePath
        pendingScrollPane = pane
        highlightVersion++
        
        // 导航到目标目录并直接加载文件列表
        viewModelScope.launch {
            // 设置路径（不通过 navigateToPaneInternal 避免清空选中状态）
            when (pane) {
                PanePosition.TOP_LEFT -> {
                    lastActiveIsLeft = true
                    leftCurrentPath = targetDir
                    leftFilterQuery = ""
                    leftRecursiveSearch = false
                }
                PanePosition.TOP_RIGHT -> {
                    lastActiveIsLeft = false
                    rightCurrentPath = targetDir
                    rightFilterQuery = ""
                    rightRecursiveSearch = false
                }
                PanePosition.BOTTOM -> {
                    bottomCurrentPath = targetDir
                    bottomFilterQuery = ""
                    bottomRecursiveSearch = false
                }
            }
            
            // 同步加载文件列表（始终刷新，即使已在同一目录也要重新加载以确保最新状态）
            val files = withContext(Dispatchers.IO) {
                if (ActiveFileSystemManager.isUsingRemote()) {
                    getRemoteFileList(targetDir, "")
                } else {
                    getFileList(targetDir, "")
                }
            }
            
            // 更新文件列表
            when (pane) {
                PanePosition.TOP_LEFT -> leftFileList = files
                PanePosition.TOP_RIGHT -> rightFileList = files
                PanePosition.BOTTOM -> bottomFileList = files
            }
            
            // 如果不是目录，设置选中和滚动
            if (!isDirectory) {
                val targetIndex = files.indexOfFirst { it.path == filePath }
                if (targetIndex >= 0) {
                    when (pane) {
                        PanePosition.TOP_LEFT -> leftSelectedFiles = setOf(filePath)
                        PanePosition.TOP_RIGHT -> rightSelectedFiles = setOf(filePath)
                        PanePosition.BOTTOM -> bottomSelectedFiles = setOf(filePath)
                    }
                    pendingScrollToIndex = targetIndex
                    Log.d(TAG, "navigateToFileAndHighlight: found file at index $targetIndex")
                } else {
                    Log.w(TAG, "navigateToFileAndHighlight: file not found in list: $filePath")
                    clearPendingScroll()
                }
            } else {
                clearPendingScroll()
            }
        }
    }
    
    /**
     * 清除滚动状态（UI 滚动完成后调用）
     * 注意：不清除 pendingHighlightFile，由高亮动画完成后或用户交互时清除
     */
    fun clearPendingScroll() {
        pendingScrollToIndex = -1
        pendingScrollPane = null
    }
    
    /**
     * 清除文件高亮状态（用户交互后调用）
     */
    fun clearHighlight() {
        pendingHighlightFile = null
    }
    
    fun navigateUp(isLeft: Boolean) {
        navigateUpForPane(if (isLeft) PanePosition.TOP_LEFT else PanePosition.TOP_RIGHT)
    }
    
    fun navigateUpForPane(pane: PanePosition) {
        val currentPath = when (pane) {
            PanePosition.TOP_LEFT -> leftCurrentPath
            PanePosition.TOP_RIGHT -> rightCurrentPath
            PanePosition.BOTTOM -> bottomCurrentPath
        }
        
        // 检查是否已经在根路径
        val rootPath = ActiveFileSystemManager.getCurrentRootPath()
        if (currentPath == rootPath || currentPath == "/" || currentPath.trimEnd('/').isEmpty()) {
            return
        }
        
        // 计算父路径（支持远程路径）
        val parent = getParentPath(currentPath) ?: rootPath
        navigateToPane(parent, pane)
    }
    
    /**
     * 获取父目录路径
     */
    private fun getParentPath(path: String): String? {
        val normalizedPath = path.trimEnd('/')
        val lastSeparator = normalizedPath.lastIndexOf('/')
        return when {
            lastSeparator <= 0 -> "/"
            else -> normalizedPath.substring(0, lastSeparator)
        }
    }
    
    fun syncPath(fromLeft: Boolean) {
        if (fromLeft) {
            rightCurrentPath = leftCurrentPath
            loadFileList(false)
        } else {
            leftCurrentPath = rightCurrentPath
            loadFileList(true)
        }
    }
    
    // ==================== 公共方法：过滤 ====================
    
    fun updateFilter(query: String, isLeft: Boolean) {
        updateFilterForPane(query, if (isLeft) PanePosition.TOP_LEFT else PanePosition.TOP_RIGHT)
    }
    
    fun updateFilterForPane(query: String, pane: PanePosition, recursive: Boolean = false) {
        when (pane) {
            PanePosition.TOP_LEFT -> {
                leftFilterQuery = query
                leftRecursiveSearch = recursive
            }
            PanePosition.TOP_RIGHT -> {
                rightFilterQuery = query
                rightRecursiveSearch = recursive
            }
            PanePosition.BOTTOM -> {
                bottomFilterQuery = query
                bottomRecursiveSearch = recursive
            }
        }
        loadFileListForPane(pane)
    }
    
    fun isRecursiveSearchEnabled(pane: PanePosition): Boolean = when (pane) {
        PanePosition.TOP_LEFT -> leftRecursiveSearch
        PanePosition.TOP_RIGHT -> rightRecursiveSearch
        PanePosition.BOTTOM -> bottomRecursiveSearch
    }
    
    // ==================== 公共方法：选择 ====================
    
    fun onFileSwiped(path: String, isLeft: Boolean) {
        lastActiveIsLeft = isLeft
        onFileSwipedForPane(path, if (isLeft) PanePosition.TOP_LEFT else PanePosition.TOP_RIGHT)
    }
    
    fun onFileSwipedForPane(path: String, pane: PanePosition) {
        val selectedSet = getSelectedFilesForPane(pane)
        val lastPath = getLastSelectedPath(pane)
        val list = getFileListForPane(pane)
        
        if (selectedSet.size == 1 && lastPath != null && selectedSet.contains(lastPath) && path != lastPath) {
            val startIndex = list.indexOfFirst { it.path == lastPath }
            val endIndex = list.indexOfFirst { it.path == path }
            
            if (startIndex != -1 && endIndex != -1) {
                val min = minOf(startIndex, endIndex)
                val max = maxOf(startIndex, endIndex)
                val rangePaths = list.subList(min, max + 1).map { it.path }
                
                setSelectedFilesForPane(pane, selectedSet + rangePaths)
                setLastSelectedPath(pane, null)
                return
            }
        }
        
        toggleFileSelectionForPane(path, pane)
    }

    fun toggleFileSelection(path: String, isLeft: Boolean) {
        toggleFileSelectionForPane(path, if (isLeft) PanePosition.TOP_LEFT else PanePosition.TOP_RIGHT)
    }
    
    fun toggleFileSelectionForPane(path: String, pane: PanePosition) {
        val selectedSet = getSelectedFilesForPane(pane)
        
        if (selectedSet.contains(path)) {
            setSelectedFilesForPane(pane, selectedSet - path)
            if (getLastSelectedPath(pane) == path) setLastSelectedPath(pane, null)
        } else {
            setSelectedFilesForPane(pane, selectedSet + path)
            setLastSelectedPath(pane, path)
        }
    }
    
    fun clearSelection(isLeft: Boolean) {
        clearSelectionForPane(if (isLeft) PanePosition.TOP_LEFT else PanePosition.TOP_RIGHT)
    }
    
    fun clearSelectionForPane(pane: PanePosition) {
        setSelectedFilesForPane(pane, emptySet())
    }

    fun selectAll(isLeft: Boolean) {
        selectAllForPane(if (isLeft) PanePosition.TOP_LEFT else PanePosition.TOP_RIGHT)
    }
    
    fun selectAllForPane(pane: PanePosition) {
        val files = getFileListForPane(pane)
        setSelectedFilesForPane(pane, files.map { it.path }.toSet())
    }

    fun invertSelection(isLeft: Boolean) {
        invertSelectionForPane(if (isLeft) PanePosition.TOP_LEFT else PanePosition.TOP_RIGHT)
    }
    
    fun invertSelectionForPane(pane: PanePosition) {
        val files = getFileListForPane(pane)
        val currentSelected = getSelectedFilesForPane(pane)
        val allPaths = files.map { it.path }.toSet()
        setSelectedFilesForPane(pane, allPaths - currentSelected)
    }

    fun selectSameType(targetFile: FileItem, isLeft: Boolean) {
        selectSameTypeForPane(targetFile, if (isLeft) PanePosition.TOP_LEFT else PanePosition.TOP_RIGHT)
    }
    
    fun selectSameTypeForPane(targetFile: FileItem, pane: PanePosition) {
        val files = getFileListForPane(pane)
        val currentSelected = getSelectedFilesForPane(pane)
        
        val pathsToAdd = files.filter { 
            if (targetFile.isDirectory) it.isDirectory
            else !it.isDirectory && it.extension == targetFile.extension
        }.map { it.path }
        
        setSelectedFilesForPane(pane, currentSelected + pathsToAdd)
    }
    
    // ==================== 公共方法：文件操作（委托）====================
    
    fun copyFiles(fromLeft: Boolean, onResult: (Boolean, String?) -> Unit) {
        val selectedPaths = if (fromLeft) leftSelectedFiles else rightSelectedFiles
        val targetPath = if (fromLeft) rightCurrentPath else leftCurrentPath
        
        fileOpsDelegate.copyFiles(selectedPaths, targetPath) { success, message ->
            if (success) loadFileList(!fromLeft)
            onResult(success, message)
        }
    }
    
    fun pasteFiles(targetPath: String, onResult: (Boolean, String?) -> Unit) {
        fileOpsDelegate.pasteFiles(targetPath, onResult)
    }
    
    fun moveFiles(fromLeft: Boolean, onResult: (Boolean, String?) -> Unit) {
        val selectedPaths = if (fromLeft) leftSelectedFiles else rightSelectedFiles
        val targetPath = if (fromLeft) rightCurrentPath else leftCurrentPath
        
        fileOpsDelegate.moveFiles(selectedPaths, targetPath) { success, message ->
            if (success) {
                loadFileList(fromLeft)
                loadFileList(!fromLeft)
                clearSelection(fromLeft)
            }
            onResult(success, message)
        }
    }
    
    fun moveFileToFolder(sourcePath: String, targetFolderPath: String, onResult: (Boolean, String?) -> Unit) {
        fileOpsDelegate.moveFileToFolder(sourcePath, targetFolderPath, onResult)
    }
    
    fun deleteFiles(isLeft: Boolean, onResult: (Boolean, String?) -> Unit) {
        val selectedPaths = if (isLeft) leftSelectedFiles else rightSelectedFiles
        
        fileOpsDelegate.deleteFiles(selectedPaths) { success, message ->
            if (success) {
                loadFileList(isLeft)
                clearSelection(isLeft)
            }
            onResult(success, message)
        }
    }
    
    fun deleteFilesForPane(panePosition: PanePosition, onResult: (Boolean, String?) -> Unit) {
        val selectedPaths = getSelectedFilesForPane(panePosition)
        
        fileOpsDelegate.deleteFiles(selectedPaths) { success, message ->
            if (success) {
                loadFileListForPane(panePosition)
                clearSelectionForPane(panePosition)
            }
            onResult(success, message)
        }
    }
    
    fun renameFile(filePath: String, newName: String, isLeft: Boolean, onResult: (Boolean, String?) -> Unit) {
        fileOpsDelegate.renameFile(filePath, newName) { success, message ->
            if (success) loadFileList(isLeft)
            onResult(success, message)
        }
    }
    
    fun batchRenameFiles(isLeft: Boolean, renames: List<Pair<String, String>>, onResult: (Boolean, String?) -> Unit) {
        fileOpsDelegate.batchRenameFiles(renames) { success, message ->
            if (success) {
                loadFileList(isLeft)
                clearSelection(isLeft)
            }
            onResult(success, message)
        }
    }
    
    fun createDirectory(folderName: String, isLeft: Boolean, onResult: (Boolean, String?) -> Unit) {
        val currentPath = if (isLeft) leftCurrentPath else rightCurrentPath
        
        fileOpsDelegate.createDirectory(currentPath, folderName) { success, message ->
            if (success) loadFileList(isLeft)
            onResult(success, message)
        }
    }
    
    fun createFolder(path: String, folderName: String, onResult: (Boolean, String?) -> Unit) {
        fileOpsDelegate.createDirectory(path, folderName, onResult)
    }
    
    fun createFile(fileName: String, isLeft: Boolean, onResult: (Boolean, String?) -> Unit) {
        val currentPath = if (isLeft) leftCurrentPath else rightCurrentPath
        
        fileOpsDelegate.createFile(currentPath, fileName) { success, message ->
            if (success) loadFileList(isLeft)
            onResult(success, message)
        }
    }
    
    fun createFile(path: String, fileName: String, onResult: (Boolean, String?) -> Unit) {
        fileOpsDelegate.createFile(path, fileName, onResult)
    }
    
    fun saveFileContent(filePath: String, content: String, onResult: (Boolean, String?) -> Unit) {
        fileOpsDelegate.saveFileContent(filePath, content) { success, message ->
            if (success) {
                loadFileList(true)
                loadFileList(false)
            }
            onResult(success, message)
        }
    }
    
    fun zipFiles(isLeft: Boolean, zipName: String, onResult: (Boolean, String?) -> Unit) {
        val selectedPaths = if (isLeft) leftSelectedFiles else rightSelectedFiles
        val currentPath = if (isLeft) leftCurrentPath else rightCurrentPath
        
        fileOpsDelegate.zipFiles(selectedPaths, currentPath, zipName) { success, message ->
            if (success) {
                loadFileList(isLeft)
                clearSelection(isLeft)
            }
            onResult(success, message)
        }
    }
    
    fun compressFiles(filePaths: List<String>, targetDir: String, zipName: String, onResult: (Boolean, String?) -> Unit) {
        fileOpsDelegate.compressFiles(filePaths, targetDir, zipName, onResult)
    }
    
    fun unzipFile(filePath: String, isLeft: Boolean, onResult: (Boolean, String?) -> Unit) {
        val currentPath = if (isLeft) leftCurrentPath else rightCurrentPath
        
        fileOpsDelegate.unzipFile(filePath, currentPath) { success, message ->
            if (success) loadFileList(isLeft)
            onResult(success, message)
        }
    }
    
    // ==================== 公共方法：排序 ====================
    
    fun updateSortType(type: SortType) {
        sortType = type
        loadFileList(true)
        loadFileList(false)
    }
    
    fun toggleSortOrder() {
        sortOrder = if (sortOrder == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
        loadFileList(true)
        loadFileList(false)
    }
    
    fun updateShowHiddenFiles(show: Boolean) {
        showHiddenFiles = show
        loadFileList(true)
        loadFileList(false)
    }
    
    // ==================== 公共方法：预览 ====================
    
    fun openFileForViewing(filePath: String) {
        currentViewingFile = filePath
        
        // 记录文件查看历史
        viewModelScope.launch {
            val file = java.io.File(filePath)
            if (file.exists() && file.isFile) {
                com.rostrum.ui.history.FileHistoryManager.addFileAccess(
                    path = filePath,
                    name = file.name,
                    isDirectory = false
                )
            }
        }
    }
    
    fun openFileForViewingInPane(filePath: String, pane: PanePosition) {
        when (pane) {
            PanePosition.TOP_LEFT -> topLeftViewingFile = filePath
            PanePosition.TOP_RIGHT -> topRightViewingFile = filePath
            PanePosition.BOTTOM -> bottomViewingFile = filePath
        }
        currentViewingFile = filePath
    }
    
    fun clearViewingFileForPane(pane: PanePosition) {
        when (pane) {
            PanePosition.TOP_LEFT -> topLeftViewingFile = null
            PanePosition.TOP_RIGHT -> topRightViewingFile = null
            PanePosition.BOTTOM -> bottomViewingFile = null
        }
    }
    
    fun getViewingFileForPane(pane: PanePosition): String? = when (pane) {
        PanePosition.TOP_LEFT -> topLeftViewingFile
        PanePosition.TOP_RIGHT -> topRightViewingFile
        PanePosition.BOTTOM -> bottomViewingFile
    }
    
    fun handleFilePreviewWithBinding(
        filePath: String,
        sourcePane: PanePosition,
        availablePreviewPanes: List<PanePosition>
    ): PanePosition? {
        if (availablePreviewPanes.isEmpty()) {
            currentViewingFile = filePath
            return null
        }
        
        var targetPane = PaneBindingManager.getBoundPreviewPane(sourcePane)
        
        if (targetPane == null || !availablePreviewPanes.contains(targetPane)) {
            targetPane = PaneBindingManager.findAvailablePreviewPane(sourcePane, availablePreviewPanes)
            if (targetPane != null) {
                PaneBindingManager.bind(sourcePane, targetPane)
            }
        }
        
        if (targetPane != null) {
            openFileForViewingInPane(filePath, targetPane)
        }
        
        return targetPane
    }
    
    fun handleDragPreview(
        filePath: String,
        sourcePane: PanePosition,
        targetPreviewPane: PanePosition
    ) {
        PaneBindingManager.handleDragPreview(sourcePane, targetPreviewPane)
        openFileForViewingInPane(filePath, targetPreviewPane)
    }

    // ==================== 公共方法：文件对比 ====================
    
    fun openFileComparator() {
        val leftFile = leftSelectedFiles.firstOrNull()?.let { File(it) }
        val rightFile = rightSelectedFiles.firstOrNull()?.let { File(it) }

        if (leftFile != null && rightFile != null 
            && leftFile.exists() && rightFile.exists()
            && !leftFile.isDirectory && !rightFile.isDirectory) {
            compareFileA = leftFile
            compareFileB = rightFile
            showFileComparator = true
        }
    }

    fun closeFileComparator() {
        showFileComparator = false
        compareFileA = null
        compareFileB = null
    }
    
    // ==================== 公共方法：窗格操作 ====================
    
    fun getCurrentPathForPane(pane: PanePosition): String = when (pane) {
        PanePosition.TOP_LEFT -> leftCurrentPath
        PanePosition.TOP_RIGHT -> rightCurrentPath
        PanePosition.BOTTOM -> bottomCurrentPath
    }
    
    fun getSelectedFileItemsForPane(pane: PanePosition): List<FileItem> {
        val selectedPaths = when (pane) {
            PanePosition.TOP_LEFT -> leftSelectedFiles
            PanePosition.TOP_RIGHT -> rightSelectedFiles
            PanePosition.BOTTOM -> bottomSelectedFiles
        }
        val fileList = when (pane) {
            PanePosition.TOP_LEFT -> leftFileList
            PanePosition.TOP_RIGHT -> rightFileList
            PanePosition.BOTTOM -> bottomFileList
        }
        return fileList.filter { it.path in selectedPaths }
    }
    
    fun copyFilesToClipboard(paths: List<String>) {
        fileOpsDelegate.setClipboard(paths, isCut = false)
    }
    
    fun cutFilesToClipboard(paths: List<String>) {
        fileOpsDelegate.setClipboard(paths, isCut = true)
    }
    
    fun pasteFilesFromClipboard(targetPath: String) {
        fileOpsDelegate.pasteFiles(targetPath) { success, message ->
            if (success) {
                loadFileList(true)
                loadFileList(false)
            }
            if (!success && message != null) {
                errorMessage = message
            }
        }
    }
    
    // ==================== Private Helpers: Selection ====================
    
    private fun getSelectedFilesForPane(pane: PanePosition): Set<String> = when (pane) {
        PanePosition.TOP_LEFT -> leftSelectedFiles
        PanePosition.TOP_RIGHT -> rightSelectedFiles
        PanePosition.BOTTOM -> bottomSelectedFiles
    }
    
    private fun setSelectedFilesForPane(pane: PanePosition, files: Set<String>) {
        when (pane) {
            PanePosition.TOP_LEFT -> leftSelectedFiles = files
            PanePosition.TOP_RIGHT -> rightSelectedFiles = files
            PanePosition.BOTTOM -> bottomSelectedFiles = files
        }
    }
    
    private fun getFileListForPane(pane: PanePosition): List<FileItem> = when (pane) {
        PanePosition.TOP_LEFT -> leftFileList
        PanePosition.TOP_RIGHT -> rightFileList
        PanePosition.BOTTOM -> bottomFileList
    }
    
    private fun getLastSelectedPath(pane: PanePosition): String? = when (pane) {
        PanePosition.TOP_LEFT -> leftLastSelectedPath
        PanePosition.TOP_RIGHT -> rightLastSelectedPath
        PanePosition.BOTTOM -> bottomLastSelectedPath
    }
    
    private fun setLastSelectedPath(pane: PanePosition, path: String?) {
        when (pane) {
            PanePosition.TOP_LEFT -> leftLastSelectedPath = path
            PanePosition.TOP_RIGHT -> rightLastSelectedPath = path
            PanePosition.BOTTOM -> bottomLastSelectedPath = path
        }
    }
    
    // ==================== Private Helpers: File List ====================
    
    private fun getFileList(path: String, filter: String = ""): List<FileItem> {
        return try {
            val dir = File(path)
            
            val hasManageStorage = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else false
            
            val isSystemDirectory = path.startsWith("/data") || 
                                    path.startsWith("/system") ||
                                    path.startsWith("/vendor") ||
                                    path.startsWith("/product") ||
                                    path.startsWith("/proc") ||
                                    path.startsWith("/mnt")
            
            val isSystemRoot = path == "/" || path.trimEnd('/').isEmpty()
            
            // ========== 硬编码特殊目录处理 ==========
            // /storage 目录 - 硬编码显示 emulated 和 self
            if (path == "/storage" || path == "/storage/") {
                val hardcodedItems = mutableListOf<FileItem>()
                // emulated 目录
                val emulatedDir = File("/storage/emulated")
                hardcodedItems.add(FileItem(
                    file = emulatedDir,
                    name = "emulated",
                    path = "/storage/emulated",
                    isDirectory = true,
                    size = 0L,
                    lastModified = System.currentTimeMillis(),
                    extension = "",
                    childCount = 1
                ))
                // self 链接（如果存在）
                val selfDir = File("/storage/self")
                if (selfDir.exists()) {
                    hardcodedItems.add(FileItem(
                        file = selfDir,
                        name = "self",
                        path = "/storage/self",
                        isDirectory = true,
                        size = 0L,
                        lastModified = System.currentTimeMillis(),
                        extension = "",
                        childCount = 0
                    ))
                }
                return applyFilterAndSort(hardcodedItems, filter)
            }
            
            // /storage/emulated 目录 - 硬编码显示 0
            if (path == "/storage/emulated" || path == "/storage/emulated/") {
                val hardcodedItems = mutableListOf<FileItem>()
                val zeroDir = File("/storage/emulated/0")
                hardcodedItems.add(FileItem(
                    file = zeroDir,
                    name = "0",
                    path = "/storage/emulated/0",
                    isDirectory = true,
                    size = 0L,
                    lastModified = System.currentTimeMillis(),
                    extension = "",
                    childCount = -1 // 不计算子项
                ))
                return applyFilterAndSort(hardcodedItems, filter)
            }
            // ========== 硬编码特殊目录处理结束 ==========
            
            // 使用 MANAGE_EXTERNAL_STORAGE 权限处理系统目录
            if (hasManageStorage && (isSystemDirectory || isSystemRoot)) {
                tryListWithManageStorage(path, filter)?.let { return it }
            }
            
            // 系统根目录特殊处理
            if (isSystemRoot) {
                tryListSystemRoot(path, filter)?.let { return it }
            }
            
            // 受限目录处理
            val normalizedPathForCheck = path.replace("/sdcard/", "/storage/emulated/0/")
            val isRestrictedPath = RestrictedPathHelper.isRestrictedPath(path) ||
                                   normalizedPathForCheck.contains("/Android/data") ||
                                   normalizedPathForCheck.contains("/Android/obb")
            
            var files = emptyList<FileItem>()
            
            if (isRestrictedPath) {
                files = tryListRestrictedPath(path)
            } else if (dir.exists() && dir.isDirectory) {
                files = listFilesWithZeroWidth(path)
            }
            
            // 应用过滤和排序
            applyFilterAndSort(files, filter)
            
        } catch (e: Exception) {
            Log.e(TAG, "getFileList error", e)
            emptyList()
        }
    }
    
    private fun tryListWithManageStorage(path: String, filter: String): List<FileItem>? {
        return try {
            val fileArray = RestrictedPathHelper.listFilesWithZeroWidthPriority(path)
            val files = fileArray?.mapNotNull { file ->
                try { FileItem(file) } catch (e: Exception) { null }
            } ?: return null
            
            applyFilterAndSort(files, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list with MANAGE_EXTERNAL_STORAGE: $path", e)
            null
        }
    }
    
    private fun tryListSystemRoot(path: String, filter: String): List<FileItem>? {
        val context = getApplication<Application>()
        return when (val result = RestrictedPathHelper.listRestrictedDirectory(path, context)) {
            is RestrictedPathHelper.RestrictedListResult.Success -> {
                applyFilterAndSort(result.files, filter)
            }
            is RestrictedPathHelper.RestrictedListResult.NeedPermission -> {
                errorMessage = result.message
                null
            }
            is RestrictedPathHelper.RestrictedListResult.Error -> {
                // 尝试回退方法
                tryListWithZeroWidthFallback(path, filter)
            }
        }
    }
    
    private fun tryListWithZeroWidthFallback(path: String, filter: String): List<FileItem>? {
        return try {
            val shellFiles = RestrictedPathHelper.listFilesWithZeroWidthPriority(path)
            if (shellFiles != null && shellFiles.isNotEmpty()) {
                val files = shellFiles.mapNotNull { file ->
                    try { FileItem(file) } catch (e: Exception) { null }
                }
                if (files.isNotEmpty()) applyFilterAndSort(files, filter) else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Fallback also failed: ${e.message}")
            errorMessage = "访问系统根目录失败"
            null
        }
    }
    
    private fun tryListRestrictedPath(path: String): List<FileItem> {
        val context = getApplication<Application>()
        return when (val result = RestrictedPathHelper.listRestrictedDirectory(path, context)) {
            is RestrictedPathHelper.RestrictedListResult.Success -> result.files
            is RestrictedPathHelper.RestrictedListResult.NeedPermission -> {
                errorMessage = result.message
                emptyList()
            }
            is RestrictedPathHelper.RestrictedListResult.Error -> {
                errorMessage = result.message
                emptyList()
            }
        }
    }
    
    private fun listFilesWithZeroWidth(path: String): List<FileItem> {
        val fileArray = RestrictedPathHelper.listFilesWithZeroWidthPriority(path)
        return fileArray?.mapNotNull { file ->
            try { FileItem(file) } catch (e: Exception) { null }
        } ?: emptyList()
    }
    
    private fun applyFilterAndSort(files: List<FileItem>, filter: String): List<FileItem> {
        var result = files
        
        // 过滤隐藏文件（如果设置不显示隐藏文件）
        if (!showHiddenFiles) {
            result = result.filter { !it.name.startsWith(".") }
        }
        
        if (filter.isNotEmpty()) {
            val isInverse = filter.startsWith("!")
            val actualFilter = if (isInverse) filter.substring(1) else filter
            
            if (actualFilter.isNotEmpty()) {
                result = try {
                    val regex = Regex(actualFilter, RegexOption.IGNORE_CASE)
                    result.filter { 
                        val match = regex.containsMatchIn(it.name)
                        if (isInverse) !match else match
                    }
                } catch (e: Exception) {
                    result.filter { 
                        val match = it.name.contains(actualFilter, ignoreCase = true)
                        if (isInverse) !match else match
                    }
                }
            }
        }
        
        return try {
            if (result.isNotEmpty()) {
                FileSortUtils.sortFilesWithCollator(result, sortType, sortOrder)
            } else emptyList()
        } catch (e: Exception) {
            result.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name })
        }
    }
    
    private fun getFileListRecursive(path: String, filter: String, maxResults: Int = 500): List<FileItem> {
        if (filter.isEmpty()) return getFileList(path, "")
        
        val results = mutableListOf<FileItem>()
        val isInverse = filter.startsWith("!")
        val actualFilter = if (isInverse) filter.substring(1) else filter
        
        if (actualFilter.isEmpty()) return getFileList(path, "")
        
        val regex = try { Regex(actualFilter, RegexOption.IGNORE_CASE) } catch (e: Exception) { null }
        
        fun searchDir(dir: File) {
            if (results.size >= maxResults) return
            
            try {
                var files = RestrictedPathHelper.listFilesWithZeroWidthPriority(dir.absolutePath)?.toList()
                
                if (files.isNullOrEmpty() && RestrictedPathHelper.isRestrictedPath(dir.absolutePath)) {
                    val result = RestrictedPathHelper.listRestrictedDirectory(dir.absolutePath)
                    if (result is RestrictedPathHelper.RestrictedListResult.Success) {
                        files = result.files.mapNotNull { it.file }
                    }
                }
                
                if (files == null) return
                
                for (file in files) {
                    if (results.size >= maxResults) break
                    
                    val matches = if (regex != null) {
                        regex.containsMatchIn(file.name)
                    } else {
                        file.name.contains(actualFilter, ignoreCase = true)
                    }
                    
                    val shouldInclude = if (isInverse) !matches else matches
                    
                    if (shouldInclude) {
                        try { results.add(FileItem(file)) } catch (e: Exception) { }
                    }
                    
                    if (file.isDirectory) searchDir(file)
                }
            } catch (e: Exception) { }
        }
        
        val startDir = File(path)
        if (startDir.exists() && startDir.isDirectory) searchDir(startDir)
        
        return try {
            if (results.isNotEmpty()) {
                FileSortUtils.sortFilesWithCollator(results, sortType, sortOrder)
            } else emptyList()
        } catch (e: Exception) {
            results.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name })
        }
    }
    
    // ==================== 远程文件系统支持 ====================
    
    /**
     * 获取远程文件列表
     */
    private suspend fun getRemoteFileList(path: String, filter: String = ""): List<FileItem> {
        return remoteListMutex.withLock {
            Log.d(TAG, "获取远程文件列表: path=$path")
            val first = loadRemoteFileListOnce(path, filter)
            if (first != null) return@withLock first

            delay(250)
            Log.w(TAG, "远程文件列表首次失败，重试: path=$path")
            val retry = loadRemoteFileListOnce(path, filter)
            if (retry != null) return@withLock retry

            errorMessage = "获取远程文件列表失败: $path"
            emptyList()
        }
    }

    private suspend fun loadRemoteFileListOnce(path: String, filter: String): List<FileItem>? {
        return try {
            val fileSystem = ActiveFileSystemManager.getActiveBackend()
            val result = fileSystem.list(path)

            if (result.isFailure) {
                val error = result.exceptionOrNull()
                Log.w(TAG, "获取远程文件列表失败，等待可能重试: ${error?.message}", error)
                return null
            }

            val fileInfoList = result.getOrThrow()
            Log.d(TAG, "获取到 ${fileInfoList.size} 个文件/目录")
            val files = fileInfoList.map { fileInfoToFileItem(it) }
            applyFilterAndSort(files, filter)
        } catch (e: Exception) {
            Log.w(TAG, "获取远程文件列表异常，等待可能重试: path=$path", e)
            null
        }
    }
    
    /**
     * 将 FileInfo 转换为 FileItem
     */
    private fun fileInfoToFileItem(fileInfo: FileInfo): FileItem {
        return FileItem(
            file = null,  // 远程文件没有本地 File 对象
            name = fileInfo.name,
            path = fileInfo.path,
            isDirectory = fileInfo.isDirectory,
            size = fileInfo.size,
            lastModified = fileInfo.lastModified,
            extension = if (fileInfo.extension.startsWith(".")) {
                fileInfo.extension.substring(1)
            } else {
                fileInfo.extension
            },
            childCount = -1,  // -1 表示远程文件，不显示项数
            permissions = fileInfo.permissions,
            mimeType = fileInfo.mimeType
        )
    }
}
