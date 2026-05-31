package com.rostrum.core.domain.plugin.repository

import com.rostrum.core.domain.plugin.model.Plugin
import com.rostrum.core.domain.plugin.model.PluginState
import com.rostrum.core.domain.plugin.model.PluginType
import kotlinx.coroutines.flow.Flow

/**
 * 插件仓储接口
 * 
 * 定义插件相关的数据访问契约
 */
interface PluginRepository {
    
    /**
     * 获取所有插件
     */
    suspend fun getAllPlugins(): List<Plugin>
    
    /**
     * 根据 ID 获取插件
     */
    suspend fun getPluginById(id: String): Plugin?
    
    /**
     * 根据类型获取插件
     */
    suspend fun getPluginsByType(type: PluginType): List<Plugin>
    
    /**
     * 根据状态获取插件
     */
    suspend fun getPluginsByState(state: PluginState): List<Plugin>
    
    /**
     * 观察插件变化
     */
    fun observePlugins(): Flow<List<Plugin>>
    
    /**
     * 观察单个插件变化
     */
    fun observePlugin(id: String): Flow<Plugin?>
    
    /**
     * 安装插件
     */
    suspend fun installPlugin(plugin: Plugin): Result<Unit>
    
    /**
     * 卸载插件
     */
    suspend fun uninstallPlugin(id: String): Result<Unit>
    
    /**
     * 更新插件状态
     */
    suspend fun updatePluginState(id: String, state: PluginState): Result<Unit>
    
    /**
     * 启用/禁用插件
     */
    suspend fun setPluginEnabled(id: String, enabled: Boolean): Result<Unit>
}
