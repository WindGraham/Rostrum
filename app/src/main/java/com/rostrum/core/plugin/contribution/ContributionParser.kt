package com.rostrum.core.plugin.contribution

import android.util.Log
import com.rostrum.core.plugin.PluginCategory
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

/**
 * 配置文件解析器
 * 
 * 解析 plugin.json 文件，提取扩展点声明和配置信息
 */
object ContributionParser {
    private const val TAG = "ContributionParser"
    
    /**
     * 解析 plugin.json 文件
     * 
     * @param jsonFile plugin.json 文件路径
     * @return 解析结果，失败返回 Result.failure
     */
    fun parseManifest(jsonFile: File): Result<PluginManifest> {
        return try {
            if (!jsonFile.exists()) {
                return Result.failure(IllegalArgumentException("Plugin manifest file not found: ${jsonFile.absolutePath}"))
            }
            
            val jsonContent = jsonFile.readText()
            val json = JSONObject(jsonContent)
            
            val manifest = PluginManifest(
                id = json.getString("id"),
                name = json.getString("name"),
                version = json.getString("version"),
                description = json.optString("description", null),
                author = json.optString("author", null),
                main = json.getString("main"),
                icon = json.optString("icon", null),
                contributes = parseContributes(json.optJSONObject("contributes")),
                activationEvents = parseStringArray(json.optJSONArray("activationEvents")) ?: emptyList(),
                permissions = parseStringArray(json.optJSONArray("permissions")) ?: emptyList(),
                dependencies = parseDependencies(json.optJSONObject("dependencies")),
                category = parseCategory(json.optString("category", null))
            )
            
            Result.success(manifest)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse plugin manifest: ${jsonFile.absolutePath}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 解析扩展点声明
     */
    private fun parseContributes(contributesJson: JSONObject?): ContributionPoints? {
        if (contributesJson == null) return null
        
        return ContributionPoints(
            filePreview = parseFilePreviewContributions(contributesJson.optJSONArray("filePreview")),
            fileEditor = parseFileEditorContributions(contributesJson.optJSONArray("fileEditor")),
            commands = parseCommandContributions(contributesJson.optJSONArray("commands")),
            menus = parseMenuContributions(contributesJson.optJSONObject("menus")),
            languages = parseLanguageContributions(contributesJson.optJSONArray("languages")),
            themes = parseThemeContributions(contributesJson.optJSONArray("themes")),
            frontends = parseFrontendContributions(contributesJson.optJSONArray("frontends"))
        )
    }
    
    /**
     * 解析文件预览扩展点
     */
    private fun parseFilePreviewContributions(array: JSONArray?): List<FilePreviewContribution>? {
        if (array == null) return null
        
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            FilePreviewContribution(
                mimeTypes = parseStringArray(obj.optJSONArray("mimeTypes")),
                extensions = parseStringArray(obj.optJSONArray("extensions")),
                priority = obj.optInt("priority", 50)
            )
        }
    }
    
    /**
     * 解析文件编辑扩展点
     */
    private fun parseFileEditorContributions(array: JSONArray?): List<FileEditorContribution>? {
        if (array == null) return null
        
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            FileEditorContribution(
                mimeTypes = parseStringArray(obj.optJSONArray("mimeTypes")),
                extensions = parseStringArray(obj.optJSONArray("extensions")),
                priority = obj.optInt("priority", 50)
            )
        }
    }
    
    /**
     * 解析命令扩展点
     */
    private fun parseCommandContributions(array: JSONArray?): List<CommandContribution>? {
        if (array == null) return null
        
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            CommandContribution(
                command = obj.getString("command"),
                title = obj.getString("title"),
                category = obj.optString("category", null),
                icon = obj.optString("icon", null),
                description = obj.optString("description", null)
            )
        }
    }
    
    /**
     * 解析菜单扩展点
     */
    private fun parseMenuContributions(menusJson: JSONObject?): MenuContributions? {
        if (menusJson == null) return null
        
        return MenuContributions(
            fileContextMenu = parseMenuItems(menusJson.optJSONArray("fileContextMenu")),
            editorContextMenu = parseMenuItems(menusJson.optJSONArray("editorContextMenu")),
            commandPalette = parseMenuItems(menusJson.optJSONArray("commandPalette"))
        )
    }
    
    /**
     * 解析菜单项列表
     */
    private fun parseMenuItems(array: JSONArray?): List<MenuContribution>? {
        if (array == null) return null
        
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            MenuContribution(
                command = obj.getString("command"),
                when_ = obj.optString("when", null),  // JSON 中使用 "when"，Kotlin 中使用 when_
                group = obj.optString("group", null),
                order = if (obj.has("order")) obj.getInt("order") else null
            )
        }
    }
    
    /**
     * 解析语言支持扩展点
     */
    private fun parseLanguageContributions(array: JSONArray?): List<LanguageContribution>? {
        if (array == null) return null
        
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            LanguageContribution(
                id = obj.getString("id"),
                extensions = parseStringArray(obj.optJSONArray("extensions")),
                filenames = parseStringArray(obj.optJSONArray("filenames")),
                filenamePatterns = parseStringArray(obj.optJSONArray("filenamePatterns")),
                mimeTypes = parseStringArray(obj.optJSONArray("mimeTypes"))
            )
        }
    }
    
    /**
     * 解析主题扩展点
     */
    private fun parseThemeContributions(array: JSONArray?): List<ThemeContribution>? {
        if (array == null) return null
        
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            ThemeContribution(
                id = obj.getString("id"),
                label = obj.getString("label"),
                uiTheme = obj.getString("uiTheme"),
                path = obj.getString("path")
            )
        }
    }

    /**
     * 解析自定义前端入口扩展点
     */
    private fun parseFrontendContributions(array: JSONArray?): List<FrontendContribution>? {
        if (array == null) return null

        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            FrontendContribution(
                id = obj.getString("id"),
                title = obj.getString("title"),
                path = obj.getString("path"),
                icon = obj.optString("icon", null),
                showInDrawer = obj.optBoolean("showInDrawer", true),
                backendServiceId = obj.optString("backendServiceId", null),
                backendCommand = obj.optString("backendCommand", null)
            )
        }
    }
    
    /**
     * 解析字符串数组
     */
    private fun parseStringArray(array: JSONArray?): List<String>? {
        if (array == null) return null
        return (0 until array.length()).map { array.getString(it) }
    }
    
    /**
     * 解析依赖对象
     */
    private fun parseDependencies(depsJson: JSONObject?): Map<String, String> {
        if (depsJson == null) return emptyMap()
        
        val deps = mutableMapOf<String, String>()
        val keys = depsJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            deps[key] = depsJson.getString(key)
        }
        return deps
    }
    
    /**
     * 解析插件分类
     */
    private fun parseCategory(categoryStr: String?): PluginCategory? {
        if (categoryStr == null) return null
        
        return try {
            PluginCategory.valueOf(categoryStr.uppercase())
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
