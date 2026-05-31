package com.rostrum.plugins.marketplace.preview.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.rostrum.core.filesystem.FileSystemService
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
import com.rostrum.ui.main.LoadingSpinner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 图片预览插件
 * 
 * 支持: jpg, jpeg, png, bmp, webp, gif
 * 功能: 缩放、旋转、翻转
 * 支持本地文件和SSH远程文件
 * 
 * 实现 ToolPlugin 接口，提供MCP工具供AI调用
 */
class ImagePreviewPlugin : Plugin, FilePreviewPlugin, ToolPlugin {
    
    override val id = "com.rostrum.plugin.preview.image"
    override val name = "Image Preview"
    override val version = "1.0.0"
    override val author = "OmniMaster Team"
    override val description = "图片预览插件，支持缩放、旋转、翻转等操作"
    override val category = PluginCategory.PREVIEW
    override val dependencies: List<String> = emptyList()
    
    override val supportedMimeTypes = listOf(
        "image/jpeg", "image/png", "image/bmp", "image/webp", "image/gif"
    )
    
    override val supportedExtensions = listOf(
        ".jpg", ".jpeg", ".png", ".bmp", ".webp", ".gif"
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
        return supportedExtensions.any { file.extension.equals(it, ignoreCase = true) } ||
               supportedMimeTypes.any { file.mimeType?.contains(it) == true }
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
        
        return PreviewResult.Success(
            previewComponent = { ImagePreviewContent(fileObj) },
            metadata = PreviewMetadata(
                title = file.name,
                description = "图片预览",
                canEdit = true,
                canExport = true
            )
        )
    }
    
    /**
     * 创建预览组件（支持远程文件）
     */
    override suspend fun createPreview(file: FileInfo, fileSystem: FileSystemService): PreviewResult {
        if (!canPreview(file)) return PreviewResult.Unsupported
        
        // 远程文件：使用FileSystemService读取
        if (file.isRemote) {
            return PreviewResult.Success(
                previewComponent = { ImageRemotePreviewContent(file.path, file.name, fileSystem) },
                metadata = PreviewMetadata(
                    title = file.name,
                    description = "图片预览 (远程)",
                    canEdit = false,
                    canExport = false
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
                name = "image_get_info",
                description = "获取图片文件的详细信息（尺寸、格式、大小等）",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "图片文件路径")
                    ),
                    required = listOf("filePath")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "width" to JsonSchemaProperty(type = "number", description = "图片宽度（像素）"),
                        "height" to JsonSchemaProperty(type = "number", description = "图片高度（像素）"),
                        "format" to JsonSchemaProperty(type = "string", description = "图片格式（JPEG/PNG/GIF等）"),
                        "size" to JsonSchemaProperty(type = "number", description = "文件大小（字节）")
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
                        
                        // 只获取图片尺寸，不加载完整图片
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeFile(filePath, options)
                        
                        val width = options.outWidth
                        val height = options.outHeight
                        val mimeType = options.outMimeType ?: "unknown"
                        val format = when {
                            mimeType.contains("jpeg") || mimeType.contains("jpg") -> "JPEG"
                            mimeType.contains("png") -> "PNG"
                            mimeType.contains("gif") -> "GIF"
                            mimeType.contains("webp") -> "WebP"
                            mimeType.contains("bmp") -> "BMP"
                            else -> mimeType
                        }
                        
                        MCPResult(
                            success = true,
                            data = mapOf(
                                "success" to true,
                                "width" to width,
                                "height" to height,
                                "format" to format,
                                "size" to file.length(),
                                "mimeType" to mimeType
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
                name = "image_resize",
                description = "调整图片尺寸",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "源图片路径"),
                        "outputPath" to JsonSchemaProperty(type = "string", description = "输出图片路径"),
                        "width" to JsonSchemaProperty(type = "number", description = "目标宽度（像素）"),
                        "height" to JsonSchemaProperty(type = "number", description = "目标高度（像素）"),
                        "keepAspectRatio" to JsonSchemaProperty(
                            type = "boolean",
                            description = "是否保持宽高比，默认true",
                            default = true
                        )
                    ),
                    required = listOf("filePath", "outputPath", "width", "height")
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
                            ?: return@MCPTool MCPResult(success = false, error = MCPError("INVALID_ARGS", "缺少filePath参数"))
                        val outputPath = args["outputPath"] as? String
                            ?: return@MCPTool MCPResult(success = false, error = MCPError("INVALID_ARGS", "缺少outputPath参数"))
                        val targetWidth = (args["width"] as? Number)?.toInt()
                            ?: return@MCPTool MCPResult(success = false, error = MCPError("INVALID_ARGS", "缺少width参数"))
                        val targetHeight = (args["height"] as? Number)?.toInt()
                            ?: return@MCPTool MCPResult(success = false, error = MCPError("INVALID_ARGS", "缺少height参数"))
                        val keepAspectRatio = args["keepAspectRatio"] as? Boolean ?: true
                        
                        val sourceFile = java.io.File(filePath)
                        if (!sourceFile.exists()) {
                            return@MCPTool MCPResult(success = false, error = MCPError("FILE_NOT_FOUND", "源文件不存在"))
                        }
                        
                        // 加载原图
                        val originalBitmap = BitmapFactory.decodeFile(filePath)
                            ?: return@MCPTool MCPResult(success = false, error = MCPError("DECODE_ERROR", "无法解码图片"))
                        
                        // 计算目标尺寸
                        val (finalWidth, finalHeight) = if (keepAspectRatio) {
                            val ratio = minOf(
                                targetWidth.toFloat() / originalBitmap.width,
                                targetHeight.toFloat() / originalBitmap.height
                            )
                            Pair(
                                (originalBitmap.width * ratio).toInt(),
                                (originalBitmap.height * ratio).toInt()
                            )
                        } else {
                            Pair(targetWidth, targetHeight)
                        }
                        
                        // 调整尺寸
                        val resizedBitmap = Bitmap.createScaledBitmap(
                            originalBitmap, finalWidth, finalHeight, true
                        )
                        
                        // 保存
                        val outputFile = java.io.File(outputPath)
                        outputFile.parentFile?.mkdirs()
                        val format = when {
                            outputPath.endsWith(".png", ignoreCase = true) -> Bitmap.CompressFormat.PNG
                            outputPath.endsWith(".webp", ignoreCase = true) -> Bitmap.CompressFormat.WEBP
                            else -> Bitmap.CompressFormat.JPEG
                        }
                        outputFile.outputStream().use { out ->
                            resizedBitmap.compress(format, 90, out)
                        }
                        
                        // 释放资源
                        originalBitmap.recycle()
                        resizedBitmap.recycle()
                        
                        MCPResult(
                            success = true,
                            data = mapOf(
                                "success" to true,
                                "message" to "图片已调整为 ${finalWidth}x${finalHeight} 并保存到 $outputPath"
                            )
                        )
                    } catch (e: Exception) {
                        MCPResult(success = false, error = MCPError("ERROR", e.message ?: "未知错误"))
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE, ToolPermission.WRITE_FILE)
            )
        )
    }
}

@Composable
private fun ImagePreviewContent(file: File) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // 变换状态
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var flipX by remember { mutableFloatStateOf(1f) }
    
    // 加载图片
    LaunchedEffect(file.absolutePath) {
        withContext(Dispatchers.IO) {
            try {
                if (!file.exists() || !file.canRead()) {
                    error = "无法读取文件"
                    isLoading = false
                    return@withContext
                }
                
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
                
                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    error = "无效的图片文件"
                    isLoading = false
                    return@withContext
                }
                
                // 计算采样率避免 OOM
                val maxSize = 2048
                var sampleSize = 1
                while (options.outWidth / sampleSize > maxSize || options.outHeight / sampleSize > maxSize) {
                    sampleSize *= 2
                }
                
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                val loadedBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                if (loadedBitmap != null) {
                    bitmap = loadedBitmap
                } else {
                    error = "图片解码失败"
                }
                isLoading = false
            } catch (e: OutOfMemoryError) {
                error = "图片太大，内存不足"
                isLoading = false
            } catch (e: Exception) {
                error = "加载失败: ${e.message}"
                isLoading = false
            }
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
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩小
            IconButton(onClick = { scale = (scale * 0.8f).coerceAtLeast(0.1f) }) {
                Icon(Icons.Default.ZoomOut, "缩小")
            }
            // 重置
            IconButton(onClick = { 
                scale = 1f
                offset = Offset.Zero
                rotation = 0f
                flipX = 1f
            }) {
                Icon(Icons.Default.Refresh, "重置")
            }
            // 放大
            IconButton(onClick = { scale = (scale * 1.25f).coerceAtMost(5f) }) {
                Icon(Icons.Default.ZoomIn, "放大")
            }
            
                Spacer(Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outline))
            
            // 左旋转
            IconButton(onClick = { rotation -= 90f }) {
                Icon(Icons.Default.RotateLeft, "左旋转")
            }
            // 右旋转
            IconButton(onClick = { rotation += 90f }) {
                Icon(Icons.Default.RotateRight, "右旋转")
            }
            // 水平翻转
            IconButton(onClick = { flipX *= -1f }) {
                Icon(Icons.Default.Flip, "翻转")
            }
        }
        
        // 图片显示区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.1f, 5f)
                        offset += pan
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> Box(modifier = Modifier.fillMaxSize()) // 不显示"加载中"
                error != null -> Text(error!!, color = Color.Red)
                bitmap != null -> {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = file.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale * flipX,
                                scaleY = scale,
                                rotationZ = rotation,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )
                }
                else -> Text("无法加载图片", color = Color.White)
            }
        }
    }
}

/**
 * 远程图片预览组件
 * 
 * 通过 FileSystemService 读取SSH远程文件内容
 */
@Composable
private fun ImageRemotePreviewContent(
    filePath: String,
    fileName: String,
    fileSystem: FileSystemService
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // 图像变换参数
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var flipX by remember { mutableFloatStateOf(1f) }
    
    // 从远程文件系统加载图片
    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val result = fileSystem.readFile(filePath)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        val data = result.getOrThrow()
                        val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
                        if (bmp != null) {
                            bitmap = bmp
                        } else {
                            error = "无法解码图片"
                        }
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
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D2D))
                .padding(8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "图片预览 (远程)",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
            
            bitmap?.let {
                Text(
                    text = "${it.width}x${it.height}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 重置
            IconButton(onClick = { 
                scale = 1f
                rotation = 0f
                offset = Offset.Zero
                flipX = 1f
            }) {
                Icon(Icons.Default.RestartAlt, "重置", tint = Color.White)
            }
            
            // 缩小
            IconButton(onClick = { scale = (scale * 0.9f).coerceIn(0.1f, 5f) }) {
                Icon(Icons.Default.ZoomOut, "缩小", tint = Color.White)
            }
            // 放大
            IconButton(onClick = { scale = (scale * 1.1f).coerceIn(0.1f, 5f) }) {
                Icon(Icons.Default.ZoomIn, "放大", tint = Color.White)
            }
            
            // 左旋转
            IconButton(onClick = { rotation -= 90f }) {
                Icon(Icons.Default.RotateLeft, "左旋转", tint = Color.White)
            }
            // 右旋转
            IconButton(onClick = { rotation += 90f }) {
                Icon(Icons.Default.RotateRight, "右旋转", tint = Color.White)
            }
            // 水平翻转
            IconButton(onClick = { flipX *= -1f }) {
                Icon(Icons.Default.Flip, "翻转", tint = Color.White)
            }
        }
        
        // 图片显示区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.1f, 5f)
                        offset += pan
                    }
                },
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
                            text = "正在加载远程图片...",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                error != null -> Text(error!!, color = Color.Red)
                bitmap != null -> {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = fileName,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale * flipX,
                                scaleY = scale,
                                rotationZ = rotation,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )
                }
                else -> Text("无法加载图片", color = Color.White)
            }
        }
    }
}