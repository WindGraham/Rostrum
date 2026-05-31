package com.rostrum.core.ssh.auth

import android.content.Context
import android.util.Base64
import android.util.Log
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * SSH 主机密钥验证器
 * 
 * 管理已知主机的公钥，防止中间人攻击
 * 
 * @author OmniMaster
 * @license BSD-2-Clause
 */
class SshHostKeyVerifier(
    private val context: Context
) : HostKeyRepository {
    
    companion object {
        private const val TAG = "SshHostKeyVerifier"
        private const val KNOWN_HOSTS_FILE = "known_hosts.json"
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    
    private val knownHostsFile: File
        get() = File(context.filesDir, KNOWN_HOSTS_FILE)
    
    // 内存缓存
    private val knownHosts = mutableMapOf<String, KnownHost>()
    
    init {
        // 加载已知主机
        loadKnownHosts()
    }
    
    /**
     * 验证主机密钥
     * 
     * @param host 主机地址
     * @param port 端口
     * @param key 主机公钥
     * @return 验证结果
     */
    suspend fun verifyHostKey(
        host: String,
        port: Int,
        key: ByteArray,
        keyType: String
    ): VerificationResult = withContext(Dispatchers.IO) {
        val hostId = getHostId(host, port)
        val keyFingerprint = getFingerprint(key)
        
        val existingHost = knownHosts[hostId]
        
        when {
            existingHost == null -> {
                // 新主机
                Log.d(TAG, "新主机: $hostId")
                VerificationResult.Unknown(
                    host = host,
                    port = port,
                    keyType = keyType,
                    fingerprint = keyFingerprint
                )
            }
            existingHost.keyFingerprint == keyFingerprint -> {
                // 密钥匹配
                Log.d(TAG, "主机密钥验证通过: $hostId")
                VerificationResult.Trusted(host, port)
            }
            else -> {
                // 密钥已更改（可能是中间人攻击）
                Log.w(TAG, "主机密钥已更改: $hostId")
                VerificationResult.Changed(
                    host = host,
                    port = port,
                    oldFingerprint = existingHost.keyFingerprint,
                    newFingerprint = keyFingerprint,
                    keyType = keyType
                )
            }
        }
    }
    
    /**
     * 添加已知主机
     */
    suspend fun addKnownHost(
        host: String,
        port: Int,
        key: ByteArray,
        keyType: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val hostId = getHostId(host, port)
            val fingerprint = getFingerprint(key)
            val encodedKey = Base64.encodeToString(key, Base64.NO_WRAP)
            
            val knownHost = KnownHost(
                host = host,
                port = port,
                keyType = keyType,
                keyFingerprint = fingerprint,
                keyData = encodedKey,
                addedAt = System.currentTimeMillis()
            )
            
            knownHosts[hostId] = knownHost
            saveKnownHosts()
            
            Log.i(TAG, "已添加已知主机: $hostId ($keyType)")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "添加已知主机失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 更新主机密钥（用户确认后）
     */
    suspend fun updateHostKey(
        host: String,
        port: Int,
        newKey: ByteArray,
        keyType: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val hostId = getHostId(host, port)
            val fingerprint = getFingerprint(newKey)
            val encodedKey = Base64.encodeToString(newKey, Base64.NO_WRAP)
            
            val knownHost = KnownHost(
                host = host,
                port = port,
                keyType = keyType,
                keyFingerprint = fingerprint,
                keyData = encodedKey,
                addedAt = System.currentTimeMillis()
            )
            
            knownHosts[hostId] = knownHost
            saveKnownHosts()
            
            Log.i(TAG, "已更新主机密钥: $hostId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "更新主机密钥失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 删除已知主机
     */
    suspend fun removeKnownHost(host: String, port: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val hostId = getHostId(host, port)
            knownHosts.remove(hostId)
            saveKnownHosts()
            
            Log.i(TAG, "已删除已知主机: $hostId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "删除已知主机失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取所有已知主机
     */
    fun getAllKnownHosts(): List<KnownHost> {
        return knownHosts.values.toList()
    }
    
    /**
     * 清除所有已知主机
     */
    suspend fun clearAllKnownHosts(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            knownHosts.clear()
            saveKnownHosts()
            Log.i(TAG, "已清除所有已知主机")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "清除已知主机失败", e)
            Result.failure(e)
        }
    }
    
    // ==================== HostKeyRepository 接口实现 ====================
    
    override fun getKnownHostsRepositoryID(): String {
        return "OmniMaster_SSH_KnownHosts"
    }
    
    override fun check(host: String, key: ByteArray): Int {
        val result = runBlocking {
            verifyHostKey(host, 22, key, getKeyType(key))
        }
        
        return when (result) {
            is VerificationResult.Trusted -> HostKeyRepository.OK
            is VerificationResult.Unknown -> HostKeyRepository.NOT_INCLUDED
            is VerificationResult.Changed -> HostKeyRepository.CHANGED
        }
    }
    
    override fun add(hostKey: HostKey, userInfo: UserInfo?) {
        runBlocking {
            // JSch HostKey.getKey() 返回 base64 编码的字符串
            val keyData = Base64.decode(hostKey.key, Base64.NO_WRAP)
            addKnownHost(
                host = hostKey.host,
                port = 22,
                key = keyData,
                keyType = hostKey.type
            )
        }
    }
    
    override fun remove(host: String, type: String) {
        runBlocking {
            removeKnownHost(host, 22)
        }
    }
    
    override fun remove(host: String, type: String, key: ByteArray) {
        runBlocking {
            removeKnownHost(host, 22)
        }
    }
    
    override fun getHostKey(): Array<HostKey> {
        return knownHosts.values.mapNotNull { knownHost ->
            try {
                val keyData = Base64.decode(knownHost.keyData, Base64.NO_WRAP)
                HostKey(knownHost.host, keyData)
            } catch (e: Exception) {
                null
            }
        }.toTypedArray()
    }
    
    override fun getHostKey(host: String, type: String): Array<HostKey> {
        return knownHosts.values
            .filter { it.host == host && (type.isEmpty() || it.keyType == type) }
            .mapNotNull { knownHost ->
                try {
                    val keyData = Base64.decode(knownHost.keyData, Base64.NO_WRAP)
                    HostKey(knownHost.host, keyData)
                } catch (e: Exception) {
                    null
                }
            }.toTypedArray()
    }
    
    // ==================== 私有方法 ====================
    
    private fun getHostId(host: String, port: Int): String {
        return if (port == 22) host else "$host:$port"
    }
    
    private fun getFingerprint(key: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(key)
        return digest.joinToString(":") { "%02x".format(it) }
    }
    
    private fun getKeyType(key: ByteArray): String {
        // 简化的密钥类型检测
        return when {
            key.size > 256 -> "ssh-rsa"
            key.size == 32 -> "ssh-ed25519"
            else -> "unknown"
        }
    }
    
    private fun loadKnownHosts() {
        try {
            if (knownHostsFile.exists()) {
                val content = knownHostsFile.readText()
                val hosts = json.decodeFromString<List<KnownHost>>(content)
                
                knownHosts.clear()
                hosts.forEach { host ->
                    val hostId = getHostId(host.host, host.port)
                    knownHosts[hostId] = host
                }
                
                Log.d(TAG, "已加载 ${knownHosts.size} 个已知主机")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载已知主机失败", e)
        }
    }
    
    private fun saveKnownHosts() {
        try {
            val content = json.encodeToString(knownHosts.values.toList())
            knownHostsFile.writeText(content)
            Log.d(TAG, "已保存 ${knownHosts.size} 个已知主机")
        } catch (e: Exception) {
            Log.e(TAG, "保存已知主机失败", e)
        }
    }
}

/**
 * 主机密钥验证结果
 */
sealed class VerificationResult {
    /**
     * 已信任（密钥匹配）
     */
    data class Trusted(
        val host: String,
        val port: Int
    ) : VerificationResult()
    
    /**
     * 未知主机（首次连接）
     */
    data class Unknown(
        val host: String,
        val port: Int,
        val keyType: String,
        val fingerprint: String
    ) : VerificationResult()
    
    /**
     * 密钥已更改（潜在安全风险）
     */
    data class Changed(
        val host: String,
        val port: Int,
        val oldFingerprint: String,
        val newFingerprint: String,
        val keyType: String
    ) : VerificationResult()
}

/**
 * 已知主机信息
 */
@Serializable
data class KnownHost(
    val host: String,
    val port: Int,
    val keyType: String,
    val keyFingerprint: String,
    val keyData: String,
    val addedAt: Long
) {
    /**
     * 显示名称
     */
    val displayName: String
        get() = if (port == 22) host else "$host:$port"
    
    /**
     * 格式化的添加时间
     */
    val formattedAddedAt: String
        get() {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(addedAt))
        }
    
    /**
     * 简短的指纹（前8个字节）
     */
    val shortFingerprint: String
        get() = keyFingerprint.split(":").take(4).joinToString(":")
}
