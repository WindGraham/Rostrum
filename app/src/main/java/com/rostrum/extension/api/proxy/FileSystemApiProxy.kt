package com.rostrum.extension.api.proxy

import android.os.RemoteException
import android.util.Log
// IExtensionHostService will be generated from AIDL
import com.rostrum.core.plugin.ipc.cache.IPCCache
import com.rostrum.core.plugin.ipc.retry.RetryUtils
import com.rostrum.core.plugin.ipc.serialization.FileInfoSerialization
import com.rostrum.extension.api.FileInfo
import com.rostrum.extension.api.FileSystemApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FileSystemApi 的 IPC 代理实现
 * 
 * 将 API 调用转发到主进程的 ExtensionHostService
 * 支持缓存、重试和超时
 */
class FileSystemApiProxy(
    private val service: com.rostrum.core.plugin.ipc.IExtensionHostService,
    private val cache: IPCCache? = null,
    private val enableRetry: Boolean = true,
    private val timeoutMs: Long = 30000L
) : FileSystemApi {
    
    private val TAG = "FileSystemApiProxy"
    
    override suspend fun readFile(uri: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        // 检查缓存
        val cacheKey = cache?.generateKey("readFile", uri)
        val cachedData = cacheKey?.let { key -> cache?.get(key) }
        if (cachedData != null) {
            return@withContext Result.success(cachedData)
        }
        
        // 执行 IPC 调用（带重试和超时）
        val result = if (enableRetry) {
            RetryUtils.executeWithRetryAndTimeout<ByteArray>(
                timeoutMs = timeoutMs
            ) {
                service.readFile(uri)
            }
        } else {
            RetryUtils.executeWithTimeout<ByteArray>(timeoutMs) {
                service.readFile(uri)
            }
        }
        
        result.onSuccess { data ->
            // 缓存结果
            cacheKey?.let { key -> cache?.put(key, data) }
        }
        
        result
    }
    
    override suspend fun writeFile(uri: String, content: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        // 写入操作不使用缓存，但需要清除相关缓存
        val result = if (enableRetry) {
            RetryUtils.executeWithRetryAndTimeout<Unit>(timeoutMs = timeoutMs) {
                service.writeFile(uri, content)
            }
        } else {
            RetryUtils.executeWithTimeout<Unit>(timeoutMs) {
                service.writeFile(uri, content)
            }
        }
        
        // 清除相关缓存
        result.onSuccess {
            val cacheInstance = cache
            if (cacheInstance != null) {
                cacheInstance.remove(cacheInstance.generateKey("readFile", uri))
                cacheInstance.remove(cacheInstance.generateKey("getFileInfo", uri))
                cacheInstance.remove(cacheInstance.generateKey("listDirectory", java.io.File(uri).parent ?: ""))
            }
        }
        
        result
    }
    
    override suspend fun readTextFile(uri: String, encoding: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val content = service.readTextFile(uri, encoding)
            Result.success(content)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to read text file: $uri", e)
            Result.failure(e)
        }
    }
    
    override suspend fun writeTextFile(uri: String, content: String, encoding: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            service.writeTextFile(uri, content, encoding)
            Result.success(Unit)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to write text file: $uri", e)
            Result.failure(e)
        }
    }
    
    override suspend fun exists(uri: String): Boolean = withContext(Dispatchers.IO) {
        // 检查缓存
        val cacheKey = cache?.generateKey("exists", uri)
        val cachedResult = cacheKey?.let { key ->
            cache?.get(key)?.toString(Charsets.UTF_8)?.toBoolean()
        }
        if (cachedResult != null) {
            return@withContext cachedResult
        }
        
        // 执行 IPC 调用
        val result = if (enableRetry) {
            RetryUtils.executeWithRetryAndTimeout<Boolean>(timeoutMs = timeoutMs) {
                service.exists(uri)
            }
        } else {
            RetryUtils.executeWithTimeout<Boolean>(timeoutMs) {
                service.exists(uri)
            }
        }
        
        result.getOrNull()?.let { exists ->
            // 缓存结果（较短的 TTL）
            cacheKey?.let { key ->
                cache?.put(key, exists.toString().toByteArray(Charsets.UTF_8), ttl = 60000L)  // 1 分钟
            }
            exists
        } ?: false
    }
    
    override suspend fun getFileInfo(uri: String): FileInfo? = withContext(Dispatchers.IO) {
        // 检查缓存
        val cacheKey = cache?.generateKey("getFileInfo", uri)
        val cachedJson = cacheKey?.let { key ->
            cache?.get(key)?.toString(Charsets.UTF_8)
        }
        if (cachedJson != null) {
            return@withContext FileInfoSerialization.deserialize(cachedJson)
        }
        
        // 执行 IPC 调用
        val result = if (enableRetry) {
            RetryUtils.executeWithRetryAndTimeout<String>(timeoutMs = timeoutMs) {
                service.getFileInfo(uri)
            }
        } else {
            RetryUtils.executeWithTimeout<String>(timeoutMs) {
                service.getFileInfo(uri)
            }
        }
        
        result.getOrNull()?.let { json ->
            val fileInfo = FileInfoSerialization.deserialize(json)
            // 缓存结果
            fileInfo?.let {
                cacheKey?.let { key ->
                    cache?.put(key, json.toByteArray(Charsets.UTF_8))
                }
            }
            fileInfo
        }
    }
    
    override suspend fun listDirectory(uri: String): Result<List<FileInfo>> = withContext(Dispatchers.IO) {
        // 检查缓存
        val cacheKey = cache?.generateKey("listDirectory", uri)
        val cachedJson = cacheKey?.let { key ->
            cache?.get(key)?.toString(Charsets.UTF_8)
        }
        if (cachedJson != null) {
            return@withContext Result.success(FileInfoSerialization.deserializeList(cachedJson))
        }
        
        // 执行 IPC 调用
        val result = if (enableRetry) {
            RetryUtils.executeWithRetryAndTimeout<String>(timeoutMs = timeoutMs) {
                service.listDirectory(uri)
            }
        } else {
            RetryUtils.executeWithTimeout<String>(timeoutMs) {
                service.listDirectory(uri)
            }
        }
        
        result.map { json ->
            val files = FileInfoSerialization.deserializeList(json)
            // 缓存结果
            cacheKey?.let { key ->
                cache?.put(key, json.toByteArray(Charsets.UTF_8))
            }
            files
        }
    }
    
    override suspend fun createDirectory(uri: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            service.createDirectory(uri)
            Result.success(Unit)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to create directory: $uri", e)
            Result.failure(e)
        }
    }
    
    override suspend fun delete(uri: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            service.delete(uri)
            Result.success(Unit)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to delete: $uri", e)
            Result.failure(e)
        }
    }
    
    override suspend fun copy(sourceUri: String, targetUri: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            service.copy(sourceUri, targetUri)
            Result.success(Unit)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to copy: $sourceUri -> $targetUri", e)
            Result.failure(e)
        }
    }
    
    override suspend fun move(sourceUri: String, targetUri: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            service.move(sourceUri, targetUri)
            Result.success(Unit)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to move: $sourceUri -> $targetUri", e)
            Result.failure(e)
        }
    }
}

