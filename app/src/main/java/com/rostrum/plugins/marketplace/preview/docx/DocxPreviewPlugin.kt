package com.rostrum.plugins.marketplace.preview.docx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rostrum.core.mcp.*
import com.rostrum.core.office.OfficeDocumentRenderer
import com.rostrum.core.plugin.Plugin
import com.rostrum.core.plugin.PluginCapability
import com.rostrum.core.plugin.PluginCategory
import com.rostrum.core.plugin.PluginContext
import com.rostrum.core.plugin.models.FileInfo
import com.rostrum.core.plugin.providers.FilePreviewPlugin
import com.rostrum.core.plugin.providers.PreviewMetadata
import com.rostrum.core.plugin.providers.PreviewResult
import com.rostrum.core.plugin.providers.ToolPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * DOCX 文件预览插件
 * 
 * 插件商城插件 - 使用 Apache POI 预览 DOCX 文件
 * 
 * 插件ID: com.rostrum.plugin.preview.docx
 * 版本: 1.0.0
 * 作者: OmniMaster Team
 * 
 * 实现 ToolPlugin 接口，提供MCP工具供AI调用
 */
class DocxPreviewPlugin : Plugin, FilePreviewPlugin, ToolPlugin {
    
    override val id: String = "com.rostrum.plugin.preview.docx"
    override val name: String = "DOCX Preview"
    override val version: String = "1.0.0"
    override val author: String = "OmniMaster Team"
    override val description: String = "提供 DOCX 文件预览功能，使用 Apache POI 渲染文档内容（支持格式、表格、图片）"
    override val category: PluginCategory = PluginCategory.PREVIEW
    override val dependencies: List<String> = emptyList()
    
    override val supportedMimeTypes: List<String> = listOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    )
    
    override val supportedExtensions: List<String> = listOf(".docx")
    
    private var pluginContext: PluginContext? = null
    
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
        pluginContext = null
        return Result.success(Unit)
    }
    
    override fun getCapabilities(): List<PluginCapability> {
        return listOf(PluginCapability.FILE_PREVIEW)
    }
    
    override fun canPreview(file: FileInfo): Boolean {
        return supportedExtensions.contains(file.extension.lowercase()) ||
               file.mimeType?.contains("wordprocessingml") == true
    }
    
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
            
            // 创建预览组件（在组件内部异步加载文档）
            val previewComponent: @Composable () -> Unit = {
                DocxPreviewContent(fileObj)
            }
            
            PreviewResult.Success(
                previewComponent = previewComponent,
                metadata = PreviewMetadata(
                    title = file.name,
                    description = "DOCX 文档预览",
                    canEdit = true,
                    canExport = true
                )
            )
        } catch (e: SecurityException) {
            PreviewResult.Error("文件权限错误: ${e.message}", e)
        } catch (e: OutOfMemoryError) {
            PreviewResult.Error("内存不足，文件可能过大", e)
        } catch (e: Exception) {
            android.util.Log.e("DocxPreview", "Word预览失败", e)
            PreviewResult.Error("预览失败: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }
    
    override fun getPreviewPriority(file: FileInfo): Int {
        return 100
    }
    
    // ==================== ToolPlugin 实现 ====================
    
    override val toolCategory = ToolCategory.FILE_SYSTEM
    
    override fun getMCPTools(): List<MCPTool> {
        return listOf(
            MCPTool(
                name = "docx_extract_text",
                description = "从DOCX文件中提取纯文本内容",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "DOCX文件路径")
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
                        
                        val text = withContext(Dispatchers.IO) {
                            OfficeDocumentRenderer.extractDocxText(file)
                        }
                        
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
            ),
            MCPTool(
                name = "docx_modify_paragraph",
                description = "修改DOCX文档中的段落（添加、修改、删除）",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "DOCX文件路径"),
                        "action" to JsonSchemaProperty(
                            type = "string",
                            description = "操作类型：add（添加）、modify（修改）、delete（删除）",
                            enum = listOf("add", "modify", "delete")
                        ),
                        "index" to JsonSchemaProperty(type = "number", description = "段落索引（modify和delete需要）"),
                        "text" to JsonSchemaProperty(type = "string", description = "段落文本（add和modify需要）"),
                        "bold" to JsonSchemaProperty(type = "boolean", description = "是否粗体"),
                        "italic" to JsonSchemaProperty(type = "boolean", description = "是否斜体"),
                        "color" to JsonSchemaProperty(type = "string", description = "文字颜色，如 '#FF0000'"),
                        "fontSize" to JsonSchemaProperty(type = "number", description = "字体大小"),
                        "alignment" to JsonSchemaProperty(
                            type = "string",
                            description = "对齐方式：LEFT, CENTER, RIGHT, JUSTIFY",
                            enum = listOf("LEFT", "CENTER", "RIGHT", "JUSTIFY")
                        )
                    ),
                    required = listOf("filePath", "action")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "message" to JsonSchemaProperty(type = "string")
                    )
                ),
                handler = { args, context ->
                    try {
                        val filePath = args["filePath"] as? String
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少filePath参数")
                            )
                        
                        val action = args["action"] as? String
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少action参数")
                            )
                        
                        val file = File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "FILE_NOT_FOUND", message = "文件不存在: $filePath")
                            )
                        }
                        
                        val modifications = when (action) {
                            "add" -> {
                                val text = args["text"] as? String
                                    ?: return@MCPTool MCPResult(
                                        success = false,
                                        error = MCPError(code = "INVALID_ARGUMENT", message = "add操作需要text参数")
                                    )
                                listOf(
                                    OfficeDocumentRenderer.DocxModification.AddParagraph(
                                        text = text,
                                        bold = args["bold"] as? Boolean,
                                        italic = args["italic"] as? Boolean,
                                        color = args["color"] as? String,
                                        fontSize = (args["fontSize"] as? Number)?.toInt(),
                                        alignment = (args["alignment"] as? String)?.let {
                                            when (it.uppercase()) {
                                                "LEFT" -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT
                                                "CENTER" -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER
                                                "RIGHT" -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT
                                                "JUSTIFY" -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.BOTH
                                                else -> null
                                            }
                                        }
                                    )
                                )
                            }
                            "modify" -> {
                                val index = (args["index"] as? Number)?.toInt()
                                    ?: return@MCPTool MCPResult(
                                        success = false,
                                        error = MCPError(code = "INVALID_ARGUMENT", message = "modify操作需要index参数")
                                    )
                                val text = args["text"] as? String
                                listOf(
                                    OfficeDocumentRenderer.DocxModification.ModifyParagraph(
                                        index = index,
                                        text = text,
                                        bold = args["bold"] as? Boolean,
                                        italic = args["italic"] as? Boolean,
                                        color = args["color"] as? String,
                                        fontSize = (args["fontSize"] as? Number)?.toInt(),
                                        alignment = (args["alignment"] as? String)?.let {
                                            when (it.uppercase()) {
                                                "LEFT" -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT
                                                "CENTER" -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER
                                                "RIGHT" -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT
                                                "JUSTIFY" -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.BOTH
                                                else -> null
                                            }
                                        }
                                    )
                                )
                            }
                            "delete" -> {
                                val index = (args["index"] as? Number)?.toInt()
                                    ?: return@MCPTool MCPResult(
                                        success = false,
                                        error = MCPError(code = "INVALID_ARGUMENT", message = "delete操作需要index参数")
                                    )
                                listOf(OfficeDocumentRenderer.DocxModification.DeleteParagraph(index))
                            }
                            else -> {
                                return@MCPTool MCPResult(
                                    success = false,
                                    error = MCPError(code = "INVALID_ARGUMENT", message = "无效的action: $action")
                                )
                            }
                        }
                        
                        val result = withContext(Dispatchers.IO) {
                            OfficeDocumentRenderer.modifyDocx(file, modifications)
                        }
                        if (result.isSuccess) {
                            MCPResult(
                                success = true,
                                data = mapOf("message" to "修改成功")
                            )
                        } else {
                            MCPResult(
                                success = false,
                                error = MCPError(code = "MODIFICATION_FAILED", message = result.exceptionOrNull()?.message ?: "修改失败")
                            )
                        }
                    } catch (e: Exception) {
                        MCPResult(
                            success = false,
                            error = MCPError(code = "MODIFICATION_FAILED", message = "修改失败: ${e.message}")
                        )
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE, ToolPermission.WRITE_FILE)
            ),
            MCPTool(
                name = "docx_modify_table",
                description = "修改DOCX文档中的表格（添加、修改单元格）",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "DOCX文件路径"),
                        "action" to JsonSchemaProperty(
                            type = "string",
                            description = "操作类型：add（添加表格）、modify（修改单元格）",
                            enum = listOf("add", "modify")
                        ),
                        "rows" to JsonSchemaProperty(
                            type = "array",
                            description = "表格行数据（add操作需要）",
                            items = JsonSchemaProperty(
                                type = "array",
                                items = JsonSchemaProperty(type = "string")
                            )
                        ),
                        "tableIndex" to JsonSchemaProperty(type = "number", description = "表格索引（modify操作需要）"),
                        "rowIndex" to JsonSchemaProperty(type = "number", description = "行索引（modify操作需要）"),
                        "columnIndex" to JsonSchemaProperty(type = "number", description = "列索引（modify操作需要）"),
                        "value" to JsonSchemaProperty(type = "string", description = "单元格值（modify操作需要）")
                    ),
                    required = listOf("filePath", "action")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "message" to JsonSchemaProperty(type = "string")
                    )
                ),
                handler = { args, context ->
                    try {
                        val filePath = args["filePath"] as? String
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少filePath参数")
                            )
                        
                        val action = args["action"] as? String
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少action参数")
                            )
                        
                        val file = File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "FILE_NOT_FOUND", message = "文件不存在: $filePath")
                            )
                        }
                        
                        val modifications = when (action) {
                            "add" -> {
                                @Suppress("UNCHECKED_CAST")
                                val rows = args["rows"] as? List<List<String>>
                                    ?: return@MCPTool MCPResult(
                                        success = false,
                                        error = MCPError(code = "INVALID_ARGUMENT", message = "add操作需要rows参数")
                                    )
                                listOf(
                                    OfficeDocumentRenderer.DocxModification.AddTable(
                                        rows = rows,
                                        rowCount = rows.size,
                                        columnCount = rows.maxOfOrNull { it.size } ?: 0
                                    )
                                )
                            }
                            "modify" -> {
                                val tableIndex = (args["tableIndex"] as? Number)?.toInt()
                                    ?: return@MCPTool MCPResult(
                                        success = false,
                                        error = MCPError(code = "INVALID_ARGUMENT", message = "modify操作需要tableIndex参数")
                                    )
                                val rowIndex = (args["rowIndex"] as? Number)?.toInt()
                                    ?: return@MCPTool MCPResult(
                                        success = false,
                                        error = MCPError(code = "INVALID_ARGUMENT", message = "modify操作需要rowIndex参数")
                                    )
                                val columnIndex = (args["columnIndex"] as? Number)?.toInt()
                                    ?: return@MCPTool MCPResult(
                                        success = false,
                                        error = MCPError(code = "INVALID_ARGUMENT", message = "modify操作需要columnIndex参数")
                                    )
                                val value = args["value"] as? String
                                    ?: return@MCPTool MCPResult(
                                        success = false,
                                        error = MCPError(code = "INVALID_ARGUMENT", message = "modify操作需要value参数")
                                    )
                                listOf(
                                    OfficeDocumentRenderer.DocxModification.ModifyTable(
                                        tableIndex = tableIndex,
                                        rowIndex = rowIndex,
                                        columnIndex = columnIndex,
                                        value = value
                                    )
                                )
                            }
                            else -> {
                                return@MCPTool MCPResult(
                                    success = false,
                                    error = MCPError(code = "INVALID_ARGUMENT", message = "无效的action: $action")
                                )
                            }
                        }
                        
                        val result = withContext(Dispatchers.IO) {
                            OfficeDocumentRenderer.modifyDocx(file, modifications)
                        }
                        if (result.isSuccess) {
                            MCPResult(
                                success = true,
                                data = mapOf("message" to "修改成功")
                            )
                        } else {
                            MCPResult(
                                success = false,
                                error = MCPError(code = "MODIFICATION_FAILED", message = result.exceptionOrNull()?.message ?: "修改失败")
                            )
                        }
                    } catch (e: Exception) {
                        MCPResult(
                            success = false,
                            error = MCPError(code = "MODIFICATION_FAILED", message = "修改失败: ${e.message}")
                        )
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE, ToolPermission.WRITE_FILE)
            ),
            MCPTool(
                name = "docx_insert_image",
                description = "在DOCX文档中插入图片",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "DOCX文件路径"),
                        "imagePath" to JsonSchemaProperty(type = "string", description = "图片文件路径"),
                        "width" to JsonSchemaProperty(type = "number", description = "图片宽度（像素），默认200"),
                        "height" to JsonSchemaProperty(type = "number", description = "图片高度（像素），默认200")
                    ),
                    required = listOf("filePath", "imagePath")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "message" to JsonSchemaProperty(type = "string")
                    )
                ),
                handler = { args, context ->
                    try {
                        val filePath = args["filePath"] as? String
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少filePath参数")
                            )
                        
                        val imagePath = args["imagePath"] as? String
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少imagePath参数")
                            )
                        
                        val file = File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "FILE_NOT_FOUND", message = "文件不存在: $filePath")
                            )
                        }
                        
                        val imageFile = File(imagePath)
                        if (!imageFile.exists()) {
                            return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "FILE_NOT_FOUND", message = "图片文件不存在: $imagePath")
                            )
                        }
                        
                        val modifications = listOf(
                            OfficeDocumentRenderer.DocxModification.InsertImage(
                                imagePath = imagePath,
                                width = (args["width"] as? Number)?.toInt(),
                                height = (args["height"] as? Number)?.toInt()
                            )
                        )
                        
                        val result = withContext(Dispatchers.IO) {
                            OfficeDocumentRenderer.modifyDocx(file, modifications)
                        }
                        if (result.isSuccess) {
                            MCPResult(
                                success = true,
                                data = mapOf("message" to "插入图片成功")
                            )
                        } else {
                            MCPResult(
                                success = false,
                                error = MCPError(code = "MODIFICATION_FAILED", message = result.exceptionOrNull()?.message ?: "插入图片失败")
                            )
                        }
                    } catch (e: Exception) {
                        MCPResult(
                            success = false,
                            error = MCPError(code = "MODIFICATION_FAILED", message = "插入图片失败: ${e.message}")
                        )
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE, ToolPermission.WRITE_FILE)
            )
        )
    }
}

// Word 主题颜色
private val WordBlue = Color(0xFF2B579A)
private val WordBlueLight = Color(0xFF3B6DAA)
private val WordHeaderBg = Color(0xFF2B579A)
private val WordSurface = Color(0xFFF5F5F5)
private val WordPaper = Color.White
private val WordTableHeader = Color(0xFF4472C4)
private val WordTableBorder = Color(0xFFD6DCE4)

/**
 * DOCX 预览内容组件（美化版 - 仿 Word 风格）
 */
@Composable
private fun DocxPreviewContent(file: File) {
    var docxInfo by remember { mutableStateOf<OfficeDocumentRenderer.DocxInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // 使用file作为key，确保切换文件时重置状态
    var scale by remember(file.absolutePath) { mutableFloatStateOf(1.0f) }
    var fontSize by remember(file.absolutePath) { mutableFloatStateOf(1.0f) }
    val scrollState = rememberScrollState()
    
    // 异步加载文档
    LaunchedEffect(file.absolutePath) {
        isLoading = true
        error = null
        docxInfo = null
        
        try {
            val loadedInfo = withContext(Dispatchers.IO) {
                try {
                    OfficeDocumentRenderer.readDocx(file)
                } catch (e: org.apache.poi.EmptyFileException) {
                    throw IllegalArgumentException("Word文件为空或已损坏")
                } catch (e: org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException) {
                    throw IllegalArgumentException("不是有效的Word文件格式")
                } catch (e: java.util.zip.ZipException) {
                    throw IllegalArgumentException("Word文件已损坏或格式错误")
                }
            }
            
            if (currentCoroutineContext().isActive) {
                docxInfo = loadedInfo
                isLoading = false
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程被取消，忽略
        } catch (e: SecurityException) {
            if (currentCoroutineContext().isActive) {
                error = "文件权限错误: ${e.message}"
                isLoading = false
            }
        } catch (e: OutOfMemoryError) {
            if (currentCoroutineContext().isActive) {
                error = "内存不足，文件可能过大"
                isLoading = false
            }
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) {
                android.util.Log.e("DocxPreview", "加载Word文档失败", e)
                error = "加载失败: ${e.message ?: e.javaClass.simpleName}"
                isLoading = false
            }
        }
    }
    
    // 显示加载状态
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = WordBlue
                )
                Text(
                    text = "正在加载 Word 文档...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    
    // 显示错误状态
    if (error != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
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
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }
    
    // 显示文档内容
    val info = docxInfo ?: return
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 工具栏 - 固定高度，支持水平滚动
        Surface(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            color = WordBlue,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：图标和标题
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Word 预览",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${info.paragraphs.size} 段落 · ${info.tables.size} 表格",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                
                // 右侧：缩放和字体大小控制
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 字体大小控制
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.FormatSize,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                        
                        IconButton(
                            onClick = { fontSize = (fontSize - 0.1f).coerceAtLeast(0.7f) },
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
                            text = "${(fontSize * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.width(36.dp),
                            textAlign = TextAlign.Center
                        )
                        
                        IconButton(
                            onClick = { fontSize = (fontSize + 0.1f).coerceAtMost(1.5f) },
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
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 页面缩放控制
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { scale = (scale - 0.1f).coerceAtLeast(0.5f) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.ZoomOut,
                                contentDescription = "缩小",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${(scale * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        
                        IconButton(
                            onClick = { scale = (scale + 0.1f).coerceAtMost(2.0f) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.ZoomIn,
                                contentDescription = "放大",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        IconButton(
                            onClick = { 
                                scale = 1.0f
                                fontSize = 1.0f
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.FitScreen,
                                contentDescription = "重置",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // 文档内容（仿 Word 纸张风格）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WordSurface)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 2.0f)
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            // 纸张容器（带阴影）
            Card(
                modifier = Modifier
                    .fillMaxHeight()
                    .width((600 * scale).dp)
                    .padding(vertical = 16.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale
                    ),
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = WordPaper),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(
                            horizontal = 48.dp,
                            vertical = 36.dp
                        )
                ) {
                    // 显示段落
                    info.paragraphs.forEach { para ->
                        if (para.text.isNotBlank()) {
                            DocxParagraphView(para, fontSize)
                            Spacer(modifier = Modifier.height((12 * fontSize).dp))
                        }
                    }
                    
                    // 显示表格
                    info.tables.forEach { table ->
                        Spacer(modifier = Modifier.height(16.dp))
                        DocxTableView(table, fontSize)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // 显示图片信息
                    if (info.images.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        DocxImagesInfo(info.images.size, fontSize)
                    }
                }
            }
        }
    }
}

/**
 * 段落视图
 */
@Composable
private fun DocxParagraphView(
    para: OfficeDocumentRenderer.DocxParagraph,
    fontScale: Float
) {
    val baseFontSize = (para.fontSize ?: 14).toFloat()
    val scaledFontSize = (baseFontSize * fontScale).coerceAtLeast(8f).sp
    // 确保lineHeight不会是负数，最小为字体大小的1.2倍
    val lineHeightValue = (baseFontSize * fontScale * 1.6f).coerceAtLeast(baseFontSize * fontScale * 1.2f).coerceAtLeast(10f)
    val lineHeight = lineHeightValue.sp
    
    // 解析颜色
    val textColor = para.color?.let { colorStr ->
        try {
            Color(android.graphics.Color.parseColor(colorStr))
        } catch (e: Exception) {
            Color(0xFF333333)
        }
    } ?: Color(0xFF333333)
    
    // 判断是否是标题（根据字体大小和粗体）
    val isHeading = para.isBold && (para.fontSize ?: 14) > 14
    
    Text(
        text = para.text,
        fontSize = scaledFontSize,
        fontWeight = if (para.isBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (para.isItalic) FontStyle.Italic else FontStyle.Normal,
        color = textColor,
        lineHeight = lineHeight,
        textAlign = when (para.alignment) {
            "CENTER" -> TextAlign.Center
            "RIGHT" -> TextAlign.Right
            "JUSTIFY", "BOTH" -> TextAlign.Justify
            else -> TextAlign.Start
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isHeading) {
                    Modifier.padding(bottom = 8.dp)
                } else {
                    Modifier
                }
            )
    )
}

/**
 * 表格视图
 */
@Composable
private fun DocxTableView(
    table: OfficeDocumentRenderer.DocxTable,
    fontScale: Float
) {
    val horizontalScrollState = rememberScrollState()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScrollState)
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, WordTableBorder, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
        ) {
            // 表头（第一行）
            if (table.rows.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .background(WordTableHeader)
                ) {
                    table.rows[0].forEachIndexed { index, cell ->
                        Box(
                            modifier = Modifier
                                .width((120 * fontScale).dp)
                                .padding(10.dp)
                                .then(
                                    if (index < table.rows[0].size - 1) {
                                        Modifier.border(
                                            width = 0.5.dp,
                                            color = Color.White.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(0.dp)
                                        )
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = cell.ifEmpty { "-" },
                                fontSize = (12 * fontScale).sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            
            // 数据行
            table.rows.drop(1).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .background(
                            if (rowIndex % 2 == 0) Color.White else Color(0xFFF2F6FA)
                        )
                        .border(
                            width = 0.5.dp,
                            color = WordTableBorder
                        )
                ) {
                    row.forEachIndexed { colIndex, cell ->
                        Box(
                            modifier = Modifier
                                .width((120 * fontScale).dp)
                                .padding(10.dp)
                                .then(
                                    if (colIndex < row.size - 1) {
                                        Modifier.border(
                                            width = 0.5.dp,
                                            color = WordTableBorder,
                                            shape = RoundedCornerShape(0.dp)
                                        )
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = cell.ifEmpty { "-" },
                                fontSize = (12 * fontScale).sp,
                                color = Color(0xFF333333),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 图片信息提示
 */
@Composable
private fun DocxImagesInfo(
    imageCount: Int,
    fontScale: Float
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF0F4F8),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                tint = WordBlue,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = "文档包含 $imageCount 张图片",
                    fontSize = (14 * fontScale).sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333)
                )
                Text(
                    text = "图片预览功能正在开发中",
                    fontSize = (12 * fontScale).sp,
                    color = Color(0xFF666666)
                )
            }
        }
    }
}
