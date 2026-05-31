package com.rostrum.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rostrum.core.domain.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

/**
 * 文件属性对话框
 * 
 * 显示文件的详细信息，包括：
 * - 基本信息（名称、路径、类型、大小）
 * - 时间信息（修改时间、创建时间）
 * - 权限信息（读/写/执行）
 * - 校验值（MD5/SHA1）
 * - MIME类型
 */
@Composable
fun FilePropertiesDialog(
    fileItem: FileItem,
    onDismiss: () -> Unit
) {
    val file = File(fileItem.path)
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val scope = rememberCoroutineScope()
    
    // 权限状态
    var canRead by remember { mutableStateOf(file.canRead()) }
    var canWrite by remember { mutableStateOf(file.canWrite()) }
    var canExecute by remember { mutableStateOf(file.canExecute()) }
    
    // 校验值状态
    var md5Hash by remember { mutableStateOf<String?>(null) }
    var sha1Hash by remember { mutableStateOf<String?>(null) }
    var isCalculating by remember { mutableStateOf(false) }
    var showHashes by remember { mutableStateOf(false) }
    
    // MIME类型
    val mimeType = remember(fileItem) {
        getMimeType(fileItem.path)
    }
    
    // 计算校验值
    fun calculateHashes() {
        if (fileItem.isDirectory || !file.exists()) return
        
        scope.launch {
            isCalculating = true
            showHashes = true
            
            withContext(Dispatchers.IO) {
                try {
                    md5Hash = calculateMD5(file)
                    sha1Hash = calculateSHA1(file)
                } catch (e: Exception) {
                    md5Hash = "计算失败"
                    sha1Hash = "计算失败"
                }
            }
            
            isCalculating = false
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文件属性") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 基本信息
                PropertySection(title = "基本信息") {
                    PropertyRow("名称", fileItem.name)
                    PropertyRow("路径", fileItem.path)
                    PropertyRow("类型", if (fileItem.isDirectory) "文件夹" else getFileTypeDescription(fileItem.extension))
                    
                    if (!fileItem.isDirectory) {
                        PropertyRow("大小", fileItem.getFormattedSize())
                        PropertyRow("扩展名", fileItem.extension.takeIf { it.isNotEmpty() } ?: "无")
                        PropertyRow("MIME类型", mimeType)
                    }
                }
                
                // 时间信息
                PropertySection(title = "时间信息") {
                    PropertyRow("修改时间", dateFormat.format(Date(fileItem.lastModified)))
                    if (file.exists()) {
                        val createdTime = getCreatedTime(file)
                        if (createdTime > 0) {
                            PropertyRow("创建时间", dateFormat.format(Date(createdTime)))
                        }
                    }
                }
                
                // 权限信息
                if (file.exists()) {
                    PropertySection(title = "权限设置") {
                        PermissionRow(
                            label = "可读",
                            checked = canRead,
                            onCheckedChange = { 
                                if (file.setReadable(it)) canRead = it
                            }
                        )
                        
                        PermissionRow(
                            label = "可写",
                            checked = canWrite,
                            onCheckedChange = { 
                                if (file.setWritable(it)) canWrite = it
                            }
                        )
                        
                        PermissionRow(
                            label = "可执行",
                            checked = canExecute,
                            onCheckedChange = { 
                                if (file.setExecutable(it)) canExecute = it 
                            }
                        )
                        
                        Text(
                            text = "注: 部分权限可能需要ROOT或特定文件系统支持",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 校验值
                if (!fileItem.isDirectory && file.exists()) {
                    PropertySection(title = "文件校验") {
                        if (!showHashes) {
                            Button(
                                onClick = { calculateHashes() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("计算 MD5/SHA1")
                            }
                        } else {
                            if (isCalculating) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("计算中...", style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                md5Hash?.let {
                                    PropertyRow("MD5", it, isMonospace = true)
                                }
                                sha1Hash?.let {
                                    PropertyRow("SHA1", it, isMonospace = true)
                                }
                            }
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

@Composable
private fun PropertySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Column(
            modifier = Modifier.padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
        Divider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun PropertyRow(
    label: String,
    value: String,
    isMonospace: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (isMonospace) FontFamily.Monospace else null,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun PermissionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 计算文件MD5
 */
private fun calculateMD5(file: File): String {
    val digest = MessageDigest.getInstance("MD5")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } > 0) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * 计算文件SHA1
 */
private fun calculateSHA1(file: File): String {
    val digest = MessageDigest.getInstance("SHA-1")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } > 0) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * 获取文件创建时间（Android API 26+）
 */
private fun getCreatedTime(file: File): Long {
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.nio.file.Files.readAttributes(
                file.toPath(),
                java.nio.file.attribute.BasicFileAttributes::class.java
            ).creationTime().toMillis()
        } else {
            0L
        }
    } catch (e: Exception) {
        0L
    }
}

/**
 * 获取MIME类型
 */
private fun getMimeType(path: String): String {
    val extension = path.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "txt" -> "text/plain"
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js" -> "application/javascript"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "webp" -> "image/webp"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        "avi" -> "video/x-msvideo"
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "zip" -> "application/zip"
        "rar" -> "application/x-rar-compressed"
        "7z" -> "application/x-7z-compressed"
        "tar" -> "application/x-tar"
        "gz" -> "application/gzip"
        "apk" -> "application/vnd.android.package-archive"
        "exe" -> "application/x-msdownload"
        else -> android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }
}

/**
 * 获取文件类型描述
 */
private fun getFileTypeDescription(extension: String): String {
    return when (extension.lowercase()) {
        "txt" -> "文本文件"
        "html", "htm" -> "HTML 文档"
        "css" -> "CSS 样式表"
        "js" -> "JavaScript 文件"
        "json" -> "JSON 文件"
        "xml" -> "XML 文件"
        "md" -> "Markdown 文档"
        "jpg", "jpeg" -> "JPEG 图像"
        "png" -> "PNG 图像"
        "gif" -> "GIF 图像"
        "bmp" -> "BMP 图像"
        "webp" -> "WebP 图像"
        "mp3" -> "MP3 音频"
        "mp4" -> "MP4 视频"
        "avi" -> "AVI 视频"
        "pdf" -> "PDF 文档"
        "doc", "docx" -> "Word 文档"
        "xls", "xlsx" -> "Excel 表格"
        "ppt", "pptx" -> "PowerPoint 演示文稿"
        "zip" -> "ZIP 压缩包"
        "rar" -> "RAR 压缩包"
        "7z" -> "7Z 压缩包"
        "apk" -> "Android 安装包"
        "exe" -> "Windows 可执行文件"
        else -> if (extension.isEmpty()) "文件" else "${extension.uppercase()} 文件"
    }
}
