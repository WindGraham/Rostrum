package com.rostrum.core.data.repository

import android.content.Context
import android.util.Log
import com.rostrum.core.domain.plugin.model.Plugin
import com.rostrum.core.domain.plugin.model.PluginState
import com.rostrum.core.domain.plugin.model.PluginType
import com.rostrum.core.domain.plugin.repository.PluginRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 插件仓储实现
 * 
 * 使用内存存储和 StateFlow 实现插件管理
 */
class PluginRepositoryImpl(
    private val context: Context
) : PluginRepository {
    
    companion object {
        private const val TAG = "PluginRepositoryImpl"
    }
    
    // 插件列表（使用 StateFlow 支持观察）
    private val _plugins = MutableStateFlow<List<Plugin>>(emptyList())
    
    // 互斥锁保护并发访问
    private val mutex = Mutex()
    
    override suspend fun getAllPlugins(): List<Plugin> {
        return _plugins.value
    }
    
    override suspend fun getPluginById(id: String): Plugin? {
        return _plugins.value.find { it.id == id }
    }
    
    override suspend fun getPluginsByType(type: PluginType): List<Plugin> {
        return _plugins.value.filter { it.type == type }
    }
    
    override suspend fun getPluginsByState(state: PluginState): List<Plugin> {
        return _plugins.value.filter { it.state == state }
    }
    
    override fun observePlugins(): Flow<List<Plugin>> {
        return _plugins
    }
    
    override fun observePlugin(id: String): Flow<Plugin?> {
        return _plugins.map { plugins ->
            plugins.find { it.id == id }
        }
    }
    
    override suspend fun installPlugin(plugin: Plugin): Result<Unit> {
        return mutex.withLock {
            try {
                // 检查是否已存在
                if (_plugins.value.any { it.id == plugin.id }) {
                    return@withLock Result.failure(IllegalStateException("插件已存在: ${plugin.id}"))
                }
                
                // 添加插件
                _plugins.value = _plugins.value + plugin
                Log.d(TAG, "Installed plugin: ${plugin.id}")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install plugin: ${plugin.id}", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun uninstallPlugin(id: String): Result<Unit> {
        return mutex.withLock {
            try {
                val plugin = _plugins.value.find { it.id == id }
                    ?: return@withLock Result.failure(IllegalStateException("插件不存在: $id"))
                
                // 检查是否可以卸载
                if (plugin.type == PluginType.BUILTIN) {
                    return@withLock Result.failure(IllegalStateException("不能卸载内置插件"))
                }
                
                // 移除插件
                _plugins.value = _plugins.value.filter { it.id != id }
                Log.d(TAG, "Uninstalled plugin: $id")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to uninstall plugin: $id", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun updatePluginState(id: String, state: PluginState): Result<Unit> {
        return mutex.withLock {
            try {
                val index = _plugins.value.indexOfFirst { it.id == id }
                if (index == -1) {
                    return@withLock Result.failure(IllegalStateException("插件不存在: $id"))
                }
                
                // 更新状态
                val updatedPlugins = _plugins.value.toMutableList()
                updatedPlugins[index] = updatedPlugins[index].copy(state = state)
                _plugins.value = updatedPlugins
                
                Log.d(TAG, "Updated plugin state: $id -> $state")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update plugin state: $id", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun setPluginEnabled(id: String, enabled: Boolean): Result<Unit> {
        return mutex.withLock {
            try {
                val index = _plugins.value.indexOfFirst { it.id == id }
                if (index == -1) {
                    return@withLock Result.failure(IllegalStateException("插件不存在: $id"))
                }
                
                // 更新启用状态
                val updatedPlugins = _plugins.value.toMutableList()
                updatedPlugins[index] = updatedPlugins[index].copy(enabled = enabled)
                _plugins.value = updatedPlugins
                
                Log.d(TAG, "Set plugin enabled: $id -> $enabled")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set plugin enabled: $id", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * 注册内置插件（在应用启动时调用）
     */
    suspend fun registerBuiltinPlugins(plugins: List<Plugin>) {
        mutex.withLock {
            _plugins.value = plugins
            Log.d(TAG, "Registered ${plugins.size} builtin plugins")
        }
    }
}
