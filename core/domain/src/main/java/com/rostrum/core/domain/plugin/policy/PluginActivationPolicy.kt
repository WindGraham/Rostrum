package com.rostrum.core.domain.plugin.policy

import com.rostrum.core.domain.plugin.model.ActivationEvent
import com.rostrum.core.domain.plugin.model.ActivationEventType
import com.rostrum.core.domain.plugin.model.Plugin
import com.rostrum.core.domain.plugin.model.PluginState

/**
 * 插件激活策略接口
 * 
 * 定义插件何时以及如何被激活
 */
interface PluginActivationPolicy {
    
    /**
     * 检查插件是否应该在给定事件下激活
     * 
     * @param plugin 插件
     * @param event 触发事件
     * @return 是否应该激活
     */
    fun shouldActivate(plugin: Plugin, event: ActivationEvent): Boolean
    
    /**
     * 检查插件是否可以被激活
     * 
     * @param plugin 插件
     * @return 如果可以激活返回 null，否则返回拒绝原因
     */
    fun canActivate(plugin: Plugin): String?
    
    /**
     * 获取插件激活优先级
     * 
     * @param plugin 插件
     * @return 优先级（数字越小优先级越高）
     */
    fun getActivationPriority(plugin: Plugin): Int
}

/**
 * 默认插件激活策略
 */
class DefaultPluginActivationPolicy : PluginActivationPolicy {
    
    override fun shouldActivate(plugin: Plugin, event: ActivationEvent): Boolean {
        // 检查插件是否启用
        if (!plugin.enabled) return false
        
        // 检查插件是否已经激活
        if (plugin.state == PluginState.ACTIVE) return false
        
        // 检查激活事件是否匹配
        return plugin.activationEvents.any { pluginEvent ->
            matchActivationEvent(pluginEvent, event)
        }
    }
    
    override fun canActivate(plugin: Plugin): String? {
        if (!plugin.enabled) {
            return "插件已禁用"
        }
        
        if (plugin.state == PluginState.ERROR) {
            return "插件处于错误状态"
        }
        
        if (plugin.state == PluginState.ACTIVATING) {
            return "插件正在激活中"
        }
        
        return null
    }
    
    override fun getActivationPriority(plugin: Plugin): Int {
        // 内置插件优先级最高
        return when (plugin.type) {
            com.rostrum.core.domain.plugin.model.PluginType.BUILTIN -> 0
            com.rostrum.core.domain.plugin.model.PluginType.NATIVE -> 1
            com.rostrum.core.domain.plugin.model.PluginType.VSCODE_COMPATIBLE -> 2
            com.rostrum.core.domain.plugin.model.PluginType.EXTERNAL -> 3
        }
    }
    
    private fun matchActivationEvent(
        pluginEvent: ActivationEvent,
        triggerEvent: ActivationEvent
    ): Boolean {
        if (pluginEvent.type != triggerEvent.type) return false
        
        return when (pluginEvent.type) {
            ActivationEventType.ON_STARTUP -> true
            
            ActivationEventType.ON_LANGUAGE -> {
                pluginEvent.parameter == triggerEvent.parameter ||
                pluginEvent.parameter == "*"
            }
            
            ActivationEventType.ON_COMMAND -> {
                pluginEvent.parameter == triggerEvent.parameter
            }
            
            ActivationEventType.ON_FILE_SYSTEM -> {
                matchGlobPattern(pluginEvent.parameter, triggerEvent.parameter)
            }
            
            ActivationEventType.ON_VIEW -> {
                pluginEvent.parameter == triggerEvent.parameter
            }
            
            ActivationEventType.WORKSPACE_CONTAINS -> {
                matchGlobPattern(pluginEvent.parameter, triggerEvent.parameter)
            }
        }
    }
    
    private fun matchGlobPattern(pattern: String?, value: String?): Boolean {
        if (pattern == null || value == null) return false
        if (pattern == "*") return true
        
        // 简单的 glob 匹配
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
            .toRegex()
        
        return regex.matches(value)
    }
}

/**
 * 懒加载插件激活策略
 * 
 * 尽可能延迟插件激活，减少启动时间
 */
class LazyPluginActivationPolicy : PluginActivationPolicy {
    
    private val defaultPolicy = DefaultPluginActivationPolicy()
    
    override fun shouldActivate(plugin: Plugin, event: ActivationEvent): Boolean {
        // 启动事件时，只激活必要的插件
        if (event.type == ActivationEventType.ON_STARTUP) {
            return plugin.type == com.rostrum.core.domain.plugin.model.PluginType.BUILTIN
        }
        
        return defaultPolicy.shouldActivate(plugin, event)
    }
    
    override fun canActivate(plugin: Plugin): String? {
        return defaultPolicy.canActivate(plugin)
    }
    
    override fun getActivationPriority(plugin: Plugin): Int {
        return defaultPolicy.getActivationPriority(plugin)
    }
}
