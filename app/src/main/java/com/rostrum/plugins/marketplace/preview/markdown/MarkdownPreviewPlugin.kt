package com.rostrum.plugins.marketplace.preview.markdown

import android.content.Context
import android.widget.TextView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import com.rostrum.core.event.EventBusImpl
import com.rostrum.core.event.EventSubscriber
import com.rostrum.core.event.FileContentUpdateEvent
import com.rostrum.core.filesystem.FileSystemBackend
import com.rostrum.core.network.MarkdownRenderer
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
 * Markdown 预览插件
 * 
 * 使用 Markwon 库渲染 Markdown 文件
 * 支持: md, markdown, mdown, mkdn
 * 支持本地文件和SSH远程文件
 * 
 * 实现 ToolPlugin 接口，提供MCP工具供AI调用
 */
class MarkdownPreviewPlugin : Plugin, FilePreviewPlugin, ToolPlugin {
    
    override val id = "com.rostrum.plugin.preview.markdown"
    override val name = "Markdown Preview"
    override val version = "1.0.0"
    override val author = "OmniMaster Team"
    override val description = "Markdown 文件预览插件，支持标准 Markdown 语法、表格、删除线等"
    override val category = PluginCategory.PREVIEW
    override val dependencies: List<String> = emptyList()
    
    override val supportedMimeTypes = listOf(
        "text/markdown",
        "text/x-markdown"
    )
    
    override val supportedExtensions = listOf(
        ".md", ".markdown", ".mdown", ".mkdn", ".mkd"
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
    
    /**
     * 是否支持远程文件预览
     */
    override fun supportsRemoteFiles(): Boolean = true
    
    override suspend fun createPreview(file: FileInfo): PreviewResult {
        if (!canPreview(file)) return PreviewResult.Unsupported
        
        // 本地文件：使用传统方式
        val fileObj = File(file.path)
        if (!fileObj.exists()) {
            return PreviewResult.Error("文件不存在: ${file.path}")
        }
        
        return PreviewResult.Success(
            previewComponent = { MarkdownPreviewContent(fileObj) },
            metadata = PreviewMetadata(
                title = file.name,
                description = "Markdown 预览",
                canEdit = true
            )
        )
    }
    
    /**
     * 创建预览组件（支持远程文件）
     */
    override suspend fun createPreview(file: FileInfo, fileSystem: FileSystemBackend): PreviewResult {
        if (!canPreview(file)) return PreviewResult.Unsupported
        
        // 远程文件：使用 FileSystemBackend 读取
        if (file.isRemote) {
            return PreviewResult.Success(
                previewComponent = { MarkdownRemotePreviewContent(file.path, file.name, fileSystem) },
                metadata = PreviewMetadata(
                    title = file.name,
                    description = "Markdown 预览 (远程)",
                    canEdit = true
                )
            )
        }
        
        // 本地文件：使用传统方式
        return createPreview(file)
    }
    
    override fun getPreviewPriority(file: FileInfo) = 100
    
    // ==================== ToolPlugin 实现 ====================
    
    override val toolCategory = ToolCategory.FILE_SYSTEM
    
    override fun getMCPTools(): List<MCPTool> {
        return listOf(
            MCPTool(
                name = "markdown_to_html",
                description = "将Markdown文件转换为HTML格式",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(
                            type = "string",
                            description = "Markdown文件路径"
                        ),
                        "outputPath" to JsonSchemaProperty(
                            type = "string",
                            description = "输出HTML文件路径（可选，不提供则返回HTML字符串）"
                        )
                    ),
                    required = listOf("filePath")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "html" to JsonSchemaProperty(type = "string", description = "转换后的HTML内容"),
                        "outputPath" to JsonSchemaProperty(type = "string", description = "输出文件路径（如果指定了）")
                    )
                ),
                handler = { args, _ ->
                    try {
                        val filePath = args["filePath"] as? String
                            ?: return@MCPTool MCPResult(success = false, error = MCPError("INVALID_ARGS", "缺少filePath参数"))
                        val outputPath = args["outputPath"] as? String
                        
                        val file = java.io.File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(success = false, error = MCPError("FILE_NOT_FOUND", "文件不存在"))
                        }
                        
                        val markdown = file.readText()
                        
                        // 简单的 Markdown 到 HTML 转换
                        val html = buildString {
                            append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">")
                            append("<style>body{font-family:sans-serif;padding:20px;line-height:1.6;}</style>")
                            append("</head><body>")
                            
                            // 基本转换
                            var text = markdown
                            // 标题
                            text = text.replace(Regex("^### (.+)$", RegexOption.MULTILINE), "<h3>$1</h3>")
                            text = text.replace(Regex("^## (.+)$", RegexOption.MULTILINE), "<h2>$1</h2>")
                            text = text.replace(Regex("^# (.+)$", RegexOption.MULTILINE), "<h1>$1</h1>")
                            // 粗体和斜体
                            text = text.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
                            text = text.replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
                            // 代码块
                            text = text.replace(Regex("```(\\w*)\\n([\\s\\S]*?)```"), "<pre><code>$2</code></pre>")
                            text = text.replace(Regex("`(.+?)`"), "<code>$1</code>")
                            // 链接
                            text = text.replace(Regex("\\[(.+?)\\]\\((.+?)\\)"), "<a href=\"$2\">$1</a>")
                            // 列表
                            text = text.replace(Regex("^- (.+)$", RegexOption.MULTILINE), "<li>$1</li>")
                            // 段落
                            text = text.replace(Regex("\n\n+"), "</p><p>")
                            
                            append("<p>$text</p>")
                            append("</body></html>")
                        }
                        
                        // 如果指定了输出路径，保存文件
                        if (outputPath != null) {
                            java.io.File(outputPath).writeText(html)
                        }
                        
                        MCPResult(
                            success = true,
                            data = mapOf(
                                "success" to true,
                                "html" to html,
                                "outputPath" to (outputPath ?: "")
                            )
                        )
                    } catch (e: Exception) {
                        MCPResult(success = false, error = MCPError("ERROR", e.message ?: "未知错误"))
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE, ToolPermission.WRITE_FILE)
            ),
            MCPTool(
                name = "markdown_extract_text",
                description = "从Markdown文件中提取纯文本内容（去除Markdown语法）",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "Markdown文件路径")
                    ),
                    required = listOf("filePath")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "text" to JsonSchemaProperty(type = "string", description = "提取的纯文本内容")
                    )
                ),
                handler = { args, _ ->
                    try {
                        val filePath = args["filePath"] as? String
                            ?: return@MCPTool MCPResult(success = false, error = MCPError("INVALID_ARGS", "缺少filePath参数"))
                        
                        val file = java.io.File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(success = false, error = MCPError("FILE_NOT_FOUND", "文件不存在"))
                        }
                        
                        val markdown = file.readText()
                        
                        // 移除 Markdown 语法，提取纯文本
                        var text = markdown
                        // 移除代码块
                        text = text.replace(Regex("```[\\s\\S]*?```"), "")
                        // 移除行内代码
                        text = text.replace(Regex("`[^`]+`"), "")
                        // 移除标题标记
                        text = text.replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
                        // 移除粗体/斜体
                        text = text.replace(Regex("\\*+([^*]+)\\*+"), "$1")
                        text = text.replace(Regex("_+([^_]+)_+"), "$1")
                        // 移除链接，保留文字
                        text = text.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
                        // 移除图片
                        text = text.replace(Regex("!\\[[^\\]]*\\]\\([^)]+\\)"), "")
                        // 移除列表标记
                        text = text.replace(Regex("^[\\-*+]\\s+", RegexOption.MULTILINE), "")
                        text = text.replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
                        // 移除水平线
                        text = text.replace(Regex("^[-*_]{3,}$", RegexOption.MULTILINE), "")
                        // 清理多余空行
                        text = text.replace(Regex("\n{3,}"), "\n\n")
                        text = text.trim()
                        
                        MCPResult(
                            success = true,
                            data = mapOf(
                                "success" to true,
                                "text" to text
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
private fun MarkdownPreviewContent(file: File) {
    var content by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // 从文件读取初始内容
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                content = file.readText(charset("UTF-8"))
                isLoading = false
            } catch (e: Exception) {
                error = "读取失败: ${e.message}"
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
                    android.util.Log.d("MarkdownPreview", "Received FileContentUpdateEvent for: ${file.name}")
                    withContext(Dispatchers.Main) {
                        content = event.content
                        isLoading = false
                        error = null
                    }
                }
            }
        }
        
        eventBus.subscribe(FileContentUpdateEvent::class, subscriber, priority = 15)
        android.util.Log.d("MarkdownPreview", "Subscribed to FileContentUpdateEvent for: ${file.absolutePath}")
        
        onDispose {
            eventBus.unsubscribe(FileContentUpdateEvent::class, subscriber)
            android.util.Log.d("MarkdownPreview", "Unsubscribed from FileContentUpdateEvent for: ${file.absolutePath}")
        }
    }
    
    // 获取当前窗口位置和编辑器打开回调
    val currentPanePosition = LocalCurrentPanePosition.current
    val onOpenInEditor = LocalOpenInEditor.current
    
    // 打开实时编辑的函数
    val openRealtimeEditorAction: () -> Unit = {
        android.util.Log.d("MarkdownPreview", "请求打开实时编辑: ${file.absolutePath}")
        
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
            android.util.Log.d("MarkdownPreview", "已建立绑定: editor=$targetEditorPane, preview=$currentPanePosition")
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 工具栏 - 固定高度，支持水平滚动
        Surface(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Markdown 预览",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${content.lines().size} 行",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 实时编辑按钮
                    if (onOpenInEditor != null && currentPanePosition != null) {
                        FilledTonalButton(
                            onClick = openRealtimeEditorAction,
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "实时编辑",
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    "实时编辑",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // 内容区域
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingSpinner()
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            else -> {
                // 使用 AndroidView 嵌入 TextView 以显示 Markdown
                AndroidView(
                    factory = { ctx ->
                        TextView(ctx).apply {
                            // 初始渲染
                            MarkdownRenderer.renderTo(this, content)
                        }
                    },
                    update = { textView ->
                        // content 变化时重新渲染 Markdown
                        MarkdownRenderer.renderTo(textView, content)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                )
            }
        }
    }
}

/**
 * 远程 Markdown 文件预览组件
 * 
 * 通过 FileSystemBackend 读取远程文件内容
 */
@Composable
private fun MarkdownRemotePreviewContent(
    filePath: String,
    fileName: String,
    fileSystem: FileSystemBackend
) {
    var content by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // 从远程文件系统读取初始内容
    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val result = fileSystem.readText(filePath)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        content = result.getOrThrow()
                        isLoading = false
                    } else {
                        error = "读取失败: ${result.exceptionOrNull()?.message}"
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = "读取失败: ${e.message}"
                    isLoading = false
                }
            }
        }
    }
    
    // 订阅 FileContentUpdateEvent 实现实时预览（远程文件也支持）
    DisposableEffect(filePath) {
        val eventBus = EventBusImpl.getInstance()
        val normalizedPath = PathUtils.normalizePath(filePath)
        
        val subscriber = object : EventSubscriber<FileContentUpdateEvent> {
            override suspend fun onEvent(event: FileContentUpdateEvent) {
                if (PathUtils.pathsEqual(event.filePath, normalizedPath)) {
                    android.util.Log.d("MarkdownPreview", "Received FileContentUpdateEvent for remote: $fileName")
                    withContext(Dispatchers.Main) {
                        content = event.content
                        isLoading = false
                        error = null
                    }
                }
            }
        }
        
        eventBus.subscribe(FileContentUpdateEvent::class, subscriber, priority = 15)
        android.util.Log.d("MarkdownPreview", "Subscribed to FileContentUpdateEvent for remote: $filePath")
        
        onDispose {
            eventBus.unsubscribe(FileContentUpdateEvent::class, subscriber)
            android.util.Log.d("MarkdownPreview", "Unsubscribed from FileContentUpdateEvent for remote: $filePath")
        }
    }
    
    // 获取当前窗口位置和编辑器打开回调
    val currentPanePosition = LocalCurrentPanePosition.current
    val onOpenInEditor = LocalOpenInEditor.current
    
    // 打开实时编辑的函数
    val openRealtimeEditorAction: () -> Unit = {
        android.util.Log.d("MarkdownPreview", "请求打开远程实时编辑: $filePath")
        
        // 查找可用的编辑器窗口（排除当前预览窗口）
        val allPanes = listOf(PanePosition.TOP_LEFT, PanePosition.TOP_RIGHT, PanePosition.BOTTOM)
        val availablePanes = allPanes.filter { it != currentPanePosition }
        
        // 优先选择第一个可用窗口作为编辑器
        val targetEditorPane = availablePanes.firstOrNull()
        
        if (targetEditorPane != null && onOpenInEditor != null && currentPanePosition != null) {
            // 在编辑器窗口打开文件
            onOpenInEditor.invoke(filePath, targetEditorPane)
            // 建立编辑器-预览窗口绑定
            PaneBindingManager.bindEditorToPreview(targetEditorPane, currentPanePosition)
            android.util.Log.d("MarkdownPreview", "已建立远程绑定: editor=$targetEditorPane, preview=$currentPanePosition")
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 工具栏 - 固定高度，支持水平滚动
        Surface(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Markdown 预览 (远程)",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${content.lines().size} 行",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 实时编辑按钮
                    if (onOpenInEditor != null && currentPanePosition != null) {
                        FilledTonalButton(
                            onClick = openRealtimeEditorAction,
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "实时编辑",
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    "实时编辑",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // 内容区域
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingSpinner()
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            else -> {
                // 使用 AndroidView 嵌入 TextView 以显示 Markdown
                AndroidView(
                    factory = { ctx ->
                        TextView(ctx).apply {
                            // 初始渲染
                            MarkdownRenderer.renderTo(this, content)
                        }
                    },
                    update = { textView ->
                        // content 变化时重新渲染 Markdown
                        MarkdownRenderer.renderTo(textView, content)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                )
            }
        }
    }
}
