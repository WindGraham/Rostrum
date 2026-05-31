package com.rostrum.core.plugin.rpc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Messenger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 插件RPC客户端
 * 
 * 主程序使用此类连接到插件服务
 */
class PluginRPCClient(
    private val context: Context,
    private val pluginPackageName: String,
    private val pluginServiceName: String
) {
    private val rpcChannel = RPCChannel()
    private var messenger: Messenger? = null
    private var isBound = false
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            messenger = Messenger(service)
            rpcChannel.connect(messenger!!)
            isBound = true
            _connectionState.value = ConnectionState.CONNECTED
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            messenger = null
            rpcChannel.disconnect()
            isBound = false
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }
    
    /**
     * 连接到插件服务
     */
    fun connect() {
        if (isBound) return
        
        val intent = Intent().apply {
            // 设置Action用于RPC绑定
            action = "com.rostrum.plugin.RPC"
            component = ComponentName(pluginPackageName, pluginServiceName)
        }
        
        val bound = context.bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        
        if (!bound) {
            _connectionState.value = ConnectionState.ERROR
        } else {
            _connectionState.value = ConnectionState.CONNECTING
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
        rpcChannel.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
    }
    
    /**
     * 调用RPC方法
     */
    suspend fun call(method: String, params: Map<String, String> = emptyMap()): String {
        if (!isBound) {
            throw RPCException("Not connected to plugin service")
        }
        return rpcChannel.send(method, params)
    }
    
    /**
     * 连接状态
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }
}

