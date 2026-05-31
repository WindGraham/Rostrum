package com.rostrum.core.gateway

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class GatewayServer(
    private val config: GatewayConfig = GatewayConfig(),
    private val pairingManager: PairingManager,
    private val mcpServerPorts: Map<String, Int> = mapOf(
        "filesystem" to 18700,
        "terminal" to 18701
    )
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val connectionSemaphore = Semaphore(config.maxConnections)
    private val running = AtomicBoolean(false)
    private val activeConnections = AtomicInteger(0)

    companion object {
        private const val TAG = "GatewayServer"
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return

        acceptJob = scope.launch {
            try {
                serverSocket = ServerSocket(config.port).also {
                    it.soTimeout = 1000
                }
                Log.d(TAG, "Gateway listening on port ${config.port}")

                while (isActive && running.get()) {
                    val socket = try {
                        serverSocket?.accept()
                    } catch (_: SocketTimeoutException) {
                        continue
                    } ?: break

                    if (!connectionSemaphore.tryAcquire()) {
                        sendResponse(socket, 503, buildErrorJson("Server busy"))
                        socket.close()
                        continue
                    }

                    launch {
                        activeConnections.incrementAndGet()
                        try {
                            withTimeout(config.requestTimeout) {
                                handleConnection(socket)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Connection error", e)
                            runCatching { sendResponse(socket, 500, buildErrorJson(e.message ?: "Internal error")) }
                        } finally {
                            runCatching { socket.close() }
                            activeConnections.decrementAndGet()
                            connectionSemaphore.release()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
            } finally {
                Log.d(TAG, "Gateway server stopped")
            }
        }
    }

    fun stop() {
        running.set(false)
        acceptJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
        activeConnections.set(0)
        Log.d(TAG, "Gateway stop requested")
    }

    fun isRunning(): Boolean = running.get()

    fun getPort(): Int = config.port

    fun getLocalIpAddress(): String {
        return try {
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { addr ->
                    !addr.isLoopbackAddress && addr is Inet4Address
                }
                ?.hostAddress ?: "127.0.0.1"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local IP", e)
            "127.0.0.1"
        }
    }

    fun getConnectedDeviceCount(): Int = activeConnections.get()

    private suspend fun handleConnection(socket: Socket) {
        val input = socket.getInputStream().bufferedReader()
        val requestLine = input.readLine() ?: return
        if (requestLine.length > 8192) {
            sendResponse(socket, 414, buildErrorJson("URI too long"))
            return
        }
        val parts = requestLine.split(" ")
        if (parts.size < 3) {
            sendResponse(socket, 400, buildErrorJson("Bad request"))
            return
        }

        val method = parts[0]
        val path = parts[1]

        if (method == "OPTIONS") {
            sendCorsPreflightResponse(socket)
            return
        }

        val headers = readHeaders(input)

        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        if (contentLength < 0 || contentLength > config.maxRequestBodySize) {
            sendResponse(socket, 413, buildErrorJson("Request body too large"))
            return
        }
        val body = if (contentLength > 0) {
            val buf = CharArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = input.read(buf, read, contentLength - read)
                if (n == -1) break
                read += n
            }
            String(buf, 0, read)
        } else {
            ""
        }

        when {
            method == "GET" && path == "/health" -> handleHealth(socket)
            method == "GET" && path == "/info" -> handleInfo(socket)
            method == "POST" && path == "/mcp" -> handleMcp(socket, headers, body)
            method == "POST" && path == "/pair" -> handlePair(socket, body)
            else -> sendResponse(socket, 404, buildErrorJson("Not found"))
        }
    }

    private fun readHeaders(reader: BufferedReader): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        var count = 0
        while (count < 100) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                headers[line.substring(0, colonIndex).trim().lowercase()] =
                    line.substring(colonIndex + 1).trim()
            }
            count++
        }
        return headers
    }

    private fun handleHealth(socket: Socket) {
        val response = buildJsonObject { put("status", "ok") }
        sendResponse(socket, 200, response.toString())
    }

    private fun handleInfo(socket: Socket) {
        val response = buildJsonObject {
            put("deviceName", Build.MODEL)
            put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("androidVersion", Build.VERSION.RELEASE)
            put("gatewayVersion", "1.0.0")
            put("mcpServices", buildJsonArray {
                for ((name, port) in mcpServerPorts) {
                    add(buildJsonObject {
                        put("name", name)
                        put("port", port)
                    })
                }
            })
        }
        sendResponse(socket, 200, response.toString())
    }

    private suspend fun handleMcp(socket: Socket, headers: Map<String, String>, body: String) {
        val authHeader = headers["authorization"]
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendResponse(socket, 401, buildErrorJson("Missing or invalid Authorization header"))
            return
        }

        val token = authHeader.removePrefix("Bearer ")
        if (!pairingManager.validateToken(token)) {
            sendResponse(socket, 403, buildErrorJson("Invalid token"))
            return
        }

        val requestJson = try {
            json.decodeFromString<JsonObject>(body)
        } catch (e: Exception) {
            sendResponse(socket, 400, buildErrorJson("Invalid JSON body"))
            return
        }

        val serverName = requestJson["server"]?.jsonPrimitive?.content
        val mcpBody = requestJson["body"]?.jsonObject

        if (serverName == null || mcpBody == null) {
            sendResponse(socket, 400, buildErrorJson("Missing 'server' or 'body' field"))
            return
        }

        val port = mcpServerPorts[serverName]
        if (port == null) {
            sendResponse(socket, 404, buildErrorJson("Unknown MCP server: $serverName"))
            return
        }

        val mcpResponse = proxyToMcpServer(port, mcpBody)
        sendResponse(socket, 200, mcpResponse)
    }

    private suspend fun proxyToMcpServer(port: Int, body: JsonObject): String =
        withContext(Dispatchers.IO) {
            val url = URL("http://127.0.0.1:$port/mcp")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("MCP-Protocol-Version", "2025-03-26")
                conn.doOutput = true
                conn.connectTimeout = 10_000
                conn.readTimeout = 60_000

                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                BufferedReader(InputStreamReader(stream)).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }

    private fun handlePair(socket: Socket, body: String) {
        val requestJson = try {
            json.decodeFromString<JsonObject>(body)
        } catch (e: Exception) {
            sendResponse(socket, 400, buildErrorJson("Invalid JSON body"))
            return
        }

        val code = requestJson["code"]?.jsonPrimitive?.content
        val deviceName = requestJson["deviceName"]?.jsonPrimitive?.content

        if (code == null || deviceName == null) {
            sendResponse(socket, 400, buildErrorJson("Missing 'code' or 'deviceName' field"))
            return
        }

        val device = pairingManager.attemptPairing(code, deviceName)
        if (device == null) {
            sendResponse(socket, 403, buildErrorJson("Invalid or expired pairing code"))
            return
        }

        val response = buildJsonObject {
            put("token", device.token)
            put("deviceName", Build.MODEL)
            put("deviceId", device.id)
        }
        sendResponse(socket, 200, response.toString())
    }

    private fun sendResponse(socket: Socket, statusCode: Int, body: String) {
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            413 -> "Payload Too Large"
            414 -> "URI Too Long"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            503 -> "Service Unavailable"
            else -> "Unknown"
        }

        val responseBytes = body.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${responseBytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }

        socket.getOutputStream().apply {
            write(header.toByteArray(Charsets.UTF_8))
            write(responseBytes)
            flush()
        }
    }

    private fun sendCorsPreflightResponse(socket: Socket) {
        val header = buildString {
            append("HTTP/1.1 204 No Content\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type, Authorization\r\n")
            append("Access-Control-Max-Age: 86400\r\n")
            append("Content-Length: 0\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        socket.getOutputStream().apply {
            write(header.toByteArray(Charsets.UTF_8))
            flush()
        }
    }

    private fun buildErrorJson(message: String): String {
        return buildJsonObject { put("error", message) }.toString()
    }
}
