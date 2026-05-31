package com.rostrum.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.rostrum.core.domain.model.FileItem

object FileIconUtils {
    
    // 扩展名常量
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "svg")
    private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "wma")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "3gp", "webm")
    private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "jar")
    private val APK_EXTENSIONS = setOf("apk", "xapk", "apks")
    private val CODE_EXTENSIONS = setOf("xml", "json", "html", "css", "js", "java", "kt", "py", "c", "cpp", "h", "sh", "bat", "gradle", "properties", "pro")
    private val TEXT_EXTENSIONS = setOf("txt", "md", "log", "prop", "conf", "ini")
    private val DOC_EXTENSIONS = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf")
    
    /**
     * 获取文件对应的图标
     */
    fun getFileIcon(fileItem: FileItem): ImageVector {
        if (fileItem.isDirectory) return Icons.Default.Folder
        
        return when (fileItem.extension) {
            in IMAGE_EXTENSIONS -> Icons.Default.Image
            in AUDIO_EXTENSIONS -> Icons.Default.Audiotrack // 或者 MusicNote
            in VIDEO_EXTENSIONS -> Icons.Default.PlayCircle // 或者 VideoFile (如果可用)
            in ARCHIVE_EXTENSIONS -> Icons.Default.FolderZip // 只有较新版本Compose有，如果报错改为 Inventory2
            in APK_EXTENSIONS -> Icons.Default.Android
            in CODE_EXTENSIONS -> Icons.Default.Code
            in TEXT_EXTENSIONS -> Icons.Default.Description
            in DOC_EXTENSIONS -> if (fileItem.extension == "pdf") Icons.Default.PictureAsPdf else Icons.Default.Description
            else -> Icons.Default.InsertDriveFile
        }
    }
    
    /**
     * 获取文件对应的图标颜色
     */
    fun getFileIconColor(fileItem: FileItem): Color {
        if (fileItem.isDirectory) return Color(0xFFFFCC80) // 浅橙色
        
        return when (fileItem.extension) {
            in IMAGE_EXTENSIONS -> Color(0xFFBA68C8) // 紫色
            in AUDIO_EXTENSIONS -> Color(0xFF4FC3F7) // 浅蓝色
            in VIDEO_EXTENSIONS -> Color(0xFFE57373) // 红色
            in ARCHIVE_EXTENSIONS -> Color(0xFFA1887F) // 棕色
            in APK_EXTENSIONS -> Color(0xFF81C784) // 绿色
            in CODE_EXTENSIONS -> Color(0xFF4DB6AC) // 青色
            in TEXT_EXTENSIONS -> Color(0xFF90A4AE) // 蓝灰色
            in DOC_EXTENSIONS -> Color(0xFF7986CB) // 靛青色
            else -> Color.Gray
        }
    }
}
