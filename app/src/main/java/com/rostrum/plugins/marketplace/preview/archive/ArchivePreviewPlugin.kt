package com.rostrum.plugins.marketplace.preview.archive

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rostrum.core.plugin.Plugin
import com.rostrum.core.plugin.PluginCapability
import com.rostrum.core.plugin.PluginCategory
import com.rostrum.core.plugin.PluginContext
import com.rostrum.core.plugin.models.FileInfo
import com.rostrum.core.plugin.providers.FilePreviewPlugin
import com.rostrum.core.plugin.providers.PreviewMetadata
import com.rostrum.core.plugin.providers.PreviewResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 压缩文件预览插件
 * 
 * 支持: zip, jar, apk
 * 功能: 列出压缩包内容，支持预览压缩包内的文件（解压到临时目录）
 * 
 * 退出时自动清理临时文件
 */
class ArchivePreviewPlugin : Plugin, FilePreviewPlugin {
    
    override val id = "com.rostrum.plugin.preview.archive"
    override val name = "Archive Preview"
    override val version = "1.0.0"
    override val author = "OmniMaster Team"
    override val description = "压缩文件预览插件，支持ZIP/JAR/APK"
    override val category = PluginCategory.PREVIEW
    override val dependencies: List<String> = emptyList()
    
    override val supportedMimeTypes = listOf(
        "application/zip",
        "application/java-archive",
        "application/vnd.android.package-archive",
        "application/x-zip-compressed"
    )
    
    override val supportedExtensions = listOf(
        ".zip", ".jar", ".apk"
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
            previewComponent = { ArchivePreviewContent(fileObj) },
            metadata = PreviewMetadata(
                title = file.name,
                description = "压缩文件预览",
                canEdit = false
            )
        )
    }
    
    override fun getPreviewPriority(file: FileInfo) = 60
}

/**
 * 压缩文件条目信息
 */
data class ArchiveEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long,
    val lastModified: Long
)

/**
 * 压缩文件预览内容组件
 */
@Composable
private fun ArchivePreviewContent(file: File) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<ArchiveEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var totalFiles by remember { mutableIntStateOf(0) }
    var totalSize by remember { mutableLongStateOf(0L) }
    var selectedEntry by remember { mutableStateOf<ArchiveEntry?>(null) }
    var extractedFilePath by remember { mutableStateOf<String?>(null) }
    var isExtracting by remember { mutableStateOf(false) }
    
    // 加载压缩文件内容列表
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                val entryList = mutableListOf<ArchiveEntry>()
                var fileCount = 0
                var totalSizeAccum = 0L
                
                ZipInputStream(FileInputStream(file)).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        entryList.add(
                            ArchiveEntry(
                                name = entry.name.substringAfterLast('/').ifEmpty { entry.name },
                                path = entry.name,
                                isDirectory = entry.isDirectory,
                                size = entry.size.coerceAtLeast(0),
                                compressedSize = entry.compressedSize.coerceAtLeast(0),
                                lastModified = entry.time
                            )
                        )
                        if (!entry.isDirectory) {
                            fileCount++
                            totalSizeAccum += entry.size.coerceAtLeast(0)
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
                
                // 按目录优先、名称排序
                entryList.sortWith(compareBy({ !it.isDirectory }, { it.path.lowercase() }))
                
                entries = entryList
                totalFiles = fileCount
                totalSize = totalSizeAccum
                isLoading = false
            } catch (e: Exception) {
                android.util.Log.e("ArchivePreview", "读取压缩文件失败", e)
                error = "读取失败: ${e.message ?: e.javaClass.simpleName}"
                isLoading = false
            }
        }
    }
    
    // 退出时清理临时文件
    DisposableEffect(file) {
        onDispose {
            // 清理临时目录
            val tempDir = File(context.cacheDir, "archive_preview")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
                android.util.Log.d("ArchivePreview", "已清理临时文件目录")
            }
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部信息栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderZip,
                        contentDescription = null,
                        tint = Color(0xFF8D6E63),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                
                if (!isLoading && error == null) {
                    Text(
                        text = "$totalFiles 文件 | ${formatFileSize(totalSize)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = Color(0xFF6366F1)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "正在读取压缩文件...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            entries.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "压缩包为空",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                // 文件列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(entries) { entry ->
                        ArchiveEntryItem(
                            entry = entry,
                            archiveFile = file,
                            context = context,
                            onExtract = { extractPath ->
                                extractedFilePath = extractPath
                            }
                        )
                    }
                }
            }
        }
    }
    
    // 提取文件预览对话框
    if (extractedFilePath != null) {
        ExtractedFilePreviewDialog(
            extractedPath = extractedFilePath!!,
            onDismiss = { extractedFilePath = null }
        )
    }
}

/**
 * 压缩文件条目项
 */
@Composable
private fun ArchiveEntryItem(
    entry: ArchiveEntry,
    archiveFile: File,
    context: Context,
    onExtract: (String) -> Unit
) {
    var isExtracting by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !entry.isDirectory && !isExtracting) {
                // 点击提取并预览文件
                if (!entry.isDirectory) {
                    isExtracting = true
                    // 在后台提取文件
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val extractedPath = extractFile(context, archiveFile, entry)
                            withContext(Dispatchers.Main) {
                                isExtracting = false
                                if (extractedPath != null) {
                                    onExtract(extractedPath)
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isExtracting = false
                            }
                        }
                    }
                }
            },
        color = if (entry.isDirectory) 
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        else 
            MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Icon(
                imageVector = if (entry.isDirectory) Icons.Default.Folder else getFileIcon(entry.name),
                contentDescription = null,
                tint = if (entry.isDirectory) Color(0xFF5C6BC0) else getFileIconColor(entry.name),
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 文件信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!entry.isDirectory) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = formatFileSize(entry.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (entry.compressedSize > 0 && entry.size > 0) {
                            val ratio = (1 - entry.compressedSize.toFloat() / entry.size) * 100
                            Text(
                                text = "压缩 ${ratio.toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }
            }
            
            // 提取状态/预览按钮
            if (!entry.isDirectory) {
                if (isExtracting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF6366F1)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "预览",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * 提取文件到临时目录
 */
private fun extractFile(context: Context, archiveFile: File, entry: ArchiveEntry): String? {
    try {
        val tempDir = File(context.cacheDir, "archive_preview")
        tempDir.mkdirs()
        
        val outputFile = File(tempDir, entry.name)
        
        ZipInputStream(FileInputStream(archiveFile)).use { zipIn ->
            var zipEntry: ZipEntry? = zipIn.nextEntry
            while (zipEntry != null) {
                if (zipEntry.name == entry.path) {
                    FileOutputStream(outputFile).use { out ->
                        zipIn.copyTo(out)
                    }
                    return outputFile.absolutePath
                }
                zipIn.closeEntry()
                zipEntry = zipIn.nextEntry
            }
        }
        
        return null
    } catch (e: Exception) {
        android.util.Log.e("ArchivePreview", "提取文件失败: ${entry.path}", e)
        return null
    }
}

/**
 * 提取文件预览对话框
 */
@Composable
private fun ExtractedFilePreviewDialog(
    extractedPath: String,
    onDismiss: () -> Unit
) {
    val file = File(extractedPath)
    var content by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // 加载文件内容
    LaunchedEffect(extractedPath) {
        withContext(Dispatchers.IO) {
            try {
                // 检查是否为文本文件
                val isTextFile = file.extension.lowercase() in listOf(
                    "txt", "md", "log", "json", "xml", "yaml", "yml", "toml",
                    "kt", "java", "py", "js", "ts", "html", "css", "scss",
                    "c", "cpp", "h", "hpp", "swift", "go", "rs", "rb", "php",
                    "sh", "bash", "gradle", "properties", "ini", "cfg", "conf"
                )
                
                if (isTextFile && file.length() < 1024 * 1024) { // 1MB 限制
                    content = file.readText()
                } else {
                    content = "文件类型不支持预览或文件过大\n\n路径: ${file.absolutePath}\n大小: ${formatFileSize(file.length())}"
                }
                isLoading = false
            } catch (e: Exception) {
                error = "读取失败: ${e.message}"
                isLoading = false
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            ) 
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                    error != null -> {
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(
                                text = content ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 格式化文件大小
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

/**
 * 获取文件图标
 */
private fun getFileIcon(filename: String): androidx.compose.ui.graphics.vector.ImageVector {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> Icons.Default.Image
        "mp3", "wav", "ogg", "m4a", "flac", "aac" -> Icons.Default.AudioFile
        "mp4", "mkv", "avi", "webm", "mov" -> Icons.Default.VideoFile
        "pdf" -> Icons.Default.PictureAsPdf
        "doc", "docx" -> Icons.Default.Description
        "xls", "xlsx" -> Icons.Default.TableChart
        "ppt", "pptx" -> Icons.Default.Slideshow
        "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip
        "apk" -> Icons.Default.Android
        "kt", "java", "py", "js", "ts", "c", "cpp", "h", "go", "rs" -> Icons.Default.Code
        "html", "htm", "css", "scss" -> Icons.Default.Language
        "json", "xml", "yaml", "yml" -> Icons.Default.DataObject
        "txt", "md", "log" -> Icons.Default.Article
        else -> Icons.Default.InsertDriveFile
    }
}

/**
 * 获取文件图标颜色
 */
private fun getFileIconColor(filename: String): Color {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> Color(0xFF42A5F5)
        "mp3", "wav", "ogg", "m4a", "flac", "aac" -> Color(0xFFEC407A)
        "mp4", "mkv", "avi", "webm", "mov" -> Color(0xFFAB47BC)
        "pdf" -> Color(0xFFE53935)
        "doc", "docx" -> Color(0xFF1565C0)
        "xls", "xlsx" -> Color(0xFF2E7D32)
        "ppt", "pptx" -> Color(0xFFE65100)
        "zip", "rar", "7z", "tar", "gz" -> Color(0xFF8D6E63)
        "apk" -> Color(0xFF4CAF50)
        "kt", "java", "py", "js", "ts", "c", "cpp", "h", "go", "rs" -> Color(0xFF7E57C2)
        "html", "htm", "css", "scss" -> Color(0xFFFF7043)
        "json", "xml", "yaml", "yml" -> Color(0xFF26A69A)
        "txt", "md", "log" -> Color(0xFF546E7A)
        else -> Color(0xFF78909C)
    }
}
