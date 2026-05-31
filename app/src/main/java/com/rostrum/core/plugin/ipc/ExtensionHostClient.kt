package com.rostrum.core.plugin.ipc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
// IExtensionHostService will be generated from AIDL
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Extension Host Client
 * 
 * 运行在插件进程中，连接到主进程的 ExtensionHostService
 */
class ExtensionHostClient(private val context: Context) {
    private val TAG = "ExtensionHostClient"
    
    private var service: com.rostrum.core.plugin.ipc.IExtensionHostService? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Service connected")
            service = com.rostrum.core.plugin.ipc.IExtensionHostService.Stub.asInterface(binder)
            isBound = true
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            service = null
            isBound = false
        }
    }
    
    /**
     * 连接到 Extension Host Service
     */
    suspend fun connect(): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            val intent = Intent(context, ExtensionHostService::class.java)
            val bound = context.bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
            
            if (bound) {
                continuation.resume(Result.success(Unit))
            } else {
                continuation.resume(Result.failure(
                    IllegalStateException("Failed to bind to ExtensionHostService")
                ))
            }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
            service = null
        }
    }
    
    /**
     * 检查服务是否可用
     */
    suspend fun ping(): Boolean {
        return try {
            service?.ping() ?: false
        } catch (e: RemoteException) {
            Log.e(TAG, "Ping failed", e)
            false
        }
    }
    
    /**
     * 获取服务接口（用于 API 代理）
     */
    fun getService(): com.rostrum.core.plugin.ipc.IExtensionHostService? {
        return service
    }
    
    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean {
        return isBound && service != null
    }
}

