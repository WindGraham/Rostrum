package com.rostrum.core.filesystem

import android.util.Log
import com.rostrum.core.plugin.models.FileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 本地文件系统实现
 * 
 * 实现 FileSystemService 接口，提供本地文件操作功能
 * 
 * @author OmniMaster
 * @license BSD-2-Clause
 */
class LocalFileSystem : FileSystemService {
    override val backendId: String = "local"
    override val kind: FileSystemBackendKind = FileSystemBackendKind.LOCAL

    
    companion object {
        private const val TAG = "LocalFileSystem"
    }
    
    override suspend fun readFile(uri: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val file = File(uri)
            if (!file.exists()) {
                return@withContext Result.failure(IOException("文件不存在: $uri"))
            }
            if (!file.isFile) {
                return@withContext Result.failure(IOException("路径不是文件: $uri"))
            }
            Result.success(file.readBytes())
        } catch (e: Exception) {
            Log.e(TAG, "读取文件失败: $uri", e)
            Result.failure(e)
        }
    }
    
    override suspend fun writeFile(uri: String, content: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(uri)
            // 确保父目录存在
            file.parentFile?.mkdirs()
            file.writeBytes(content)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "写入文件失败: $uri", e)
            Result.failure(e)
        }
    }
    
    override suspend fun readTextFile(uri: String, encoding: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(uri)
            if (!file.exists()) {
                return@withContext Result.failure(IOException("文件不存在: $uri"))
            }
            if (!file.isFile) {
                return@withContext Result.failure(IOException("路径不是文件: $uri"))
            }
            Result.success(file.readText(charset(encoding)))
        } catch (e: Exception) {
            Log.e(TAG, "读取文本文件失败: $uri", e)
            Result.failure(e)
        }
    }
    
    override suspend fun writeTextFile(uri: String, content: String, encoding: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(uri)
            // 确保父目录存在
            file.parentFile?.mkdirs()
            file.writeText(content, charset(encoding))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "写入文本文件失败: $uri", e)
            Result.failure(e)
        }
    }
    
    override suspend fun exists(uri: String): Boolean = withContext(Dispatchers.IO) {
        File(uri).exists()
    }
    
    override suspend fun getFileInfo(uri: String): FileInfo? = withContext(Dispatchers.IO) {
        try {
            val file = File(uri)
            if (!file.exists()) {
                return@withContext null
            }
            fileToFileInfo(file)
        } catch (e: Exception) {
            Log.e(TAG, "获取文件信息失败: $uri", e)
            null
        }
    }
    
    override suspend fun listDirectory(uri: String): Result<List<FileInfo>> = withContext(Dispatchers.IO) {
        try {
            val dir = File(uri)
            if (!dir.exists()) {
                return@withContext Result.failure(IOException("目录不存在: $uri"))
            }
            if (!dir.isDirectory) {
                return@withContext Result.failure(IOException("路径不是目录: $uri"))
            }
            
            val files = dir.listFiles() ?: emptyArray()
            val fileInfoList = files.mapNotNull { file ->
                try {
                    fileToFileInfo(file)
                } catch (e: Exception) {
                    Log.w(TAG, "无法获取文件信息: ${file.absolutePath}", e)
                    null
                }
            }
            Result.success(fileInfoList)
        } catch (e: Exception) {
            Log.e(TAG, "列出目录失败: $uri", e)
            Result.failure(e)
        }
    }
    
    override suspend fun createDirectory(uri: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dir = File(uri)
            if (dir.exists()) {
                if (dir.isDirectory) {
                    return@withContext Result.success(Unit)
                } else {
                    return@withContext Result.failure(IOException("路径已存在且不是目录: $uri"))
                }
            }
            if (dir.mkdirs()) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("创建目录失败: $uri"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建目录失败: $uri", e)
            Result.failure(e)
        }
    }
    
    override suspend fun delete(uri: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(uri)
            if (!file.exists()) {
                return@withContext Result.success(Unit) // 文件不存在视为删除成功
            }
            
            val success = if (file.isDirectory) {
                deleteRecursively(file)
            } else {
                file.delete()
            }
            
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("删除失败: $uri"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除失败: $uri", e)
            Result.failure(e)
        }
    }
    
    override suspend fun copy(sourceUri: String, targetUri: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val source = File(sourceUri)
            val target = File(targetUri)
            
            if (!source.exists()) {
                return@withContext Result.failure(IOException("源文件不存在: $sourceUri"))
            }
            
            // 确保目标父目录存在
            target.parentFile?.mkdirs()
            
            if (source.isDirectory) {
                copyDirectoryRecursively(source, target)
            } else {
                source.copyTo(target, overwrite = true)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "复制失败: $sourceUri -> $targetUri", e)
            Result.failure(e)
        }
    }
    
    override suspend fun move(sourceUri: String, targetUri: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val source = File(sourceUri)
            val target = File(targetUri)
            
            if (!source.exists()) {
                return@withContext Result.failure(IOException("源文件不存在: $sourceUri"))
            }
            
            // 确保目标父目录存在
            target.parentFile?.mkdirs()
            
            // 尝试直接重命名（同一文件系统内最高效）
            if (source.renameTo(target)) {
                return@withContext Result.success(Unit)
            }
            
            // 重命名失败，使用复制+删除
            if (source.isDirectory) {
                copyDirectoryRecursively(source, target)
            } else {
                source.copyTo(target, overwrite = true)
            }
            deleteRecursively(source)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "移动失败: $sourceUri -> $targetUri", e)
            Result.failure(e)
        }
    }
    
    /**
     * 将 File 转换为 FileInfo
     */
    private fun fileToFileInfo(file: File): FileInfo {
        val name = file.name
        val extension = if (file.isFile && name.contains('.')) {
            "." + name.substringAfterLast('.')
        } else {
            ""
        }
        
        return FileInfo(
            path = file.absolutePath,
            name = name,
            extension = extension,
            size = if (file.isFile) file.length() else 0,
            lastModified = file.lastModified(),
            isDirectory = file.isDirectory,
            permissions = getPermissionsString(file),
            mimeType = getMimeType(file)
        )
    }
    
    /**
     * 获取文件权限字符串
     */
    private fun getPermissionsString(file: File): String {
        val sb = StringBuilder()
        sb.append(if (file.canRead()) "r" else "-")
        sb.append(if (file.canWrite()) "w" else "-")
        sb.append(if (file.canExecute()) "x" else "-")
        // 简化的权限字符串（只显示当前用户权限）
        return sb.toString() + sb.toString() + sb.toString()
    }
    
    /**
     * 获取文件MIME类型
     */
    private fun getMimeType(file: File): String? {
        if (file.isDirectory) return null
        
        val extension = file.extension.lowercase()
        return when (extension) {
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "md" -> "text/markdown"
            "kt" -> "text/x-kotlin"
            "java" -> "text/x-java"
            "py" -> "text/x-python"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            else -> null
        }
    }
    
    /**
     * 递归删除目录
     */
    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                if (!deleteRecursively(child)) {
                    return false
                }
            }
        }
        return file.delete()
    }
    
    /**
     * 递归复制目录
     */
    private fun copyDirectoryRecursively(source: File, target: File) {
        target.mkdirs()
        source.listFiles()?.forEach { child ->
            val targetChild = File(target, child.name)
            if (child.isDirectory) {
                copyDirectoryRecursively(child, targetChild)
            } else {
                child.copyTo(targetChild, overwrite = true)
            }
        }
    }
}
