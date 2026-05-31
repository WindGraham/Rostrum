package com.rostrum.core.shell

import kotlinx.coroutines.flow.StateFlow

/**
 * Shell Session接口
 * 
 * 统一的Shell会话接口，支持不同的实现（内置Shell、插件Shell等）
 */
interface IShellSession {
    /**
     * 终端输出流
     */
    val output: StateFlow<String>
    
    /**
     * 命令历史
     */
    val commandHistory: List<String>
    
    /**
     * 启动Shell会话
     */
    fun startSession()
    
    /**
     * 发送命令
     */
    fun sendCommand(command: String)
    
    /**
     * 关闭Shell会话
     */
    fun close()
    
    /**
     * 清空输出
     */
    fun clearOutput()
    
    /**
     * 追加文本到终端输出（供外部工具调用）
     * 支持各类编程语言执行工具（Python、C、Java等）直接输出到终端
     * 
     * @param text 要追加的文本内容
     */
    fun appendToOutput(text: String)
    
    /**
     * 检查会话是否已就绪
     * 用于替代固定的 delay 等待
     * 
     * @return true 如果会话已初始化完成并可以接收命令
     */
    fun isReady(): Boolean
}
