package com.rostrum.ui.main

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import com.rostrum.core.event.EventBusImpl
import com.rostrum.core.event.FileModifiedEvent
import com.rostrum.core.event.FileContentUpdateEvent
import com.rostrum.core.plugin.language.EditorLanguageService
import com.rostrum.core.plugin.providers.CompletionItem
import com.rostrum.core.plugin.providers.DefinitionLocation
import com.rostrum.core.plugin.providers.Diagnostic
import com.rostrum.core.util.PathUtils
import com.rostrum.core.filesystem.ActiveFileSystemManager

/**
 * 保存状态
 */
enum class SaveStatus {
    IDLE,       // 空闲
    SAVING,     // 保存中
    SAVED,      // 已保存
    ERROR       // 错误
}

/**
 * 文件编辑器
 * 
 * @param filePath 文件路径
 * @param onSave 保存回调
 * @param onClose 关闭回调（可选）
 * @param isStreamingMode 是否为流式模式（跳过磁盘读取，等待事件更新内容）
 * @param enableRealtimeSave 是否启用实时保存（HTML文件默认启用）
 * @param saveDelayMs 实时保存延迟（毫秒）
 * @param enableRealtimePreview 是否启用实时预览（发布FileContentUpdateEvent，无需保存文件）
 * @param realtimePreviewDebounceMs 实时预览防抖时间（毫秒）
 * @param modifier Modifier
 */
@Composable
fun FileEditor(
    filePath: String?,
    onSave: (String, String) -> Unit, // path, newContent
    onClose: (() -> Unit)? = null,
    onDefinitionResolved: ((DefinitionLocation) -> Unit)? = null,
    isStreamingMode: Boolean = false,
    enableRealtimeSave: Boolean = filePath?.let { 
        it.endsWith(".html", ignoreCase = true) || it.endsWith(".htm", ignoreCase = true)
    } ?: false,
    saveDelayMs: Long = 500,
    enableRealtimePreview: Boolean = false,
    realtimePreviewDebounceMs: Long = 100,
    modifier: Modifier = Modifier
) {
    val TAG = "FileEditor"
    
    var editorValue by remember { mutableStateOf(TextFieldValue("")) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isModified by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf(SaveStatus.IDLE) }
    var diagnostics by remember { mutableStateOf<List<Diagnostic>>(emptyList()) }
    var diagnosticsJob by remember { mutableStateOf<Job?>(null) }
    var completions by remember { mutableStateOf<List<CompletionItem>>(emptyList()) }
    var languageHint by remember { mutableStateOf<String?>(null) }
    var languageBusy by remember { mutableStateOf(false) }
    val content = editorValue.text
    
    // 获取事件总线
    val eventBus = remember { EventBusImpl.getInstance() }
    
    // 协程作用域
    val scope = rememberCoroutineScope()
    
    // 滚动状态（用于自动滚动到底部）
    val scrollState = rememberScrollState()
    
    // 实时保存任务
    var saveJob by remember { mutableStateOf<Job?>(null) }
    
    // 实时预览任务（不保存文件，只发布内容更新事件）
    var previewJob by remember { mutableStateOf<Job?>(null) }
    
    // 是否正在接收外部流式内容（避免与用户编辑冲突）
    var isReceivingStreamContent by remember { mutableStateOf(false) }
    
    // 流式模式下初始化
    LaunchedEffect(isStreamingMode) {
        if (isStreamingMode) {
            isReceivingStreamContent = true
            Log.d(TAG, "Streaming mode enabled, waiting for content updates")
        }
    }
    
    // 订阅 FileContentUpdateEvent，接收流式写入的内容更新
    DisposableEffect(filePath) {
        if (filePath == null) {
            onDispose { }
        } else {
            val normalizedPath = PathUtils.normalizePath(filePath)
            val subscriber = object : com.rostrum.core.event.EventSubscriber<FileContentUpdateEvent> {
                override suspend fun onEvent(event: FileContentUpdateEvent) {
                    if (PathUtils.pathsEqual(event.filePath, normalizedPath)) {
                        Log.d(TAG, "Received FileContentUpdateEvent for editor: $normalizedPath, content length: ${event.content.length}")
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            // 只有当不是用户手动修改时才更新内容
                            if (!isModified || isReceivingStreamContent) {
                                isReceivingStreamContent = true
                                editorValue = TextFieldValue(
                                    text = event.content,
                                    selection = TextRange(event.content.length)
                                )
                                // 不设置 isModified = true，因为这是外部流式写入
                                
                                // 流式模式下自动滚动到底部
                                if (isStreamingMode) {
                                    scope.launch {
                                        // 等待一帧让内容更新完成
                                        delay(16)
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            eventBus.subscribe(FileContentUpdateEvent::class, subscriber, priority = 5)
            Log.d(TAG, "Editor subscribed to FileContentUpdateEvent for: $normalizedPath")
            
            onDispose {
                eventBus.unsubscribe(FileContentUpdateEvent::class, subscriber)
                Log.d(TAG, "Editor unsubscribed from FileContentUpdateEvent for: $normalizedPath")
            }
        }
    }
    
    // 加载文件内容
    LaunchedEffect(filePath, isStreamingMode) {
        if (filePath != null) {
            // 流式模式下跳过磁盘读取，等待事件更新内容
            if (isStreamingMode) {
                Log.d(TAG, "Streaming mode: skipping disk read for $filePath")
                editorValue = TextFieldValue("")
                isLoading = false
                error = null
                isModified = false
                return@LaunchedEffect
            }
            
            isLoading = true
            error = null
            isModified = false
            
            try {
                // 检查是否为远程文件系统
                val isRemote = ActiveFileSystemManager.isUsingRemote()
                
                if (isRemote) {
                    // 远程文件：通过 ActiveFileSystemManager 读取
                    val fs = ActiveFileSystemManager.getActiveFileSystem()
                    val result = withContext(Dispatchers.IO) {
                        fs.readFile(filePath)
                    }
                    
                    if (result.isSuccess) {
                        val bytes = result.getOrThrow()
                        if (bytes.size > 1024 * 1024) { // > 1MB
                            error = "文件过大，建议使用 Hex 查看"
                        } else {
                            // 检查是否为二进制文件
                            val isBinary = bytes.take(512).any { it == 0.toByte() }
                            if (isBinary) {
                                error = "检测到二进制文件，请使用 Hex 查看"
                            } else {
                                val text = String(bytes, Charsets.UTF_8)
                                editorValue = TextFieldValue(text, TextRange(text.length))
                            }
                        }
                    } else {
                        error = "读取失败: ${result.exceptionOrNull()?.message ?: "未知错误"}"
                    }
                } else {
                    // 本地文件：直接读取
                    val file = File(filePath)
                    if (file.exists() && file.isFile) {
                        if (file.length() > 1024 * 1024) { // > 1MB
                             error = "文件过大，建议使用 Hex 查看"
                        } else {
                            // 检查是否为二进制文件 (读取前 512 字节检查是否有空字符)
                            val isBinary = withContext(Dispatchers.IO) {
                                 try {
                                     file.inputStream().use { input ->
                                         val buffer = ByteArray(512)
                                         val read = input.read(buffer)
                                         if (read <= 0) false
                                         else (0 until read).any { buffer[it] == 0.toByte() }
                                     }
                                 } catch (e: Exception) {
                                     false
                                 }
                            }

                            if (isBinary) {
                                error = "检测到二进制文件，请使用 Hex 查看"
                            } else {
                                val text = withContext(Dispatchers.IO) {
                                    file.readText(Charsets.UTF_8)
                                }
                                editorValue = TextFieldValue(text, TextRange(text.length))
                            }
                        }
                    } else {
                        error = "文件不存在或无法打开"
                    }
                }
            } catch (e: Exception) {
                error = "读取失败: ${e.message}"
            } finally {
                isLoading = false
            }
        } else {
            editorValue = TextFieldValue("")
            error = "未选择文件"
        }
    }
    
    // 实时保存逻辑（debounce）
    LaunchedEffect(content, isModified, enableRealtimeSave) {
        if (enableRealtimeSave && isModified && filePath != null && error == null) {
            // 取消之前的保存任务
            saveJob?.cancel()
            
            // 创建新的延迟保存任务
            saveJob = scope.launch {
                delay(saveDelayMs)
                
                try {
                    saveStatus = SaveStatus.SAVING
                    Log.d(TAG, "Realtime saving file: $filePath")
                    
                    // 执行保存（支持本地和远程文件）
                    val saveResult = withContext(Dispatchers.IO) {
                        val fs = ActiveFileSystemManager.getActiveFileSystem()
                        fs.writeTextFile(filePath, content)
                    }
                    
                    if (saveResult.isFailure) {
                        throw saveResult.exceptionOrNull() ?: Exception("保存失败")
                    }
                    onSave(filePath, content)
                    
                    // 发布文件修改事件（使用规范化路径确保事件匹配）
                    val normalizedPath = PathUtils.normalizePath(filePath)
                    Log.d(TAG, "Publishing FileModifiedEvent for: $normalizedPath (original: $filePath)")
                    eventBus.publish(FileModifiedEvent(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        filePath = normalizedPath
                    ))
                    
                    isModified = false
                    saveStatus = SaveStatus.SAVED
                    Log.d(TAG, "Realtime save completed: $normalizedPath")
                    
                    // 1秒后恢复到空闲状态
                    delay(1000)
                    if (saveStatus == SaveStatus.SAVED) {
                        saveStatus = SaveStatus.IDLE
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Realtime save failed: ${e.message}", e)
                    saveStatus = SaveStatus.ERROR
                    delay(2000)
                    saveStatus = SaveStatus.IDLE
                }
            }
        }
    }
    
    // 实时预览逻辑（不保存文件，只发布内容更新事件）
    LaunchedEffect(content, enableRealtimePreview) {
        if (enableRealtimePreview && filePath != null && error == null) {
            // 取消之前的预览任务
            previewJob?.cancel()
            
            // 创建新的延迟预览任务
            previewJob = scope.launch {
                delay(realtimePreviewDebounceMs)
                
                try {
                    val normalizedPath = PathUtils.normalizePath(filePath)
                    Log.d(TAG, "Publishing FileContentUpdateEvent for: $normalizedPath")
                    eventBus.publish(FileContentUpdateEvent(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        filePath = normalizedPath,
                        content = content,
                        isModified = isModified
                    ))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to publish FileContentUpdateEvent: ${e.message}", e)
                }
            }
        }
    }

    // 语言诊断（防抖）
    LaunchedEffect(content, filePath, error) {
        diagnosticsJob?.cancel()
        if (filePath == null || error != null) {
            diagnostics = emptyList()
            return@LaunchedEffect
        }

        diagnosticsJob = scope.launch {
            delay(300)
            diagnostics = runCatching {
                EditorLanguageService.diagnose(filePath, content)
            }.getOrElse {
                Log.w(TAG, "Language diagnose failed: ${it.message}")
                emptyList()
            }
        }
    }
    
    // 清理协程
    DisposableEffect(filePath) {
        onDispose {
            saveJob?.cancel()
            previewJob?.cancel()
            diagnosticsJob?.cancel()
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        if (isLoading) {
            Text("加载中...", modifier = Modifier.align(Alignment.Center))
        } else if (error != null) {
             Text(
                 text = error!!,
                 color = MaterialTheme.colorScheme.error,
                 modifier = Modifier.align(Alignment.Center)
             )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // 使用 Surface + BasicTextField 替代 OutlinedTextField，以便控制滚动
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    BasicTextField(
                        value = editorValue,
                        onValueChange = {
                            editorValue = it
                            isModified = true
                            // 用户开始手动编辑，停止接收流式内容
                            isReceivingStreamContent = false
                            completions = emptyList()
                            languageHint = null
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }

                if (diagnostics.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "代码诊断 (${diagnostics.size})",
                                style = MaterialTheme.typography.labelLarge
                            )
                            diagnostics.take(5).forEach { item ->
                                val color = when (item.severity) {
                                    com.rostrum.core.plugin.providers.DiagnosticSeverity.ERROR ->
                                        MaterialTheme.colorScheme.error
                                    com.rostrum.core.plugin.providers.DiagnosticSeverity.WARNING ->
                                        Color(0xFFB26A00)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Text(
                                    text = "[${item.severity}] ${item.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = color
                                )
                            }
                        }
                    }
                }

                val isLanguageFile = filePath?.let {
                    it.endsWith(".java", ignoreCase = true) || it.endsWith(".kt", ignoreCase = true)
                } ?: false
                val languageFilePath = if (isLanguageFile) filePath else null

                if (languageFilePath != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            languageBusy = true
                                            val cursor = editorValue.selection.start.coerceIn(0, editorValue.text.length)
                                            completions = runCatching {
                                                FileEditorLanguageApi.requestCompletions(
                                                    filePath = languageFilePath,
                                                    code = editorValue.text,
                                                    cursorPosition = cursor
                                                )
                                            }.onFailure {
                                                Log.w(TAG, "Completion request failed: ${it.message}")
                                            }.getOrDefault(emptyList())
                                            languageHint = if (completions.isEmpty()) {
                                                "未找到可用补全"
                                            } else {
                                                "补全候选 ${completions.size} 项"
                                            }
                                            languageBusy = false
                                        }
                                    },
                                    enabled = !languageBusy
                                ) {
                                    Text("请求补全")
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            languageBusy = true
                                            val cursor = editorValue.selection.start.coerceIn(0, editorValue.text.length)
                                            val definition = FileEditorLanguageApi.requestDefinition(
                                                filePath = languageFilePath,
                                                code = editorValue.text,
                                                cursorPosition = cursor
                                            ).getOrNull()
                                            languageHint = if (definition == null) {
                                                "未找到定义"
                                            } else {
                                                "定义: ${definition.filePath}:${definition.range.startLine}:${definition.range.startColumn}"
                                            }
                                            if (definition != null) {
                                                onDefinitionResolved?.invoke(definition)
                                            }
                                            languageBusy = false
                                        }
                                    },
                                    enabled = !languageBusy
                                ) {
                                    Text("跳转定义")
                                }
                            }

                            if (!languageHint.isNullOrBlank()) {
                                Text(
                                    text = languageHint!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (completions.isNotEmpty()) {
                                val cursor = editorValue.selection.start.coerceIn(0, editorValue.text.length)
                                val prefixStart = run {
                                    var idx = cursor - 1
                                    while (idx >= 0 && (editorValue.text[idx].isLetterOrDigit() || editorValue.text[idx] == '_')) {
                                        idx--
                                    }
                                    idx + 1
                                }

                                completions.take(8).forEach { item ->
                                    TextButton(
                                        onClick = {
                                            val insertion = item.insertText?.takeIf { it.isNotBlank() } ?: item.label
                                            val before = editorValue.text.substring(0, prefixStart)
                                            val after = editorValue.text.substring(cursor)
                                            val merged = before + insertion + after
                                            val nextCursor = (prefixStart + insertion.length).coerceIn(0, merged.length)
                                            editorValue = TextFieldValue(
                                                text = merged,
                                                selection = TextRange(nextCursor)
                                            )
                                            isModified = true
                                            completions = emptyList()
                                            languageHint = "已插入补全: ${item.label}"
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(item.label, style = MaterialTheme.typography.bodySmall)
                                            if (!item.detail.isNullOrBlank()) {
                                                Text(
                                                    item.detail ?: "",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 保存状态指示器
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        when (saveStatus) {
                            SaveStatus.SAVING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "保存中...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            SaveStatus.SAVED -> {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFF4CAF50)
                                )
                                Text(
                                    text = "已保存",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                            SaveStatus.ERROR -> {
                                Text(
                                    text = "保存失败",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            SaveStatus.IDLE -> {
                                if (enableRealtimeSave) {
                                    Icon(
                                        Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "实时保存已启用",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 关闭按钮（仅在 onClose 不为 null 时显示）
                        if (onClose != null) {
                            OutlinedButton(
                                onClick = onClose
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("关闭")
                            }
                        }
                        
                        // 手动保存按钮（支持本地和SSH远程文件）
                        Button(
                            onClick = {
                                if (filePath != null) {
                                    scope.launch {
                                        try {
                                            saveStatus = SaveStatus.SAVING
                                            Log.d(TAG, "Manual saving file: $filePath")
                                            
                                            // 执行保存（支持本地和远程文件）
                                                val saveResult = withContext(Dispatchers.IO) {
                                                    val fs = ActiveFileSystemManager.getActiveFileSystem()
                                                    fs.writeTextFile(filePath, content)
                                            }
                                            
                                            if (saveResult.isFailure) {
                                                throw saveResult.exceptionOrNull() ?: Exception("保存失败")
                                            }
                                            onSave(filePath, content)
                                            
                                            // 发布文件修改事件（使用规范化路径确保事件匹配）
                                            val normalizedPath = PathUtils.normalizePath(filePath)
                                            Log.d(TAG, "Publishing FileModifiedEvent for: $normalizedPath (original: $filePath)")
                                            eventBus.publish(FileModifiedEvent(
                                                id = UUID.randomUUID().toString(),
                                                timestamp = System.currentTimeMillis(),
                                                filePath = normalizedPath
                                            ))
                                            
                                            isModified = false
                                            saveStatus = SaveStatus.SAVED
                                            Log.d(TAG, "Manual save completed: $normalizedPath")
                                            delay(1000)
                                            if (saveStatus == SaveStatus.SAVED) {
                                                saveStatus = SaveStatus.IDLE
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Manual save failed: ${e.message}", e)
                                            saveStatus = SaveStatus.ERROR
                                        }
                                    }
                                }
                            },
                            enabled = isModified && saveStatus != SaveStatus.SAVING
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("保存")
                        }
                    }
                }
            }
        }
    }
}

/**
 * FileEditor 对外语言能力入口（V1）。
 */
object FileEditorLanguageApi {
    suspend fun requestCompletions(
        filePath: String?,
        code: String,
        cursorPosition: Int
    ): List<CompletionItem> {
        val prefix = extractPrefix(code, cursorPosition)
        return EditorLanguageService.getCompletions(
            filePath = filePath,
            cursor = cursorPosition,
            prefix = prefix,
            contextWindow = code
        )
    }

    suspend fun requestDefinition(
        filePath: String?,
        code: String,
        cursorPosition: Int
    ): Result<DefinitionLocation?> {
        return EditorLanguageService.requestDefinition(filePath, code, cursorPosition)
    }

    private fun extractPrefix(code: String, cursor: Int): String {
        if (cursor <= 0 || cursor > code.length) return ""
        var start = cursor - 1
        while (start >= 0 && (code[start].isLetterOrDigit() || code[start] == '_')) {
            start--
        }
        return code.substring(start + 1, cursor)
    }
}
