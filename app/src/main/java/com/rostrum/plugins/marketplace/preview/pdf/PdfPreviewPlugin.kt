package com.rostrum.plugins.marketplace.preview.pdf

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF 文件预览插件
 * 
 * 使用 Android 原生 PdfRenderer API 预览 PDF 文件
 * 支持：高清渲染、缩放、滚动、页面导航
 * 
 * 协议：Apache License 2.0（可商用）
 * 
 * 插件ID: com.rostrum.plugin.preview.pdf
 * 版本: 1.0.0
 * 作者: OmniMaster Team
 * 
 * 实现 ToolPlugin 接口，提供MCP工具供AI调用
 */
class PdfPreviewPlugin : Plugin, FilePreviewPlugin, ToolPlugin {
    
    override val id = "com.rostrum.plugin.preview.pdf"
    override val name = "PDF Preview"
    override val version = "1.0.0"
    override val author = "OmniMaster Team"
    override val description = "PDF 文件预览插件，使用Android原生渲染，支持高清缩放、滚动、页面导航"
    override val category = PluginCategory.PREVIEW
    override val dependencies: List<String> = emptyList()
    
    override val supportedMimeTypes = listOf(
        "application/pdf"
    )
    
    override val supportedExtensions = listOf(".pdf")
    
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
        return supportedExtensions.any { file.extension.equals(it, ignoreCase = true) } ||
               file.mimeType?.contains("pdf") == true
    }
    
    override suspend fun createPreview(file: FileInfo): PreviewResult {
        if (!canPreview(file)) return PreviewResult.Unsupported
        
        val fileObj = File(file.path)
        if (!fileObj.exists()) {
            return PreviewResult.Error("文件不存在: ${file.path}")
        }
        
        return try {
            // 验证文件是否为有效的 PDF
            withContext(Dispatchers.IO) {
                if (!fileObj.canRead()) {
                    throw SecurityException("无法读取文件，请检查文件权限")
                }
                
                // 检查文件大小（避免读取过大文件）
                val fileSize = fileObj.length()
                if (fileSize > 100 * 1024 * 1024) { // 100MB
                    throw IllegalArgumentException("文件过大（${fileSize / 1024 / 1024}MB），请使用外部应用打开")
                }
                
                val bytes = fileObj.inputStream().use { 
                    val buffer = ByteArray(4096)
                    it.read(buffer)
                    buffer
                } // 只读取前4KB检查
                val pdfHeader = "%PDF".toByteArray()
                if (bytes.size < 4 || !bytes.take(4).toByteArray().contentEquals(pdfHeader)) {
                    throw IllegalArgumentException("无效的 PDF 文件格式")
                }
            }
            
            // 创建预览组件
            val previewComponent: @Composable () -> Unit = {
                PdfPreviewContent(fileObj)
            }
            
            PreviewResult.Success(
                previewComponent = previewComponent,
                metadata = PreviewMetadata(
                    title = file.name,
                    description = "PDF 文档预览",
                    canEdit = false,
                    canExport = true
                )
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程被取消，这是正常的（用户切换文件等），不应该显示错误
            throw e // 重新抛出以正确传播取消
        } catch (e: SecurityException) {
            PreviewResult.Error("文件权限错误: ${e.message}", e)
        } catch (e: OutOfMemoryError) {
            PreviewResult.Error("内存不足，文件可能过大", e)
        } catch (e: Exception) {
            android.util.Log.e("PdfPreviewPlugin", "PDF预览失败", e)
            PreviewResult.Error("预览失败: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }
    
    override fun getPreviewPriority(file: FileInfo) = 100
    
    // ==================== ToolPlugin 实现 ====================
    
    override val toolCategory = ToolCategory.FILE_SYSTEM
    
    override fun getMCPTools(): List<MCPTool> {
        return listOf(
            MCPTool(
                name = "pdf_get_info",
                description = "获取PDF文件的详细信息（页数、大小等）",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "PDF文件路径")
                    ),
                    required = listOf("filePath")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "pageCount" to JsonSchemaProperty(type = "number", description = "页数"),
                        "fileSize" to JsonSchemaProperty(type = "number", description = "文件大小（字节）")
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
                        
                        // 使用 PdfRenderer 获取页数
                        val pageCount = withContext(Dispatchers.IO) {
                            try {
                                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                                val renderer = PdfRenderer(pfd)
                                val count = renderer.pageCount
                                renderer.close()
                                pfd.close()
                                count
                            } catch (e: Exception) {
                                0
                            }
                        }
                        
                        MCPResult(
                            success = true,
                            data = mapOf(
                                "pageCount" to pageCount,
                                "fileSize" to file.length()
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
                name = "pdf_extract_text",
                description = "从PDF文件中提取文本内容（需要第三方库支持）",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "PDF文件路径"),
                        "pageNumber" to JsonSchemaProperty(type = "number", description = "页码（从1开始），不指定则提取所有页面", default = 0)
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
                    MCPResult(
                        success = false,
                        error = MCPError(code = "NOT_IMPLEMENTED", message = "PDF文本提取功能需要PDFBox库支持，暂未集成")
                    )
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE)
            )
        )
    }
}

/**
 * PDF 页面数据
 */
private data class PdfPageData(
    val pageIndex: Int,
    val bitmap: Bitmap?,
    val width: Int,
    val height: Int,
    val isLoading: Boolean = false
)

/**
 * PDF 预览内容组件（使用 Android 原生 PdfRenderer）
 */
@Composable
private fun PdfPreviewContent(file: File) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }
    var scale by remember { mutableFloatStateOf(1.0f) }
    var pages by remember { mutableStateOf<List<PdfPageData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pdfFileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    
    // 加载PDF
    LaunchedEffect(file.absolutePath) {
        isLoading = true
        error = null
        
        try {
            withContext(Dispatchers.IO) {
            if (!file.exists() || !file.canRead()) {
                    error = "无法读取文件，请检查文件权限"
                    return@withContext
                }
                
                val pfd = try {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } catch (e: Exception) {
                    error = "无法打开文件: ${e.message}"
                    return@withContext
                }
                
                val renderer = try {
                    PdfRenderer(pfd)
                } catch (e: Exception) {
                    pfd.close()
                    error = "PDF文件格式错误或已损坏: ${e.message}"
                    return@withContext
                }
                
                // 先获取页数
                val totalPageCount = renderer.pageCount
                
                // 保存renderer和pfd供懒加载使用（在UI线程设置）
                withContext(Dispatchers.Main) {
                    if (currentCoroutineContext().isActive) {
                        pdfFileDescriptor = pfd
                        pdfRenderer = renderer
                        pageCount = totalPageCount
                    } else {
                        // 协程已取消，清理资源
                        renderer.close()
                        pfd.close()
                        return@withContext
                    }
                }
                
                // 检查协程是否仍然活跃
                if (!currentCoroutineContext().isActive) {
                    return@withContext
                }
                
                // 不限制页数，使用懒加载模式
                // 只预加载前几页，其他页面在滚动时按需加载
                val preloadPages = minOf(totalPageCount, 10) // 预加载前10页
                val renderedPages = mutableListOf<PdfPageData>()
                
                // 使用保存的renderer引用，但每次使用前检查
                var currentRenderer: PdfRenderer? = null
                withContext(Dispatchers.Main) {
                    currentRenderer = pdfRenderer
                }
                
                for (i in 0 until preloadPages) {
                    // 检查协程是否被取消
                    if (!currentCoroutineContext().isActive) {
                        break
                    }
                    
                    // 检查renderer是否仍然有效
                    val rendererToUse = withContext(Dispatchers.Main) {
                        pdfRenderer
                    }
                    
                    if (rendererToUse == null) {
                        Log.d("PdfPreview", "Renderer已关闭，停止预加载")
                        break
                    }
                    
                    try {
                        val page = rendererToUse.openPage(i)
                        
                        try {
                            // 计算缩放后的尺寸（提高分辨率，但限制最大尺寸）
                            val scaleFactor = 1.5f
                            val baseWidth = page.width
                            val baseHeight = page.height
                            val width = (baseWidth * scaleFactor).toInt().coerceAtMost(2048)
                            val height = (baseHeight * scaleFactor).toInt().coerceAtMost(2048)
                            
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            // 白色背景
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            
                            page.render(
                                bitmap,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                            )
                            
                            renderedPages.add(PdfPageData(i, bitmap, baseWidth, baseHeight))
                        } catch (e: OutOfMemoryError) {
                            Log.e("PdfPreview", "渲染第${i+1}页时内存不足", e)
                            // 跳过这一页，继续处理其他页
                        } catch (e: IllegalStateException) {
                            // Renderer已关闭
                            Log.d("PdfPreview", "Renderer已关闭，停止预加载")
                            break
                        } catch (e: Exception) {
                            Log.e("PdfPreview", "渲染第${i+1}页失败", e)
                            // 跳过这一页，继续处理其他页
                        } finally {
                            try {
                                page.close()
                            } catch (e: Exception) {
                                // 忽略关闭错误
                            }
                        }
                    } catch (e: IllegalStateException) {
                        // Renderer已关闭
                        Log.d("PdfPreview", "Renderer已关闭，停止预加载")
                        break
                    } catch (e: Exception) {
                        Log.e("PdfPreview", "打开第${i+1}页失败", e)
                        // 继续处理其他页
                    }
                }
                
                // 为未加载的页面创建占位符
                for (i in renderedPages.size until totalPageCount) {
                    renderedPages.add(PdfPageData(i, null, 0, 0, isLoading = false))
                }
                
                // 在UI线程更新状态
                withContext(Dispatchers.Main) {
                    if (currentCoroutineContext().isActive && pdfRenderer != null) {
                        pages = renderedPages
                    } else {
                        // 协程已取消或renderer已关闭，清理bitmap
                        renderedPages.forEach { it.bitmap?.recycle() }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程被取消（用户切换文件等），这是正常的，不应该显示错误
            Log.d("PdfPreview", "PDF加载被取消（用户切换文件）")
            // 不设置错误状态，直接返回
            return@LaunchedEffect
        } catch (e: OutOfMemoryError) {
            Log.e("PdfPreview", "加载PDF时内存不足", e)
            error = "内存不足，文件可能过大。建议使用外部应用打开"
            isLoading = false
        } catch (e: SecurityException) {
            Log.e("PdfPreview", "文件权限错误", e)
            error = "文件权限错误: ${e.message}"
            isLoading = false
        } catch (e: Exception) {
            // 检查是否是Compose取消异常（通过消息判断）
            if (e.message?.contains("left the composition") == true || 
                e.javaClass.simpleName.contains("CancellationException")) {
                Log.d("PdfPreview", "PDF加载被取消（组件离开组合）")
                return@LaunchedEffect
            }
            Log.e("PdfPreview", "加载PDF失败", e)
            error = "加载失败: ${e.message ?: e.javaClass.simpleName}"
            isLoading = false
        } finally {
            if (currentCoroutineContext().isActive) {
                isLoading = false
            }
        }
    }
    
    // 清理资源
    DisposableEffect(file.absolutePath) {
        onDispose {
            // 先清空renderer引用，防止其他协程继续使用
            val rendererToClose = pdfRenderer
            val pfdToClose = pdfFileDescriptor
            pdfRenderer = null
            pdfFileDescriptor = null
            
            // 清理所有页面的bitmap
            pages.forEach { it.bitmap?.recycle() }
            
            // 关闭PDF renderer和文件描述符（在IO线程）
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    rendererToClose?.close()
                } catch (e: Exception) {
                    // 忽略关闭错误（可能已经关闭）
                }
                try {
                    pfdToClose?.close()
                } catch (e: Exception) {
                    // 忽略关闭错误（可能已经关闭）
                }
            }
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 工具栏 - 固定高度，支持水平滚动
        Surface(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
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
                // 左侧：标题和页数
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
            Text(
                text = "PDF 预览",
                            style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
                        if (pageCount > 0) {
                            Text(
                                text = "共 $pageCount 页",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // 右侧：缩放控制和外部打开
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 缩小按钮
                    IconButton(
                        onClick = { scale = (scale - 0.25f).coerceAtLeast(0.5f) },
                        enabled = scale > 0.5f,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.ZoomOut,
                            contentDescription = "缩小",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // 缩放比例
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${(scale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    
                    // 放大按钮
                    IconButton(
                        onClick = { scale = (scale + 0.25f).coerceAtMost(3.0f) },
                        enabled = scale < 3.0f,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.ZoomIn,
                            contentDescription = "放大",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // 重置按钮
                    IconButton(
                        onClick = { scale = 1.0f },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.FitScreen,
                            contentDescription = "重置",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
            
            // 使用外部应用打开
                    FilledTonalButton(
                onClick = {
                    try {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        error = "无法打开 PDF: ${e.message}"
                            }
                        },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            Icons.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("外部打开", fontSize = 12.sp)
                    }
                }
            }
        }
        
        // PDF 预览区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF525659))
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                "正在加载 PDF...",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                
                error != null -> {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                            modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                        )
                            Spacer(modifier = Modifier.height(12.dp))
                        Text(
                                text = error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                            Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                try {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/pdf")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                        // ignore
                                    }
                                }
                            ) {
                                Text("使用外部应用打开")
                            }
                        }
                    }
                }
                
                pages.isNotEmpty() -> {
                    // 页面列表（支持缩放）
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.5f, 3.0f)
                                }
                            },
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(pages) { index, pageData ->
                            PdfPageView(
                                pageData = pageData,
                                pageNumber = index + 1,
                                scale = scale,
                                renderer = pdfRenderer,
                                onPageLoad = { pageIndex, bitmap, width, height ->
                                    // 更新页面数据
                                    val updatedPages = pages.toMutableList()
                                    if (pageIndex < updatedPages.size) {
                                        updatedPages[pageIndex] = PdfPageData(pageIndex, bitmap, width, height)
                                        pages = updatedPages
                                    } else {
                                        // 索引超出范围，回收bitmap
                                        bitmap?.recycle()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单个 PDF 页面视图（支持懒加载）
 */
@Composable
private fun PdfPageView(
    pageData: PdfPageData,
    pageNumber: Int,
    scale: Float,
    renderer: PdfRenderer?,
    onPageLoad: (Int, Bitmap?, Int, Int) -> Unit
) {
    // 如果页面未加载且renderer可用，尝试加载
    LaunchedEffect(pageData.pageIndex, pageData.bitmap, renderer) {
        if (pageData.bitmap == null && pageData.pageIndex >= 0 && renderer != null) {
            try {
                withContext(Dispatchers.IO) {
                    if (!currentCoroutineContext().isActive) return@withContext
                    
                    // 检查renderer是否仍然有效
                    val rendererToUse = renderer
                    if (rendererToUse == null) {
                        Log.d("PdfPreview", "Renderer为null，取消懒加载第${pageNumber}页")
                        return@withContext
                    }
                    
                    try {
                        val page = rendererToUse.openPage(pageData.pageIndex)
                        
                        try {
                            val scaleFactor = 1.5f
                            val baseWidth = page.width
                            val baseHeight = page.height
                            val width = (baseWidth * scaleFactor).toInt().coerceAtMost(2048)
                            val height = (baseHeight * scaleFactor).toInt().coerceAtMost(2048)
                            
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            
                            page.render(
                                bitmap,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                            )
                            
                            // 切换到主线程更新UI
                            withContext(Dispatchers.Main) {
                                if (currentCoroutineContext().isActive && renderer != null) {
                                    onPageLoad(pageData.pageIndex, bitmap, baseWidth, baseHeight)
                                } else {
                                    bitmap.recycle()
                                }
                            }
                        } catch (e: OutOfMemoryError) {
                            Log.e("PdfPreview", "懒加载第${pageNumber}页时内存不足", e)
                            // 加载失败，保持null状态
                        } catch (e: IllegalStateException) {
                            // Renderer已关闭
                            Log.d("PdfPreview", "Renderer已关闭，取消懒加载第${pageNumber}页")
                            // 加载失败，保持null状态
                        } catch (e: Exception) {
                            Log.e("PdfPreview", "懒加载第${pageNumber}页失败", e)
                            // 加载失败，保持null状态
                        } finally {
                            try {
                                page.close()
                            } catch (e: Exception) {
                                // 忽略关闭错误
                            }
                        }
                    } catch (e: IllegalStateException) {
                        // Renderer已关闭
                        Log.d("PdfPreview", "Renderer已关闭，无法打开第${pageNumber}页")
                    } catch (e: NullPointerException) {
                        // PdfDocumentProxy为null
                        Log.d("PdfPreview", "PdfDocumentProxy为null，取消懒加载第${pageNumber}页")
                    } catch (e: Exception) {
                        Log.e("PdfPreview", "打开第${pageNumber}页失败", e)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被取消，忽略
            } catch (e: Exception) {
                // 检查是否是取消异常
                if (e.message?.contains("left the composition") == true || 
                    e.javaClass.simpleName.contains("CancellationException")) {
                    // 忽略取消异常
                    return@LaunchedEffect
                }
                Log.e("PdfPreview", "懒加载第${pageNumber}页异常", e)
                // 加载失败，保持null状态
            }
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 页面
        Card(
            modifier = Modifier
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale
                )
                .shadow(8.dp, RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            if (pageData.bitmap != null) {
                Image(
                    bitmap = pageData.bitmap.asImageBitmap(),
                    contentDescription = "第 $pageNumber 页",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            } else {
                // 占位符（加载中或未加载）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .background(Color(0xFFF5F5F5)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color(0xFF6366F1)
                        )
                        Text(
                            text = "加载第 $pageNumber 页...",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
        
        // 页码标签
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "第 $pageNumber 页",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
