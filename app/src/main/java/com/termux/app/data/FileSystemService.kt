package com.termux.app.data

/**
 * File system service interface - migrated from Rostrum
 * Unified API for both local and remote files
 */
interface FileSystemService {
    suspend fun readFile(uri: String): Result<ByteArray>
    suspend fun writeFile(uri: String, content: ByteArray): Result<Unit>
    suspend fun readTextFile(uri: String, encoding: String = "UTF-8"): Result<String>
    suspend fun writeTextFile(uri: String, content: String, encoding: String = "UTF-8"): Result<Unit>
    suspend fun exists(uri: String): Boolean
    suspend fun getFileInfo(uri: String): Result<FileItem>
    suspend fun listDirectory(uri: String): Result<List<FileItem>>
    suspend fun createDirectory(uri: String): Result<Unit>
    suspend fun delete(uri: String): Result<Unit>
    suspend fun copy(source: String, target: String): Result<Unit>
    suspend fun move(source: String, target: String): Result<Unit>
}
