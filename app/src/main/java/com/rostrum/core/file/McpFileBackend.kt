package com.rostrum.core.file

import android.util.Log
import com.rostrum.core.bridge.McpBridge
import com.rostrum.core.bridge.McpContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class McpFileBackend(
    private val bridge: McpBridge
) : FileOperationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "McpFileBackend"
    }

    override suspend fun listFiles(path: String): Result<List<FileInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val result = bridge.callTool("list_directory", mapOf("path" to path)).getOrThrow()
            if (result.isError) throw RuntimeException(extractText(result))

            val text = extractText(result)
            json.decodeFromString<List<FileInfo>>(text)
        }
    }

    override suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val result = bridge.callTool("read_file", mapOf("path" to path)).getOrThrow()
            if (result.isError) throw RuntimeException(extractText(result))
            extractText(result)
        }
    }

    override suspend fun writeFile(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val result = bridge.callTool(
                "write_file",
                mapOf("path" to path, "content" to content)
            ).getOrThrow()
            if (result.isError) throw RuntimeException(extractText(result))
        }
    }

    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val result = bridge.callTool("delete_file", mapOf("path" to path)).getOrThrow()
            if (result.isError) throw RuntimeException(extractText(result))
        }
    }

    override suspend fun copyFile(src: String, dst: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val result = bridge.callTool(
                "copy_file",
                mapOf("source" to src, "destination" to dst)
            ).getOrThrow()
            if (result.isError) throw RuntimeException(extractText(result))
        }
    }

    override suspend fun moveFile(src: String, dst: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val result = bridge.callTool(
                "move_file",
                mapOf("source" to src, "destination" to dst)
            ).getOrThrow()
            if (result.isError) throw RuntimeException(extractText(result))
        }
    }

    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val result = bridge.callTool("create_directory", mapOf("path" to path)).getOrThrow()
            if (result.isError) throw RuntimeException(extractText(result))
        }
    }

    override suspend fun getFileInfo(path: String): Result<FileInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val result = bridge.callTool("get_file_info", mapOf("path" to path)).getOrThrow()
            if (result.isError) throw RuntimeException(extractText(result))

            val text = extractText(result)
            json.decodeFromString<FileInfo>(text)
        }
    }

    private fun extractText(result: com.rostrum.core.bridge.McpToolResult): String {
        return result.content
            .filterIsInstance<McpContent.Text>()
            .joinToString("\n") { it.text }
    }
}
