package com.termux.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Local file system implementation - migrated from Rostrum
 */
class LocalFileSystem : FileSystemService {
    
    override suspend fun readFile(uri: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            Result.success(File(uri).readBytes())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun writeFile(uri: String, content: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            File(uri).writeBytes(content)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun readTextFile(uri: String, encoding: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(File(uri).readText(java.nio.charset.Charset.forName(encoding)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun writeTextFile(uri: String, content: String, encoding: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            File(uri).writeText(content, java.nio.charset.Charset.forName(encoding))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exists(uri: String): Boolean = withContext(Dispatchers.IO) {
        File(uri).exists()
    }

    override suspend fun getFileInfo(uri: String): Result<FileItem> = withContext(Dispatchers.IO) {
        try {
            val file = File(uri)
            Result.success(FileItem.fromFile(file))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listDirectory(uri: String): Result<List<FileItem>> = withContext(Dispatchers.IO) {
        try {
            val dir = File(uri)
            val files = dir.listFiles()?.map { FileItem.fromFile(it) } ?: emptyList()
            Result.success(files.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { it.name.lowercase() }))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createDirectory(uri: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val success = File(uri).mkdirs()
            if (success) Result.success(Unit) else Result.failure(Exception("Failed to create directory"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun delete(uri: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val success = File(uri).deleteRecursively()
            if (success) Result.success(Unit) else Result.failure(Exception("Failed to delete"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun copy(source: String, target: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            File(source).copyTo(File(target), overwrite = true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun move(source: String, target: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            File(source).renameTo(File(target))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
