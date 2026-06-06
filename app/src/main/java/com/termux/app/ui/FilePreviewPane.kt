package com.termux.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

private val PreviewBackground = Color(0xFF334155)
private val TopBarBackground = Color(0xFF1E293B)
private val AccentIndigo = Color(0xFF6366F1)
private val AccentEmerald = Color(0xFF10B981)
private val TextMuted = Color.White.copy(alpha = 0.6f)

/**
 * Enhanced file preview pane with remote file support, PDF thumbnails,
 * and binary file handling.
 */
@Composable
fun FilePreviewPane(
    filePath: String?,
    fileContent: String?,
    imageBytes: ByteArray? = null,
    isImage: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    isRemote: Boolean = false,
    remoteHost: String? = null,
    remoteUser: String? = null,
    isBinary: Boolean = false,
    onCopyContent: (() -> Unit)? = null,
    onOpenExternally: (() -> Unit)? = null,
    pdfPreviewBitmap: Bitmap? = null
) {
    val isCodeFile = filePath?.let { path ->
        val ext = path.substringAfterLast('.', "").lowercase()
        ext in codeExtensions
    } ?: false

    val isPdf = filePath?.lowercase()?.endsWith(".pdf") == true

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PreviewBackground)
    ) {
        PreviewTopBar(
            filePath = filePath,
            onClose = onClose,
            onCopyContent = onCopyContent,
            hasTextContent = fileContent != null && !isBinary
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                filePath == null -> EmptyPreviewState()
                pdfPreviewBitmap != null -> PdfPreviewContent(bitmap = pdfPreviewBitmap)
                isImage -> ImagePreviewContent(filePath = filePath, imageBytes = imageBytes)
                isBinary -> BinaryFileInfo(
                    filePath = filePath,
                    fileBytes = imageBytes,
                    isPdf = isPdf,
                    isRemote = isRemote,
                    remoteHost = remoteHost,
                    remoteUser = remoteUser,
                    onOpenExternally = onOpenExternally
                )
                isCodeFile && fileContent != null -> CodePreviewContent(content = fileContent, onCopyContent = onCopyContent)
                fileContent != null -> TextPreviewContent(content = fileContent, onCopyContent = onCopyContent)
                else -> UnsupportedPreviewState(filePath = filePath)
            }
        }
    }
}

@Composable
private fun PreviewTopBar(
    filePath: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onCopyContent: (() -> Unit)? = null,
    hasTextContent: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(TopBarBackground)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = filePath?.substringAfterLast('/') ?: "No file selected",
            modifier = Modifier.weight(1f),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (hasTextContent && onCopyContent != null) {
            IconButton(onClick = onCopyContent, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy content",
                    tint = AccentIndigo,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close preview",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun TextPreviewContent(
    content: String,
    modifier: Modifier = Modifier,
    onCopyContent: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {
        if (onCopyContent != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onCopyContent,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2D2D)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, tint = Color(0xFFD4D4D4), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("复制内容", color = Color(0xFFD4D4D4), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        SelectionContainer {
            Text(
                text = content,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ImagePreviewContent(
    filePath: String,
    imageBytes: ByteArray? = null,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(filePath, imageBytes) {
        if (imageBytes != null) {
            runCatching { BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) }.getOrNull()
        } else {
            runCatching { BitmapFactory.decodeFile(filePath) }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Image preview",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Photo,
                    contentDescription = null,
                    tint = AccentIndigo,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Could not load image",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PdfPreviewContent(
    bitmap: Bitmap,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D44)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "PDF first page preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "PDF 预览 (第一页)",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun EmptyPreviewState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = AccentIndigo,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No file selected",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select a file from the browser to preview it here",
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun UnsupportedPreviewState(
    filePath: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            tint = AccentEmerald,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Unsupported file type",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = filePath,
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Binary file info card for remote binary files that can't be rendered as text.
 */
@Composable
fun BinaryFileInfo(
    filePath: String,
    fileBytes: ByteArray? = null,
    isPdf: Boolean = false,
    isRemote: Boolean = false,
    remoteHost: String? = null,
    remoteUser: String? = null,
    onOpenExternally: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fileName = filePath.substringAfterLast('/')
    val fileSize = fileBytes?.size?.toLong() ?: 0L
    val ext = filePath.substringAfterLast('.', "").lowercase()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Info,
                    contentDescription = null,
                    tint = AccentIndigo,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (isPdf) "PDF 文件" else "二进制文件",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = fileName,
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "类型: .$ext",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(4.dp))

                Text(
                    text = "大小: ${formatFileSize(fileSize)}",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )

                if (isRemote && (remoteHost != null || remoteUser != null)) {
                    Spacer(Modifier.height(4.dp))
                    val serverInfo = buildString {
                        if (remoteUser != null) append(remoteUser)
                        if (remoteHost != null) {
                            if (remoteUser != null) append("@")
                            append(remoteHost)
                        }
                    }
                    Text(
                        text = "远程服务器: $serverInfo",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (onOpenExternally != null) {
            Button(
                onClick = {
                    Log.d("FilePreviewPane", "Open externally clicked for: $filePath")
                    onOpenExternally()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("使用其他应用打开", color = Color.White)
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "此文件类型无法直接预览，请下载后用外部应用打开",
            color = TextMuted,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Code file extensions for syntax-highlighted preview.
 */
private val codeExtensions = setOf(
    "java", "kt", "kts", "gradle", "groovy", "scala",
    "js", "jsx", "ts", "tsx", "css", "scss", "sass", "less",
    "json", "xml", "yaml", "yml", "toml",
    "c", "h", "cpp", "cc", "hpp", "go", "rs", "swift",
    "py", "rb", "php", "lua", "pl",
    "sh", "bash", "zsh", "ps1", "bat", "cmd",
    "sql", "md", "markdown"
)

/**
 * Binary file extensions that cannot be previewed as text.
 */
private val binaryExtensions = setOf(
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
    "zip", "rar", "tar", "gz", "bz2", "7z", "apk",
    "exe", "dll", "so", "bin", "dat", "class", "jar",
    "mp3", "mp4", "avi", "mkv", "mov", "flv", "wmv",
    "ogg", "wav", "flac", "aac"
)

fun isBinaryExtension(filePath: String): Boolean {
    val ext = filePath.substringAfterLast('.', "").lowercase()
    return ext in binaryExtensions
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes <= 0 -> "未知"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
        else -> "%.1f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
    }
}

/**
 * Code preview with monospace font, dark background, and basic line numbers.
 */
@Composable
private fun CodePreviewContent(
    content: String,
    modifier: Modifier = Modifier,
    onCopyContent: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val lines = remember(content) { content.lines() }
    val lineCount = lines.size
    val gutterWidth = (lineCount.toString().length * 10 + 16).dp

    Column(modifier = modifier.fillMaxSize()) {
        if (onCopyContent != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF252526))
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onCopyContent,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3C3C3C)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("复制内容", color = Color(0xFFCCCCCC), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier
                    .width(gutterWidth)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .background(Color(0xFF252526))
                    .padding(vertical = 12.dp)
            ) {
                lines.forEachIndexed { index, _ ->
                    Text(
                        text = "${index + 1}",
                        color = Color(0xFF858585),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.5.dp),
                        textAlign = TextAlign.End
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxSize()
                    .background(Color(0xFF3E3E3E))
            )

            SelectionContainer {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(12.dp)
                ) {
                    lines.forEach { line ->
                        Text(
                            text = line,
                            color = Color(0xFFD4D4D4),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.4f
                            ),
                            modifier = Modifier.padding(vertical = 0.5.dp)
                        )
                    }
                }
            }
        }
    }
}
