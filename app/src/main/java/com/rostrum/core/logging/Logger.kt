package com.rostrum.core.logging

import android.util.Log

/**
 * 统一日志系统 - 解决"日志系统混乱"问题
 * 自动在Release关闭Debug日志
 */
object Logger {
    // 通过系统属性判断是否为Debug模式
    private val isDebug: Boolean = android.os.Debug.isDebuggerConnected() || 
        System.getProperty("debug.mode") == "true"
    
    fun d(tag: String, message: String) {
        if (isDebug) Log.d(tag, message)
    }
    
    fun i(tag: String, message: String) {
        if (isDebug) Log.i(tag, message)
    }
    
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (isDebug) Log.w(tag, message, throwable)
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        // Error日志在Release也记录
        Log.e(tag, message, throwable)
    }
    
    fun perf(operation: String, durationMs: Long) {
        if (isDebug) Log.d("Perf", "$operation: ${durationMs}ms")
    }
}
