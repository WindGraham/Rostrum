package com.rostrum.core.plugin.loader

import android.content.Context
import android.util.Log
import com.rostrum.core.plugin.Plugin
import com.rostrum.core.plugin.contribution.ContributionParser
import com.rostrum.core.plugin.contribution.PluginManifest
import com.rostrum.extension.api.Extension
import dalvik.system.DexClassLoader
import java.io.File

/**
 * 插件加载器
 * 
 * 负责动态加载插件类并创建插件实例
 */
class PluginLoader(private val context: Context) {
    private val TAG = "PluginLoader"
    
    /**
     * 加载插件
     * 
     * @param pluginDir 插件目录
     * @return 加载结果，包含插件实例和清单信息
     */
    fun loadPlugin(pluginDir: File): Result<LoadedPlugin> {
        return try {
            // 1. 解析 plugin.json
            val manifestFile = File(pluginDir, "plugin.json")
            val manifestResult = ContributionParser.parseManifest(manifestFile)
            val manifest = manifestResult.getOrElse {
                return Result.failure(it)
            }
            
            // 2. 查找插件 JAR/APK 文件
            val pluginJar = findPluginJar(pluginDir, manifest)
            if (pluginJar == null) {
                return Result.failure(IllegalArgumentException("Plugin JAR/APK not found in: ${pluginDir.absolutePath}"))
            }
            
            // 3. 创建 DexClassLoader
            val optimizedDir = File(context.cacheDir, "plugin_optimized/${manifest.id}")
            optimizedDir.mkdirs()
            
            val classLoader = DexClassLoader(
                pluginJar.absolutePath,
                optimizedDir.absolutePath,
                null,
                context.classLoader
            )
            
            // 4. 加载插件主类
            val pluginClass = classLoader.loadClass(manifest.main)
            
            // 5. 创建插件实例
            val pluginInstance = pluginClass.newInstance()
            
            // 6. 验证插件类型
            if (pluginInstance !is Extension) {
                return Result.failure(IllegalArgumentException("Plugin class ${manifest.main} does not implement Extension interface"))
            }
            
            val loadedPlugin = LoadedPlugin(
                manifest = manifest,
                instance = pluginInstance,
                classLoader = classLoader,
                pluginDir = pluginDir,
                pluginJar = pluginJar
            )
            
            Log.d(TAG, "Successfully loaded plugin: ${manifest.id}")
            Result.success(loadedPlugin)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load plugin from: ${pluginDir.absolutePath}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 查找插件 JAR/APK 文件
     * 
     * 优先查找与插件ID同名的 JAR/APK 文件
     */
    private fun findPluginJar(pluginDir: File, manifest: PluginManifest): File? {
        // 查找可能的文件名模式
        val possibleNames = listOf(
            "${manifest.id}.jar",
            "${manifest.id}.apk",
            "plugin.jar",
            "plugin.apk",
            "main.jar",
            "main.apk"
        )
        
        for (name in possibleNames) {
            val file = File(pluginDir, name)
            if (file.exists() && file.isFile) {
                return file
            }
        }
        
        // 如果没找到，查找目录下所有 JAR/APK 文件
        val jarFiles = pluginDir.listFiles { _, name ->
            name.endsWith(".jar", ignoreCase = true) || name.endsWith(".apk", ignoreCase = true)
        }
        
        return jarFiles?.firstOrNull()
    }
    
    /**
     * 从内置资源加载插件（用于内置插件）
     * 
     * @param pluginId 插件ID
     * @return 加载结果
     */
    fun loadBuiltinPlugin(pluginId: String): Result<LoadedPlugin>? {
        // 对于内置插件，直接从类路径加载
        // 这里需要根据实际情况实现
        // 暂时返回 null，表示不支持
        return null
    }
}

/**
 * 已加载的插件信息
 */
data class LoadedPlugin(
    /**
     * 插件清单
     */
    val manifest: PluginManifest,
    
    /**
     * 插件实例（实现 Extension 接口）
     */
    val instance: Extension,
    
    /**
     * 类加载器
     */
    val classLoader: ClassLoader,
    
    /**
     * 插件目录
     */
    val pluginDir: File,
    
    /**
     * 插件 JAR/APK 文件
     */
    val pluginJar: File
)

