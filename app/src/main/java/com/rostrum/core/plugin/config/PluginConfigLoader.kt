package com.rostrum.core.plugin.config

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import java.io.InputStream

/**
 * 插件配置加载器
 * 
 * 从 assets/plugins/builtin-plugins.json 加载内置插件配置
 * 实现插件与框架的松耦合：通过配置文件管理插件，无需修改代码
 */
object PluginConfigLoader {
    private const val TAG = "PluginConfigLoader"
    private const val CONFIG_FILE = "plugins/builtin-plugins.json"
    
    /**
     * 插件配置数据类
     */
    data class PluginConfig(
        val id: String,
        val className: String,
        val enabled: Boolean,
        val priority: Int = 100
    )
    
    /**
     * 配置数据类
     */
    data class Config(
        val version: String,
        val plugins: List<PluginConfig>
    )
    
    /**
     * 从 assets 加载插件配置
     * 
     * @param context Android Context
     * @return 配置对象，失败返回 null
     */
    fun loadConfig(context: Context): Config? {
        return try {
            val inputStream: InputStream = context.assets.open(CONFIG_FILE)
            val jsonContent = inputStream.bufferedReader().use { it.readText() }
            inputStream.close()
            
            val json = JSONObject(jsonContent)
            val version = json.optString("version", "1.0.0")
            val pluginsArray = json.getJSONArray("plugins")
            
            val plugins = mutableListOf<PluginConfig>()
            for (i in 0 until pluginsArray.length()) {
                val pluginObj = pluginsArray.getJSONObject(i)
                plugins.add(
                    PluginConfig(
                        id = pluginObj.getString("id"),
                        className = pluginObj.getString("class"),
                        enabled = pluginObj.optBoolean("enabled", true),
                        priority = pluginObj.optInt("priority", 100)
                    )
                )
            }
            
            Log.d(TAG, "Loaded ${plugins.size} plugin configurations from $CONFIG_FILE")
            Config(version = version, plugins = plugins)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load plugin config from $CONFIG_FILE", e)
            null
        }
    }
    
    /**
     * 获取启用的插件配置
     */
    fun getEnabledPlugins(context: Context): List<PluginConfig> {
        return loadConfig(context)?.plugins?.filter { it.enabled } ?: emptyList()
    }
}

