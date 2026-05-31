package com.rostrum.core.server

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * 远程文件编辑器。
 * 通过 rostrum-server 实现实时文件编辑。
 */
class RemoteFileEditor(
    private val serverUrl: String,
    private val token: String? = null
) {
    
    companion object {
        private const val TAG = "RemoteFileEditor"
        private const val CONNECT_TIMEOUT = 5000
        private const val READ_TIMEOUT = 30000
    }
    
    // 文件监控回调
    private val watchCallbacks = ConcurrentHashMap<String, (FileEvent) -> Unit>()
    
    /**
     * 获取文件列表
     */
    suspend fun listFiles(path: String): Result<List<RemoteFileInfo>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/files/list?path=${encodePath(path)}")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.applyAuth()
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val files = parseFileList(response)
                Result.success(files)
            } else {
                val error = connection.readErrorText()
                Result.failure(Exception("Failed to list files: $error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list files", e)
            Result.failure(e)
        }
    }
    
    /**
     * 读取文件内容
     */
    suspend fun readFile(path: String): Result<RemoteFileContent> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/files/read?path=${encodePath(path)}")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.applyAuth()
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val content = parseFileContent(response)
                Result.success(content)
            } else {
                val error = connection.readErrorText()
                Result.failure(Exception("Failed to read file: $error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file", e)
            Result.failure(e)
        }
    }
    
    /**
     * 写入文件内容
     */
    suspend fun writeFile(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/files/write")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.applyAuth()
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val requestBody = JSONObject().apply {
                put("path", path)
                put("content", content)
            }
            
            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }
            
            if (connection.responseCode == 200) {
                Result.success(Unit)
            } else {
                val error = connection.readErrorText()
                Result.failure(Exception("Failed to write file: $error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write file", e)
            Result.failure(e)
        }
    }
    
    /**
     * 删除文件
     */
    suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/files/delete")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.applyAuth()
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val requestBody = JSONObject().apply {
                put("path", path)
            }
            
            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }
            
            if (connection.responseCode == 200) {
                Result.success(Unit)
            } else {
                val error = connection.readErrorText()
                Result.failure(Exception("Failed to delete file: $error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file", e)
            Result.failure(e)
        }
    }
    
    /**
     * 创建目录
     */
    suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/files/mkdir")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.applyAuth()
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val requestBody = JSONObject().apply {
                put("path", path)
            }
            
            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }
            
            if (connection.responseCode == 200) {
                Result.success(Unit)
            } else {
                val error = connection.readErrorText()
                Result.failure(Exception("Failed to create directory: $error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create directory", e)
            Result.failure(e)
        }
    }
    
    /**
     * 执行命令
     */
    suspend fun executeCommand(command: String, workDir: String? = null): Result<CommandResult> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/command/execute")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.applyAuth()
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val requestBody = JSONObject().apply {
                put("command", command)
                if (workDir != null) {
                    put("work_dir", workDir)
                }
            }
            
            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val result = parseCommandResult(response)
                Result.success(result)
            } else {
                val error = connection.readErrorText()
                Result.failure(Exception("Failed to execute command: $error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute command", e)
            Result.failure(e)
        }
    }
    
    /**
     * 监控文件变化
     */
    fun watchFiles(path: String): Flow<FileEvent> = flow {
        try {
            val url = URL("$serverUrl/api/files/watch?path=${encodePath(path)}")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.applyAuth()
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = 0 // 长连接
            connection.setRequestProperty("Accept", "text/event-stream")
            
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                if (line!!.startsWith("data: ")) {
                    val data = line!!.substring(6)
                    val event = parseFileEvent(data)
                    emit(event)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to watch files", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 检查远端服务状态。
     */
    suspend fun checkStatus(): Result<ServerStatus> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/api/status")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.applyAuth()
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val status = parseServerStatus(response)
                Result.success(status)
            } else {
                Result.failure(Exception("Rostrum Server is not running"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check status", e)
            Result.failure(e)
        }
    }
    
    /**
     * Ping rostrum-server。
     */
    suspend fun ping(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/health")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.applyAuth()
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
             
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                Result.success(response.contains("ok") || response.contains("running"))
            } else {
                Result.success(false)
            }
        } catch (e: Exception) {
            Result.success(false)
        }
    }
    
    // 解析文件列表
    private fun parseFileList(response: String): List<RemoteFileInfo> {
        val files = mutableListOf<RemoteFileInfo>()
        val jsonArray = org.json.JSONArray(response)
        
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            files.add(RemoteFileInfo(
                name = jsonObject.getString("name"),
                path = jsonObject.getString("path"),
                size = jsonObject.getLong("size"),
                isDir = jsonObject.getBoolean("is_dir"),
                modTime = jsonObject.getString("mod_time"),
                mode = jsonObject.getString("mode")
            ))
        }
        
        return files
    }
    
    // 解析文件内容
    private fun parseFileContent(response: String): RemoteFileContent {
        val jsonObject = JSONObject(response)
        return RemoteFileContent(
            content = jsonObject.getString("content"),
            path = jsonObject.getString("path"),
            size = jsonObject.getLong("size"),
            modTime = jsonObject.getString("mod_time")
        )
    }
    
    // 解析命令结果
    private fun parseCommandResult(response: String): CommandResult {
        val jsonObject = JSONObject(response)
        return CommandResult(
            output = jsonObject.getString("output"),
            error = jsonObject.getString("error"),
            exitCode = jsonObject.getInt("exit_code")
        )
    }
    
    // 解析文件事件
    private fun parseFileEvent(response: String): FileEvent {
        val jsonObject = JSONObject(response)
        return FileEvent(
            path = jsonObject.getString("path"),
            operation = jsonObject.getString("operation"),
            timestamp = jsonObject.getLong("timestamp")
        )
    }
    
    // 解析远端服务状态
    private fun parseServerStatus(response: String): ServerStatus {
        val jsonObject = JSONObject(response)
        return ServerStatus(
            status = jsonObject.getString("status"),
            version = jsonObject.getString("version"),
            uptime = jsonObject.getString("uptime"),
            workspace = jsonObject.getString("workspace")
        )
    }

    suspend fun copyPath(source: String, target: String): Result<Unit> = withContext(Dispatchers.IO) {
        postPathPair("/api/files/copy", source, target, "copy")
    }

    suspend fun movePath(source: String, target: String): Result<Unit> = withContext(Dispatchers.IO) {
        postPathPair("/api/files/move", source, target, "move")
    }

    private fun postPathPair(endpoint: String, source: String, target: String, operation: String): Result<Unit> {
        return try {
            val url = URL("$serverUrl$endpoint")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.applyAuth()
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                put("source", source)
                put("target", target)
            }

            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }

            if (connection.responseCode == 200) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to $operation path: ${connection.readErrorText()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to $operation path", e)
            Result.failure(e)
        }
    }

    private fun encodePath(path: String): String = URLEncoder.encode(path, "UTF-8")

    private fun HttpURLConnection.applyAuth() {
        val bearer = token?.takeIf { it.isNotBlank() } ?: return
        setRequestProperty("Authorization", "Bearer $bearer")
    }

    private fun HttpURLConnection.readErrorText(): String {
        return (errorStream ?: inputStream)?.bufferedReader()?.readText().orEmpty()
    }
}

/**
 * 远程文件信息
 */
data class RemoteFileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val isDir: Boolean,
    val modTime: String,
    val mode: String
)

/**
 * 远程文件内容
 */
data class RemoteFileContent(
    val content: String,
    val path: String,
    val size: Long,
    val modTime: String
)

/**
 * 命令执行结果
 */
data class CommandResult(
    val output: String,
    val error: String,
    val exitCode: Int
)

/**
 * 文件事件
 */
data class FileEvent(
    val path: String,
    val operation: String,
    val timestamp: Long
)

/**
 * 远端服务状态。
 */
data class ServerStatus(
    val status: String,
    val version: String,
    val uptime: String,
    val workspace: String
)
