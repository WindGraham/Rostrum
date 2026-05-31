package com.rostrum.core.performance

import android.util.Log

/**
 * 性能监控器 - 解决"性能监控缺失"问题
 */
object PerformanceMonitor {
    private const val TAG = "PerfMonitor"
    
    private val traces = mutableMapOf<String, Long>()
    
    fun startTrace(name: String) {
        traces[name] = System.currentTimeMillis()
        Log.d(TAG, "Started trace: $name")
    }
    
    fun stopTrace(name: String) {
        traces[name]?.let { startTime ->
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "Trace $name: ${duration}ms")
            traces.remove(name)
        }
    }
    
    fun recordMetric(name: String, value: Long) {
        Log.d(TAG, "Metric $name: $value")
    }
    
    inline fun <T> trace(name: String, block: () -> T): T {
        startTrace(name)
        return try {
            block()
        } finally {
            stopTrace(name)
        }
    }
}
