package com.rostrum.core.runtime.impl

import android.util.Log
import com.rostrum.core.runtime.FileExecutionContext
import com.rostrum.core.runtime.FileExecutionResult
import com.rostrum.core.runtime.FileExecutor
import com.rostrum.core.runtime.PythonExecutionContext
import com.rostrum.core.runtime.PythonExecutionService
import com.rostrum.core.domain.model.FileItem

/**
 * Python 文件执行器
 * 
 * 使用 PythonExecutionService 执行 Python 文件（.py, .pyw）
 */
class PythonFileExecutor(
    private val pythonExecutionService: PythonExecutionService
) : FileExecutor {
    
    companion object {
        private const val TAG = "PythonFileExecutor"
    }
    
    override val supportedExtensions = listOf("py", "pyw")
    
    override val name = "Python"
    
    override val description = "Python 文件执行器，支持执行 .py 和 .pyw 文件"
    
    override fun isAvailable(): Boolean {
        return pythonExecutionService.isAvailable()
    }
    
    override suspend fun execute(
        file: FileItem,
        context: FileExecutionContext
    ): FileExecutionResult {
        val startTime = System.currentTimeMillis()
        
        Log.d(TAG, "Executing Python file: ${file.path}")
        
        // 检查文件是否存在
        val actualFile = file.file ?: return FileExecutionResult.failure(
            error = "File object is null: ${file.path}",
            executionTime = 0
        )
        
        // 转换上下文
        val pythonContext = PythonExecutionContext(
            workingDirectory = context.workingDirectory ?: actualFile.parentFile?.absolutePath,
            environment = context.environment,
            timeout = context.timeout,
            arguments = context.arguments,
            stdin = context.stdin
        )
        
        // 调用 Python 执行服务
        val result = pythonExecutionService.executeFile(actualFile, pythonContext)
        
        // 转换结果
        return FileExecutionResult(
            success = result.success,
            output = result.output,
            error = result.error,
            exitCode = result.exitCode,
            executionTime = result.executionTime,
            timedOut = result.timedOut,
            executorName = name
        )
    }
    
    /**
     * 获取 Python 版本
     */
    fun getPythonVersion(): String {
        return pythonExecutionService.getVersion()
    }
}
