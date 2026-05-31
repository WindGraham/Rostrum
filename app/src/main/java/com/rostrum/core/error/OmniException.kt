package com.rostrum.core.error

/**
 * OmniMaster 异常基类
 * 
 * 所有自定义异常的基类，包含错误码便于分类和追踪
 */
sealed class OmniException(
    message: String,
    cause: Throwable? = null,
    val errorCode: ErrorCode = ErrorCode.UNKNOWN
) : Exception(message, cause) {
    
    override fun toString(): String = "${errorCode}: $message"
}

/**
 * 网络异常
 */
class NetworkException(
    message: String,
    cause: Throwable? = null,
    errorCode: ErrorCode = ErrorCode.NETWORK_ERROR
) : OmniException(message, cause, errorCode) {
    
    companion object {
        fun timeout(message: String = "Network operation timed out", cause: Throwable? = null) =
            NetworkException(message, cause, ErrorCode.NETWORK_TIMEOUT)
        
        fun connectionFailed(message: String = "Failed to connect", cause: Throwable? = null) =
            NetworkException(message, cause, ErrorCode.CONNECTION_FAILED)
        
        fun authFailed(message: String = "Authentication failed", cause: Throwable? = null) =
            NetworkException(message, cause, ErrorCode.AUTHENTICATION_FAILED)
    }
}

/**
 * 文件操作异常
 */
class FileOperationException(
    message: String,
    cause: Throwable? = null,
    errorCode: ErrorCode = ErrorCode.FILE_ERROR
) : OmniException(message, cause, errorCode) {
    
    companion object {
        fun notFound(path: String, cause: Throwable? = null) =
            FileOperationException("File not found: $path", cause, ErrorCode.FILE_NOT_FOUND)
        
        fun accessDenied(path: String, cause: Throwable? = null) =
            FileOperationException("Access denied: $path", cause, ErrorCode.FILE_ACCESS_DENIED)
        
        fun readError(path: String, cause: Throwable? = null) =
            FileOperationException("Failed to read: $path", cause, ErrorCode.FILE_READ_ERROR)
        
        fun writeError(path: String, cause: Throwable? = null) =
            FileOperationException("Failed to write: $path", cause, ErrorCode.FILE_WRITE_ERROR)
        
        fun tooLarge(path: String, maxSize: Long, cause: Throwable? = null) =
            FileOperationException("File too large: $path (max: $maxSize bytes)", cause, ErrorCode.FILE_TOO_LARGE)
    }
}

/**
 * 插件异常
 */
class PluginException(
    message: String,
    cause: Throwable? = null,
    errorCode: ErrorCode = ErrorCode.PLUGIN_ERROR
) : OmniException(message, cause, errorCode) {
    
    companion object {
        fun notFound(pluginId: String, cause: Throwable? = null) =
            PluginException("Plugin not found: $pluginId", cause, ErrorCode.PLUGIN_NOT_FOUND)
        
        fun loadFailed(pluginId: String, cause: Throwable? = null) =
            PluginException("Failed to load plugin: $pluginId", cause, ErrorCode.PLUGIN_LOAD_FAILED)
        
        fun initFailed(pluginId: String, cause: Throwable? = null) =
            PluginException("Failed to initialize plugin: $pluginId", cause, ErrorCode.PLUGIN_INIT_FAILED)
        
        fun executionFailed(pluginId: String, cause: Throwable? = null) =
            PluginException("Plugin execution failed: $pluginId", cause, ErrorCode.PLUGIN_EXECUTION_FAILED)
    }
}

/**
 * 配置异常
 */
class ConfigException(
    message: String,
    cause: Throwable? = null,
    errorCode: ErrorCode = ErrorCode.CONFIG_ERROR
) : OmniException(message, cause, errorCode) {
    
    companion object {
        fun notFound(key: String, cause: Throwable? = null) =
            ConfigException("Configuration not found: $key", cause, ErrorCode.CONFIG_NOT_FOUND)
        
        fun invalid(key: String, reason: String, cause: Throwable? = null) =
            ConfigException("Invalid configuration for $key: $reason", cause, ErrorCode.CONFIG_INVALID)
        
        fun saveFailed(cause: Throwable? = null) =
            ConfigException("Failed to save configuration", cause, ErrorCode.CONFIG_SAVE_FAILED)
        
        fun loadFailed(cause: Throwable? = null) =
            ConfigException("Failed to load configuration", cause, ErrorCode.CONFIG_LOAD_FAILED)
    }
}

/**
 * Shell 异常
 */
class ShellException(
    message: String,
    cause: Throwable? = null,
    errorCode: ErrorCode = ErrorCode.SHELL_ERROR
) : OmniException(message, cause, errorCode) {
    
    companion object {
        fun executionFailed(command: String, cause: Throwable? = null) =
            ShellException("Shell execution failed: $command", cause, ErrorCode.SHELL_EXECUTION_FAILED)
        
        fun timeout(command: String, cause: Throwable? = null) =
            ShellException("Shell execution timed out: $command", cause, ErrorCode.SHELL_TIMEOUT)
        
        fun permissionDenied(cause: Throwable? = null) =
            ShellException("Shell permission denied", cause, ErrorCode.SHELL_PERMISSION_DENIED)
    }
}

/**
 * RPC 异常
 */
class RPCException(
    message: String,
    cause: Throwable? = null,
    errorCode: ErrorCode = ErrorCode.RPC_ERROR,
    val rpcCode: Int = -1
) : OmniException(message, cause, errorCode) {
    
    companion object {
        fun connectionFailed(cause: Throwable? = null) =
            RPCException("RPC connection failed", cause, ErrorCode.RPC_CONNECTION_FAILED)
        
        fun methodNotFound(method: String, cause: Throwable? = null) =
            RPCException("RPC method not found: $method", cause, ErrorCode.RPC_METHOD_NOT_FOUND)
        
        fun invalidParams(method: String, cause: Throwable? = null) =
            RPCException("Invalid RPC parameters for: $method", cause, ErrorCode.RPC_INVALID_PARAMS)
        
        fun timeout(cause: Throwable? = null) =
            RPCException("RPC operation timed out", cause, ErrorCode.RPC_TIMEOUT)
    }
}
