package com.rostrum.core.plugin.loaders

import android.content.Context
import com.rostrum.core.plugin.Plugin
import com.rostrum.core.plugin.PluginLoader
import com.rostrum.core.plugin.PluginPackage
import com.rostrum.core.plugin.vscode.VSCodePlugin
import com.rostrum.core.plugin.vscode.VSCodeExtensionHost
import org.json.JSONObject
import java.io.File

/**
 * VSCode插件加载器
 * 
 * 加载VSCode格式的插件，通过Extension Host运行
 */
class VSCodePluginLoader(
    private val context: Context
) : PluginLoader {
    
    private val extensionHost: VSCodeExtensionHost = VSCodeExtensionHost(context)
    
    override suspend fun load(pluginPackage: PluginPackage): Plugin {
        if (!canLoad(pluginPackage)) {
            throw IllegalArgumentException("Cannot load VSCode plugin: ${pluginPackage.id}")
        }
        
        // 读取package.json
        val packageJson = readPackageJson(pluginPackage)
        
        // 创建VSCode插件包装器
        val plugin = VSCodePlugin(
            metadata = pluginPackage.manifest,
            location = pluginPackage.filePath,
            packageJson = packageJson,
            extensionHost = extensionHost
        )
        
        return plugin
    }
    
    override fun canLoad(pluginPackage: PluginPackage): Boolean {
        // VSCode插件：.vsix文件，或目录中包含package.json
        val filePath = pluginPackage.filePath
        return filePath.endsWith(".vsix") || 
               File(filePath, "package.json").exists()
    }
    
    /**
     * 读取package.json
     */
    private fun readPackageJson(pluginPackage: PluginPackage): JSONObject {
        val packageJsonFile = if (pluginPackage.filePath.endsWith(".vsix")) {
            // TODO: 从.vsix文件中提取package.json
            throw UnsupportedOperationException("VSIX file extraction not implemented")
        } else {
            File(pluginPackage.filePath, "package.json")
        }
        
        if (!packageJsonFile.exists()) {
            throw IllegalArgumentException("package.json not found in plugin: ${pluginPackage.id}")
        }
        
        val content = packageJsonFile.readText()
        return JSONObject(content)
    }
}

