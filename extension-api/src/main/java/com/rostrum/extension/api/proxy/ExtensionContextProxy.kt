package com.rostrum.extension.api.proxy

import android.content.Context
import com.rostrum.core.plugin.ipc.IExtensionHostCallback
import com.rostrum.extension.api.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * ExtensionContext 的 IPC 代理实现
 * 
 * 将本地调用转换为跨进程的 AIDL 调用
 * 这是独立插件与主应用通信的桥梁
 */
class ExtensionContextProxy(
    private val pluginId: String,
    private val hostCallback: IExtensionHostCallback?,
    private val androidContext: Context? = null
) : ExtensionContext {
    
    override val appContext: Context
        get() = androidContext ?: throw IllegalStateException("Context not available in proxy mode")
    
    override val extensionPath: String = "/data/data/com.rostrum/plugins/$pluginId"
    override val storagePath: String = "/data/data/com.rostrum/plugin_storage/$pluginId"
    override val globalStoragePath: String = "/data/data/com.rostrum/plugin_storage/global"
    
    override val subscriptions: MutableList<Disposable> = mutableListOf()
    
    override val workspace: WorkspaceApi = WorkspaceProxy(hostCallback)
    override val fileSystem: FileSystemApi = FileSystemProxy(hostCallback)
    override val terminal: TerminalApi = TerminalProxy(hostCallback, pluginId)
    override val commands: CommandApi = CommandProxy(hostCallback, pluginId)
    override val window: WindowApi = WindowProxy(hostCallback)
    override val ui: UIApi = UIProxy(hostCallback, pluginId)
    
    override fun asAbsolutePath(relativePath: String): String {
        return "$extensionPath/$relativePath"
    }
    
    override fun getConfiguration(section: String?): WorkspaceConfiguration {
        return WorkspaceConfigurationProxy()
    }
}

/**
 * WorkspaceConfiguration 代理
 */
class WorkspaceConfigurationProxy : WorkspaceConfiguration {
    override fun <T> get(key: String, defaultValue: T?): T? = defaultValue
    override fun update(key: String, value: Any?): Boolean = false
}

/**
 * Workspace API 代理
 */
class WorkspaceProxy(private val callback: IExtensionHostCallback?) : WorkspaceApi {
    
    override val folders: List<WorkspaceFolder> = emptyList()
    
    override val selectedFiles: StateFlow<List<FileInfo>> = MutableStateFlow(emptyList())
    
    override suspend fun openFile(fileInfo: FileInfo) {
        // 通过 IPC 打开文件
    }
    
    override fun getConfiguration(section: String?): WorkspaceConfiguration {
        return WorkspaceConfigurationProxy()
    }
}

/**
 * FileSystem API 代理
 */
class FileSystemProxy(private val callback: IExtensionHostCallback?) : FileSystemApi {
    override suspend fun readFile(uri: String): Result<ByteArray> {
        return try {
            val data = callback?.readFile(uri) ?: ByteArray(0)
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun writeFile(uri: String, content: ByteArray): Result<Unit> {
        return try {
            callback?.writeFile(uri, content)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun readTextFile(uri: String, encoding: String): Result<String> {
        return try {
            val text = callback?.readTextFile(uri, encoding) ?: ""
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun writeTextFile(uri: String, content: String, encoding: String): Result<Unit> {
        return try {
            callback?.writeFile(uri, content.toByteArray(charset(encoding)))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun exists(uri: String): Boolean {
        return callback?.fileExists(uri) ?: false
    }
    
    override suspend fun getFileInfo(uri: String): FileInfo? {
        return null // 需要解析 JSON
    }
    
    override suspend fun listDirectory(uri: String): Result<List<FileInfo>> {
        return Result.success(emptyList())
    }
    
    override suspend fun createDirectory(uri: String): Result<Unit> {
        return Result.success(Unit)
    }
    
    override suspend fun delete(uri: String): Result<Unit> {
        return Result.success(Unit)
    }
    
    override suspend fun copy(sourceUri: String, targetUri: String): Result<Unit> {
        return Result.success(Unit)
    }
    
    override suspend fun move(sourceUri: String, targetUri: String): Result<Unit> {
        return Result.success(Unit)
    }
}

/**
 * Terminal API 代理
 */
class TerminalProxy(
    private val callback: IExtensionHostCallback?,
    private val pluginId: String
) : TerminalApi {
    
    override suspend fun createSession(
        shell: String,
        workingDirectory: String,
        environment: Map<String, String>
    ): Result<TerminalSession> {
        val session = TerminalSessionProxy("proxy_$pluginId")
        return Result.success(session)
    }
    
    override suspend fun getSession(sessionId: String): TerminalSession? = null
    
    override suspend fun closeSession(sessionId: String) {}
    
    override suspend fun executeCommand(
        command: String,
        workingDirectory: String?
    ): Result<ExecutionResult> {
        return Result.success(ExecutionResult(
            exitCode = -1,
            output = "Not implemented in proxy",
            error = "",
            executionTime = 0
        ))
    }
}

/**
 * TerminalSession 代理
 */
class TerminalSessionProxy(override val id: String) : TerminalSession {
    override val title: String = "Terminal"
    override val isRunning: Boolean = false
    override val outputFlow: Flow<String> = emptyFlow()
    override val errorFlow: Flow<String> = emptyFlow()
    override val exitFlow: Flow<Int> = emptyFlow()
    
    override suspend fun execute(command: String): ExecutionResult {
        return ExecutionResult(-1, "", "Not implemented", 0)
    }
    
    override suspend fun executeInteractive(command: String): Flow<String> = emptyFlow()
    
    override fun writeInput(input: String) {}
    override fun resize(columns: Int, rows: Int) {}
    override fun terminate() {}
}

/**
 * Command API 代理
 */
class CommandProxy(
    private val callback: IExtensionHostCallback?,
    private val pluginId: String
) : CommandApi {
    
    override fun registerCommand(command: Command): Disposable {
        callback?.registerCommand(pluginId, command.id, command.title)
        return object : Disposable { override fun dispose() {} }
    }
    
    override suspend fun executeCommand(commandId: String, vararg args: Any?): Any? {
        return callback?.executeCommand(commandId, args.joinToString(","))
    }
    
    override fun getCommands(): List<String> = emptyList()
}

/**
 * Window API 代理
 */
class WindowProxy(private val callback: IExtensionHostCallback?) : WindowApi {
    
    override suspend fun showInformationMessage(message: String, vararg items: String): String? {
        return callback?.showInformationMessage(message, items)
    }
    
    override suspend fun showWarningMessage(message: String, vararg items: String): String? {
        return callback?.showWarningMessage(message, items)
    }
    
    override suspend fun showErrorMessage(message: String, vararg items: String): String? {
        return callback?.showErrorMessage(message, items)
    }
    
    override suspend fun showInputBox(options: InputBoxOptions?): String? {
        return callback?.showInputBox(
            options?.prompt ?: "",
            options?.placeHolder ?: "",
            options?.value ?: ""
        )
    }
    
    override fun createStatusBarItem(alignment: StatusBarAlignment): StatusBarItem {
        return StatusBarItemProxy(callback)
    }
}

/**
 * StatusBarItem 代理
 */
class StatusBarItemProxy(private val callback: IExtensionHostCallback?) : StatusBarItem {
    override var text: String = ""
    override var tooltip: String? = null
    override var command: String? = null
    
    override fun show() {
        callback?.showStatusBarMessage(text, 0)
    }
    
    override fun hide() {}
    override fun dispose() {}
}

/**
 * UI API 代理
 */
class UIProxy(
    private val callback: IExtensionHostCallback?,
    private val pluginId: String
) : UIApi {
    
    override fun registerPreview(provider: FilePreviewProvider): Disposable {
        val extJson = provider.supportedExtensions.joinToString(",") { "\"$it\"" }
        callback?.registerPreviewProvider(pluginId, "$pluginId.preview", "[$extJson]")
        return object : Disposable { override fun dispose() {} }
    }
    
    override fun registerEditor(provider: FileEditorProvider): Disposable {
        val extJson = provider.supportedExtensions.joinToString(",") { "\"$it\"" }
        callback?.registerEditorProvider(pluginId, "$pluginId.editor", "[$extJson]")
        return object : Disposable { override fun dispose() {} }
    }
    
    override fun createWebviewPanel(viewType: String, title: String): WebviewPanel {
        return WebviewPanelProxy(viewType, title)
    }
    
    override fun registerStatusBarItem(alignment: StatusBarAlignment): StatusBarItem {
        return StatusBarItemProxy(callback)
    }
}

/**
 * WebviewPanel 代理
 */
class WebviewPanelProxy(
    override val viewType: String,
    override val title: String
) : WebviewPanel {
    override fun setHtml(html: String) {}
    override fun executeJavaScript(script: String) {}
    override fun show() {}
    override fun hide() {}
    override fun dispose() {}
}
