package com.rostrum.core.plugin.impl

import android.content.Context
import android.util.Log
import com.rostrum.core.plugin.*
import com.rostrum.core.plugin.activation.ActivationManager
import com.rostrum.core.plugin.contribution.ContributionParser
import com.rostrum.core.plugin.contribution.PluginManifest as ContributionManifest
import com.rostrum.core.plugin.language.LanguageSupportRegistry
import com.rostrum.core.plugin.loader.LoadedPlugin
import com.rostrum.core.plugin.loader.PluginLoader
import com.rostrum.core.plugin.preview.PluginStateManager
import com.rostrum.core.plugin.providers.FilePreviewPlugin
import com.rostrum.core.plugin.providers.LanguageSupportPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 插件管理器实现
 * 
 * 仿照 VS Code 的插件管理系统设计：
 * - 支持内置插件和外部插件
 * - 支持按需激活 (Activation Events)
 * - 支持插件启用/禁用
 * - 支持插件依赖管理
 */
class PluginManagerImpl(
    private val context: Context
) : PluginManager {
    
    companion object {
        private const val TAG = "PluginManagerImpl"
        private const val PLUGINS_DIR = "plugins"
        private const val BUILTIN_PLUGINS_DIR = "builtin_plugins"
    }
    
    // 所有已注册的插件 (pluginId -> ManagedPlugin)
    private val plugins = mutableMapOf<String, ManagedPlugin>()
    
    // 插件加载器
    private val pluginLoader = PluginLoader(context)
    
    // 激活管理器
    private val activationManager = ActivationManager()
    
    // UI 注册表（用于预览插件）
    private val uiRegistry = UIRegistryImpl()
    
    // MCP协议（用于工具插件注册）
    private val mcp: com.rostrum.core.mcp.MCPProtocol = com.rostrum.core.mcp.MCPProtocolManager.getInstance()
    
    // 事件监听器
    private val eventListeners = mutableListOf<PluginEventListener>()
    
    // 线程安全锁
    private val mutex = Mutex()
    
    /**
     * 获取 UI 注册表
     */
    fun getUIRegistry(): UIRegistryImpl = uiRegistry
    
    /**
     * 获取激活管理器
     */
    fun getActivationManager(): ActivationManager = activationManager
    
    // ==================== 插件加载 ====================
    
    override suspend fun loadPlugin(pluginPackage: PluginPackage): Result<Plugin> {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val pluginDir = File(pluginPackage.filePath)
                    val result = pluginLoader.loadPlugin(pluginDir)
                    
                    result.fold(
                        onSuccess = { loadedPlugin ->
                            // 包装为 ManagedPlugin
                            val managedPlugin = ManagedPlugin(
                                id = loadedPlugin.manifest.id,
                                manifest = loadedPlugin.manifest,
                                source = PluginSource.EXTERNAL,
                                loadedPlugin = loadedPlugin,
                                builtinPlugin = null
                            )
                            
                            plugins[managedPlugin.id] = managedPlugin
                            activationManager.registerLoadedPlugin(loadedPlugin)
                            
                            Log.d(TAG, "Loaded external plugin: ${managedPlugin.id}")
                            
                            // 创建适配器
                            val adapter = ExternalPluginAdapter(loadedPlugin)
                            Result.success(adapter)
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to load plugin: ${pluginPackage.id}", error)
                            Result.failure(error)
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Exception loading plugin: ${pluginPackage.id}", e)
                    Result.failure(e)
                }
            }
        }
    }
    
    override suspend fun loadPluginsFromDirectory(directory: String): List<Result<Plugin>> {
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<Result<Plugin>>()
            val dir = File(directory)
            
            if (!dir.exists() || !dir.isDirectory) {
                return@withContext results
            }
            
            // 遍历目录下的所有子目录（每个子目录是一个插件）
            dir.listFiles()?.filter { it.isDirectory }?.forEach { pluginDir ->
                val manifestFile = File(pluginDir, "plugin.json")
                if (manifestFile.exists()) {
                    val manifest = ContributionParser.parseManifest(manifestFile).getOrNull()
                    if (manifest != null) {
                        val package_ = PluginPackage(
                            id = manifest.id,
                            version = manifest.version,
                            filePath = pluginDir.absolutePath,
                            manifest = convertManifest(manifest)
                        )
                        results.add(loadPlugin(package_))
                    }
                }
            }
            
            results
        }
    }
    
    override suspend fun loadBuiltinPlugins(): List<Plugin> {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val loadedPlugins = mutableListOf<Plugin>()
                
                // 从配置文件加载插件配置（松耦合设计）
                val config = com.rostrum.core.plugin.config.PluginConfigLoader.loadConfig(context)
                
                if (config != null) {
                    // 使用配置文件驱动的方式加载插件
                    config.plugins.forEach { pluginConfig ->
                        if (!pluginConfig.enabled) {
                            Log.d(TAG, "⏭ Skipping disabled plugin: ${pluginConfig.id}")
                            return@forEach
                        }
                        
                        try {
                            // 通过反射动态加载插件类
                            val pluginClass = Class.forName(pluginConfig.className)
                            val plugin = pluginClass.getDeclaredConstructor().newInstance() as Plugin
                            
                            registerBuiltinPlugin(plugin)
                            loadedPlugins.add(plugin)
                            Log.d(TAG, "✓ Loaded plugin: ${pluginConfig.id} (priority=${pluginConfig.priority})")
                        } catch (e: ClassNotFoundException) {
                            Log.e(TAG, "✗ Plugin class not found: ${pluginConfig.className}", e)
                        } catch (e: Exception) {
                            Log.e(TAG, "✗ Failed to load plugin: ${pluginConfig.id}", e)
                        }
                    }
                    
                    Log.d(TAG, "Total plugins loaded from config: ${loadedPlugins.size}/${config.plugins.size}")
                } else {
                    // 如果配置文件加载失败，回退到硬编码方式（向后兼容）
                    Log.w(TAG, "Failed to load plugin config, falling back to hardcoded plugins")
                    loadedPlugins.addAll(loadHardcodedPlugins())
                }
                
                loadedPlugins
            }
        }
    }
    
    /**
     * 硬编码插件加载（向后兼容）
     * 当配置文件不可用时使用
     */
    private fun loadHardcodedPlugins(): List<Plugin> {
        val loadedPlugins = mutableListOf<Plugin>()
        val pluginFactories = listOf<Pair<String, () -> Plugin>>(
            "Image" to { com.rostrum.plugins.marketplace.preview.image.ImagePreviewPlugin() },
            "Text" to { com.rostrum.plugins.marketplace.preview.text.TextPreviewPlugin() },
            "Markdown" to { com.rostrum.plugins.marketplace.preview.markdown.MarkdownPreviewPlugin() },
            "Code" to { com.rostrum.plugins.marketplace.preview.code.CodePreviewPlugin() },
            "Audio" to { com.rostrum.plugins.marketplace.preview.audio.AudioPreviewPlugin() },
            "DOCX" to { com.rostrum.plugins.marketplace.preview.docx.DocxPreviewPlugin() },
            "PPTX" to { com.rostrum.plugins.marketplace.preview.pptx.PptxPreviewPlugin() },
            "Excel" to { com.rostrum.plugins.marketplace.preview.excel.XlsxPreviewPlugin() },
            "PDF" to { com.rostrum.plugins.marketplace.preview.pdf.PdfPreviewPlugin() },
            "HTML" to { com.rostrum.plugins.marketplace.preview.html.HtmlPreviewPlugin() },
            "JavaLanguageSupport" to { com.rostrum.plugins.marketplace.language.java.JavaLanguageSupportPlugin() }
        )
        
        pluginFactories.forEach { (name, factory) ->
            try {
                val plugin = factory()
                registerBuiltinPlugin(plugin)
                loadedPlugins.add(plugin)
                Log.d(TAG, "✓ Loaded hardcoded plugin: $name")
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to load hardcoded plugin: $name", e)
            }
        }
        
        return loadedPlugins
    }
    
    /**
     * 注册内置插件
     */
    private fun registerBuiltinPlugin(plugin: Plugin) {
        val manifest = createManifestFromPlugin(plugin)
        
        val managedPlugin = ManagedPlugin(
            id = plugin.id,
            manifest = manifest,
            source = PluginSource.BUILTIN,
            loadedPlugin = null,
            builtinPlugin = plugin
        )
        
        plugins[plugin.id] = managedPlugin
        
        // 如果是预览插件，注册到 UI 注册表
        if (plugin is FilePreviewPlugin) {
            uiRegistry.registerPreview(plugin)
        }

        // 如果是语言支持插件，注册到语言注册表
        if (plugin is LanguageSupportPlugin) {
            LanguageSupportRegistry.register(plugin)
        }
        
        // 通知监听器
        eventListeners.forEach { it.onPluginLoaded(plugin) }
    }
    
    /**
     * 从 Plugin 创建 ContributionManifest
     */
    private fun createManifestFromPlugin(plugin: Plugin): ContributionManifest {
        val contributes = if (plugin is FilePreviewPlugin) {
            com.rostrum.core.plugin.contribution.ContributionPoints(
                filePreview = listOf(
                    com.rostrum.core.plugin.contribution.FilePreviewContribution(
                        extensions = plugin.supportedExtensions,
                        mimeTypes = plugin.supportedMimeTypes,
                        priority = 100
                    )
                )
            )
        } else null
        
        return ContributionManifest(
            id = plugin.id,
            name = plugin.name,
            version = plugin.version,
            description = plugin.description,
            author = plugin.author,
            main = plugin::class.java.name,
            contributes = contributes,
            activationEvents = listOf("onStartup"),
            category = plugin.category
        )
    }
    
    /**
     * 转换清单格式
     */
    private fun convertManifest(manifest: ContributionManifest): com.rostrum.core.plugin.PluginManifest {
        return com.rostrum.core.plugin.PluginManifest(
            id = manifest.id,
            name = manifest.name,
            version = manifest.version,
            author = manifest.author ?: "Unknown",
            description = manifest.description ?: "",
            category = manifest.category ?: PluginCategory.EXTENSION,
            dependencies = manifest.dependencies,
            permissions = manifest.permissions,
            capabilities = emptyList(),
            mainClass = manifest.main
        )
    }
    
    // ==================== 插件生命周期管理 ====================
    
    override suspend fun enablePlugin(pluginId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val managedPlugin = plugins[pluginId]
                    ?: return@withLock Result.failure(IllegalArgumentException("Plugin not found: $pluginId"))
                
                try {
                    // 更新状态
                    PluginStateManager.setEnabled(pluginId, true)
                    
                    // 如果是内置插件，激活它
                    managedPlugin.builtinPlugin?.let { plugin ->
                        val pluginContext = createPluginContext(plugin)
                        plugin.initialize(pluginContext)
                        plugin.onActivate()
                        
                        // 重新注册到 UI 注册表
                        if (plugin is FilePreviewPlugin) {
                            uiRegistry.registerPreview(plugin)
                        }
                        if (plugin is LanguageSupportPlugin) {
                            LanguageSupportRegistry.register(plugin)
                        }
                        
                        eventListeners.forEach { it.onPluginActivated(plugin) }
                    }
                    
                    Log.d(TAG, "Enabled plugin: $pluginId")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enable plugin: $pluginId", e)
                    Result.failure(e)
                }
            }
        }
    }
    
    override suspend fun disablePlugin(pluginId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val managedPlugin = plugins[pluginId]
                    ?: return@withLock Result.failure(IllegalArgumentException("Plugin not found: $pluginId"))
                
                try {
                    // 更新状态
                    PluginStateManager.setEnabled(pluginId, false)
                    
                    // 如果是内置插件，停用它
                    managedPlugin.builtinPlugin?.let { plugin ->
                        if (plugin is LanguageSupportPlugin) {
                            LanguageSupportRegistry.unregister(plugin)
                        }
                        plugin.onDeactivate()
                        eventListeners.forEach { it.onPluginDeactivated(pluginId) }
                    }
                    
                    Log.d(TAG, "Disabled plugin: $pluginId")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to disable plugin: $pluginId", e)
                    Result.failure(e)
                }
            }
        }
    }
    
    override suspend fun uninstallPlugin(pluginId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val managedPlugin = plugins[pluginId]
                    ?: return@withLock Result.failure(IllegalArgumentException("Plugin not found: $pluginId"))
                
                // 不能卸载内置插件
                if (managedPlugin.source == PluginSource.BUILTIN) {
                    return@withLock Result.failure(IllegalStateException("Cannot uninstall builtin plugin: $pluginId"))
                }
                
                try {
                    // 先禁用
                    disablePlugin(pluginId)
                    
                    // 移除插件
                    plugins.remove(pluginId)
                    
                    // 删除插件文件
                    managedPlugin.loadedPlugin?.pluginDir?.deleteRecursively()
                    
                    eventListeners.forEach { it.onPluginUnloaded(pluginId) }
                    
                    Log.d(TAG, "Uninstalled plugin: $pluginId")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to uninstall plugin: $pluginId", e)
                    Result.failure(e)
                }
            }
        }
    }
    
    override suspend fun updatePlugin(pluginId: String, newVersion: String): Result<Unit> {
        return mutex.withLock {
            val managedPlugin = plugins[pluginId]
                ?: return@withLock Result.failure(IllegalStateException("插件不存在: $pluginId"))
            
            // 检查是否是外部插件（内置插件不能更新）
            if (managedPlugin.source == PluginSource.BUILTIN) {
                return@withLock Result.failure(IllegalStateException("内置插件不支持更新"))
            }
            
            try {
                // 1. 先卸载旧版本
                val uninstallResult = uninstallPlugin(pluginId)
                if (uninstallResult.isFailure) {
                    return@withLock Result.failure(uninstallResult.exceptionOrNull() 
                        ?: Exception("卸载旧版本失败"))
                }
                
                // 2. 从市场重新安装新版本
                val installResult = installFromMarket(pluginId, newVersion)
                if (installResult.isFailure) {
                    return@withLock Result.failure(installResult.exceptionOrNull() 
                        ?: Exception("安装新版本失败"))
                }
                
                Log.i(TAG, "插件更新成功: $pluginId -> $newVersion")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "插件更新失败: $pluginId", e)
                Result.failure(e)
            }
        }
    }
    
    // ==================== 插件查询 ====================
    
    override suspend fun getPlugin(pluginId: String): Plugin? {
        return mutex.withLock {
            plugins[pluginId]?.let { managedPlugin ->
                managedPlugin.builtinPlugin ?: managedPlugin.loadedPlugin?.let { ExternalPluginAdapter(it) }
            }
        }
    }
    
    override suspend fun getPluginsByCategory(category: PluginCategory): List<Plugin> {
        return mutex.withLock {
            plugins.values
                .filter { it.manifest.category == category }
                .mapNotNull { managedPlugin ->
                    managedPlugin.builtinPlugin ?: managedPlugin.loadedPlugin?.let { ExternalPluginAdapter(it) }
                }
        }
    }
    
    override suspend fun getActivePlugins(): List<Plugin> {
        return mutex.withLock {
            plugins.values
                .filter { PluginStateManager.isEnabled(it.id) }
                .mapNotNull { managedPlugin ->
                    managedPlugin.builtinPlugin ?: managedPlugin.loadedPlugin?.let { ExternalPluginAdapter(it) }
                }
        }
    }
    
    override suspend fun getAllPlugins(): List<Plugin> {
        return mutex.withLock {
            plugins.values.mapNotNull { managedPlugin ->
                managedPlugin.builtinPlugin ?: managedPlugin.loadedPlugin?.let { ExternalPluginAdapter(it) }
            }
        }
    }
    
    /**
     * 获取所有托管插件信息
     */
    fun getManagedPlugins(): List<ManagedPlugin> {
        return plugins.values.toList()
    }
    
    // ==================== 依赖管理 ====================
    
    override suspend fun resolveDependencies(pluginId: String): List<Plugin> {
        val managedPlugin = plugins[pluginId] ?: return emptyList()
        val dependencies = managedPlugin.manifest.dependencies
        
        return dependencies.keys.mapNotNull { depId ->
            getPlugin(depId)
        }
    }
    
    override suspend fun checkDependencies(pluginId: String): DependencyResult {
        val managedPlugin = plugins[pluginId] 
            ?: return DependencyResult(satisfied = false, missing = listOf(pluginId))
        
        val dependencies = managedPlugin.manifest.dependencies
        val missing = mutableListOf<String>()
        val conflicts = mutableListOf<String>()
        
        for ((depId, requiredVersion) in dependencies) {
            val depPlugin = plugins[depId]
            if (depPlugin == null) {
                missing.add(depId)
            } else {
                // 简单的版本检查
                if (!isVersionCompatible(depPlugin.manifest.version, requiredVersion)) {
                    conflicts.add("$depId requires $requiredVersion but found ${depPlugin.manifest.version}")
                }
            }
        }
        
        return DependencyResult(
            satisfied = missing.isEmpty() && conflicts.isEmpty(),
            missing = missing,
            conflicts = conflicts
        )
    }
    
    private fun isVersionCompatible(actual: String, required: String): Boolean {
        // 简单实现：只检查主版本号
        val actualMajor = actual.split(".").firstOrNull()?.toIntOrNull() ?: 0
        val requiredMajor = required.split(".").firstOrNull()?.toIntOrNull() ?: 0
        return actualMajor >= requiredMajor
    }
    
    // ==================== 事件监听 ====================
    
    override fun addPluginEventListener(listener: PluginEventListener) {
        eventListeners.add(listener)
    }
    
    override fun removePluginEventListener(listener: PluginEventListener) {
        eventListeners.remove(listener)
    }
    
    // ==================== 市场功能 ====================
    
    // 模拟的插件市场目录（实际应该从远程 API 获取）
    private val marketPlugins = listOf(
        RemotePluginInfo(
            id = "com.rostrum.plugin.markdown",
            name = "Markdown 预览",
            description = "支持 Markdown 文件的实时预览和编辑",
            version = "1.0.0",
            author = "OmniMaster",
            category = PluginCategory.PREVIEW,
            downloadUrl = "",
            iconUrl = "",
            rating = 4.5f,
            downloadCount = 1000
        ),
        RemotePluginInfo(
            id = "com.rostrum.plugin.code-highlight",
            name = "代码高亮",
            description = "支持多种编程语言的语法高亮",
            version = "1.2.0",
            author = "OmniMaster",
            category = PluginCategory.EDITOR,
            downloadUrl = "",
            iconUrl = "",
            rating = 4.8f,
            downloadCount = 2000
        ),
        RemotePluginInfo(
            id = "com.rostrum.plugin.image-viewer",
            name = "图片查看器",
            description = "支持多种图片格式的查看和基本编辑",
            version = "1.1.0",
            author = "OmniMaster",
            category = PluginCategory.PREVIEW,
            downloadUrl = "",
            iconUrl = "",
            rating = 4.2f,
            downloadCount = 1500
        )
    )
    
    override suspend fun searchPlugins(query: String, category: PluginCategory?): List<RemotePluginInfo> {
        // 从模拟市场搜索（实际应该调用远程 API）
        return marketPlugins.filter { plugin ->
            val matchesQuery = query.isBlank() || 
                plugin.name.contains(query, ignoreCase = true) ||
                plugin.description.contains(query, ignoreCase = true)
            val matchesCategory = category == null || plugin.category == category
            matchesQuery && matchesCategory
        }
    }
    
    override suspend fun installFromMarket(pluginId: String, version: String): Result<Plugin> {
        // 查找插件信息
        val remotePlugin = marketPlugins.find { it.id == pluginId }
            ?: return Result.failure(IllegalArgumentException("插件不存在: $pluginId"))
        
        // 检查是否已安装
        if (plugins.containsKey(pluginId)) {
            return Result.failure(IllegalStateException("插件已安装: $pluginId"))
        }
        
        // 由于没有真正的下载 URL，返回提示信息
        // 实际实现应该：1. 下载插件包 2. 解压 3. 加载
        Log.i(TAG, "准备从市场安装插件: ${remotePlugin.name} v$version")
        
        return Result.failure(NotImplementedError(
            "插件市场需要后端支持。请手动安装插件包。\n" +
            "插件: ${remotePlugin.name}\n" +
            "版本: $version"
        ))
    }
    
    override suspend fun updateAllPlugins(): List<UpdateResult> {
        val results = mutableListOf<UpdateResult>()
        
        mutex.withLock {
            for ((pluginId, managedPlugin) in plugins) {
                // 跳过内置插件
                if (managedPlugin.source == PluginSource.BUILTIN) continue
                
                // 检查市场中是否有更新
                val remotePlugin = marketPlugins.find { it.id == pluginId }
                if (remotePlugin != null) {
                    val currentVersion = managedPlugin.manifest.version
                    if (isNewerVersion(remotePlugin.version, currentVersion)) {
                        // 尝试更新
                        val updateResult = updatePlugin(pluginId, remotePlugin.version)
                        results.add(UpdateResult(
                            pluginId = pluginId,
                            success = updateResult.isSuccess,
                            newVersion = if (updateResult.isSuccess) remotePlugin.version else null,
                            error = updateResult.exceptionOrNull()?.message
                        ))
                    }
                }
            }
        }
        
        return results
    }
    
    /**
     * 比较版本号是否更新
     */
    private fun isNewerVersion(newVersion: String, currentVersion: String): Boolean {
        val newParts = newVersion.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentVersion.split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until maxOf(newParts.size, currentParts.size)) {
            val new = newParts.getOrElse(i) { 0 }
            val current = currentParts.getOrElse(i) { 0 }
            if (new > current) return true
            if (new < current) return false
        }
        return false
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 创建插件上下文
     */
    private fun createPluginContext(plugin: Plugin): PluginContext {
        return PluginContextImpl(
            plugin = plugin,
            appContext = context,
            pluginManager = this,
            fileSystem = createFileSystem(),
            terminal = createDummyTerminal(),
            mcp = createDummyMcp(),
            eventBus = getEventBus(),
            config = createConfigManager(),
            uiImpl = uiRegistry
        )
    }
    
    // 使用真实的 LocalFileSystem 实现文件系统服务
    private fun createFileSystem(): com.rostrum.core.filesystem.FileSystemService {
        return com.rostrum.core.filesystem.LocalFileSystem()
    }
    
    private fun createDummyTerminal() = object : com.rostrum.core.terminal.TerminalService {
        override suspend fun createSession(shell: String, workingDirectory: String, environment: Map<String, String>) = 
            Result.failure<com.rostrum.core.terminal.TerminalSession>(NotImplementedError())
        override suspend fun getSession(sessionId: String) = null
        override suspend fun closeSession(sessionId: String) {}
        override suspend fun listSessions() = emptyList<com.rostrum.core.terminal.TerminalSession>()
        override suspend fun hasCommand(command: String) = false
        override suspend fun getAvailableCommands() = emptyList<String>()
    }
    
    private fun createDummyMcp() = object : com.rostrum.core.mcp.MCPProtocol {
        override fun registerTool(tool: com.rostrum.core.mcp.MCPTool) {}
        override fun unregisterTool(toolName: String) {}
        override fun getAvailableTools() = emptyList<com.rostrum.core.mcp.MCPTool>()
        override suspend fun callTool(toolName: String, arguments: Map<String, Any>, context: com.rostrum.core.mcp.MCPContext) = 
            com.rostrum.core.mcp.MCPResult(false, null, com.rostrum.core.mcp.MCPError("NOT_IMPLEMENTED", "Not implemented"))
        override suspend fun discoverTools(scope: com.rostrum.core.mcp.MCPScope) = emptyList<com.rostrum.core.mcp.MCPTool>()
    }
    
    /**
     * 获取事件总线实例（单例）
     */
    private fun getEventBus(): com.rostrum.core.event.EventBus {
        return com.rostrum.core.event.EventBusImpl.getInstance()
    }
    
    // 使用真实的 ConfigManagerImpl 实现配置服务
    private fun createConfigManager(): com.rostrum.core.config.ConfigManager {
        return com.rostrum.core.config.ConfigManagerImpl.getInstance(context)
    }
}

/**
 * 插件来源
 */
enum class PluginSource {
    BUILTIN,    // 内置插件
    EXTERNAL,   // 外部加载的插件
    MARKET      // 从市场安装的插件
}

/**
 * 托管插件信息
 */
data class ManagedPlugin(
    val id: String,
    val manifest: ContributionManifest,
    val source: PluginSource,
    val loadedPlugin: LoadedPlugin?,      // 外部插件
    val builtinPlugin: Plugin?            // 内置插件
)

/**
 * 外部插件适配器
 * 
 * 将 Extension 接口适配为 Plugin 接口
 */
class ExternalPluginAdapter(
    private val loadedPlugin: LoadedPlugin
) : Plugin {
    override val id: String = loadedPlugin.manifest.id
    override val name: String = loadedPlugin.manifest.name
    override val version: String = loadedPlugin.manifest.version
    override val author: String = loadedPlugin.manifest.author ?: "Unknown"
    override val description: String = loadedPlugin.manifest.description ?: ""
    override val category: PluginCategory = loadedPlugin.manifest.category ?: PluginCategory.EXTENSION
    override val dependencies: List<String> = loadedPlugin.manifest.dependencies.keys.toList()
    
    override suspend fun initialize(context: PluginContext): Result<Unit> {
        // 外部插件通过 Extension.activate 初始化
        return Result.success(Unit)
    }
    
    override suspend fun onActivate(): Result<Unit> {
        return Result.success(Unit)
    }
    
    override suspend fun onDeactivate(): Result<Unit> {
        return Result.success(Unit)
    }
    
    override suspend fun onDestroy(): Result<Unit> {
        return Result.success(Unit)
    }
    
    override fun getCapabilities(): List<PluginCapability> {
        val capabilities = mutableListOf<PluginCapability>()
        loadedPlugin.manifest.contributes?.let { contributes ->
            if (contributes.filePreview != null) capabilities.add(PluginCapability.FILE_PREVIEW)
            if (contributes.fileEditor != null) capabilities.add(PluginCapability.FILE_EDIT)
            if (contributes.languages != null) capabilities.add(PluginCapability.LANGUAGE_SUPPORT)
            if (contributes.themes != null) capabilities.add(PluginCapability.THEME)
        }
        return capabilities
    }
}
