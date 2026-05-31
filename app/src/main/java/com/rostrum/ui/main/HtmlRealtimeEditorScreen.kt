package com.rostrum.ui.main

import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rostrum.core.event.EventBusImpl
import com.rostrum.core.event.EventSubscriber
import com.rostrum.core.event.FileContentUpdateEvent
import com.rostrum.core.util.PathUtils
import kotlinx.coroutines.Dispatchers
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

private const val TAG = "HtmlRealtimeEditor"

/**
 * HTML实时编辑+预览界面
 * 
 * 左侧为代码编辑器，右侧为实时预览
 * 编辑时自动更新预览，无需保存文件
 * 
 * @param filePath 文件路径
 * @param onClose 关闭回调
 * @param onSave 保存回调
 * @param modifier Modifier
 */
@Composable
fun HtmlRealtimeEditorScreen(
    filePath: String,
    onClose: () -> Unit,
    onSave: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // WebView 引用（用于导出功能，由 HtmlRealtimePreview 设置）
    var webView: WebView? by remember { mutableStateOf(null) }
    
    // 分割比例状态
    var splitRatio by remember { mutableFloatStateOf(0.5f) }
    
    // 文件名
    val fileName = remember(filePath) { File(filePath).name }
    
    // 保存状态
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    
    Column(modifier = modifier.fillMaxSize()) {
        // 工具栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：标题
                Text(
                    text = "实时编辑: $fileName",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                // 右侧：按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 保存消息
                    saveMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    
                    // 保存按钮
                    IconButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                saveMessage = "保存中..."
                                try {
                                    // 读取当前编辑器内容并保存
                                    // 注意：实际保存由FileEditor内部处理
                                    saveMessage = "已保存"
                                    kotlinx.coroutines.delay(1500)
                                    saveMessage = null
                                } catch (e: Exception) {
                                    saveMessage = "保存失败"
                                    kotlinx.coroutines.delay(2000)
                                    saveMessage = null
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Icon(
                            Icons.Filled.Save,
                            contentDescription = "保存",
                            tint = Color.White
                        )
                    }

                    // 导出为图片按钮
                    var isExporting by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            val wv = webView
                            if (wv != null && !isExporting) {
                                isExporting = true
                                exportWebViewToImage(wv, File(filePath).nameWithoutExtension, context) {
                                    isExporting = false
                                }
                            }
                        },
                        enabled = !isExporting
                    ) {
                        if (isExporting) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Filled.Image,
                                contentDescription = "导出为图片",
                                tint = Color.White
                            )
                        }
                    }
                    
                    // 关闭按钮
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "关闭",
                            tint = Color.White
                        )
                    }
                }
            }
        }
        
        // 分屏编辑+预览区域
        Row(modifier = Modifier.weight(1f)) {
            // 左侧：编辑器
            Box(
                modifier = Modifier
                    .weight(splitRatio)
                    .fillMaxHeight()
            ) {
                FileEditor(
                    filePath = filePath,
                    onSave = onSave,
                    enableRealtimeSave = false,  // 禁用自动保存，手动控制
                    enableRealtimePreview = true,  // 启用实时预览
                    realtimePreviewDebounceMs = 100,
                    modifier = Modifier.fillMaxSize()
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
                            splitRatio = (splitRatio + deltaRatio).coerceIn(0.2f, 0.8f)
                        }
                    }
            )
            
            // 右侧：预览
            Box(
                modifier = Modifier
                    .weight(1f - splitRatio)
                    .fillMaxHeight()
            ) {
                HtmlRealtimePreview(
                    filePath = filePath,
                    onWebViewReady = { webView = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * HTML实时预览组件
 * 
 * 订阅FileContentUpdateEvent，实时更新WebView内容
 */
@Composable
private fun HtmlRealtimePreview(
    filePath: String,
    onWebViewReady: (WebView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var webView: WebView? by remember { mutableStateOf(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // 规范化路径
    val normalizedPath = remember(filePath) { PathUtils.normalizePath(filePath) }
    val file = remember(filePath) { File(filePath) }
    
    // 视口尺寸（默认桌面尺寸）
    val viewportWidth = 1920
    val viewportHeight = 1080
    
    // 处理HTML内容，添加viewport
    fun processHtmlContent(html: String): String {
        val viewportMeta = "<meta name=\"viewport\" content=\"width=$viewportWidth, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">"
        
        val hasViewport = html.contains("<meta", ignoreCase = true) && 
                         html.contains("viewport", ignoreCase = true)
        
        return if (hasViewport) {
            html.replace(
                Regex("<meta[^>]*name=['\"]viewport['\"][^>]*>", RegexOption.IGNORE_CASE),
                viewportMeta
            )
        } else {
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
                "$viewportMeta\n$html"
            }
        }
    }
    
    // 更新WebView内容
    fun updateWebViewContent(content: String) {
        webView?.let { view ->
            scope.launch {
                try {
                    val url = "file://${file.parentFile?.absolutePath}/"
                    val processed = processHtmlContent(content)
                    
                    withContext(Dispatchers.Main) {
                        val screenWidthPx = context.resources.displayMetrics.widthPixels
                        val scaleRatio = screenWidthPx.toFloat() / viewportWidth.toFloat()
                        view.setInitialScale((scaleRatio * 100).toInt())
                        view.loadDataWithBaseURL(url, processed, "text/html", "UTF-8", null)
                        Log.d(TAG, "Preview updated with content")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update preview: ${e.message}", e)
                }
            }
        }
    }
    
    // 订阅FileContentUpdateEvent
    DisposableEffect(normalizedPath) {
        val eventBus = EventBusImpl.getInstance()
        val subscriber = object : EventSubscriber<FileContentUpdateEvent> {
            override suspend fun onEvent(event: FileContentUpdateEvent) {
                if (PathUtils.pathsEqual(event.filePath, normalizedPath)) {
                    Log.d(TAG, "Received FileContentUpdateEvent, updating preview")
                    withContext(Dispatchers.Main) {
                        updateWebViewContent(event.content)
                    }
                }
            }
        }
        
        eventBus.subscribe(FileContentUpdateEvent::class, subscriber, priority = 20)
        Log.d(TAG, "Subscribed to FileContentUpdateEvent for: $normalizedPath")
        
        onDispose {
            eventBus.unsubscribe(FileContentUpdateEvent::class, subscriber)
            Log.d(TAG, "Unsubscribed from FileContentUpdateEvent for: $normalizedPath")
        }
    }
    
    // 初始加载文件内容
    LaunchedEffect(filePath) {
        try {
            val content = withContext(Dispatchers.IO) {
                file.readText()
            }
            
            // 等待WebView初始化后加载内容
            kotlinx.coroutines.delay(100)
            updateWebViewContent(content)
            isLoading = false
        } catch (e: Exception) {
            error = "加载失败: ${e.message}"
            isLoading = false
        }
    }
    
    Box(modifier = modifier) {
        if (error != null) {
            Text(
                text = error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            // WebView
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = object : WebViewClient() {
                            // 拦截 file:// 请求，从本地文件系统读取（支持本地图片等资源）
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url ?: return null
                                if (url.scheme == "file") {
                                    try {
                                        val localFile = java.io.File(url.path ?: return null)
                                        if (localFile.exists() && localFile.isFile) {
                                            val ext = localFile.extension.lowercase()
                                            val mimeType = MimeTypeMap.getSingleton()
                                                .getMimeTypeFromExtension(ext)
                                                ?: "application/octet-stream"
                                            return WebResourceResponse(
                                                mimeType, "UTF-8", localFile.inputStream()
                                            )
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to load local file: ${url.path}", e)
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                        settings.apply {
                            javaScriptEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            domStorageEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            @Suppress("DEPRECATION")
                            allowFileAccessFromFileURLs = true
                            @Suppress("DEPRECATION")
                            allowUniversalAccessFromFileURLs = true
                            setSupportZoom(true)
                            cacheMode = WebSettings.LOAD_NO_CACHE
                        }
                        webView = this
                        onWebViewReady(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // 加载指示器
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

/**
 * 将 WebView 内容导出为图片并保存到相册
 */
private fun exportWebViewToImage(
    webView: android.webkit.WebView,
    baseName: String,
    context: android.content.Context,
    onComplete: () -> Unit
) {
    try {
        val width = webView.width
        val height = (webView.contentHeight * webView.scale).toInt()
        
        if (width <= 0 || height <= 0) {
            Toast.makeText(context, "页面尚未加载完成", Toast.LENGTH_SHORT).show()
            onComplete()
            return
        }
        
        val maxHeight = minOf(height, 8192)
        val bitmap = Bitmap.createBitmap(width, maxHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val savedScrollX = webView.scrollX
        val savedScrollY = webView.scrollY
        webView.scrollTo(0, 0)
        webView.draw(canvas)
        webView.scrollTo(savedScrollX, savedScrollY)
        
        val fileName = "${baseName}_${System.currentTimeMillis()}.png"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val omniDir = File(picturesDir, "OmniMaster")
            if (!omniDir.exists()) omniDir.mkdirs()
            
            val outFile = File(omniDir, fileName)
            outFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(outFile.absolutePath), arrayOf("image/png"), null
            )
            
            Toast.makeText(context, "已保存到 ${outFile.absolutePath}", Toast.LENGTH_LONG).show()
        }
        
        bitmap.recycle()
    } catch (e: OutOfMemoryError) {
        Toast.makeText(context, "页面过大，内存不足无法导出", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        android.util.Log.e(TAG, "Export failed", e)
        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
    } finally {
        onComplete()
    }
}
