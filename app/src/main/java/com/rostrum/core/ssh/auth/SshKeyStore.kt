package com.rostrum.core.ssh.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.rostrum.core.ssh.connection.SshKeyStoreProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * SSH 密钥存储
 * 
 * 使用 Android Keystore 安全存储SSH密钥
 * 
 * 功能：
 * - 存储/检索私钥
 * - 密钥元数据管理
 * - 密钥导入/导出（加密）
 * 
 * @author OmniMaster
 * @license BSD-2-Clause
 */
class SshKeyStore(
    private val context: Context
) : SshKeyStoreProvider {
    
    companion object {
        private const val TAG = "SshKeyStore"
        private const val PREFS_NAME = "ssh_key_store"
        private const val KEYS_INDEX_KEY = "keys_index"
        private const val KEY_PREFIX = "key_"
        private const val KEYSTORE_ALIAS = "OmniMaster_SSH_KEY"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // 使用 SharedPreferences 存储加密数据（数据本身用 Android Keystore 加密）
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * 保存私钥
     * 
     * @param id 密钥ID
     * @param name 密钥名称
     * @param keyData 密钥数据
     * @param keyType 密钥类型（RSA, ED25519等）
     * @param passphrase 密钥密码（可选）
     */
    suspend fun savePrivateKey(
        id: String,
        name: String,
        keyData: ByteArray,
        keyType: String = "RSA",
        passphrase: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 加密密钥数据
            val encryptedData = encryptData(keyData)
            val encodedData = Base64.encodeToString(encryptedData, Base64.NO_WRAP)
            
            // 创建密钥信息
            val keyInfo = KeyInfo(
                id = id,
                name = name,
                type = keyType,
                createdAt = System.currentTimeMillis(),
                hasPassphrase = passphrase != null
            )
            
            // 存储密钥数据
            prefs.edit()
                .putString("$KEY_PREFIX$id", encodedData)
                .apply()
            
            // 如果有密码，单独存储（也是加密的）
            if (passphrase != null) {
                val encryptedPassphrase = encryptData(passphrase.toByteArray())
                prefs.edit()
                    .putString("${KEY_PREFIX}${id}_passphrase", 
                        Base64.encodeToString(encryptedPassphrase, Base64.NO_WRAP))
                    .apply()
            }
            
            // 更新密钥索引
            updateKeyIndex { keys ->
                keys.filter { it.id != id } + keyInfo
            }
            
            Log.i(TAG, "密钥已保存: $name ($id)")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "保存密钥失败: $id", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取私钥数据
     */
    override suspend fun getPrivateKey(keyId: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val encodedData: String = prefs.getString("$KEY_PREFIX$keyId", null)
                ?: return@withContext null
            
            val encryptedData = Base64.decode(encodedData, Base64.NO_WRAP)
            decryptData(encryptedData)
            
        } catch (e: Exception) {
            Log.e(TAG, "获取密钥失败: $keyId", e)
            null
        }
    }
    
    /**
     * 获取密钥密码
     */
    suspend fun getKeyPassphrase(keyId: String): String? = withContext(Dispatchers.IO) {
        try {
            val encodedPassphrase: String = prefs.getString("${KEY_PREFIX}${keyId}_passphrase", null)
                ?: return@withContext null
            
            val encryptedPassphrase = Base64.decode(encodedPassphrase, Base64.NO_WRAP)
            String(decryptData(encryptedPassphrase))
            
        } catch (e: Exception) {
            Log.e(TAG, "获取密钥密码失败: $keyId", e)
            null
        }
    }
    
    /**
     * 删除私钥
     */
    suspend fun deletePrivateKey(keyId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            prefs.edit()
                .remove("$KEY_PREFIX$keyId")
                .remove("${KEY_PREFIX}${keyId}_passphrase")
                .apply()
            
            updateKeyIndex { keys ->
                keys.filter { it.id != keyId }
            }
            
            Log.i(TAG, "密钥已删除: $keyId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "删除密钥失败: $keyId", e)
            Result.failure(e)
        }
    }
    
    /**
     * 列出所有密钥
     */
    suspend fun listKeys(): List<KeyInfo> = withContext(Dispatchers.IO) {
        try {
            val indexJson = prefs.getString(KEYS_INDEX_KEY, null)
                ?: return@withContext emptyList()
            
            json.decodeFromString<List<KeyInfo>>(indexJson)
            
        } catch (e: Exception) {
            Log.e(TAG, "列出密钥失败", e)
            emptyList()
        }
    }
    
    /**
     * 获取密钥信息
     */
    suspend fun getKeyInfo(keyId: String): KeyInfo? = withContext(Dispatchers.IO) {
        listKeys().find { it.id == keyId }
    }
    
    /**
     * 检查密钥是否存在
     */
    suspend fun hasKey(keyId: String): Boolean = withContext(Dispatchers.IO) {
        prefs.contains("$KEY_PREFIX$keyId")
    }
    
    /**
     * 导入密钥（从PEM格式）
     */
    suspend fun importFromPem(
        name: String,
        pemContent: String,
        passphrase: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 检测密钥类型
            val keyType = when {
                pemContent.contains("RSA PRIVATE KEY") -> "RSA"
                pemContent.contains("EC PRIVATE KEY") -> "ECDSA"
                pemContent.contains("OPENSSH PRIVATE KEY") -> {
                    // OpenSSH 格式可能是 ED25519 或其他
                    if (pemContent.contains("ed25519")) "ED25519" else "OpenSSH"
                }
                pemContent.contains("DSA PRIVATE KEY") -> "DSA"
                else -> "Unknown"
            }
            
            val keyId = java.util.UUID.randomUUID().toString()
            val keyData = pemContent.toByteArray(Charsets.UTF_8)
            
            val result = savePrivateKey(keyId, name, keyData, keyType, passphrase)
            
            if (result.isSuccess) {
                Result.success(keyId)
            } else {
                Result.failure(result.exceptionOrNull()!!)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "导入PEM密钥失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 导出密钥（为PEM格式）
     * 注意：仅导出到本地，不应该传输
     */
    suspend fun exportToPem(keyId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val keyData = getPrivateKey(keyId)
                ?: return@withContext Result.failure(IllegalArgumentException("密钥不存在"))
            
            Result.success(String(keyData, Charsets.UTF_8))
            
        } catch (e: Exception) {
            Log.e(TAG, "导出PEM密钥失败: $keyId", e)
            Result.failure(e)
        }
    }
    
    /**
     * 重命名密钥
     */
    suspend fun renameKey(keyId: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            updateKeyIndex { keys ->
                keys.map { 
                    if (it.id == keyId) it.copy(name = newName) else it 
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ==================== 私有方法 ====================
    
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        // 检查是否已有密钥
        val existingKey = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }
        
        // 创建新密钥
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        
        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }
    
    private fun encryptData(data: ByteArray): ByteArray {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data)
        
        // 将 IV 和加密数据组合
        return iv + encryptedData
    }
    
    private fun decryptData(data: ByteArray): ByteArray {
        val secretKey = getOrCreateSecretKey()
        
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val encryptedData = data.copyOfRange(GCM_IV_LENGTH, data.size)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        return cipher.doFinal(encryptedData)
    }
    
    private fun updateKeyIndex(transform: (List<KeyInfo>) -> List<KeyInfo>) {
        val currentKeys = try {
            val indexJson = prefs.getString(KEYS_INDEX_KEY, null)
            if (indexJson != null) {
                json.decodeFromString<List<KeyInfo>>(indexJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
        
        val updatedKeys = transform(currentKeys)
        val updatedJson = json.encodeToString(updatedKeys)
        
        prefs.edit()
            .putString(KEYS_INDEX_KEY, updatedJson)
            .apply()
    }
}

/**
 * 密钥信息
 */
@Serializable
data class KeyInfo(
    val id: String,
    val name: String,
    val type: String,
    val createdAt: Long,
    val hasPassphrase: Boolean = false
) {
    /**
     * 格式化的密钥类型
     */
    val formattedType: String
        get() = when (type) {
            "RSA" -> "RSA"
            "ED25519" -> "Ed25519"
            "ECDSA" -> "ECDSA"
            "DSA" -> "DSA (不推荐)"
            else -> type
        }
    
    /**
     * 创建时间的格式化字符串
     */
    val formattedCreatedAt: String
        get() {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(createdAt))
        }
}
