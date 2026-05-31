package com.rostrum.core.error

import android.util.Log

/**
 * 统一错误处理器
 * 
 * 提供一致的错误日志记录和处理机制
 */
object ErrorHandler {
    
    private const val TAG = "OmniMaster"
    
    /**
     * 处理异常
     * 
     * @param e 异常
     * @param context 上下文描述（如类名、方法名）
     * @param silent 是否静默处理（不打印堆栈）
     */
    fun handle(e: Throwable, context: String = "", silent: Boolean = false) {
        val tag = if (context.isNotEmpty()) "$TAG.$context" else TAG
        val message = buildErrorMessage(e)
        
        if (silent) {
            Log.w(tag, message)
        } else {
            Log.e(tag, message, e)
        }
    }
    
    /**
     * 处理异常并返回默认值
     */
    fun <T> handleWithDefault(e: Throwable, context: String = "", default: T): T {
        handle(e, context)
        return default
    }
    
    /**
     * 记录警告级别的错误
     */
    fun warn(e: Throwable, context: String = "") {
        handle(e, context, silent = true)
    }
    
    /**
     * 记录调试级别的错误
     */
    fun debug(e: Throwable, context: String = "") {
        val tag = if (context.isNotEmpty()) "$TAG.$context" else TAG
        Log.d(tag, buildErrorMessage(e))
    }
    
    /**
     * 安全执行代码块
     * 
     * @param context 上下文描述
     * @param fallback 发生异常时的回退值
     * @param block 要执行的代码块
     * @return 执行结果或回退值
     */
    inline fun <T> runSafely(
        context: String = "",
        fallback: T? = null,
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (e: Exception) {
            handle(e, context)
            fallback
        }
    }
    
    /**
     * 安全执行代码块（非空版本）
     */
    inline fun <T : Any> runSafelyNonNull(
        context: String = "",
        fallback: T,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            handle(e, context)
            fallback
        }
    }
    
    /**
     * 安全执行挂起函数
     */
    suspend inline fun <T> runSafelySuspend(
        context: String = "",
        crossinline block: suspend () -> T
    ): Result<T> {
        return runCatching { block() }
            .onFailure { handle(it, context) }
    }
    
    /**
     * 安全执行挂起函数并返回默认值
     */
    suspend inline fun <T> runSafelySuspendWithDefault(
        context: String = "",
        default: T,
        crossinline block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            handle(e, context)
            default
        }
    }
    
    /**
     * 忽略异常（仅记录调试日志）
     * 用于确实需要忽略的场景，如资源清理
     */
    inline fun <T> ignoring(
        context: String = "",
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (e: Exception) {
            debug(e, context)
            null
        }
    }
    
    /**
     * 安全关闭资源
     */
    fun closeQuietly(closeable: AutoCloseable?, context: String = "") {
        try {
            closeable?.close()
        } catch (e: Exception) {
            debug(e, "$context.close")
        }
    }
    
    /**
     * 安全关闭多个资源
     */
    fun closeAllQuietly(vararg closeables: AutoCloseable?, context: String = "") {
        closeables.forEach { closeable ->
            closeQuietly(closeable, context)
        }
    }
    
    /**
     * 构建错误消息
     */
    private fun buildErrorMessage(e: Throwable): String {
        return when (e) {
            is OmniException -> "${e.errorCode}: ${e.message}"
            else -> "${e.javaClass.simpleName}: ${e.message}"
        }
    }
    
    /**
     * 将异常转换为用户友好的消息
     */
    fun toUserMessage(e: Throwable): String {
        return when (e) {
            is OmniException -> when (e.errorCode) {
                ErrorCode.NETWORK_TIMEOUT -> "网络连接超时，请检查网络后重试"
                ErrorCode.CONNECTION_FAILED -> "连接失败，请检查网络设置"
                ErrorCode.AUTHENTICATION_FAILED -> "认证失败，请检查账户信息"
                ErrorCode.FILE_NOT_FOUND -> "文件不存在"
                ErrorCode.FILE_ACCESS_DENIED -> "没有访问权限"
                ErrorCode.FILE_TOO_LARGE -> "文件过大"
                ErrorCode.PLUGIN_NOT_FOUND -> "插件不存在"
                ErrorCode.PLUGIN_LOAD_FAILED -> "插件加载失败"
                else -> e.message ?: "发生错误"
            }
            is java.net.SocketTimeoutException -> "网络连接超时"
            is java.net.UnknownHostException -> "无法连接到服务器"
            is java.io.FileNotFoundException -> "文件不存在"
            is SecurityException -> "没有访问权限"
            is OutOfMemoryError -> "内存不足"
            else -> e.message ?: "发生未知错误"
        }
    }
}
