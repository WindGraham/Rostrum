package com.rostrum.core.plugin.ipc.cache

import android.util.LruCache
import com.rostrum.core.config.AppConstants
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * IPC 调用缓存
 * 
 * 缓存 IPC 调用结果，减少跨进程通信次数
 */
class IPCCache(
    /**
     * 最大缓存大小（字节数）
     */
    maxSize: Int = 10 * 1024 * 1024  // 10MB
) {
    private val cache = LruCache<String, CacheEntry>(maxSize)
    private val mutex = Mutex()
    
    /**
     * 获取缓存值
     */
    suspend fun get(key: String): ByteArray? = mutex.withLock {
        val entry = cache.get(key) ?: return@withLock null
        
        // 检查是否过期
        if (entry.isExpired()) {
            cache.remove(key)
            return@withLock null
        }
        
        return@withLock entry.data
    }
    
    /**
     * 设置缓存值
     */
    suspend fun put(key: String, data: ByteArray, ttl: Long = AppConstants.Cache.IPC_CACHE_TTL_MS) = mutex.withLock {
        val entry = CacheEntry(data, System.currentTimeMillis() + ttl)
        cache.put(key, entry)
    }
    
    /**
     * 移除缓存
     */
    suspend fun remove(key: String) = mutex.withLock {
        cache.remove(key)
    }
    
    /**
     * 清空缓存
     */
    suspend fun clear() = mutex.withLock {
        cache.evictAll()
    }
    
    /**
     * 生成缓存键
     */
    fun generateKey(operation: String, vararg params: String): String {
        return "$operation:${params.joinToString(":")}"
    }
}

/**
 * 缓存条目
 */
data class CacheEntry(
    val data: ByteArray,
    val expireTime: Long
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() > expireTime
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as CacheEntry
        
        if (!data.contentEquals(other.data)) return false
        if (expireTime != other.expireTime) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + expireTime.hashCode()
        return result
    }
}

