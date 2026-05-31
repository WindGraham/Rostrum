package com.rostrum.core.plugin.rpc

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Messenger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 插件RPC服务
 * 
 * 用于插件进程与主程序之间的IPC通信
 */
abstract class PluginRPCService : Service() {
    
    protected val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    protected val rpcChannel = RPCChannel()
    
    private var messenger: Messenger? = null
    
    override fun onCreate() {
        super.onCreate()
        messenger = Messenger(PluginMessageHandler())
        setupRPCHandlers()
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return messenger?.binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        rpcChannel.disconnect()
    }
    
    /**
     * 设置RPC处理器
     * 子类实现此方法来注册具体的RPC方法
     */
    protected abstract fun setupRPCHandlers()
    
    /**
     * 插件消息处理器
     */
    private inner class PluginMessageHandler : android.os.Handler() {
        override fun handleMessage(msg: android.os.Message) {
            when (msg.what) {
                RPCChannel.MSG_RPC_REQUEST -> {
                    val jsonMessage = msg.data.getString("message") ?: return
                    handleRPCRequest(jsonMessage, msg.replyTo)
                }
                RPCChannel.MSG_RPC_RESPONSE -> {
                    val jsonMessage = msg.data.getString("message") ?: return
                    handleRPCResponse(jsonMessage)
                }
            }
        }
    }
    
    /**
     * 处理RPC请求
     */
    private fun handleRPCRequest(jsonMessage: String, replyTo: Messenger) {
        // 连接到回复Messenger
        rpcChannel.connect(replyTo)
        
        // 处理消息
        try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val message = json.decodeFromString<RPCMessage>(jsonMessage)
            serviceScope.launch {
                val handler = rpcChannel.messageHandlers[message.method]
                if (handler != null) {
                    try {
                        val result = handler(message.params)
                        val response = message.copy(result = result)
                        sendResponse(response, replyTo)
                    } catch (e: Exception) {
                        val response = message.copy(
                            error = RPCError(
                                code = -32603,
                                message = "Internal error: ${e.message}"
                            )
                        )
                        sendResponse(response, replyTo)
                    }
                } else {
                    val response = message.copy(
                        error = RPCError(
                            code = -32601,
                            message = "Method not found: ${message.method}"
                        )
                    )
                    sendResponse(response, replyTo)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PluginRPCService", "Failed to parse RPC request: ${e.message}", e)
        }
    }
    
    /**
     * 处理RPC响应
     */
    private fun handleRPCResponse(jsonMessage: String) {
        try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val message = json.decodeFromString<RPCMessage>(jsonMessage)
            rpcChannel.handleResponse(message)
        } catch (e: Exception) {
            android.util.Log.e("PluginRPCService", "Failed to handle RPC response: ${e.message}", e)
        }
    }
    
    /**
     * 发送响应
     */
    private fun sendResponse(response: RPCMessage, replyTo: Messenger) {
        try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val jsonResponse = json.encodeToString(RPCMessage.serializer(), response)
            val androidMessage = android.os.Message.obtain(null, RPCChannel.MSG_RPC_RESPONSE).apply {
                data = android.os.Bundle().apply {
                    putString("message", jsonResponse)
                }
            }
            replyTo.send(androidMessage)
        } catch (e: Exception) {
            android.util.Log.e("PluginRPCService", "Failed to send response: ${e.message}", e)
        }
    }
}

