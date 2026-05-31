package com.rostrum.core.runtime

import com.rostrum.core.domain.model.FileItem

/**
 * 文件执行服务接口
 * 
 * 根据文件类型选择合适的执行器执行文件。
 * 支持注册自定义执行器以扩展支持的文件类型。
 */
interface FileExecutionService {
    /**
     * 执行文件
     * 
     * @param file 要执行的文件
     * @param context 执行上下文
     * @return 执行结果
     */
    suspend fun executeFile(
        file: FileItem,
        context: FileExecutionContext = FileExecutionContext()
    ): FileExecutionResult
    
    /**
     * 检查文件是否可执行
     * 
     * @param file 文件项
     * @return true 如果有对应的执行器可以处理该文件
     */
    suspend fun canExecute(file: FileItem): Boolean
    
    /**
     * 获取文件对应的执行器
     * 
     * @param extension 文件扩展名（不含点号，如 "py"）
     * @return 对应的执行器，如果没有则返回 null
     */
    fun getExecutor(extension: String): FileExecutor?
    
    /**
     * 注册文件执行器
     * 
     * @param executor 文件执行器
     */
    fun registerExecutor(executor: FileExecutor)
    
    /**
     * 注销文件执行器
     * 
     * @param executor 文件执行器
     */
    fun unregisterExecutor(executor: FileExecutor)
    
    /**
     * 获取所有支持的文件扩展名
     * 
     * @return 扩展名列表
     */
    fun getSupportedExtensions(): List<String>
}

/**
 * 文件执行器接口
 * 
 * 实现此接口以支持执行特定类型的文件
 */
interface FileExecutor {
    /**
     * 支持的文件扩展名列表（不含点号，如 "py", "pyw"）
     */
    val supportedExtensions: List<String>
    
    /**
     * 执行器名称
     */
    val name: String
    
    /**
     * 执行器描述
     */
    val description: String
    
    /**
     * 检查执行器是否可用
     * 
     * @return true 如果执行器可用
     */
    fun isAvailable(): Boolean
    
    /**
     * 执行文件
     * 
     * @param file 要执行的文件
     * @param context 执行上下文
     * @return 执行结果
     */
    suspend fun execute(
        file: FileItem,
        context: FileExecutionContext
    ): FileExecutionResult
}

/**
 * 文件执行上下文
 * 
 * 配置文件执行的环境和参数
 */
data class FileExecutionContext(
    /**
     * 工作目录
     */
    val workingDirectory: String? = null,
    
    /**
     * 命令行参数
     */
    val arguments: List<String> = emptyList(),
    
    /**
     * 额外的环境变量
     */
    val environment: Map<String, String> = emptyMap(),
    
    /**
     * 执行超时时间（毫秒）
     */
    val timeout: Long = 60000,
    
    /**
     * 标准输入内容
     */
    val stdin: String? = null
)

/**
 * 文件执行结果
 */
data class FileExecutionResult(
    /**
     * 执行是否成功
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
    val timedOut: Boolean = false,
    
    /**
     * 使用的执行器名称
     */
    val executorName: String? = null
) {
    companion object {
        /**
         * 创建成功结果
         */
        fun success(
            output: String? = null,
            executionTime: Long = 0,
            executorName: String? = null
        ) = FileExecutionResult(
            success = true,
            output = output,
            exitCode = 0,
            executionTime = executionTime,
            executorName = executorName
        )
        
        /**
         * 创建失败结果
         */
        fun failure(
            error: String,
            exitCode: Int = 1,
            executionTime: Long = 0,
            executorName: String? = null
        ) = FileExecutionResult(
            success = false,
            error = error,
            exitCode = exitCode,
            executionTime = executionTime,
            executorName = executorName
        )
        
        /**
         * 创建不支持的文件类型结果
         */
        fun unsupported(extension: String) = FileExecutionResult(
            success = false,
            error = "No executor available for file type: .$extension",
            exitCode = -1
        )
    }
}
