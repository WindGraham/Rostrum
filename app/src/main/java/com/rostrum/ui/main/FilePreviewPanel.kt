package com.rostrum.ui.main

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rostrum.core.filesystem.ActiveFileSystemManager
import com.rostrum.core.filesystem.FileSystemBackend
import com.rostrum.core.plugin.models.FileInfo
import com.rostrum.core.plugin.preview.PluginStateManager
import com.rostrum.core.plugin.preview.PreviewPluginRegistry
import com.rostrum.core.plugin.providers.PreviewResult
import com.rostrum.plugins.marketplace.preview.html.LocalOpenRealtimeEditor
import com.rostrum.plugins.marketplace.preview.html.LocalOpenInEditor
import com.rostrum.plugins.marketplace.preview.html.LocalCurrentPanePosition
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 文件预览面板 - 统一使用插件系统
 * 
 * 功能：
 * - 通过插件系统预览文件（本地和远程）
 * - 文件编辑（可编辑的文本文件）
 * - 美观的空状态显示
 * - 禁用插件 = 无法预览
 */
@Composable
fun FilePreviewPanel(
    filePath: String?,
    panePosition: PanePosition? = null,
    onSaveFile: (String, String) -> Unit,
    onUnbind: (() -> Unit)? = null,
    onOpenRealtimeEditor: ((String) -> Unit)? = null,
    onOpenInEditor: ((String, PanePosition) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isEditMode by remember { mutableStateOf(false) }
    var file by remember { mutableStateOf<File?>(null) }
    var pluginsInitialized by remember { mutableStateOf(false) }
    val backendState by ActiveFileSystemManager.backendState.collectAsState()
    
    // 获取绑定状态
    val boundFilePane = panePosition?.let { PaneBindingManager.getBoundFilePane(it) }
    val bindingVersion = PaneBindingManager.bindingVersion // 强制重组
    
    // 初始化插件系统
    LaunchedEffect(Unit) {
        PreviewPluginRegistry.initializePlugins(context)
        pluginsInitialized = true
    }
    
    // 是否为远程文件系统 - 使用 collectAsState 订阅 StateFlow，确保在文件系统切换时自动更新
    val isRemote by ActiveFileSystemManager.isRemote.collectAsState()
    
    // 监测文件变化
    LaunchedEffect(filePath, isRemote) {
        // 如果文件路径从非空变为 null，清理预览缓存
        val previousPath = file?.absolutePath
        if (previousPath != null && filePath.isNullOrBlank()) {
            PreviewCacheManager.remove(previousPath)
            android.util.Log.d("FilePreviewPanel", "Cleared preview cache for previous file: $previousPath")
        }
        
        isEditMode = false
        if (filePath.isNullOrBlank()) {
            file = null
        } else if (isRemote) {
            // 远程文件：创建一个虚拟 File 对象用于保存路径信息（不检查本地存在性）
            file = File(filePath)
        } else {
            val f = File(filePath)
            file = if (f.exists()) f else null
        }
    }
    
    // 主题颜色
    val primaryGradient = listOf(
        Color(0xFF6366F1),
        Color(0xFF8B5CF6)
    )
    
    // 判断是否可编辑（文本类型）- 远程文件也支持编辑
    val canEdit = file?.let { isTextFile(it) } ?: false
    
    // 实时编辑回调：在另一个窗口打开编辑器并建立绑定
    val handleOpenRealtimeEditor: () -> Unit = {
        val currentFile = file
        val currentPane = panePosition
        if (currentFile != null && currentPane != null) {
            android.util.Log.d("FilePreviewPanel", "请求打开实时编辑: ${currentFile.absolutePath}")
            
            // 查找可用的编辑器窗口（排除当前预览窗口）
            val allPanes = listOf(PanePosition.TOP_LEFT, PanePosition.TOP_RIGHT, PanePosition.BOTTOM)
            val availablePanes = allPanes.filter { it != currentPane }
            
            // 优先选择第一个可用窗口作为编辑器
            val targetEditorPane = availablePanes.firstOrNull()
            
            if (targetEditorPane != null && onOpenInEditor != null) {
                // 在编辑器窗口打开文件
                onOpenInEditor.invoke(currentFile.absolutePath, targetEditorPane)
                // 建立编辑器-预览窗口绑定
                PaneBindingManager.bindEditorToPreview(targetEditorPane, currentPane)
                android.util.Log.d("FilePreviewPanel", "已建立绑定: editor=$targetEditorPane, preview=$currentPane")
            }
        }
    }
    
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部标题栏（带绑定指示器和实时编辑按钮）
            PreviewHeader(
                file = file,
                isEditMode = isEditMode,
                canEdit = canEdit,
                onToggleEditMode = { isEditMode = !isEditMode },
                primaryGradient = primaryGradient,
                boundFilePane = boundFilePane,
                onUnbind = onUnbind,
                onOpenRealtimeEditor = handleOpenRealtimeEditor,
                panePosition = panePosition,
                onClose = onClose
            )
            
            val fileSystem = remember(backendState.backendId) { ActiveFileSystemManager.getActiveBackend() }
            
            // 内容区域
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when {
                    file == null -> EmptyPreviewState(primaryGradient)
                    !pluginsInitialized -> Box(modifier = Modifier.fillMaxSize()) // 不显示"加载中"
                    isEditMode && canEdit -> {
                        EditableTextContent(
                            file = file!!,
                            onSave = { content ->
                                onSaveFile(file!!.absolutePath, content)
                            }
                        )
                    }
                    else -> {
                        // 使用插件系统预览（本地和远程统一处理）
                        // 使用文件路径作为 key，确保不同文件有不同的组件实例
                        // 使用 CompositionLocalProvider 提供回调和当前窗口位置
                        CompositionLocalProvider(
                            LocalOpenRealtimeEditor provides onOpenRealtimeEditor,
                            LocalOpenInEditor provides onOpenInEditor,
                            LocalCurrentPanePosition provides panePosition
                        ) {
                            key(filePath, isRemote, backendState.backendId) {
                                UnifiedPluginBasedPreviewContent(
                                    file = file!!,
                                    isRemote = isRemote,
                                    fileSystem = fileSystem
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 判断是否为文本文件
 */
private fun isTextFile(file: File): Boolean {
    return when (file.extension.lowercase()) {
        "txt", "md", "log", "csv", "properties", "pro", "cfg", "conf", "ini",
        "kt", "java", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "h", "hpp",
        "swift", "go", "rs", "rb", "php", "html", "css", "scss", "sass",
        "xml", "json", "yaml", "yml", "toml", "sql", "sh", "bash", "zsh",
        "gradle", "kts" -> true
        else -> false
    }
}

/**
 * 预览结果缓存 - 按文件路径缓存预览结果，避免切换时的闪烁
 */
private val previewCache = mutableMapOf<String, PreviewResult>()

/**
 * 预览缓存管理器 - 优化内存使用
 */
object PreviewCacheManager {
    private const val MAX_CACHE_SIZE = 10 // 最多缓存10个预览结果
    private val accessOrder = mutableListOf<String>() // 访问顺序，用于LRU淘汰
    
    /**
     * 获取缓存的预览结果
     */
    fun get(path: String): PreviewResult? {
        return previewCache[path]?.also {
            // 更新访问顺序
            accessOrder.remove(path)
            accessOrder.add(path)
        }
    }
    
    /**
     * 添加预览结果到缓存
     */
    fun put(path: String, result: PreviewResult) {
        // 如果缓存已满，移除最久未使用的
        if (previewCache.size >= MAX_CACHE_SIZE && !previewCache.containsKey(path)) {
            val oldest = accessOrder.removeFirstOrNull()
            oldest?.let { previewCache.remove(it) }
        }
        
        previewCache[path] = result
        accessOrder.remove(path)
        accessOrder.add(path)
    }
    
    /**
     * 清理不在书签中的预览缓存
     */
    fun cleanupUnused(bookmarkPaths: Set<String>) {
        val toRemove = previewCache.keys.filter { path ->
            !bookmarkPaths.contains(path) && !File(path).exists()
        }
        toRemove.forEach { path ->
            previewCache.remove(path)
            accessOrder.remove(path)
        }
    }
    
    /**
     * 清理指定路径的缓存
     */
    fun remove(path: String) {
        previewCache.remove(path)
        accessOrder.remove(path)
    }
    
    /**
     * 清空所有缓存
     */
    fun clear() {
        previewCache.clear()
        accessOrder.clear()
    }
}

/**
 * 统一的基于插件的预览内容（支持本地和远程文件）
 */
@Composable
private fun UnifiedPluginBasedPreviewContent(
    file: File,
    isRemote: Boolean,
    fileSystem: FileSystemBackend
) {
    // 创建文件信息
    val fileInfo = remember(file.absolutePath, isRemote) {
        if (isRemote) {
            // 远程文件：不检查本地文件属性
            FileInfo(
                path = file.absolutePath,
                name = file.name,
                extension = ".${file.extension}",
                size = 0L, // 远程文件大小暂时未知
                lastModified = System.currentTimeMillis(),
                isDirectory = false,
                permissions = "",
                mimeType = getMimeType(file.extension),
                isRemote = true
            )
        } else {
            // 本地文件：使用传统方式
            FileInfo(
                path = file.absolutePath,
                name = file.name,
                extension = ".${file.extension}",
                size = file.length(),
                lastModified = file.lastModified(),
                isDirectory = false,
                permissions = "",
                mimeType = getMimeType(file.extension),
                isRemote = false
            )
        }
    }
    
    // 状态版本用于触发重新查找插件
    val stateVersion = PluginStateManager.stateVersion
    
    // 预览结果 - 保留上一个预览，直到新预览准备好
    var previewResult by remember { 
        mutableStateOf<PreviewResult?>(PreviewCacheManager.get(fileInfo.path))
    }
    var currentFilePath by remember { mutableStateOf<String?>(null) }
    
    // 查找并执行预览
    LaunchedEffect(fileInfo.path, stateVersion, isRemote) {
        // 文件路径改变时，先清空当前预览结果（避免显示上一个文件的内容）
        val isFileChanged = currentFilePath != null && currentFilePath != fileInfo.path
        if (isFileChanged) {
            // 清空预览结果，显示空白，避免显示上一个文件的内容
            previewResult = null
            // 清理旧文件的预览缓存，确保不会残留
            currentFilePath?.let { 
                PreviewCacheManager.remove(it)
                android.util.Log.d("FilePreviewPanel", "Cleared preview cache for changed file: $it")
            }
        }
        currentFilePath = fileInfo.path
        
        // 检查协程是否仍然活跃
        if (!currentCoroutineContext().isActive) {
            return@LaunchedEffect
        }
        
        // 尝试从缓存加载（仅本地文件）
        if (!isRemote) {
            PreviewCacheManager.get(fileInfo.path)?.let { cached ->
                // 检查协程是否仍然活跃
                if (currentCoroutineContext().isActive) {
                    previewResult = cached
                    // 如果有缓存，仍然需要验证文件是否仍然存在
                    val fileObj = java.io.File(fileInfo.path)
                    if (fileObj.exists() && fileObj.canRead()) {
                        return@LaunchedEffect // 缓存有效，直接使用
                    } else {
                        // 文件不存在或不可读，清除缓存并重新加载
                        PreviewCacheManager.remove(fileInfo.path)
                        previewResult = null
                    }
                } else {
                    return@LaunchedEffect
                }
            }
        }
        
        // 异步加载新预览（不阻塞UI）
        val plugin = PreviewPluginRegistry.findPreviewPlugin(fileInfo)
        if (plugin != null) {
            try {
                // 根据是否为远程文件选择不同的预览方法
                val newResult = if (isRemote && plugin.supportsRemoteFiles()) {
                    // 使用支持远程文件的方法
                    plugin.createPreview(fileInfo, fileSystem)
                } else if (isRemote && !plugin.supportsRemoteFiles()) {
                    // 插件不支持远程文件，使用回退方案
                    createFallbackRemotePreview(fileInfo, fileSystem)
                } else {
                    // 本地文件，使用传统方法
                    plugin.createPreview(fileInfo)
                }
                
                // 在设置状态前检查协程是否仍然活跃，并且文件路径没有改变
                if (currentCoroutineContext().isActive && currentFilePath == fileInfo.path) {
                    previewResult = newResult
                    // 仅缓存本地文件结果
                    if (!isRemote) {
                        PreviewCacheManager.put(fileInfo.path, newResult)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被取消，忽略错误
                throw e // 重新抛出以正确传播取消
            } catch (e: Exception) {
                // 检查是否是Compose取消异常
                if (e.message?.contains("left the composition") == true || 
                    e.javaClass.simpleName.contains("CancellationException")) {
                    return@LaunchedEffect
                }
                // 只有在协程仍然活跃且文件路径没有改变时才设置错误状态
                if (currentCoroutineContext().isActive && currentFilePath == fileInfo.path) {
                    val errorResult = PreviewResult.Error("预览失败: ${e.message ?: e.javaClass.simpleName}", e)
                    previewResult = errorResult
                    if (!isRemote) {
                        PreviewCacheManager.put(fileInfo.path, errorResult)
                    }
                }
            }
        } else {
            // 没有找到插件时，尝试使用回退预览
            if (isRemote) {
                if (currentCoroutineContext().isActive && currentFilePath == fileInfo.path) {
                    previewResult = createFallbackRemotePreview(fileInfo, fileSystem)
                }
            } else {
                // 检查协程是否仍然活跃，并且文件路径没有改变
                if (currentCoroutineContext().isActive && currentFilePath == fileInfo.path) {
                    val unsupportedResult = PreviewResult.Unsupported
                    previewResult = unsupportedResult
                    PreviewCacheManager.put(fileInfo.path, unsupportedResult)
                }
            }
        }
    }
    
    // 渲染结果 - 如果有结果就显示，没有结果时显示空白（不显示"加载中"）
    when (val result = previewResult) {
        is PreviewResult.Success -> {
            // 直接调用预览组件，异常应该在组件内部处理
            // 全局异常处理器会在Application级别捕获未处理的异常
            result.previewComponent()
        }
        is PreviewResult.Error -> {
            ErrorPreviewState(result.message)
        }
        PreviewResult.Unsupported -> {
            PluginRequiredState(file)
        }
        null -> {
            // 不显示"加载中"，显示空白，等预览准备好后直接切换
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * 创建远程文件的回退预览（当插件不支持远程文件时）
 */
private suspend fun createFallbackRemotePreview(
    fileInfo: FileInfo,
    fileSystem: FileSystemBackend
): PreviewResult {
    val extension = fileInfo.extension.lowercase().removePrefix(".")
    val isTextFile = extension in listOf(
        "txt", "md", "log", "csv", "properties", "pro", "cfg", "conf", "ini",
        "kt", "java", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "h", "hpp",
        "swift", "go", "rs", "rb", "php", "html", "css", "scss", "sass",
        "xml", "json", "yaml", "yml", "toml", "sql", "sh", "bash", "zsh",
        "gradle", "kts", "markdown", "mdown", "mkdn", "mkd"
    )
    val isImageFile = extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    
    return PreviewResult.Success(
        previewComponent = {
            FallbackRemotePreviewContent(
                filePath = fileInfo.path,
                isTextFile = isTextFile,
                isImageFile = isImageFile,
                fileSystem = fileSystem
            )
        }
    )
}

/**
 * 回退远程文件预览组件
 */
@Composable
private fun FallbackRemotePreviewContent(
    filePath: String,
    isTextFile: Boolean,
    isImageFile: Boolean,
    fileSystem: FileSystemBackend
) {
    var content by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    // 加载远程文件内容
    LaunchedEffect(filePath) {
        isLoading = true
        error = null
        
        try {
            val result = withContext(Dispatchers.IO) {
                fileSystem.read(filePath)
            }
            
            if (result.isSuccess) {
                content = result.getOrThrow()
            } else {
                error = result.exceptionOrNull()?.message ?: "读取文件失败"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "读取文件异常"
        } finally {
            isLoading = false
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color(0xFF6366F1)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "正在加载远程文件...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            error != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "加载失败",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            content != null -> {
                when {
                    isTextFile -> {
                        // 文本文件预览
                        val textContent = remember(content) {
                            try {
                                String(content!!, Charsets.UTF_8)
                            } catch (e: Exception) {
                                String(content!!, Charsets.ISO_8859_1)
                            }
                        }
                        
                        SelectionContainer {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = textContent,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    isImageFile -> {
                        // 图片文件预览
                        val bitmap = remember(content) {
                            try {
                                BitmapFactory.decodeByteArray(content, 0, content!!.size)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "远程图片预览",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text = "无法解码图片",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    else -> {
                        // 不支持的文件类型
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "远程二进制文件",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "大小: ${content!!.size} 字节",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 基于插件的预览内容（仅本地文件，保留向后兼容）
 */
@Composable
private fun PluginBasedPreviewContent(file: File) {
    // 创建文件信息
    val fileInfo = remember(file.absolutePath) {
        FileInfo(
            path = file.absolutePath,
            name = file.name,
            extension = ".${file.extension}",
            size = file.length(),
            lastModified = file.lastModified(),
            isDirectory = false,
            permissions = "",
            mimeType = getMimeType(file.extension)
        )
    }
    
    // 状态版本用于触发重新查找插件
    val stateVersion = PluginStateManager.stateVersion
    
    // 预览结果 - 保留上一个预览，直到新预览准备好
    var previewResult by remember { 
        mutableStateOf<PreviewResult?>(PreviewCacheManager.get(fileInfo.path))
    }
    var currentFilePath by remember { mutableStateOf<String?>(null) }
    
    // 查找并执行预览
    LaunchedEffect(fileInfo.path, stateVersion) {
        // 文件路径改变时，先清空当前预览结果（避免显示上一个文件的内容）
        val isFileChanged = currentFilePath != null && currentFilePath != fileInfo.path
        if (isFileChanged) {
            // 清空预览结果，显示空白，避免显示上一个文件的内容
            previewResult = null
            // 清理旧文件的预览缓存，确保不会残留
            currentFilePath?.let { 
                PreviewCacheManager.remove(it)
                android.util.Log.d("FilePreviewPanel", "Cleared preview cache for changed file: $it")
            }
        }
        currentFilePath = fileInfo.path
        
        // 检查协程是否仍然活跃
        if (!currentCoroutineContext().isActive) {
            return@LaunchedEffect
        }
        
        // 尝试从缓存加载
        PreviewCacheManager.get(fileInfo.path)?.let { cached ->
            // 检查协程是否仍然活跃
            if (currentCoroutineContext().isActive) {
                previewResult = cached
                // 如果有缓存，仍然需要验证文件是否仍然存在
                val fileObj = java.io.File(fileInfo.path)
                if (fileObj.exists() && fileObj.canRead()) {
                    return@LaunchedEffect // 缓存有效，直接使用
                } else {
                    // 文件不存在或不可读，清除缓存并重新加载
                    PreviewCacheManager.remove(fileInfo.path)
                    previewResult = null
                }
            } else {
                return@LaunchedEffect
            }
        }
        
        // 异步加载新预览（不阻塞UI）
        val plugin = PreviewPluginRegistry.findPreviewPlugin(fileInfo)
        if (plugin != null) {
            try {
                val newResult = plugin.createPreview(fileInfo)
                // 在设置状态前检查协程是否仍然活跃，并且文件路径没有改变
                if (currentCoroutineContext().isActive && currentFilePath == fileInfo.path) {
                    previewResult = newResult
                    // 缓存结果
                    PreviewCacheManager.put(fileInfo.path, newResult)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被取消，忽略错误
                throw e // 重新抛出以正确传播取消
            } catch (e: Exception) {
                // 检查是否是Compose取消异常
                if (e.message?.contains("left the composition") == true || 
                    e.javaClass.simpleName.contains("CancellationException")) {
                    return@LaunchedEffect
                }
                // 只有在协程仍然活跃且文件路径没有改变时才设置错误状态
                if (currentCoroutineContext().isActive && currentFilePath == fileInfo.path) {
                    val errorResult = PreviewResult.Error("预览失败: ${e.message ?: e.javaClass.simpleName}", e)
                    previewResult = errorResult
                    PreviewCacheManager.put(fileInfo.path, errorResult)
                }
            }
        } else {
            // 检查协程是否仍然活跃，并且文件路径没有改变
            if (currentCoroutineContext().isActive && currentFilePath == fileInfo.path) {
                val unsupportedResult = PreviewResult.Unsupported
                previewResult = unsupportedResult
                PreviewCacheManager.put(fileInfo.path, unsupportedResult)
            }
        }
    }
    
    // 渲染结果 - 如果有结果就显示，没有结果时显示空白（不显示"加载中"）
    when (val result = previewResult) {
        is PreviewResult.Success -> {
            // 直接调用预览组件，异常应该在组件内部处理
            // 全局异常处理器会在Application级别捕获未处理的异常
            result.previewComponent()
        }
        is PreviewResult.Error -> {
            ErrorPreviewState(result.message)
        }
        PreviewResult.Unsupported -> {
            PluginRequiredState(file)
        }
        null -> {
            // 不显示"加载中"，显示空白，等预览准备好后直接切换
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * 加载中状态（内部使用）
 */
@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("加载中...", color = Color(0xFF6366F1))
    }
}

/**
 * 通用加载指示器（供插件使用）
 */
@Composable
fun LoadingSpinner() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("加载中...", color = Color(0xFF6366F1))
    }
}


/**
 * 错误状态
 */
@Composable
private fun ErrorPreviewState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(0.9f)
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "预览错误",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                    lineHeight = 20.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "提示：可以尝试使用外部应用打开此文件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 需要插件状态
 */
@Composable
private fun PluginRequiredState(file: File) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Extension,
                contentDescription = null,
                tint = Color(0xFF6366F1),
                modifier = Modifier.size(56.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "🔌 需要插件",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "文件类型: .${file.extension}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6366F1)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "该文件类型的预览插件\n未启用或不存在",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "请在「插件」页面启用相应插件",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * 预览头部
 */
@Composable
private fun PreviewHeader(
    file: File?,
    isEditMode: Boolean,
    canEdit: Boolean,
    onToggleEditMode: () -> Unit,
    primaryGradient: List<Color>,
    boundFilePane: PanePosition? = null,
    onUnbind: (() -> Unit)? = null,
    onOpenRealtimeEditor: (() -> Unit)? = null,
    panePosition: PanePosition? = null,
    onClose: (() -> Unit)? = null
) {
    // 预览头部 - 固定高度，支持水平滚动
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 左侧：文件图标和名称
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 文件类型图标
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            brush = Brush.linearGradient(primaryGradient),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getFileIcon(file),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(10.dp))
                
                // 文件名称（不截断，支持滚动显示完整名称）
                Column {
                    Text(
                        text = file?.name ?: "文件预览",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        softWrap = false
                    )
                    if (file != null) {
                        Text(
                            text = formatFileSize(file.length()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = false
                        )
                    }
                }
                
                // 右侧留白，确保滚动时内容不紧贴边缘
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            // 右侧：绑定指示器和操作按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 绑定指示器
                if (boundFilePane != null && onUnbind != null) {
                    Surface(
                        onClick = onUnbind,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "已绑定",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = boundFilePane.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "断开绑定",
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                
                // 实时编辑按钮（替代旧的编辑/预览模式切换）
                if (file != null && canEdit && onOpenRealtimeEditor != null && panePosition != null) {
                    FilledTonalButton(
                        onClick = onOpenRealtimeEditor,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "实时编辑",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "实时编辑",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                
                // 关闭按钮
                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 空状态显示
 */
@Composable
private fun EmptyPreviewState(primaryGradient: List<Color>) {
    val infiniteTransition = rememberInfiniteTransition(label = "emptyState")
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF6366F1).copy(alpha = 0.05f),
                        Color.Transparent
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .alpha(pulseAlpha)
                    .background(
                        brush = Brush.linearGradient(primaryGradient),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.InsertDriveFile,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = "选择文件以预览",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "点击左侧文件列表中的文件\n即可在此处预览内容",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * 可编辑文本内容
 * 支持本地和远程（SSH）文件编辑
 */
@Composable
private fun EditableTextContent(
    file: File,
    onSave: (String) -> Unit
) {
    var content by remember(file) { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isModified by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // 获取是否为远程文件系统 - 使用 collectAsState 订阅 StateFlow
    val isRemote by ActiveFileSystemManager.isRemote.collectAsState()
    
    LaunchedEffect(file, isRemote) {
        isLoading = true
        isModified = false
        error = null
        try {
            val fileContent = withContext(Dispatchers.IO) {
                val result = ActiveFileSystemManager.getActiveBackend().readText(file.absolutePath)
                if (result.isSuccess) {
                    result.getOrThrow()
                } else {
                    throw result.exceptionOrNull() ?: Exception("读取文件失败")
                }
            }
            // 检查协程是否仍然活跃（在设置状态前）
            if (currentCoroutineContext().isActive) {
                content = fileContent
                isLoading = false
            }
        } catch (e: CancellationException) {
            // 协程被取消，忽略错误（不设置状态）
            throw e // 重新抛出以正确传播取消
        } catch (e: Exception) {
            // 检查协程是否仍然活跃
            if (currentCoroutineContext().isActive) {
                error = "读取失败: ${e.message}"
                isLoading = false
            }
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("加载中...", color = Color(0xFF6366F1), fontSize = 12.sp)
            }
        } else if (error != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        } else {
            // 编辑区域
            OutlinedTextField(
                value = content,
                onValueChange = {
                    content = it
                    isModified = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6366F1),
                    cursorColor = Color(0xFF6366F1)
                ),
                shape = RoundedCornerShape(8.dp)
            )
            
            // 保存按钮
            AnimatedVisibility(
                visible = isModified,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            onSave(content)
                            isModified = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("保存更改")
                    }
                }
            }
        }
    }
}

/**
 * 获取文件图标
 */
private fun getFileIcon(file: File?): ImageVector {
    if (file == null) return Icons.Outlined.InsertDriveFile
    
    return when (file.extension.lowercase()) {
        // 图片
        "jpg", "jpeg", "png", "gif", "webp", "bmp" -> Icons.Default.Image
        // 代码
        "kt", "java", "py", "js", "ts", "c", "cpp", "swift", "go", "rs" -> Icons.Default.Code
        // 文档
        "txt", "md", "log" -> Icons.Default.Description
        "pdf" -> Icons.Default.PictureAsPdf
        "doc", "docx" -> Icons.Default.Description
        // 音视频
        "mp3", "wav", "ogg", "m4a" -> Icons.Default.Audiotrack
        "mp4", "mkv", "avi", "webm" -> Icons.Default.Movie
        // 压缩包
        "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip
        // 配置
        "json", "xml", "yaml", "yml" -> Icons.Default.DataObject
        // 其他
        else -> Icons.Default.InsertDriveFile
    }
}

/**
 * 格式化文件大小
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

/**
 * MIME 类型映射
 */
private fun getMimeType(extension: String): String {
    return when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "txt" -> "text/plain"
        "html", "htm" -> "text/html"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "md" -> "text/markdown"
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "m4a" -> "audio/mp4"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/avi"
        else -> "application/octet-stream"
    }
}
