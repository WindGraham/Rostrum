package com.rostrum.extension.api.proxy

import android.content.Context
import com.rostrum.core.plugin.ipc.ExtensionHostClient
import com.rostrum.extension.api.*
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/**
 * ExtensionContext 的代理实现
 * 
 * 在插件进程中创建，所有 API 调用通过 IPC 转发到主进程
 */
class ExtensionContextProxy(
    private val client: ExtensionHostClient,
    override val appContext: Context,
    override val extensionPath: String,
    override val storagePath: String,
    override val globalStoragePath: String
) : ExtensionContext {
    
    private val service: com.rostrum.core.plugin.ipc.IExtensionHostService = client.getService()
        ?: throw IllegalStateException("ExtensionHostClient is not connected")
    
    override val fileSystem: FileSystemApi = FileSystemApiProxy(
        service = service,
        cache = com.rostrum.core.plugin.ipc.cache.IPCCache(),  // 创建缓存实例
        enableRetry = true,
        timeoutMs = 30000L
    )
    
    override val terminal: TerminalApi = TerminalApiProxy(service)
    
    override val ui: UIApi = UIApiProxy(service, extensionPath)
    
    override val commands: CommandApi = CommandApiProxy(service)
    
    override val workspace: WorkspaceApi = WorkspaceApiProxy(service)
    
    override val window: WindowApi = WindowApiProxy(service)
    
    override val subscriptions: MutableList<Disposable> = mutableListOf()
    
    override fun asAbsolutePath(relativePath: String): String {
        return File(extensionPath, relativePath).absolutePath
    }
    
    override fun getConfiguration(section: String?): WorkspaceConfiguration {
        // 简化实现
        return object : WorkspaceConfiguration {
            override fun <T> get(key: String, defaultValue: T?): T? = defaultValue
            override fun update(key: String, value: Any?): Boolean = false
        }
    }
}

/**
 * TerminalApi 的 IPC 代理实现
 */
class TerminalApiProxy(
    private val service: com.rostrum.core.plugin.ipc.IExtensionHostService
) : TerminalApi {
    
    override suspend fun createSession(
        shell: String,
        workingDirectory: String,
        environment: Map<String, String>
    ): Result<TerminalSession> {
        // 简化实现，实际应该创建真正的终端会话
        return Result.failure(UnsupportedOperationException("Terminal session creation not yet implemented via IPC"))
    }
    
    override suspend fun getSession(sessionId: String): TerminalSession? {
        return null
    }
    
    override suspend fun closeSession(sessionId: String) {
        // 实现关闭会话
    }
    
    override suspend fun executeCommand(
        command: String,
        workingDirectory: String?
    ): Result<ExecutionResult> {
        return try {
            val output = service.executeCommand(command, workingDirectory ?: "")
            Result.success(ExecutionResult(
                exitCode = 0,
                output = output,
                error = "",
                executionTime = 0
            ))
        } catch (e: android.os.RemoteException) {
            Result.failure(e)
        }
    }
}

/**
 * UIApi 的 IPC 代理实现
 */
class UIApiProxy(
    private val service: com.rostrum.core.plugin.ipc.IExtensionHostService,
    private val pluginId: String
) : UIApi {
    
    override fun registerPreview(provider: FilePreviewProvider): Disposable {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val mimeTypesJson = json.encodeToString(
            ListSerializer(String.serializer()),
            provider.supportedMimeTypes
        )
        val extensionsJson = json.encodeToString(
            ListSerializer(String.serializer()),
            provider.supportedExtensions
        )
        
        try {
            service.registerPreview(pluginId, mimeTypesJson, extensionsJson)
        } catch (e: android.os.RemoteException) {
            android.util.Log.e("UIApiProxy", "Failed to register preview", e)
        }
        
        return object : Disposable {
            override fun dispose() {
                // 取消注册（需要服务端支持）
            }
        }
    }
    
    override fun registerEditor(provider: FileEditorProvider): Disposable {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val mimeTypesJson = json.encodeToString(
            ListSerializer(String.serializer()),
            provider.supportedMimeTypes
        )
        val extensionsJson = json.encodeToString(
            ListSerializer(String.serializer()),
            provider.supportedExtensions
        )
        
        try {
            service.registerEditor(pluginId, mimeTypesJson, extensionsJson)
        } catch (e: android.os.RemoteException) {
            android.util.Log.e("UIApiProxy", "Failed to register editor", e)
        }
        
        return object : Disposable {
            override fun dispose() {
                // 取消注册
            }
        }
    }
    
    override fun createWebviewPanel(viewType: String, title: String): WebviewPanel {
        // 简化实现
        return object : WebviewPanel {
            override val title: String = title
            override val viewType: String = viewType
            
            override fun setHtml(html: String) {}
            override fun executeJavaScript(script: String) {}
            override fun show() {}
            override fun hide() {}
            override fun dispose() {}
        }
    }
    
    override fun registerStatusBarItem(alignment: StatusBarAlignment): StatusBarItem {
        // 简化实现
        return object : StatusBarItem {
            override var text: String = ""
            override var tooltip: String? = null
            override var command: String? = null
            
            override fun show() {}
            override fun hide() {}
            override fun dispose() {}
        }
    }
}

/**
 * CommandApi 的 IPC 代理实现
 */
class CommandApiProxy(
    private val service: com.rostrum.core.plugin.ipc.IExtensionHostService
) : CommandApi {
    
    override fun registerCommand(command: Command): Disposable {
        try {
            service.registerCommand(command.id, command.title, command.category)
        } catch (e: android.os.RemoteException) {
            android.util.Log.e("CommandApiProxy", "Failed to register command", e)
        }
        
        return object : Disposable {
            override fun dispose() {
                // 取消注册
            }
        }
    }
    
    override suspend fun executeCommand(commandId: String, vararg args: Any?): Any? {
        // 将参数转换为字符串列表（简化实现）
        val argsList = args.map { it?.toString() ?: "null" }
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val argsJson = json.encodeToString(
            ListSerializer(String.serializer()),
            argsList
        )
        return try {
            service.executeRegisteredCommand(commandId, argsJson)
        } catch (e: android.os.RemoteException) {
            android.util.Log.e("CommandApiProxy", "Failed to execute command", e)
            null
        }
    }
    
    override fun getCommands(): List<String> {
        // 简化实现，实际应该从服务端获取
        return emptyList()
    }
}

/**
 * WorkspaceApi 的 IPC 代理实现
 */
class WorkspaceApiProxy(
    private val service: com.rostrum.core.plugin.ipc.IExtensionHostService
) : WorkspaceApi {
    
    override val folders: List<WorkspaceFolder> = emptyList()
    
    override val selectedFiles: kotlinx.coroutines.flow.StateFlow<List<FileInfo>> = 
        kotlinx.coroutines.flow.MutableStateFlow<List<FileInfo>>(emptyList()).asStateFlow()
    
    override suspend fun openFile(fileInfo: FileInfo) {
        // 实现打开文件
    }
    
    override fun getConfiguration(section: String?): WorkspaceConfiguration {
        return object : WorkspaceConfiguration {
            override fun <T> get(key: String, defaultValue: T?): T? = defaultValue
            override fun update(key: String, value: Any?): Boolean = false
        }
    }
}

/**
 * WindowApi 的 IPC 代理实现
 */
class WindowApiProxy(
    private val service: com.rostrum.core.plugin.ipc.IExtensionHostService
) : WindowApi {
    
    override suspend fun showInformationMessage(message: String, vararg items: String): String? {
        return try {
            // vararg 转换为 Array<String>
            val itemsArray: Array<String> = arrayOf(*items)
            val result = service.showInformationMessage(message, itemsArray)
            if (result.isBlank()) null else result
        } catch (e: android.os.RemoteException) {
            android.util.Log.e("WindowApiProxy", "Failed to show information message", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("WindowApiProxy", "Unexpected error showing information message", e)
            null
        }
    }
    
    override suspend fun showWarningMessage(message: String, vararg items: String): String? {
        return try {
            // vararg 转换为 Array<String>
            val itemsArray: Array<String> = arrayOf(*items)
            val result = service.showWarningMessage(message, itemsArray)
            if (result.isBlank()) null else result
        } catch (e: android.os.RemoteException) {
            android.util.Log.e("WindowApiProxy", "Failed to show warning message", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("WindowApiProxy", "Unexpected error showing warning message", e)
            null
        }
    }
    
    override suspend fun showErrorMessage(message: String, vararg items: String): String? {
        return try {
            // vararg 转换为 Array<String>
            val itemsArray: Array<String> = arrayOf(*items)
            val result = service.showErrorMessage(message, itemsArray)
            if (result.isBlank()) null else result
        } catch (e: android.os.RemoteException) {
            android.util.Log.e("WindowApiProxy", "Failed to show error message", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("WindowApiProxy", "Unexpected error showing error message", e)
            null
        }
    }
    
    override suspend fun showInputBox(options: InputBoxOptions?): String? {
        return try {
            val result = service.showInputBox(
                options?.prompt,
                options?.placeHolder,
                options?.value,
                options?.password ?: false
            )
            if (result.isBlank()) null else result
        } catch (e: android.os.RemoteException) {
            android.util.Log.e("WindowApiProxy", "Failed to show input box", e)
            null
        }
    }
    
    override fun createStatusBarItem(alignment: StatusBarAlignment): StatusBarItem {
        return object : StatusBarItem {
            override var text: String = ""
            override var tooltip: String? = null
            override var command: String? = null
            
            override fun show() {}
            override fun hide() {}
            override fun dispose() {}
        }
    }
}

