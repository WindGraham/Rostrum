package com.rostrum.extension.api

import kotlinx.coroutines.flow.Flow

/**
 * 终端 API
 * 
 * 提供终端命令执行功能
 */
interface TerminalApi {
    /**
     * 创建终端会话
     * 
     * @param shell Shell 路径，默认为 "/system/bin/sh"
     * @param workingDirectory 工作目录，默认为 "/"
     * @param environment 环境变量
     * @return 终端会话，失败返回 Result.failure
     */
    suspend fun createSession(
        shell: String = "/system/bin/sh",
        workingDirectory: String = "/",
        environment: Map<String, String> = emptyMap()
    ): Result<TerminalSession>
    
    /**
     * 获取终端会话
     * 
     * @param sessionId 会话 ID
     * @return 终端会话，如果不存在返回 null
     */
    suspend fun getSession(sessionId: String): TerminalSession?
    
    /**
     * 关闭终端会话
     * 
     * @param sessionId 会话 ID
     */
    suspend fun closeSession(sessionId: String)
    
    /**
     * 执行命令（同步）
     * 
     * @param command 命令
     * @param workingDirectory 工作目录
     * @return 执行结果
     */
    suspend fun executeCommand(
        command: String,
        workingDirectory: String? = null
    ): Result<ExecutionResult>
}

/**
 * 终端会话
 */
interface TerminalSession {
    /**
     * 会话 ID
     */
    val id: String
    
    /**
     * 会话标题
     */
    val title: String
    
    /**
     * 是否正在运行
     */
    val isRunning: Boolean
    
    /**
     * 执行命令（同步）
     * 
     * @param command 命令
     * @return 执行结果
     */
    suspend fun execute(command: String): ExecutionResult
    
    /**
     * 执行交互式命令（流式输出）
     * 
     * @param command 命令
     * @return 输出流
     */
    suspend fun executeInteractive(command: String): Flow<String>
    
    /**
     * 写入输入
     * 
     * @param input 输入内容
     */
    fun writeInput(input: String)
    
    /**
     * 调整终端大小
     * 
     * @param columns 列数
     * @param rows 行数
     */
    fun resize(columns: Int, rows: Int)
    
    /**
     * 终止会话
     */
    fun terminate()
    
    /**
     * 输出流
     */
    val outputFlow: Flow<String>
    
    /**
     * 错误流
     */
    val errorFlow: Flow<String>
    
    /**
     * 退出流
     */
    val exitFlow: Flow<Int>
}

/**
 * 执行结果
 */
data class ExecutionResult(
    val exitCode: Int,
    val output: String,
    val error: String,
    val executionTime: Long
)

