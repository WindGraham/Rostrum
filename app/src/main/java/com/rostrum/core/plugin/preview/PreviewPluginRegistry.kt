package com.rostrum.core.plugin.preview

import android.content.Context
import android.util.Log
import com.rostrum.core.plugin.PluginContext
import com.rostrum.core.plugin.providers.FilePreviewPlugin
import com.rostrum.core.plugin.models.FileInfo
import com.rostrum.core.plugin.impl.PluginManagerImpl
import com.rostrum.core.plugin.impl.UIRegistryImpl
import com.rostrum.core.plugin.impl.ManagedPlugin
import com.rostrum.core.plugin.impl.PluginSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 预览插件注册器
 * 
 * 使用新的 PluginManagerImpl 管理插件
 * 
 * 架构：
 * - PluginManagerImpl 负责插件的加载、激活、停用等生命周期管理
 * - UIRegistryImpl 负责预览插件的注册和查找
 * - PluginStateManager 负责插件启用/禁用状态的持久化
 */
object PreviewPluginRegistry {
    private const val TAG = "PreviewPluginRegistry"
    
    // 插件管理器实例（单例）
    private var pluginManager: PluginManagerImpl? = null
    
    // 标记是否已初始化
    @Volatile
    private var initialized = false
    
    /**
     * 获取插件管理器
     */
    fun getPluginManager(): PluginManagerImpl? = pluginManager
    
    /**
     * 获取全局UI注册表
     */
    fun getGlobalUIRegistry(): UIRegistryImpl {
        return pluginManager?.getUIRegistry() ?: UIRegistryImpl()
    }
    
    /**
     * 查找最适合预览指定文件的插件
     */
    fun findPreviewPlugin(fileInfo: FileInfo): FilePreviewPlugin? {
        return getGlobalUIRegistry().findPreviewPlugin(fileInfo)
    }
    
    /**
     * 获取所有托管插件信息
     */
    fun getManagedPlugins(): List<ManagedPlugin> {
        return pluginManager?.getManagedPlugins() ?: emptyList()
    }
    
    /**
     * 初始化插件系统
     * 
     * 使用新的 PluginManagerImpl 加载内置插件
     * 
     * @param context Android Context
     */
    suspend fun initializePlugins(context: Context) {
        if (initialized) return
        
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing plugin system with PluginManagerImpl...")
                
                // 创建插件管理器
                val manager = PluginManagerImpl(context)
                pluginManager = manager
                
                // 加载内置插件（使用新的插件管理系统）
                val builtinPlugins = manager.loadBuiltinPlugins()
                
                Log.d(TAG, "Loaded ${builtinPlugins.size} builtin plugins:")
                builtinPlugins.forEach { plugin ->
                    Log.d(TAG, "  - ${plugin.id} (${plugin.name}) v${plugin.version}")
                }
                
                initialized = true
                Log.d(TAG, "Plugin system initialized successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize plugin system", e)
            }
        }
    }
    
    /**
     * 启用插件
     */
    suspend fun enablePlugin(pluginId: String): Result<Unit> {
        return pluginManager?.enablePlugin(pluginId) 
            ?: Result.failure(IllegalStateException("Plugin manager not initialized"))
    }
    
    /**
     * 禁用插件
     */
    suspend fun disablePlugin(pluginId: String): Result<Unit> {
        return pluginManager?.disablePlugin(pluginId)
            ?: Result.failure(IllegalStateException("Plugin manager not initialized"))
    }
    
    /**
     * 检查插件管理器是否已初始化
     */
    fun isInitialized(): Boolean = initialized
}
