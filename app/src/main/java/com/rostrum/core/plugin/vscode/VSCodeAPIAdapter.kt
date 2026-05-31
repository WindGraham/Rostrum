package com.rostrum.core.plugin.vscode

import com.rostrum.core.plugin.Command
import com.rostrum.core.plugin.CommandRegistry
import com.rostrum.core.filesystem.FileSystemService
import com.rostrum.core.plugin.PluginContext
import com.rostrum.core.plugin.UIRegistry
import com.rostrum.core.plugin.Window
import com.rostrum.core.plugin.Workspace
import com.rostrum.core.plugin.vscode.WebviewPanel

/**
 * VSCode API适配器
 * 
 * 将OmniMaster的PluginContext适配为VSCode API格式
 * 使得VSCode插件可以直接使用vscode命名空间下的API
 */
class VSCodeAPIAdapter(
    private val context: PluginContext
) {
    /**
     * VSCode workspace API
     */
    val workspace = VSCodeWorkspace(context.workspace)
    
    /**
     * VSCode window API
     */
    val window = VSCodeWindow(context.window)
    
    /**
     * VSCode commands API
     */
    val commands = VSCodeCommands(context.commands)
    
    /**
     * VSCode文件系统API
     */
    val fs = VSCodeFileSystem(context.fileSystem)
}

/**
 * VSCode Workspace API实现
 */
class VSCodeWorkspace(
    private val workspace: Workspace
) {
    /**
     * 获取工作区文件夹
     */
    val workspaceFolders: List<VSCodeWorkspaceFolder>?
        get() = workspace.folders.map { 
            VSCodeWorkspaceFolder(it.uri, it.name, it.index) 
        }
    
    /**
     * 打开文本文档
     */
    suspend fun openTextDocument(uri: String): VSCodeTextDocument {
        // TODO: 实现打开文本文档
        // 这里需要调用workspace的openFile方法，然后转换为VSCodeTextDocument
        return VSCodeTextDocument(
            uri = uri,
            languageId = "",
            version = 0,
            lineCount = 0
        )
    }
    
    /**
     * 获取配置
     */
    fun getConfiguration(section: String? = null): VSCodeWorkspaceConfiguration {
        val config = workspace.getConfiguration(section)
        return VSCodeWorkspaceConfiguration(config)
    }
}

/**
 * VSCode工作区文件夹
 */
data class VSCodeWorkspaceFolder(
    val uri: String,
    val name: String,
    val index: Int
)

/**
 * VSCode工作区配置
 */
class VSCodeWorkspaceConfiguration(
    private val config: com.rostrum.core.plugin.WorkspaceConfiguration
) {
    fun <T> get(key: String, defaultValue: T? = null): T? {
        return config.get(key, defaultValue)
    }
    
    fun update(key: String, value: Any?): Boolean {
        return config.update(key, value)
    }
}

/**
 * VSCode Window API实现
 */
class VSCodeWindow(
    private val window: Window
) {
    /**
     * 显示信息消息
     */
    suspend fun showInformationMessage(
        message: String,
        vararg items: String
    ): String? {
        return window.showInformationMessage(message, *items)
    }
    
    /**
     * 显示警告消息
     */
    suspend fun showWarningMessage(
        message: String,
        vararg items: String
    ): String? {
        return window.showWarningMessage(message, *items)
    }
    
    /**
     * 显示错误消息
     */
    suspend fun showErrorMessage(
        message: String,
        vararg items: String
    ): String? {
        return window.showErrorMessage(message, *items)
    }
    
    /**
     * 显示输入框
     */
    suspend fun showInputBox(options: com.rostrum.core.plugin.InputBoxOptions? = null): String? {
        return window.showInputBox(options)
    }
    
    /**
     * 创建Webview面板
     */
    fun createWebviewPanel(
        viewType: String,
        title: String
    ): WebviewPanel {
        // TODO: 通过context.ui创建WebviewPanel
        return WebviewPanelImpl(title, viewType)
    }
}

/**
 * Window实现（需要访问context）
 */
interface WindowImpl {
    val context: PluginContext
}

/**
 * VSCode Commands API实现
 */
class VSCodeCommands(
    private val commands: CommandRegistry
) {
    /**
     * 注册命令
     */
    fun registerCommand(
        command: String,
        callback: (args: Any?) -> Any?
    ): com.rostrum.core.plugin.Disposable {
        val cmd = Command(
            id = command,
            title = command,
            handler = { args -> callback(args.firstOrNull()) }
        )
        return commands.register(cmd)
    }
    
    /**
     * 执行命令
     */
    suspend fun executeCommand(commandId: String, vararg args: Any?): Any? {
        return commands.executeCommand(commandId, *args)
    }
    
    /**
     * 获取所有命令
     */
    fun getCommands(): List<String> {
        return commands.getCommands()
    }
}

/**
 * VSCode文件系统API实现
 */
class VSCodeFileSystem(
    private val fileSystem: FileSystemService
) {
    /**
     * 读取文件
     */
    suspend fun readFile(uri: String): ByteArray {
        return fileSystem.readFile(uri).getOrThrow()
    }
    
    /**
     * 写入文件
     */
    suspend fun writeFile(uri: String, content: ByteArray): Boolean {
        return fileSystem.writeFile(uri, content).isSuccess
    }
    
    /**
     * 读取文本文件
     */
    suspend fun readTextFile(uri: String, encoding: String = "UTF-8"): String {
        return fileSystem.readTextFile(uri, encoding).getOrThrow()
    }
    
    /**
     * 写入文本文件
     */
    suspend fun writeTextFile(uri: String, content: String, encoding: String = "UTF-8"): Boolean {
        return fileSystem.writeTextFile(uri, content, encoding).isSuccess
    }
}

/**
 * VSCode文本文档（占位实现）
 */
class VSCodeTextDocument(
    val uri: String,
    val languageId: String,
    val version: Int,
    val lineCount: Int
) {
    fun getText(range: VSCodeRange? = null): String {
        // TODO: 实现获取文本内容
        return ""
    }
}

