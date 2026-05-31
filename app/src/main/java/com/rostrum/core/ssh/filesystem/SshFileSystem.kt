package com.rostrum.core.ssh.filesystem

import android.util.Log
import android.webkit.MimeTypeMap
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpException
import com.rostrum.core.filesystem.FileSystemService
import com.rostrum.core.plugin.models.FileInfo
import com.rostrum.core.ssh.connection.ISshConnection
import com.rostrum.core.ssh.session.RemoteFileInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Vector

/**
 * SSH 文件系统
 * 
 * 实现 FileSystemService 接口，提供远程文件操作功能
 * 与本地文件系统API统一
 * 
 * @author OmniMaster
 * @license BSD-2-Clause
 */
class SshFileSystem(
    private val connection: ISshConnection,
    private val cache: SshFileCache? = null
) : FileSystemService {
    
    companion object {
        private const val TAG = "SshFileSystem"
        private const val CONNECT_TIMEOUT = 10_000
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 500L
    }
    
    private var channel: ChannelSftp? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 互斥锁，保护 SFTP 通道操作（SFTP 通道不支持并发访问）
    private val channelMutex = Mutex()
    
    // 文件变更事件
    private val _fileEvents = MutableSharedFlow<FileEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val fileEvents: Flow<FileEvent> = _fileEvents.asSharedFlow()
    
    /**
     * 确保SFTP通道已连接（带重试逻辑）
     */
    private suspend fun ensureConnected(): ChannelSftp {
        val ch = channel
        
        // 检查现有通道是否可用
        if (ch != null && ch.isConnected) {
            return ch
        }
        
        // 关闭旧的失效通道
        try {
            channel?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "关闭旧通道时出错", e)
        }
        channel = null
        
        var lastException: Exception? = null
        
        // 重试连接
        for (attempt in 1..MAX_RETRY_COUNT) {
            Log.d(TAG, "尝试连接SFTP通道 (第 $attempt 次)")
            
            try {
                // 首先检查SSH连接是否有效
                if (!connection.isValid()) {
                    Log.w(TAG, "SSH连接已失效，尝试重新连接")
                    val reconnectResult = connection.connect()
                    if (reconnectResult.isFailure) {
                        lastException = reconnectResult.exceptionOrNull() as? Exception
                            ?: IOException("SSH重连失败")
                        Log.e(TAG, "SSH重连失败", lastException)
                        if (attempt < MAX_RETRY_COUNT) {
                            delay(RETRY_DELAY_MS * attempt)
                        }
                        continue
                    }
                }
                
                val result = connection.openSftpChannel()
                if (result.isFailure) {
                    lastException = result.exceptionOrNull() as? Exception
                        ?: IOException("无法打开SFTP通道")
                    Log.e(TAG, "打开SFTP通道失败 (第 $attempt 次)", lastException)
                    if (attempt < MAX_RETRY_COUNT) {
                        delay(RETRY_DELAY_MS * attempt)
                    }
                    continue
                }
                
                val newChannel = result.getOrThrow().apply {
                    connect(CONNECT_TIMEOUT)
                }
                Log.d(TAG, "SFTP通道已连接 (第 $attempt 次尝试成功)")
                channel = newChannel
                return newChannel
                
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "连接SFTP通道异常 (第 $attempt 次)", e)
                if (attempt < MAX_RETRY_COUNT) {
                    delay(RETRY_DELAY_MS * attempt)
                }
            }
        }
        
        throw lastException ?: IOException("无法连接SFTP通道，已重试 $MAX_RETRY_COUNT 次")
    }
    
    override suspend fun readFile(uri: String): Result<ByteArray> = channelMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val path = uriToPath(uri)
                
                // 检查缓存
                cache?.get(path)?.let { cached ->
                    Log.d(TAG, "从缓存读取: $path")
                    return@withContext Result.success(cached.content)
                }
                
                val ch = ensureConnected()
                val output = ByteArrayOutputStream()
                ch.get(path, output)
                
                val content = output.toByteArray()
                connection.touch()
                
                // 写入缓存
                cache?.put(path, content)
                
                Result.success(content)
                
            } catch (e: SftpException) {
                Log.e(TAG, "读取文件失败: $uri", e)
                Result.failure(IOException("读取文件失败: ${e.message}", e))
            } catch (e: Exception) {
                Log.e(TAG, "读取文件异常: $uri", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun writeFile(uri: String, content: ByteArray): Result<Unit> = channelMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val path = uriToPath(uri)
                val ch = ensureConnected()
                
                ByteArrayInputStream(content).use { input ->
                    ch.put(input, path, ChannelSftp.OVERWRITE)
                }
                
                connection.touch()
                
                // 更新缓存
                cache?.put(path, content)
                
                // 发送事件
                _fileEvents.emit(FileEvent.Modified(path))
                
                Result.success(Unit)
                
            } catch (e: SftpException) {
                Log.e(TAG, "写入文件失败: $uri", e)
                Result.failure(IOException("写入文件失败: ${e.message}", e))
            } catch (e: Exception) {
                Log.e(TAG, "写入文件异常: $uri", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun readTextFile(uri: String, encoding: String): Result<String> {
        return readFile(uri).map { bytes ->
            String(bytes, charset(encoding))
        }
    }
    
    override suspend fun writeTextFile(uri: String, content: String, encoding: String): Result<Unit> {
        return writeFile(uri, content.toByteArray(charset(encoding)))
    }
    
    override suspend fun exists(uri: String): Boolean = channelMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val path = uriToPath(uri)
                val ch = ensureConnected()
                ch.stat(path)
                true
            } catch (e: SftpException) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    false
                } else {
                    Log.e(TAG, "检查文件存在性失败: $uri", e)
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查文件存在性异常: $uri", e)
                false
            }
        }
    }
    
    override suspend fun getFileInfo(uri: String): FileInfo? = channelMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val path = uriToPath(uri)
                val ch = ensureConnected()
                val attrs = ch.stat(path)
                
                val name = path.substringAfterLast("/")
                val extension = if (attrs.isDir) "" else {
                    val dotIndex = name.lastIndexOf('.')
                    if (dotIndex >= 0) name.substring(dotIndex) else ""
                }
                
                FileInfo(
                    path = path,
                    name = name,
                    extension = extension,
                    size = attrs.size,
                    lastModified = attrs.mTime.toLong() * 1000,
                    isDirectory = attrs.isDir,
                    permissions = formatPermissions(attrs.permissions, attrs.isDir),
                    mimeType = if (attrs.isDir) null else getMimeType(extension)
                )
                
            } catch (e: SftpException) {
                Log.e(TAG, "获取文件信息失败: $uri", e)
                null
            } catch (e: Exception) {
                Log.e(TAG, "获取文件信息异常: $uri", e)
                null
            }
        }
    }
    
    override suspend fun listDirectory(uri: String): Result<List<FileInfo>> = channelMutex.withLock {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "listDirectory: uri=$uri")
            try {
                val path = uriToPath(uri)
                
                val ch = ensureConnected()
                
                @Suppress("UNCHECKED_CAST")
                val entries = ch.ls(path) as Vector<ChannelSftp.LsEntry>
                Log.d(TAG, "获取到 ${entries.size} 个条目")
                
                val files = entries.mapNotNull { entry ->
                    if (entry.filename == "." || entry.filename == "..") {
                        return@mapNotNull null
                    }
                    
                    val attrs = entry.attrs
                    val filePath = if (path.endsWith("/")) {
                        "$path${entry.filename}"
                    } else {
                        "$path/${entry.filename}"
                    }
                    
                    val extension = if (attrs.isDir) "" else {
                        val dotIndex = entry.filename.lastIndexOf('.')
                        if (dotIndex >= 0) entry.filename.substring(dotIndex) else ""
                    }
                    
                    FileInfo(
                        path = filePath,
                        name = entry.filename,
                        extension = extension,
                        size = attrs.size,
                        lastModified = attrs.mTime.toLong() * 1000,
                        isDirectory = attrs.isDir,
                        permissions = formatPermissions(attrs.permissions, attrs.isDir),
                        mimeType = if (attrs.isDir) null else getMimeType(extension)
                    )
                }
                
                connection.touch()
                Result.success(files)
                
            } catch (e: SftpException) {
                Log.e(TAG, "列出目录失败: $uri", e)
                Result.failure(IOException("列出目录失败: ${e.message}", e))
            } catch (e: Exception) {
                Log.e(TAG, "列出目录异常: $uri", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun createDirectory(uri: String): Result<Unit> = channelMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val path = uriToPath(uri)
                val ch = ensureConnected()
                ch.mkdir(path)
                
                connection.touch()
                _fileEvents.emit(FileEvent.Created(path))
                
                Result.success(Unit)
                
            } catch (e: SftpException) {
                Log.e(TAG, "创建目录失败: $uri", e)
                Result.failure(IOException("创建目录失败: ${e.message}", e))
            } catch (e: Exception) {
                Log.e(TAG, "创建目录异常: $uri", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun delete(uri: String): Result<Unit> = channelMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val path = uriToPath(uri)
                val ch = ensureConnected()
                
                // 判断是文件还是目录
                val attrs = ch.stat(path)
                if (attrs.isDir) {
                    deleteDirectoryRecursive(ch, path)
                } else {
                    ch.rm(path)
                }
                
                connection.touch()
                cache?.invalidate(path)
                _fileEvents.emit(FileEvent.Deleted(path))
                
                Result.success(Unit)
                
            } catch (e: SftpException) {
                Log.e(TAG, "删除失败: $uri", e)
                Result.failure(IOException("删除失败: ${e.message}", e))
            } catch (e: Exception) {
                Log.e(TAG, "删除异常: $uri", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun copy(sourceUri: String, targetUri: String): Result<Unit> = channelMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val sourcePath = uriToPath(sourceUri)
                val targetPath = uriToPath(targetUri)
                val ch = ensureConnected()
                
                // SFTP 没有原生的复制命令，需要先下载再上传
                // 或者使用远程命令
                val attrs = ch.stat(sourcePath)
                
                if (attrs.isDir) {
                    copyDirectoryRecursive(ch, sourcePath, targetPath)
                } else {
                    // 对于文件，下载再上传
                    val output = ByteArrayOutputStream()
                    ch.get(sourcePath, output)
                    val content = output.toByteArray()
                    
                    ByteArrayInputStream(content).use { input ->
                        ch.put(input, targetPath, ChannelSftp.OVERWRITE)
                    }
                }
                
                connection.touch()
                _fileEvents.emit(FileEvent.Created(targetPath))
                
                Result.success(Unit)
                
            } catch (e: SftpException) {
                Log.e(TAG, "复制失败: $sourceUri -> $targetUri", e)
                Result.failure(IOException("复制失败: ${e.message}", e))
            } catch (e: Exception) {
                Log.e(TAG, "复制异常: $sourceUri -> $targetUri", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun move(sourceUri: String, targetUri: String): Result<Unit> = channelMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val sourcePath = uriToPath(sourceUri)
                val targetPath = uriToPath(targetUri)
                val ch = ensureConnected()
                
                ch.rename(sourcePath, targetPath)
                
                connection.touch()
                cache?.invalidate(sourcePath)
                _fileEvents.emit(FileEvent.Deleted(sourcePath))
                _fileEvents.emit(FileEvent.Created(targetPath))
                
                Result.success(Unit)
                
            } catch (e: SftpException) {
                Log.e(TAG, "移动失败: $sourceUri -> $targetUri", e)
                Result.failure(IOException("移动失败: ${e.message}", e))
            } catch (e: Exception) {
                Log.e(TAG, "移动异常: $sourceUri -> $targetUri", e)
                Result.failure(e)
            }
        }
    }
    
    // SSH 特有功能
    
    /**
     * 修改文件权限
     */
    suspend fun chmod(path: String, permissions: Int): Result<Unit> = channelMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val ch = ensureConnected()
                ch.chmod(permissions, path)
                connection.touch()
                Result.success(Unit)
            } catch (e: SftpException) {
                Result.failure(IOException("修改权限失败: ${e.message}", e))
            }
        }
    }
    
    /**
     * 创建符号链接
     */
    suspend fun symlink(targetPath: String, linkPath: String): Result<Unit> = channelMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val ch = ensureConnected()
                ch.symlink(targetPath, linkPath)
                connection.touch()
                Result.success(Unit)
            } catch (e: SftpException) {
                Result.failure(IOException("创建符号链接失败: ${e.message}", e))
            }
        }
    }
    
    /**
     * 获取当前工作目录
     */
    suspend fun pwd(): Result<String> = channelMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val ch = ensureConnected()
                Result.success(ch.pwd())
            } catch (e: SftpException) {
                Result.failure(IOException("获取工作目录失败: ${e.message}", e))
            }
        }
    }
    
    /**
     * 切换工作目录
     */
    suspend fun cd(path: String): Result<Unit> = channelMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val ch = ensureConnected()
                ch.cd(path)
                connection.touch()
                Result.success(Unit)
            } catch (e: SftpException) {
                Result.failure(IOException("切换目录失败: ${e.message}", e))
            }
        }
    }
    
    /**
     * 关闭文件系统
     */
    fun close() {
        Log.d(TAG, "关闭SshFileSystem")
        try {
            channel?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "关闭SFTP通道时出错", e)
        } finally {
            channel = null
            scope.cancel()
        }
    }
    
    // 私有辅助方法
    
    private fun uriToPath(uri: String): String {
        // 处理 sftp:// 前缀
        return if (uri.startsWith("sftp://")) {
            val pathStart = uri.indexOf('/', 7)  // 跳过 sftp://host
            if (pathStart >= 0) uri.substring(pathStart) else "/"
        } else {
            uri
        }
    }
    
    private fun formatPermissions(permissions: Int, isDirectory: Boolean): String {
        val sb = StringBuilder()
        sb.append(if (isDirectory) 'd' else '-')
        sb.append(if ((permissions and 256) != 0) 'r' else '-')
        sb.append(if ((permissions and 128) != 0) 'w' else '-')
        sb.append(if ((permissions and 64) != 0) 'x' else '-')
        sb.append(if ((permissions and 32) != 0) 'r' else '-')
        sb.append(if ((permissions and 16) != 0) 'w' else '-')
        sb.append(if ((permissions and 8) != 0) 'x' else '-')
        sb.append(if ((permissions and 4) != 0) 'r' else '-')
        sb.append(if ((permissions and 2) != 0) 'w' else '-')
        sb.append(if ((permissions and 1) != 0) 'x' else '-')
        return sb.toString()
    }
    
    private fun getMimeType(extension: String): String? {
        if (extension.isEmpty()) return null
        val ext = extension.removePrefix(".")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }
    
    private suspend fun deleteDirectoryRecursive(channel: ChannelSftp, path: String) {
        @Suppress("UNCHECKED_CAST")
        val entries = channel.ls(path) as Vector<ChannelSftp.LsEntry>
        
        for (entry in entries) {
            if (entry.filename == "." || entry.filename == "..") continue
            
            val entryPath = "$path/${entry.filename}"
            if (entry.attrs.isDir) {
                deleteDirectoryRecursive(channel, entryPath)
            } else {
                channel.rm(entryPath)
            }
        }
        
        channel.rmdir(path)
    }
    
    private suspend fun copyDirectoryRecursive(
        channel: ChannelSftp,
        sourcePath: String,
        targetPath: String
    ) {
        // 创建目标目录
        try {
            channel.mkdir(targetPath)
        } catch (e: SftpException) {
            // 目录可能已存在
        }
        
        @Suppress("UNCHECKED_CAST")
        val entries = channel.ls(sourcePath) as Vector<ChannelSftp.LsEntry>
        
        for (entry in entries) {
            if (entry.filename == "." || entry.filename == "..") continue
            
            val sourceEntryPath = "$sourcePath/${entry.filename}"
            val targetEntryPath = "$targetPath/${entry.filename}"
            
            if (entry.attrs.isDir) {
                copyDirectoryRecursive(channel, sourceEntryPath, targetEntryPath)
            } else {
                val output = ByteArrayOutputStream()
                channel.get(sourceEntryPath, output)
                val content = output.toByteArray()
                
                ByteArrayInputStream(content).use { input ->
                    channel.put(input, targetEntryPath, ChannelSftp.OVERWRITE)
                }
            }
        }
    }
}

/**
 * 文件事件
 */
sealed class FileEvent {
    abstract val path: String
    
    data class Created(override val path: String) : FileEvent()
    data class Modified(override val path: String) : FileEvent()
    data class Deleted(override val path: String) : FileEvent()
}
