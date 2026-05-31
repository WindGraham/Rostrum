package com.rostrum.core.ssh.connection

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * SSH 连接池
 * 
 * 管理多个SSH连接的生命周期，提供：
 * - 连接复用
 * - 空闲连接清理
 * - 连接状态监控
 * 
 * @author OmniMaster
 * @license BSD-2-Clause
 */
class SshConnectionPool(
    private val keyStoreProvider: SshKeyStoreProvider? = null
) {
    companion object {
        private const val TAG = "SshConnectionPool"
        
        // 默认空闲超时时间（5分钟）
        const val DEFAULT_IDLE_TIMEOUT_MS = 5 * 60 * 1000L
        
        // 清理检查间隔（1分钟）
        const val CLEANUP_INTERVAL_MS = 60 * 1000L
        
        // 最大连接数
        const val MAX_CONNECTIONS = 10
    }
    
    // 连接映射：connectionId -> Connection
    private val connections = ConcurrentHashMap<String, SshConnectionImpl>()
    
    // 连接状态映射
    private val _connectionStates = MutableStateFlow<Map<String, SshConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, SshConnectionState>> = _connectionStates.asStateFlow()
    
    // 活跃连接数
    private val _activeConnectionCount = MutableStateFlow(0)
    val activeConnectionCount: StateFlow<Int> = _activeConnectionCount.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var cleanupJob: Job? = null
    
    init {
        startCleanupTask()
    }
    
    /**
     * 获取或创建连接
     * 
     * @param config SSH配置
     * @param autoConnect 是否自动连接
     * @return SSH连接
     */
    suspend fun getConnection(
        config: SshConfig,
        autoConnect: Boolean = true
    ): Result<ISshConnection> {
        // 检查是否已有连接
        val existingConnection = connections[config.id]
        if (existingConnection != null && existingConnection.isValid()) {
            existingConnection.touch()
            Log.d(TAG, "复用现有连接: ${config.displayName}")
            return Result.success(existingConnection)
        }
        
        // 检查连接数限制
        if (connections.size >= MAX_CONNECTIONS) {
            // 尝试清理无效连接
            cleanupInvalidConnections()
            
            if (connections.size >= MAX_CONNECTIONS) {
                return Result.failure(
                    IllegalStateException("连接数已达上限 ($MAX_CONNECTIONS)")
                )
            }
        }
        
        // 创建新连接
        Log.i(TAG, "创建新连接: ${config.displayName}")
        val connection = SshConnectionImpl(config, keyStoreProvider)
        connections[config.id] = connection
        
        // 监听连接状态
        scope.launch {
            connection.state.collect { state ->
                updateConnectionState(config.id, state)
            }
        }
        
        // 自动连接
        if (autoConnect) {
            val connectResult = connection.connect()
            if (connectResult.isFailure) {
                connections.remove(config.id)
                return Result.failure(connectResult.exceptionOrNull()!!)
            }
        }
        
        updateActiveCount()
        return Result.success(connection)
    }
    
    /**
     * 获取已有连接（不创建新连接）
     */
    fun getExistingConnection(connectionId: String): ISshConnection? {
        return connections[connectionId]?.takeIf { it.isValid() }
    }
    
    /**
     * 释放连接（标记为可回收，但不立即断开）
     */
    fun releaseConnection(connectionId: String) {
        Log.d(TAG, "释放连接: $connectionId")
        // 只更新活动时间，让清理任务处理断开
        connections[connectionId]?.touch()
    }
    
    /**
     * 断开并移除连接
     */
    suspend fun removeConnection(connectionId: String) {
        Log.i(TAG, "移除连接: $connectionId")
        connections.remove(connectionId)?.let { connection ->
            connection.disconnect()
            connection.close()
        }
        updateConnectionState(connectionId, null)
        updateActiveCount()
    }
    
    /**
     * 断开所有连接
     */
    suspend fun disconnectAll() {
        Log.i(TAG, "断开所有连接")
        val allConnections = connections.values.toList()
        connections.clear()
        
        allConnections.forEach { connection ->
            try {
                connection.disconnect()
                connection.close()
            } catch (e: Exception) {
                Log.w(TAG, "断开连接时出错", e)
            }
        }
        
        _connectionStates.value = emptyMap()
        updateActiveCount()
    }
    
    /**
     * 清理空闲连接
     */
    fun cleanupIdleConnections(idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS) {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()
        
        connections.forEach { (id, connection) ->
            val idleTime = now - connection.lastActiveTime
            if (idleTime > idleTimeoutMs) {
                Log.d(TAG, "连接空闲超时: $id (${idleTime}ms)")
                toRemove.add(id)
            }
        }
        
        scope.launch {
            toRemove.forEach { id ->
                removeConnection(id)
            }
        }
    }
    
    /**
     * 清理无效连接
     */
    private fun cleanupInvalidConnections() {
        val toRemove = connections.filter { (_, conn) -> !conn.isValid() }.keys
        
        scope.launch {
            toRemove.forEach { id ->
                removeConnection(id)
            }
        }
    }
    
    /**
     * 启动定期清理任务
     */
    private fun startCleanupTask() {
        cleanupJob?.cancel()
        cleanupJob = scope.launch {
            while (isActive) {
                delay(CLEANUP_INTERVAL_MS)
                cleanupIdleConnections()
                cleanupInvalidConnections()
            }
        }
    }
    
    /**
     * 更新连接状态
     */
    private fun updateConnectionState(connectionId: String, state: SshConnectionState?) {
        val currentStates = _connectionStates.value.toMutableMap()
        if (state != null) {
            currentStates[connectionId] = state
        } else {
            currentStates.remove(connectionId)
        }
        _connectionStates.value = currentStates
    }
    
    /**
     * 更新活跃连接数
     */
    private fun updateActiveCount() {
        _activeConnectionCount.value = connections.count { (_, conn) -> conn.isValid() }
    }
    
    /**
     * 获取所有连接信息
     */
    fun getAllConnections(): List<ConnectionInfo> {
        return connections.map { (id, conn) ->
            ConnectionInfo(
                id = id,
                config = conn.config,
                state = conn.state.value,
                lastActiveTime = conn.lastActiveTime
            )
        }
    }
    
    /**
     * 关闭连接池
     */
    fun shutdown() {
        cleanupJob?.cancel()
        scope.cancel()
        runBlocking {
            disconnectAll()
        }
    }
    
    /**
     * 连接信息
     */
    data class ConnectionInfo(
        val id: String,
        val config: SshConfig,
        val state: SshConnectionState,
        val lastActiveTime: Long
    )
}

/**
 * 空的密钥存储提供者（用于不需要密钥认证的场景）
 */
class EmptySshKeyStoreProvider : SshKeyStoreProvider {
    override suspend fun getPrivateKey(keyId: String): ByteArray? = null
}
