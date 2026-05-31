package com.rostrum.core.domain.usecase.plugin

import com.rostrum.core.domain.plugin.model.Plugin
import com.rostrum.core.domain.plugin.model.PluginState
import com.rostrum.core.domain.plugin.model.PluginType
import com.rostrum.core.domain.plugin.repository.PluginRepository
import com.rostrum.core.domain.usecase.FlowUseCase
import com.rostrum.core.domain.usecase.NoParamsUseCase
import kotlinx.coroutines.flow.Flow

/**
 * 获取所有插件用例
 */
class GetPluginsUseCase(
    private val repository: PluginRepository
) : NoParamsUseCase<List<Plugin>> {
    
    override suspend fun invoke(): List<Plugin> {
        return repository.getAllPlugins()
    }
}

/**
 * 观察插件列表变化用例
 */
class ObservePluginsUseCase(
    private val repository: PluginRepository
) : FlowUseCase<Unit, List<Plugin>> {
    
    override fun invoke(params: Unit): Flow<List<Plugin>> {
        return repository.observePlugins()
    }
}

/**
 * 根据 ID 获取插件用例
 */
class GetPluginByIdUseCase(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(id: String): Plugin? {
        return repository.getPluginById(id)
    }
}

/**
 * 根据类型获取插件用例
 */
class GetPluginsByTypeUseCase(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(type: PluginType): List<Plugin> {
        return repository.getPluginsByType(type)
    }
}

/**
 * 根据状态获取插件用例
 */
class GetPluginsByStateUseCase(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(state: PluginState): List<Plugin> {
        return repository.getPluginsByState(state)
    }
}
