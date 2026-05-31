package com.rostrum.core.plugin.ipc

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.rostrum.core.filesystem.FileSystemService
import com.rostrum.core.terminal.TerminalService
import com.rostrum.extension.api.FileInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Extension Host Service
 * 
 * 运行在主进程中，处理来自插件进程的 IPC 请求
 */
class ExtensionHostService : Service() {
    private val TAG = "ExtensionHostService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 依赖注入的服务（由主应用提供）
    private var fileSystemService: FileSystemService? = null
    private var terminalService: TerminalService? = null
    
    // 命令注册表（临时实现，后续应该从 CommandApi 获取）
    private val registeredCommands = mutableMapOf<String, CommandHandler>()
    
    // 预览提供者注册表（临时实现，后续应该从 UIApi 获取）
    private val previewProviders = mutableMapOf<String, PreviewProviderInfo>()
    
    private val binder = object : com.rostrum.core.plugin.ipc.IExtensionHostService.Stub() {
        @Throws(RemoteException::class)
        override fun readFile(uri: String): ByteArray {
            return runBlocking {
                fileSystemService?.readFile(uri)?.getOrNull() ?: ByteArray(0)
            }
        }
        
        @Throws(RemoteException::class)
        override fun writeFile(uri: String, content: ByteArray?) {
            runBlocking {
                fileSystemService?.writeFile(uri, content ?: ByteArray(0))
            }
        }
        
        @Throws(RemoteException::class)
        override fun readTextFile(uri: String, encoding: String): String {
            return runBlocking {
                fileSystemService?.readTextFile(uri, encoding)?.getOrNull() ?: ""
            }
        }
        
        @Throws(RemoteException::class)
        override fun writeTextFile(uri: String, content: String?, encoding: String) {
            runBlocking {
                fileSystemService?.writeTextFile(uri, content ?: "", encoding)
            }
        }
        
        @Throws(RemoteException::class)
        override fun exists(uri: String): Boolean {
            return runBlocking {
                fileSystemService?.exists(uri) ?: false
            }
        }
        
        @Throws(RemoteException::class)
        override fun getFileInfo(uri: String): String {
            return runBlocking {
                val fileInfo = fileSystemService?.getFileInfo(uri)
                if (fileInfo != null) {
                    // 转换为 Extension API 的 FileInfo 并序列化
                    val extensionFileInfo = com.rostrum.extension.api.FileInfo(
                        uri = uri,  // 使用传入的 URI
                        name = fileInfo.name,
                        path = fileInfo.path,
                        size = fileInfo.size,
                        lastModified = fileInfo.lastModified,
                        isDirectory = fileInfo.isDirectory,
                        isFile = !fileInfo.isDirectory,
                        mimeType = fileInfo.mimeType,
                        extension = fileInfo.extension
                    )
                    com.rostrum.core.plugin.ipc.serialization.FileInfoSerialization.serialize(extensionFileInfo)
                } else {
                    "{}"
                }
            }
        }
        
        @Throws(RemoteException::class)
        override fun listDirectory(uri: String): String {
            return runBlocking {
                val result = fileSystemService?.listDirectory(uri)
                val files = result?.getOrNull() ?: emptyList()
                // 转换为 Extension API 的 FileInfo 列表并序列化
                val extensionFileInfos = files.map { fileInfo ->
                    com.rostrum.extension.api.FileInfo(
                        uri = fileInfo.path,  // 使用 path 作为 URI
                        name = fileInfo.name,
                        path = fileInfo.path,
                        size = fileInfo.size,
                        lastModified = fileInfo.lastModified,
                        isDirectory = fileInfo.isDirectory,
                        isFile = !fileInfo.isDirectory,
                        mimeType = fileInfo.mimeType,
                        extension = fileInfo.extension
                    )
                }
                com.rostrum.core.plugin.ipc.serialization.FileInfoSerialization.serializeList(extensionFileInfos)
            }
        }
        
        @Throws(RemoteException::class)
        override fun createDirectory(uri: String) {
            runBlocking {
                fileSystemService?.createDirectory(uri)
            }
        }
        
        @Throws(RemoteException::class)
        override fun delete(uri: String) {
            runBlocking {
                fileSystemService?.delete(uri)
            }
        }
        
        @Throws(RemoteException::class)
        override fun copy(sourceUri: String, targetUri: String) {
            runBlocking {
                fileSystemService?.copy(sourceUri, targetUri)
            }
        }
        
        @Throws(RemoteException::class)
        override fun move(sourceUri: String, targetUri: String) {
            runBlocking {
                fileSystemService?.move(sourceUri, targetUri)
            }
        }
        
        @Throws(RemoteException::class)
        override fun executeCommand(command: String, workingDirectory: String?): String {
            return runBlocking {
                try {
                    val session = terminalService?.createSession(
                        workingDirectory = workingDirectory ?: "/"
                    )?.getOrNull()
                    if (session != null) {
                        val result = session.execute(command)
                        terminalService?.closeSession(session.id)
                        result.output
                    } else {
                        ""
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to execute command: $command", e)
                    ""
                }
            }
        }
        
        @Throws(RemoteException::class)
        override fun registerCommand(commandId: String, title: String, category: String?) {
            registeredCommands[commandId] = CommandHandler(commandId, title, category)
            Log.d(TAG, "Command registered: $commandId")
        }
        
        @Throws(RemoteException::class)
        override fun executeRegisteredCommand(commandId: String, argsJson: String): String {
            val handler = registeredCommands[commandId]
            return if (handler != null) {
                // 执行命令（简化实现）
                "Command executed: $commandId"
            } else {
                "Command not found: $commandId"
            }
        }
        
        @Throws(RemoteException::class)
        override fun registerPreview(pluginId: String, mimeTypesJson: String, extensionsJson: String) {
            previewProviders[pluginId] = PreviewProviderInfo(
                pluginId = pluginId,
                mimeTypes = Json.decodeFromString<List<String>>(mimeTypesJson),
                extensions = Json.decodeFromString<List<String>>(extensionsJson)
            )
            Log.d(TAG, "Preview provider registered: $pluginId")
        }
        
        @Throws(RemoteException::class)
        override fun registerEditor(pluginId: String, mimeTypesJson: String, extensionsJson: String) {
            // 类似预览提供者
            Log.d(TAG, "Editor provider registered: $pluginId")
        }
        
        @Throws(RemoteException::class)
        override fun showInformationMessage(message: String, items: Array<String>): String {
            // 简化实现，实际应该显示对话框并返回用户选择
            Log.d(TAG, "Information message: $message")
            return if (items != null && items.isNotEmpty()) items[0] else ""
        }
        
        @Throws(RemoteException::class)
        override fun showWarningMessage(message: String, items: Array<String>): String {
            Log.d(TAG, "Warning message: $message")
            return if (items != null && items.isNotEmpty()) items[0] else ""
        }
        
        @Throws(RemoteException::class)
        override fun showErrorMessage(message: String, items: Array<String>): String {
            Log.d(TAG, "Error message: $message")
            return if (items != null && items.isNotEmpty()) items[0] else ""
        }
        
        @Throws(RemoteException::class)
        override fun showInputBox(
            prompt: String?,
            placeHolder: String?,
            defaultValue: String?,
            password: Boolean
        ): String {
            // 简化实现，实际应该显示输入框并返回用户输入
            Log.d(TAG, "Input box: $prompt")
            return defaultValue ?: ""
        }
        
        @Throws(RemoteException::class)
        override fun notifyEvent(eventType: String, eventData: String) {
            Log.d(TAG, "Event notified: $eventType, data: $eventData")
            // 发布事件到事件总线
        }
        
        @Throws(RemoteException::class)
        override fun ping(): Boolean {
            return true
        }
        
        @Throws(RemoteException::class)
        override fun executeBatch(requestJson: String): String {
            return runBlocking {
                try {
                    // 解析批量操作请求
                    val request = kotlinx.serialization.json.Json.decodeFromString<com.rostrum.core.plugin.ipc.batch.BatchOperationRequest>(requestJson)
                    
                    // 执行批量操作
                    val results = mutableMapOf<String, com.rostrum.core.plugin.ipc.batch.BatchOperationResponse.OperationResult>()
                    
                    for (operation in request.operations) {
                        val result = executeSingleOperation(operation)
                        results[operation.id] = result
                    }
                    
                    // 返回批量操作响应
                    val response = com.rostrum.core.plugin.ipc.batch.BatchOperationResponse(results)
                    kotlinx.serialization.json.Json.encodeToString(response)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to execute batch operation", e)
                    "{\"results\":{}}"
                }
            }
        }
        
        /**
         * 执行单个操作
         */
        private suspend fun executeSingleOperation(
            operation: com.rostrum.core.plugin.ipc.batch.BatchOperationRequest.Operation
        ): com.rostrum.core.plugin.ipc.batch.BatchOperationResponse.OperationResult {
            return try {
                when (operation.type) {
                    com.rostrum.core.plugin.ipc.batch.BatchOperationRequest.OperationType.READ_FILE -> {
                        val params = kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(operation.params)
                        val uri = params["uri"] ?: throw IllegalArgumentException("Missing uri parameter")
                        val data = fileSystemService?.readFile(uri)?.getOrNull() ?: ByteArray(0)
                        com.rostrum.core.plugin.ipc.batch.BatchOperationResponse.OperationResult(
                            success = true,
                            data = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
                        )
                    }
                    com.rostrum.core.plugin.ipc.batch.BatchOperationRequest.OperationType.EXISTS -> {
                        val params = kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(operation.params)
                        val uri = params["uri"] ?: throw IllegalArgumentException("Missing uri parameter")
                        val exists = fileSystemService?.exists(uri) ?: false
                        com.rostrum.core.plugin.ipc.batch.BatchOperationResponse.OperationResult(
                            success = true,
                            data = exists.toString()
                        )
                    }
                    // 其他操作类型...
                    else -> {
                        com.rostrum.core.plugin.ipc.batch.BatchOperationResponse.OperationResult(
                            success = false,
                            error = "Unsupported operation type: ${operation.type}"
                        )
                    }
                }
            } catch (e: Exception) {
                com.rostrum.core.plugin.ipc.batch.BatchOperationResponse.OperationResult(
                    success = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ExtensionHostService created")
        
        // 初始化服务（应该通过依赖注入获取）
        // fileSystemService = ...
        // terminalService = ...
    }
    
    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "ExtensionHostService bound")
        return binder as IBinder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ExtensionHostService destroyed")
    }
    
    /**
     * 设置文件系统服务（依赖注入）
     */
    fun setFileSystemService(service: FileSystemService) {
        this.fileSystemService = service
    }
    
    /**
     * 设置终端服务（依赖注入）
     */
    fun setTerminalService(service: TerminalService) {
        this.terminalService = service
    }
}

/**
 * 命令处理器信息
 */
data class CommandHandler(
    val commandId: String,
    val title: String,
    val category: String?
)

/**
 * 预览提供者信息
 */
data class PreviewProviderInfo(
    val pluginId: String,
    val mimeTypes: List<String>,
    val extensions: List<String>
)

