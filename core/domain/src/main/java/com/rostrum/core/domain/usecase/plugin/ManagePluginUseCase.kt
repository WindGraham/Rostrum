package com.rostrum.core.domain.usecase.plugin

import com.rostrum.core.domain.plugin.model.Plugin
import com.rostrum.core.domain.plugin.model.PluginState
import com.rostrum.core.domain.plugin.repository.PluginRepository
import com.rostrum.core.domain.usecase.UseCase
import com.rostrum.core.domain.usecase.UseCaseResult

/**
 * 激活插件用例
 */
class ActivatePluginUseCase(
    private val repository: PluginRepository
) : UseCase<String, UseCaseResult<Unit>> {
    
    override suspend fun invoke(params: String): UseCaseResult<Unit> {
        return try {
            repository.setPluginEnabled(params, true).getOrThrow()
            repository.updatePluginState(params, PluginState.ACTIVE).getOrThrow()
            UseCaseResult.Success(Unit)
        } catch (e: Exception) {
            UseCaseResult.Error(e)
        }
    }
}

/**
 * 停用插件用例
 */
class DeactivatePluginUseCase(
    private val repository: PluginRepository
) : UseCase<String, UseCaseResult<Unit>> {
    
    override suspend fun invoke(params: String): UseCaseResult<Unit> {
        return try {
            repository.setPluginEnabled(params, false).getOrThrow()
            repository.updatePluginState(params, PluginState.INACTIVE).getOrThrow()
            UseCaseResult.Success(Unit)
        } catch (e: Exception) {
            UseCaseResult.Error(e)
        }
    }
}

/**
 * 安装插件用例
 */
class InstallPluginUseCase(
    private val repository: PluginRepository
) : UseCase<Plugin, UseCaseResult<Unit>> {
    
    override suspend fun invoke(params: Plugin): UseCaseResult<Unit> {
        return try {
            repository.installPlugin(params).getOrThrow()
            UseCaseResult.Success(Unit)
        } catch (e: Exception) {
            UseCaseResult.Error(e)
        }
    }
}

/**
 * 卸载插件用例
 */
class UninstallPluginUseCase(
    private val repository: PluginRepository
) : UseCase<String, UseCaseResult<Unit>> {
    
    override suspend fun invoke(params: String): UseCaseResult<Unit> {
        return try {
            repository.uninstallPlugin(params).getOrThrow()
            UseCaseResult.Success(Unit)
        } catch (e: Exception) {
            UseCaseResult.Error(e)
        }
    }
}
