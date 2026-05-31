package com.rostrum.core.plugin

/**
 * 插件管理器接口
 */
interface PluginManager {
    /**
     * 加载插件
     * 
     * @param pluginPackage 插件包
     * @return 加载结果
     */
    suspend fun loadPlugin(pluginPackage: PluginPackage): Result<Plugin>
    
    /**
     * 从目录加载插件
     * 
     * @param directory 目录路径
     * @return 加载结果列表
     */
    suspend fun loadPluginsFromDirectory(directory: String): List<Result<Plugin>>
    
    /**
     * 加载内置插件
     * 
     * @return 插件列表
     */
    suspend fun loadBuiltinPlugins(): List<Plugin>
    
    /**
     * 启用插件
     * 
     * @param pluginId 插件ID
     * @return 启用结果
     */
    suspend fun enablePlugin(pluginId: String): Result<Unit>
    
    /**
     * 禁用插件
     * 
     * @param pluginId 插件ID
     * @return 禁用结果
     */
    suspend fun disablePlugin(pluginId: String): Result<Unit>
    
    /**
     * 卸载插件
     * 
     * @param pluginId 插件ID
     * @return 卸载结果
     */
    suspend fun uninstallPlugin(pluginId: String): Result<Unit>
    
    /**
     * 更新插件
     * 
     * @param pluginId 插件ID
     * @param newVersion 新版本
     * @return 更新结果
     */
    suspend fun updatePlugin(pluginId: String, newVersion: String): Result<Unit>
    
    /**
     * 获取插件
     * 
     * @param pluginId 插件ID
     * @return 插件，如果不存在返回null
     */
    suspend fun getPlugin(pluginId: String): Plugin?
    
    /**
     * 按分类获取插件
     * 
     * @param category 插件分类
     * @return 插件列表
     */
    suspend fun getPluginsByCategory(category: PluginCategory): List<Plugin>
    
    /**
     * 获取激活的插件
     * 
     * @return 插件列表
     */
    suspend fun getActivePlugins(): List<Plugin>
    
    /**
     * 获取所有插件
     * 
     * @return 插件列表
     */
    suspend fun getAllPlugins(): List<Plugin>
    
    /**
     * 解析依赖
     * 
     * @param pluginId 插件ID
     * @return 依赖插件列表
     */
    suspend fun resolveDependencies(pluginId: String): List<Plugin>
    
    /**
     * 检查依赖
     * 
     * @param pluginId 插件ID
     * @return 依赖检查结果
     */
    suspend fun checkDependencies(pluginId: String): DependencyResult
    
    /**
     * 添加插件事件监听器
     * 
     * @param listener 监听器
     */
    fun addPluginEventListener(listener: PluginEventListener)
    
    /**
     * 移除插件事件监听器
     * 
     * @param listener 监听器
     */
    fun removePluginEventListener(listener: PluginEventListener)
    
    /**
     * 搜索插件（远程市场）
     * 
     * @param query 搜索关键词
     * @param category 插件分类（可选）
     * @return 远程插件信息列表
     */
    suspend fun searchPlugins(query: String, category: PluginCategory? = null): List<RemotePluginInfo>
    
    /**
     * 从市场安装插件
     * 
     * @param pluginId 插件ID
     * @param version 版本号，默认为 "latest"
     * @return 安装结果
     */
    suspend fun installFromMarket(pluginId: String, version: String = "latest"): Result<Plugin>
    
    /**
     * 更新所有插件
     * 
     * @return 更新结果列表
     */
    suspend fun updateAllPlugins(): List<UpdateResult>
}

/**
 * 插件包
 */
data class PluginPackage(
    val id: String,
    val version: String,
    val filePath: String,
    val manifest: PluginManifest,
    val signature: String? = null  // 数字签名
)

/**
 * 插件清单
 */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val category: PluginCategory,
    val dependencies: Map<String, String> = emptyMap(),  // pluginId -> version
    val permissions: List<String> = emptyList(),
    val capabilities: List<String> = emptyList(),
    val mainClass: String,  // 插件入口类
    val icon: String? = null,
    val screenshots: List<String> = emptyList(),
    val changelog: String? = null,
    val license: String? = null
)

/**
 * 依赖检查结果
 */
data class DependencyResult(
    val satisfied: Boolean,
    val missing: List<String> = emptyList(),
    val conflicts: List<String> = emptyList()
)

/**
 * 插件事件监听器
 */
interface PluginEventListener {
    fun onPluginLoaded(plugin: Plugin)
    fun onPluginUnloaded(pluginId: String)
    fun onPluginActivated(plugin: Plugin)
    fun onPluginDeactivated(pluginId: String)
}

/**
 * 远程插件信息
 */
data class RemotePluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val category: PluginCategory,
    val downloadUrl: String,
    val iconUrl: String? = null,
    val screenshots: List<String> = emptyList(),
    val rating: Float = 0f,
    val downloadCount: Long = 0
)

/**
 * 更新结果
 */
data class UpdateResult(
    val pluginId: String,
    val success: Boolean,
    val newVersion: String? = null,
    val error: String? = null
)

