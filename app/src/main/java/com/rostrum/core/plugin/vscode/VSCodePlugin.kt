package com.rostrum.core.plugin.vscode

import com.rostrum.core.plugin.Plugin
import com.rostrum.core.plugin.PluginCapability
import com.rostrum.core.plugin.PluginCategory
import com.rostrum.core.plugin.PluginContext
import com.rostrum.core.plugin.PluginManifest
import org.json.JSONObject

/**
 * VSCode插件包装器
 * 
 * 将VSCode插件包装为OmniMaster Plugin接口
 */
class VSCodePlugin(
    private val metadata: PluginManifest,
    private val location: String,
    private val packageJson: JSONObject,
    private val extensionHost: VSCodeExtensionHost
) : Plugin {
    
    override val id: String = metadata.id
    override val name: String = metadata.name
    override val version: String = metadata.version
    override val author: String = metadata.author
    override val description: String = metadata.description
    override val category: PluginCategory = metadata.category
    override val dependencies: List<String> = metadata.dependencies.keys.toList()
    
    private var pluginContext: PluginContext? = null
    private var isActivated = false
    
    override suspend fun initialize(context: PluginContext): Result<Unit> {
        this.pluginContext = context
        return Result.success(Unit)
    }
    
    override suspend fun onActivate(): Result<Unit> {
        if (isActivated) {
            return Result.success(Unit)
        }
        
        return try {
            // 通过Extension Host激活插件
            extensionHost.activateExtension(
                extensionId = id,
                extensionPath = location,
                context = pluginContext!!
            )
            
            isActivated = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun onDeactivate(): Result<Unit> {
        if (!isActivated) {
            return Result.success(Unit)
        }
        
        return try {
            // 通过Extension Host停用插件
            extensionHost.deactivateExtension(id)
            isActivated = false
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun onDestroy(): Result<Unit> {
        pluginContext = null
        return Result.success(Unit)
    }
    
    override fun getCapabilities(): List<PluginCapability> {
        return metadata.capabilities.mapNotNull { capName ->
            try {
                PluginCapability.valueOf(capName.uppercase())
            } catch (e: Exception) {
                null
            }
        }
    }
}

