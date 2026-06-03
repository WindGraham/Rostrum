package com.rostrum.ui.network

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.SocketTimeoutException
import java.util.UUID
import android.widget.TextView
import com.rostrum.core.event.EventBusImpl
import com.rostrum.core.event.EventSubscriber
import com.rostrum.core.event.FileContentUpdateEvent
import com.rostrum.core.event.FileModifiedEvent
import com.rostrum.core.filesystem.ActiveFileSystemManager
import com.rostrum.core.filesystem.FileSystemBackend
import com.rostrum.core.network.MarkdownRenderer
import com.rostrum.core.util.PathUtils
import com.rostrum.core.plugin.models.FileInfo

private const val TAG = "SshRealtimeEditor"
private const val MAX_RETRY_COUNT = 3
private const val RETRY_DELAY_MS = 1000L
private const val AUTO_SAVE_DELAY_MS = 2000L
private const val PREVIEW_DEBOUNCE_MS = 150L
private const val MAX_FILE_SIZE_BYTES = 1024 * 1024 // 1MB

/**
 * 编辑器状态
 */
enum class EditorState {
    IDLE,           // 空闲
    LOADING,        // 加载中
    EDITING,        // 编辑中
    SAVING,         // 保存中
    SAVED,          // 已保存
    ERROR,          // 错误
    OFFLINE         // 离线
}

/**
 * 网络状态
 */
enum class NetworkState {
    CONNECTED,      // 已连接
    SLOW,           // 网络较慢
    DISCONNECTED,   // 已断开
    UNKNOWN         // 未知
}

/**
 * 保存结果
 */
sealed class SaveResult {
    data class Success(val timestamp: Long) : SaveResult()
    data class Error(val exception: Throwable, val retryable: Boolean = true) : SaveResult()
    data class Pending(val attempt: Int, val maxRetries: Int) : SaveResult()
}

/**
 * 编辑器配置
 */
data class EditorConfig(
    val enableAutoSave: Boolean = true,
    val autoSaveDelayMs: Long = AUTO_SAVE_DELAY_MS,
    val enablePreview: Boolean = true,
    val enableSyntaxHighlight: Boolean = false,
    val maxRetries: Int = MAX_RETRY_COUNT,
    val retryDelayMs: Long = RETRY_DELAY_MS,
    val fontSize: Float = 14f,
    val useMonospaceFont: Boolean = true
)

/**
 * SSH实时文件编辑器组件
 * 
 * 支持远程SSH文件的实时编辑和预览，具有自动保存、网络状态监控、
 * 错误重试等功能。适用于本地和远程文件系统。
 * 
 * @param fileUri 文件URI或路径
 * @param onClose 关闭回调
 * @param onSave 保存回调，参数为 (uri, content)
 * @param config 编辑器配置
 * @param modifier Modifier
 */
@Composable
fun SshRealtimeEditor(
    fileUri: String,
    onClose: () -> Unit,
    onSave: ((String, String) -> Unit)? = null,
    config: EditorConfig = EditorConfig(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 文件系统服务
    val fileSystem = remember { ActiveFileSystemManager.getActiveBackend() }
    val isRemote = remember { ActiveFileSystemManager.isUsingRemote() }
    
    // 文件名
    val fileName = remember(fileUri) { File(fileUri).name }
    val fileExtension = remember(fileUri) { 
        File(fileUri).extension.lowercase() 
    }
    
    // 核心状态
    var editorState by remember { mutableStateOf(EditorState.IDLE) }
    var content by remember { mutableStateOf("") }
    var originalContent by remember { mutableStateOf("") }
    var isModified by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // 网络状态
    var networkState by remember { mutableStateOf(NetworkState.UNKNOWN) }
    var lastPingTime by remember { mutableStateOf<Long?>(null) }
    
    // 保存相关
    var lastSaveTime by remember { mutableStateOf<Long?>(null) }
    var saveJob by remember { mutableStateOf<Job?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    
    // 预览相关
    var showPreview by remember { mutableStateOf(shouldShowPreview(fileExtension)) }
    var splitRatio by remember { mutableFloatStateOf(0.5f) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    
    // 事件总线
    val eventBus = remember { EventBusImpl.getInstance() }
    val normalizedPath = remember(fileUri) { PathUtils.normalizePath(fileUri) }
    
    // 滚动状态
    val scrollState = rememberScrollState()
    
    // 派生状态：是否可以保存
    val canSave by remember {
        derivedStateOf {
            isModified && 
            editorState != EditorState.SAVING && 
            editorState != EditorState.LOADING &&
            editorState != EditorState.ERROR
        }
    }
    
    // 派生状态：显示的状态文本
    val statusText by remember {
        derivedStateOf {
            when (editorState) {
                EditorState.IDLE -> if (isModified) "未保存" else "就绪"
                EditorState.LOADING -> "加载中..."
                EditorState.EDITING -> "编辑中"
                EditorState.SAVING -> "保存中${if (retryCount > 0) " (重试 $retryCount/${config.maxRetries})" else ""}..."
                EditorState.SAVED -> "已保存" + lastSaveTime?.let { " ${formatTime(it)}" }.orEmpty()
                EditorState.ERROR -> "错误: ${errorMessage ?: "未知错误"}"
                EditorState.OFFLINE -> "离线模式"
            }
        }
    }
    
    // ===== 网络状态检测 =====
    LaunchedEffect(isRemote) {
        if (!isRemote) {
            networkState = NetworkState.CONNECTED
            return@LaunchedEffect
        }
        
        while (isActive) {
            try {
                val startTime = System.currentTimeMillis()
                // 通过检查文件是否存在来测试连接
                val exists = withContext(Dispatchers.IO) {
                    fileSystem.exists(fileUri)
                }
                val pingTime = System.currentTimeMillis() - startTime
                lastPingTime = pingTime
                
                networkState = when {
                    pingTime > 1000 -> NetworkState.SLOW
                    else -> NetworkState.CONNECTED
                }
            } catch (e: Exception) {
                networkState = NetworkState.DISCONNECTED
                Log.w(TAG, "Network check failed: ${e.message}")
            }
            
            delay(5000) // 每5秒检测一次
        }
    }
    
    // ===== 加载文件内容 =====
    LaunchedEffect(fileUri) {
        loadFileContent(
            fileUri = fileUri,
            fileSystem = fileSystem,
            onLoading = { editorState = EditorState.LOADING },
            onSuccess = { loadedContent ->
                content = loadedContent
                originalContent = loadedContent
                isModified = false
                editorState = EditorState.IDLE
                errorMessage = null
            },
            onError = { error ->
                errorMessage = error
                editorState = EditorState.ERROR
            }
        )
    }
    
    // ===== 订阅内容更新事件 =====
    DisposableEffect(fileUri) {
        val subscriber = object : EventSubscriber<FileContentUpdateEvent> {
            override suspend fun onEvent(event: FileContentUpdateEvent) {
                if (PathUtils.pathsEqual(event.filePath, normalizedPath)) {
                    withContext(Dispatchers.Main) {
                        content = event.content
                        // 不标记为修改，因为这是外部更新
                    }
                }
            }
        }
        
        eventBus.subscribe(FileContentUpdateEvent::class, subscriber)
        
        onDispose {
            eventBus.unsubscribe(FileContentUpdateEvent::class, subscriber)
        }
    }
    
    // ===== 自动保存逻辑 =====
    LaunchedEffect(content, isModified) {
        if (!config.enableAutoSave || !isModified || editorState == EditorState.ERROR) {
            return@LaunchedEffect
        }
        
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(config.autoSaveDelayMs)
            
            if (isActive && isModified) {
                performSave(
                    fileUri = fileUri,
                    content = content,
                    fileSystem = fileSystem,
                    eventBus = eventBus,
                    normalizedPath = normalizedPath,
                    maxRetries = config.maxRetries,
                    retryDelayMs = config.retryDelayMs,
                    onStateChange = { state -> editorState = state },
                    onRetryChange = { count -> retryCount = count },
                    onSaveTimeChange = { time -> lastSaveTime = time },
                    onError = { error -> errorMessage = error },
                    onSuccess = { 
                        isModified = false
                        onSave?.invoke(fileUri, content)
                    }
                )
            }
        }
    }
    
    // ===== 实时预览逻辑 =====
    LaunchedEffect(content) {
        if (!config.enablePreview || !showPreview) {
            return@LaunchedEffect
        }
        
        previewJob?.cancel()
        previewJob = scope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            
            if (isActive) {
                eventBus.publish(FileContentUpdateEvent(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    filePath = normalizedPath,
                    content = content,
                    isModified = isModified
                ))
            }
        }
    }
    
    // 清理协程
    DisposableEffect(fileUri) {
        onDispose {
            saveJob?.cancel()
            previewJob?.cancel()
        }
    }
    
    // ===== UI =====
    Column(modifier = modifier.fillMaxSize()) {
        // 顶部工具栏
        EditorToolbar(
            fileName = fileName,
            isRemote = isRemote,
            networkState = networkState,
            editorState = editorState,
            statusText = statusText,
            canSave = canSave,
            showPreview = showPreview,
            canPreview = shouldShowPreview(fileExtension),
            onSaveClick = {
                scope.launch {
                    saveJob?.cancel()
                    performSave(
                        fileUri = fileUri,
                        content = content,
                        fileSystem = fileSystem,
                        eventBus = eventBus,
                        normalizedPath = normalizedPath,
                        maxRetries = config.maxRetries,
                        retryDelayMs = config.retryDelayMs,
                        onStateChange = { state -> editorState = state },
                        onRetryChange = { count -> retryCount = count },
                        onSaveTimeChange = { time -> lastSaveTime = time },
                        onError = { error -> errorMessage = error },
                        onSuccess = {
                            isModified = false
                            onSave?.invoke(fileUri, content)
                        }
                    )
                }
            },
            onRefreshClick = {
                scope.launch {
                    loadFileContent(
                        fileUri = fileUri,
                        fileSystem = fileSystem,
                        onLoading = { editorState = EditorState.LOADING },
                        onSuccess = { loadedContent ->
                            content = loadedContent
                            originalContent = loadedContent
                            isModified = false
                            editorState = EditorState.IDLE
                            errorMessage = null
                        },
                        onError = { error ->
                            errorMessage = error
                            editorState = EditorState.ERROR
                        }
                    )
                }
            },
            onPreviewToggle = { showPreview = !showPreview },
            onCloseClick = onClose
        )
        
        // 错误提示
        if (errorMessage != null && editorState == EditorState.ERROR) {
            ErrorBanner(
                message = errorMessage!!,
                onDismiss = { errorMessage = null },
                onRetry = {
                    scope.launch {
                        loadFileContent(
                            fileUri = fileUri,
                            fileSystem = fileSystem,
                            onLoading = { editorState = EditorState.LOADING },
                            onSuccess = { loadedContent ->
                                content = loadedContent
                                originalContent = loadedContent
                                isModified = false
                                editorState = EditorState.IDLE
                                errorMessage = null
                            },
                            onError = { error ->
                                errorMessage = error
                                editorState = EditorState.ERROR
                            }
                        )
                    }
                }
            )
        }
        
        // 编辑器主体
        if (showPreview && config.enablePreview) {
            // 分屏模式
            EditorWithPreview(
                content = content,
                onContentChange = { 
                    content = it
                    isModified = true
                    editorState = EditorState.EDITING
                },
                fileExtension = fileExtension,
                splitRatio = splitRatio,
                onSplitRatioChange = { splitRatio = it },
                scrollState = scrollState,
                config = config,
                fileUri = fileUri
            )
        } else {
            // 纯编辑模式
            EditorPane(
                content = content,
                onContentChange = { 
                    content = it
                    isModified = true
                    editorState = EditorState.EDITING
                },
                scrollState = scrollState,
                config = config
            )
        }
        
        // 底部状态栏
        EditorStatusBar(
            isRemote = isRemote,
            networkState = networkState,
            pingTime = lastPingTime,
            charCount = content.length,
            lineCount = content.count { it == '\n' } + 1,
            encoding = "UTF-8"
        )
    }
}

/**
 * 编辑器工具栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorToolbar(
    fileName: String,
    isRemote: Boolean,
    networkState: NetworkState,
    editorState: EditorState,
    statusText: String,
    canSave: Boolean,
    showPreview: Boolean,
    canPreview: Boolean,
    onSaveClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onPreviewToggle: () -> Unit,
    onCloseClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：文件名和网络状态
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isRemote) Icons.Default.Cloud else Icons.Default.Computer,
                        contentDescription = null,
                        tint = when (networkState) {
                            NetworkState.CONNECTED -> Color(0xFF4CAF50)
                            NetworkState.SLOW -> Color(0xFFFF9800)
                            NetworkState.DISCONNECTED -> Color(0xFFE91E63)
                            NetworkState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    
                    // 状态指示器
                    StatusIndicator(editorState)
                }
                
                // 右侧：操作按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 状态文本
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    
                    // 预览切换按钮
                    if (canPreview) {
                        IconButton(
                            onClick = onPreviewToggle,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (showPreview) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    Color.Transparent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Preview,
                                contentDescription = if (showPreview) "隐藏预览" else "显示预览"
                            )
                        }
                    }
                    
                    // 刷新按钮
                    IconButton(
                        onClick = onRefreshClick,
                        enabled = editorState != EditorState.LOADING
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    
                    // 保存按钮
                    Button(
                        onClick = onSaveClick,
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (editorState) {
                                EditorState.SAVED -> Color(0xFF4CAF50)
                                EditorState.ERROR -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        Icon(
                            imageVector = when (editorState) {
                                EditorState.SAVED -> Icons.Default.Check
                                EditorState.SAVING -> Icons.Default.Sync
                                else -> Icons.Default.Save
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (editorState) {
                                EditorState.SAVING -> "保存中"
                                EditorState.SAVED -> "已保存"
                                else -> "保存"
                            }
                        )
                    }
                    
                    // 关闭按钮
                    IconButton(onClick = onCloseClick) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
            }
            
            // 分隔线
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        }
    }
}

/**
 * 状态指示器
 */
@Composable
private fun StatusIndicator(state: EditorState) {
    val (color, text) = when (state) {
        EditorState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant to ""
        EditorState.LOADING -> MaterialTheme.colorScheme.primary to ""
        EditorState.EDITING -> Color(0xFFFF9800) to ""
        EditorState.SAVING -> MaterialTheme.colorScheme.primary to ""
        EditorState.SAVED -> Color(0xFF4CAF50) to ""
        EditorState.ERROR -> MaterialTheme.colorScheme.error to ""
        EditorState.OFFLINE -> Color(0xFF9E9E9E) to ""
    }
    
    if (state == EditorState.SAVING || state == EditorState.LOADING) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = color
        )
    } else if (state != EditorState.IDLE) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, shape = MaterialTheme.shapes.small)
        )
    }
}

/**
 * 错误横幅
 */
@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onRetry) {
                    Text("重试")
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

/**
 * 分屏编辑+预览
 */
@Composable
private fun EditorWithPreview(
    content: String,
    onContentChange: (String) -> Unit,
    fileExtension: String,
    splitRatio: Float,
    onSplitRatioChange: (Float) -> Unit,
    scrollState: ScrollState,
    config: EditorConfig,
    fileUri: String
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // 编辑器
        Box(
            modifier = Modifier
                .weight(splitRatio)
                .fillMaxHeight()
        ) {
            EditorPane(
                content = content,
                onContentChange = onContentChange,
                scrollState = scrollState,
                config = config
            )
        }
        
        // 可拖拽分割线
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaRatio = dragAmount.x / size.width
                        onSplitRatioChange((splitRatio + deltaRatio).coerceIn(0.2f, 0.8f))
                    }
                }
        )
        
        // 预览
        Box(
            modifier = Modifier
                .weight(1f - splitRatio)
                .fillMaxHeight()
        ) {
            PreviewPane(
                content = content,
                fileExtension = fileExtension,
                fileUri = fileUri
            )
        }
    }
}

/**
 * 编辑器面板
 */
@Composable
private fun EditorPane(
    content: String,
    onContentChange: (String) -> Unit,
    scrollState: ScrollState,
    config: EditorConfig
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        BasicTextField(
            value = content,
            onValueChange = onContentChange,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            textStyle = TextStyle(
                fontSize = config.fontSize.sp,
                fontFamily = if (config.useMonospaceFont) FontFamily.Monospace else FontFamily.Default,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box {
                    if (content.isEmpty()) {
                        Text(
                            text = "在此输入内容...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

/**
 * 预览面板
 */
@Composable
private fun PreviewPane(
    content: String,
    fileExtension: String,
    fileUri: String
) {
    val context = LocalContext.current
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        when (fileExtension) {
            "md", "markdown" -> {
                // Markdown 预览
                AndroidView(
                    factory = { ctx ->
                        TextView(ctx).apply {
                            setPadding(16, 16, 16, 16)
                            setBackgroundColor(android.graphics.Color.WHITE)
                        }
                    },
                    update = { textView ->
                        MarkdownRenderer.renderTo(textView, content)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            "html", "htm" -> {
                // HTML 预览
                HtmlPreviewContent(
                    content = content,
                    basePath = File(fileUri).parent ?: "",
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                // 纯文本预览
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "该文件类型不支持预览",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * HTML预览内容
 */
@Composable
private fun HtmlPreviewContent(
    content: String,
    basePath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    AndroidView(
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    domStorageEnabled = true
                    allowFileAccess = true
                }
                webViewClient = android.webkit.WebViewClient()
            }
        },
        update = { webView ->
            val processedContent = processHtmlContent(content)
            webView.loadDataWithBaseURL(
                "file://$basePath/",
                processedContent,
                "text/html",
                "UTF-8",
                null
            )
        },
        modifier = modifier
    )
}

/**
 * 底部状态栏
 */
@Composable
private fun EditorStatusBar(
    isRemote: Boolean,
    networkState: NetworkState,
    pingTime: Long?,
    charCount: Int,
    lineCount: Int,
    encoding: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：连接状态
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRemote) {
                    val displayText = when (networkState) {
                        NetworkState.CONNECTED -> "已连接" + pingTime?.let { " (${it}ms)" }.orEmpty()
                        NetworkState.SLOW -> "连接较慢" + pingTime?.let { " (${it}ms)" }.orEmpty()
                        NetworkState.DISCONNECTED -> "已断开"
                        NetworkState.UNKNOWN -> "检测中..."
                    }
                    val displayColor = when (networkState) {
                        NetworkState.CONNECTED -> Color(0xFF4CAF50)
                        NetworkState.SLOW -> Color(0xFFFF9800)
                        NetworkState.DISCONNECTED -> Color(0xFFE91E63)
                        NetworkState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    
                    Icon(
                        imageVector = when (networkState) {
                            NetworkState.CONNECTED -> Icons.Default.CloudDone
                            NetworkState.SLOW -> Icons.Default.CloudQueue
                            NetworkState.DISCONNECTED -> Icons.Default.CloudOff
                            NetworkState.UNKNOWN -> Icons.Default.Cloud
                        },
                        contentDescription = null,
                        tint = displayColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.labelSmall,
                        color = displayColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "本地文件",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 右侧：文件统计
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "$encoding",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$lineCount 行",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$charCount 字符",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ===== 工具函数 =====

/**
 * 加载文件内容
 */
private suspend fun loadFileContent(
    fileUri: String,
    fileSystem: FileSystemBackend,
    onLoading: () -> Unit,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    onLoading()
    
    try {
        // 检查文件大小
        val fileInfo = fileSystem.stat(fileUri)
        if (fileInfo != null && fileInfo.size > MAX_FILE_SIZE_BYTES) {
            onError("文件过大 (${formatFileSize(fileInfo.size)})，建议下载后编辑")
            return
        }
        
        // 读取文件
        val result = fileSystem.read(fileUri)
        
        if (result.isSuccess) {
            val bytes = result.getOrThrow()
            
            // 检查是否为二进制文件
            val isBinary = bytes.take(512).any { it == 0.toByte() }
            if (isBinary) {
                onError("检测到二进制文件，不支持编辑")
                return
            }
            
            val content = String(bytes, Charsets.UTF_8)
            onSuccess(content)
        } else {
            val error = result.exceptionOrNull()
            onError("读取失败: ${error?.message ?: "未知错误"}")
        }
    } catch (e: SocketTimeoutException) {
        Log.e(TAG, "Load timeout: ${e.message}")
        onError("连接超时，请检查网络")
    } catch (e: Exception) {
        Log.e(TAG, "Load error: ${e.message}", e)
        onError("读取失败: ${e.message ?: "未知错误"}")
    }
}

/**
 * 执行保存操作（带重试机制）
 */
private suspend fun performSave(
    fileUri: String,
    content: String,
    fileSystem: FileSystemBackend,
    eventBus: EventBusImpl,
    normalizedPath: String,
    maxRetries: Int,
    retryDelayMs: Long,
    onStateChange: (EditorState) -> Unit,
    onRetryChange: (Int) -> Unit,
    onSaveTimeChange: (Long) -> Unit,
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) {
    onStateChange(EditorState.SAVING)
    
    var attempt = 0
    var lastError: Throwable? = null
    
    while (attempt < maxRetries) {
        onRetryChange(attempt)
        
        try {
            val result = withContext(Dispatchers.IO) {
                fileSystem.writeText(fileUri, content)
            }
            
            if (result.isSuccess) {
                // 发布文件修改事件
                eventBus.publish(FileModifiedEvent(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    filePath = normalizedPath
                ))
                
                onSaveTimeChange(System.currentTimeMillis())
                onStateChange(EditorState.SAVED)
                onRetryChange(0)
                onSuccess()
                
                // 延迟恢复到 IDLE 状态
                delay(2000)
                onStateChange(EditorState.IDLE)
                return
            } else {
                lastError = result.exceptionOrNull()
                attempt++
                if (attempt < maxRetries) {
                    delay(retryDelayMs * attempt) // 指数退避
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Save timeout (attempt $attempt): ${e.message}")
            lastError = e
            attempt++
            if (attempt < maxRetries) {
                delay(retryDelayMs * attempt)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Save error (attempt $attempt): ${e.message}", e)
            lastError = e
            attempt++
            if (attempt < maxRetries) {
                delay(retryDelayMs * attempt)
            }
        }
    }
    
    // 所有重试失败
    onError("保存失败: ${lastError?.message ?: "网络错误"}")
    onStateChange(EditorState.ERROR)
    onRetryChange(0)
}

/**
 * 处理HTML内容，添加viewport
 */
private fun processHtmlContent(html: String): String {
    val viewportMeta = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
    
    val hasViewport = html.contains("<meta", ignoreCase = true) && 
                     html.contains("viewport", ignoreCase = true)
    
    return if (hasViewport) {
        html
    } else if (html.contains("<head>", ignoreCase = true)) {
        html.replace(
            Regex("<head>", RegexOption.IGNORE_CASE),
            "<head>\n    $viewportMeta"
        )
    } else if (html.contains("<html>", ignoreCase = true)) {
        html.replace(
            Regex("<html>", RegexOption.IGNORE_CASE),
            "<html>\n<head>\n    $viewportMeta\n</head>"
        )
    } else {
        "<html>\n<head>\n    $viewportMeta\n</head>\n<body>\n$html\n</body>\n</html>"
    }
}

/**
 * 判断是否显示预览
 */
private fun shouldShowPreview(extension: String): Boolean {
    return extension in listOf("html", "htm", "md", "markdown")
}

/**
 * 格式化时间
 */
private fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60000 -> "刚刚"
        diff < 3600000 -> "${diff / 60000}分钟前"
        else -> "${diff / 3600000}小时前"
    }
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

// ===== ViewModel 版本（可选）=====

/**
 * SSH编辑器ViewModel
 * 用于在更复杂的场景下管理编辑器状态
 */
class SshEditorViewModel(
    private val fileSystem: FileSystemBackend,
    private val config: EditorConfig = EditorConfig()
) {
    private val _editorState = MutableStateFlow(EditorState.IDLE)
    val editorState: StateFlow<EditorState> = _editorState.asStateFlow()
    
    private val _networkState = MutableStateFlow(NetworkState.UNKNOWN)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()
    
    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()
    
    private val _isModified = MutableStateFlow(false)
    val isModified: StateFlow<Boolean> = _isModified.asStateFlow()
    
    private var saveJob: Job? = null
    
    suspend fun loadFile(fileUri: String) {
        _editorState.value = EditorState.LOADING
        
        try {
            val result = fileSystem.readText(fileUri)
            result.onSuccess {
                _content.value = it
                _isModified.value = false
                _editorState.value = EditorState.IDLE
            }.onFailure {
                _editorState.value = EditorState.ERROR
            }
        } catch (e: Exception) {
            _editorState.value = EditorState.ERROR
        }
    }
    
    fun updateContent(newContent: String) {
        _content.value = newContent
        _isModified.value = true
        _editorState.value = EditorState.EDITING
        
        // 触发自动保存
        if (config.enableAutoSave) {
            scheduleAutoSave()
        }
    }
    
    private fun scheduleAutoSave() {
        saveJob?.cancel()
        saveJob = CoroutineScope(Dispatchers.IO).launch {
            delay(config.autoSaveDelayMs)
            // 执行保存...
        }
    }
}
