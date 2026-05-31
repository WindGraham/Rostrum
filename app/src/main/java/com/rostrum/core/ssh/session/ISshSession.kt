package com.rostrum.core.ssh.session

import com.rostrum.core.ssh.connection.ISshConnection
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * SSH 会话基础接口
 * 
 * 所有SSH会话类型（终端、文件传输、命令执行）的基础接口
 * 
 * @author OmniMaster
 * @license BSD-2-Clause
 */
sealed interface ISshSession {
    /**
     * 会话ID
     */
    val id: String
    
    /**
     * 关联的SSH连接
     */
    val connection: ISshConnection
    
    /**
     * 会话是否活跃
     */
    val isActive: Boolean
    
    /**
     * 会话状态
     */
    val state: StateFlow<SessionState>
    
    /**
     * 启动会话
     */
    suspend fun start(): Result<Unit>
    
    /**
     * 关闭会话（挂起版本，用于需要等待关闭完成的场景）
     */
    suspend fun closeAsync()
    
    /**
     * 关闭会话（同步版本，兼容IShellSession）
     */
    fun close()
}

/**
 * 会话状态
 */
sealed class SessionState {
    /**
     * 未启动
     */
    object Idle : SessionState() {
        override fun toString() = "Idle"
    }
    
    /**
     * 启动中
     */
    object Starting : SessionState() {
        override fun toString() = "Starting"
    }
    
    /**
     * 运行中
     */
    object Running : SessionState() {
        override fun toString() = "Running"
    }
    
    /**
     * 已暂停
     */
    object Paused : SessionState() {
        override fun toString() = "Paused"
    }
    
    /**
     * 已关闭
     */
    object Closed : SessionState() {
        override fun toString() = "Closed"
    }
    
    /**
     * 错误状态
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : SessionState()
    
    val isRunning: Boolean
        get() = this is Running
    
    val isClosed: Boolean
        get() = this is Closed || this is Error
}

/**
 * 会话工厂
 */
object SshSessionFactory {
    /**
     * 创建终端会话
     */
    fun createTerminalSession(
        connection: ISshConnection,
        columns: Int = 80,
        rows: Int = 24
    ): SshTerminalSession {
        return SshTerminalSession(connection, columns, rows)
    }
    
    /**
     * 创建文件传输会话
     */
    fun createFileSession(connection: ISshConnection): SshFileSession {
        return SshFileSession(connection)
    }
    
    /**
     * 创建命令执行会话
     */
    fun createCommandSession(connection: ISshConnection): SshCommandSession {
        return SshCommandSession(connection)
    }
}
