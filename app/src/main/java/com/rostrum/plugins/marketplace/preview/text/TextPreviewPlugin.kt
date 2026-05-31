package com.rostrum.plugins.marketplace.preview.text

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rostrum.core.event.EventBusImpl
import com.rostrum.core.event.EventSubscriber
import com.rostrum.core.event.FileContentUpdateEvent
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
import com.rostrum.core.util.PathUtils
import com.rostrum.ui.main.LoadingSpinner
import com.rostrum.ui.main.PaneBindingManager
import com.rostrum.ui.main.PanePosition
import com.rostrum.plugins.marketplace.preview.html.LocalCurrentPanePosition
import com.rostrum.plugins.marketplace.preview.html.LocalOpenInEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 纯文本预览插件
 * 
 * 支持: txt, log, ini, cfg, conf, properties, env
 * 
 * 实现 ToolPlugin 接口，提供MCP工具供AI调用
 */
class TextPreviewPlugin : Plugin, FilePreviewPlugin, ToolPlugin {
    
    override val id = "com.rostrum.plugin.preview.text"
    override val name = "Text Preview"
    override val version = "1.0.0"
    override val author = "OmniMaster Team"
    override val description = "纯文本文件预览插件"
    override val category = PluginCategory.PREVIEW
    override val dependencies: List<String> = emptyList()
    
    override val supportedMimeTypes = listOf(
        "text/plain"
    )
    
    override val supportedExtensions = listOf(
        ".txt", ".log", ".ini", ".cfg", ".conf", ".properties", ".env",
        ".gitignore", ".dockerignore", ".editorconfig"
    )
    
    private var pluginContext: PluginContext? = null
    
    override suspend fun initialize(context: PluginContext): Result<Unit> {
        pluginContext = context
        return Result.success(Unit)
    }
    
    override suspend fun onActivate() = Result.success(Unit)
    override suspend fun onDeactivate() = Result.success(Unit)
    override suspend fun onDestroy(): Result<Unit> {
        pluginContext = null
        return Result.success(Unit)
    }
    
    override fun getCapabilities() = listOf(PluginCapability.FILE_PREVIEW)
    
    override fun canPreview(file: FileInfo): Boolean {
        return supportedExtensions.any { file.extension.equals(it, ignoreCase = true) }
    }
    
    override suspend fun createPreview(file: FileInfo): PreviewResult {
        if (!canPreview(file)) return PreviewResult.Unsupported
        
        val fileObj = File(file.path)
        if (!fileObj.exists()) {
            return PreviewResult.Error("文件不存在: ${file.path}")
        }
        
        return PreviewResult.Success(
            previewComponent = { TextPreviewContent(fileObj) },
            metadata = PreviewMetadata(
                title = file.name,
                description = "文本预览",
                canEdit = true
            )
        )
    }
    
    override fun getPreviewPriority(file: FileInfo) = 50
    
    // ==================== ToolPlugin 实现 ====================
    
    override val toolCategory = ToolCategory.FILE_SYSTEM
    
    override fun getMCPTools(): List<MCPTool> {
        return listOf(
            MCPTool(
                name = "text_file_stats",
                description = "获取文本文件的统计信息（行数、字符数、单词数等）",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "文本文件路径"),
                        "encoding" to JsonSchemaProperty(
                            type = "string",
                            description = "文件编码，默认UTF-8",
                            default = "UTF-8"
                        )
                    ),
                    required = listOf("filePath")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "lines" to JsonSchemaProperty(type = "number", description = "行数"),
                        "characters" to JsonSchemaProperty(type = "number", description = "字符数"),
                        "words" to JsonSchemaProperty(type = "number", description = "单词数"),
                        "bytes" to JsonSchemaProperty(type = "number", description = "字节数")
                    )
                ),
                handler = { args, context ->
                    try {
                        val filePath = args["filePath"] as? String
                            ?: return@MCPTool MCPResult(success = false, error = MCPError("INVALID_ARGS", "缺少filePath参数"))
                        
                        val file = java.io.File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(success = false, error = MCPError("FILE_NOT_FOUND", "文件不存在"))
                        }
                        
                        val content = file.readText()
                        val lines = content.lines().size
                        val characters = content.length
                        val words = content.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                        val bytes = file.length()
                        
                        MCPResult(
                            success = true,
                            data = mapOf(
                                "success" to true,
                                "lines" to lines,
                                "characters" to characters,
                                "words" to words,
                                "bytes" to bytes
                            )
                        )
                    } catch (e: Exception) {
                        MCPResult(success = false, error = MCPError("ERROR", e.message ?: "未知错误"))
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE)
            ),
            MCPTool(
                name = "text_search_in_file",
                description = "在文本文件中搜索指定内容",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "文本文件路径"),
                        "searchText" to JsonSchemaProperty(type = "string", description = "要搜索的文本"),
                        "caseSensitive" to JsonSchemaProperty(
                            type = "boolean",
                            description = "是否区分大小写，默认false",
                            default = false
                        ),
                        "useRegex" to JsonSchemaProperty(
                            type = "boolean",
                            description = "是否使用正则表达式，默认false",
                            default = false
                        )
                    ),
                    required = listOf("filePath", "searchText")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "matches" to JsonSchemaProperty(
                            type = "array",
                            description = "匹配结果列表",
                            items = JsonSchemaProperty(type = "object")
                        ),
                        "count" to JsonSchemaProperty(type = "number", description = "匹配数量")
                    )
                ),
                handler = { args, context ->
                    try {
                        val filePath = args["filePath"] as? String
                            ?: return@MCPTool MCPResult(success = false, error = MCPError("INVALID_ARGS", "缺少filePath参数"))
                        val searchText = args["searchText"] as? String
                            ?: return@MCPTool MCPResult(success = false, error = MCPError("INVALID_ARGS", "缺少searchText参数"))
                        val caseSensitive = args["caseSensitive"] as? Boolean ?: false
                        val useRegex = args["useRegex"] as? Boolean ?: false
                        
                        val file = java.io.File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(success = false, error = MCPError("FILE_NOT_FOUND", "文件不存在"))
                        }
                        
                        val content = file.readText()
                        val lines = content.lines()
                        val matches = mutableListOf<Map<String, Any>>()
                        
                        val pattern = if (useRegex) {
                            if (caseSensitive) Regex(searchText) else Regex(searchText, RegexOption.IGNORE_CASE)
                        } else {
                            if (caseSensitive) Regex(Regex.escape(searchText)) 
                            else Regex(Regex.escape(searchText), RegexOption.IGNORE_CASE)
                        }
                        
                        lines.forEachIndexed { lineIndex, line ->
                            pattern.findAll(line).forEach { matchResult ->
                                matches.add(mapOf(
                                    "line" to (lineIndex + 1),
                                    "column" to (matchResult.range.first + 1),
                                    "text" to matchResult.value,
                                    "context" to line.take(200)
                                ))
                            }
                        }
                        
                        MCPResult(
                            success = true,
                            data = mapOf(
                                "success" to true,
                                "matches" to matches,
                                "count" to matches.size
                            )
                        )
                    } catch (e: Exception) {
                        MCPResult(success = false, error = MCPError("ERROR", e.message ?: "未知错误"))
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE)
            )
        )
    }
}

@Composable
private fun TextPreviewContent(file: File) {
    var content by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var encoding by remember { mutableStateOf("UTF-8") }
    var fontSize by remember { mutableFloatStateOf(13f) }
    
    // 从文件读取初始内容
    LaunchedEffect(file, encoding) {
        withContext(Dispatchers.IO) {
            try {
                if (!file.exists() || !file.canRead()) {
                    error = "无法读取文件，请检查文件权限"
                    isLoading = false
                    return@withContext
                }
                
                // 检查文件大小（避免读取过大文件）
                val fileSize = file.length()
                if (fileSize > 10 * 1024 * 1024) { // 10MB
                    error = "文件过大（${fileSize / 1024 / 1024}MB），无法预览"
                    isLoading = false
                    return@withContext
                }
                
                content = file.readText(charset(encoding))
                isLoading = false
            } catch (e: SecurityException) {
                error = "文件权限错误: ${e.message}"
                isLoading = false
            } catch (e: java.nio.charset.UnsupportedCharsetException) {
                error = "不支持的字符编码: $encoding"
                isLoading = false
            } catch (e: OutOfMemoryError) {
                error = "内存不足，文件可能过大"
                isLoading = false
            } catch (e: Exception) {
                android.util.Log.e("TextPreview", "读取文本文件失败", e)
                error = "读取失败: ${e.message ?: e.javaClass.simpleName}"
                isLoading = false
            }
        }
    }
    
    // 订阅 FileContentUpdateEvent 实现实时预览
    DisposableEffect(file.absolutePath) {
        val eventBus = EventBusImpl.getInstance()
        val normalizedPath = PathUtils.normalizePath(file.absolutePath)
        
        val subscriber = object : EventSubscriber<FileContentUpdateEvent> {
            override suspend fun onEvent(event: FileContentUpdateEvent) {
                if (PathUtils.pathsEqual(event.filePath, normalizedPath)) {
                    android.util.Log.d("TextPreview", "Received FileContentUpdateEvent for: ${file.name}")
                    withContext(Dispatchers.Main) {
                        content = event.content
                        isLoading = false
                        error = null
                    }
                }
            }
        }
        
        eventBus.subscribe(FileContentUpdateEvent::class, subscriber, priority = 15)
        android.util.Log.d("TextPreview", "Subscribed to FileContentUpdateEvent for: ${file.absolutePath}")
        
        onDispose {
            eventBus.unsubscribe(FileContentUpdateEvent::class, subscriber)
            android.util.Log.d("TextPreview", "Unsubscribed from FileContentUpdateEvent for: ${file.absolutePath}")
        }
    }
    
    // 获取当前窗口位置和编辑器打开回调
    val currentPanePosition = LocalCurrentPanePosition.current
    val onOpenInEditor = LocalOpenInEditor.current
    
    // 打开实时编辑的函数
    val openRealtimeEditorAction: () -> Unit = {
        android.util.Log.d("TextPreview", "请求打开实时编辑: ${file.absolutePath}")
        
        // 查找可用的编辑器窗口（排除当前预览窗口）
        val allPanes = listOf(PanePosition.TOP_LEFT, PanePosition.TOP_RIGHT, PanePosition.BOTTOM)
        val availablePanes = allPanes.filter { it != currentPanePosition }
        
        // 优先选择第一个可用窗口作为编辑器
        val targetEditorPane = availablePanes.firstOrNull()
        
        if (targetEditorPane != null && onOpenInEditor != null && currentPanePosition != null) {
            // 在编辑器窗口打开文件
            onOpenInEditor.invoke(file.absolutePath, targetEditorPane)
            // 建立编辑器-预览窗口绑定
            PaneBindingManager.bindEditorToPreview(targetEditorPane, currentPanePosition)
            android.util.Log.d("TextPreview", "已建立绑定: editor=$targetEditorPane, preview=$currentPanePosition")
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 工具栏 - 固定高度，支持水平滚动
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "文本预览 | ${content.lines().size} 行",
                style = MaterialTheme.typography.labelSmall
            )
            
            // 实时编辑按钮
            if (onOpenInEditor != null && currentPanePosition != null) {
                Surface(
                    onClick = openRealtimeEditorAction,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "实时编辑",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "编辑",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            // 字体大小控制
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { fontSize = (fontSize - 1f).coerceAtLeast(10f) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.TextDecrease,
                        contentDescription = "减小字体",
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = "${fontSize.toInt()}",
                    fontSize = 10.sp,
                    modifier = Modifier.width(16.dp)
                )
                IconButton(
                    onClick = { fontSize = (fontSize + 1f).coerceAtMost(24f) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.TextIncrease,
                        contentDescription = "增大字体",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            
            // 编码选择 - 紧凑按钮
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf("UTF-8", "GBK", "ISO").forEach { enc ->
                    Surface(
                        onClick = { 
                            encoding = if (enc == "ISO") "ISO-8859-1" else enc
                            isLoading = true 
                        },
                        color = if ((enc == "ISO" && encoding == "ISO-8859-1") || encoding == enc) 
                            MaterialTheme.colorScheme.primaryContainer
                        else 
                            Color.Transparent,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(horizontal = 1.dp)
                    ) {
                        Text(
                            text = enc,
                            fontSize = 9.sp,
                            color = if ((enc == "ISO" && encoding == "ISO-8859-1") || encoding == enc)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        
        // 内容区域
        when {
            isLoading -> {
                // 不显示"加载中"，显示空白
                Box(Modifier.fillMaxSize())
            }
            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                SelectionContainer {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        item {
                            Text(
                                text = content,
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.4f).sp
                            )
                        }
                    }
                }
            }
        }
    }
}

