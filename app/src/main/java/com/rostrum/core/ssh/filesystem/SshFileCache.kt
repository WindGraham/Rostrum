package com.rostrum.core.ssh.filesystem

import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest

/**
 * SSH 文件缓存
 * 
 * 缓存远程文件内容，减少网络请求
 * 支持内存缓存和磁盘缓存两级
 * 
 * @author OmniMaster
 * @license BSD-2-Clause
 */
class SshFileCache(
    private val cacheDir: File? = null,
    private val maxMemoryCacheSize: Int = DEFAULT_MEMORY_CACHE_SIZE,
    private val maxDiskCacheSize: Long = DEFAULT_DISK_CACHE_SIZE,
    private val defaultTtlMs: Long = DEFAULT_TTL_MS
) {
    companion object {
        private const val TAG = "SshFileCache"
        
        // 默认内存缓存大小：10MB
        const val DEFAULT_MEMORY_CACHE_SIZE = 10 * 1024 * 1024
        
        // 默认磁盘缓存大小：100MB
        const val DEFAULT_DISK_CACHE_SIZE = 100L * 1024 * 1024
        
        // 默认TTL：5分钟
        const val DEFAULT_TTL_MS = 5 * 60 * 1000L
        
        // 清理间隔：1分钟
        const val CLEANUP_INTERVAL_MS = 60 * 1000L
    }
    
    // 内存缓存
    private val memoryCache = object : LruCache<String, CachedFile>(maxMemoryCacheSize) {
        override fun sizeOf(key: String, value: CachedFile): Int {
            return value.content.size
        }
    }
    
    // 元数据缓存（用于跟踪缓存项的TTL）
    private val metadata = mutableMapOf<String, CacheMetadata>()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var cleanupJob: Job? = null
    
    init {
        // 确保缓存目录存在
        cacheDir?.mkdirs()
        
        // 启动清理任务
        startCleanupTask()
    }
    
    /**
     * 获取缓存内容
     * 
     * @param remotePath 远程文件路径
     * @return 缓存的文件内容，如果不存在或已过期返回null
     */
    fun get(remotePath: String): CachedFile? {
        val key = pathToKey(remotePath)
        
        // 检查元数据
        val meta = metadata[key]
        if (meta != null && meta.isExpired()) {
            // 缓存已过期
            invalidate(remotePath)
            return null
        }
        
        // 先查内存缓存
        memoryCache.get(key)?.let { cached ->
            Log.d(TAG, "内存缓存命中: $remotePath")
            return cached
        }
        
        // 再查磁盘缓存
        if (cacheDir != null) {
            val diskFile = getDiskCacheFile(key)
            if (diskFile.exists()) {
                try {
                    val content = diskFile.readBytes()
                    val cachedFile = CachedFile(
                        content = content,
                        cachedAt = meta?.cachedAt ?: diskFile.lastModified(),
                        etag = meta?.etag
                    )
                    
                    // 写入内存缓存
                    memoryCache.put(key, cachedFile)
                    
                    Log.d(TAG, "磁盘缓存命中: $remotePath")
                    return cachedFile
                } catch (e: Exception) {
                    Log.e(TAG, "读取磁盘缓存失败: $remotePath", e)
                }
            }
        }
        
        return null
    }
    
    /**
     * 写入缓存
     * 
     * @param remotePath 远程文件路径
     * @param content 文件内容
     * @param etag 可选的ETag（用于验证）
     * @param ttlMs 缓存有效期（毫秒）
     */
    fun put(
        remotePath: String,
        content: ByteArray,
        etag: String? = null,
        ttlMs: Long = defaultTtlMs
    ) {
        val key = pathToKey(remotePath)
        val now = System.currentTimeMillis()
        
        val cachedFile = CachedFile(
            content = content,
            cachedAt = now,
            etag = etag
        )
        
        // 更新元数据
        metadata[key] = CacheMetadata(
            cachedAt = now,
            expiresAt = now + ttlMs,
            etag = etag,
            size = content.size.toLong()
        )
        
        // 写入内存缓存
        memoryCache.put(key, cachedFile)
        
        // 异步写入磁盘缓存
        if (cacheDir != null && content.size <= maxDiskCacheSize / 10) {  // 单个文件不超过总大小的10%
            scope.launch {
                try {
                    val diskFile = getDiskCacheFile(key)
                    diskFile.parentFile?.mkdirs()
                    diskFile.writeBytes(content)
                    Log.d(TAG, "写入磁盘缓存: $remotePath (${content.size} bytes)")
                } catch (e: Exception) {
                    Log.e(TAG, "写入磁盘缓存失败: $remotePath", e)
                }
            }
        }
    }
    
    /**
     * 使缓存失效
     */
    fun invalidate(remotePath: String) {
        val key = pathToKey(remotePath)
        
        memoryCache.remove(key)
        metadata.remove(key)
        
        if (cacheDir != null) {
            scope.launch {
                try {
                    val diskFile = getDiskCacheFile(key)
                    if (diskFile.exists()) {
                        diskFile.delete()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "删除磁盘缓存失败: $remotePath", e)
                }
            }
        }
        
        Log.d(TAG, "缓存已失效: $remotePath")
    }
    
    /**
     * 使目录下所有缓存失效
     */
    fun invalidateDirectory(directoryPath: String) {
        val prefix = pathToKey(directoryPath)
        
        val keysToRemove = metadata.keys.filter { it.startsWith(prefix) }
        keysToRemove.forEach { key ->
            memoryCache.remove(key)
            metadata.remove(key)
        }
        
        Log.d(TAG, "目录缓存已失效: $directoryPath (${keysToRemove.size} 项)")
    }
    
    /**
     * 清空所有缓存
     */
    fun clear() {
        memoryCache.evictAll()
        metadata.clear()
        
        if (cacheDir != null) {
            scope.launch {
                try {
                    cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                } catch (e: Exception) {
                    Log.e(TAG, "清空磁盘缓存失败", e)
                }
            }
        }
        
        Log.i(TAG, "缓存已清空")
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getStatistics(): CacheStatistics {
        val memorySize = memoryCache.size()
        val diskSize = calculateDiskCacheSize()
        
        return CacheStatistics(
            memoryEntries = metadata.size,
            memorySizeBytes = memorySize.toLong(),
            diskSizeBytes = diskSize,
            hitCount = memoryCache.hitCount(),
            missCount = memoryCache.missCount()
        )
    }
    
    /**
     * 检查是否有有效缓存
     */
    fun hasValidCache(remotePath: String): Boolean {
        val key = pathToKey(remotePath)
        val meta = metadata[key] ?: return false
        return !meta.isExpired()
    }
    
    /**
     * 刷新缓存TTL
     */
    fun refreshTtl(remotePath: String, newTtlMs: Long = defaultTtlMs) {
        val key = pathToKey(remotePath)
        val meta = metadata[key] ?: return
        
        metadata[key] = meta.copy(
            expiresAt = System.currentTimeMillis() + newTtlMs
        )
    }
    
    /**
     * 关闭缓存
     */
    fun close() {
        cleanupJob?.cancel()
        scope.cancel()
    }
    
    // 私有方法
    
    private fun pathToKey(path: String): String {
        // 使用MD5哈希作为键，避免路径字符问题
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(path.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    private fun getDiskCacheFile(key: String): File {
        // 使用两级目录结构，避免单目录文件过多
        val subDir = key.take(2)
        return File(cacheDir, "$subDir/$key")
    }
    
    private fun calculateDiskCacheSize(): Long {
        return cacheDir?.walkTopDown()
            ?.filter { it.isFile }
            ?.sumOf { it.length() }
            ?: 0L
    }
    
    private fun startCleanupTask() {
        cleanupJob?.cancel()
        cleanupJob = scope.launch {
            while (isActive) {
                delay(CLEANUP_INTERVAL_MS)
                cleanupExpiredEntries()
                cleanupDiskCache()
            }
        }
    }
    
    private fun cleanupExpiredEntries() {
        val now = System.currentTimeMillis()
        val expiredKeys = metadata.filter { (_, meta) -> meta.isExpired() }.keys
        
        expiredKeys.forEach { key ->
            memoryCache.remove(key)
            metadata.remove(key)
        }
        
        if (expiredKeys.isNotEmpty()) {
            Log.d(TAG, "清理了 ${expiredKeys.size} 个过期缓存项")
        }
    }
    
    private suspend fun cleanupDiskCache() {
        if (cacheDir == null) return
        
        val currentSize = calculateDiskCacheSize()
        if (currentSize <= maxDiskCacheSize) return
        
        // 按修改时间排序，删除最旧的文件
        val files = cacheDir.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.lastModified() }
            .toList()
        
        var sizeToFree = currentSize - (maxDiskCacheSize * 0.8).toLong()  // 清理到80%
        
        for (file in files) {
            if (sizeToFree <= 0) break
            
            val fileSize = file.length()
            try {
                file.delete()
                sizeToFree -= fileSize
            } catch (e: Exception) {
                Log.w(TAG, "删除缓存文件失败: ${file.name}", e)
            }
        }
        
        Log.d(TAG, "磁盘缓存清理完成，当前大小: ${calculateDiskCacheSize() / 1024}KB")
    }
}

/**
 * 缓存的文件
 */
data class CachedFile(
    val content: ByteArray,
    val cachedAt: Long,
    val etag: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CachedFile
        return content.contentEquals(other.content) && cachedAt == other.cachedAt && etag == other.etag
    }
    
    override fun hashCode(): Int {
        var result = content.contentHashCode()
        result = 31 * result + cachedAt.hashCode()
        result = 31 * result + (etag?.hashCode() ?: 0)
        return result
    }
}

/**
 * 缓存元数据
 */
data class CacheMetadata(
    val cachedAt: Long,
    val expiresAt: Long,
    val etag: String?,
    val size: Long
) {
    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
}

/**
 * 缓存统计
 */
data class CacheStatistics(
    val memoryEntries: Int,
    val memorySizeBytes: Long,
    val diskSizeBytes: Long,
    val hitCount: Int,
    val missCount: Int
) {
    val hitRate: Float
        get() = if (hitCount + missCount > 0) {
            hitCount.toFloat() / (hitCount + missCount)
        } else {
            0f
        }
    
    val totalSizeBytes: Long
        get() = memorySizeBytes + diskSizeBytes
    
    val formattedMemorySize: String
        get() = formatSize(memorySizeBytes)
    
    val formattedDiskSize: String
        get() = formatSize(diskSizeBytes)
    
    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
