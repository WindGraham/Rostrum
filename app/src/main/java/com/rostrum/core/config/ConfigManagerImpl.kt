package com.rostrum.core.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.configDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_config")

/**
 * ConfigManager 实现类
 * 
 * 使用 DataStore 持久化应用配置
 */
class ConfigManagerImpl(private val context: Context) : ConfigManager {
    
    private val dataStore = context.configDataStore
    
    // 通用配置键
    private object GeneralKeys {
        val LANGUAGE = stringPreferencesKey("general_language")
        val THEME = stringPreferencesKey("general_theme")
        val DEVELOPER_MODE = booleanPreferencesKey("general_developer_mode")
        val IS_FIRST_LAUNCH = booleanPreferencesKey("general_is_first_launch")
        val HAS_AGREED_TO_TERMS = booleanPreferencesKey("general_has_agreed_to_terms")
        val HAS_PROMPTED_FLOATING_BALL_PERMISSIONS =
            booleanPreferencesKey("general_has_prompted_floating_ball_permissions")
    }
    
    // 终端配置键
    private object TerminalKeys {
        val DEFAULT_SHELL = stringPreferencesKey("terminal_shell")
        val WORKING_DIR = stringPreferencesKey("terminal_working_dir")
        val ENABLE_BELL = booleanPreferencesKey("terminal_bell")
        val CURSOR_STYLE = stringPreferencesKey("terminal_cursor_style")
        val FONT_SIZE = intPreferencesKey("terminal_font_size")
        val FONT_FAMILY = stringPreferencesKey("terminal_font_family")
        val COLOR_SCHEME = stringPreferencesKey("terminal_color_scheme")
    }

    private val disallowedShellPrefixes = listOf(
        "/data/data/com.termux/",
        "/data/user/0/com.termux/"
    )
    
    // 文件管理器配置键
    private object FileManagerKeys {
        val SHOW_HIDDEN = booleanPreferencesKey("fm_show_hidden")
        val DEFAULT_VIEW = stringPreferencesKey("fm_default_view")
        val SORT_BY = stringPreferencesKey("fm_sort_by")
        val SORT_ORDER = stringPreferencesKey("fm_sort_order")
        val GRID_ICON_SIZE = stringPreferencesKey("fm_grid_icon_size")
        val DEFAULT_OPEN_PATH = stringPreferencesKey("fm_default_open_path")
        val QUICK_ACCESS_PATHS = stringPreferencesKey("fm_quick_access_paths")
    }
    
    // 插件配置键
    private object PluginsKeys {
        val AUTO_UPDATE = booleanPreferencesKey("plugins_auto_update")
        val ALLOW_REMOTE = booleanPreferencesKey("plugins_allow_remote")
    }
    
    // 安全配置键
    private object SecurityKeys {
        val REQUIRE_SIGNATURE = booleanPreferencesKey("security_require_signature")
        val SANDBOX_ENABLED = booleanPreferencesKey("security_sandbox_enabled")
    }

    // 构建配置键
    private object BuildKeys {
        val ENABLE_OFFLINE_BUILD = booleanPreferencesKey("build_enable_offline")
        val DEFAULT_COMPILE_SDK = intPreferencesKey("build_compile_sdk")
        val DEFAULT_TARGET_SDK = intPreferencesKey("build_target_sdk")
        val DEFAULT_MIN_SDK = intPreferencesKey("build_min_sdk")
        val TOOLCHAIN_ROOT = stringPreferencesKey("build_toolchain_root")
        val ALLOW_UNSIGNED_DEBUG = booleanPreferencesKey("build_allow_unsigned_debug")
        val DEBUG_AUTO_SIGN = booleanPreferencesKey("build_debug_auto_sign")
        val ENABLE_EMBEDDED_SERVER_EXPERIMENT = booleanPreferencesKey("build_enable_embedded_server_experiment")
        val RELEASE_KEYSTORE_PATH = stringPreferencesKey("build_release_keystore_path")
        val RELEASE_KEY_ALIAS = stringPreferencesKey("build_release_key_alias")
        val RELEASE_STORE_TYPE = stringPreferencesKey("build_release_store_type")
        val RELEASE_V1 = booleanPreferencesKey("build_release_v1_enabled")
        val RELEASE_V2 = booleanPreferencesKey("build_release_v2_enabled")
        val RELEASE_V3 = booleanPreferencesKey("build_release_v3_enabled")
    }
    
    // 布局配置键
    private object LayoutKeys {
        val LAYOUT_MODE = stringPreferencesKey("layout_mode")
        val SHOW_FILE_MANAGER = booleanPreferencesKey("layout_show_fm")
        val RIGHT_PANEL_MODE = stringPreferencesKey("layout_right_panel")
        val PREVIEW_MODE = stringPreferencesKey("layout_preview_mode")
        val FILE_MANAGER_RATIO = floatPreferencesKey("layout_fm_ratio")
        val PREVIEW_RATIO = floatPreferencesKey("layout_preview_ratio")
        val SECONDARY_RATIO = floatPreferencesKey("layout_secondary_ratio")
    }
    
    // 主题配置键
    private object ThemeKeys {
        val NAME = stringPreferencesKey("theme_name")
        val COLORS_JSON = stringPreferencesKey("theme_colors")
    }
    
    private fun buildAppConfig(prefs: Preferences): AppConfig {
        return AppConfig(
            general = GeneralConfig(
                language = prefs[GeneralKeys.LANGUAGE] ?: "zh-CN",
                theme = prefs[GeneralKeys.THEME] ?: "auto",
                developerMode = prefs[GeneralKeys.DEVELOPER_MODE] ?: false,
                isFirstLaunch = prefs[GeneralKeys.IS_FIRST_LAUNCH] ?: true,
                hasAgreedToTerms = prefs[GeneralKeys.HAS_AGREED_TO_TERMS] ?: false,
                hasPromptedFloatingBallPermissions =
                    prefs[GeneralKeys.HAS_PROMPTED_FLOATING_BALL_PERMISSIONS] ?: false
            ),
            terminal = TerminalConfig(
                defaultShell = sanitizeShellPath(prefs[TerminalKeys.DEFAULT_SHELL]),
                defaultWorkingDirectory = prefs[TerminalKeys.WORKING_DIR] ?: "/sdcard",
                enableBell = prefs[TerminalKeys.ENABLE_BELL] ?: false,
                cursorStyle = prefs[TerminalKeys.CURSOR_STYLE]?.let {
                    runCatching { CursorStyle.valueOf(it) }.getOrNull()
                } ?: CursorStyle.BLOCK,
                fontSize = prefs[TerminalKeys.FONT_SIZE] ?: 12,
                fontFamily = prefs[TerminalKeys.FONT_FAMILY] ?: "monospace",
                colorScheme = prefs[TerminalKeys.COLOR_SCHEME]?.let {
                    runCatching { TerminalColorScheme.valueOf(it) }.getOrNull()
                } ?: TerminalColorScheme.DEFAULT
            ),
            fileManager = FileManagerConfig(
                showHiddenFiles = prefs[FileManagerKeys.SHOW_HIDDEN] ?: false,
                defaultView = prefs[FileManagerKeys.DEFAULT_VIEW]?.let {
                    runCatching { FileViewMode.valueOf(it) }.getOrNull()
                } ?: FileViewMode.GRID,  // 默认使用GRID（简洁模式）
                sortBy = prefs[FileManagerKeys.SORT_BY]?.let {
                    runCatching { SortBy.valueOf(it) }.getOrNull()
                } ?: SortBy.NAME,
                sortOrder = prefs[FileManagerKeys.SORT_ORDER]?.let {
                    runCatching { SortOrder.valueOf(it) }.getOrNull()
                } ?: SortOrder.ASCENDING,
                gridIconSize = prefs[FileManagerKeys.GRID_ICON_SIZE]?.let {
                    runCatching { GridIconSize.valueOf(it) }.getOrNull()
                } ?: GridIconSize.TINY,
                defaultOpenPath = prefs[FileManagerKeys.DEFAULT_OPEN_PATH] ?: "",
                quickAccessPaths = parseQuickAccessPaths(prefs[FileManagerKeys.QUICK_ACCESS_PATHS])
            ),
            plugins = PluginsConfig(
                autoUpdate = prefs[PluginsKeys.AUTO_UPDATE] ?: false,
                allowRemotePlugins = prefs[PluginsKeys.ALLOW_REMOTE] ?: true
            ),
            security = SecurityConfig(
                requirePluginSignature = prefs[SecurityKeys.REQUIRE_SIGNATURE] ?: false,
                sandboxEnabled = prefs[SecurityKeys.SANDBOX_ENABLED] ?: true
            ),
            build = BuildSystemConfig(
                enableOfflineBuild = prefs[BuildKeys.ENABLE_OFFLINE_BUILD] ?: true,
                defaultCompileSdk = prefs[BuildKeys.DEFAULT_COMPILE_SDK] ?: 34,
                defaultTargetSdk = prefs[BuildKeys.DEFAULT_TARGET_SDK] ?: 34,
                defaultMinSdk = prefs[BuildKeys.DEFAULT_MIN_SDK] ?: 26,
                toolchainRoot = prefs[BuildKeys.TOOLCHAIN_ROOT] ?: "",
                allowUnsignedDebug = prefs[BuildKeys.ALLOW_UNSIGNED_DEBUG] ?: false,
                debugAutoSign = prefs[BuildKeys.DEBUG_AUTO_SIGN] ?: true,
                enableEmbeddedServerExperiment = prefs[BuildKeys.ENABLE_EMBEDDED_SERVER_EXPERIMENT] ?: false,
                releaseSigning = BuildSigningConfig(
                    keystorePath = prefs[BuildKeys.RELEASE_KEYSTORE_PATH] ?: "",
                    keyAlias = prefs[BuildKeys.RELEASE_KEY_ALIAS] ?: "",
                    storeType = prefs[BuildKeys.RELEASE_STORE_TYPE] ?: "JKS",
                    v1Enabled = prefs[BuildKeys.RELEASE_V1] ?: true,
                    v2Enabled = prefs[BuildKeys.RELEASE_V2] ?: true,
                    v3Enabled = prefs[BuildKeys.RELEASE_V3] ?: true
                )
            )
        )
    }

    override val appConfigFlow: Flow<AppConfig> = dataStore.data.map { prefs ->
        buildAppConfig(prefs)
    }

    override suspend fun getAppConfig(): AppConfig {
        val prefs = dataStore.data.first()
        return buildAppConfig(prefs)
    }
    
    override suspend fun updateAppConfig(updater: (AppConfig) -> AppConfig): Result<Unit> {
        return runCatching {
            val current = getAppConfig()
            val updated = updater(current)
            dataStore.edit { prefs ->
                // 保存通用配置
                prefs[GeneralKeys.LANGUAGE] = updated.general.language
                prefs[GeneralKeys.THEME] = updated.general.theme
                prefs[GeneralKeys.DEVELOPER_MODE] = updated.general.developerMode
                prefs[GeneralKeys.IS_FIRST_LAUNCH] = updated.general.isFirstLaunch
                prefs[GeneralKeys.HAS_AGREED_TO_TERMS] = updated.general.hasAgreedToTerms
                prefs[GeneralKeys.HAS_PROMPTED_FLOATING_BALL_PERMISSIONS] =
                    updated.general.hasPromptedFloatingBallPermissions
                
                // 保存终端配置
                prefs[TerminalKeys.DEFAULT_SHELL] = sanitizeShellPath(updated.terminal.defaultShell)
                prefs[TerminalKeys.WORKING_DIR] = updated.terminal.defaultWorkingDirectory
                prefs[TerminalKeys.ENABLE_BELL] = updated.terminal.enableBell
                prefs[TerminalKeys.CURSOR_STYLE] = updated.terminal.cursorStyle.name
                prefs[TerminalKeys.FONT_SIZE] = updated.terminal.fontSize
                prefs[TerminalKeys.FONT_FAMILY] = updated.terminal.fontFamily
                prefs[TerminalKeys.COLOR_SCHEME] = updated.terminal.colorScheme.name
                
                // 保存文件管理器配置
                prefs[FileManagerKeys.SHOW_HIDDEN] = updated.fileManager.showHiddenFiles
                prefs[FileManagerKeys.DEFAULT_VIEW] = updated.fileManager.defaultView.name
                prefs[FileManagerKeys.SORT_BY] = updated.fileManager.sortBy.name
                prefs[FileManagerKeys.SORT_ORDER] = updated.fileManager.sortOrder.name
                prefs[FileManagerKeys.GRID_ICON_SIZE] = updated.fileManager.gridIconSize.name
                prefs[FileManagerKeys.DEFAULT_OPEN_PATH] = updated.fileManager.defaultOpenPath
                prefs[FileManagerKeys.QUICK_ACCESS_PATHS] = serializeQuickAccessPaths(updated.fileManager.quickAccessPaths)
                
                // 保存插件配置
                prefs[PluginsKeys.AUTO_UPDATE] = updated.plugins.autoUpdate
                prefs[PluginsKeys.ALLOW_REMOTE] = updated.plugins.allowRemotePlugins
                
                // 保存安全配置
                prefs[SecurityKeys.REQUIRE_SIGNATURE] = updated.security.requirePluginSignature
                prefs[SecurityKeys.SANDBOX_ENABLED] = updated.security.sandboxEnabled

                // 保存构建配置
                prefs[BuildKeys.ENABLE_OFFLINE_BUILD] = updated.build.enableOfflineBuild
                prefs[BuildKeys.DEFAULT_COMPILE_SDK] = updated.build.defaultCompileSdk
                prefs[BuildKeys.DEFAULT_TARGET_SDK] = updated.build.defaultTargetSdk
                prefs[BuildKeys.DEFAULT_MIN_SDK] = updated.build.defaultMinSdk
                prefs[BuildKeys.TOOLCHAIN_ROOT] = updated.build.toolchainRoot
                prefs[BuildKeys.ALLOW_UNSIGNED_DEBUG] = updated.build.allowUnsignedDebug
                prefs[BuildKeys.DEBUG_AUTO_SIGN] = updated.build.debugAutoSign
                prefs[BuildKeys.ENABLE_EMBEDDED_SERVER_EXPERIMENT] = updated.build.enableEmbeddedServerExperiment
                prefs[BuildKeys.RELEASE_KEYSTORE_PATH] = updated.build.releaseSigning.keystorePath
                prefs[BuildKeys.RELEASE_KEY_ALIAS] = updated.build.releaseSigning.keyAlias
                prefs[BuildKeys.RELEASE_STORE_TYPE] = updated.build.releaseSigning.storeType
                prefs[BuildKeys.RELEASE_V1] = updated.build.releaseSigning.v1Enabled
                prefs[BuildKeys.RELEASE_V2] = updated.build.releaseSigning.v2Enabled
                prefs[BuildKeys.RELEASE_V3] = updated.build.releaseSigning.v3Enabled
            }
        }
    }
    
    override suspend fun getUserConfig(userId: String): UserConfig {
        val prefs = dataStore.data.first()
        val prefsKey = stringPreferencesKey("user_${userId}_prefs")
        val prefsJson = prefs[prefsKey]
        // 简化实现：返回空配置
        return UserConfig()
    }
    
    override suspend fun updateUserConfig(
        userId: String,
        updater: (UserConfig) -> UserConfig
    ): Result<Unit> {
        return runCatching {
            val current = getUserConfig(userId)
            val updated = updater(current)
            val prefsKey = stringPreferencesKey("user_${userId}_prefs")
            dataStore.edit { prefs ->
                // 简化实现：暂不保存用户配置
            }
        }
    }

    private fun sanitizeShellPath(rawShell: String?): String {
        if (rawShell.isNullOrBlank()) return "/system/bin/sh"
        if (disallowedShellPrefixes.any { rawShell.startsWith(it) }) {
            return "/system/bin/sh"
        }
        return rawShell
    }
    
    override suspend fun getPluginConfig(pluginId: String): Map<String, Any> {
        val prefs = dataStore.data.first()
        val configKey = stringPreferencesKey("plugin_${pluginId}_config")
        // 简化实现：返回空配置
        return emptyMap()
    }
    
    override suspend fun updatePluginConfig(
        pluginId: String,
        config: Map<String, Any>
    ): Result<Unit> {
        return runCatching {
            val configKey = stringPreferencesKey("plugin_${pluginId}_config")
            dataStore.edit { prefs ->
                // 简化实现：暂不保存插件配置
            }
        }
    }
    
    override suspend fun getThemeConfig(): ThemeConfig {
        val prefs = dataStore.data.first()
        return ThemeConfig(
            name = prefs[ThemeKeys.NAME] ?: "default",
            colors = emptyMap()  // 简化实现
        )
    }
    
    override suspend fun updateThemeConfig(theme: ThemeConfig): Result<Unit> {
        return runCatching {
            dataStore.edit { prefs ->
                prefs[ThemeKeys.NAME] = theme.name
            }
        }
    }
    
    override suspend fun getLayoutConfig(): LayoutConfig {
        val prefs = dataStore.data.first()
        return LayoutConfig(
            layoutMode = prefs[LayoutKeys.LAYOUT_MODE]?.let {
                runCatching { LayoutMode.valueOf(it) }.getOrNull()
            } ?: LayoutMode.DUAL_WINDOW,
            showFileManager = prefs[LayoutKeys.SHOW_FILE_MANAGER] ?: true,
            rightPanelMode = prefs[LayoutKeys.RIGHT_PANEL_MODE]?.let {
                runCatching { RightPanelMode.valueOf(it) }.getOrNull()
            } ?: RightPanelMode.PREVIEW,
            previewMode = prefs[LayoutKeys.PREVIEW_MODE]?.let {
                runCatching { PreviewMode.valueOf(it) }.getOrNull()
            } ?: PreviewMode.AUTO,
            panelRatios = mapOf(
                "fileManager" to (prefs[LayoutKeys.FILE_MANAGER_RATIO] ?: 0.3f),
                "preview" to (prefs[LayoutKeys.PREVIEW_RATIO] ?: prefs[LayoutKeys.SECONDARY_RATIO] ?: 0.7f)
            )
        )
    }
    
    override suspend fun updateLayoutConfig(layout: LayoutConfig): Result<Unit> {
        return runCatching {
            dataStore.edit { prefs ->
                prefs[LayoutKeys.LAYOUT_MODE] = layout.layoutMode.name
                prefs[LayoutKeys.SHOW_FILE_MANAGER] = layout.showFileManager
                prefs[LayoutKeys.RIGHT_PANEL_MODE] = layout.rightPanelMode.name
                prefs[LayoutKeys.PREVIEW_MODE] = layout.previewMode.name
                layout.panelRatios["fileManager"]?.let { prefs[LayoutKeys.FILE_MANAGER_RATIO] = it }
                layout.panelRatios["preview"]?.let { prefs[LayoutKeys.PREVIEW_RATIO] = it }
            }
        }
    }
    
    companion object {
        @Volatile
        private var instance: ConfigManagerImpl? = null
        
        fun getInstance(context: Context): ConfigManagerImpl {
            return instance ?: synchronized(this) {
                instance ?: ConfigManagerImpl(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    // ==================== 快捷地址序列化/反序列化 ====================
    
    /**
     * 解析快捷地址 JSON 字符串
     * 格式: name|path|icon,name2|path2|icon2
     */
    private fun parseQuickAccessPaths(json: String?): List<QuickAccessPath> {
        if (json.isNullOrBlank()) return getDefaultQuickAccessPaths()
        
        return try {
            json.split(",").mapNotNull { item ->
                val parts = item.split("|")
                if (parts.size >= 2) {
                    QuickAccessPath(
                        name = parts[0],
                        path = parts[1],
                        icon = parts.getOrElse(2) { "folder" }
                    )
                } else null
            }
        } catch (e: Exception) {
            getDefaultQuickAccessPaths()
        }
    }
    
    /**
     * 序列化快捷地址列表为 JSON 字符串
     */
    private fun serializeQuickAccessPaths(paths: List<QuickAccessPath>): String {
        return paths.joinToString(",") { "${it.name}|${it.path}|${it.icon}" }
    }
    
    /**
     * 获取默认快捷地址
     */
    private fun getDefaultQuickAccessPaths(): List<QuickAccessPath> {
        val rootPath = try {
            com.rostrum.core.util.FileUtils.getExternalStorageRoot().absolutePath
        } catch (e: Exception) {
            "/storage/emulated/0"
        }
        return listOf(
            QuickAccessPath("根目录", rootPath, "folder"),
            QuickAccessPath("下载", "$rootPath/Download", "download"),
            QuickAccessPath("文档", "$rootPath/Documents", "description"),
            QuickAccessPath("图片", "$rootPath/Pictures", "image"),
            QuickAccessPath("音乐", "$rootPath/Music", "music_note"),
            QuickAccessPath("视频", "$rootPath/Movies", "movie")
        )
    }
}
