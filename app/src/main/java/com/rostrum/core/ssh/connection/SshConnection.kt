package com.rostrum.core.ssh.connection

import android.util.Log
import com.jcraft.jsch.*
import com.rostrum.core.config.AppConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * SSH 连接接口
 * 
 * 提供SSH连接的核心功能，包括：
 * - 连接管理（连接、断开、重连）
 * - 通道创建（Shell、SFTP、Exec）
 * - 连接状态监控
 * 
 * @author OmniMaster
 * @license BSD-2-Clause
 */
interface ISshConnection : Closeable {
    /**
     * 连接配置
     */
    val config: SshConfig
    
    /**
     * 连接状态流
     */
    val state: StateFlow<SshConnectionState>
    
    /**
     * 连接ID
     */
    val connectionId: String
    
    /**
     * 最后活动时间
     */
    val lastActiveTime: Long
    
    /**
     * 建立连接
     */
    suspend fun connect(): Result<Unit>
    
    /**
     * 断开连接
     */
    suspend fun disconnect()
    
    /**
     * 测试连接并返回延迟
     */
    suspend fun testConnection(): Result<LatencyMetrics>
    
    /**
     * 打开 Shell 通道
     */
    fun openShellChannel(): Result<ChannelShell>
    
    /**
     * 打开 SFTP 通道
     */
    fun openSftpChannel(): Result<ChannelSftp>
    
    /**
     * 打开 Exec 通道
     */
    fun openExecChannel(): Result<ChannelExec>

    /**
     * 建立本地端口转发：127.0.0.1:localPort -> remoteHost:remotePort。
     * localPort 传 0 时由 JSch 自动分配可用端口。
     */
    fun setLocalPortForwarding(localPort: Int, remoteHost: String, remotePort: Int): Result<Int>

    /**
     * 移除本地端口转发。
     */
    fun removeLocalPortForwarding(localPort: Int): Result<Unit>
    
    /**
     * 更新最后活动时间
     */
    fun touch()
    
    /**
     * 检查连接是否有效
     */
    fun isValid(): Boolean
}

/**
 * SSH 连接实现
 */
class SshConnectionImpl(
    override val config: SshConfig,
    private val keyStore: SshKeyStoreProvider? = null
) : ISshConnection {
    
    companion object {
        private const val TAG = "SshConnection"
        private const val CHANNEL_TYPE_SHELL = "shell"
        private const val CHANNEL_TYPE_SFTP = "sftp"
        private const val CHANNEL_TYPE_EXEC = "exec"
    }
    
    override val connectionId: String = config.id
    
    private val jsch = JSch()
    private var session: Session? = null
    
    private val _state = MutableStateFlow<SshConnectionState>(SshConnectionState.Disconnected)
    override val state: StateFlow<SshConnectionState> = _state.asStateFlow()
    
    private val _lastActiveTime = AtomicLong(System.currentTimeMillis())
    override val lastActiveTime: Long get() = _lastActiveTime.get()
    
    private val isConnecting = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        if (isConnecting.getAndSet(true)) {
            return@withContext Result.failure(IllegalStateException("连接正在进行中"))
        }
        
        try {
            _state.value = SshConnectionState.Connecting
            
            // 断开现有连接
            session?.disconnect()
            session = null
            
            Log.i(TAG, "正在连接到 ${config.host}:${config.port}")
            
            // 配置认证
            when (val auth = config.authMethod) {
                is SshConfig.AuthMethod.Password -> {
                    // 密码认证不需要预配置
                }
                is SshConfig.AuthMethod.PrivateKey -> {
                    // 从密钥存储获取密钥
                    val keyData = keyStore?.getPrivateKey(auth.keyId)
                        ?: return@withContext Result.failure(
                            IllegalStateException("未找到密钥: ${auth.keyId}")
                        )
                    
                    if (auth.passphrase != null) {
                        jsch.addIdentity(auth.keyId, keyData, null, auth.passphrase.toByteArray())
                    } else {
                        jsch.addIdentity(auth.keyId, keyData, null, null)
                    }
                }
                is SshConfig.AuthMethod.PrivateKeyData -> {
                    val keyData = Base64.decode(auth.keyData)
                    if (auth.passphrase != null) {
                        jsch.addIdentity("key-${config.id}", keyData, null, auth.passphrase.toByteArray())
                    } else {
                        jsch.addIdentity("key-${config.id}", keyData, null, null)
                    }
                }
            }
            
            // 创建会话
            val startTime = System.currentTimeMillis()
            session = jsch.getSession(config.username, config.host, config.port).apply {
                // 配置密码
                if (config.authMethod is SshConfig.AuthMethod.Password) {
                    setPassword((config.authMethod as SshConfig.AuthMethod.Password).password)
                }
                
                // 配置选项
                val configProps = Properties().apply {
                    if (!config.strictHostKeyChecking) {
                        put("StrictHostKeyChecking", "no")
                    }
                    put("PreferredAuthentications", "publickey,password,keyboard-interactive")
                }
                setConfig(configProps)
                
                // 设置超时
                timeout = config.connectionTimeout
                
                // 设置保活
                if (config.keepAliveInterval > 0) {
                    setServerAliveInterval(config.keepAliveInterval * 1000)
                    setServerAliveCountMax(3)
                }
                
                // 连接
                connect(config.connectionTimeout)
            }
            
            val latency = System.currentTimeMillis() - startTime
            _state.value = SshConnectionState.Connected(latencyMs = latency)
            touch()
            
            Log.i(TAG, "SSH 连接成功，延迟: ${latency}ms")
            Result.success(Unit)
            
        } catch (e: JSchException) {
            Log.e(TAG, "SSH 连接失败", e)
            val errorMessage = when {
                e.message?.contains("Auth") == true -> "认证失败：用户名或密码错误"
                e.message?.contains("timeout") == true -> "连接超时"
                e.message?.contains("refused") == true -> "连接被拒绝"
                e.message?.contains("UnknownHost") == true -> "无法解析主机名"
                else -> "连接失败: ${e.message}"
            }
            _state.value = SshConnectionState.Error(errorMessage, e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "SSH 连接异常", e)
            _state.value = SshConnectionState.Error("连接异常: ${e.message}", e)
            Result.failure(e)
        } finally {
            isConnecting.set(false)
        }
    }
    
    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "断开 SSH 连接: ${config.displayName}")
            session?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "断开连接时出错", e)
        } finally {
            session = null
            _state.value = SshConnectionState.Disconnected
        }
    }
    
    override suspend fun testConnection(): Result<LatencyMetrics> = withContext(Dispatchers.IO) {
        val sess = session
        if (sess == null || !sess.isConnected) {
            return@withContext Result.failure(IllegalStateException("未连接"))
        }
        
        try {
            val startTime = System.currentTimeMillis()
            
            // 通过执行简单命令测试连接
            val channel = sess.openChannel(CHANNEL_TYPE_EXEC) as ChannelExec
            channel.setCommand("echo ping")
            channel.connect(5000)
            
            while (!channel.isClosed) {
                delay(10)
            }
            channel.disconnect()
            
            val latency = System.currentTimeMillis() - startTime
            touch()
            
            Result.success(LatencyMetrics(latency))
        } catch (e: Exception) {
            Log.e(TAG, "测试连接失败", e)
            Result.failure(e)
        }
    }
    
    override fun openShellChannel(): Result<ChannelShell> {
        val sess = session
        if (sess == null || !sess.isConnected) {
            return Result.failure(IllegalStateException("未连接"))
        }
        
        return try {
            val channel = sess.openChannel(CHANNEL_TYPE_SHELL) as ChannelShell
            touch()
            Result.success(channel)
        } catch (e: JSchException) {
            Log.e(TAG, "打开 Shell 通道失败", e)
            Result.failure(e)
        }
    }
    
    override fun openSftpChannel(): Result<ChannelSftp> {
        val sess = session
        if (sess == null || !sess.isConnected) {
            return Result.failure(IllegalStateException("未连接"))
        }
        
        return try {
            val channel = sess.openChannel(CHANNEL_TYPE_SFTP) as ChannelSftp
            touch()
            Result.success(channel)
        } catch (e: JSchException) {
            Log.e(TAG, "打开 SFTP 通道失败", e)
            Result.failure(e)
        }
    }
    
    override fun openExecChannel(): Result<ChannelExec> {
        val sess = session
        if (sess == null || !sess.isConnected) {
            return Result.failure(IllegalStateException("未连接"))
        }
        
        return try {
            val channel = sess.openChannel(CHANNEL_TYPE_EXEC) as ChannelExec
            touch()
            Result.success(channel)
        } catch (e: JSchException) {
            Log.e(TAG, "打开 Exec 通道失败", e)
            Result.failure(e)
        }
    }

    override fun setLocalPortForwarding(localPort: Int, remoteHost: String, remotePort: Int): Result<Int> {
        val sess = session
        if (sess == null || !sess.isConnected) {
            return Result.failure(IllegalStateException("未连接"))
        }

        return try {
            val assignedPort = sess.setPortForwardingL("127.0.0.1", localPort, remoteHost, remotePort)
            touch()
            Result.success(assignedPort)
        } catch (e: JSchException) {
            Log.e(TAG, "建立端口转发失败", e)
            Result.failure(e)
        }
    }

    override fun removeLocalPortForwarding(localPort: Int): Result<Unit> {
        val sess = session
        if (sess == null || !sess.isConnected) {
            return Result.failure(IllegalStateException("未连接"))
        }

        return try {
            sess.delPortForwardingL(localPort)
            touch()
            Result.success(Unit)
        } catch (e: JSchException) {
            Log.e(TAG, "移除端口转发失败", e)
            Result.failure(e)
        }
    }
    
    override fun touch() {
        _lastActiveTime.set(System.currentTimeMillis())
    }
    
    override fun isValid(): Boolean {
        val sess = session ?: return false
        return sess.isConnected
    }
    
    /**
     * 深度验证连接是否真正可用（通过发送 KeepAlive）
     * 仅在需要确认连接状态时调用
     */
    fun deepValidate(): Boolean {
        val sess = session ?: return false
        if (!sess.isConnected) return false
        
        return try {
            sess.sendKeepAliveMsg()
            true
        } catch (e: Exception) {
            Log.w(TAG, "连接深度验证失败: ${e.message}")
            false
        }
    }
    
    override fun close() {
        runBlocking {
            disconnect()
        }
        scope.cancel()
    }
}

/**
 * 密钥存储提供者接口
 * 
 * 用于从安全存储中获取SSH密钥
 */
interface SshKeyStoreProvider {
    suspend fun getPrivateKey(keyId: String): ByteArray?
}
