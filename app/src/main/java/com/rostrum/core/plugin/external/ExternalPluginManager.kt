package com.rostrum.core.plugin.external

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Environment
import android.os.IBinder
import android.os.FileObserver
import android.util.Log
import com.rostrum.core.plugin.contribution.ContributionParser
import com.rostrum.core.plugin.contribution.PluginManifest
import com.rostrum.core.plugin.ipc.ExtensionHostService
import com.rostrum.core.plugin.ipc.IExtensionHostService
import com.rostrum.core.plugin.ipc.PluginProcessService
import com.rostrum.core.plugin.preview.PluginStateManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * 外部插件管理器
 * 
 * 负责：
 * 1. 扫描外部插件目录
 * 2. 解析 plugin.json 清单
 * 3. 在独立进程中加载插件 APK
 * 4. 管理插件生命周期
 * 
 * 这是真正的 VS Code 风格插件系统的核心
 */
class ExternalPluginManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "ExternalPluginManager"
        
        // 外部插件目录（用户可以放置自己编写的插件）
        val EXTERNAL_PLUGINS_DIR = File(
            Environment.getExternalStorageDirectory(),
            "OmniMaster/plugins"
        )
        
        // 内置插件目录（从 assets 复制）
        fun getBuiltinPluginsDir(context: Context) = File(context.filesDir, "builtin_plugins")
        
        // 已安装插件目录
        fun getInstalledPluginsDir(context: Context) = File(context.filesDir, "installed_plugins")

        // 插件运行时镜像目录。PRoot 在 Android bind mount/FUSE 路径上可能无法
        // chdir/getcwd，因此后端实际运行使用 rootfs 内部的 Linux 路径。
        fun getRuntimePluginsDir(context: Context) = File(
            context.filesDir,
            "usr/var/lib/proot-distro/installed-rootfs/ubuntu/opt/omnimaster/runtime_plugins"
        )

        fun getRuntimePluginGuestDir(pluginId: String) = "/opt/omnimaster/runtime_plugins/$pluginId"
    }
    
    // 已发现的插件
    private val _discoveredPlugins = MutableStateFlow<List<DiscoveredPlugin>>(emptyList())
    val discoveredPlugins: StateFlow<List<DiscoveredPlugin>> = _discoveredPlugins.asStateFlow()
    
    // 已激活的插件
    private val _activatedPlugins = MutableStateFlow<Set<String>>(emptySet())
    val activatedPlugins: StateFlow<Set<String>> = _activatedPlugins.asStateFlow()
    
    // Extension Host Service 连接
    private var extensionHostService: IExtensionHostService? = null
    private var isServiceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "ExtensionHostService connected")
            extensionHostService = IExtensionHostService.Stub.asInterface(service)
            isServiceBound = true
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "ExtensionHostService disconnected")
            extensionHostService = null
            isServiceBound = false
        }
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var externalRootObserver: FileObserver? = null
    private val externalPluginDirObservers = mutableMapOf<String, FileObserver>()
    private var pendingScanJob: Job? = null
    
    /**
     * 初始化插件管理器
     */
    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Initializing ExternalPluginManager...")
            
            // 1. 确保目录存在
            ensureDirectoriesExist()
            
            // 2. 绑定 Extension Host Service
            bindExtensionHostService()
            
            // 3. 扫描并发现插件
            scanPlugins()

            // 4. 监听外部插件目录，用户直接创建/修改 plugin.json 时自动刷新
            startExternalPluginWatchers()
            
            Log.d(TAG, "ExternalPluginManager initialized, found ${_discoveredPlugins.value.size} plugins")
        }
    }
    
    /**
     * 确保必要的目录存在
     */
    private fun ensureDirectoriesExist() {
        EXTERNAL_PLUGINS_DIR.mkdirs()
        getBuiltinPluginsDir(context).mkdirs()
        getInstalledPluginsDir(context).mkdirs()
        getRuntimePluginsDir(context).mkdirs()
        installBundledPluginSamples()
        
        Log.d(TAG, "Plugin directories:")
        Log.d(TAG, "  External: ${EXTERNAL_PLUGINS_DIR.absolutePath}")
        Log.d(TAG, "  Builtin: ${getBuiltinPluginsDir(context).absolutePath}")
        Log.d(TAG, "  Installed: ${getInstalledPluginsDir(context).absolutePath}")
        Log.d(TAG, "  Runtime: ${getRuntimePluginsDir(context).absolutePath}")
    }

    private fun installBundledPluginSamples() {
        val samples = mapOf(
            "counter-demo" to "com.rostrum.demo.counter"
        )
        samples.forEach { (assetPath, pluginId) ->
            installBundledPluginSample(assetPath, File(getInstalledPluginsDir(context), pluginId), pluginId)
            installBundledPluginSample(assetPath, File(EXTERNAL_PLUGINS_DIR, pluginId), pluginId)
        }
    }

    private fun installBundledPluginSample(assetPath: String, targetDir: File, pluginId: String) {
            val manifestFile = File(targetDir, "plugin.json")
            if (manifestFile.exists()) return
            runCatching {
                copyAssetDirectory(assetPath, targetDir)
                Log.d(TAG, "Installed bundled plugin sample: $pluginId -> ${targetDir.absolutePath}")
            }.onFailure { error ->
                Log.w(TAG, "Failed to install bundled plugin sample $pluginId to ${targetDir.absolutePath}: ${error.message}")
            }
    }

    private fun copyAssetDirectory(assetPath: String, targetDir: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            targetDir.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                targetDir.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }

        targetDir.mkdirs()
        children.forEach { child ->
            copyAssetDirectory("$assetPath/$child", File(targetDir, child))
        }
    }
    
    /**
     * 绑定 Extension Host Service
     */
    private fun bindExtensionHostService() {
        val intent = Intent(context, ExtensionHostService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    /**
     * 扫描所有插件目录
     */
    suspend fun scanPlugins() {
        val plugins = mutableListOf<DiscoveredPlugin>()
        
        // 扫描外部插件目录
        plugins.addAll(scanDirectory(EXTERNAL_PLUGINS_DIR, PluginSource.EXTERNAL))
        
        // 扫描内置插件目录
        plugins.addAll(scanDirectory(getBuiltinPluginsDir(context), PluginSource.BUILTIN))
        
        // 扫描已安装插件目录
        plugins.addAll(scanDirectory(getInstalledPluginsDir(context), PluginSource.INSTALLED))
        
        _discoveredPlugins.value = plugins
        
        Log.d(TAG, "Discovered ${plugins.size} plugins:")
        plugins.forEach { plugin ->
            Log.d(TAG, "  - ${plugin.manifest.id} (${plugin.source})")
        }

        refreshExternalPluginDirObservers()
    }

    /**
     * 监听 /storage/emulated/0/OmniMaster/plugins 下的目录和文件变化。
     *
     * 这样用户或 Agent 直接创建自定义前端/后端小程序后，不需要重启应用，
     * StateFlow 会刷新，左侧抽屉会跟随 Compose 状态更新。这里必须递归监听
     * frontend/backend/data 之外的插件源文件，否则分步创建 plugin.json、index.html、
     * server.py 时可能只镜像到早期的 plugin.json。
     */
    private fun startExternalPluginWatchers() {
        if (externalRootObserver != null) return

        val events = FileObserver.CREATE or
            FileObserver.DELETE or
            FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or
            FileObserver.CLOSE_WRITE or
            FileObserver.MODIFY

        externalRootObserver = object : FileObserver(EXTERNAL_PLUGINS_DIR.absolutePath, events) {
            override fun onEvent(event: Int, path: String?) {
                if (path.isNullOrBlank()) return
                schedulePluginScan("root:$path")
            }
        }.also { observer ->
            observer.startWatching()
            Log.d(TAG, "Started external plugin root watcher: ${EXTERNAL_PLUGINS_DIR.absolutePath}")
        }

        refreshExternalPluginDirObservers()
    }

    private fun refreshExternalPluginDirObservers() {
        if (externalRootObserver == null) return
        val currentDirs = EXTERNAL_PLUGINS_DIR.listFiles()
            ?.filter { it.isDirectory }
            ?.flatMap { pluginDir ->
                pluginDir.walkTopDown()
                    .filter { it.isDirectory }
                    .map { it.absolutePath }
                    .toList()
            }
            ?.toSet()
            .orEmpty()

        val removedDirs = externalPluginDirObservers.keys - currentDirs
        removedDirs.forEach { dir ->
            externalPluginDirObservers.remove(dir)?.stopWatching()
        }

        val events = FileObserver.CREATE or
            FileObserver.DELETE or
            FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or
            FileObserver.CLOSE_WRITE or
            FileObserver.MODIFY

        currentDirs.forEach { dir ->
            if (dir !in externalPluginDirObservers) {
                externalPluginDirObservers[dir] = object : FileObserver(dir, events) {
                    override fun onEvent(event: Int, path: String?) {
                        schedulePluginScan("plugin:${File(dir).name}/${path.orEmpty()}")
                    }
                }.also { it.startWatching() }
            }
        }
    }

    private fun schedulePluginScan(reason: String) {
        pendingScanJob?.cancel()
        pendingScanJob = scope.launch {
            delay(1200)
            Log.d(TAG, "Rescanning external plugins after file event: $reason")
            scanPlugins()
        }
    }
    
    /**
     * 扫描单个目录
     */
    private fun scanDirectory(directory: File, source: PluginSource): List<DiscoveredPlugin> {
        if (!directory.exists() || !directory.isDirectory) {
            return emptyList()
        }
        
        val plugins = mutableListOf<DiscoveredPlugin>()
        
        directory.listFiles()?.filter { it.isDirectory }?.forEach { pluginDir ->
            val manifestFile = File(pluginDir, "plugin.json")
            if (manifestFile.exists()) {
                try {
                    val manifest = ContributionParser.parseManifest(manifestFile).getOrNull()
                    if (manifest != null) {
                        // 查找插件 APK
                        val apkFile = findPluginApk(pluginDir, manifest)
                        
                        val runtimeDir = mirrorPluginForRuntime(pluginDir, manifest.id)

                        plugins.add(DiscoveredPlugin(
                            manifest = manifest,
                            pluginDir = pluginDir,
                            runtimeDir = runtimeDir,
                            runtimeGuestDir = getRuntimePluginGuestDir(manifest.id),
                            apkFile = apkFile,
                            source = source,
                            isEnabled = PluginStateManager.isEnabled(manifest.id)
                        ))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse plugin manifest: ${manifestFile.absolutePath}", e)
                }
            }
        }
        
        return plugins
    }

    private fun mirrorPluginForRuntime(pluginDir: File, pluginId: String): File {
        val runtimeRoot = getRuntimePluginsDir(context)
        val targetDir = File(runtimeRoot, pluginId)
        val tmpDir = File(runtimeRoot, ".tmp_${pluginId}_${System.currentTimeMillis()}")
        return runCatching {
            if (tmpDir.exists()) tmpDir.deleteRecursively()
            tmpDir.mkdirs()
            pluginDir.copyRecursively(tmpDir, overwrite = true)
            preserveRuntimeData(targetDir, tmpDir)
            if (targetDir.exists()) targetDir.deleteRecursively()
            if (!tmpDir.renameTo(targetDir)) {
                tmpDir.copyRecursively(targetDir, overwrite = true)
                tmpDir.deleteRecursively()
            }
            targetDir
        }.onFailure { error ->
            Log.w(TAG, "Failed to mirror plugin $pluginId for runtime: ${error.message}")
            if (tmpDir.exists()) tmpDir.deleteRecursively()
        }.getOrDefault(pluginDir)
    }

    private fun preserveRuntimeData(currentRuntimeDir: File, nextRuntimeDir: File) {
        val currentDataDir = File(currentRuntimeDir, "data")
        if (!currentDataDir.exists() || !currentDataDir.isDirectory) return
        val nextDataDir = File(nextRuntimeDir, "data")
        if (nextDataDir.exists()) nextDataDir.deleteRecursively()
        currentDataDir.copyRecursively(nextDataDir, overwrite = true)
    }
    
    /**
     * 查找插件 APK 文件
     */
    private fun findPluginApk(pluginDir: File, manifest: PluginManifest): File? {
        val possibleNames = listOf(
            "${manifest.id}.apk",
            "${manifest.id.substringAfterLast(".")}.apk",
            "plugin.apk",
            "${pluginDir.name}.apk"
        )
        
        for (name in possibleNames) {
            val file = File(pluginDir, name)
            if (file.exists()) {
                return file
            }
        }
        
        // 查找目录下任何 APK 文件
        return pluginDir.listFiles()?.find { it.extension.equals("apk", ignoreCase = true) }
    }
    
    /**
     * 激活插件
     * 
     * 在独立进程中加载并激活插件
     */
    suspend fun activatePlugin(pluginId: String): Result<Unit> {
        val plugin = _discoveredPlugins.value.find { it.manifest.id == pluginId }
            ?: return Result.failure(IllegalArgumentException("Plugin not found: $pluginId"))
        
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Activating plugin: $pluginId")
                
                // 启动插件进程服务
                val intent = Intent(context, PluginProcessService::class.java).apply {
                    putExtra("plugin_id", pluginId)
                    putExtra("plugin_dir", plugin.pluginDir.absolutePath)
                }
                context.startService(intent)
                
                // 更新状态
                PluginStateManager.setEnabled(pluginId, true)
                _activatedPlugins.value = _activatedPlugins.value + pluginId
                
                // 重新扫描以更新状态
                scanPlugins()
                
                Log.d(TAG, "Plugin activated: $pluginId")
                Result.success(Unit)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to activate plugin: $pluginId", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * 停用插件
     */
    suspend fun deactivatePlugin(pluginId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Deactivating plugin: $pluginId")
                
                // 更新状态
                PluginStateManager.setEnabled(pluginId, false)
                _activatedPlugins.value = _activatedPlugins.value - pluginId
                
                // 重新扫描以更新状态
                scanPlugins()
                
                Log.d(TAG, "Plugin deactivated: $pluginId")
                Result.success(Unit)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to deactivate plugin: $pluginId", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * 触发激活事件
     * 
     * 当满足激活条件时（如打开特定文件），激活匹配的插件
     * 
     * @param event 激活事件（如 "onFile:*.pdf"）
     */
    suspend fun triggerActivationEvent(event: String) {
        val matchingPlugins = _discoveredPlugins.value.filter { plugin ->
            plugin.isEnabled && matchesActivationEvent(plugin.manifest, event)
        }
        
        for (plugin in matchingPlugins) {
            if (plugin.manifest.id !in _activatedPlugins.value) {
                activatePlugin(plugin.manifest.id)
            }
        }
    }
    
    /**
     * 检查激活事件是否匹配
     */
    private fun matchesActivationEvent(manifest: PluginManifest, event: String): Boolean {
        for (activationEvent in manifest.activationEvents) {
            when {
                activationEvent == "*" -> return true
                activationEvent == "onStartup" && event == "onStartup" -> return true
                activationEvent.startsWith("onFile:") && event.startsWith("onFile:") -> {
                    val pattern = activationEvent.substringAfter("onFile:")
                    val fileExt = event.substringAfter("onFile:")
                    
                    // 简单通配符匹配
                    if (pattern == "*" || pattern == fileExt) {
                        return true
                    }
                    if (pattern.startsWith("*.")) {
                        val ext = pattern.substringAfter("*.")
                        if (fileExt.endsWith(".$ext") || fileExt == ext) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }
    
    /**
     * 查找能处理指定文件的预览插件
     */
    fun findPreviewPlugin(filePath: String): DiscoveredPlugin? {
        val extension = File(filePath).extension.lowercase()
        
        return _discoveredPlugins.value.find { plugin ->
            plugin.isEnabled && plugin.manifest.contributes?.filePreview?.any { preview ->
                preview.extensions?.any { it.equals(".$extension", ignoreCase = true) } == true
            } == true
        }
    }
    
    /**
     * 安装插件（从 APK 文件）
     */
    suspend fun installPlugin(apkFile: File): Result<DiscoveredPlugin> {
        return withContext(Dispatchers.IO) {
            try {
                if (!apkFile.exists() || !apkFile.isFile) {
                    return@withContext Result.failure(IllegalArgumentException("Plugin package not found: ${apkFile.absolutePath}"))
                }

                val manifestText = readManifestFromArchive(apkFile)
                    ?: return@withContext Result.failure(IllegalArgumentException("plugin.json not found in package: ${apkFile.name}"))
                val tempManifest = File(context.cacheDir, "plugin_manifest_${System.currentTimeMillis()}.json")
                tempManifest.writeText(manifestText)
                val manifest = ContributionParser.parseManifest(tempManifest).getOrElse {
                    tempManifest.delete()
                    return@withContext Result.failure(it)
                }
                tempManifest.delete()

                val targetDir = File(getInstalledPluginsDir(context), manifest.id)
                val tmpDir = File(getInstalledPluginsDir(context), ".tmp_${manifest.id}_${System.currentTimeMillis()}")
                if (tmpDir.exists()) tmpDir.deleteRecursively()
                tmpDir.mkdirs()

                unzipPluginPackage(apkFile, tmpDir)
                val manifestAtRoot = File(tmpDir, "plugin.json")
                if (!manifestAtRoot.exists()) {
                    File(tmpDir, "assets/plugin.json").takeIf { it.exists() }?.copyTo(manifestAtRoot, overwrite = true)
                }
                if (!manifestAtRoot.exists()) {
                    tmpDir.deleteRecursively()
                    return@withContext Result.failure(IllegalArgumentException("Extracted package does not contain plugin.json"))
                }

                if (targetDir.exists()) targetDir.deleteRecursively()
                if (!tmpDir.renameTo(targetDir)) {
                    tmpDir.copyRecursively(targetDir, overwrite = true)
                    tmpDir.deleteRecursively()
                }

                PluginStateManager.setEnabled(manifest.id, true)
                scanPlugins()

                val installed = _discoveredPlugins.value.find { it.manifest.id == manifest.id }
                    ?: DiscoveredPlugin(
                        manifest = manifest,
                        pluginDir = targetDir,
                        runtimeDir = targetDir,
                        runtimeGuestDir = targetDir.absolutePath,
                        apkFile = findPluginApk(targetDir, manifest),
                        source = PluginSource.INSTALLED,
                        isEnabled = true
                    )
                Result.success(installed)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun readManifestFromArchive(packageFile: File): String? {
        ZipFile(packageFile).use { zip ->
            val entry = zip.getEntry("plugin.json")
                ?: zip.getEntry("assets/plugin.json")
                ?: zip.entries().asSequence().firstOrNull { it.name.endsWith("/plugin.json") }
                ?: return null
            return zip.getInputStream(entry).bufferedReader().use { it.readText() }
        }
    }

    private fun unzipPluginPackage(packageFile: File, targetDir: File) {
        val canonicalTarget = targetDir.canonicalFile
        ZipFile(packageFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val outFile = File(targetDir, entry.name).canonicalFile
                if (!outFile.path.startsWith(canonicalTarget.path + File.separator)) {
                    throw IllegalArgumentException("Unsafe zip entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }
    
    /**
     * 卸载插件
     */
    suspend fun uninstallPlugin(pluginId: String): Result<Unit> {
        val plugin = _discoveredPlugins.value.find { it.manifest.id == pluginId }
            ?: return Result.failure(IllegalArgumentException("Plugin not found: $pluginId"))
        
        // 只能卸载用户安装的插件
        if (plugin.source == PluginSource.BUILTIN) {
            return Result.failure(IllegalStateException("Cannot uninstall builtin plugin"))
        }
        
        return withContext(Dispatchers.IO) {
            try {
                // 先停用
                deactivatePlugin(pluginId)
                
                // 删除插件目录
                plugin.pluginDir.deleteRecursively()
                
                // 重新扫描
                scanPlugins()
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 获取 Extension Host Service
     */
    fun getExtensionHostService(): IExtensionHostService? = extensionHostService
    
    /**
     * 释放资源
     */
    fun destroy() {
        pendingScanJob?.cancel()
        externalRootObserver?.stopWatching()
        externalRootObserver = null
        externalPluginDirObservers.values.forEach { it.stopWatching() }
        externalPluginDirObservers.clear()
        if (isServiceBound) {
            context.unbindService(serviceConnection)
            isServiceBound = false
        }
        scope.cancel()
    }
}

/**
 * 已发现的插件
 */
data class DiscoveredPlugin(
    val manifest: PluginManifest,
    val pluginDir: File,
    val runtimeDir: File = pluginDir,
    val runtimeGuestDir: String = pluginDir.absolutePath,
    val apkFile: File?,
    val source: PluginSource,
    val isEnabled: Boolean
)

/**
 * 插件来源
 */
enum class PluginSource {
    BUILTIN,    // 内置插件
    INSTALLED,  // 用户安装的插件
    EXTERNAL    // 外部目录的插件
}
