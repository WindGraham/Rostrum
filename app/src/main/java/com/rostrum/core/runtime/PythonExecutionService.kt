package com.rostrum.core.runtime

import java.io.File

/**
 * Python 执行服务接口
 * 
 * 供本地脚本执行和文件运行功能使用。
 * 提供直接执行 Python 代码和文件的能力，返回结构化结果。
 */
interface PythonExecutionService {
    /**
     * 执行 Python 代码片段
     * 
     * @param code Python 代码字符串
     * @param context 执行上下文
     * @return 执行结果
     */
    suspend fun executeCode(
        code: String,
        context: PythonExecutionContext = PythonExecutionContext()
    ): PythonExecutionResult
    
    /**
     * 执行 Python 文件
     * 
     * @param file .py 文件
     * @param context 执行上下文
     * @return 执行结果
     */
    suspend fun executeFile(
        file: File,
        context: PythonExecutionContext = PythonExecutionContext()
    ): PythonExecutionResult
    
    /**
     * 检查 Python 运行时是否可用
     * 
     * @return true 如果 Python 可用
     */
    fun isAvailable(): Boolean
    
    /**
     * 获取 Python 版本
     * 
     * @return Python 版本字符串，如 "Python 3.14.2"
     */
    fun getVersion(): String
    
    /**
     * 初始化 Python 运行时
     * 
     * @return true 如果初始化成功
     */
    suspend fun initialize(): Boolean
}

/**
 * Python 执行上下文
 * 
 * 配置 Python 代码执行的环境和参数
 */
data class PythonExecutionContext(
    /**
     * 工作目录，Python 代码执行时的当前目录
     */
    val workingDirectory: String? = null,
    
    /**
     * 额外的环境变量
     */
    val environment: Map<String, String> = emptyMap(),
    
    /**
     * 执行超时时间（毫秒）
     */
    val timeout: Long = 30000,
    
    /**
     * 命令行参数（用于执行文件时）
     */
    val arguments: List<String> = emptyList(),
    
    /**
     * 标准输入内容
     */
    val stdin: String? = null
)

/**
 * Python 执行结果
 * 
 * 包含执行的输出、错误信息和状态
 */
data class PythonExecutionResult(
    /**
     * 执行是否成功（exitCode == 0）
     */
    val success: Boolean,
    
    /**
     * 标准输出内容
     */
    val output: String? = null,
    
    /**
     * 标准错误输出内容
     */
    val error: String? = null,
    
    /**
     * 进程退出码
     */
    val exitCode: Int = 0,
    
    /**
     * 执行耗时（毫秒）
     */
    val executionTime: Long = 0,
    
    /**
     * 是否因超时被终止
     */
    val timedOut: Boolean = false
) {
    companion object {
        /**
         * 创建成功结果
         */
        fun success(
            output: String? = null,
            executionTime: Long = 0
        ) = PythonExecutionResult(
            success = true,
            output = output,
            exitCode = 0,
            executionTime = executionTime
        )
        
        /**
         * 创建失败结果
         */
        fun failure(
            error: String,
            exitCode: Int = 1,
            executionTime: Long = 0
        ) = PythonExecutionResult(
            success = false,
            error = error,
            exitCode = exitCode,
            executionTime = executionTime
        )
        
        /**
         * 创建超时结果
         */
        fun timeout(executionTime: Long) = PythonExecutionResult(
            success = false,
            error = "Execution timed out",
            exitCode = -1,
            executionTime = executionTime,
            timedOut = true
        )
    }
}
