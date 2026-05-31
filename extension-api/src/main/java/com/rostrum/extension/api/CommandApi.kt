package com.rostrum.extension.api

/**
 * 命令 API
 * 
 * 提供命令注册和执行功能
 */
interface CommandApi {
    /**
     * 注册命令
     * 
     * @param command 命令定义
     * @return Disposable，用于取消注册
     */
    fun registerCommand(command: Command): Disposable
    
    /**
     * 执行命令
     * 
     * @param commandId 命令 ID
     * @param args 命令参数
     * @return 执行结果，如果命令不存在返回 null
     */
    suspend fun executeCommand(commandId: String, vararg args: Any?): Any?
    
    /**
     * 获取所有已注册的命令 ID
     * 
     * @return 命令 ID 列表
     */
    fun getCommands(): List<String>
}

/**
 * 命令定义
 */
data class Command(
    /**
     * 命令唯一标识符
     */
    val id: String,
    
    /**
     * 命令显示标题
     */
    val title: String,
    
    /**
     * 命令处理器
     * 
     * @param args 命令参数
     * @return 执行结果
     */
    val handler: suspend (List<Any?>) -> Any?,
    
    /**
     * 命令图标（可选）
     */
    val icon: String? = null,
    
    /**
     * 命令分类（可选）
     */
    val category: String? = null,
    
    /**
     * 命令描述（可选）
     */
    val description: String? = null
)

