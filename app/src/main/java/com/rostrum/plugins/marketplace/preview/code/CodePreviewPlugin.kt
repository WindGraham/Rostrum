package com.rostrum.plugins.marketplace.preview.code

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rostrum.core.event.EventBusImpl
import com.rostrum.core.event.EventSubscriber
import com.rostrum.core.event.FileContentUpdateEvent
import com.rostrum.core.filesystem.FileSystemBackend
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
 * 代码预览插件
 * 
 * 支持多种编程语言，带行号和基础语法高亮
 * 支持本地文件和SSH远程文件
 * 
 * 实现 ToolPlugin 接口，提供MCP工具供AI调用
 */
class CodePreviewPlugin : Plugin, FilePreviewPlugin, ToolPlugin {
    
    override val id = "com.rostrum.plugin.preview.code"
    override val name = "Code Preview"
    override val version = "1.0.0"
    override val author = "OmniMaster Team"
    override val description = "代码预览插件，支持多种编程语言，带行号和语法高亮"
    override val category = PluginCategory.PREVIEW
    override val dependencies: List<String> = emptyList()
    
    override val supportedMimeTypes = listOf(
        "text/x-java", "text/x-kotlin", "text/x-python",
        "text/javascript", "text/typescript",
        "text/x-c", "text/x-c++", "text/x-go", "text/x-rust",
        // 排除 text/html，由 HTML 预览插件处理
        "text/css", "text/xml", "application/json",
        "text/x-shellscript", "text/x-sql", "text/markdown"
    )
    
    override val supportedExtensions = listOf(
        // JVM
        ".java", ".kt", ".kts", ".gradle", ".groovy", ".scala",
        // Web（排除 .html 和 .htm，由 HTML 预览插件处理）
        ".js", ".jsx", ".ts", ".tsx", ".css", ".scss", ".sass", ".less",
        // 数据
        ".json", ".xml", ".yaml", ".yml", ".toml",
        // 系统
        ".c", ".h", ".cpp", ".cc", ".hpp", ".go", ".rs", ".swift",
        // 脚本
        ".py", ".rb", ".php", ".lua", ".pl", ".sh", ".bash", ".zsh", ".ps1", ".bat", ".cmd",
        // 数据库
        ".sql",
        // 文档
        ".md", ".markdown",
        // 配置
        ".pro", ".cmake", ".make", ".makefile"
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
        // 明确排除 HTML 文件，由 HTML 预览插件处理
        val isHtmlFile = file.extension.equals(".html", ignoreCase = true) ||
                         file.extension.equals(".htm", ignoreCase = true) ||
                         file.mimeType?.contains("html", ignoreCase = true) == true
        
        if (isHtmlFile) {
            return false // HTML 文件由专门的 HTML 预览插件处理
        }
        
        return supportedExtensions.any { file.extension.equals(it, ignoreCase = true) }
    }
    
    /**
     * 是否支持远程文件预览
     */
    override fun supportsRemoteFiles(): Boolean = true
    
    override suspend fun createPreview(file: FileInfo): PreviewResult {
        if (!canPreview(file)) return PreviewResult.Unsupported
        
        val fileObj = File(file.path)
        if (!fileObj.exists()) {
            return PreviewResult.Error("文件不存在: ${file.path}")
        }
        
        val language = getLanguageFromExtension(file.extension)
        
        return PreviewResult.Success(
            previewComponent = { CodePreviewContent(fileObj, language) },
            metadata = PreviewMetadata(
                title = file.name,
                description = "$language 代码预览",
                canEdit = true
            )
        )
    }
    
    /**
     * 创建预览组件（支持远程文件）
     */
    override suspend fun createPreview(file: FileInfo, fileSystem: FileSystemBackend): PreviewResult {
        if (!canPreview(file)) return PreviewResult.Unsupported
        
        val language = getLanguageFromExtension(file.extension)
        
        // 远程文件：使用 FileSystemBackend 读取
        if (file.isRemote) {
            return PreviewResult.Success(
                previewComponent = { CodeRemotePreviewContent(file.path, file.name, language, fileSystem) },
                metadata = PreviewMetadata(
                    title = file.name,
                    description = "$language 代码预览 (远程)",
                    canEdit = true
                )
            )
        }
        
        // 本地文件：使用传统方式
        return createPreview(file)
    }
    
    override fun getPreviewPriority(file: FileInfo) = 80
    
    private fun getLanguageFromExtension(ext: String): String {
        return when (ext.lowercase()) {
            ".java" -> "Java"
            ".kt", ".kts" -> "Kotlin"
            ".py" -> "Python"
            ".js", ".jsx" -> "JavaScript"
            ".ts", ".tsx" -> "TypeScript"
            ".c", ".h" -> "C"
            ".cpp", ".cc", ".hpp" -> "C++"
            ".go" -> "Go"
            ".rs" -> "Rust"
            ".swift" -> "Swift"
            ".rb" -> "Ruby"
            ".php" -> "PHP"
            ".html", ".htm" -> "HTML"
            ".css", ".scss", ".sass", ".less" -> "CSS"
            ".json" -> "JSON"
            ".xml" -> "XML"
            ".yaml", ".yml" -> "YAML"
            ".sql" -> "SQL"
            ".sh", ".bash", ".zsh" -> "Shell"
            ".md", ".markdown" -> "Markdown"
            ".gradle", ".groovy" -> "Groovy"
            else -> "Code"
        }
    }
    
    // ==================== ToolPlugin 实现 ====================
    
    override val toolCategory = ToolCategory.FILE_SYSTEM
    
    override fun getMCPTools(): List<MCPTool> {
        return listOf(
            MCPTool(
                name = "code_analyze",
                description = "分析代码文件，提取函数、类、导入等信息",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "代码文件路径"),
                        "language" to JsonSchemaProperty(
                            type = "string",
                            description = "编程语言（可选，自动检测）"
                        )
                    ),
                    required = listOf("filePath")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "language" to JsonSchemaProperty(type = "string", description = "检测到的编程语言"),
                        "functions" to JsonSchemaProperty(
                            type = "array",
                            description = "函数列表",
                            items = JsonSchemaProperty(type = "object")
                        ),
                        "classes" to JsonSchemaProperty(
                            type = "array",
                            description = "类列表",
                            items = JsonSchemaProperty(type = "object")
                        ),
                        "imports" to JsonSchemaProperty(
                            type = "array",
                            description = "导入语句列表",
                            items = JsonSchemaProperty(type = "string")
                        )
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
                        
                        val content = file.readText()
                        val lines = content.lines()
                        val extension = file.extension.lowercase()
                        
                        // 统计信息
                        val totalLines = lines.size
                        val blankLines = lines.count { it.isBlank() }
                        val codeLines = totalLines - blankLines
                        
                        // 根据语言提取函数和类
                        val functions = mutableListOf<Map<String, Any>>()
                        val classes = mutableListOf<Map<String, Any>>()
                        val imports = mutableListOf<String>()
                        
                        when (extension) {
                            "kt", "java" -> {
                                // Kotlin/Java 分析
                                lines.forEachIndexed { index, line ->
                                    val trimmed = line.trim()
                                    when {
                                        trimmed.startsWith("import ") -> {
                                            imports.add(trimmed.removePrefix("import ").removeSuffix(";"))
                                        }
                                        trimmed.matches(Regex("(public |private |protected |internal |open |abstract )*(fun |def ).*\\(.*")) -> {
                                            val name = trimmed.substringAfter("fun ").substringBefore("(").trim()
                                            functions.add(mapOf("name" to name, "line" to (index + 1)))
                                        }
                                        trimmed.matches(Regex("(public |private |protected |internal |open |abstract |data |sealed )*(class |interface |object |enum ).*")) -> {
                                            val name = trimmed.substringAfter("class ").substringAfter("interface ").substringAfter("object ").substringBefore(" ").substringBefore("(").substringBefore("{").trim()
                                            classes.add(mapOf("name" to name, "line" to (index + 1)))
                                        }
                                    }
                                }
                            }
                            "py" -> {
                                // Python 分析
                                lines.forEachIndexed { index, line ->
                                    val trimmed = line.trim()
                                    when {
                                        trimmed.startsWith("import ") || trimmed.startsWith("from ") -> {
                                            imports.add(trimmed)
                                        }
                                        trimmed.startsWith("def ") -> {
                                            val name = trimmed.removePrefix("def ").substringBefore("(").trim()
                                            functions.add(mapOf("name" to name, "line" to (index + 1)))
                                        }
                                        trimmed.startsWith("class ") -> {
                                            val name = trimmed.removePrefix("class ").substringBefore("(").substringBefore(":").trim()
                                            classes.add(mapOf("name" to name, "line" to (index + 1)))
                                        }
                                    }
                                }
                            }
                            "js", "ts", "jsx", "tsx" -> {
                                // JavaScript/TypeScript 分析
                                lines.forEachIndexed { index, line ->
                                    val trimmed = line.trim()
                                    when {
                                        trimmed.startsWith("import ") -> {
                                            imports.add(trimmed)
                                        }
                                        trimmed.matches(Regex("(export )?(async )?(function |const |let |var ).*\\(.*|.*=>.*")) && trimmed.contains("function") -> {
                                            val name = trimmed.substringAfter("function ").substringBefore("(").trim()
                                            if (name.isNotBlank()) functions.add(mapOf("name" to name, "line" to (index + 1)))
                                        }
                                        trimmed.matches(Regex("(export )?(abstract )?(class ).*")) -> {
                                            val name = trimmed.substringAfter("class ").substringBefore(" ").substringBefore("{").trim()
                                            classes.add(mapOf("name" to name, "line" to (index + 1)))
                                        }
                                    }
                                }
                            }
                        }
                        
                        MCPResult(
                            success = true,
                            data = mapOf(
                                "success" to true,
                                "totalLines" to totalLines,
                                "codeLines" to codeLines,
                                "blankLines" to blankLines,
                                "functions" to functions,
                                "classes" to classes,
                                "imports" to imports
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
private fun CodePreviewContent(file: File, language: String) {
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var fontSize by remember { mutableFloatStateOf(12f) }
    
    // 从文件读取初始内容
    LaunchedEffect(file) {
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
                
                lines = file.readLines()
                isLoading = false
            } catch (e: SecurityException) {
                error = "文件权限错误: ${e.message}"
                isLoading = false
            } catch (e: OutOfMemoryError) {
                error = "内存不足，文件可能过大"
                isLoading = false
            } catch (e: Exception) {
                android.util.Log.e("CodePreview", "读取代码文件失败", e)
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
                    android.util.Log.d("CodePreview", "Received FileContentUpdateEvent for: ${file.name}")
                    withContext(Dispatchers.Main) {
                        lines = event.content.lines()
                        isLoading = false
                        error = null
                    }
                }
            }
        }
        
        eventBus.subscribe(FileContentUpdateEvent::class, subscriber, priority = 15)
        android.util.Log.d("CodePreview", "Subscribed to FileContentUpdateEvent for: ${file.absolutePath}")
        
        onDispose {
            eventBus.unsubscribe(FileContentUpdateEvent::class, subscriber)
            android.util.Log.d("CodePreview", "Unsubscribed from FileContentUpdateEvent for: ${file.absolutePath}")
        }
    }
    
    // 主题颜色
    val bgColor = Color(0xFF1E1E1E)
    val lineNumColor = Color(0xFF858585)
    val textColor = Color(0xFFD4D4D4)
    val keywordColor = Color(0xFF569CD6)
    val stringColor = Color(0xFFCE9178)
    val commentColor = Color(0xFF6A9955)
    val numberColor = Color(0xFFB5CEA8)
    
    // 获取当前窗口位置和编辑器打开回调
    val currentPanePosition = LocalCurrentPanePosition.current
    val onOpenInEditor = LocalOpenInEditor.current
    
    // 打开实时编辑的函数
    val openRealtimeEditorAction: () -> Unit = {
        android.util.Log.d("CodePreview", "请求打开实时编辑: ${file.absolutePath}")
        
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
            android.util.Log.d("CodePreview", "已建立绑定: editor=$targetEditorPane, preview=$currentPanePosition")
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 工具栏 - 固定高度，支持水平滚动
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(Color(0xFF2D2D2D))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$language | ${lines.size} 行",
                color = Color.White,
                fontSize = 12.sp
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "实时编辑",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                
                // 字体大小控制
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { fontSize = (fontSize - 1f).coerceAtLeast(8f) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Filled.TextDecrease,
                            contentDescription = "减小字体",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "${fontSize.toInt()}",
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.width(20.dp),
                        textAlign = TextAlign.Center
                    )
                    IconButton(
                        onClick = { fontSize = (fontSize + 1f).coerceAtMost(24f) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Filled.TextIncrease,
                            contentDescription = "增大字体",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        
        // 代码区域
        when {
            isLoading -> {
                // 不显示"加载中"，显示空白
                Box(Modifier.fillMaxSize().background(bgColor))
            }
            error != null -> {
                Box(
                    Modifier.fillMaxSize().background(bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(error!!, color = Color.Red)
                }
            }
            else -> {
                SelectionContainer {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(bgColor)
                            .padding(vertical = 4.dp)
                    ) {
                        itemsIndexed(lines) { index, line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            ) {
                                // 行号
                                Text(
                                    text = "${index + 1}".padStart(4),
                                    color = lineNumColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = fontSize.sp,
                                    modifier = Modifier.width((fontSize * 4).dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                // 代码
                                Text(
                                    text = highlightLine(line, language, keywordColor, stringColor, commentColor, numberColor, textColor),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = fontSize.sp,
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun highlightLine(
    line: String,
    language: String,
    keywordColor: Color,
    stringColor: Color,
    commentColor: Color,
    numberColor: Color,
    textColor: Color
) = buildAnnotatedString {
    // 简单的语法高亮
    val keywords = when (language) {
        "Java", "Kotlin" -> setOf(
            "class", "fun", "val", "var", "if", "else", "for", "while", "return",
            "import", "package", "public", "private", "protected", "static", "final",
            "override", "suspend", "interface", "abstract", "data", "object", "companion",
            "when", "is", "in", "null", "true", "false", "this", "super", "new", "void"
        )
        "JavaScript", "TypeScript" -> setOf(
            "function", "const", "let", "var", "if", "else", "for", "while", "return",
            "import", "export", "from", "class", "extends", "new", "this", "async", "await",
            "true", "false", "null", "undefined", "typeof", "instanceof"
        )
        "Python" -> setOf(
            "def", "class", "if", "elif", "else", "for", "while", "return", "import", "from",
            "as", "try", "except", "finally", "with", "lambda", "True", "False", "None",
            "and", "or", "not", "in", "is", "pass", "break", "continue", "yield", "async", "await"
        )
        else -> emptySet()
    }
    
    // 检查是否是注释行
    val trimmed = line.trim()
    if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("--")) {
        withStyle(SpanStyle(color = commentColor)) { append(line) }
        return@buildAnnotatedString
    }
    
    // 简单的词法分析
    var i = 0
    while (i < line.length) {
        when {
            // 字符串
            line[i] == '"' || line[i] == '\'' -> {
                val quote = line[i]
                val start = i
                i++
                while (i < line.length && line[i] != quote) {
                    if (line[i] == '\\' && i + 1 < line.length) i++
                    i++
                }
                if (i < line.length) i++
                withStyle(SpanStyle(color = stringColor)) {
                    append(line.substring(start, i))
                }
            }
            // 数字
            line[i].isDigit() -> {
                val start = i
                while (i < line.length && (line[i].isDigit() || line[i] == '.' || line[i] == 'x' || line[i] in 'a'..'f' || line[i] in 'A'..'F')) {
                    i++
                }
                withStyle(SpanStyle(color = numberColor)) {
                    append(line.substring(start, i))
                }
            }
            // 标识符/关键字
            line[i].isLetter() || line[i] == '_' -> {
                val start = i
                while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_')) {
                    i++
                }
                val word = line.substring(start, i)
                if (word in keywords) {
                    withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)) {
                        append(word)
                    }
                } else {
                    withStyle(SpanStyle(color = textColor)) {
                        append(word)
                    }
                }
            }
            else -> {
                withStyle(SpanStyle(color = textColor)) {
                    append(line[i])
                }
                i++
            }
        }
    }
}

/**
 * 远程代码文件预览组件
 * 
 * 通过 FileSystemBackend 读取远程文件内容
 */
@Composable
private fun CodeRemotePreviewContent(
    filePath: String,
    fileName: String,
    language: String,
    fileSystem: FileSystemBackend
) {
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var fontSize by remember { mutableFloatStateOf(12f) }
    
    // 从远程文件系统读取初始内容
    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val result = fileSystem.readText(filePath)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        lines = result.getOrThrow().lines()
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
                    android.util.Log.d("CodePreview", "Received FileContentUpdateEvent for remote: $fileName")
                    withContext(Dispatchers.Main) {
                        lines = event.content.lines()
                        isLoading = false
                        error = null
                    }
                }
            }
        }
        
        eventBus.subscribe(FileContentUpdateEvent::class, subscriber, priority = 15)
        android.util.Log.d("CodePreview", "Subscribed to FileContentUpdateEvent for remote: $filePath")
        
        onDispose {
            eventBus.unsubscribe(FileContentUpdateEvent::class, subscriber)
            android.util.Log.d("CodePreview", "Unsubscribed from FileContentUpdateEvent for remote: $filePath")
        }
    }
    
    // 主题颜色
    val bgColor = Color(0xFF1E1E1E)
    val lineNumColor = Color(0xFF858585)
    val textColor = Color(0xFFD4D4D4)
    val keywordColor = Color(0xFF569CD6)
    val stringColor = Color(0xFFCE9178)
    val commentColor = Color(0xFF6A9955)
    val numberColor = Color(0xFFB5CEA8)
    
    // 获取当前窗口位置和编辑器打开回调
    val currentPanePosition = LocalCurrentPanePosition.current
    val onOpenInEditor = LocalOpenInEditor.current
    
    // 打开实时编辑的函数
    val openRealtimeEditorAction: () -> Unit = {
        android.util.Log.d("CodePreview", "请求打开远程实时编辑: $filePath")
        
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
            android.util.Log.d("CodePreview", "已建立远程绑定: editor=$targetEditorPane, preview=$currentPanePosition")
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 工具栏 - 固定高度，支持水平滚动
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(Color(0xFF2D2D2D))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$language (远程) | ${lines.size} 行",
                color = Color.White,
                fontSize = 12.sp
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "实时编辑",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                
                // 字体大小控制
                IconButton(
                    onClick = { if (fontSize > 8f) fontSize -= 1f },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Remove, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Text("${fontSize.toInt()}sp", color = Color.White, fontSize = 11.sp)
                IconButton(
                    onClick = { if (fontSize < 24f) fontSize += 1f },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        
        // 代码区域
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingSpinner()
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(bgColor),
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
                SelectionContainer {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(bgColor)
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 8.dp)
                    ) {
                        itemsIndexed(lines) { index, line ->
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // 行号
                                Text(
                                    text = "${index + 1}",
                                    color = lineNumColor,
                                    fontSize = fontSize.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(48.dp),
                                    textAlign = TextAlign.End
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                // 代码行（带语法高亮）
                                Text(
                                    text = highlightLine(
                                        line = line,
                                        language = language,
                                        keywordColor = keywordColor,
                                        stringColor = stringColor,
                                        commentColor = commentColor,
                                        numberColor = numberColor,
                                        textColor = textColor
                                    ),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * 1.4).sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
