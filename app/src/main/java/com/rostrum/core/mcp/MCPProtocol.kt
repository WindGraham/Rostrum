package com.rostrum.core.mcp

/**
 * MCP（Model Context Protocol）协议接口
 */
interface MCPProtocol {
    /**
     * 注册工具
     * 
     * @param tool MCP工具
     */
    fun registerTool(tool: MCPTool)
    
    /**
     * 注销工具
     * 
     * @param toolName 工具名称
     */
    fun unregisterTool(toolName: String)
    
    /**
     * 获取可用工具列表
     * 
     * @return 工具列表
     */
    fun getAvailableTools(): List<MCPTool>
    
    /**
     * 调用工具
     * 
     * @param toolName 工具名称
     * @param arguments 工具参数
     * @param context MCP上下文
     * @return 工具执行结果
     */
    suspend fun callTool(
        toolName: String,
        arguments: Map<String, Any>,
        context: MCPContext
    ): MCPResult
    
    /**
     * 发现工具
     * 
     * @param scope 发现范围
     * @return 工具列表
     */
    suspend fun discoverTools(scope: MCPScope = MCPScope.ALL): List<MCPTool>
}

/**
 * MCP工具
 */
data class MCPTool(
    val name: String,
    val description: String,
    val inputSchema: JsonSchema,
    val outputSchema: JsonSchema,
    val handler: MCPToolHandler,
    val category: ToolCategory,
    val permissions: List<ToolPermission>
)

/**
 * MCP上下文
 */
data class MCPContext(
    val sessionId: String,
    val userId: String?,
    val workspacePath: String?,
    val environment: Map<String, String>,
    val permissions: Set<String>
)

/**
 * MCP结果
 */
data class MCPResult(
    val success: Boolean,
    val data: Any? = null,
    val error: MCPError? = null,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * MCP错误
 */
data class MCPError(
    val code: String,
    val message: String,
    val details: Map<String, Any> = emptyMap()
)

/**
 * 工具类别
 */
enum class ToolCategory {
    FILE_SYSTEM,
    TERMINAL,
    NETWORK,
    DATABASE,
    PLUGIN,
    SYSTEM,
    CUSTOM
}

/**
 * 工具权限
 */
enum class ToolPermission {
    READ_FILE,
    WRITE_FILE,
    EXECUTE_COMMAND,
    NETWORK_ACCESS,
    SYSTEM_ACCESS
}

/**
 * MCP范围
 */
enum class MCPScope {
    ALL,
    PLUGIN,
    SYSTEM,
    USER
}

/**
 * JSON Schema（简化版）
 */
data class JsonSchema(
    val type: String,
    val properties: Map<String, JsonSchemaProperty> = emptyMap(),
    val required: List<String> = emptyList()
)

/**
 * JSON Schema属性
 */
data class JsonSchemaProperty(
    val type: String,  // "string", "number", "boolean", "array", "object"
    val description: String? = null,
    val default: Any? = null,
    val enum: List<Any>? = null,
    val secret: Boolean = false,  // 标记敏感字段（如密码）
    val items: JsonSchemaProperty? = null  // 用于array类型，定义数组元素类型
)

/**
 * MCP工具处理器
 */
typealias MCPToolHandler = suspend (Map<String, Any>, MCPContext) -> MCPResult
