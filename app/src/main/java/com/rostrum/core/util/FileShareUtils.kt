package com.rostrum.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * 文件分享工具类
 * 
 * 提供以下功能：
 * 1. 分享单个文件到其他应用
 * 2. 分享文件夹（自动压缩为 ZIP）
 * 3. 分享多个文件
 * 4. 用其他应用打开文件
 */
object FileShareUtils {
    
    private const val TAG = "FileShareUtils"

    data class ApkInstallResult(
        val launched: Boolean,
        val requiresUserPermission: Boolean = false,
        val message: String? = null
    )
    
    /**
     * 分享文件或文件夹
     * 
     * @param context 上下文
     * @param file 要分享的文件或文件夹
     * @param onError 错误回调
     */
    fun shareFile(context: Context, file: File, onError: ((String) -> Unit)? = null) {
        try {
            if (!file.exists()) {
                val msg = "文件不存在: ${file.name}"
                onError?.invoke(msg) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                return
            }
            
            if (file.isDirectory) {
                // 文件夹需要先压缩
                shareDirectory(context, file, onError)
            } else {
                // 直接分享文件
                shareSingleFile(context, file, onError)
            }
        } catch (e: Exception) {
            Log.e(TAG, "分享文件失败", e)
            val msg = "分享失败: ${e.message}"
            onError?.invoke(msg) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 分享多个文件
     * 
     * @param context 上下文
     * @param files 要分享的文件列表
     * @param onError 错误回调
     */
    fun shareFiles(context: Context, files: List<File>, onError: ((String) -> Unit)? = null) {
        try {
            if (files.isEmpty()) {
                val msg = "没有选择文件"
                onError?.invoke(msg) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                return
            }
            
            // 检查是否包含文件夹
            val hasDirectory = files.any { it.isDirectory }
            
            if (hasDirectory || files.size > 1) {
                // 有文件夹或多个文件，压缩后分享
                shareAsZip(context, files, onError)
            } else {
                // 单个文件
                shareSingleFile(context, files.first(), onError)
            }
        } catch (e: Exception) {
            Log.e(TAG, "分享多个文件失败", e)
            val msg = "分享失败: ${e.message}"
            onError?.invoke(msg) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 用其他应用打开文件
     * 
     * @param context 上下文
     * @param file 要打开的文件
     * @param onError 错误回调
     */
    fun openWith(context: Context, file: File, onError: ((String) -> Unit)? = null) {
        try {
            if (!file.exists()) {
                val msg = "文件不存在: ${file.name}"
                onError?.invoke(msg) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                return
            }
            
            if (file.isDirectory) {
                val msg = "无法用其他应用打开文件夹"
                onError?.invoke(msg) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                return
            }
            
            val uri = getFileUri(context, file)
            val mimeType = getMimeType(file)
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val chooserIntent = Intent.createChooser(intent, "用其他应用打开 ${file.name}")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            try {
                context.startActivity(chooserIntent)
            } catch (e: android.content.ActivityNotFoundException) {
                val msg = "没有找到可以打开此文件的应用"
                onError?.invoke(msg) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开文件失败", e)
            val msg = "打开失败: ${e.message}"
            onError?.invoke(msg) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 安装 APK（拉起系统安装器）。
     */
    fun installApk(
        context: Context,
        apkFile: File,
        onError: ((String) -> Unit)? = null
    ): ApkInstallResult {
        if (!apkFile.exists() || !apkFile.isFile) {
            val msg = "APK 不存在: ${apkFile.absolutePath}"
            onError?.invoke(msg) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            return ApkInstallResult(launched = false, message = msg)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val msg = "当前未授予“安装未知应用”权限，请先授权"
            onError?.invoke(msg) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            return ApkInstallResult(
                launched = false,
                requiresUserPermission = true,
                message = msg
            )
        }

        return runCatching {
            val uri = getFileUri(context, apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ApkInstallResult(launched = true)
        }.getOrElse { e ->
            Log.e(TAG, "安装 APK 失败", e)
            val msg = "安装失败: ${e.message}"
            onError?.invoke(msg) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            ApkInstallResult(launched = false, message = msg)
        }
    }

    fun openUnknownAppsSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }
    
    /**
     * 分享单个文件
     */
    private fun shareSingleFile(context: Context, file: File, onError: ((String) -> Unit)?) {
        val uri = getFileUri(context, file)
        val mimeType = getMimeType(file)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooserIntent = Intent.createChooser(intent, "分享 ${file.name}")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
        
        Log.d(TAG, "分享文件: ${file.absolutePath}, MIME: $mimeType")
    }
    
    /**
     * 分享文件夹（压缩后分享）
     */
    private fun shareDirectory(context: Context, directory: File, onError: ((String) -> Unit)?) {
        // 创建临时 ZIP 文件
        val zipFile = File(context.cacheDir, "share/${directory.name}.zip")
        zipFile.parentFile?.mkdirs()
        
        // 如果已存在旧的压缩文件，先删除
        if (zipFile.exists()) {
            zipFile.delete()
        }
        
        // 压缩文件夹
        val result = ZipUtils.zip(listOf(directory), zipFile)
        
        if (result.isSuccess) {
            shareSingleFile(context, zipFile, onError)
        } else {
            val msg = "压缩文件夹失败: ${result.exceptionOrNull()?.message}"
            onError?.invoke(msg) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 将多个文件压缩后分享
     */
    private fun shareAsZip(context: Context, files: List<File>, onError: ((String) -> Unit)?) {
        // 使用时间戳创建唯一的 ZIP 文件名
        val timestamp = System.currentTimeMillis()
        val zipFileName = if (files.size == 1 && files.first().isDirectory) {
            "${files.first().name}.zip"
        } else {
            "files_$timestamp.zip"
        }
        
        val zipFile = File(context.cacheDir, "share/$zipFileName")
        zipFile.parentFile?.mkdirs()
        
        // 如果已存在旧的压缩文件，先删除
        if (zipFile.exists()) {
            zipFile.delete()
        }
        
        // 压缩文件
        val result = ZipUtils.zip(files, zipFile)
        
        if (result.isSuccess) {
            shareSingleFile(context, zipFile, onError)
        } else {
            val msg = "压缩文件失败: ${result.exceptionOrNull()?.message}"
            onError?.invoke(msg) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 获取文件的 Content URI（通过 FileProvider）
     */
    private fun getFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
    
    /**
     * 获取文件的 MIME 类型
     */
    fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        
        // 首先尝试使用系统的 MIME 类型映射
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (mimeType != null) {
            return mimeType
        }
        
        // 自定义 MIME 类型映射
        return when (extension) {
            // 文本文件
            "txt", "log", "ini", "cfg", "conf" -> "text/plain"
            "md", "markdown" -> "text/markdown"
            "json" -> "application/json"
            "xml" -> "text/xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "text/javascript"
            "kt", "kts" -> "text/x-kotlin"
            "java" -> "text/x-java"
            "py" -> "text/x-python"
            "c", "h" -> "text/x-c"
            "cpp", "hpp", "cc", "cxx" -> "text/x-c++"
            "sh" -> "text/x-shellscript"
            
            // 图片
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            
            // 音频
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"
            
            // 视频
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            
            // 文档
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            
            // 压缩文件
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            
            // 安装包
            "apk" -> "application/vnd.android.package-archive"
            
            // 默认
            else -> "application/octet-stream"
        }
    }
}
