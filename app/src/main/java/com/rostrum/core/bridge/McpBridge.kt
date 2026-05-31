package com.rostrum.core.bridge

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

sealed class McpConnectionState {
    data object Disconnected : McpConnectionState()
    data object Connecting : McpConnectionState()
    data class Connected(val serverName: String, val serverVersion: String) : McpConnectionState()
    data class Error(val message: String) : McpConnectionState()
}

data class McpToolInfo(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

data class McpToolResult(
    val content: List<McpContent>,
    val isError: Boolean = false
)

sealed class McpContent {
    data class Text(val text: String) : McpContent()
    data class Image(val data: String, val mimeType: String) : McpContent()
}

class McpBridge(
    private val baseUrl: String
) {
    private val _state = MutableStateFlow<McpConnectionState>(McpConnectionState.Disconnected)
    val state: StateFlow<McpConnectionState> = _state

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cachedTools: List<McpToolInfo>? = null

    companion object {
        private const val TAG = "McpBridge"
    }

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            _state.value = McpConnectionState.Connecting

            val initRequest = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", UUID.randomUUID().toString())
                put("method", "initialize")
                put("params", buildJsonObject {
                    put("protocolVersion", "2025-03-26")
                    put("capabilities", buildJsonObject {})
                    put("clientInfo", buildJsonObject {
                        put("name", "OmniMaster")
                        put("version", "1.0.0")
                    })
                })
            }

            val response = sendRequest(initRequest)
            val result = response["result"]?.jsonObject
                ?: throw RuntimeException("Initialize failed: no result in response")

            val serverInfo = result["serverInfo"]?.jsonObject
            val serverName = serverInfo?.get("name")?.jsonPrimitive?.content ?: "unknown"
            val serverVersion = serverInfo?.get("version")?.jsonPrimitive?.content ?: "unknown"

            val notifyRequest = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "notifications/initialized")
            }
            sendRequest(notifyRequest)

            _state.value = McpConnectionState.Connected(serverName, serverVersion)
            Log.d(TAG, "Connected to $serverName v$serverVersion at $baseUrl")
            Unit
        }.onFailure {
            _state.value = McpConnectionState.Error(it.message ?: "Connection failed")
            Log.e(TAG, "Connection failed to $baseUrl", it)
        }
    }

    suspend fun listTools(): Result<List<McpToolInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            cachedTools?.let { return@runCatching it }

            val request = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", UUID.randomUUID().toString())
                put("method", "tools/list")
            }

            val response = sendRequest(request)
            val result = response["result"]?.jsonObject
                ?: throw RuntimeException("tools/list failed: no result")

            val toolsArray = result["tools"]?.jsonArray
                ?: throw RuntimeException("tools/list failed: no tools array")

            val tools = toolsArray.map { toolElement ->
                val toolObj = toolElement.jsonObject
                McpToolInfo(
                    name = toolObj["name"]?.jsonPrimitive?.content ?: "",
                    description = toolObj["description"]?.jsonPrimitive?.content ?: "",
                    inputSchema = toolObj["inputSchema"]?.jsonObject ?: JsonObject(emptyMap())
                )
            }

            cachedTools = tools
            tools
        }
    }

    suspend fun callTool(
        toolName: String,
        arguments: Map<String, Any>
    ): Result<McpToolResult> = withContext(Dispatchers.IO) {
        runCatching {
            val argsJson = buildJsonObject {
                for ((key, value) in arguments) {
                    when (value) {
                        is String -> put(key, value)
                        is Number -> put(key, value.toDouble())
                        is Boolean -> put(key, value)
                        else -> put(key, value.toString())
                    }
                }
            }

            val request = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", UUID.randomUUID().toString())
                put("method", "tools/call")
                put("params", buildJsonObject {
                    put("name", toolName)
                    put("arguments", argsJson)
                })
            }

            val response = sendRequest(request)

            val error = response["error"]?.jsonObject
            if (error != null) {
                val errorMsg = error["message"]?.jsonPrimitive?.content ?: "Unknown error"
                return@runCatching McpToolResult(
                    content = listOf(McpContent.Text("Error: $errorMsg")),
                    isError = true
                )
            }

            val result = response["result"]?.jsonObject
                ?: throw RuntimeException("tools/call failed: no result")

            val contentArray = result["content"]?.jsonArray ?: throw RuntimeException("No content in result")
            val contentList = contentArray.map { element ->
                val obj = element.jsonObject
                when (obj["type"]?.jsonPrimitive?.content) {
                    "image" -> McpContent.Image(
                        data = obj["data"]?.jsonPrimitive?.content ?: "",
                        mimeType = obj["mimeType"]?.jsonPrimitive?.content ?: "image/png"
                    )
                    else -> McpContent.Text(obj["text"]?.jsonPrimitive?.content ?: "")
                }
            }

            val isError = result["isError"]?.jsonPrimitive?.content?.toBoolean() ?: false
            McpToolResult(content = contentList, isError = isError)
        }
    }

    suspend fun disconnect() {
        _state.value = McpConnectionState.Disconnected
        cachedTools = null
    }

    fun invalidateToolCache() {
        cachedTools = null
    }

    private suspend fun sendRequest(requestBody: JsonObject): JsonObject = mutex.withLock {
        val url = URL("$baseUrl/mcp")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("MCP-Protocol-Version", "2025-03-26")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 60_000

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestBody.toString())
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            if (responseCode !in 200..299) {
                throw RuntimeException("HTTP $responseCode: $responseText")
            }

            json.decodeFromString<JsonObject>(responseText)
        } finally {
            conn.disconnect()
        }
    }
}
