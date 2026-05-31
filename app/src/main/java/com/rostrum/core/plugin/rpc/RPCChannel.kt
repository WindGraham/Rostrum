package com.rostrum.core.plugin.rpc

import android.os.Message
import android.os.Messenger
import android.os.Handler
import android.os.IBinder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * RPC消息格式
 */
@Serializable
data class RPCMessage(
    val id: String,
    val method: String,
    val params: Map<String, String> = emptyMap(),
    val result: String? = null,
    val error: RPCError? = null
)

/**
 * RPC错误
 */
@Serializable
data class RPCError(
    val code: Int,
    val message: String,
    val data: String? = null
)

/**
 * RPC通信通道
 * 
 * 用于主程序与插件之间的进程间通信
 */
class RPCChannel {
    private val pendingRequests = mutableMapOf<String, Channel<RPCMessage>>()
    val messageHandlers = mutableMapOf<String, suspend (Map<String, String>) -> String>()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private var messenger: Messenger? = null
    private val replyMessenger = Messenger(ReplyHandler())
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * 连接到插件进程
     */
    fun connect(messenger: Messenger) {
        this.messenger = messenger
        _isConnected.value = true
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        messenger = null
        _isConnected.value = false
        pendingRequests.clear()
    }
    
    /**
     * 发送RPC请求
     */
    suspend fun send(method: String, params: Map<String, String> = emptyMap()): String {
        val messageId = generateMessageId()
        val message = RPCMessage(
            id = messageId,
            method = method,
            params = params
        )
        
        val responseChannel = Channel<RPCMessage>(Channel.UNLIMITED)
        pendingRequests[messageId] = responseChannel
        
        try {
            val jsonMessage = json.encodeToString(RPCMessage.serializer(), message)
            val androidMessage = Message.obtain(null, MSG_RPC_REQUEST).apply {
                replyTo = replyMessenger
                data = android.os.Bundle().apply {
                    putString("message", jsonMessage)
                }
            }
            
            messenger?.send(androidMessage) ?: throw RPCException("Not connected")
            
            // 等待响应
            val response = responseChannel.receive()
            pendingRequests.remove(messageId)
            
            if (response.error != null) {
                throw RPCException(
                    response.error.message,
                    response.error.code
                )
            }
            
            return response.result ?: ""
        } catch (e: Exception) {
            pendingRequests.remove(messageId)
            throw RPCException("RPC call failed: ${e.message}", -1)
        }
    }
    
    /**
     * 注册消息处理器
     */
    fun registerHandler(method: String, handler: suspend (Map<String, String>) -> String) {
        messageHandlers[method] = handler
    }
    
    /**
     * 获取消息处理器（内部使用）
     */
    internal fun getHandler(method: String): (suspend (Map<String, String>) -> String)? {
        return messageHandlers[method]
    }
    
    /**
     * 处理收到的RPC请求
     */
    private suspend fun handleRequest(message: RPCMessage): RPCMessage {
        val handler = messageHandlers[message.method]
            ?: return message.copy(
                error = RPCError(
                    code = -32601,
                    message = "Method not found: ${message.method}"
                )
            )
        
        return try {
            val result = handler(message.params)
            message.copy(result = result)
        } catch (e: Exception) {
            message.copy(
                error = RPCError(
                    code = -32603,
                    message = "Internal error: ${e.message}"
                )
            )
        }
    }
    
    /**
     * 处理收到的RPC响应
     */
    fun handleResponse(message: RPCMessage) {
        val channel = pendingRequests[message.id]
        channel?.trySend(message)
    }
    
    /**
     * 处理收到的消息
     */
    private fun handleMessage(jsonMessage: String, isRequest: Boolean) {
        try {
            val message = json.decodeFromString<RPCMessage>(jsonMessage)
            if (isRequest) {
                // 异步处理请求
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
                    val response = handleRequest(message)
                    sendResponse(response)
                }
            } else {
                handleResponse(message)
            }
        } catch (e: Exception) {
            android.util.Log.e("RPCChannel", "Failed to handle message: ${e.message}", e)
        }
    }
    
    /**
     * 发送响应
     */
    private fun sendResponse(response: RPCMessage) {
        try {
            val jsonResponse = json.encodeToString(RPCMessage.serializer(), response)
            val androidMessage = Message.obtain(null, MSG_RPC_RESPONSE).apply {
                data = android.os.Bundle().apply {
                    putString("message", jsonResponse)
                }
            }
            messenger?.send(androidMessage)
        } catch (e: Exception) {
            android.util.Log.e("RPCChannel", "Failed to send response: ${e.message}", e)
        }
    }
    
    /**
     * 响应处理器
     */
    private inner class ReplyHandler : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_RPC_REQUEST -> {
                    val jsonMessage = msg.data.getString("message") ?: return
                    handleMessage(jsonMessage, isRequest = true)
                }
                MSG_RPC_RESPONSE -> {
                    val jsonMessage = msg.data.getString("message") ?: return
                    handleMessage(jsonMessage, isRequest = false)
                }
            }
        }
    }
    
    private fun generateMessageId(): String {
        return java.util.UUID.randomUUID().toString()
    }
    
    companion object {
        const val MSG_RPC_REQUEST = 1
        const val MSG_RPC_RESPONSE = 2
    }
}

/**
 * RPC异常
 */
class RPCException(message: String, val code: Int = -1) : Exception(message)

