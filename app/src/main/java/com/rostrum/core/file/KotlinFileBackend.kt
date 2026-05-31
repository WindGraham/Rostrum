package com.rostrum.core.file

import android.util.Log
import android.webkit.MimeTypeMap
import com.rostrum.core.util.FileOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class KotlinFileBackend : FileOperationRepository {

    companion object {
        private const val TAG = "KotlinFileBackend"
    }

    override suspend fun listFiles(path: String): Result<List<FileInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) {
                throw IllegalArgumentException("Not a valid directory: $path")
            }
            val files = dir.listFiles() ?: emptyArray()
            files.map { it.toFileInfo() }
        }
    }

    override suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (!file.exists()) throw IllegalArgumentException("File not found: $path")
            file.readText(Charsets.UTF_8)
        }
    }

    override suspend fun writeFile(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
        }
    }

    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        FileOperations.delete(File(path))
    }

    override suspend fun copyFile(src: String, dst: String): Result<Unit> = withContext(Dispatchers.IO) {
        FileOperations.copy(File(src), File(dst))
    }

    override suspend fun moveFile(src: String, dst: String): Result<Unit> = withContext(Dispatchers.IO) {
        FileOperations.move(File(src), File(dst))
    }

    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(path)
            if (!dir.mkdirs() && !dir.exists()) {
                throw RuntimeException("Failed to create directory: $path")
            }
        }
    }

    override suspend fun getFileInfo(path: String): Result<FileInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (!file.exists()) throw IllegalArgumentException("File not found: $path")
            file.toFileInfo()
        }
    }

    private fun File.toFileInfo(): FileInfo {
        val ext = extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return FileInfo(
            path = absolutePath,
            name = name,
            size = if (isFile) length() else 0L,
            lastModified = lastModified(),
            isDirectory = isDirectory,
            permissions = buildPermissionString(),
            mimeType = mime
        )
    }

    private fun File.buildPermissionString(): String {
        return buildString {
            append(if (canRead()) "r" else "-")
            append(if (canWrite()) "w" else "-")
            append(if (canExecute()) "x" else "-")
        }
    }
}
