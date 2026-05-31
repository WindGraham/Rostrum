package com.rostrum.core.mcp

import com.rostrum.core.mcp.impl.MCPProtocolImpl

/**
 * MCP协议管理器
 * 
 * 提供全局MCPProtocol单例，确保所有组件使用同一个实例
 */
object MCPProtocolManager {
    @Volatile
    private var instance: MCPProtocol? = null
    
    /**
     * 获取MCP协议实例（单例）
     */
    fun getInstance(): MCPProtocol {
        return instance ?: synchronized(this) {
            instance ?: MCPProtocolImpl().also { instance = it }
        }
    }
    
    /**
     * 重置实例（用于测试）
     */
    fun reset() {
        synchronized(this) {
            instance = null
        }
    }
}

