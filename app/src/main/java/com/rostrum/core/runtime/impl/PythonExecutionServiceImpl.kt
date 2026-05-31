package com.rostrum.core.runtime.impl

import android.util.Log
import com.rostrum.core.runtime.PythonExecutionContext
import com.rostrum.core.runtime.PythonExecutionResult
import com.rostrum.core.runtime.PythonExecutionService
import com.rostrum.core.shell.python.PythonRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Python 执行服务实现
 * 
 * 封装 PythonRuntime，提供代码和文件执行功能。
 * 支持超时控制、环境变量配置和标准输入输出捕获。
 */
class PythonExecutionServiceImpl(
    private val pythonRuntime: PythonRuntime
) : PythonExecutionService {
    
    companion object {
        private const val TAG = "PythonExecutionService"
        private const val MAX_OUTPUT_SIZE = 1024 * 1024 // 1MB
    }
    
    override suspend fun executeCode(
        code: String,
        context: PythonExecutionContext
    ): PythonExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            // 确保运行时已初始化
            if (!ensureInitialized()) {
                return@withContext PythonExecutionResult.failure(
                    error = "Python runtime not available",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }
            
            val pythonBinary = pythonRuntime.getPythonBinary()
            val env = buildEnvironment(context)
            
            // 构建命令：python -c "code"
            val command = mutableListOf(
                pythonBinary.absolutePath,
                "-c",
                code
            )
            
            Log.d(TAG, "Executing Python code: ${code.take(100)}...")
            
            executeProcess(command, env, context, startTime)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Python code", e)
            PythonExecutionResult.failure(
                error = e.message ?: "Unknown error",
                executionTime = System.currentTimeMillis() - startTime
            )
        }
    }
    
    override suspend fun executeFile(
        file: File,
        context: PythonExecutionContext
    ): PythonExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            // 检查文件是否存在
            if (!file.exists()) {
                return@withContext PythonExecutionResult.failure(
                    error = "File not found: ${file.absolutePath}",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }
            
            // 检查文件扩展名
            if (!file.name.endsWith(".py") && !file.name.endsWith(".pyw")) {
                return@withContext PythonExecutionResult.failure(
                    error = "Not a Python file: ${file.name}",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }
            
            // 确保运行时已初始化
            if (!ensureInitialized()) {
                return@withContext PythonExecutionResult.failure(
                    error = "Python runtime not available",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }
            
            val pythonBinary = pythonRuntime.getPythonBinary()
            val env = buildEnvironment(context)
            
            // 构建命令：python file.py [arguments]
            val command = mutableListOf(
                pythonBinary.absolutePath,
                file.absolutePath
            )
            command.addAll(context.arguments)
            
            Log.d(TAG, "Executing Python file: ${file.absolutePath}")
            
            // 如果未指定工作目录，使用文件所在目录
            val effectiveContext = if (context.workingDirectory == null) {
                context.copy(workingDirectory = file.parentFile?.absolutePath)
            } else {
                context
            }
            
            executeProcess(command, env, effectiveContext, startTime)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Python file", e)
            PythonExecutionResult.failure(
                error = e.message ?: "Unknown error",
                executionTime = System.currentTimeMillis() - startTime
            )
        }
    }
    
    override fun isAvailable(): Boolean {
        return pythonRuntime.isAvailable()
    }
    
    override fun getVersion(): String {
        return pythonRuntime.getVersion()
    }
    
    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        pythonRuntime.initialize()
    }
    
    /**
     * 确保 Python 运行时已初始化
     */
    private fun ensureInitialized(): Boolean {
        if (!pythonRuntime.isAvailable()) {
            Log.d(TAG, "Python runtime not available, attempting initialization...")
            return pythonRuntime.initialize()
        }
        return true
    }
    
    /**
     * 构建执行环境变量
     */
    private fun buildEnvironment(context: PythonExecutionContext): Map<String, String> {
        val env = pythonRuntime.getEnvironment().toMutableMap()
        
        // 添加用户指定的环境变量
        env.putAll(context.environment)
        
        // 设置工作目录相关环境变量
        context.workingDirectory?.let { workDir ->
            env["PWD"] = workDir
        }
        
        return env
    }
    
    /**
     * 执行进程并捕获输出
     */
    private suspend fun executeProcess(
        command: List<String>,
        env: Map<String, String>,
        context: PythonExecutionContext,
        startTime: Long
    ): PythonExecutionResult {
        val processBuilder = ProcessBuilder(command).apply {
            environment().clear()
            environment().putAll(env)
            
            context.workingDirectory?.let { workDir ->
                directory(File(workDir))
            }
            
            // 分离标准输出和错误输出
            redirectErrorStream(false)
        }
        
        val process = processBuilder.start()
        
        try {
            // 处理标准输入
            context.stdin?.let { input ->
                process.outputStream.bufferedWriter().use { writer ->
                    writer.write(input)
                    writer.flush()
                }
            }
            process.outputStream.close()
            
            // 使用超时等待进程完成
            val result = withTimeoutOrNull(context.timeout) {
                // 异步读取输出
                val outputBuilder = StringBuilder()
                val errorBuilder = StringBuilder()
                
                val outputReader = process.inputStream.bufferedReader()
                val errorReader = process.errorStream.bufferedReader()
                
                // 读取标准输出
                var outputLine = outputReader.readLine()
                while (outputLine != null && outputBuilder.length < MAX_OUTPUT_SIZE) {
                    outputBuilder.appendLine(outputLine)
                    outputLine = outputReader.readLine()
                }
                
                // 读取错误输出
                var errorLine = errorReader.readLine()
                while (errorLine != null && errorBuilder.length < MAX_OUTPUT_SIZE) {
                    errorBuilder.appendLine(errorLine)
                    errorLine = errorReader.readLine()
                }
                
                // 等待进程结束
                process.waitFor()
                
                val exitCode = process.exitValue()
                val output = outputBuilder.toString().trimEnd()
                val error = errorBuilder.toString().trimEnd()
                val executionTime = System.currentTimeMillis() - startTime
                
                PythonExecutionResult(
                    success = exitCode == 0,
                    output = output.ifEmpty { null },
                    error = error.ifEmpty { null },
                    exitCode = exitCode,
                    executionTime = executionTime
                )
            }
            
            return if (result != null) {
                result
            } else {
                // 超时，强制终止进程
                process.destroyForcibly()
                process.waitFor(1, TimeUnit.SECONDS)
                PythonExecutionResult.timeout(System.currentTimeMillis() - startTime)
            }
        } finally {
            // 确保进程被正确清理
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }
}
