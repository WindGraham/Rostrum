package com.rostrum.core.plugin

/**
 * 插件基础接口
 * 
 * 所有插件必须实现此接口，定义插件的基本信息和生命周期
 * 
 * @property id 插件唯一标识符，格式：com.rostrum.{category}.{name}
 * @property name 插件显示名称
 * @property version 版本号，遵循语义化版本（如 "1.0.0"）
 * @property author 作者名称
 * @property description 插件描述
 * @property category 插件分类
 * @property dependencies 依赖的其他插件ID列表
 */
interface Plugin {
    val id: String
    val name: String
    val version: String
    val author: String
    val description: String
    val category: PluginCategory
    val dependencies: List<String>
    
    /**
     * 初始化插件
     * 
     * @param context 插件上下文，提供API访问
     * @return 初始化结果
     */
    suspend fun initialize(context: PluginContext): Result<Unit>
    
    /**
     * 激活插件
     * 插件激活后可以开始提供服务
     * 
     * @return 激活结果
     */
    suspend fun onActivate(): Result<Unit>
    
    /**
     * 停用插件
     * 插件停用后停止提供服务，但保留状态
     * 
     * @return 停用结果
     */
    suspend fun onDeactivate(): Result<Unit>
    
    /**
     * 销毁插件
     * 清理所有资源，释放引用
     * 
     * @return 销毁结果
     */
    suspend fun onDestroy(): Result<Unit>
    
    /**
     * 获取插件能力声明
     * 
     * @return 插件支持的能力列表
     */
    fun getCapabilities(): List<PluginCapability>
}

/**
 * 插件元数据（用于插件清单）
 */
data class PluginMetadata(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val category: PluginCategory,
    val icon: String? = null,
    val dependencies: List<String> = emptyList(),
    val permissions: List<PluginPermission> = emptyList(),
    val minAppVersion: String? = null,
    val capabilities: List<PluginCapability> = emptyList()
)

/**
 * 插件分类
 */
enum class PluginCategory {
    PREVIEW,      // 文件预览
    EDITOR,       // 文件编辑
    LANGUAGE,     // 语言支持
    TOOL,         // 工具类
    AI_AGENT,     // AI代理
    THEME,        // 主题
    EXTENSION     // 其他扩展
}

/**
 * 插件能力
 */
enum class PluginCapability {
    FILE_PREVIEW,         // 文件预览
    FILE_EDIT,            // 文件编辑
    LANGUAGE_SUPPORT,     // 语言支持
    TERMINAL_COMMAND,     // 终端命令
    AI_AGENT,             // AI代理
    TOOL_CALL,            // 工具调用
    THEME,                // 主题
    CUSTOM                // 自定义能力
}

/**
 * 插件权限
 */
enum class PluginPermission {
    READ_FILES,        // 读取文件
    WRITE_FILES,       // 写入文件
    NETWORK_ACCESS,    // 网络访问
    TERMINAL_ACCESS,   // 终端访问
    UI_ACCESS,         // UI访问
    SYSTEM_ACCESS      // 系统访问（需要Root）
}

