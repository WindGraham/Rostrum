package com.rostrum.core.filesystem

import com.rostrum.core.plugin.models.FileInfo

/**
 * Product-level filesystem boundary.
 *
 * UI, plugins, local storage, SSH/SFTP, and remote rostrum-server integrations
 * should depend on this shape instead of concrete transport implementations.
 */
interface FileSystemBackend {
    val backendId: String
    val kind: FileSystemBackendKind

    suspend fun read(path: String): Result<ByteArray>

    suspend fun write(path: String, content: ByteArray): Result<Unit>

    suspend fun readText(path: String, encoding: String = "UTF-8"): Result<String> {
        return read(path).mapCatching { bytes -> String(bytes, charset(encoding)) }
    }

    suspend fun writeText(path: String, content: String, encoding: String = "UTF-8"): Result<Unit> {
        return write(path, content.toByteArray(charset(encoding)))
    }

    suspend fun exists(path: String): Boolean

    suspend fun stat(path: String): FileInfo?

    suspend fun list(path: String): Result<List<FileInfo>>

    suspend fun mkdir(path: String): Result<Unit>

    suspend fun delete(path: String): Result<Unit>

    suspend fun copy(sourcePath: String, targetPath: String): Result<Unit>

    suspend fun move(sourcePath: String, targetPath: String): Result<Unit>
}

enum class FileSystemBackendKind {
    LOCAL,
    SSH,
    REMOTE_SERVER,
    MCP,
    VIRTUAL,
    UNKNOWN
}
