package com.rostrum.core.plugin.impl

import android.content.Context
import com.rostrum.core.config.ConfigManager
import com.rostrum.core.event.EventBus
import com.rostrum.core.filesystem.FileSystemService
import com.rostrum.core.mcp.MCPProtocol
import com.rostrum.core.plugin.*
import com.rostrum.core.plugin.language.LanguageSupportRegistry
import com.rostrum.core.plugin.providers.FileEditorPlugin
import com.rostrum.core.plugin.providers.FilePreviewPlugin
import com.rostrum.core.plugin.providers.LanguageSupportPlugin
import com.rostrum.core.terminal.TerminalService
import com.rostrum.core.domain.model.FileItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PluginContext的基础实现
 * 
 * 这是一个占位实现，后续需要根据实际需求完善
 */
class PluginContextImpl(
    override val plugin: Plugin,
    override val appContext: Context,
    override val pluginManager: PluginManager,
    override val fileSystem: FileSystemService,
    override val terminal: TerminalService,
    override val mcp: MCPProtocol,
    override val eventBus: EventBus,
    override val config: ConfigManager,
    private val workspaceImpl: Workspace = com.rostrum.core.plugin.impl.WorkspaceImpl("", MutableStateFlow(emptyList())),
    private val commandsImpl: CommandRegistry = CommandRegistryImpl(),
    private val windowImpl: Window = com.rostrum.core.plugin.impl.WindowImpl(appContext, null, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())),
    private val uiImpl: UIRegistry = com.rostrum.core.plugin.preview.PreviewPluginRegistry.getGlobalUIRegistry()
) : PluginContext {
    
    override val workspace: Workspace = workspaceImpl
    override val commands: CommandRegistry = commandsImpl
    override val window: Window = windowImpl
    override val ui: UIRegistry = uiImpl
    override val subscriptions = mutableListOf<Disposable>()
    
    override fun registerCommand(command: Command): Disposable {
        return commands.register(command)
    }
    
    override fun registerFilePreview(plugin: FilePreviewPlugin): Disposable {
        return ui.registerPreview(plugin)
    }
    
    override fun registerFileEditor(plugin: FileEditorPlugin): Disposable {
        return ui.registerEditor(plugin)
    }
    
    override fun registerLanguageSupport(plugin: LanguageSupportPlugin): Disposable {
        LanguageSupportRegistry.register(plugin)
        return object : Disposable {
            override fun dispose() {
                LanguageSupportRegistry.unregister(plugin)
            }
        }
    }
    
}

// WorkspaceImpl 已在 WorkspaceImpl.kt 中定义，这里注释掉避免重复
/*
/**
 * Workspace的基础实现
 */
class WorkspaceImpl : Workspace {
    private val _selectedFiles = MutableStateFlow<List<FileItem>>(emptyList())
    override val selectedFiles: StateFlow<List<FileItem>> = _selectedFiles.asStateFlow()
    
    override val folders: List<WorkspaceFolder> = emptyList()
    
    override suspend fun openFile(file: FileItem) {
        // TODO: 实现打开文件逻辑
    }
    
    override fun getConfiguration(section: String?): WorkspaceConfiguration {
        return WorkspaceConfigurationImpl()
    }
}
*/

/**
 * CommandRegistry的基础实现
 */
class CommandRegistryImpl : CommandRegistry {
    private val commands = mutableMapOf<String, Command>()
    
    override fun register(command: Command): Disposable {
        commands[command.id] = command
        return object : Disposable {
            override fun dispose() {
                commands.remove(command.id)
            }
        }
    }
    
    override suspend fun executeCommand(commandId: String, vararg args: Any?): Any? {
        val command = commands[commandId] ?: return null
        return command.handler(args.toList())
    }
    
    override fun getCommands(): List<String> {
        return commands.keys.toList()
    }
}

// WindowImpl 已在 WindowImpl.kt 中定义，这里注释掉避免重复
/*
/**
 * Window的基础实现
 */
class WindowImpl : Window {
    override suspend fun showInformationMessage(message: String, vararg items: String): String? {
        // TODO: 实现显示信息消息
        return null
    }
    
    override suspend fun showWarningMessage(message: String, vararg items: String): String? {
        // TODO: 实现显示警告消息
        return null
    }
    
    override suspend fun showErrorMessage(message: String, vararg items: String): String? {
        // TODO: 实现显示错误消息
        return null
    }
    
    override suspend fun showInputBox(options: InputBoxOptions?): String? {
        // TODO: 实现显示输入框
        return null
    }
    
    override fun createStatusBarItem(alignment: StatusBarAlignment): StatusBarItem {
        return StatusBarItemImpl()
    }
}
*/

/**
 * UIRegistry的基础实现
 */
class UIRegistryImpl : UIRegistry {
    private val previewPlugins = mutableListOf<FilePreviewPlugin>()
    private val editorPlugins = mutableListOf<FileEditorPlugin>()
    
    override fun registerPreview(plugin: FilePreviewPlugin): Disposable {
        previewPlugins.add(plugin)
        return object : Disposable {
            override fun dispose() {
                previewPlugins.remove(plugin)
            }
        }
    }
    
    /**
     * 获取所有已注册的预览插件
     */
    fun getPreviewPlugins(): List<FilePreviewPlugin> {
        return previewPlugins.toList()
    }
    
    /**
     * 查找最适合预览指定文件的插件
     */
    fun findPreviewPlugin(fileInfo: com.rostrum.core.plugin.models.FileInfo): FilePreviewPlugin? {
        return previewPlugins
            .filter { 
                it.canPreview(fileInfo) && 
                com.rostrum.core.plugin.preview.PluginStateManager.isEnabled(it.id)
            }
            .maxByOrNull { it.getPreviewPriority(fileInfo) }
    }
    
    override fun registerEditor(plugin: FileEditorPlugin): Disposable {
        editorPlugins.add(plugin)
        return object : Disposable {
            override fun dispose() {
                editorPlugins.remove(plugin)
            }
        }
    }
    
    override fun createWebviewPanel(viewType: String, title: String): com.rostrum.core.plugin.vscode.WebviewPanel {
        // TODO: 实现创建Webview面板
        return com.rostrum.core.plugin.vscode.WebviewPanelImpl(title, viewType)
    }
}

/**
 * WorkspaceConfiguration的基础实现
 */
class WorkspaceConfigurationImpl : WorkspaceConfiguration {
    private val config = mutableMapOf<String, Any?>()
    
    override fun <T> get(key: String, defaultValue: T?): T? {
        @Suppress("UNCHECKED_CAST")
        return config[key] as? T ?: defaultValue
    }
    
    override fun update(key: String, value: Any?): Boolean {
        config[key] = value
        return true
    }
}

/**
 * StatusBarItem的基础实现
 */
class StatusBarItemImpl : StatusBarItem {
    override var text: String = ""
    override var tooltip: String? = null
    override var command: String? = null
    private var isVisible = false
    
    override fun show() {
        isVisible = true
    }
    
    override fun hide() {
        isVisible = false
    }
    
    override fun dispose() {
        hide()
    }
}
