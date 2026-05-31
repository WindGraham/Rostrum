package com.rostrum.core.domain.plugin.service

import com.rostrum.core.domain.plugin.model.ActivationEvent
import com.rostrum.core.domain.plugin.model.Plugin
import com.rostrum.core.domain.plugin.model.PluginState
import com.rostrum.core.domain.plugin.policy.PluginActivationPolicy
import com.rostrum.core.domain.plugin.repository.PluginRepository
import kotlinx.coroutines.flow.Flow

/**
 * 插件服务接口
 * 
 * 定义插件生命周期管理的业务逻辑
 */
interface PluginService {
    
    /**
     * 初始化插件系统
     */
    suspend fun initialize()
    
    /**
     * 触发激活事件
     * 
     * @param event 激活事件
     * @return 被激活的插件列表
     */
    suspend fun triggerActivationEvent(event: ActivationEvent): List<Plugin>
    
    /**
     * 激活插件
     * 
     * @param pluginId 插件 ID
     * @return 激活结果
     */
    suspend fun activatePlugin(pluginId: String): Result<Unit>
    
    /**
     * 停用插件
     * 
     * @param pluginId 插件 ID
     * @return 停用结果
     */
    suspend fun deactivatePlugin(pluginId: String): Result<Unit>
    
    /**
     * 获取所有插件
     */
    fun getAllPlugins(): Flow<List<Plugin>>
    
    /**
     * 获取激活的插件
     */
    fun getActivePlugins(): Flow<List<Plugin>>
    
    /**
     * 销毁插件系统
     */
    suspend fun destroy()
}

/**
 * 插件服务默认实现
 */
class PluginServiceImpl(
    private val repository: PluginRepository,
    private val activationPolicy: PluginActivationPolicy
) : PluginService {
    
    override suspend fun initialize() {
        // 加载所有插件
        val plugins = repository.getAllPlugins()
        
        // 按优先级排序
        val sortedPlugins = plugins.sortedBy { activationPolicy.getActivationPriority(it) }
        
        // 触发启动激活事件
        val startupEvent = ActivationEvent(
            type = com.rostrum.core.domain.plugin.model.ActivationEventType.ON_STARTUP
        )
        
        for (plugin in sortedPlugins) {
            if (activationPolicy.shouldActivate(plugin, startupEvent)) {
                activatePlugin(plugin.id)
            }
        }
    }
    
    override suspend fun triggerActivationEvent(event: ActivationEvent): List<Plugin> {
        val activatedPlugins = mutableListOf<Plugin>()
        val plugins = repository.getAllPlugins()
        
        for (plugin in plugins) {
            if (activationPolicy.shouldActivate(plugin, event)) {
                val result = activatePlugin(plugin.id)
                if (result.isSuccess) {
                    repository.getPluginById(plugin.id)?.let {
                        activatedPlugins.add(it)
                    }
                }
            }
        }
        
        return activatedPlugins
    }
    
    override suspend fun activatePlugin(pluginId: String): Result<Unit> {
        val plugin = repository.getPluginById(pluginId)
            ?: return Result.failure(IllegalArgumentException("插件不存在: $pluginId"))
        
        val canActivateError = activationPolicy.canActivate(plugin)
        if (canActivateError != null) {
            return Result.failure(IllegalStateException(canActivateError))
        }
        
        // 更新状态为激活中
        repository.updatePluginState(pluginId, PluginState.ACTIVATING)
        
        return try {
            // 执行激活逻辑（具体实现由子类完成）
            doActivate(plugin)
            
            // 更新状态为已激活
            repository.updatePluginState(pluginId, PluginState.ACTIVE)
            Result.success(Unit)
        } catch (e: Exception) {
            // 更新状态为错误
            repository.updatePluginState(pluginId, PluginState.ERROR)
            Result.failure(e)
        }
    }
    
    override suspend fun deactivatePlugin(pluginId: String): Result<Unit> {
        val plugin = repository.getPluginById(pluginId)
            ?: return Result.failure(IllegalArgumentException("插件不存在: $pluginId"))
        
        if (plugin.state != PluginState.ACTIVE) {
            return Result.failure(IllegalStateException("插件未激活"))
        }
        
        // 更新状态为停用中
        repository.updatePluginState(pluginId, PluginState.DEACTIVATING)
        
        return try {
            // 执行停用逻辑
            doDeactivate(plugin)
            
            // 更新状态为已停用
            repository.updatePluginState(pluginId, PluginState.DEACTIVATED)
            Result.success(Unit)
        } catch (e: Exception) {
            // 恢复状态
            repository.updatePluginState(pluginId, PluginState.ACTIVE)
            Result.failure(e)
        }
    }
    
    override fun getAllPlugins(): Flow<List<Plugin>> {
        return repository.observePlugins()
    }
    
    override fun getActivePlugins(): Flow<List<Plugin>> {
        return kotlinx.coroutines.flow.flow {
            repository.observePlugins().collect { plugins ->
                emit(plugins.filter { it.state == PluginState.ACTIVE })
            }
        }
    }
    
    override suspend fun destroy() {
        // 停用所有激活的插件
        val activePlugins = repository.getPluginsByState(PluginState.ACTIVE)
        for (plugin in activePlugins) {
            deactivatePlugin(plugin.id)
        }
    }
    
    /**
     * 执行插件激活（子类可覆盖）
     */
    protected open suspend fun doActivate(plugin: Plugin) {
        // 默认实现：无操作
    }
    
    /**
     * 执行插件停用（子类可覆盖）
     */
    protected open suspend fun doDeactivate(plugin: Plugin) {
        // 默认实现：无操作
    }
}
