package com.rostrum.core.plugin.ipc.retry

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retry

/**
 * 重试策略
 */
interface RetryPolicy {
    /**
     * 是否应该重试
     * 
     * @param attempt 当前尝试次数（从 1 开始）
     * @param error 错误信息
     * @return 是否应该重试
     */
    fun shouldRetry(attempt: Int, error: Throwable): Boolean
    
    /**
     * 获取重试延迟（毫秒）
     * 
     * @param attempt 当前尝试次数（从 1 开始）
     * @return 延迟时间（毫秒）
     */
    fun getRetryDelay(attempt: Int): Long
    
    /**
     * 最大重试次数
     */
    val maxRetries: Int
}

/**
 * 指数退避重试策略
 */
class ExponentialBackoffRetryPolicy(
    override val maxRetries: Int = 3,
    private val initialDelay: Long = 1000L,  // 1 秒
    private val maxDelay: Long = 30000L,     // 30 秒
    private val multiplier: Double = 2.0
) : RetryPolicy {
    
    override fun shouldRetry(attempt: Int, error: Throwable): Boolean {
        if (attempt > maxRetries) {
            return false
        }
        
        // 不重试某些错误类型
        return when (error) {
            is IllegalArgumentException,
            is IllegalStateException,
            is SecurityException -> false
            else -> true
        }
    }
    
    override fun getRetryDelay(attempt: Int): Long {
        val delay = (initialDelay * Math.pow(multiplier, (attempt - 1).toDouble())).toLong()
        return delay.coerceAtMost(maxDelay)
    }
}

/**
 * 固定延迟重试策略
 */
class FixedDelayRetryPolicy(
    override val maxRetries: Int = 3,
    private val delay: Long = 1000L
) : RetryPolicy {
    
    override fun shouldRetry(attempt: Int, error: Throwable): Boolean {
        return attempt <= maxRetries
    }
    
    override fun getRetryDelay(attempt: Int): Long {
        return delay
    }
}

/**
 * 重试工具函数
 */
object RetryUtils {
    private const val TAG = "RetryUtils"
    
    /**
     * 执行带重试的操作
     */
    suspend fun <T> executeWithRetry(
        policy: RetryPolicy = ExponentialBackoffRetryPolicy(),
        operation: suspend () -> T
    ): Result<T> {
        var lastError: Throwable? = null
        
        for (attempt in 1..policy.maxRetries) {
            try {
                val result = operation()
                return Result.success(result)
            } catch (e: Throwable) {
                lastError = e
                
                if (!policy.shouldRetry(attempt, e)) {
                    Log.w(TAG, "Retry not allowed for error: ${e.message}")
                    break
                }
                
                if (attempt < policy.maxRetries) {
                    val delay = policy.getRetryDelay(attempt)
                    Log.d(TAG, "Retry attempt $attempt after ${delay}ms: ${e.message}")
                    delay(delay)
                }
            }
        }
        
        return Result.failure(lastError ?: IllegalStateException("Unknown error"))
    }
    
    /**
     * 执行带超时的操作
     */
    suspend fun <T> executeWithTimeout(
        timeoutMs: Long = 30000L,  // 30 秒
        operation: suspend () -> T
    ): Result<T> {
        return try {
            val result = kotlinx.coroutines.withTimeout(timeoutMs) {
                operation()
            }
            Result.success(result)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Operation timed out after ${timeoutMs}ms")
            Result.failure(e)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
    
    /**
     * 执行带重试和超时的操作
     */
    suspend fun <T> executeWithRetryAndTimeout(
        policy: RetryPolicy = ExponentialBackoffRetryPolicy(),
        timeoutMs: Long = 30000L,
        operation: suspend () -> T
    ): Result<T> {
        return executeWithRetry(policy) {
            executeWithTimeout(timeoutMs, operation).getOrThrow()
        }
    }
}

