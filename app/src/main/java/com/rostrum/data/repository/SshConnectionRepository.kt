package com.rostrum.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.rostrum.core.ssh.connection.SshConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SSH连接保存结果
 */
enum class SshSaveResult {
    /** 新增连接 */
    ADDED,
    /** 更新已有连接（同ID） */
    UPDATED_SAME_ID,
    /** 更新已有连接（相同host+port+username组合） */
    UPDATED_DUPLICATE
}

/**
 * SSH连接配置仓库
 * 
 * 负责SSH连接配置的持久化存储和管理
 */
interface SshConnectionRepository {
    /**
     * 获取所有保存的连接配置
     */
    fun getAllConnections(): List<SshConfig>
    
    /**
     * 观察连接配置变化
     */
    fun observeConnections(): Flow<List<SshConfig>>
    
    /**
     * 保存连接配置
     * 
     * @return 保存结果（新增/更新）
     */
    suspend fun saveConnection(config: SshConfig): SshSaveResult
    
    /**
     * 删除连接配置
     */
    suspend fun deleteConnection(configId: String)
    
    /**
     * 更新连接配置
     */
    suspend fun updateConnection(config: SshConfig)
    
    /**
     * 根据ID获取连接配置
     */
    fun getConnectionById(configId: String): SshConfig?
    
    /**
     * 根据主机地址查找连接配置
     */
    fun findConnectionByHost(host: String, port: Int = 22, username: String? = null): SshConfig?
}

/**
 * SSH连接仓库实现
 * 
 * 使用SharedPreferences进行持久化存储
 */
class SshConnectionRepositoryImpl(context: Context) : SshConnectionRepository {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, 
        Context.MODE_PRIVATE
    )
    
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    
    private val _connectionsFlow = MutableStateFlow<List<SshConfig>>(emptyList())
    
    init {
        // 初始化时加载保存的连接
        _connectionsFlow.value = loadConnections()
    }
    
    override fun getAllConnections(): List<SshConfig> {
        return _connectionsFlow.value
    }
    
    override fun observeConnections(): Flow<List<SshConfig>> {
        return _connectionsFlow.asStateFlow()
    }
    
    override suspend fun saveConnection(config: SshConfig): SshSaveResult {
        val currentList = loadConnections().toMutableList()
        var result: SshSaveResult
        
        // 1. 首先检查是否已存在相同ID的配置
        val existingIndex = currentList.indexOfFirst { it.id == config.id }
        if (existingIndex >= 0) {
            // 相同ID存在，直接更新
            android.util.Log.d(TAG, "Updating existing SSH connection with same ID: ${config.id}")
            currentList[existingIndex] = config
            result = SshSaveResult.UPDATED_SAME_ID
        } else {
            // 2. 检查是否已存在相同的 host+port+username 组合
            val duplicateIndex = currentList.indexOfFirst { 
                it.host == config.host && 
                it.port == config.port && 
                it.username == config.username 
            }
            if (duplicateIndex >= 0) {
                // 存在相同的连接配置，使用新配置数据但保留原ID进行更新
                val existingConfig = currentList[duplicateIndex]
                android.util.Log.d(
                    TAG, 
                    "Found duplicate SSH connection for ${config.username}@${config.host}:${config.port}. " +
                    "Replacing config ID ${existingConfig.id} with ${config.id}"
                )
                // 使用新配置但保留原ID，或者使用新配置完全替换（这里选择完全替换，保留新ID）
                currentList[duplicateIndex] = config
                result = SshSaveResult.UPDATED_DUPLICATE
            } else {
                // 3. 全新配置，添加到列表
                android.util.Log.d(TAG, "Adding new SSH connection: ${config.connectionKey}")
                currentList.add(config)
                result = SshSaveResult.ADDED
            }
        }
        
        saveConnections(currentList)
        _connectionsFlow.value = currentList
        return result
    }
    
    override suspend fun deleteConnection(configId: String) {
        val currentList = loadConnections().filter { it.id != configId }
        saveConnections(currentList)
        _connectionsFlow.value = currentList
    }
    
    override suspend fun updateConnection(config: SshConfig) {
        saveConnection(config)
    }
    
    override fun getConnectionById(configId: String): SshConfig? {
        return loadConnections().find { it.id == configId }
    }
    
    override fun findConnectionByHost(host: String, port: Int, username: String?): SshConfig? {
        return loadConnections().find { config ->
            config.host == host && 
            config.port == port &&
            (username == null || config.username == username)
        }
    }
    
    /**
     * 从SharedPreferences加载连接列表
     */
    private fun loadConnections(): List<SshConfig> {
        val jsonString = prefs.getString(KEY_CONNECTIONS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<SshConfig>>(jsonString)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load SSH connections", e)
            emptyList()
        }
    }
    
    /**
     * 保存连接列表到SharedPreferences
     */
    private fun saveConnections(connections: List<SshConfig>) {
        try {
            val jsonString = json.encodeToString(connections)
            prefs.edit().putString(KEY_CONNECTIONS, jsonString).apply()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save SSH connections", e)
        }
    }
    
    companion object {
        private const val TAG = "SshConnectionRepository"
        private const val PREFS_NAME = "omnimaster_ssh_connections"
        private const val KEY_CONNECTIONS = "ssh_connections_json"
    }
}
