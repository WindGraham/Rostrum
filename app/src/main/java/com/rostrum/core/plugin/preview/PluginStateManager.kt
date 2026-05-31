package com.rostrum.core.plugin.preview

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 插件状态管理器
 * 
 * 管理插件的启用/停用状态
 * 使用 Compose State 实现响应式 UI 更新
 */
object PluginStateManager {
    private const val TAG = "PluginStateManager"
    
    // 存储插件启用状态 (pluginId -> enabled)
    private val pluginStates = mutableMapOf<String, Boolean>()
    
    // 状态变化版本（Compose State，会触发重组）
    var stateVersion by mutableIntStateOf(0)
        private set
    
    /**
     * 获取状态版本号（用于触发重组）
     * 在 @Composable 函数中使用时会自动订阅变化
     */
    @Composable
    fun getStateVersion(): Int = stateVersion
    
    /**
     * 非 Composable 版本
     */
    fun getStateVersionValue(): Int = stateVersion
    
    /**
     * 检查插件是否启用
     */
    fun isEnabled(pluginId: String): Boolean {
        val enabled = pluginStates[pluginId] ?: true // 默认启用
        Log.d(TAG, "isEnabled($pluginId) = $enabled")
        return enabled
    }
    
    /**
     * 设置插件启用状态
     */
    fun setEnabled(pluginId: String, enabled: Boolean) {
        Log.d(TAG, "setEnabled($pluginId, $enabled) - version was $stateVersion")
        pluginStates[pluginId] = enabled
        stateVersion++ // 触发 Compose 重组
        Log.d(TAG, "setEnabled complete - version now $stateVersion")
    }
    
    /**
     * 切换插件启用状态
     */
    fun toggle(pluginId: String): Boolean {
        val newState = !isEnabled(pluginId)
        Log.d(TAG, "toggle($pluginId): $newState")
        setEnabled(pluginId, newState)
        return newState
    }
    
    /**
     * 获取所有插件状态
     */
    fun getAllStates(): Map<String, Boolean> {
        return pluginStates.toMap()
    }
}

