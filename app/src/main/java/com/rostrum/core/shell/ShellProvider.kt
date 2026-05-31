package com.rostrum.core.shell

import kotlinx.coroutines.flow.StateFlow

/**
 * Shell Provider接口
 * 
 * 统一的Shell能力提供者接口，支持：
 * - 内置Shell实现（基于系统Shell/libsu）
 * - 插件提供的Shell实现
 * - 自研Shell实现（计划中）
 * 
 * 设计原则：
 * - Shell作为核心能力，但实现可以插件化
 * - 内置实现优先，插件实现作为增强
 */
interface ShellProvider {
    /**
     * Provider唯一标识符
     */
    val id: String
    
    /**
     * Provider名称
     */
    val name: String
    
    /**
     * Provider优先级（数字越大优先级越高）
     * 内置Shell优先级：100
     * 插件Shell优先级：50
     */
    val priority: Int
    
    /**
     * 是否可用
     */
    val isAvailable: Boolean
    
    /**
     * 创建Shell会话
     */
    fun createSession(): IShellSession
    
    /**
     * 检查命令是否支持
     */
    fun supportsCommand(command: String): Boolean
}

// ShellManager 接口已移除，使用 ShellManager 类（在 ShellManager.kt 中定义）
