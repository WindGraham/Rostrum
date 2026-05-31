package com.rostrum.core.plugin.ipc.process

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rostrum.core.plugin.contribution.PluginManifest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * 进程池管理器
 * 
 * 管理插件进程的创建、复用和销毁
 * 支持多个插件共享同一个进程（进程组）
 */
class ProcessPool(private val context: Context) {
    private val TAG = "ProcessPool"
    
    // 进程组配置（插件ID -> 进程组名称）
    private val pluginProcessGroups = mutableMapOf<String, String>()
    
    // 进程组信息（进程组名称 -> 进程组）
    private val processGroups = mutableMapOf<String, ProcessGroup>()
    
    // 进程组互斥锁
    private val mutex = Mutex()
    
    /**
     * 获取或创建插件进程
     * 
     * @param manifest 插件清单
     * @param pluginDir 插件目录
     * @return 进程组信息
     */
    suspend fun getOrCreateProcess(
        manifest: PluginManifest,
        pluginDir: File
    ): Result<ProcessGroup> = mutex.withLock {
        try {
            // 1. 确定进程组名称
            val groupName = determineProcessGroup(manifest)
            
            // 2. 获取或创建进程组
            val group = processGroups.getOrPut(groupName) {
                createProcessGroup(groupName)
            }
            
            // 3. 注册插件到进程组
            pluginProcessGroups[manifest.id] = groupName
            group.addPlugin(manifest.id, pluginDir)
            
            Log.d(TAG, "Plugin ${manifest.id} assigned to process group: $groupName")
            Result.success(group)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get or create process for plugin: ${manifest.id}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 释放插件进程
     * 
     * @param pluginId 插件ID
     */
    suspend fun releaseProcess(pluginId: String) = mutex.withLock {
        val groupName = pluginProcessGroups.remove(pluginId) ?: return@withLock
        
        val group = processGroups[groupName] ?: return@withLock
        group.removePlugin(pluginId)
        
        // 如果进程组为空，销毁进程组
        if (group.isEmpty()) {
            destroyProcessGroup(groupName)
            processGroups.remove(groupName)
            Log.d(TAG, "Process group destroyed: $groupName")
        }
    }
    
    /**
     * 确定进程组名称
     * 
     * 策略：
     * 1. 如果插件指定了进程组，使用指定名称
     * 2. 否则，根据插件分类创建进程组
     */
    private fun determineProcessGroup(manifest: PluginManifest): String {
        // TODO: 从 manifest 中读取进程组配置
        // 当前实现：根据插件分类创建进程组
        val category = manifest.category?.name?.lowercase() ?: "default"
        return "plugin_$category"
    }
    
    /**
     * 创建进程组
     */
    private fun createProcessGroup(groupName: String): ProcessGroup {
        val processName = ":$groupName"
        val group = ProcessGroup(
            name = groupName,
            processName = processName,
            context = context
        )
        
        Log.d(TAG, "Created process group: $groupName")
        return group
    }
    
    /**
     * 销毁进程组
     */
    private fun destroyProcessGroup(groupName: String) {
        val group = processGroups[groupName] ?: return
        group.destroy()
        Log.d(TAG, "Destroyed process group: $groupName")
    }
    
    /**
     * 获取所有进程组信息
     */
    suspend fun getAllProcessGroups(): Map<String, ProcessGroupInfo> = mutex.withLock {
        processGroups.mapValues { (_, group) ->
            ProcessGroupInfo(
                name = group.name,
                processName = group.processName,
                pluginCount = group.pluginCount,
                isActive = group.isActive
            )
        }
    }
}

/**
 * 进程组
 */
class ProcessGroup(
    val name: String,
    val processName: String,
    private val context: Context
) {
    private val plugins = mutableSetOf<String>()
    private val pluginDirs = mutableMapOf<String, File>()
    private val mutex = Mutex()
    
    /**
     * 添加插件到进程组
     */
    suspend fun addPlugin(pluginId: String, pluginDir: File) = mutex.withLock {
        plugins.add(pluginId)
        pluginDirs[pluginId] = pluginDir
        
        // 如果进程组刚创建，启动进程
        if (plugins.size == 1) {
            startProcess()
        }
    }
    
    /**
     * 从进程组移除插件
     */
    suspend fun removePlugin(pluginId: String) = mutex.withLock {
        plugins.remove(pluginId)
        pluginDirs.remove(pluginId)
        
        // 如果进程组为空，停止进程
        if (plugins.isEmpty()) {
            stopProcess()
        }
    }
    
    /**
     * 检查进程组是否为空
     */
    suspend fun isEmpty(): Boolean = mutex.withLock {
        plugins.isEmpty()
    }
    
    /**
     * 获取插件数量
     */
    val pluginCount: Int
        get() = plugins.size
    
    /**
     * 检查进程组是否激活
     */
    val isActive: Boolean
        get() = plugins.isNotEmpty()
    
    /**
     * 启动进程
     */
    private fun startProcess() {
        // 启动 PluginProcessService
        val intent = Intent(context, com.rostrum.core.plugin.ipc.PluginProcessService::class.java)
        intent.putExtra("process_group", name)
        context.startService(intent)
    }
    
    /**
     * 停止进程
     */
    private fun stopProcess() {
        // 停止 PluginProcessService
        val intent = Intent(context, com.rostrum.core.plugin.ipc.PluginProcessService::class.java)
        context.stopService(intent)
    }
    
    /**
     * 销毁进程组
     */
    fun destroy() {
        stopProcess()
        plugins.clear()
        pluginDirs.clear()
    }
}

/**
 * 进程组信息
 */
data class ProcessGroupInfo(
    val name: String,
    val processName: String,
    val pluginCount: Int,
    val isActive: Boolean
)

