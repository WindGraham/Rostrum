package com.rostrum.core.plugin

import android.content.Context
import com.rostrum.core.config.ConfigManager
import com.rostrum.core.event.EventBus
import com.rostrum.core.filesystem.FileSystemService
import com.rostrum.core.mcp.MCPProtocol
import com.rostrum.core.plugin.providers.FileEditorPlugin
import com.rostrum.core.plugin.providers.FilePreviewPlugin
import com.rostrum.core.plugin.providers.LanguageSupportPlugin
import com.rostrum.core.terminal.TerminalService
import com.rostrum.core.domain.model.FileItem
import kotlinx.coroutines.flow.StateFlow

/**
 * 插件上下文接口
 * 
 * 提供插件访问主程序功能的接口
 */
interface PluginContext {
    /**
     * 当前插件实例
     */
    val plugin: Plugin
    
    /**
     * Android上下文
     */
    val appContext: Context
    
    /**
     * 插件管理器
     */
    val pluginManager: PluginManager
    
    /**
     * 文件系统服务
     */
    val fileSystem: FileSystemService
    
    /**
     * 终端服务
     */
    val terminal: TerminalService
    
    /**
     * MCP协议
     */
    val mcp: MCPProtocol
    
    /**
     * 事件总线
     */
    val eventBus: EventBus
    
    /**
     * 配置管理器
     */
    val config: ConfigManager
    
    /**
     * 工作区接口
     */
    val workspace: Workspace
    
    /**
     * 命令注册表
     */
    val commands: CommandRegistry
    
    /**
     * 窗口接口
     */
    val window: Window
    
    /**
     * UI注册表
     */
    val ui: UIRegistry
    
    /**
     * 订阅列表（用于资源清理）
     */
    val subscriptions: MutableList<Disposable>
    
    /**
     * 注册命令
     */
    fun registerCommand(command: Command): Disposable
    
    /**
     * 注册文件预览插件
     */
    fun registerFilePreview(plugin: FilePreviewPlugin): Disposable
    
    /**
     * 注册文件编辑插件
     */
    fun registerFileEditor(plugin: FileEditorPlugin): Disposable
    
    /**
     * 注册语言支持插件
     */
    fun registerLanguageSupport(plugin: LanguageSupportPlugin): Disposable
    
}

/**
 * 可释放资源接口
 */
interface Disposable {
    fun dispose()
}

/**
 * 工作区接口
 */
interface Workspace {
    /**
     * 工作区文件夹列表
     */
    val folders: List<WorkspaceFolder>
    
    /**
     * 当前选中的文件
     */
    val selectedFiles: StateFlow<List<FileItem>>
    
    /**
     * 打开文件
     */
    suspend fun openFile(file: FileItem)
    
    /**
     * 获取工作区配置
     */
    fun getConfiguration(section: String? = null): WorkspaceConfiguration
}

/**
 * 工作区文件夹
 */
data class WorkspaceFolder(
    val uri: String,
    val name: String,
    val index: Int = 0
)

/**
 * 工作区配置
 */
interface WorkspaceConfiguration {
    fun <T> get(key: String, defaultValue: T? = null): T?
    fun update(key: String, value: Any?): Boolean
}

/**
 * 命令注册表
 */
interface CommandRegistry {
    /**
     * 注册命令
     */
    fun register(command: Command): Disposable
    
    /**
     * 执行命令
     */
    suspend fun executeCommand(commandId: String, vararg args: Any?): Any?
    
    /**
     * 获取所有命令ID
     */
    fun getCommands(): List<String>
}

/**
 * 命令定义
 */
data class Command(
    val id: String,
    val title: String,
    val handler: suspend (List<Any?>) -> Any?,
    val icon: String? = null,
    val category: String? = null
)

/**
 * 窗口接口
 */
interface Window {
    /**
     * 显示信息消息
     */
    suspend fun showInformationMessage(message: String, vararg items: String): String?
    
    /**
     * 显示警告消息
     */
    suspend fun showWarningMessage(message: String, vararg items: String): String?
    
    /**
     * 显示错误消息
     */
    suspend fun showErrorMessage(message: String, vararg items: String): String?
    
    /**
     * 显示输入框
     */
    suspend fun showInputBox(options: InputBoxOptions? = null): String?
    
    /**
     * 创建状态栏项
     */
    fun createStatusBarItem(alignment: StatusBarAlignment = StatusBarAlignment.LEFT): StatusBarItem
}

/**
 * 输入框选项
 */
data class InputBoxOptions(
    val prompt: String? = null,
    val placeHolder: String? = null,
    val value: String? = null,
    val password: Boolean = false,
    val ignoreFocusOut: Boolean = false,
    val validateInput: ((String) -> String?)? = null
)

/**
 * 状态栏对齐方式
 */
enum class StatusBarAlignment {
    LEFT,
    RIGHT
}

/**
 * 状态栏项
 */
interface StatusBarItem {
    var text: String
    var tooltip: String?
    var command: String?
    fun show()
    fun hide()
    fun dispose()
}

/**
 * UI注册表
 */
interface UIRegistry {
    /**
     * 注册文件预览插件
     */
    fun registerPreview(plugin: FilePreviewPlugin): Disposable
    
    /**
     * 注册文件编辑插件
     */
    fun registerEditor(plugin: FileEditorPlugin): Disposable
    
    /**
     * 创建Webview面板（用于VSCode插件兼容）
     */
    fun createWebviewPanel(viewType: String, title: String): com.rostrum.core.plugin.vscode.WebviewPanel
}
