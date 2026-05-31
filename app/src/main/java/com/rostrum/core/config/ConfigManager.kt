package com.rostrum.core.config

import kotlinx.coroutines.flow.Flow

/**
 * 配置管理器接口
 */
interface ConfigManager {
    /**
     * 获取应用配置
     * 
     * @return 应用配置
     */
    suspend fun getAppConfig(): AppConfig

    /**
     * 监听应用配置变化
     *
     * @return 应用配置 Flow
     */
    val appConfigFlow: Flow<AppConfig>
    
    /**
     * 更新应用配置
     * 
     * @param updater 更新函数
     * @return 更新结果
     */
    suspend fun updateAppConfig(updater: (AppConfig) -> AppConfig): Result<Unit>
    
    /**
     * 获取用户配置
     * 
     * @param userId 用户ID
     * @return 用户配置
     */
    suspend fun getUserConfig(userId: String): UserConfig
    
    /**
     * 更新用户配置
     * 
     * @param userId 用户ID
     * @param updater 更新函数
     * @return 更新结果
     */
    suspend fun updateUserConfig(
        userId: String,
        updater: (UserConfig) -> UserConfig
    ): Result<Unit>
    
    /**
     * 获取插件配置
     * 
     * @param pluginId 插件ID
     * @return 插件配置
     */
    suspend fun getPluginConfig(pluginId: String): Map<String, Any>
    
    /**
     * 更新插件配置
     * 
     * @param pluginId 插件ID
     * @param config 配置数据
     * @return 更新结果
     */
    suspend fun updatePluginConfig(
        pluginId: String,
        config: Map<String, Any>
    ): Result<Unit>
    
    /**
     * 获取主题配置
     * 
     * @return 主题配置
     */
    suspend fun getThemeConfig(): ThemeConfig
    
    /**
     * 更新主题配置
     * 
     * @param theme 主题配置
     * @return 更新结果
     */
    suspend fun updateThemeConfig(theme: ThemeConfig): Result<Unit>
    
    /**
     * 获取布局配置
     * 
     * @return 布局配置
     */
    suspend fun getLayoutConfig(): LayoutConfig
    
    /**
     * 更新布局配置
     * 
     * @param layout 布局配置
     * @return 更新结果
     */
    suspend fun updateLayoutConfig(layout: LayoutConfig): Result<Unit>
    
}

/**
 * 应用配置
 */
data class AppConfig(
    val general: GeneralConfig,
    val terminal: TerminalConfig,
    val fileManager: FileManagerConfig,
    val plugins: PluginsConfig,
    val security: SecurityConfig,
    val build: BuildSystemConfig = BuildSystemConfig()
)

/**
 * 通用配置
 */
data class GeneralConfig(
    val language: String = "zh-CN",
    val theme: String = "auto",
    val developerMode: Boolean = false,  // 开发者模式开关
    val isFirstLaunch: Boolean = true,   // 首次启动标记
    val hasAgreedToTerms: Boolean = false, // 是否已同意用户协议和隐私政策
    val hasPromptedFloatingBallPermissions: Boolean = false // 是否已完成首次悬浮球权限引导
)

/**
 * 终端配置
 */
data class TerminalConfig(
    val defaultShell: String = "/system/bin/sh",
    val defaultWorkingDirectory: String = "/sdcard",
    val enableBell: Boolean = false,
    val cursorStyle: CursorStyle = CursorStyle.BLOCK,
    val fontSize: Int = 12,
    val fontFamily: String = "monospace",
    val colorScheme: TerminalColorScheme = TerminalColorScheme.DEFAULT
)

/**
 * 光标样式
 */
enum class CursorStyle {
    BLOCK,
    UNDERLINE,
    BAR
}

/**
 * 终端配色方案
 */
enum class TerminalColorScheme {
    DEFAULT,
    DARK,
    LIGHT,
    SOLARIZED,
    MONOKAI
}

/**
 * 快捷地址（快速访问目录）
 */
data class QuickAccessPath(
    val name: String,
    val path: String,
    val icon: String = "folder"  // 图标标识
)

/**
 * 文件管理器配置
 */
data class FileManagerConfig(
    val showHiddenFiles: Boolean = false,
    val defaultView: FileViewMode = FileViewMode.GRID,  // 默认改为GRID（简洁模式）
    val sortBy: SortBy = SortBy.NAME,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val gridIconSize: GridIconSize = GridIconSize.TINY,  // 网格图标大小（默认最小图标）
    val defaultOpenPath: String = "",  // 默认打开目录（空字符串表示使用根目录）
    val quickAccessPaths: List<QuickAccessPath> = emptyList()  // 用户自定义快捷地址
)

/**
 * 文件视图模式
 */
enum class FileViewMode {
    LIST,
    GRID,
    TREE
}

/**
 * 网格图标大小
 */
enum class GridIconSize(val columns: Int, val iconDp: Int) {
    TINY(8, 40),     // 每行8个，图标40dp（最小）
    SMALL(6, 56),    // 每行6个，图标56dp
    MEDIUM(4, 80),   // 每行4个，图标80dp
    LARGE(3, 100)    // 每行3个，图标100dp
}

/**
 * 排序方式
 */
enum class SortBy {
    NAME,
    SIZE,
    DATE,
    TYPE
}

/**
 * 排序顺序
 */
enum class SortOrder {
    ASCENDING,
    DESCENDING
}

/**
 * 插件配置
 */
data class PluginsConfig(
    val autoUpdate: Boolean = false,
    val allowRemotePlugins: Boolean = true
)

/**
 * 安全配置
 */
data class SecurityConfig(
    val requirePluginSignature: Boolean = false,
    val sandboxEnabled: Boolean = true
)

/**
 * 构建系统配置
 */
data class BuildSystemConfig(
    val enableOfflineBuild: Boolean = true,
    val defaultCompileSdk: Int = 34,
    val defaultTargetSdk: Int = 34,
    val defaultMinSdk: Int = 26,
    val toolchainRoot: String = "",
    val allowUnsignedDebug: Boolean = false,
    val debugAutoSign: Boolean = true,
    val enableEmbeddedServerExperiment: Boolean = false,
    val releaseSigning: BuildSigningConfig = BuildSigningConfig()
)

data class BuildSigningConfig(
    val keystorePath: String = "",
    val keyAlias: String = "",
    val storeType: String = "JKS",
    val v1Enabled: Boolean = true,
    val v2Enabled: Boolean = true,
    val v3Enabled: Boolean = true
)

/**
 * 用户配置
 */
data class UserConfig(
    val preferences: Map<String, Any> = emptyMap()
)

/**
 * 主题配置
 */
data class ThemeConfig(
    val name: String,
    val colors: Map<String, String> = emptyMap()
)

/**
 * 布局配置
 */
data class LayoutConfig(
    val layoutMode: LayoutMode = LayoutMode.DUAL_WINDOW,
    val showFileManager: Boolean = true,
    val rightPanelMode: RightPanelMode = RightPanelMode.PREVIEW,
    val previewMode: PreviewMode = PreviewMode.AUTO,
    val panelRatios: Map<String, Float> = mapOf(
        "fileManager" to 0.3f,
        "preview" to 0.7f
    )
)

/**
 * 布局模式
 */
enum class LayoutMode {
    DUAL_WINDOW,
    TERMINAL_FOCUS,
    CUSTOM
}

/**
 * 右侧面板模式
 */
enum class RightPanelMode {
    PREVIEW,
    TERMINAL,
    EDITOR,
    PLUGIN
}

/**
 * 预览模式
 */
enum class PreviewMode {
    AUTO,
    TEXT,
    IMAGE,
    CODE,
    PDF,
    VIDEO
}
