package com.rostrum.core.plugin.activation

import android.util.Log
import com.rostrum.core.plugin.contribution.PluginManifest
import com.rostrum.core.plugin.loader.LoadedPlugin
import com.rostrum.extension.api.Extension
import com.rostrum.extension.api.ExtensionContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 激活管理器
 * 
 * 负责管理插件的激活事件和生命周期
 */
class ActivationManager {
    private val TAG = "ActivationManager"
    
    // 已加载但未激活的插件
    private val loadedPlugins = mutableMapOf<String, LoadedPlugin>()
    
    // 已激活的插件
    private val activatedPlugins = mutableMapOf<String, ActivatedPlugin>()
    
    // 激活状态流
    private val _activationState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val activationState: StateFlow<Map<String, Boolean>> = _activationState.asStateFlow()
    
    /**
     * 注册已加载的插件
     * 
     * @param plugin 已加载的插件
     */
    fun registerLoadedPlugin(plugin: LoadedPlugin) {
        loadedPlugins[plugin.manifest.id] = plugin
        Log.d(TAG, "Registered loaded plugin: ${plugin.manifest.id}")
    }
    
    /**
     * 触发激活事件
     * 
     * @param event 激活事件类型（如 "onFile:*.pdf"）
     * @param context 插件上下文
     * @return 激活的插件列表
     */
    suspend fun triggerActivationEvent(
        event: String,
        context: ExtensionContext
    ): List<ActivatedPlugin> {
        val activated = mutableListOf<ActivatedPlugin>()
        
        // 查找匹配的插件
        for ((pluginId, loadedPlugin) in loadedPlugins) {
            if (matchesActivationEvent(loadedPlugin.manifest, event)) {
                val activatedPlugin = activatePlugin(loadedPlugin, context)
                if (activatedPlugin != null) {
                    activated.add(activatedPlugin)
                }
            }
        }
        
        return activated
    }
    
    /**
     * 激活插件
     * 
     * @param loadedPlugin 已加载的插件
     * @param context 插件上下文
     * @return 激活的插件，失败返回 null
     */
    suspend fun activatePlugin(
        loadedPlugin: LoadedPlugin,
        context: ExtensionContext
    ): ActivatedPlugin? {
        val pluginId = loadedPlugin.manifest.id
        
        // 如果已经激活，直接返回
        if (activatedPlugins.containsKey(pluginId)) {
            return activatedPlugins[pluginId]
        }
        
        return try {
            Log.d(TAG, "Activating plugin: $pluginId")
            
            // 调用插件的 activate 方法
            loadedPlugin.instance.activate(context)
            
            val activatedPlugin = ActivatedPlugin(
                loadedPlugin = loadedPlugin,
                context = context,
                isActive = true
            )
            
            // 从加载列表移除，添加到激活列表
            loadedPlugins.remove(pluginId)
            activatedPlugins[pluginId] = activatedPlugin
            
            // 更新状态
            updateActivationState()
            
            Log.d(TAG, "Successfully activated plugin: $pluginId")
            activatedPlugin
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to activate plugin: $pluginId", e)
            null
        }
    }
    
    /**
     * 停用插件
     * 
     * @param pluginId 插件ID
     */
    suspend fun deactivatePlugin(pluginId: String) {
        val activatedPlugin = activatedPlugins[pluginId] ?: return
        
        try {
            Log.d(TAG, "Deactivating plugin: $pluginId")
            
            // 调用插件的 deactivate 方法
            activatedPlugin.loadedPlugin.instance.deactivate()
            
            // 清理订阅
            activatedPlugin.context.subscriptions.forEach { it.dispose() }
            activatedPlugin.context.subscriptions.clear()
            
            // 从激活列表移除，重新添加到加载列表
            activatedPlugins.remove(pluginId)
            loadedPlugins[pluginId] = activatedPlugin.loadedPlugin
            
            // 更新状态
            updateActivationState()
            
            Log.d(TAG, "Successfully deactivated plugin: $pluginId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deactivate plugin: $pluginId", e)
        }
    }
    
    /**
     * 检查激活事件是否匹配
     * 
     * @param manifest 插件清单
     * @param event 激活事件
     * @return 是否匹配
     */
    private fun matchesActivationEvent(manifest: PluginManifest, event: String): Boolean {
        val activationEvents = manifest.activationEvents
        
        // 如果没有定义激活事件，默认不激活
        if (activationEvents.isEmpty()) {
            return false
        }
        
        // 检查是否匹配任何激活事件
        for (activationEvent in activationEvents) {
            if (matchesEventPattern(activationEvent, event)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * 检查事件模式是否匹配
     * 
     * 支持的模式：
     * - "onStartup": 应用启动时
     * - "onFile:*": 打开任何文件时
     * - "onFile:*.pdf": 打开 PDF 文件时
     * - "onCommand:commandId": 执行特定命令时
     * - "onLanguage:languageId": 打开特定语言文件时
     */
    private fun matchesEventPattern(pattern: String, event: String): Boolean {
        // 精确匹配
        if (pattern == event) {
            return true
        }
        
        // 通配符匹配
        when {
            pattern == "onStartup" && event == "onStartup" -> return true
            pattern == "onFile:*" && event.startsWith("onFile:") -> return true
            pattern.startsWith("onFile:") && event.startsWith("onFile:") -> {
                // 提取文件扩展名模式
                val patternExt = pattern.substringAfter("onFile:")
                val eventExt = event.substringAfter("onFile:")
                
                // 支持通配符匹配
                if (patternExt.contains("*")) {
                    val regex = patternExt.replace("*", ".*").toRegex()
                    return regex.matches(eventExt)
                }
                
                return patternExt == eventExt
            }
            pattern.startsWith("onCommand:") && event.startsWith("onCommand:") -> {
                val patternCmd = pattern.substringAfter("onCommand:")
                val eventCmd = event.substringAfter("onCommand:")
                return patternCmd == eventCmd
            }
            pattern.startsWith("onLanguage:") && event.startsWith("onLanguage:") -> {
                val patternLang = pattern.substringAfter("onLanguage:")
                val eventLang = event.substringAfter("onLanguage:")
                return patternLang == eventLang
            }
        }
        
        return false
    }
    
    /**
     * 更新激活状态
     */
    private fun updateActivationState() {
        val state = mutableMapOf<String, Boolean>()
        
        // 已加载但未激活的插件
        loadedPlugins.keys.forEach { state[it] = false }
        
        // 已激活的插件
        activatedPlugins.keys.forEach { state[it] = true }
        
        _activationState.value = state
    }
    
    /**
     * 获取已激活的插件
     */
    fun getActivatedPlugins(): List<ActivatedPlugin> {
        return activatedPlugins.values.toList()
    }
    
    /**
     * 获取已加载但未激活的插件
     */
    fun getLoadedPlugins(): List<LoadedPlugin> {
        return loadedPlugins.values.toList()
    }
    
    /**
     * 检查插件是否已激活
     */
    fun isActivated(pluginId: String): Boolean {
        return activatedPlugins.containsKey(pluginId)
    }
}

/**
 * 已激活的插件
 */
data class ActivatedPlugin(
    /**
     * 已加载的插件
     */
    val loadedPlugin: LoadedPlugin,
    
    /**
     * 插件上下文
     */
    val context: ExtensionContext,
    
    /**
     * 是否处于激活状态
     */
    val isActive: Boolean
)

