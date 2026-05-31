package com.rostrum.plugins.marketplace.preview.html

import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rostrum.core.domain.model.FileItem
import com.rostrum.ui.main.DragState
import com.rostrum.ui.main.PaneContentType
import com.rostrum.ui.main.PanePosition
import com.rostrum.core.mcp.*
import com.rostrum.core.plugin.Plugin
import com.rostrum.core.plugin.PluginCapability
import com.rostrum.core.plugin.PluginCategory
import com.rostrum.core.plugin.PluginContext
import com.rostrum.core.plugin.models.FileInfo
import com.rostrum.core.plugin.providers.FilePreviewPlugin
import com.rostrum.core.plugin.providers.PreviewMetadata
import com.rostrum.core.plugin.providers.PreviewResult
import com.rostrum.core.plugin.providers.ToolPlugin
import com.rostrum.core.event.EventBusImpl
import com.rostrum.core.event.EventSubscriber
import com.rostrum.core.event.FileModifiedEvent
import com.rostrum.core.event.FileContentUpdateEvent
import com.rostrum.core.filesystem.FileSystemService
import com.rostrum.core.util.PathUtils
import com.rostrum.ui.main.PaneBindingManager
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.security.MessageDigest

/**
 * 提供打开实时编辑界面回调的 CompositionLocal
 * 
 * 用于从 FilePreviewPanel 向 HtmlPreviewContent 传递回调
 */
val LocalOpenRealtimeEditor = compositionLocalOf<((String) -> Unit)?> { null }

/**
 * 提供在指定编辑器窗口打开文件的回调的 CompositionLocal
 * 
 * 用于从 FilePreviewPanel 向 HtmlPreviewContent 传递回调
 * 回调参数：(filePath: String, targetEditorPane: PanePosition)
 */
val LocalOpenInEditor = compositionLocalOf<((String, PanePosition) -> Unit)?> { null }

/**
 * 提供当前预览窗口位置的 CompositionLocal
 */
val LocalCurrentPanePosition = compositionLocalOf<PanePosition?> { null }

/**
 * HTML 文件预览插件
 * 
 * 插件商城插件 - 使用 WebView 预览 HTML 文件
 * 
 * 插件ID: com.rostrum.plugin.preview.html
 * 版本: 1.0.0
 * 作者: OmniMaster Team
 * 
 * 功能：
 * - HTML 文件实时预览
 * - 编辑按钮（可拖拽到其他窗口）
 * - 文件修改后自动刷新预览
 * - MCP 工具支持
 */
class HtmlPreviewPlugin : Plugin, FilePreviewPlugin, ToolPlugin {
    
    override val id: String = "com.rostrum.plugin.preview.html"
    override val name: String = "HTML Preview"
    override val version: String = "1.0.0"
    override val author: String = "OmniMaster Team"
    override val description: String = "提供 HTML 文件预览功能，支持实时预览和编辑"
    override val category: PluginCategory = PluginCategory.PREVIEW
    override val dependencies: List<String> = emptyList()
    
    override val supportedMimeTypes: List<String> = listOf(
        "text/html",
        "application/xhtml+xml"
    )
    
    override val supportedExtensions: List<String> = listOf(".html", ".htm", ".xhtml")
    
    private var pluginContext: PluginContext? = null
    
    // 文件监控状态（用于实时预览）
    private val fileWatchers = mutableMapOf<String, FileWatcher>()
    
    override suspend fun initialize(context: PluginContext): Result<Unit> {
        pluginContext = context
        return Result.success(Unit)
    }
    
    override suspend fun onActivate(): Result<Unit> {
        return Result.success(Unit)
    }
    
    override suspend fun onDeactivate(): Result<Unit> {
        return Result.success(Unit)
    }
    
    override suspend fun onDestroy(): Result<Unit> {
        // 清理所有文件监控
        fileWatchers.values.forEach { it.stop() }
        fileWatchers.clear()
        pluginContext = null
        return Result.success(Unit)
    }
    
    override fun getCapabilities(): List<PluginCapability> {
        return listOf(PluginCapability.FILE_PREVIEW)
    }
    
    override fun canPreview(file: FileInfo): Boolean {
        // 优先检查扩展名，确保 HTML 文件被正确识别
        val isHtmlExtension = supportedExtensions.any { 
            file.extension.equals(it, ignoreCase = true) 
        }
        val isHtmlMimeType = file.mimeType?.contains("html", ignoreCase = true) == true
        
        // 如果是 HTML 文件，返回 true
        if (isHtmlExtension || isHtmlMimeType) {
            return true
        }
        
        return false
    }
    
    /**
     * 是否支持远程文件预览
     */
    override fun supportsRemoteFiles(): Boolean = true
    
    override suspend fun createPreview(file: FileInfo): PreviewResult {
        if (!canPreview(file)) {
            return PreviewResult.Unsupported
        }
        
        val fileObj = File(file.path)
        if (!fileObj.exists()) {
            return PreviewResult.Error("文件不存在: ${file.path}")
        }
        
        return try {
            // 检查文件权限
            if (!fileObj.canRead()) {
                return PreviewResult.Error("无法读取文件，请检查文件权限")
            }
            
            // 创建预览组件
            val previewComponent: @Composable () -> Unit = {
                HtmlPreviewContent(fileObj, this@HtmlPreviewPlugin)
            }
            
            PreviewResult.Success(
                previewComponent = previewComponent,
                metadata = PreviewMetadata(
                    title = file.name,
                    description = "HTML 文档预览",
                    canEdit = true,
                    canExport = true
                )
            )
        } catch (e: SecurityException) {
            PreviewResult.Error("文件权限错误: ${e.message}", e)
        } catch (e: OutOfMemoryError) {
            PreviewResult.Error("内存不足，文件可能过大", e)
        } catch (e: Exception) {
            android.util.Log.e("HtmlPreview", "HTML预览失败", e)
            PreviewResult.Error("预览失败: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }
    
    override fun getPreviewPriority(file: FileInfo): Int {
        // HTML 文件应该优先使用 WebView 渲染，而不是代码预览
        // 设置更高的优先级，确保 HTML 文件使用 WebView 而不是代码预览
        return 200
    }
    
    /**
     * 创建预览组件（支持远程文件）
     */
    override suspend fun createPreview(file: FileInfo, fileSystem: FileSystemService): PreviewResult {
        if (!canPreview(file)) {
            return PreviewResult.Unsupported
        }
        
        // 远程文件：使用 FileSystemService 读取
        if (file.isRemote) {
            return try {
                val previewComponent: @Composable () -> Unit = {
                    HtmlRemotePreviewContent(
                        filePath = file.path,
                        fileName = file.name,
                        fileSystem = fileSystem,
                        plugin = this@HtmlPreviewPlugin
                    )
                }
                
                PreviewResult.Success(
                    previewComponent = previewComponent,
                    metadata = PreviewMetadata(
                        title = file.name,
                        description = "HTML 文档预览 (远程)",
                        canEdit = true,
                        canExport = true
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("HtmlPreview", "远程HTML预览失败", e)
                PreviewResult.Error("远程预览失败: ${e.message ?: e.javaClass.simpleName}", e)
            }
        }
        
        // 本地文件：使用传统方式
        return createPreview(file)
    }
    
    // ==================== ToolPlugin 实现 ====================
    
    override val toolCategory = ToolCategory.FILE_SYSTEM
    
    override fun getMCPTools(): List<MCPTool> {
        return listOf(
            MCPTool(
                name = "html_get_info",
                description = "获取HTML文件的详细信息",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "HTML文件路径")
                    ),
                    required = listOf("filePath")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "fileSize" to JsonSchemaProperty(type = "number", description = "文件大小（字节）"),
                        "lineCount" to JsonSchemaProperty(type = "number", description = "行数")
                    )
                ),
                handler = { args, context ->
                    try {
                        val filePath = args["filePath"] as? String
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少filePath参数")
                            )
                        
                        val file = File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "FILE_NOT_FOUND", message = "文件不存在: $filePath")
                            )
                        }
                        
                        val lineCount = withContext(Dispatchers.IO) {
                            file.readLines().size
                        }
                        
                        MCPResult(
                            success = true,
                            data = mapOf(
                                "fileSize" to file.length(),
                                "lineCount" to lineCount
                            )
                        )
                    } catch (e: Exception) {
                        MCPResult(
                            success = false,
                            error = MCPError(code = "READ_FAILED", message = "读取失败: ${e.message}")
                        )
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE)
            ),
            MCPTool(
                name = "html_extract_text",
                description = "从HTML文件中提取纯文本内容（去除HTML标签）",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "HTML文件路径")
                    ),
                    required = listOf("filePath")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "text" to JsonSchemaProperty(type = "string", description = "提取的文本内容")
                    )
                ),
                handler = { args, context ->
                    try {
                        val filePath = args["filePath"] as? String
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少filePath参数")
                            )
                        
                        val file = File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "FILE_NOT_FOUND", message = "文件不存在: $filePath")
                            )
                        }
                        
                        val htmlContent = withContext(Dispatchers.IO) {
                            file.readText()
                        }
                        
                        // 简单的HTML标签去除（实际项目中可以使用更完善的HTML解析库）
                        val text = htmlContent
                            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
                            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
                            .replace(Regex("<[^>]+>"), "")
                            .replace("&nbsp;", " ")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&amp;", "&")
                            .replace("&quot;", "\"")
                            .replace("&#39;", "'")
                            .trim()
                        
                        MCPResult(
                            success = true,
                            data = mapOf("text" to text)
                        )
                    } catch (e: Exception) {
                        MCPResult(
                            success = false,
                            error = MCPError(code = "EXTRACTION_FAILED", message = "提取失败: ${e.message}")
                        )
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE)
            )
        )
    }
    
    /**
     * 注册文件监控（用于实时预览）
     * 
     * @param filePath 监控的文件路径
     * @param onChanged 文件保存后的刷新回调
     * @param onContentUpdate 内容更新回调（实时预览，无需保存文件）
     */
    internal fun registerFileWatcher(
        filePath: String, 
        onChanged: () -> Unit,
        onContentUpdate: ((String) -> Unit)? = null
    ): FileWatcher {
        val watcher = FileWatcher(filePath, onChanged, onContentUpdate)
        fileWatchers[filePath] = watcher
        watcher.start()
        return watcher
    }
    
    /**
     * 取消文件监控
     */
    internal fun unregisterFileWatcher(filePath: String) {
        fileWatchers[filePath]?.stop()
        fileWatchers.remove(filePath)
    }
}

/**
 * HTML 预览内容组件
 * 
 * @param file 预览的文件
 * @param plugin 插件实例
 */
@Composable
private fun HtmlPreviewContent(
    file: File,
    plugin: HtmlPreviewPlugin
) {
    val context = LocalContext.current
    
    // 从 CompositionLocal 获取回调和当前窗口位置
    val onOpenRealtimeEditor = LocalOpenRealtimeEditor.current
    val onOpenInEditor = LocalOpenInEditor.current
    val currentPanePosition = LocalCurrentPanePosition.current
    
    var webView: WebView? by remember { mutableStateOf(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    // 视口尺寸状态（用于模拟不同设备，默认 iPad 尺寸）
    var viewportWidth by remember { mutableIntStateOf(768) }
    var viewportHeight by remember { mutableIntStateOf(1024) }
    var showViewportSettings by remember { mutableStateOf(false) }
    
    // 缩放和偏移状态（用于手势缩放）
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    // HTML 内容缓存
    var htmlContent by remember { mutableStateOf<String?>(null) }
    var baseUrl by remember { mutableStateOf<String?>(null) }
    var processedHtmlContent by remember { mutableStateOf<String?>(null) }
    
    // 待加载内容缓存（用于 WebView 未初始化时缓存内容）
    var pendingContent by remember { mutableStateOf<String?>(null) }
    
    // 文件修改时间（用于检测文件变化）
    var lastModified by remember { mutableStateOf(file.lastModified()) }
    
    // 获取 MIME 类型
    fun getMimeTypeFromExtension(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "bmp" -> "image/bmp"
            "ico" -> "image/x-icon"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "eot" -> "application/vnd.ms-fontobject"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) 
                ?: "application/octet-stream"
        }
    }
    
    // 处理 HTML 内容，添加或修改 viewport meta 标签
    fun processHtmlContent(html: String, width: Int, height: Int): String {
        val viewportMeta = "<meta name=\"viewport\" content=\"width=$width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">"
        
        // 检查是否已有 viewport meta 标签
        val hasViewport = html.contains("<meta", ignoreCase = true) && 
                         html.contains("viewport", ignoreCase = true)
        
        return if (hasViewport) {
            // 替换现有的 viewport meta 标签
            html.replace(
                Regex("<meta[^>]*name=['\"]viewport['\"][^>]*>", RegexOption.IGNORE_CASE),
                viewportMeta
            )
        } else {
            // 在 <head> 标签后添加 viewport meta 标签
            if (html.contains("<head>", ignoreCase = true)) {
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
                // 如果没有 head 标签，在开头添加
                "$viewportMeta\n$html"
            }
        }
    }
    
    // 异步加载 HTML 文件内容
    LaunchedEffect(file.absolutePath, viewportWidth, viewportHeight) {
        try {
            val content = withContext(Dispatchers.IO) {
                file.readText()
            }
            val url = "file://${file.parentFile?.absolutePath}/"
            htmlContent = content
            baseUrl = url
            
            // 处理 HTML 内容，添加 viewport
            val processed = processHtmlContent(content, viewportWidth, viewportHeight)
            processedHtmlContent = processed
            
            // 如果 WebView 已经创建，立即加载内容
            webView?.let { view ->
                // 计算缩放比例
                val screenWidthPx = context.resources.displayMetrics.widthPixels
                val scaleRatio = screenWidthPx.toFloat() / viewportWidth.toFloat()
                view.setInitialScale((scaleRatio * 100).toInt())
                view.loadDataWithBaseURL(url, processed, "text/html", "UTF-8", null)
            }
        } catch (e: SecurityException) {
            error = "文件访问被拒绝: ${e.message}"
            isLoading = false
        } catch (e: Exception) {
            error = "读取文件失败: ${e.message}"
            isLoading = false
        }
    }
    
    // 内容更新函数（用于实时预览，无需保存文件）
    fun updatePreviewContent(content: String) {
        // 如果 WebView 未初始化，缓存内容等待后续加载
        if (webView == null) {
            android.util.Log.d("HtmlPreview", "WebView not initialized yet, caching content for later")
            pendingContent = content
            return
        }
        
        // 清除待加载内容缓存
        pendingContent = null
        
        scope.launch {
            try {
                val url = "file://${file.parentFile?.absolutePath}/"
                val processed = processHtmlContent(content, viewportWidth, viewportHeight)
                
                withContext(Dispatchers.Main) {
                    webView?.let { view ->
                        val screenWidthPx = context.resources.displayMetrics.widthPixels
                        val scaleRatio = screenWidthPx.toFloat() / viewportWidth.toFloat()
                        view.setInitialScale((scaleRatio * 100).toInt())
                        view.loadDataWithBaseURL(url, processed, "text/html", "UTF-8", null)
                        android.util.Log.d("HtmlPreview", "Preview updated with new content (realtime)")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HtmlPreview", "Failed to update preview content: ${e.message}", e)
            }
        }
    }
    
    // 注册文件监控 - 等待 WebView 初始化后再注册
    LaunchedEffect(file.absolutePath, webView) {
        // 只有当 WebView 已初始化时才注册文件监控
        if (webView == null) {
            android.util.Log.d("HtmlPreview", "WebView not initialized yet, delaying file watcher registration")
            return@LaunchedEffect
        }
        
        // 先清理可能存在的旧监控（确保文件路径变化时旧监控被正确清理）
        plugin.unregisterFileWatcher(file.absolutePath)
        android.util.Log.d("HtmlPreview", "Registering file watcher after WebView initialized: ${file.absolutePath}")
        
        plugin.registerFileWatcher(
            filePath = file.absolutePath,
            onChanged = {
                // 文件修改后刷新预览
                val newModified = file.lastModified()
                if (newModified > lastModified) {
                    lastModified = newModified
                    android.util.Log.d("HtmlPreview", "File modified, reloading preview: ${file.absolutePath}")
                    
                    // 使用 loadDataWithBaseURL 重新加载内容，而不是简单的 reload
                    scope.launch {
                        try {
                            val content = withContext(Dispatchers.IO) {
                                file.readText()
                            }
                            val url = "file://${file.parentFile?.absolutePath}/"
                            val processed = processHtmlContent(content, viewportWidth, viewportHeight)
                            
                            withContext(Dispatchers.Main) {
                                webView?.let { view ->
                                    val screenWidthPx = context.resources.displayMetrics.widthPixels
                                    val scaleRatio = screenWidthPx.toFloat() / viewportWidth.toFloat()
                                    view.setInitialScale((scaleRatio * 100).toInt())
                                    view.loadDataWithBaseURL(url, processed, "text/html", "UTF-8", null)
                                    android.util.Log.d("HtmlPreview", "Preview reloaded successfully")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("HtmlPreview", "Failed to reload preview: ${e.message}", e)
                            // 如果重新加载失败，回退到简单的 reload
                            withContext(Dispatchers.Main) {
                                webView?.reload()
                            }
                        }
                    }
                }
            },
            onContentUpdate = { content ->
                // 实时预览回调：直接使用内容更新 WebView，无需读取文件
                updatePreviewContent(content)
            }
        )
    }
    
    // 清理文件监控
    DisposableEffect(file.absolutePath) {
        onDispose {
            plugin.unregisterFileWatcher(file.absolutePath)
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 工具栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 2.dp
        ) {
            // 工具栏支持左右滑动，固定高度
            val toolbarScrollState = rememberScrollState()
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .horizontalScroll(toolbarScrollState)  // 启用水平滚动
                    .padding(horizontal = 12.dp),  // 固定高度样式
                horizontalArrangement = Arrangement.spacedBy(8.dp),  // 元素间等间距
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：图标和标题
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Language,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "HTML 预览",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                
                // 弹性空间，保持右侧按钮右对齐
                Spacer(Modifier.weight(1f))
                
                // 右侧：控制按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 视口尺寸选择按钮
                    IconButton(
                        onClick = {
                            showViewportSettings = !showViewportSettings
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Devices,
                            contentDescription = "设备尺寸",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // 导出为图片按钮
                    var isExporting by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            val wv = webView
                            if (wv != null && !isExporting) {
                                isExporting = true
                                exportWebViewToImage(wv, file.nameWithoutExtension, context) {
                                    isExporting = false
                                }
                            }
                        },
                        enabled = !isExporting,
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Filled.Image,
                                contentDescription = "导出为图片",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 刷新按钮
                    IconButton(
                        onClick = {
                            webView?.reload()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "刷新",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // 实时编辑按钮（可拖拽）
                    var dragStartPosition by remember { mutableStateOf<Offset?>(null) }
                    var isDraggingButton by remember { mutableStateOf(false) }
                    // 记录按钮的全局位置，用于正确计算拖拽坐标
                    var buttonGlobalPosition by remember { mutableStateOf(Offset.Zero) }
                    
                    // 打开实时编辑界面的函数（集成到三窗口系统）
                    val openRealtimeEditorAction: () -> Unit = {
                        android.util.Log.d("HtmlPreview", "请求打开实时编辑: ${file.absolutePath}")
                        
                        // 查找可用的编辑器窗口（排除当前预览窗口）
                        val allPanes = listOf(PanePosition.TOP_LEFT, PanePosition.TOP_RIGHT, PanePosition.BOTTOM)
                        val availablePanes = allPanes.filter { it != currentPanePosition }
                        
                        // 优先选择第一个可用窗口作为编辑器
                        val targetEditorPane = availablePanes.firstOrNull()
                        
                        if (targetEditorPane != null && onOpenInEditor != null && currentPanePosition != null) {
                            // 在编辑器窗口打开文件
                            android.util.Log.d("HtmlPreview", "在编辑器窗口打开: pane=$targetEditorPane, file=${file.absolutePath}")
                            onOpenInEditor.invoke(file.absolutePath, targetEditorPane)
                            
                            // 建立编辑器-预览窗口绑定
                            com.rostrum.ui.main.PaneBindingManager.bindEditorToPreview(targetEditorPane, currentPanePosition)
                            android.util.Log.d("HtmlPreview", "已建立绑定: editor=$targetEditorPane, preview=$currentPanePosition")
                        } else {
                            // 后备方案：打开全屏编辑界面
                            android.util.Log.d("HtmlPreview", "使用全屏编辑界面（后备方案）")
                            onOpenRealtimeEditor?.invoke(file.absolutePath)
                        }
                    }
                    
                    // 打开普通编辑器的函数（用于拖拽到编辑器窗口）
                    val openEditorAction: (PanePosition?) -> Unit = { targetPane ->
                        android.util.Log.d("HtmlPreview", "请求打开编辑器: ${file.absolutePath}, targetPane=$targetPane")
                        if (targetPane != null && onOpenInEditor != null && currentPanePosition != null) {
                            // 在指定窗口打开文件
                            onOpenInEditor.invoke(file.absolutePath, targetPane)
                            // 建立编辑器-预览窗口绑定
                            com.rostrum.ui.main.PaneBindingManager.bindEditorToPreview(targetPane, currentPanePosition)
                        }
                    }
                    
                    FilledTonalButton(
                        onClick = {
                            // 点击编辑按钮 - 打开实时编辑界面
                            openRealtimeEditorAction()
                        },
                        modifier = Modifier
                            .onGloballyPositioned { layoutCoordinates ->
                                // 记录按钮的全局位置
                                buttonGlobalPosition = layoutCoordinates.localToRoot(Offset.Zero)
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        dragStartPosition = offset
                                        isDraggingButton = true
                                        // 创建文件项用于拖拽
                                        val fileItem = FileItem(file = file)
                                        // 转换为全局坐标
                                        val globalStart = buttonGlobalPosition + offset
                                        // 使用全局拖拽状态，传入当前窗口位置
                                        DragState.startDrag(
                                            fileItem,
                                            globalStart,
                                            currentPanePosition ?: PanePosition.BOTTOM
                                        )
                                        android.util.Log.d("HtmlPreview", "拖拽开始: globalStart=$globalStart, buttonPos=$buttonGlobalPosition")
                                    },
                                    onDrag = { change, dragAmount ->
                                        // 更新拖拽位置（转换为全局坐标）
                                        val globalPosition = buttonGlobalPosition + change.position
                                        DragState.updateDragPosition(globalPosition)
                                    },
                                    onDragEnd = {
                                        // 拖拽结束 - 检查是否拖到了编辑器窗口
                                        val targetPane = DragState.dragTargetPane
                                        val targetContentType = DragState.dragTargetContentType
                                        
                                        android.util.Log.d("HtmlPreview", "拖拽结束: targetPane=$targetPane, targetContentType=$targetContentType")
                                        
                                        if (targetPane != null && currentPanePosition != null) {
                                            // 拖到了目标窗口，在该窗口打开文件
                                            openEditorAction(targetPane)
                                            android.util.Log.d("HtmlPreview", "拖拽到窗口: ${file.absolutePath}, pane=$targetPane")
                                        } else {
                                            // 没有有效的目标窗口，查找默认窗口
                                            val defaultPane = listOf(PanePosition.TOP_LEFT, PanePosition.TOP_RIGHT, PanePosition.BOTTOM)
                                                .filter { it != currentPanePosition }
                                                .firstOrNull()
                                            openEditorAction(defaultPane)
                                        }
                                        
                                        DragState.endDrag()
                                        isDraggingButton = false
                                        dragStartPosition = null
                                    },
                                    onDragCancel = {
                                        DragState.cancelDrag()
                                        isDraggingButton = false
                                        dragStartPosition = null
                                    }
                                )
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "实时编辑（长按拖拽到其他窗口）",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "实时编辑",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
        
        // 视口尺寸设置对话框
        if (showViewportSettings) {
            AlertDialog(
                onDismissRequest = { showViewportSettings = false },
                title = { Text("设备尺寸设置") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 预设设备尺寸
                        Text(
                            text = "预设设备：",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 手机
                            FilterChip(
                                selected = viewportWidth == 375 && viewportHeight == 667,
                                onClick = {
                                    viewportWidth = 375
                                    viewportHeight = 667
                                },
                                label = { Text("iPhone") }
                            )
                            // 平板
                            FilterChip(
                                selected = viewportWidth == 768 && viewportHeight == 1024,
                                onClick = {
                                    viewportWidth = 768
                                    viewportHeight = 1024
                                },
                                label = { Text("iPad") }
                            )
                            // 桌面
                            FilterChip(
                                selected = viewportWidth == 1920 && viewportHeight == 1080,
                                onClick = {
                                    viewportWidth = 1920
                                    viewportHeight = 1080
                                },
                                label = { Text("桌面") }
                            )
                        }
                        
                        Divider()
                        
                        // 自定义尺寸
                        Text(
                            text = "自定义尺寸：",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "宽度: $viewportWidth px",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Slider(
                                value = viewportWidth.toFloat(),
                                onValueChange = { viewportWidth = it.toInt().coerceIn(320, 3840) },
                                valueRange = 320f..3840f,
                                steps = 35
                            )
                        }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "高度: $viewportHeight px",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Slider(
                                value = viewportHeight.toFloat(),
                                onValueChange = { viewportHeight = it.toInt().coerceIn(480, 2160) },
                                valueRange = 480f..2160f,
                                steps = 33
                            )
                        }
                        
                        Text(
                            text = "当前尺寸: ${viewportWidth} × ${viewportHeight} px",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showViewportSettings = false }) {
                        Text("确定")
                    }
                }
            )
        }
        
        // WebView 内容（带手势缩放）
        Box(modifier = Modifier.fillMaxSize()) {
            // 缩放和偏移容器
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(Unit) {
                        // 双指缩放手势（支持缩放和拖拽）
                        detectTransformGestures { centroid, pan, zoom, rotation ->
                            // 缩放
                            scale = (scale * zoom).coerceIn(0.5f, 5.0f)
                            // 拖拽移动（仅在缩放后可用）
                            if (scale > 1.0f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            // 禁用 WebView 内置的缩放控件，使用自定义手势缩放
                            settings.builtInZoomControls = false
                            settings.displayZoomControls = false
                            settings.setSupportZoom(false) // 禁用 WebView 的缩放支持，使用 Compose 的缩放
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            settings.allowFileAccessFromFileURLs = true
                            settings.allowUniversalAccessFromFileURLs = true
                            
                            // 允许 WebView 内部滚动，但禁用过度滚动效果
                            overScrollMode = android.view.View.OVER_SCROLL_NEVER
                            
                            // 设置触摸事件拦截，防止与侧边栏手势冲突
                            setOnTouchListener { view, event ->
                                // 允许 WebView 处理触摸事件
                                val handled = view.onTouchEvent(event)
                                
                                // 如果 WebView 正在滚动，消费事件，防止触发侧边栏
                                when (event.action) {
                                    android.view.MotionEvent.ACTION_DOWN -> {
                                        // 记录触摸开始，阻止父视图拦截触摸事件
                                        parent?.requestDisallowInterceptTouchEvent(true)
                                    }
                                    android.view.MotionEvent.ACTION_MOVE -> {
                                        // 如果 WebView 可以滚动，继续阻止父视图拦截
                                        if (canScrollVertically(1) || canScrollVertically(-1) ||
                                            canScrollHorizontally(1) || canScrollHorizontally(-1)) {
                                            parent?.requestDisallowInterceptTouchEvent(true)
                                        }
                                    }
                                    android.view.MotionEvent.ACTION_UP,
                                    android.view.MotionEvent.ACTION_CANCEL -> {
                                        // 触摸结束，允许父视图拦截触摸事件
                                        parent?.requestDisallowInterceptTouchEvent(false)
                                    }
                                }
                                handled
                            }
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                error = null
                            }
                            
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                            }
                            
                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?
                            ) {
                                super.onReceivedError(view, errorCode, description, failingUrl)
                                isLoading = false
                                error = "加载失败: $description (错误代码: $errorCode)"
                            }
                            
                            // 拦截本地文件请求，支持相对路径图片
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url ?: return null
                                
                                // 只处理 file:// 协议的请求
                                if (url.scheme == "file") {
                                    try {
                                        val localPath = url.path ?: return null
                                        val localFile = java.io.File(localPath)
                                        
                                        if (localFile.exists() && localFile.isFile) {
                                            val extension = localFile.extension.lowercase()
                                            val mimeType = getMimeTypeFromExtension(extension)
                                            
                                            android.util.Log.d("HtmlPreview", "Intercepting local file: $localPath, mime: $mimeType")
                                            
                                            return WebResourceResponse(
                                                mimeType,
                                                "UTF-8",
                                                localFile.inputStream()
                                            )
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("HtmlPreview", "Error loading local file: ${url.path}", e)
                                    }
                                }
                                
                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                        
                        webView = this
                        
                        // 设置 WebView 的初始缩放和视口
                        // 计算缩放比例，使网页按指定视口宽度显示
                        val screenWidthPx = resources.displayMetrics.widthPixels
                        val scaleRatio = screenWidthPx.toFloat() / viewportWidth.toFloat()
                        
                        // 设置初始缩放
                        setInitialScale((scaleRatio * 100).toInt())
                        
                        // 优先加载待加载的内容（实时预览缓存）
                        val pending = pendingContent
                        if (pending != null) {
                            android.util.Log.d("HtmlPreview", "WebView initialized, loading pending content")
                            val url = "file://${file.parentFile?.absolutePath}/"
                            val processed = processHtmlContent(pending, viewportWidth, viewportHeight)
                            loadDataWithBaseURL(url, processed, "text/html", "UTF-8", null)
                            pendingContent = null
                        } else {
                            // 如果没有待加载内容，检查已加载的 HTML 内容
                            processedHtmlContent?.let { content ->
                                baseUrl?.let { url ->
                                    loadDataWithBaseURL(
                                        url,
                                        content,
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    // 如果文件修改时间改变，重新加载
                    val currentModified = file.lastModified()
                    if (currentModified > lastModified) {
                        lastModified = currentModified
                        // 重新读取文件内容
                        scope.launch {
                            try {
                                val content = withContext(Dispatchers.IO) {
                                    file.readText()
                                }
                                val url = "file://${file.parentFile?.absolutePath}/"
                                htmlContent = content
                                baseUrl = url
                                
                                // 处理 HTML 内容
                                val processed = processHtmlContent(content, viewportWidth, viewportHeight)
                                processedHtmlContent = processed
                                
                                // 计算缩放比例
                                val screenWidthPx = context.resources.displayMetrics.widthPixels
                                val scaleRatio = screenWidthPx.toFloat() / viewportWidth.toFloat()
                                view.setInitialScale((scaleRatio * 100).toInt())
                                
                                view.loadDataWithBaseURL(url, processed, "text/html", "UTF-8", null)
                            } catch (e: Exception) {
                                error = "重新加载失败: ${e.message}"
                            }
                        }
                    }
                }
            )
            }
            
            // 加载指示器
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // 错误提示
            if (error != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * 文件监控器（用于实时预览）
 * 
 * 优化特性：
 * - 100ms 轮询间隔（快速响应）
 * - 内容哈希检测（避免无效刷新）
 * - EventBus 事件订阅（立即响应文件修改事件）
 * - FileContentUpdateEvent 订阅（实时预览，无需保存文件）
 * 
 * @param filePath 监控的文件路径
 * @param onChanged 文件保存后的刷新回调
 * @param onContentUpdate 内容更新回调（实时预览，无需保存文件）
 */
internal class FileWatcher(
    private val filePath: String,
    private val onChanged: () -> Unit,
    private val onContentUpdate: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "FileWatcher"
        private const val POLL_INTERVAL_MS = 100L  // 减少到100ms
    }
    
    private var isRunning = false
    private var lastModified = 0L
    private var lastContentHash: String = ""
    private var watchJob: Job? = null
    private var eventSubscriber: EventSubscriber<FileModifiedEvent>? = null
    private var contentUpdateSubscriber: EventSubscriber<FileContentUpdateEvent>? = null
    
    /**
     * 计算文件内容的哈希值
     */
    private fun calculateContentHash(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val content = file.readBytes()
            val hashBytes = digest.digest(content)
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // 如果计算失败，返回空字符串，后备使用时间戳检测
            ""
        }
    }
    
    fun start() {
        isRunning = true
        val file = File(filePath)
        if (file.exists()) {
            lastModified = file.lastModified()
            lastContentHash = calculateContentHash(file)
        }
        
        // 订阅 EventBus 的 FileModifiedEvent 事件
        val eventBus = EventBusImpl.getInstance()
        eventSubscriber = object : EventSubscriber<FileModifiedEvent> {
            override suspend fun onEvent(event: FileModifiedEvent) {
                // 使用 PathUtils 规范化路径进行比较
                if (PathUtils.pathsEqual(event.filePath, filePath)) {
                    android.util.Log.d(TAG, "Received FileModifiedEvent for: $filePath (matched with event path: ${event.filePath})")
                    // 立即触发刷新（通过事件总线）
                    val currentFile = File(filePath)
                    if (currentFile.exists()) {
                        val newHash = calculateContentHash(currentFile)
                        if (newHash.isNotEmpty() && newHash != lastContentHash) {
                            lastContentHash = newHash
                            lastModified = currentFile.lastModified()
                            android.util.Log.d(TAG, "Content hash changed, triggering refresh for: $filePath")
                            withContext(Dispatchers.Main) {
                                onChanged()
                            }
                        } else if (newHash.isEmpty()) {
                            // 如果哈希计算失败，使用时间戳
                            val currentModified = currentFile.lastModified()
                            if (currentModified > lastModified) {
                                lastModified = currentModified
                                android.util.Log.d(TAG, "Timestamp changed, triggering refresh for: $filePath")
                                withContext(Dispatchers.Main) {
                                    onChanged()
                                }
                            }
                        } else {
                            android.util.Log.d(TAG, "Content hash unchanged, skipping refresh for: $filePath")
                        }
                    }
                } else {
                    android.util.Log.d(TAG, "Event path mismatch: event=${event.filePath}, watch=$filePath")
                }
            }
        }
        eventBus.subscribe(FileModifiedEvent::class, eventSubscriber!!, priority = 10)
        android.util.Log.d(TAG, "Subscribed to FileModifiedEvent for: $filePath")
        
        // 订阅 FileContentUpdateEvent（实时预览，无需保存文件）
        if (onContentUpdate != null) {
            contentUpdateSubscriber = object : EventSubscriber<FileContentUpdateEvent> {
                override suspend fun onEvent(event: FileContentUpdateEvent) {
                    // 使用 PathUtils 规范化路径进行比较
                    if (PathUtils.pathsEqual(event.filePath, filePath)) {
                        android.util.Log.d(TAG, "Received FileContentUpdateEvent for: $filePath")
                        // 直接使用事件中的内容更新预览，无需读取文件
                        withContext(Dispatchers.Main) {
                            onContentUpdate.invoke(event.content)
                        }
                    }
                }
            }
            eventBus.subscribe(FileContentUpdateEvent::class, contentUpdateSubscriber!!, priority = 15)
            android.util.Log.d(TAG, "Subscribed to FileContentUpdateEvent for: $filePath")
        }
        
        // 使用协程定期检查文件修改（作为事件总线的补充）
        watchJob = CoroutineScope(Dispatchers.IO).launch {
            while (isRunning) {
                try {
                    val currentFile = File(filePath)
                    if (currentFile.exists()) {
                        val currentModified = currentFile.lastModified()
                        if (currentModified > lastModified) {
                            // 时间戳变化，检查内容哈希
                            val newHash = calculateContentHash(currentFile)
                            if (newHash.isNotEmpty() && newHash != lastContentHash) {
                                // 内容确实变化了
                                lastContentHash = newHash
                                lastModified = currentModified
                                android.util.Log.d(TAG, "Content changed (poll): $filePath")
                                withContext(Dispatchers.Main) {
                                    onChanged()
                                }
                            } else if (newHash.isEmpty()) {
                                // 哈希计算失败，使用时间戳
                                lastModified = currentModified
                                android.util.Log.d(TAG, "Content changed (poll, timestamp): $filePath")
                                withContext(Dispatchers.Main) {
                                    onChanged()
                                }
                            } else {
                                // 时间戳变化但内容未变，只更新时间戳
                                lastModified = currentModified
                                android.util.Log.d(TAG, "Timestamp changed but content same: $filePath")
                            }
                        }
                    }
                    delay(POLL_INTERVAL_MS)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "文件监控错误", e)
                    delay(1000) // 出错后延迟更长时间
                }
            }
        }
    }
    
    fun stop() {
        isRunning = false
        watchJob?.cancel()
        watchJob = null
        
        // 取消事件订阅
        eventSubscriber?.let { subscriber ->
            EventBusImpl.getInstance().unsubscribe(FileModifiedEvent::class, subscriber)
            android.util.Log.d(TAG, "Unsubscribed from FileModifiedEvent for: $filePath")
        }
        eventSubscriber = null
        
        // 取消内容更新事件订阅
        contentUpdateSubscriber?.let { subscriber ->
            EventBusImpl.getInstance().unsubscribe(FileContentUpdateEvent::class, subscriber)
            android.util.Log.d(TAG, "Unsubscribed from FileContentUpdateEvent for: $filePath")
        }
        contentUpdateSubscriber = null
    }
}

/**
 * 远程 HTML 文件预览组件
 * 
 * 通过 FileSystemService 读取SSH远程文件内容
 */
@Composable
private fun HtmlRemotePreviewContent(
    filePath: String,
    fileName: String,
    fileSystem: FileSystemService,
    plugin: HtmlPreviewPlugin
) {
    val context = LocalContext.current
    
    // 从 CompositionLocal 获取回调和当前窗口位置
    val onOpenRealtimeEditor = LocalOpenRealtimeEditor.current
    val onOpenInEditor = LocalOpenInEditor.current
    val currentPanePosition = LocalCurrentPanePosition.current
    
    var webView: WebView? by remember { mutableStateOf(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    // 视口尺寸状态（用于模拟不同设备，默认 iPad 尺寸）
    var viewportWidth by remember { mutableIntStateOf(768) }
    var viewportHeight by remember { mutableIntStateOf(1024) }
    var showViewportSettings by remember { mutableStateOf(false) }
    
    // 缩放和偏移状态（用于手势缩放）
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    // HTML 内容缓存
    var htmlContent by remember { mutableStateOf<String?>(null) }
    var processedHtmlContent by remember { mutableStateOf<String?>(null) }
    
    // 待加载内容缓存（用于 WebView 未初始化时缓存内容）
    var pendingContent by remember { mutableStateOf<String?>(null) }
    
    // 处理 HTML 内容，添加或修改 viewport meta 标签
    fun processHtmlContent(html: String, width: Int, height: Int): String {
        val viewportMeta = "<meta name=\"viewport\" content=\"width=$width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">"
        
        // 检查是否已有 viewport meta 标签
        val hasViewport = html.contains("<meta", ignoreCase = true) && 
                         html.contains("viewport", ignoreCase = true)
        
        return if (hasViewport) {
            // 替换现有的 viewport meta 标签
            html.replace(
                Regex("<meta[^>]*name=['\"]viewport['\"][^>]*>", RegexOption.IGNORE_CASE),
                viewportMeta
            )
        } else {
            // 在 <head> 标签后添加 viewport meta 标签
            if (html.contains("<head>", ignoreCase = true)) {
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
                // 如果没有 head 标签，在开头添加
                "$viewportMeta\n$html"
            }
        }
    }
    
    // 异步从远程文件系统读取 HTML 内容
    LaunchedEffect(filePath, viewportWidth, viewportHeight) {
        isLoading = true
        error = null
        
        withContext(Dispatchers.IO) {
            try {
                val result = fileSystem.readTextFile(filePath)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        val content = result.getOrThrow()
                        htmlContent = content
                        
                        // 处理 HTML 内容，添加 viewport
                        val processed = processHtmlContent(content, viewportWidth, viewportHeight)
                        processedHtmlContent = processed
                        
                        // 如果 WebView 已经创建，立即加载内容
                        webView?.let { view ->
                            // 计算缩放比例
                            val screenWidthPx = context.resources.displayMetrics.widthPixels
                            val scaleRatio = screenWidthPx.toFloat() / viewportWidth.toFloat()
                            view.setInitialScale((scaleRatio * 100).toInt())
                            // 使用 data URL 加载，远程文件没有本地 baseUrl
                            view.loadDataWithBaseURL(null, processed, "text/html", "UTF-8", null)
                        }
                        isLoading = false
                    } else {
                        error = "读取失败: ${result.exceptionOrNull()?.message}"
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = "读取远程文件失败: ${e.message}"
                    isLoading = false
                }
            }
        }
    }
    
    // 内容更新函数（用于实时预览，无需保存文件）
    fun updatePreviewContent(content: String) {
        // 如果 WebView 未初始化，缓存内容等待后续加载
        if (webView == null) {
            android.util.Log.d("HtmlPreview", "WebView not initialized yet (remote), caching content for later")
            pendingContent = content
            return
        }
        
        // 清除待加载内容缓存
        pendingContent = null
        
        scope.launch {
            try {
                val processed = processHtmlContent(content, viewportWidth, viewportHeight)
                
                withContext(Dispatchers.Main) {
                    webView?.let { view ->
                        val screenWidthPx = context.resources.displayMetrics.widthPixels
                        val scaleRatio = screenWidthPx.toFloat() / viewportWidth.toFloat()
                        view.setInitialScale((scaleRatio * 100).toInt())
                        view.loadDataWithBaseURL(null, processed, "text/html", "UTF-8", null)
                        android.util.Log.d("HtmlPreview", "Remote preview updated with new content (realtime)")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HtmlPreview", "Failed to update remote preview content: ${e.message}", e)
            }
        }
    }
    
    // 订阅 FileContentUpdateEvent 实现实时预览（远程文件也支持）
    DisposableEffect(filePath, webView) {
        if (webView == null) {
            return@DisposableEffect onDispose { }
        }
        
        val eventBus = EventBusImpl.getInstance()
        val normalizedPath = PathUtils.normalizePath(filePath)
        
        val subscriber = object : EventSubscriber<FileContentUpdateEvent> {
            override suspend fun onEvent(event: FileContentUpdateEvent) {
                if (PathUtils.pathsEqual(event.filePath, normalizedPath)) {
                    android.util.Log.d("HtmlPreview", "Received FileContentUpdateEvent for remote: $fileName")
                    withContext(Dispatchers.Main) {
                        updatePreviewContent(event.content)
                    }
                }
            }
        }
        
        eventBus.subscribe(FileContentUpdateEvent::class, subscriber, priority = 15)
        android.util.Log.d("HtmlPreview", "Subscribed to FileContentUpdateEvent for remote: $filePath")
        
        onDispose {
            eventBus.unsubscribe(FileContentUpdateEvent::class, subscriber)
            android.util.Log.d("HtmlPreview", "Unsubscribed from FileContentUpdateEvent for remote: $filePath")
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 工具栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 2.dp
        ) {
            // 工具栏支持左右滑动，固定高度
            val toolbarScrollState = rememberScrollState()
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .horizontalScroll(toolbarScrollState)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：图标和标题
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Language,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "HTML 预览 (远程)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                
                // 弹性空间，保持右侧按钮右对齐
                Spacer(Modifier.weight(1f))
                
                // 右侧：控制按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 视口尺寸选择按钮
                    IconButton(
                        onClick = {
                            showViewportSettings = !showViewportSettings
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Devices,
                            contentDescription = "设备尺寸",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // 刷新按钮 - 重新从远程读取内容
                    IconButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                error = null
                                withContext(Dispatchers.IO) {
                                    try {
                                        val result = fileSystem.readTextFile(filePath)
                                        withContext(Dispatchers.Main) {
                                            if (result.isSuccess) {
                                                val content = result.getOrThrow()
                                                htmlContent = content
                                                val processed = processHtmlContent(content, viewportWidth, viewportHeight)
                                                processedHtmlContent = processed
                                                webView?.let { view ->
                                                    val screenWidthPx = context.resources.displayMetrics.widthPixels
                                                    val scaleRatio = screenWidthPx.toFloat() / viewportWidth.toFloat()
                                                    view.setInitialScale((scaleRatio * 100).toInt())
                                                    view.loadDataWithBaseURL(null, processed, "text/html", "UTF-8", null)
                                                }
                                                isLoading = false
                                            } else {
                                                error = "刷新失败: ${result.exceptionOrNull()?.message}"
                                                isLoading = false
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            error = "刷新失败: ${e.message}"
                                            isLoading = false
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "刷新",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // 实时编辑按钮
                    // 打开实时编辑界面的函数（集成到三窗口系统）
                    val openRealtimeEditorAction: () -> Unit = {
                        android.util.Log.d("HtmlPreview", "请求打开远程实时编辑: $filePath")
                        
                        // 查找可用的编辑器窗口（排除当前预览窗口）
                        val allPanes = listOf(PanePosition.TOP_LEFT, PanePosition.TOP_RIGHT, PanePosition.BOTTOM)
                        val availablePanes = allPanes.filter { it != currentPanePosition }
                        
                        // 优先选择第一个可用窗口作为编辑器
                        val targetEditorPane = availablePanes.firstOrNull()
                        
                        if (targetEditorPane != null && onOpenInEditor != null && currentPanePosition != null) {
                            // 在编辑器窗口打开文件
                            android.util.Log.d("HtmlPreview", "在编辑器窗口打开远程文件: pane=$targetEditorPane, file=$filePath")
                            onOpenInEditor.invoke(filePath, targetEditorPane)
                            
                            // 建立编辑器-预览窗口绑定
                            PaneBindingManager.bindEditorToPreview(targetEditorPane, currentPanePosition)
                            android.util.Log.d("HtmlPreview", "已建立绑定: editor=$targetEditorPane, preview=$currentPanePosition")
                        } else {
                            // 后备方案：打开全屏编辑界面
                            android.util.Log.d("HtmlPreview", "使用全屏编辑界面（后备方案）")
                            onOpenRealtimeEditor?.invoke(filePath)
                        }
                    }
                    
                    FilledTonalButton(
                        onClick = {
                            // 点击编辑按钮 - 打开实时编辑界面
                            openRealtimeEditorAction()
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "实时编辑",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "实时编辑",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
        
        // 视口尺寸设置对话框
        if (showViewportSettings) {
            AlertDialog(
                onDismissRequest = { showViewportSettings = false },
                title = { Text("设备尺寸设置") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 预设设备尺寸
                        Text(
                            text = "预设设备：",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 手机
                            FilterChip(
                                selected = viewportWidth == 375 && viewportHeight == 667,
                                onClick = {
                                    viewportWidth = 375
                                    viewportHeight = 667
                                },
                                label = { Text("iPhone") }
                            )
                            // 平板
                            FilterChip(
                                selected = viewportWidth == 768 && viewportHeight == 1024,
                                onClick = {
                                    viewportWidth = 768
                                    viewportHeight = 1024
                                },
                                label = { Text("iPad") }
                            )
                            // 桌面
                            FilterChip(
                                selected = viewportWidth == 1920 && viewportHeight == 1080,
                                onClick = {
                                    viewportWidth = 1920
                                    viewportHeight = 1080
                                },
                                label = { Text("桌面") }
                            )
                        }
                        
                        HorizontalDivider()
                        
                        // 自定义尺寸
                        Text(
                            text = "自定义尺寸：",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "宽度: $viewportWidth px",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Slider(
                                value = viewportWidth.toFloat(),
                                onValueChange = { viewportWidth = it.toInt().coerceIn(320, 3840) },
                                valueRange = 320f..3840f,
                                steps = 35
                            )
                        }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "高度: $viewportHeight px",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Slider(
                                value = viewportHeight.toFloat(),
                                onValueChange = { viewportHeight = it.toInt().coerceIn(480, 2160) },
                                valueRange = 480f..2160f,
                                steps = 33
                            )
                        }
                        
                        Text(
                            text = "当前尺寸: ${viewportWidth} × ${viewportHeight} px",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showViewportSettings = false }) {
                        Text("确定")
                    }
                }
            )
        }
        
        // WebView 内容（带手势缩放）
        Box(modifier = Modifier.fillMaxSize()) {
            // 缩放和偏移容器
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(Unit) {
                        // 双指缩放手势（支持缩放和拖拽）
                        detectTransformGestures { centroid, pan, zoom, rotation ->
                            // 缩放
                            scale = (scale * zoom).coerceIn(0.5f, 5.0f)
                            // 拖拽移动（仅在缩放后可用）
                            if (scale > 1.0f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            // 禁用 WebView 内置的缩放控件，使用自定义手势缩放
                            settings.builtInZoomControls = false
                            settings.displayZoomControls = false
                            settings.setSupportZoom(false)
                            // 远程文件不需要文件访问权限
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.allowFileAccessFromFileURLs = false
                            settings.allowUniversalAccessFromFileURLs = false
                            
                            // 允许 WebView 内部滚动，但禁用过度滚动效果
                            overScrollMode = android.view.View.OVER_SCROLL_NEVER
                            
                            // 设置触摸事件拦截，防止与侧边栏手势冲突
                            setOnTouchListener { view, event ->
                                val handled = view.onTouchEvent(event)
                                when (event.action) {
                                    android.view.MotionEvent.ACTION_DOWN -> {
                                        parent?.requestDisallowInterceptTouchEvent(true)
                                    }
                                    android.view.MotionEvent.ACTION_MOVE -> {
                                        if (canScrollVertically(1) || canScrollVertically(-1) ||
                                            canScrollHorizontally(1) || canScrollHorizontally(-1)) {
                                            parent?.requestDisallowInterceptTouchEvent(true)
                                        }
                                    }
                                    android.view.MotionEvent.ACTION_UP,
                                    android.view.MotionEvent.ACTION_CANCEL -> {
                                        parent?.requestDisallowInterceptTouchEvent(false)
                                    }
                                }
                                handled
                            }
                            
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    // 不在这里设置 isLoading，因为由远程读取控制
                                }
                                
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                }
                                
                                override fun onReceivedError(
                                    view: WebView?,
                                    errorCode: Int,
                                    description: String?,
                                    failingUrl: String?
                                ) {
                                    super.onReceivedError(view, errorCode, description, failingUrl)
                                    isLoading = false
                                    error = "加载失败: $description (错误代码: $errorCode)"
                                }
                            }
                            
                            webView = this
                            
                            // 设置 WebView 的初始缩放和视口
                            val screenWidthPx = resources.displayMetrics.widthPixels
                            val scaleRatio = screenWidthPx.toFloat() / viewportWidth.toFloat()
                            setInitialScale((scaleRatio * 100).toInt())
                            
                            // 优先加载待加载的内容（实时预览缓存）
                            val pending = pendingContent
                            if (pending != null) {
                                android.util.Log.d("HtmlPreview", "WebView initialized (remote), loading pending content")
                                val processed = processHtmlContent(pending, viewportWidth, viewportHeight)
                                loadDataWithBaseURL(null, processed, "text/html", "UTF-8", null)
                                pendingContent = null
                            } else {
                                // 如果没有待加载内容，检查已加载的 HTML 内容
                                processedHtmlContent?.let { content ->
                                    loadDataWithBaseURL(null, content, "text/html", "UTF-8", null)
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // 加载指示器
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // 错误提示
            if (error != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * 将 WebView 内容导出为图片并保存到相册
 *
 * 使用 WebView.draw(Canvas) 方法将整个网页内容（包括不可见区域）渲染为 Bitmap，
 * 然后通过 MediaStore API 保存到系统相册。
 * 
 * 不依赖任何第三方库，纯 Android 原生 API 实现。
 */
private fun exportWebViewToImage(
    webView: WebView,
    baseName: String,
    context: android.content.Context,
    onComplete: () -> Unit
) {
    try {
        // 获取整个网页内容的尺寸（包括不可见区域）
        val width = webView.width
        val height = (webView.contentHeight * webView.scale).toInt()
        
        if (width <= 0 || height <= 0) {
            Toast.makeText(context, "页面尚未加载完成", Toast.LENGTH_SHORT).show()
            onComplete()
            return
        }
        
        // 限制最大高度避免 OOM（最大 8192px）
        val maxHeight = minOf(height, 8192)
        
        // 创建 Bitmap 并绘制 WebView 内容
        val bitmap = Bitmap.createBitmap(width, maxHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 保存当前滚动位置
        val savedScrollX = webView.scrollX
        val savedScrollY = webView.scrollY
        webView.scrollTo(0, 0)
        
        webView.draw(canvas)
        
        // 恢复滚动位置
        webView.scrollTo(savedScrollX, savedScrollY)
        
        // 保存到相册
        val fileName = "${baseName}_${System.currentTimeMillis()}.png"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 MediaStore API
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OmniMaster")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
                
                Toast.makeText(context, "已保存到 Pictures/OmniMaster/$fileName", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Android 9 及以下
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val omniDir = java.io.File(picturesDir, "OmniMaster")
            if (!omniDir.exists()) omniDir.mkdirs()
            
            val outFile = java.io.File(omniDir, fileName)
            outFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            // 通知媒体扫描器
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(outFile.absolutePath), arrayOf("image/png"), null
            )
            
            Toast.makeText(context, "已保存到 ${outFile.absolutePath}", Toast.LENGTH_LONG).show()
        }
        
        bitmap.recycle()
    } catch (e: OutOfMemoryError) {
        Toast.makeText(context, "页面过大，内存不足无法导出", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        android.util.Log.e("HtmlPreview", "Export failed", e)
        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
    } finally {
        onComplete()
    }
}
