package com.rostrum.core.ssh.connection

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * SSH 连接配置
 * 
 * 支持密码认证和密钥认证两种方式
 * 
 * @author OmniMaster
 * @license BSD-2-Clause
 */
@Serializable
data class SshConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = DEFAULT_PORT,
    val username: String,
    val authMethod: AuthMethod = AuthMethod.Password(""),
    val strictHostKeyChecking: Boolean = false,
    val keepAliveInterval: Int = DEFAULT_KEEP_ALIVE_INTERVAL,
    val connectionTimeout: Int = DEFAULT_CONNECTION_TIMEOUT
) {
    companion object {
        const val DEFAULT_PORT = 22
        const val DEFAULT_KEEP_ALIVE_INTERVAL = 30 // 秒
        const val DEFAULT_CONNECTION_TIMEOUT = 30_000 // 毫秒
    }
    
    /**
     * 认证方式
     */
    @Serializable
    sealed class AuthMethod {
        /**
         * 密码认证
         */
        @Serializable
        data class Password(val password: String) : AuthMethod()
        
        /**
         * 密钥认证
         * 
         * @param keyId 密钥ID（在SshKeyStore中的标识）
         * @param passphrase 密钥密码（可选）
         */
        @Serializable
        data class PrivateKey(
            val keyId: String,
            val passphrase: String? = null
        ) : AuthMethod()
        
        /**
         * 密钥数据认证（直接提供密钥数据）
         * 
         * @param keyData 密钥数据（Base64编码）
         * @param passphrase 密钥密码（可选）
         */
        @Serializable
        data class PrivateKeyData(
            val keyData: String,
            val passphrase: String? = null
        ) : AuthMethod()
    }
    
    /**
     * 获取显示名称
     */
    val displayName: String
        get() = if (name.isNotBlank()) name else "$username@$host"
    
    /**
     * 获取连接标识符（用于连接池）
     */
    val connectionKey: String
        get() = "$username@$host:$port"
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SshConfig
        return id == other.id
    }
    
    override fun hashCode(): Int = id.hashCode()
}

/**
 * SSH 连接状态
 */
sealed class SshConnectionState {
    /**
     * 未连接
     */
    object Disconnected : SshConnectionState() {
        override fun toString() = "Disconnected"
    }
    
    /**
     * 正在连接
     */
    object Connecting : SshConnectionState() {
        override fun toString() = "Connecting"
    }
    
    /**
     * 已连接
     * 
     * @param latencyMs 连接延迟（毫秒）
     * @param connectedAt 连接建立时间
     */
    data class Connected(
        val latencyMs: Long = 0,
        val connectedAt: Long = System.currentTimeMillis()
    ) : SshConnectionState()
    
    /**
     * 重新连接中
     * 
     * @param attempt 当前尝试次数
     * @param maxAttempts 最大尝试次数
     */
    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int
    ) : SshConnectionState()
    
    /**
     * 连接错误
     * 
     * @param message 错误信息
     * @param cause 原始异常
     * @param isRecoverable 是否可恢复
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val isRecoverable: Boolean = true
    ) : SshConnectionState()
    
    val isConnected: Boolean
        get() = this is Connected
    
    val isDisconnected: Boolean
        get() = this is Disconnected || this is Error
}

/**
 * 终端尺寸
 */
data class TerminalDimensions(
    val columns: Int = 80,
    val rows: Int = 24,
    val widthPixels: Int = 0,
    val heightPixels: Int = 0
)

/**
 * 连接延迟指标
 */
data class LatencyMetrics(
    val pingMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)
