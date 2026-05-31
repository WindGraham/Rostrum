package com.rostrum.core.mcp.impl

import android.util.Log
import com.rostrum.core.mcp.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * MCP协议实现
 * 
 * 管理所有注册的MCP工具，提供工具注册、注销、调用等功能
 */
class MCPProtocolImpl : MCPProtocol {
    private val TAG = "MCPProtocolImpl"
    
    // 工具注册表 (toolName -> MCPTool)
    private val tools = mutableMapOf<String, MCPTool>()
    
    // 线程安全锁
    private val mutex = Mutex()
    
    override fun registerTool(tool: MCPTool) {
        synchronized(tools) {
            if (tools.containsKey(tool.name)) {
                Log.w(TAG, "Tool '${tool.name}' already registered, replacing...")
            }
            tools[tool.name] = tool
            Log.d(TAG, "Registered MCP tool: ${tool.name} (category: ${tool.category})")
        }
    }
    
    override fun unregisterTool(toolName: String) {
        synchronized(tools) {
            val removed = tools.remove(toolName)
            if (removed != null) {
                Log.d(TAG, "Unregistered MCP tool: $toolName")
            } else {
                Log.w(TAG, "Tool '$toolName' not found, cannot unregister")
            }
        }
    }
    
    override fun getAvailableTools(): List<MCPTool> {
        return synchronized(tools) {
            tools.values.toList()
        }
    }
    
    override suspend fun callTool(
        toolName: String,
        arguments: Map<String, Any>,
        context: MCPContext
    ): MCPResult {
        return mutex.withLock {
            val tool = tools[toolName]
            if (tool == null) {
                return@withLock MCPResult(
                    success = false,
                    error = MCPError(
                        code = "TOOL_NOT_FOUND",
                        message = "Tool '$toolName' is not registered"
                    )
                )
            }
            
            // 检查权限
            val hasPermission = tool.permissions.all { permission ->
                context.permissions.contains(permission.name)
            }
            if (!hasPermission) {
                return@withLock MCPResult(
                    success = false,
                    error = MCPError(
                        code = "PERMISSION_DENIED",
                        message = "Insufficient permissions to call tool '$toolName'"
                    )
                )
            }
            
            // 调用工具处理器
            return@withLock try {
                tool.handler(arguments, context)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling tool '$toolName'", e)
                MCPResult(
                    success = false,
                    error = MCPError(
                        code = "TOOL_EXECUTION_ERROR",
                        message = "Tool execution failed: ${e.message}",
                        details = mapOf("exception" to e.javaClass.simpleName)
                    )
                )
            }
        }
    }
    
    override suspend fun discoverTools(scope: MCPScope): List<MCPTool> {
        return mutex.withLock {
            when (scope) {
                MCPScope.ALL -> tools.values.toList()
                MCPScope.PLUGIN -> tools.values.filter { it.category == ToolCategory.PLUGIN }
                MCPScope.SYSTEM -> tools.values.filter { it.category == ToolCategory.SYSTEM }
                MCPScope.USER -> tools.values.filter { it.category !in listOf(ToolCategory.SYSTEM, ToolCategory.PLUGIN) }
            }
        }
    }
    
    /**
     * 获取工具数量
     */
    fun getToolCount(): Int = synchronized(tools) { tools.size }
    
    /**
     * 清空所有工具
     */
    fun clear() {
        synchronized(tools) {
            tools.clear()
            Log.d(TAG, "Cleared all MCP tools")
        }
    }
}

