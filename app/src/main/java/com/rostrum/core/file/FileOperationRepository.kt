package com.rostrum.core.file

import kotlinx.serialization.Serializable

interface FileOperationRepository {
    suspend fun listFiles(path: String): Result<List<FileInfo>>
    suspend fun readFile(path: String): Result<String>
    suspend fun writeFile(path: String, content: String): Result<Unit>
    suspend fun deleteFile(path: String): Result<Unit>
    suspend fun copyFile(src: String, dst: String): Result<Unit>
    suspend fun moveFile(src: String, dst: String): Result<Unit>
    suspend fun createDirectory(path: String): Result<Unit>
    suspend fun getFileInfo(path: String): Result<FileInfo>
}

@Serializable
data class FileInfo(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val permissions: String = "",
    val mimeType: String? = null
)
