package com.rostrum.plugins.marketplace.preview.pptx

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PPTX 预览插件
 * 
 * 提取并显示 PPTX 幻灯片的文本和图片内容
 * 
 * 实现 ToolPlugin 接口，提供MCP工具供AI调用
 */
class PptxPreviewPlugin : Plugin, FilePreviewPlugin, ToolPlugin {
    
    override val id = "com.rostrum.plugin.preview.pptx"
    override val name = "PPTX Preview"
    override val version = "1.0.0"
    override val author = "OmniMaster Team"
    override val description = "PPTX 演示文稿预览，使用 Apache POI 提取幻灯片文本、图片和形状（支持编辑）"
    override val category = PluginCategory.PREVIEW
    override val dependencies: List<String> = emptyList()
    
    override val supportedMimeTypes = listOf(
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    )
    
    override val supportedExtensions = listOf(".pptx")
    
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
        return file.extension.equals(".pptx", ignoreCase = true)
    }
    
    override suspend fun createPreview(file: FileInfo): PreviewResult {
        if (!canPreview(file)) return PreviewResult.Unsupported
        
        val fileObj = File(file.path)
        if (!fileObj.exists()) {
            return PreviewResult.Error("文件不存在: ${file.path}")
        }
        
        return try {
            // 检查文件权限
            if (!fileObj.canRead()) {
                return PreviewResult.Error("无法读取文件，请检查文件权限")
            }
            
            // 使用Apache POI读取PPTX
            val pptxInfo = withContext(Dispatchers.IO) {
                try {
                    OfficeDocumentRenderer.readPptx(fileObj)
                } catch (e: org.apache.poi.EmptyFileException) {
                    throw IllegalArgumentException("PPTX文件为空或已损坏")
                } catch (e: org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException) {
                    throw IllegalArgumentException("不是有效的PPTX文件格式")
                } catch (e: java.util.zip.ZipException) {
                    throw IllegalArgumentException("PPTX文件已损坏或格式错误")
                }
            }
            
            PreviewResult.Success(
                previewComponent = { PptxPreviewContent(pptxInfo) },
                metadata = PreviewMetadata(
                    title = file.name,
                    description = "PPTX 预览（${pptxInfo.slideCount}张幻灯片）",
                    canEdit = true,
                    canExport = true
                )
            )
        } catch (e: SecurityException) {
            PreviewResult.Error("文件权限错误: ${e.message}", e)
        } catch (e: OutOfMemoryError) {
            PreviewResult.Error("内存不足，文件可能过大", e)
        } catch (e: Exception) {
            android.util.Log.e("PptxPreview", "PPTX预览失败", e)
            PreviewResult.Error("预览失败: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }
    
    override fun getPreviewPriority(file: FileInfo) = 100
    
    // ==================== ToolPlugin 实现 ====================
    
    override val toolCategory = ToolCategory.FILE_SYSTEM
    
    override fun getMCPTools(): List<MCPTool> {
        return listOf(
            MCPTool(
                name = "pptx_extract_slides",
                description = "从PPTX文件中提取所有幻灯片的文本内容",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "PPTX文件路径")
                    ),
                    required = listOf("filePath")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "slides" to JsonSchemaProperty(
                            type = "array",
                            description = "幻灯片列表",
                            items = JsonSchemaProperty(type = "object")
                        ),
                        "totalSlides" to JsonSchemaProperty(type = "number", description = "总幻灯片数")
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
                        
                        val pptxInfo = withContext(Dispatchers.IO) {
                            OfficeDocumentRenderer.readPptx(file)
                        }
                        
                        MCPResult(
                            success = true,
                            data = mapOf(
                                "slides" to pptxInfo.slides.map { slide ->
                                    mapOf(
                                        "index" to slide.index,
                                        "title" to (slide.title ?: ""),
                                        "texts" to slide.texts,
                                        "imageCount" to slide.images.size
                                    )
                                },
                                "totalSlides" to pptxInfo.slideCount
                            )
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
                name = "pptx_modify_slide_text",
                description = "修改PPTX幻灯片中的文本",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "PPTX文件路径"),
                        "slideIndex" to JsonSchemaProperty(type = "number", description = "幻灯片索引（从0开始）"),
                        "shapeIndex" to JsonSchemaProperty(type = "number", description = "文本形状索引（从0开始）"),
                        "text" to JsonSchemaProperty(type = "string", description = "新文本内容")
                    ),
                    required = listOf("filePath", "slideIndex", "shapeIndex", "text")
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
                        
                        val slideIndex = (args["slideIndex"] as? Number)?.toInt()
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少slideIndex参数")
                            )
                        
                        val shapeIndex = (args["shapeIndex"] as? Number)?.toInt()
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少shapeIndex参数")
                            )
                        
                        val text = args["text"] as? String
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少text参数")
                            )
                        
                        val file = File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "FILE_NOT_FOUND", message = "文件不存在: $filePath")
                            )
                        }
                        
                        val result = withContext(Dispatchers.IO) {
                            OfficeDocumentRenderer.modifyPptxSlideText(file, slideIndex, shapeIndex, text)
                        }
                        if (result.isSuccess) {
                            MCPResult(
                                success = true,
                                data = mapOf("message" to "修改文本成功")
                            )
                        } else {
                            MCPResult(
                                success = false,
                                error = MCPError(code = "MODIFICATION_FAILED", message = result.exceptionOrNull()?.message ?: "修改文本失败")
                            )
                        }
                    } catch (e: Exception) {
                        MCPResult(
                            success = false,
                            error = MCPError(code = "MODIFICATION_FAILED", message = "修改文本失败: ${e.message}")
                        )
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE, ToolPermission.WRITE_FILE)
            ),
            MCPTool(
                name = "pptx_add_slide",
                description = "添加PPTX幻灯片",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "PPTX文件路径"),
                        "layoutIndex" to JsonSchemaProperty(type = "number", description = "布局索引，默认0", default = 0)
                    ),
                    required = listOf("filePath")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "slideIndex" to JsonSchemaProperty(type = "number", description = "新添加的幻灯片索引"),
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
                        
                        val layoutIndex = (args["layoutIndex"] as? Number)?.toInt() ?: 0
                        
                        val file = File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "FILE_NOT_FOUND", message = "文件不存在: $filePath")
                            )
                        }
                        
                        val result = withContext(Dispatchers.IO) {
                            OfficeDocumentRenderer.addPptxSlide(file, layoutIndex)
                        }
                        if (result.isSuccess) {
                            MCPResult(
                                success = true,
                                data = mapOf(
                                    "slideIndex" to result.getOrNull(),
                                    "message" to "添加幻灯片成功"
                                )
                            )
                        } else {
                            MCPResult(
                                success = false,
                                error = MCPError(code = "MODIFICATION_FAILED", message = result.exceptionOrNull()?.message ?: "添加幻灯片失败")
                            )
                        }
                    } catch (e: Exception) {
                        MCPResult(
                            success = false,
                            error = MCPError(code = "MODIFICATION_FAILED", message = "添加幻灯片失败: ${e.message}")
                        )
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE, ToolPermission.WRITE_FILE)
            ),
            MCPTool(
                name = "pptx_delete_slide",
                description = "删除PPTX幻灯片",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "PPTX文件路径"),
                        "slideIndex" to JsonSchemaProperty(type = "number", description = "幻灯片索引（从0开始）")
                    ),
                    required = listOf("filePath", "slideIndex")
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
                        
                        val slideIndex = (args["slideIndex"] as? Number)?.toInt()
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少slideIndex参数")
                            )
                        
                        val file = File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "FILE_NOT_FOUND", message = "文件不存在: $filePath")
                            )
                        }
                        
                        val result = withContext(Dispatchers.IO) {
                            OfficeDocumentRenderer.deletePptxSlide(file, slideIndex)
                        }
                        if (result.isSuccess) {
                            MCPResult(
                                success = true,
                                data = mapOf("message" to "删除幻灯片成功")
                            )
                        } else {
                            MCPResult(
                                success = false,
                                error = MCPError(code = "MODIFICATION_FAILED", message = result.exceptionOrNull()?.message ?: "删除幻灯片失败")
                            )
                        }
                    } catch (e: Exception) {
                        MCPResult(
                            success = false,
                            error = MCPError(code = "MODIFICATION_FAILED", message = "删除幻灯片失败: ${e.message}")
                        )
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE, ToolPermission.WRITE_FILE)
            ),
            MCPTool(
                name = "pptx_add_text_shape",
                description = "在PPTX幻灯片中添加文本形状",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "PPTX文件路径"),
                        "slideIndex" to JsonSchemaProperty(type = "number", description = "幻灯片索引（从0开始）"),
                        "text" to JsonSchemaProperty(type = "string", description = "文本内容"),
                        "x" to JsonSchemaProperty(type = "number", description = "X坐标，默认100", default = 100),
                        "y" to JsonSchemaProperty(type = "number", description = "Y坐标，默认100", default = 100),
                        "width" to JsonSchemaProperty(type = "number", description = "宽度，默认200", default = 200),
                        "height" to JsonSchemaProperty(type = "number", description = "高度，默认50", default = 50)
                    ),
                    required = listOf("filePath", "slideIndex", "text")
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
                        
                        val slideIndex = (args["slideIndex"] as? Number)?.toInt()
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少slideIndex参数")
                            )
                        
                        val text = args["text"] as? String
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少text参数")
                            )
                        
                        val file = File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "FILE_NOT_FOUND", message = "文件不存在: $filePath")
                            )
                        }
                        
                        val x = (args["x"] as? Number)?.toDouble() ?: 100.0
                        val y = (args["y"] as? Number)?.toDouble() ?: 100.0
                        val width = (args["width"] as? Number)?.toDouble() ?: 200.0
                        val height = (args["height"] as? Number)?.toDouble() ?: 50.0
                        
                        val result = withContext(Dispatchers.IO) {
                            OfficeDocumentRenderer.addPptxTextShape(file, slideIndex, text, x, y, width, height)
                        }
                        if (result.isSuccess) {
                            MCPResult(
                                success = true,
                                data = mapOf("message" to "添加文本形状成功")
                            )
                        } else {
                            MCPResult(
                                success = false,
                                error = MCPError(code = "MODIFICATION_FAILED", message = result.exceptionOrNull()?.message ?: "添加文本形状失败")
                            )
                        }
                    } catch (e: Exception) {
                        MCPResult(
                            success = false,
                            error = MCPError(code = "MODIFICATION_FAILED", message = "添加文本形状失败: ${e.message}")
                        )
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE, ToolPermission.WRITE_FILE)
            )
        )
    }
}

// PowerPoint 主题颜色
private val PptOrange = Color(0xFFD24726)
private val PptOrangeLight = Color(0xFFE25B3E)
private val PptSurface = Color(0xFF2D2D2D)
private val PptSlideBg = Color(0xFF1E1E1E)
private val PptTextPrimary = Color.White
private val PptTextSecondary = Color.White.copy(alpha = 0.7f)

/**
 * PPTX 预览内容组件（美化版 - 仿 PowerPoint 风格）
 */
@Composable
private fun PptxPreviewContent(pptxInfo: OfficeDocumentRenderer.PptxInfo) {
    var scale by remember { mutableFloatStateOf(1.0f) }
    var selectedSlideIndex by remember { mutableIntStateOf(0) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 工具栏 - 固定高度，支持水平滚动
        Surface(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            color = PptOrange,
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
                        Icons.Filled.Slideshow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "PowerPoint 预览",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${pptxInfo.slideCount} 张幻灯片",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                
                // 右侧：缩放控制
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { scale = (scale - 0.1f).coerceAtLeast(0.5f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.ZoomOut,
                            contentDescription = "缩小",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${(scale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = { scale = (scale + 0.1f).coerceAtMost(2.0f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.ZoomIn,
                            contentDescription = "放大",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = { scale = 1.0f },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.FitScreen,
                            contentDescription = "重置",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        
        // 主内容区域
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(PptSurface)
        ) {
            // 左侧：幻灯片缩略图列表
            Surface(
                modifier = Modifier
                    .width(160.dp)
                    .fillMaxHeight(),
                color = PptSlideBg
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(pptxInfo.slides) { index, slide ->
                        SlideThumbCard(
                            slide = slide,
                            slideNumber = index + 1,
                            isSelected = index == selectedSlideIndex,
                            onClick = { selectedSlideIndex = index }
                        )
                    }
                }
            }
            
            // 右侧：选中的幻灯片详情
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 2.0f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val selectedSlide = pptxInfo.slides.getOrNull(selectedSlideIndex)
                if (selectedSlide != null) {
                    SlideDetailCard(
                        slide = selectedSlide,
                        slideNumber = selectedSlideIndex + 1,
                        scale = scale
                    )
                }
            }
        }
    }
}

/**
 * 幻灯片缩略图卡片
 */
@Composable
private fun SlideThumbCard(
    slide: OfficeDocumentRenderer.PptxSlide,
    slideNumber: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PptOrange.copy(alpha = 0.3f) else Color(0xFF3D3D3D)
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, PptOrange)
        } else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 幻灯片内容预览
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                // 标题
                if (!slide.title.isNullOrBlank()) {
                    Text(
                        text = slide.title,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = PptTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // 图片指示
                if (slide.images.isNotEmpty()) {
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            Icons.Filled.Image,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = PptTextSecondary
                        )
                        Text(
                            text = "${slide.images.size}",
                            fontSize = 8.sp,
                            color = PptTextSecondary
                        )
                    }
                }
            }
            
            // 页码
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(2.dp)
            ) {
                Text(
                    text = "$slideNumber",
                    fontSize = 9.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * 幻灯片详情卡片
 */
@Composable
private fun SlideDetailCard(
    slide: OfficeDocumentRenderer.PptxSlide,
    slideNumber: Int,
    scale: Float
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .aspectRatio(16f / 9f)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale
            )
            .shadow(16.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // 幻灯片标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "幻灯片 $slideNumber",
                    fontSize = 12.sp,
                    color = PptOrange,
                    fontWeight = FontWeight.Bold
                )
                
                if (slide.images.isNotEmpty()) {
                    Surface(
                        color = PptOrange.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Image,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = PptOrange
                            )
                            Text(
                                text = "${slide.images.size} 张图片",
                                fontSize = 11.sp,
                                color = PptOrange
                            )
                        }
                    }
                }
            }
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color(0xFFEEEEEE)
            )
            
            // 标题（如果有）
            if (!slide.title.isNullOrBlank()) {
                Text(
                    text = slide.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            // 图片展示区域
            if (slide.images.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(slide.images) { index, image ->
                        SlideImageView(image, index + 1)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // 文本内容
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(slide.texts.drop(1)) { index, text ->
                    if (text.isNotBlank()) {
                        Text(
                            text = "• $text",
                            fontSize = 14.sp,
                            color = Color(0xFF555555),
                            lineHeight = 20.sp
                        )
                    }
                }
                
                if (slide.texts.size <= 1 && slide.images.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "(无内容)",
                                fontSize = 14.sp,
                                color = Color(0xFF999999)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 幻灯片图片视图
 */
@Composable
private fun SlideImageView(
    image: OfficeDocumentRenderer.PptxImage,
    imageNumber: Int
) {
    Card(
        modifier = Modifier
            .height(120.dp)
            .widthIn(min = 100.dp, max = 200.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (image.data != null) {
                // 有图片数据，显示图片
                val bitmap = remember(image.data) {
                    try {
                        BitmapFactory.decodeByteArray(image.data, 0, image.data.size)
                    } catch (e: Exception) {
                        null
                    }
                }
                
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "图片 $imageNumber",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // 解码失败
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.BrokenImage,
                            contentDescription = null,
                            tint = Color(0xFFCCCCCC),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "图片 $imageNumber",
                            fontSize = 10.sp,
                            color = Color(0xFF999999)
                        )
                    }
                }
            } else {
                // 没有图片数据
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = null,
                        tint = Color(0xFFCCCCCC),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "图片 $imageNumber",
                        fontSize = 10.sp,
                        color = Color(0xFF999999)
                    )
                }
            }
        }
    }
}
