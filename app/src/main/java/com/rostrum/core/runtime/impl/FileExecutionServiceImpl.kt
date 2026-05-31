package com.rostrum.core.runtime.impl

import android.util.Log
import com.rostrum.core.runtime.FileExecutionContext
import com.rostrum.core.runtime.FileExecutionResult
import com.rostrum.core.runtime.FileExecutionService
import com.rostrum.core.runtime.FileExecutor
import com.rostrum.core.domain.model.FileItem
import java.util.concurrent.ConcurrentHashMap

/**
 * 文件执行服务实现
 * 
 * 管理文件执行器注册表，根据文件扩展名选择合适的执行器执行文件。
 */
class FileExecutionServiceImpl : FileExecutionService {
    
    companion object {
        private const val TAG = "FileExecutionService"
    }
    
    // 扩展名到执行器的映射（线程安全）
    private val executors = ConcurrentHashMap<String, FileExecutor>()
    
    // 注册的执行器列表（用于管理和注销）
    private val registeredExecutors = mutableSetOf<FileExecutor>()
    
    override suspend fun executeFile(
        file: FileItem,
        context: FileExecutionContext
    ): FileExecutionResult {
        val startTime = System.currentTimeMillis()
        
        // 检查文件是否存在
        val actualFile = file.file
        if (actualFile == null || !actualFile.exists()) {
            return FileExecutionResult.failure(
                error = "File not found: ${file.path}",
                executionTime = System.currentTimeMillis() - startTime
            )
        }
        
        // 检查是否是目录
        if (file.isDirectory) {
            return FileExecutionResult.failure(
                error = "Cannot execute a directory: ${file.path}",
                executionTime = System.currentTimeMillis() - startTime
            )
        }
        
        // 获取扩展名
        val extension = file.extension.lowercase()
        
        // 查找执行器
        val executor = executors[extension]
        if (executor == null) {
            Log.w(TAG, "No executor found for extension: $extension")
            return FileExecutionResult.unsupported(extension)
        }
        
        // 检查执行器是否可用
        if (!executor.isAvailable()) {
            return FileExecutionResult.failure(
                error = "Executor '${executor.name}' is not available",
                executorName = executor.name,
                executionTime = System.currentTimeMillis() - startTime
            )
        }
        
        Log.d(TAG, "Executing file ${file.name} with executor: ${executor.name}")
        
        return try {
            executor.execute(file, context)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing file ${file.path}", e)
            FileExecutionResult.failure(
                error = e.message ?: "Unknown error",
                executorName = executor.name,
                executionTime = System.currentTimeMillis() - startTime
            )
        }
    }
    
    override suspend fun canExecute(file: FileItem): Boolean {
        if (file.isDirectory) return false
        
        val extension = file.extension.lowercase()
        val executor = executors[extension]
        
        return executor != null && executor.isAvailable()
    }
    
    override fun getExecutor(extension: String): FileExecutor? {
        return executors[extension.lowercase()]
    }
    
    override fun registerExecutor(executor: FileExecutor) {
        synchronized(registeredExecutors) {
            registeredExecutors.add(executor)
            
            for (ext in executor.supportedExtensions) {
                val normalizedExt = ext.lowercase()
                val existing = executors[normalizedExt]
                
                if (existing != null) {
                    Log.w(TAG, "Overwriting executor for .$normalizedExt: " +
                            "${existing.name} -> ${executor.name}")
                }
                
                executors[normalizedExt] = executor
                Log.d(TAG, "Registered executor '${executor.name}' for .$normalizedExt")
            }
        }
    }
    
    override fun unregisterExecutor(executor: FileExecutor) {
        synchronized(registeredExecutors) {
            if (registeredExecutors.remove(executor)) {
                for (ext in executor.supportedExtensions) {
                    val normalizedExt = ext.lowercase()
                    // 只移除属于该执行器的映射
                    if (executors[normalizedExt] == executor) {
                        executors.remove(normalizedExt)
                        Log.d(TAG, "Unregistered executor '${executor.name}' for .$normalizedExt")
                    }
                }
            }
        }
    }
    
    override fun getSupportedExtensions(): List<String> {
        return executors.keys.toList().sorted()
    }
    
    /**
     * 获取所有注册的执行器
     */
    fun getRegisteredExecutors(): List<FileExecutor> {
        return registeredExecutors.toList()
    }
    
    /**
     * 清除所有执行器
     */
    fun clearExecutors() {
        synchronized(registeredExecutors) {
            executors.clear()
            registeredExecutors.clear()
            Log.d(TAG, "All executors cleared")
        }
    }
}
