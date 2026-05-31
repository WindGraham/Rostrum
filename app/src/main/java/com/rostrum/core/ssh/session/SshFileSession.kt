package com.rostrum.core.ssh.session

import android.content.Context
import android.net.Uri
import android.util.Log
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpException
import com.jcraft.jsch.SftpProgressMonitor
import com.rostrum.core.ssh.connection.ISshConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.util.UUID
import java.util.Vector

/**
 * SSH 文件传输会话
 * 
 * 提供文件上传、下载和远程文件操作功能
 * 
 * @author OmniMaster
 * @license BSD-2-Clause
 */
class SshFileSession(
    override val connection: ISshConnection
) : ISshSession {
    
    companion object {
        private const val TAG = "SshFileSession"
        private const val CONNECT_TIMEOUT = 10_000
    }
    
    override val id: String = UUID.randomUUID().toString()
    
    private var channel: ChannelSftp? = null
    
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    override val state: StateFlow<SessionState> = _state.asStateFlow()
    
    override val isActive: Boolean
        get() = _state.value.isRunning && channel?.isConnected == true
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override suspend fun start(): Result<Unit> = withContext(Dispatchers.IO) {
        if (_state.value.isRunning) {
            return@withContext Result.success(Unit)
        }
        
        _state.value = SessionState.Starting
        
        try {
            val channelResult = connection.openSftpChannel()
            if (channelResult.isFailure) {
                val error = channelResult.exceptionOrNull()!!
                _state.value = SessionState.Error("无法打开SFTP通道", error)
                return@withContext Result.failure(error)
            }
            
            channel = channelResult.getOrThrow().apply {
                connect(CONNECT_TIMEOUT)
            }
            
            _state.value = SessionState.Running
            Log.i(TAG, "SFTP会话已启动: $id")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "启动SFTP会话失败", e)
            _state.value = SessionState.Error("启动失败: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun closeAsync() {
        closeInternal()
    }
    
    override fun close() {
        closeInternal()
    }
    
    private fun closeInternal() {
        if (_state.value.isClosed) return
        
        Log.i(TAG, "关闭SFTP会话: $id")
        
        try {
            channel?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "关闭会话时出错", e)
        } finally {
            channel = null
            _state.value = SessionState.Closed
            scope.cancel()
        }
    }
    
    /**
     * 上传文件
     * 
     * @param localFile 本地文件
     * @param remotePath 远程路径
     * @param onProgress 进度回调
     */
    suspend fun upload(
        localFile: File,
        remotePath: String,
        onProgress: (TransferProgress) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ensureConnected()
        val ch = channel ?: return@withContext Result.failure(IllegalStateException("未连接"))
        
        try {
            val fileSize = localFile.length()
            var lastUpdate = System.currentTimeMillis()
            var lastTransferred = 0L
            
            val monitor = object : SftpProgressMonitor {
                var transferred = 0L
                
                override fun init(op: Int, src: String, dest: String, max: Long) {
                    Log.d(TAG, "开始上传: $src -> $dest, 大小: $max")
                    onProgress(TransferProgress(0, fileSize, 0))
                }
                
                override fun count(count: Long): Boolean {
                    transferred += count
                    val now = System.currentTimeMillis()
                    val elapsed = now - lastUpdate
                    
                    if (elapsed >= 100) { // 每100ms更新一次
                        val speed = if (elapsed > 0) {
                            ((transferred - lastTransferred) * 1000 / elapsed)
                        } else 0L
                        
                        onProgress(TransferProgress(transferred, fileSize, speed))
                        lastUpdate = now
                        lastTransferred = transferred
                    }
                    return true
                }
                
                override fun end() {
                    onProgress(TransferProgress(fileSize, fileSize, 0, completed = true))
                    Log.d(TAG, "上传完成")
                }
            }
            
            FileInputStream(localFile).use { fis ->
                ch.put(fis, remotePath, monitor, ChannelSftp.OVERWRITE)
            }
            
            connection.touch()
            Result.success(Unit)
            
        } catch (e: SftpException) {
            Log.e(TAG, "上传文件失败", e)
            Result.failure(IOException("上传失败: ${e.message}", e))
        }
    }
    
    /**
     * 上传文件（从Uri）
     */
    suspend fun upload(
        context: Context,
        localUri: Uri,
        remotePath: String,
        onProgress: (TransferProgress) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ensureConnected()
        val ch = channel ?: return@withContext Result.failure(IllegalStateException("未连接"))
        
        try {
            val contentResolver = context.contentResolver
            val fileSize = contentResolver.openFileDescriptor(localUri, "r")?.use { 
                it.statSize 
            } ?: 0L
            
            var lastUpdate = System.currentTimeMillis()
            var lastTransferred = 0L
            
            val monitor = object : SftpProgressMonitor {
                var transferred = 0L
                
                override fun init(op: Int, src: String, dest: String, max: Long) {
                    onProgress(TransferProgress(0, fileSize, 0))
                }
                
                override fun count(count: Long): Boolean {
                    transferred += count
                    val now = System.currentTimeMillis()
                    val elapsed = now - lastUpdate
                    
                    if (elapsed >= 100) {
                        val speed = if (elapsed > 0) {
                            ((transferred - lastTransferred) * 1000 / elapsed)
                        } else 0L
                        
                        onProgress(TransferProgress(transferred, fileSize, speed))
                        lastUpdate = now
                        lastTransferred = transferred
                    }
                    return true
                }
                
                override fun end() {
                    onProgress(TransferProgress(fileSize, fileSize, 0, completed = true))
                }
            }
            
            contentResolver.openInputStream(localUri)?.use { inputStream ->
                ch.put(inputStream, remotePath, monitor, ChannelSftp.OVERWRITE)
            } ?: return@withContext Result.failure(IOException("无法打开文件"))
            
            connection.touch()
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "上传文件失败", e)
            Result.failure(IOException("上传失败: ${e.message}", e))
        }
    }
    
    /**
     * 下载文件
     * 
     * @param remotePath 远程路径
     * @param localFile 本地文件
     * @param onProgress 进度回调
     */
    suspend fun download(
        remotePath: String,
        localFile: File,
        onProgress: (TransferProgress) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ensureConnected()
        val ch = channel ?: return@withContext Result.failure(IllegalStateException("未连接"))
        
        try {
            val fileSize = ch.stat(remotePath).size
            var lastUpdate = System.currentTimeMillis()
            var lastTransferred = 0L
            
            val monitor = object : SftpProgressMonitor {
                var transferred = 0L
                
                override fun init(op: Int, src: String, dest: String, max: Long) {
                    Log.d(TAG, "开始下载: $src -> $dest, 大小: $max")
                    onProgress(TransferProgress(0, fileSize, 0))
                }
                
                override fun count(count: Long): Boolean {
                    transferred += count
                    val now = System.currentTimeMillis()
                    val elapsed = now - lastUpdate
                    
                    if (elapsed >= 100) {
                        val speed = if (elapsed > 0) {
                            ((transferred - lastTransferred) * 1000 / elapsed)
                        } else 0L
                        
                        onProgress(TransferProgress(transferred, fileSize, speed))
                        lastUpdate = now
                        lastTransferred = transferred
                    }
                    return true
                }
                
                override fun end() {
                    onProgress(TransferProgress(fileSize, fileSize, 0, completed = true))
                    Log.d(TAG, "下载完成")
                }
            }
            
            // 确保父目录存在
            localFile.parentFile?.mkdirs()
            
            FileOutputStream(localFile).use { fos ->
                ch.get(remotePath, fos, monitor)
            }
            
            connection.touch()
            Result.success(Unit)
            
        } catch (e: SftpException) {
            Log.e(TAG, "下载文件失败", e)
            Result.failure(IOException("下载失败: ${e.message}", e))
        }
    }
    
    /**
     * 下载文件（到Uri）
     */
    suspend fun download(
        context: Context,
        remotePath: String,
        localUri: Uri,
        onProgress: (TransferProgress) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ensureConnected()
        val ch = channel ?: return@withContext Result.failure(IllegalStateException("未连接"))
        
        try {
            val fileSize = ch.stat(remotePath).size
            var lastUpdate = System.currentTimeMillis()
            var lastTransferred = 0L
            
            val monitor = object : SftpProgressMonitor {
                var transferred = 0L
                
                override fun init(op: Int, src: String, dest: String, max: Long) {
                    onProgress(TransferProgress(0, fileSize, 0))
                }
                
                override fun count(count: Long): Boolean {
                    transferred += count
                    val now = System.currentTimeMillis()
                    val elapsed = now - lastUpdate
                    
                    if (elapsed >= 100) {
                        val speed = if (elapsed > 0) {
                            ((transferred - lastTransferred) * 1000 / elapsed)
                        } else 0L
                        
                        onProgress(TransferProgress(transferred, fileSize, speed))
                        lastUpdate = now
                        lastTransferred = transferred
                    }
                    return true
                }
                
                override fun end() {
                    onProgress(TransferProgress(fileSize, fileSize, 0, completed = true))
                }
            }
            
            context.contentResolver.openOutputStream(localUri)?.use { outputStream ->
                ch.get(remotePath, outputStream, monitor)
            } ?: return@withContext Result.failure(IOException("无法打开输出流"))
            
            connection.touch()
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "下载文件失败", e)
            Result.failure(IOException("下载失败: ${e.message}", e))
        }
    }
    
    /**
     * 列出目录内容
     */
    suspend fun listDirectory(path: String): Result<List<RemoteFileInfo>> = withContext(Dispatchers.IO) {
        ensureConnected()
        val ch = channel ?: return@withContext Result.failure(IllegalStateException("未连接"))
        
        try {
            @Suppress("UNCHECKED_CAST")
            val entries = ch.ls(path) as Vector<ChannelSftp.LsEntry>
            
            val files = entries.mapNotNull { entry ->
                if (entry.filename == "." || entry.filename == "..") {
                    return@mapNotNull null
                }
                
                val attrs = entry.attrs
                RemoteFileInfo(
                    name = entry.filename,
                    path = if (path.endsWith("/")) "$path${entry.filename}" else "$path/${entry.filename}",
                    size = attrs.size,
                    modifiedTime = attrs.mTime.toLong() * 1000,
                    permissions = attrs.permissions,
                    isDirectory = attrs.isDir,
                    isLink = attrs.isLink
                )
            }
            
            connection.touch()
            Result.success(files)
            
        } catch (e: SftpException) {
            Log.e(TAG, "列出目录失败: $path", e)
            Result.failure(IOException("列出目录失败: ${e.message}", e))
        }
    }
    
    /**
     * 获取文件信息
     */
    suspend fun stat(path: String): Result<RemoteFileInfo> = withContext(Dispatchers.IO) {
        ensureConnected()
        val ch = channel ?: return@withContext Result.failure(IllegalStateException("未连接"))
        
        try {
            val attrs = ch.stat(path)
            val filename = path.substringAfterLast("/")
            
            Result.success(RemoteFileInfo(
                name = filename,
                path = path,
                size = attrs.size,
                modifiedTime = attrs.mTime.toLong() * 1000,
                permissions = attrs.permissions,
                isDirectory = attrs.isDir,
                isLink = attrs.isLink
            ))
            
        } catch (e: SftpException) {
            Log.e(TAG, "获取文件信息失败: $path", e)
            Result.failure(IOException("获取文件信息失败: ${e.message}", e))
        }
    }
    
    /**
     * 检查文件是否存在
     */
    suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        ensureConnected()
        val ch = channel ?: return@withContext false
        
        try {
            ch.stat(path)
            true
        } catch (e: SftpException) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                false
            } else {
                throw IOException("检查文件存在性失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 创建目录
     */
    suspend fun mkdir(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        ensureConnected()
        val ch = channel ?: return@withContext Result.failure(IllegalStateException("未连接"))
        
        try {
            ch.mkdir(path)
            connection.touch()
            Result.success(Unit)
        } catch (e: SftpException) {
            Result.failure(IOException("创建目录失败: ${e.message}", e))
        }
    }
    
    /**
     * 删除文件
     */
    suspend fun rm(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        ensureConnected()
        val ch = channel ?: return@withContext Result.failure(IllegalStateException("未连接"))
        
        try {
            ch.rm(path)
            connection.touch()
            Result.success(Unit)
        } catch (e: SftpException) {
            Result.failure(IOException("删除文件失败: ${e.message}", e))
        }
    }
    
    /**
     * 删除目录
     */
    suspend fun rmdir(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        ensureConnected()
        val ch = channel ?: return@withContext Result.failure(IllegalStateException("未连接"))
        
        try {
            ch.rmdir(path)
            connection.touch()
            Result.success(Unit)
        } catch (e: SftpException) {
            Result.failure(IOException("删除目录失败: ${e.message}", e))
        }
    }
    
    /**
     * 重命名/移动
     */
    suspend fun rename(oldPath: String, newPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        ensureConnected()
        val ch = channel ?: return@withContext Result.failure(IllegalStateException("未连接"))
        
        try {
            ch.rename(oldPath, newPath)
            connection.touch()
            Result.success(Unit)
        } catch (e: SftpException) {
            Result.failure(IOException("重命名失败: ${e.message}", e))
        }
    }
    
    /**
     * 修改权限
     */
    suspend fun chmod(permissions: Int, path: String): Result<Unit> = withContext(Dispatchers.IO) {
        ensureConnected()
        val ch = channel ?: return@withContext Result.failure(IllegalStateException("未连接"))
        
        try {
            ch.chmod(permissions, path)
            connection.touch()
            Result.success(Unit)
        } catch (e: SftpException) {
            Result.failure(IOException("修改权限失败: ${e.message}", e))
        }
    }
    
    /**
     * 获取当前工作目录
     */
    suspend fun pwd(): Result<String> = withContext(Dispatchers.IO) {
        ensureConnected()
        val ch = channel ?: return@withContext Result.failure(IllegalStateException("未连接"))
        
        try {
            Result.success(ch.pwd())
        } catch (e: SftpException) {
            Result.failure(IOException("获取工作目录失败: ${e.message}", e))
        }
    }
    
    /**
     * 切换目录
     */
    suspend fun cd(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        ensureConnected()
        val ch = channel ?: return@withContext Result.failure(IllegalStateException("未连接"))
        
        try {
            ch.cd(path)
            connection.touch()
            Result.success(Unit)
        } catch (e: SftpException) {
            Result.failure(IOException("切换目录失败: ${e.message}", e))
        }
    }
    
    private suspend fun ensureConnected() {
        if (!isActive) {
            start()
        }
    }
}

/**
 * 传输进度
 */
data class TransferProgress(
    val transferred: Long,
    val total: Long,
    val speedBps: Long,
    val completed: Boolean = false
) {
    /**
     * 完成百分比 (0-100)
     */
    val percentage: Int
        get() = if (total > 0) ((transferred * 100) / total).toInt() else 0
    
    /**
     * 剩余时间（秒）
     */
    val remainingSeconds: Long
        get() = if (speedBps > 0) (total - transferred) / speedBps else 0
    
    /**
     * 格式化速度
     */
    val formattedSpeed: String
        get() = when {
            speedBps >= 1024 * 1024 -> String.format("%.1f MB/s", speedBps / (1024.0 * 1024.0))
            speedBps >= 1024 -> String.format("%.1f KB/s", speedBps / 1024.0)
            else -> "$speedBps B/s"
        }
}

/**
 * 远程文件信息
 */
data class RemoteFileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val modifiedTime: Long,
    val permissions: Int,
    val isDirectory: Boolean,
    val isLink: Boolean
) {
    /**
     * 权限字符串
     */
    val permissionString: String
        get() {
            val sb = StringBuilder()
            sb.append(if (isDirectory) 'd' else if (isLink) 'l' else '-')
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
    
    /**
     * 格式化大小
     */
    val formattedSize: String
        get() = when {
            isDirectory -> "-"
            size >= 1024 * 1024 * 1024 -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
            size >= 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            size >= 1024 -> String.format("%.1f KB", size / 1024.0)
            else -> "$size B"
        }
}
