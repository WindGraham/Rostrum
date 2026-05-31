package com.rostrum.core.network

import android.util.Log
import com.jcraft.jsch.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.*

/**
 * SFTP 客户端
 * 
 * 使用 JSch 库实现 SSH/SFTP 协议
 * 支持：
 * - 密码认证
 * - 密钥认证
 * - 文件上传/下载
 * - 目录操作
 * - 文件权限管理
 * 
 * @author OmniMaster
 * @license BSD-2-Clause
 */
class SftpClient {
    
    companion object {
        private const val TAG = "SftpClient"
        // 使用 AppConstants 配置
        private val DEFAULT_PORT = com.rostrum.core.config.AppConstants.Network.SFTP_DEFAULT_PORT
        private val CONNECTION_TIMEOUT = com.rostrum.core.config.AppConstants.Network.SFTP_CONNECTION_TIMEOUT_MS
        private val CHANNEL_TYPE_SFTP = com.rostrum.core.config.AppConstants.Network.SFTP_CHANNEL_TYPE
        private val CHANNEL_TYPE_EXEC = com.rostrum.core.config.AppConstants.Network.SFTP_EXEC_CHANNEL_TYPE
    }
    
    /**
     * SFTP 文件信息
     */
    data class SftpFileInfo(
        val filename: String,
        val path: String,
        val size: Long,
        val modTime: Long,
        val permissions: Int,
        val isDirectory: Boolean,
        val isLink: Boolean,
        val owner: String,
        val group: String
    ) {
        val permissionString: String
            get() {
                val sb = StringBuilder()
                sb.append(if (isDirectory) 'd' else if (isLink) 'l' else '-')
                sb.append(if ((permissions and 256) != 0) 'r' else '-')  // 0400
                sb.append(if ((permissions and 128) != 0) 'w' else '-')  // 0200
                sb.append(if ((permissions and 64) != 0) 'x' else '-')   // 0100
                sb.append(if ((permissions and 32) != 0) 'r' else '-')   // 0040
                sb.append(if ((permissions and 16) != 0) 'w' else '-')   // 0020
                sb.append(if ((permissions and 8) != 0) 'x' else '-')    // 0010
                sb.append(if ((permissions and 4) != 0) 'r' else '-')    // 0004
                sb.append(if ((permissions and 2) != 0) 'w' else '-')    // 0002
                sb.append(if ((permissions and 1) != 0) 'x' else '-')    // 0001
                return sb.toString()
            }
    }
    
    /**
     * 连接配置
     */
    data class ConnectionConfig(
        val host: String,
        val port: Int = DEFAULT_PORT,
        val username: String,
        val password: String? = null,
        val privateKey: ByteArray? = null,
        val privateKeyPassphrase: String? = null,
        val strictHostKeyChecking: Boolean = false
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ConnectionConfig
            return host == other.host && port == other.port && username == other.username
        }
        
        override fun hashCode(): Int {
            var result = host.hashCode()
            result = 31 * result + port
            result = 31 * result + username.hashCode()
            return result
        }
    }
    
    /**
     * 传输进度回调
     */
    interface TransferProgressListener {
        fun onProgress(transferred: Long, total: Long)
        fun onComplete()
        fun onError(message: String)
    }
    
    private val jsch = JSch()
    private var session: Session? = null
    private var sftpChannel: ChannelSftp? = null
    
    val isConnected: Boolean
        get() = session?.isConnected == true && sftpChannel?.isConnected == true
    
    /**
     * 连接到 SFTP 服务器
     */
    suspend fun connect(config: ConnectionConfig) = withContext(Dispatchers.IO) {
        disconnect()
        
        try {
            Log.i(TAG, "连接到 ${config.host}:${config.port}")
            
            // 配置密钥认证
            if (config.privateKey != null) {
                if (config.privateKeyPassphrase != null) {
                    jsch.addIdentity("key", config.privateKey, null, config.privateKeyPassphrase.toByteArray())
                } else {
                    jsch.addIdentity("key", config.privateKey, null, null)
                }
            }
            
            // 创建会话
            session = jsch.getSession(config.username, config.host, config.port).apply {
                // 配置密码认证
                if (config.password != null) {
                    setPassword(config.password)
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
                timeout = CONNECTION_TIMEOUT
                
                // 连接
                connect(CONNECTION_TIMEOUT)
            }
            
            // 打开 SFTP 通道
            sftpChannel = (session?.openChannel(CHANNEL_TYPE_SFTP) as? ChannelSftp)?.apply {
                connect(CONNECTION_TIMEOUT)
            }
            
            Log.i(TAG, "SFTP 连接成功")
            
        } catch (e: JSchException) {
            Log.e(TAG, "SFTP 连接失败", e)
            disconnect()
            throw IOException("SFTP 连接失败: ${e.message}", e)
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        try {
            sftpChannel?.disconnect()
            session?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "断开连接时出错", e)
        } finally {
            sftpChannel = null
            session = null
        }
    }
    
    /**
     * 列出目录内容
     */
    suspend fun listDirectory(path: String): List<SftpFileInfo> = withContext(Dispatchers.IO) {
        val channel = requireChannel()
        
        try {
            @Suppress("UNCHECKED_CAST")
            val entries = channel.ls(path) as Vector<ChannelSftp.LsEntry>
            
            entries.mapNotNull { entry ->
                // 跳过 . 和 ..
                if (entry.filename == "." || entry.filename == "..") {
                    return@mapNotNull null
                }
                
                val attrs = entry.attrs
                SftpFileInfo(
                    filename = entry.filename,
                    path = if (path.endsWith("/")) "$path${entry.filename}" else "$path/${entry.filename}",
                    size = attrs.size,
                    modTime = attrs.mTime.toLong() * 1000,
                    permissions = attrs.permissions,
                    isDirectory = attrs.isDir,
                    isLink = attrs.isLink,
                    owner = "",
                    group = ""
                )
            }
        } catch (e: SftpException) {
            Log.e(TAG, "列出目录失败: $path", e)
            throw IOException("列出目录失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取当前工作目录
     */
    suspend fun pwd(): String = withContext(Dispatchers.IO) {
        requireChannel().pwd()
    }
    
    /**
     * 切换目录
     */
    suspend fun cd(path: String) = withContext(Dispatchers.IO) {
        try {
            requireChannel().cd(path)
        } catch (e: SftpException) {
            Log.e(TAG, "切换目录失败: $path", e)
            throw IOException("切换目录失败: ${e.message}", e)
        }
    }
    
    /**
     * 创建目录
     */
    suspend fun mkdir(path: String) = withContext(Dispatchers.IO) {
        try {
            requireChannel().mkdir(path)
        } catch (e: SftpException) {
            Log.e(TAG, "创建目录失败: $path", e)
            throw IOException("创建目录失败: ${e.message}", e)
        }
    }
    
    /**
     * 删除文件
     */
    suspend fun rm(path: String) = withContext(Dispatchers.IO) {
        try {
            requireChannel().rm(path)
        } catch (e: SftpException) {
            Log.e(TAG, "删除文件失败: $path", e)
            throw IOException("删除文件失败: ${e.message}", e)
        }
    }
    
    /**
     * 删除目录
     */
    suspend fun rmdir(path: String) = withContext(Dispatchers.IO) {
        try {
            requireChannel().rmdir(path)
        } catch (e: SftpException) {
            Log.e(TAG, "删除目录失败: $path", e)
            throw IOException("删除目录失败: ${e.message}", e)
        }
    }
    
    /**
     * 重命名/移动文件
     */
    suspend fun rename(oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        try {
            requireChannel().rename(oldPath, newPath)
        } catch (e: SftpException) {
            Log.e(TAG, "重命名失败: $oldPath -> $newPath", e)
            throw IOException("重命名失败: ${e.message}", e)
        }
    }
    
    /**
     * 修改文件权限
     */
    suspend fun chmod(permissions: Int, path: String) = withContext(Dispatchers.IO) {
        try {
            requireChannel().chmod(permissions, path)
        } catch (e: SftpException) {
            Log.e(TAG, "修改权限失败: $path", e)
            throw IOException("修改权限失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取文件属性
     */
    suspend fun stat(path: String): SftpFileInfo = withContext(Dispatchers.IO) {
        try {
            val attrs = requireChannel().stat(path)
            val filename = path.substringAfterLast("/")
            
            SftpFileInfo(
                filename = filename,
                path = path,
                size = attrs.size,
                modTime = attrs.mTime.toLong() * 1000,
                permissions = attrs.permissions,
                isDirectory = attrs.isDir,
                isLink = attrs.isLink,
                owner = "",
                group = ""
            )
        } catch (e: SftpException) {
            Log.e(TAG, "获取文件属性失败: $path", e)
            throw IOException("获取文件属性失败: ${e.message}", e)
        }
    }
    
    /**
     * 检查文件/目录是否存在
     */
    suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            requireChannel().stat(path)
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
     * 下载文件
     */
    suspend fun download(
        remotePath: String,
        localFile: File,
        progressListener: TransferProgressListener? = null
    ) = withContext(Dispatchers.IO) {
        val channel = requireChannel()
        
        try {
            val fileSize = channel.stat(remotePath).size
            
            FileOutputStream(localFile).use { fos ->
                val monitor = object : SftpProgressMonitor {
                    private var transferred: Long = 0
                    
                    override fun init(op: Int, src: String, dest: String, max: Long) {
                        Log.d(TAG, "开始下载: $src -> $dest, 大小: $max")
                    }
                    
                    override fun count(count: Long): Boolean {
                        transferred += count
                        progressListener?.onProgress(transferred, fileSize)
                        return true
                    }
                    
                    override fun end() {
                        progressListener?.onComplete()
                        Log.d(TAG, "下载完成")
                    }
                }
                
                channel.get(remotePath, fos, monitor)
            }
        } catch (e: SftpException) {
            Log.e(TAG, "下载文件失败: $remotePath", e)
            progressListener?.onError(e.message ?: "下载失败")
            throw IOException("下载文件失败: ${e.message}", e)
        }
    }
    
    /**
     * 上传文件
     */
    suspend fun upload(
        localFile: File,
        remotePath: String,
        progressListener: TransferProgressListener? = null
    ) = withContext(Dispatchers.IO) {
        val channel = requireChannel()
        
        try {
            val fileSize = localFile.length()
            
            FileInputStream(localFile).use { fis ->
                val monitor = object : SftpProgressMonitor {
                    private var transferred: Long = 0
                    
                    override fun init(op: Int, src: String, dest: String, max: Long) {
                        Log.d(TAG, "开始上传: $src -> $dest, 大小: $max")
                    }
                    
                    override fun count(count: Long): Boolean {
                        transferred += count
                        progressListener?.onProgress(transferred, fileSize)
                        return true
                    }
                    
                    override fun end() {
                        progressListener?.onComplete()
                        Log.d(TAG, "上传完成")
                    }
                }
                
                channel.put(fis, remotePath, monitor, ChannelSftp.OVERWRITE)
            }
        } catch (e: SftpException) {
            Log.e(TAG, "上传文件失败: ${localFile.path} -> $remotePath", e)
            progressListener?.onError(e.message ?: "上传失败")
            throw IOException("上传文件失败: ${e.message}", e)
        }
    }
    
    /**
     * 读取文件内容
     */
    suspend fun readFile(remotePath: String): ByteArray = withContext(Dispatchers.IO) {
        val channel = requireChannel()
        
        try {
            ByteArrayOutputStream().use { baos ->
                channel.get(remotePath, baos)
                baos.toByteArray()
            }
        } catch (e: SftpException) {
            Log.e(TAG, "读取文件失败: $remotePath", e)
            throw IOException("读取文件失败: ${e.message}", e)
        }
    }
    
    /**
     * 写入文件内容
     */
    suspend fun writeFile(remotePath: String, content: ByteArray) = withContext(Dispatchers.IO) {
        val channel = requireChannel()
        
        try {
            ByteArrayInputStream(content).use { bais ->
                channel.put(bais, remotePath, ChannelSftp.OVERWRITE)
            }
        } catch (e: SftpException) {
            Log.e(TAG, "写入文件失败: $remotePath", e)
            throw IOException("写入文件失败: ${e.message}", e)
        }
    }
    
    /**
     * 执行远程命令
     */
    suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        val sess = session ?: throw IOException("未连接到服务器")
        
        try {
            val channel = sess.openChannel(CHANNEL_TYPE_EXEC) as ChannelExec
            channel.setCommand(command)
            
            val output = ByteArrayOutputStream()
            val errOutput = ByteArrayOutputStream()
            
            channel.outputStream = output
            channel.setErrStream(errOutput)
            
            channel.connect()
            
            // 等待命令执行完成
            while (!channel.isClosed) {
                Thread.sleep(100)
            }
            
            channel.disconnect()
            
            val result = output.toString("UTF-8")
            val error = errOutput.toString("UTF-8")
            
            if (error.isNotEmpty()) {
                Log.w(TAG, "命令错误输出: $error")
            }
            
            result
        } catch (e: JSchException) {
            Log.e(TAG, "执行命令失败: $command", e)
            throw IOException("执行命令失败: ${e.message}", e)
        }
    }
    
    private fun requireChannel(): ChannelSftp {
        return sftpChannel ?: throw IOException("SFTP 通道未连接")
    }
}

