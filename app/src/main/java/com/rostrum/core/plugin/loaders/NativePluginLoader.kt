package com.rostrum.core.plugin.loaders

import android.content.Context
import com.rostrum.core.plugin.Plugin
import com.rostrum.core.plugin.PluginLoader
import com.rostrum.core.plugin.PluginManifest
import com.rostrum.core.plugin.PluginPackage
import dalvik.system.DexClassLoader
import java.io.File

/**
 * 原生Kotlin插件加载器
 * 
 * 加载编译为DEX/Kotlin类的原生插件
 */
class NativePluginLoader(
    private val context: Context
) : PluginLoader {
    
    private val pluginCacheDir = File(context.filesDir, "plugins_cache")
    
    init {
        pluginCacheDir.mkdirs()
    }
    
    override suspend fun load(pluginPackage: PluginPackage): Plugin {
        if (!canLoad(pluginPackage)) {
            throw IllegalArgumentException("Cannot load plugin: ${pluginPackage.id}")
        }
        
        // 从插件包位置加载类
        val pluginClass = loadPluginClass(pluginPackage)
        
        // 实例化插件
        val plugin = pluginClass.getDeclaredConstructor().newInstance() as Plugin
        
        // 验证插件元数据
        validatePluginMetadata(plugin, pluginPackage.manifest)
        
        return plugin
    }
    
    override fun canLoad(pluginPackage: PluginPackage): Boolean {
        // 原生插件：.jar, .dex, .apk 文件，或manifest中mainClass指向Kotlin类
        val filePath = pluginPackage.filePath
        return filePath.endsWith(".jar") || 
               filePath.endsWith(".dex") || 
               filePath.endsWith(".apk") ||
               pluginPackage.manifest.mainClass.isNotEmpty()
    }
    
    /**
     * 加载插件类
     */
    private fun loadPluginClass(pluginPackage: PluginPackage): Class<out Plugin> {
        val pluginFile = File(pluginPackage.filePath)
        if (!pluginFile.exists()) {
            throw IllegalArgumentException("Plugin file not found: ${pluginPackage.filePath}")
        }
        
        // 创建DexClassLoader
        val optimizedDir = File(pluginCacheDir, pluginPackage.id)
        optimizedDir.mkdirs()
        
        val classLoader = DexClassLoader(
            pluginFile.absolutePath,
            optimizedDir.absolutePath,
            null,
            context.classLoader
        )
        
        // 加载插件主类（从manifest中获取）
        val className = pluginPackage.manifest.mainClass
        val pluginClass = classLoader.loadClass(className)
        
        if (!Plugin::class.java.isAssignableFrom(pluginClass)) {
            throw IllegalArgumentException("Plugin class must implement Plugin interface")
        }
        
        @Suppress("UNCHECKED_CAST")
        return pluginClass as Class<out Plugin>
    }
    
    /**
     * 验证插件元数据
     */
    private fun validatePluginMetadata(plugin: Plugin, manifest: PluginManifest) {
        require(plugin.id == manifest.id) {
            "Plugin ID mismatch: ${plugin.id} != ${manifest.id}"
        }
        require(plugin.name == manifest.name) {
            "Plugin name mismatch: ${plugin.name} != ${manifest.name}"
        }
        require(plugin.version == manifest.version) {
            "Plugin version mismatch: ${plugin.version} != ${manifest.version}"
        }
    }
}

