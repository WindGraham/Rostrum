package com.rostrum.core.plugin.ipc

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.rostrum.core.plugin.loader.PluginLoader
import com.rostrum.extension.api.Extension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * 插件进程服务
 * 
 * 运行在独立的插件进程中，负责加载和运行插件
 * 
 * 注意：需要在 AndroidManifest.xml 中配置为独立进程
 */
class PluginProcessService : Service() {
    private val TAG = "PluginProcessService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 已加载的插件
    private val loadedPlugins = mutableMapOf<String, LoadedPluginInfo>()
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PluginProcessService created in process: ${android.os.Process.myPid()}")
        
        // 连接到主进程的 ExtensionHostService
        connectToHostService()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pluginId = intent?.getStringExtra("plugin_id")
        val pluginDir = intent?.getStringExtra("plugin_dir")
        
        if (pluginId != null && pluginDir != null) {
            serviceScope.launch {
                loadAndActivatePlugin(pluginId, File(pluginDir))
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    /**
     * 连接到主进程的 ExtensionHostService
     */
    private fun connectToHostService() {
        serviceScope.launch {
            val client = ExtensionHostClient(this@PluginProcessService)
            val result = client.connect()
            result.onSuccess {
                Log.d(TAG, "Connected to ExtensionHostService")
            }.onFailure { error ->
                Log.e(TAG, "Failed to connect to ExtensionHostService", error)
            }
        }
    }
    
    /**
     * 加载并激活插件
     */
    private suspend fun loadAndActivatePlugin(pluginId: String, pluginDir: File) {
        try {
            Log.d(TAG, "Loading plugin: $pluginId from $pluginDir")
            
            // 1. 加载插件
            val pluginLoader = PluginLoader(this)
            val loadResult = pluginLoader.loadPlugin(pluginDir)
            
            val loadedPlugin = loadResult.getOrElse { error ->
                Log.e(TAG, "Failed to load plugin", error)
                return
            }
            
            // 2. 创建 ExtensionContext（使用代理）
            val client = ExtensionHostClient(this)
            client.connect().getOrElse {
                Log.e(TAG, "Failed to connect to host service", it)
                return
            }
            
            val extensionPath = pluginDir.absolutePath
            val storagePath = File(filesDir, "plugins/$pluginId").absolutePath
            val globalStoragePath = File(filesDir, "plugins/global").absolutePath
            
            val extensionContext = com.rostrum.extension.api.proxy.ExtensionContextProxy(
                client = client,
                appContext = this,
                extensionPath = extensionPath,
                storagePath = storagePath,
                globalStoragePath = globalStoragePath
            )
            
            // 3. 激活插件
            loadedPlugin.instance.activate(extensionContext)
            
            // 4. 保存插件信息
            loadedPlugins[pluginId] = LoadedPluginInfo(
                pluginId = pluginId,
                loadedPlugin = loadedPlugin,
                extensionContext = extensionContext
            )
            
            Log.d(TAG, "Plugin activated: $pluginId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load and activate plugin: $pluginId", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 停用所有插件
        loadedPlugins.values.forEach { info ->
            try {
                kotlinx.coroutines.runBlocking {
                    info.loadedPlugin.instance.deactivate()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to deactivate plugin: ${info.pluginId}", e)
            }
        }
        
        loadedPlugins.clear()
        Log.d(TAG, "PluginProcessService destroyed")
    }
}

/**
 * 已加载的插件信息
 */
data class LoadedPluginInfo(
    val pluginId: String,
    val loadedPlugin: com.rostrum.core.plugin.loader.LoadedPlugin,
    val extensionContext: com.rostrum.extension.api.ExtensionContext
)

