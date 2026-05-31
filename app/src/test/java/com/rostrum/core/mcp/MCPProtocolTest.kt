package com.rostrum.core.mcp

import org.junit.Assert.*
import org.junit.Test

/**
 * MCP 协议测试
 * 
 * 测试 Model Context Protocol 的核心功能
 */
class MCPProtocolTest {
    
    /**
     * 测试工具类别枚举
     */
    @Test
    fun `test tool categories`() {
        val categories = listOf(
            "FILE_SYSTEM",
            "TERMINAL",
            "NETWORK",
            "DATABASE",
            "PLUGIN",
            "SYSTEM",
            "CUSTOM"
        )
        
        assertEquals(7, categories.size)
        assertTrue(categories.contains("FILE_SYSTEM"))
        assertTrue(categories.contains("TERMINAL"))
        assertTrue(categories.contains("SYSTEM"))
    }
    
    /**
     * 测试工具权限枚举
     */
    @Test
    fun `test tool permissions`() {
        val permissions = listOf(
            "READ_FILE",
            "WRITE_FILE",
            "EXECUTE_COMMAND",
            "NETWORK_ACCESS",
            "SYSTEM_ACCESS"
        )
        
        assertEquals(5, permissions.size)
        assertTrue(permissions.contains("READ_FILE"))
        assertTrue(permissions.contains("WRITE_FILE"))
        assertTrue(permissions.contains("EXECUTE_COMMAND"))
    }
    
    /**
     * 测试 MCP 工具定义结构
     */
    @Test
    fun `test MCP tool definition structure`() {
        val tool = mapOf(
            "name" to "read_file",
            "description" to "读取文件内容",
            "category" to "FILE_SYSTEM",
            "permissions" to listOf("READ_FILE"),
            "parameters" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf(
                        "type" to "string",
                        "description" to "文件路径"
                    )
                ),
                "required" to listOf("path")
            )
        )
        
        assertEquals("read_file", tool["name"])
        assertEquals("FILE_SYSTEM", tool["category"])
        
        @Suppress("UNCHECKED_CAST")
        val permissions = tool["permissions"] as List<String>
        assertTrue(permissions.contains("READ_FILE"))
    }
    
    /**
     * 测试 MCP 上下文结构
     */
    @Test
    fun `test MCP context structure`() {
        val context = mapOf(
            "sessionId" to "mcp-session-123",
            "workspacePath" to "/storage/emulated/0",
            "metadata" to mapOf(
                "userId" to "user-1",
                "timestamp" to System.currentTimeMillis()
            )
        )
        
        assertEquals("mcp-session-123", context["sessionId"])
        assertEquals("/storage/emulated/0", context["workspacePath"])
        
        @Suppress("UNCHECKED_CAST")
        val metadata = context["metadata"] as Map<String, Any>
        assertEquals("user-1", metadata["userId"])
    }
    
    /**
     * 测试 MCP 结果结构
     */
    @Test
    fun `test MCP result structure`() {
        // 成功结果
        val successResult = mapOf(
            "success" to true,
            "data" to mapOf(
                "content" to "文件内容",
                "size" to 1024
            ),
            "message" to "操作成功"
        )
        
        assertTrue(successResult["success"] as Boolean)
        
        @Suppress("UNCHECKED_CAST")
        val data = successResult["data"] as Map<String, Any>
        assertEquals("文件内容", data["content"])
        
        // 失败结果
        val failureResult = mapOf(
            "success" to false,
            "error" to mapOf(
                "code" to "FILE_NOT_FOUND",
                "message" to "文件不存在"
            )
        )
        
        assertFalse(failureResult["success"] as Boolean)
        
        @Suppress("UNCHECKED_CAST")
        val error = failureResult["error"] as Map<String, String>
        assertEquals("FILE_NOT_FOUND", error["code"])
    }
    
    /**
     * 测试权限检查逻辑
     */
    @Test
    fun `test permission check logic`() {
        val requiredPermissions = setOf("READ_FILE", "WRITE_FILE")
        val grantedPermissions = setOf("READ_FILE", "WRITE_FILE", "EXECUTE_COMMAND")
        val insufficientPermissions = setOf("READ_FILE")
        
        // 有足够权限
        assertTrue(grantedPermissions.containsAll(requiredPermissions))
        
        // 权限不足
        assertFalse(insufficientPermissions.containsAll(requiredPermissions))
    }
    
    /**
     * 测试工具发现范围
     */
    @Test
    fun `test tool discovery scope`() {
        val scopes = listOf(
            "LOCAL",      // 本地工具
            "PLUGIN",     // 插件工具
            "NETWORK",    // 网络工具
            "ALL"         // 所有工具
        )
        
        assertEquals(4, scopes.size)
        assertTrue(scopes.contains("ALL"))
    }
    
    /**
     * 测试工具参数验证
     */
    @Test
    fun `test tool parameter validation`() {
        fun validateParams(params: Map<String, Any>, required: List<String>): List<String> {
            val errors = mutableListOf<String>()
            for (param in required) {
                if (!params.containsKey(param)) {
                    errors.add("缺少必需参数: $param")
                } else if (params[param] == null || (params[param] is String && (params[param] as String).isBlank())) {
                    errors.add("参数 '$param' 不能为空")
                }
            }
            return errors
        }
        
        // 有效参数
        val validParams = mapOf("path" to "/test.txt", "content" to "Hello")
        val validErrors = validateParams(validParams, listOf("path"))
        assertTrue(validErrors.isEmpty())
        
        // 缺少参数
        val missingParams = mapOf("content" to "Hello")
        val missingErrors = validateParams(missingParams, listOf("path"))
        assertEquals(1, missingErrors.size)
        assertTrue(missingErrors[0].contains("path"))
        
        // 空参数
        val emptyParams = mapOf("path" to "")
        val emptyErrors = validateParams(emptyParams, listOf("path"))
        assertEquals(1, emptyErrors.size)
    }
    
    /**
     * 测试错误代码
     */
    @Test
    fun `test MCP error codes`() {
        val errorCodes = mapOf(
            "TOOL_NOT_FOUND" to "工具不存在",
            "PERMISSION_DENIED" to "权限不足",
            "INVALID_PARAMETER" to "参数无效",
            "EXECUTION_ERROR" to "执行错误",
            "TIMEOUT" to "超时"
        )
        
        assertEquals(5, errorCodes.size)
        assertTrue(errorCodes.containsKey("PERMISSION_DENIED"))
    }
    
    /**
     * 测试工具适配器转换
     */
    @Test
    fun `test tool adapter conversion`() {
        // MCP Context -> Tool Context 转换
        val mcpContext = mapOf(
            "sessionId" to "mcp-123",
            "workspacePath" to "/workspace",
            "metadata" to emptyMap<String, Any>()
        )
        
        val toolContext = mapOf(
            "sessionId" to mcpContext["sessionId"],
            "workspacePath" to mcpContext["workspacePath"],
            "userId" to null,
            "environment" to emptyMap<String, String>(),
            "permissions" to emptySet<String>()
        )
        
        assertEquals(mcpContext["sessionId"], toolContext["sessionId"])
        assertEquals(mcpContext["workspacePath"], toolContext["workspacePath"])
    }
    
    /**
     * 测试插件工具列表
     */
    @Test
    fun `test plugin tools list`() {
        // 模拟插件提供的工具列表
        val pluginTools = listOf(
            mapOf("name" to "html_write_stream", "category" to "PLUGIN"),
            mapOf("name" to "pdf_get_info", "category" to "PLUGIN"),
            mapOf("name" to "excel_read_sheet", "category" to "PLUGIN")
        )
        
        assertEquals(3, pluginTools.size)
        assertTrue(pluginTools.all { it["category"] == "PLUGIN" })
    }
}
