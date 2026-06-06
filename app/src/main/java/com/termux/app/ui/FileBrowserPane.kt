package com.termux.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termux.app.data.ActiveFileSystemManager
import com.termux.app.data.FileItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Rostrum color scheme
private val PanelBg = Color(0xFF0F172A)
private val PathBarBg = Color(0xFF111827)
private val SurfaceBg = Color(0xFF1E293B)
private val MutedText = Color(0xFF94A3B8)
private val BrightText = Color(0xFFE5E7EB)
private val AccentIndigo = Color(0xFF6366F1)
private val AccentGreen = Color(0xFF10B981)

/**
 * Rostrum-style file browser pane with breadcrumb path, quick access, and file list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileBrowserPane(
    currentPath: String,
    fileList: List<FileItem>,
    isLoading: Boolean,
    onNavigateUp: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    selectedFiles: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    onFileLongClick: (FileItem) -> Unit = {}
) {
    val isRemote by ActiveFileSystemManager.isRemote.collectAsState()
    val rootPath by ActiveFileSystemManager.rootPath.collectAsState()

    Column(modifier = modifier.fillMaxSize().background(PanelBg)) {
        // Path navigation bar
        PathBar(
            currentPath = currentPath,
            isRemote = isRemote,
            isRoot = currentPath == rootPath || currentPath == "/",
            onNavigateUp = onNavigateUp,
            onNavigateHome = { /* navigate to rootPath */ },
            onRefresh = onRefresh
        )

        // File list
        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading && fileList.isEmpty() -> LoadingState()
                fileList.isEmpty() -> EmptyState()
                else -> FileList(
                    fileList = fileList,
                    onFileClick = onFileClick,
                    selectedFiles = selectedFiles,
                    isSelectionMode = isSelectionMode,
                    onFileLongClick = onFileLongClick
                )
            }
        }
    }
}

@Composable
private fun PathBar(
    currentPath: String,
    isRemote: Boolean,
    isRoot: Boolean,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onRefresh: () -> Unit
) {
    var showQuickAccess by remember { mutableStateOf(false) }
    val quickPaths = remember {
        listOf(
            "根目录" to "/",
            "下载" to "/sdcard/Download",
            "文档" to "/sdcard/Documents",
            "图片" to "/sdcard/Pictures"
        )
    }

    Column(modifier = Modifier.fillMaxWidth().background(PathBarBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back/Up button
            IconButton(onClick = onNavigateUp, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回上级",
                    tint = if (!isRoot) MutedText else MutedText.copy(alpha = 0.38f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Home button
            IconButton(
                onClick = onNavigateHome,
                modifier = Modifier.size(28.dp),
                enabled = !isRoot
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "根目录",
                    tint = if (!isRoot) MutedText else MutedText.copy(alpha = 0.38f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Quick access button
            Box {
                IconButton(onClick = { showQuickAccess = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Bookmark, contentDescription = "快捷访问", tint = MutedText, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showQuickAccess, onDismissRequest = { showQuickAccess = false }) {
                    quickPaths.forEach { (name, path) ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, null, tint = Color(0xFFFFCC80), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(name)
                                }
                            },
                            onClick = { showQuickAccess = false /* TODO: navigate to path */ }
                        )
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            // Remote indicator
            if (isRemote) {
                Row(
                    modifier = Modifier
                        .background(AccentIndigo.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Computer, null, tint = AccentIndigo, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("SSH", color = AccentIndigo, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(4.dp))
            }

            // Path text (scrollable)
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentPath,
                    color = BrightText,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Refresh
            IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = MutedText, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileList(
    fileList: List<FileItem>,
    onFileClick: (FileItem) -> Unit,
    selectedFiles: Set<String>,
    isSelectionMode: Boolean,
    onFileLongClick: (FileItem) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = fileList, key = { it.path }) { item ->
            FileItemRow(
                item = item,
                onClick = { onFileClick(item) },
                isSelected = selectedFiles.contains(item.path),
                isSelectionMode = isSelectionMode,
                onLongClick = { onFileLongClick(item) }
            )
            HorizontalDivider(color = SurfaceBg, thickness = 0.5.dp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileItemRow(
    item: FileItem,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onLongClick: () -> Unit = {}
) {
    val icon = getFileIcon(item)
    val iconColor = getFileIconColor(item)
    val bgColor = if (isSelectionMode && isSelected) AccentIndigo.copy(alpha = 0.12f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onLongClick()
                    } else {
                        onClick()
                    }
                },
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = if (isSelected) "已选择" else "未选择",
                tint = if (isSelected) AccentIndigo else MutedText,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = BrightText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!item.isDirectory) {
                    Text(formatSize(item.size), color = MutedText, style = MaterialTheme.typography.labelSmall)
                }
                Text(formatDate(item.lastModified), color = MutedText, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AccentIndigo, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(12.dp))
            Text("加载中...", color = MutedText)
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Warning, null, tint = MutedText, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("文件夹为空", color = MutedText.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * File icon mapping matching Rostrum's FileIconUtils.
 */
private fun getFileIcon(item: FileItem): ImageVector {
    if (item.isDirectory) return Icons.Default.Folder

    return when (item.extension.lowercase()) {
        // Images
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "svg" -> Icons.Default.Image
        // Audio
        "mp3", "wav", "ogg", "m4a", "flac", "aac", "wma" -> Icons.Default.MusicNote
        // Video
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "3gp", "webm" -> Icons.Default.Movie
        // Archives
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "jar" -> Icons.AutoMirrored.Filled.InsertDriveFile
        // Code
        "xml", "json", "html", "css", "js", "jsx", "ts", "tsx", "java", "kt", "py", "c", "cpp", "h", "hpp", "go", "rs", "swift", "rb", "php", "sql", "sh", "bash", "zsh" -> Icons.Default.Code
        // Docs
        "doc", "docx" -> Icons.Default.Description
        "xls", "xlsx" -> Icons.Default.TableChart
        "pdf" -> Icons.Default.PictureAsPdf
        // Text
        "txt", "md", "log", "csv", "properties", "cfg", "conf", "ini", "yaml", "yml", "toml", "env" -> Icons.Default.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

private fun getFileIconColor(item: FileItem): Color {
    if (item.isDirectory) return Color(0xFFFFCC80)

    return when (item.extension.lowercase()) {
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "svg" -> Color(0xFFBA68C8)
        "mp3", "wav", "ogg", "m4a", "flac", "aac", "wma" -> Color(0xFF4FC3F7)
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "3gp", "webm" -> Color(0xFFE57373)
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "jar" -> Color(0xFFA1887F)
        "xml", "json", "html", "css", "js", "jsx", "ts", "tsx", "java", "kt", "py", "c", "cpp", "h", "hpp", "go", "rs", "swift", "rb", "php", "sql", "sh", "bash", "zsh" -> Color(0xFF4DB6AC)
        "txt", "md", "log", "csv", "properties", "cfg", "conf", "ini", "yaml", "yml", "toml", "env" -> Color(0xFF90A4AE)
        "pdf" -> Color(0xFF7986CB)
        "doc", "docx" -> Color(0xFF7986CB)
        "xls", "xlsx" -> Color(0xFF7986CB)
        else -> Color(0xFF64748B)
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return ""
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
        size < 1024L * 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024))
        else -> "%.1f GB".format(size / (1024.0 * 1024 * 1024))
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return ""
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
