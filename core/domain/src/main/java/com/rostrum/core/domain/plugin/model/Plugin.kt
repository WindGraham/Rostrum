package com.rostrum.core.domain.plugin.model

/**
 * 插件领域模型
 * 
 * 定义插件的基本属性和生命周期
 */
data class Plugin(
    /** 插件 ID（唯一标识符） */
    val id: String,
    
    /** 插件名称 */
    val name: String,
    
    /** 插件版本 */
    val version: String,
    
    /** 插件描述 */
    val description: String,
    
    /** 插件作者 */
    val author: String? = null,
    
    /** 插件类型 */
    val type: PluginType,
    
    /** 激活事件 */
    val activationEvents: List<ActivationEvent> = emptyList(),
    
    /** 扩展点贡献 */
    val contributions: List<ExtensionContribution> = emptyList(),
    
    /** 插件状态 */
    val state: PluginState = PluginState.INACTIVE,
    
    /** 是否启用 */
    val enabled: Boolean = true
)

/**
 * 插件类型
 */
enum class PluginType {
    /** 内置插件 */
    BUILTIN,
    
    /** 外部插件 */
    EXTERNAL,
    
    /** VS Code 兼容插件 */
    VSCODE_COMPATIBLE,
    
    /** Native 插件 */
    NATIVE
}

/**
 * 插件状态
 */
enum class PluginState {
    /** 未激活 */
    INACTIVE,
    
    /** 激活中 */
    ACTIVATING,
    
    /** 已激活 */
    ACTIVE,
    
    /** 停用中 */
    DEACTIVATING,
    
    /** 已停用 */
    DEACTIVATED,
    
    /** 错误 */
    ERROR
}

/**
 * 激活事件
 */
data class ActivationEvent(
    /** 事件类型 */
    val type: ActivationEventType,
    
    /** 事件参数 */
    val parameter: String? = null
)

/**
 * 激活事件类型
 */
enum class ActivationEventType {
    /** 启动时激活 */
    ON_STARTUP,
    
    /** 打开特定语言文件时激活 */
    ON_LANGUAGE,
    
    /** 执行特定命令时激活 */
    ON_COMMAND,
    
    /** 打开特定文件类型时激活 */
    ON_FILE_SYSTEM,
    
    /** 视图可见时激活 */
    ON_VIEW,
    
    /** 工作区包含特定文件时激活 */
    WORKSPACE_CONTAINS
}

/**
 * 扩展点贡献
 */
data class ExtensionContribution(
    /** 扩展点 ID */
    val extensionPointId: String,
    
    /** 贡献数据 */
    val data: Map<String, Any>
)

/**
 * 扩展点定义
 */
data class ExtensionPoint(
    /** 扩展点 ID */
    val id: String,
    
    /** 扩展点名称 */
    val name: String,
    
    /** 扩展点描述 */
    val description: String,
    
    /** Schema（用于验证贡献数据） */
    val schema: Map<String, Any>? = null
) {
    companion object {
        // 预定义扩展点
        val COMMANDS = ExtensionPoint(
            id = "commands",
            name = "Commands",
            description = "注册命令"
        )
        
        val FILE_PREVIEW = ExtensionPoint(
            id = "filePreview",
            name = "File Preview",
            description = "文件预览处理器"
        )
        
        val LANGUAGE_SUPPORT = ExtensionPoint(
            id = "languageSupport",
            name = "Language Support",
            description = "语言支持"
        )
        
        val THEMES = ExtensionPoint(
            id = "themes",
            name = "Themes",
            description = "主题贡献"
        )
        
    }
}
