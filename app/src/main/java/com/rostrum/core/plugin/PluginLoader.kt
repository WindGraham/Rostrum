package com.rostrum.core.plugin

import android.content.Context
import com.rostrum.core.plugin.loaders.NativePluginLoader
import com.rostrum.core.plugin.loaders.VSCodePluginLoader

/**
 * 插件加载器接口
 */
interface PluginLoader {
    /**
     * 加载插件
     */
    suspend fun load(pluginPackage: PluginPackage): Plugin
    
    /**
     * 检查是否可以加载指定插件包
     */
    fun canLoad(pluginPackage: PluginPackage): Boolean
}

/**
 * 复合插件加载器
 * 
 * 根据插件类型选择合适的加载器
 */
class CompositePluginLoader(
    private val context: Context,
    private val nativeLoader: NativePluginLoader = NativePluginLoader(context),
    private val vsCodeLoader: VSCodePluginLoader = VSCodePluginLoader(context)
) : PluginLoader {
    
    override suspend fun load(pluginPackage: PluginPackage): Plugin {
        // 根据文件扩展名和manifest判断插件类型
        return when {
            vsCodeLoader.canLoad(pluginPackage) -> vsCodeLoader.load(pluginPackage)
            nativeLoader.canLoad(pluginPackage) -> nativeLoader.load(pluginPackage)
            else -> throw IllegalArgumentException("Cannot determine plugin loader for: ${pluginPackage.id}")
        }
    }
    
    override fun canLoad(pluginPackage: PluginPackage): Boolean {
        return nativeLoader.canLoad(pluginPackage) || vsCodeLoader.canLoad(pluginPackage)
    }
}

