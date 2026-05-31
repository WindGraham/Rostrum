package com.rostrum.core.plugin.providers

import com.rostrum.core.mcp.MCPTool
import com.rostrum.core.mcp.ToolCategory
import com.rostrum.core.plugin.Plugin

/**
 * 工具插件接口
 * 
 * 实现此接口的插件可以提供工具功能，并自动注册为MCP工具供AI调用
 * 
 * 设计目标：
 * - 松耦合：工具插件与文件浏览框架解耦，通过MCP协议暴露功能
 * - 高内聚：每个工具插件专注于特定工具功能
 * - 易扩展：添加新工具只需实现此接口并注册
 */
interface ToolPlugin : Plugin {
    /**
     * 工具类别
     * 用于MCP工具分类和权限控制
     */
    val toolCategory: ToolCategory
    
    /**
     * 获取MCP工具定义列表
     * 
     * 插件可以实现多个工具，每个工具对应一个MCP工具定义
     * 
     * @return MCP工具列表
     */
    fun getMCPTools(): List<MCPTool>
    
    /**
     * 工具插件初始化
     * 
     * 在插件激活时调用，用于准备工具资源
     * 
     * @return 初始化结果
     */
    suspend fun initializeTools(): Result<Unit> = Result.success(Unit)
    
    /**
     * 工具插件清理
     * 
     * 在插件停用时调用，用于清理工具资源
     * 
     * @return 清理结果
     */
    suspend fun cleanupTools(): Result<Unit> = Result.success(Unit)
}

