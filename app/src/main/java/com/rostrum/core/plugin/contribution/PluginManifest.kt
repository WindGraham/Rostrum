package com.rostrum.core.plugin.contribution

import com.rostrum.core.plugin.PluginCategory

/**
 * 插件清单（从 plugin.json 解析）
 * 
 * 对应 VSCode 的 package.json contributes 字段
 */
data class PluginManifest(
    /**
     * 插件唯一标识符
     */
    val id: String,
    
    /**
     * 插件显示名称
     */
    val name: String,
    
    /**
     * 版本号
     */
    val version: String,
    
    /**
     * 描述
     */
    val description: String? = null,
    
    /**
     * 作者
     */
    val author: String? = null,
    
    /**
     * 插件入口类全限定名
     */
    val main: String,
    
    /**
     * 图标路径
     */
    val icon: String? = null,
    
    /**
     * 扩展点声明
     */
    val contributes: ContributionPoints? = null,
    
    /**
     * 激活事件列表
     */
    val activationEvents: List<String> = emptyList(),
    
    /**
     * 权限声明
     */
    val permissions: List<String> = emptyList(),
    
    /**
     * 依赖声明
     */
    val dependencies: Map<String, String> = emptyMap(),
    
    /**
     * 插件分类
     */
    val category: PluginCategory? = null
)

/**
 * 扩展点声明
 */
data class ContributionPoints(
    /**
     * 文件预览扩展点
     */
    val filePreview: List<FilePreviewContribution>? = null,
    
    /**
     * 文件编辑扩展点
     */
    val fileEditor: List<FileEditorContribution>? = null,
    
    /**
     * 命令扩展点
     */
    val commands: List<CommandContribution>? = null,
    
    /**
     * 菜单扩展点
     */
    val menus: MenuContributions? = null,
    
    /**
     * 语言支持扩展点
     */
    val languages: List<LanguageContribution>? = null,
    
    /**
     * 主题扩展点
     */
    val themes: List<ThemeContribution>? = null,

    /**
     * 自定义前端入口扩展点
     */
    val frontends: List<FrontendContribution>? = null
)

/**
 * 文件预览扩展点
 */
data class FilePreviewContribution(
    val mimeTypes: List<String>? = null,
    val extensions: List<String>? = null,
    val priority: Int = 50
)

/**
 * 文件编辑扩展点
 */
data class FileEditorContribution(
    val mimeTypes: List<String>? = null,
    val extensions: List<String>? = null,
    val priority: Int = 50
)

/**
 * 命令扩展点
 */
data class CommandContribution(
    val command: String,
    val title: String,
    val category: String? = null,
    val icon: String? = null,
    val description: String? = null
)

/**
 * 菜单扩展点
 */
data class MenuContributions(
    val fileContextMenu: List<MenuContribution>? = null,
    val editorContextMenu: List<MenuContribution>? = null,
    val commandPalette: List<MenuContribution>? = null
)

/**
 * 菜单项贡献
 */
data class MenuContribution(
    val command: String,
    val when_: String? = null,  // 条件表达式（使用 when_ 避免 Kotlin 关键字冲突）
    val group: String? = null,
    val order: Int? = null
)

/**
 * 语言支持扩展点
 */
data class LanguageContribution(
    val id: String,
    val extensions: List<String>? = null,
    val filenames: List<String>? = null,
    val filenamePatterns: List<String>? = null,
    val mimeTypes: List<String>? = null
)

/**
 * 主题扩展点
 */
data class ThemeContribution(
    val id: String,
    val label: String,
    val uiTheme: String,  // "dark" or "light"
    val path: String
)

/**
 * 自定义前端入口
 *
 * path 指向插件目录内的 HTML 文件，例如 "frontend/index.html"。
 * backendServiceId/backendCommand 用于声明可选的 PRoot 后端服务。
 */
data class FrontendContribution(
    val id: String,
    val title: String,
    val path: String,
    val icon: String? = null,
    val showInDrawer: Boolean = true,
    val backendServiceId: String? = null,
    val backendCommand: String? = null
)
