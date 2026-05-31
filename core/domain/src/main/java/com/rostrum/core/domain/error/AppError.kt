package com.rostrum.core.domain.error

/**
 * 统一应用错误类型
 * 
 * 定义应用中可能发生的各种错误类型，
 * 用于跨层统一错误处理和传递
 */
sealed class AppError : Exception() {
    
    /**
     * 网络错误
     */
    data class NetworkError(
        val throwable: Throwable,
        val statusCode: Int? = null,
        val url: String? = null
    ) : AppError() {
        override val message: String
            get() = "网络错误: ${throwable.message}" + (statusCode?.let { " (状态码: $it)" } ?: "")
        override val cause: Throwable get() = throwable
    }
    
    /**
     * 插件错误
     */
    data class PluginError(
        val pluginId: String,
        val pluginName: String? = null,
        override val message: String
    ) : AppError()
    
    /**
     * 文件操作错误
     */
    data class FileError(
        val path: String,
        val operation: FileOperation,
        override val message: String
    ) : AppError()
    
    /**
     * 工具执行错误
     */
    data class ToolError(
        val toolName: String,
        val reason: String,
        val parameters: Map<String, Any> = emptyMap()
    ) : AppError() {
        override val message: String
            get() = "工具 '$toolName' 执行错误: $reason"
    }
    
    /**
     * 权限错误
     */
    data class PermissionError(
        val permission: String,
        override val message: String = "缺少权限: $permission"
    ) : AppError()
    
    /**
     * 配置错误
     */
    data class ConfigurationError(
        val key: String,
        override val message: String
    ) : AppError()
    
    /**
     * 验证错误
     */
    data class ValidationError(
        val field: String,
        override val message: String
    ) : AppError()
    
    /**
     * 未知错误
     */
    data class UnknownError(
        val throwable: Throwable? = null,
        override val message: String = throwable?.message ?: "未知错误"
    ) : AppError() {
        override val cause: Throwable? get() = throwable
    }
    
    /**
     * 获取用户友好的错误消息
     */
    fun getUserMessage(): String = message ?: "发生错误"
    
    /**
     * 是否可重试
     */
    fun isRetryable(): Boolean = when (this) {
        is NetworkError -> statusCode != 401 && statusCode != 403 && statusCode != 404
        is ToolError -> true
        else -> false
    }
}

/**
 * 文件操作类型
 */
enum class FileOperation {
    READ,
    WRITE,
    DELETE,
    CREATE,
    RENAME,
    MOVE,
    COPY,
    COMPRESS,
    DECOMPRESS
}

/**
 * 应用结果类型别名
 */
typealias AppResult<T> = Result<T>

/**
 * 扩展函数：将 Throwable 转换为 AppError
 */
fun Throwable.toAppError(): AppError {
    return when (this) {
        is AppError -> this
        is java.net.UnknownHostException -> AppError.NetworkError(this)
        is java.net.SocketTimeoutException -> AppError.NetworkError(this)
        is java.io.IOException -> AppError.FileError(
            path = "",
            operation = FileOperation.READ,
            message = this.message ?: "IO 错误"
        )
        is SecurityException -> AppError.PermissionError(
            permission = "unknown",
            message = this.message ?: "权限错误"
        )
        else -> AppError.UnknownError(this)
    }
}

/**
 * 扩展函数：将错误映射为 AppError
 */
fun <T> Result<T>.mapToAppError(): Result<T> {
    return this.onFailure { error ->
        if (error !is AppError) {
            throw error.toAppError()
        }
    }
}

/**
 * 扩展函数：获取 AppError 或 null
 */
fun <T> Result<T>.appErrorOrNull(): AppError? {
    return exceptionOrNull() as? AppError
}

/**
 * 扩展函数：处理 AppError
 */
inline fun <T> Result<T>.onAppError(action: (AppError) -> Unit): Result<T> {
    val error = exceptionOrNull()
    if (error is AppError) {
        action(error)
    } else if (error != null) {
        action(error.toAppError())
    }
    return this
}
