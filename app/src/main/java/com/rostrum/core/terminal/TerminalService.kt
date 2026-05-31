package com.rostrum.core.terminal

import kotlinx.coroutines.flow.Flow

/**
 * 终端服务接口
 */
interface TerminalService {
    /**
     * 创建终端会话
     * 
     * @param shell Shell路径，默认为 "/system/bin/sh"
     * @param workingDirectory 工作目录，默认为 "/"
     * @param environment 环境变量
     * @return 终端会话
     */
    suspend fun createSession(
        shell: String = "/system/bin/sh",
        workingDirectory: String = "/",
        environment: Map<String, String> = emptyMap()
    ): Result<TerminalSession>
    
    /**
     * 获取会话
     * 
     * @param sessionId 会话ID
     * @return 终端会话，如果不存在返回null
     */
    suspend fun getSession(sessionId: String): TerminalSession?
    
    /**
     * 关闭会话
     * 
     * @param sessionId 会话ID
     */
    suspend fun closeSession(sessionId: String)
    
    /**
     * 列出所有会话
     * 
     * @return 会话列表
     */
    suspend fun listSessions(): List<TerminalSession>
    
    /**
     * 检查命令是否存在
     * 
     * @param command 命令名称
     * @return 是否存在
     */
    suspend fun hasCommand(command: String): Boolean
    
    /**
     * 获取可用命令列表
     * 
     * @return 命令列表
     */
    suspend fun getAvailableCommands(): List<String>
}

/**
 * 终端会话
 */
interface TerminalSession {
    /**
     * 会话ID
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

