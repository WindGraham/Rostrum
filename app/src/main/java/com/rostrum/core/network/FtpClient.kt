package com.rostrum.core.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * FTP 客户端
 * 
 * 使用 Apache Commons Net 库实现 FTP 协议
 * 支持：
 * - 匿名登录
 * - 用户名密码认证
 * - 主动/被动模式
 * - ASCII/二进制传输
 * - 断点续传
 * 
 * @author OmniMaster
 * @license Apache-2.0
 */
class FtpClient {
    
    companion object {
        private const val TAG = "FtpClient"
        // 使用 AppConstants 配置
        private val DEFAULT_PORT = com.rostrum.core.config.AppConstants.Network.FTP_DEFAULT_PORT
        private val CONNECTION_TIMEOUT = com.rostrum.core.config.AppConstants.Network.FTP_CONNECTION_TIMEOUT_MS
        private val DATA_TIMEOUT = com.rostrum.core.config.AppConstants.Network.FTP_DATA_TIMEOUT_MS
        private val BUFFER_SIZE = com.rostrum.core.config.AppConstants.Limits.BUFFER_SIZE
        
        // 匿名登录凭证
        private val ANONYMOUS_USER = com.rostrum.core.config.AppConstants.Network.FTP_ANONYMOUS_USER
        private val ANONYMOUS_PASS = com.rostrum.core.config.AppConstants.Network.FTP_ANONYMOUS_PASS
    }
    
    /**
     * FTP 文件信息
     */
    data class FtpFileInfo(
        val name: String,
        val path: String,
        val size: Long,
        val modifiedTime: Long,
        val isDirectory: Boolean,
        val isLink: Boolean,
        val permissions: String,
        val owner: String,
        val group: String
    ) {
        val sizeFormatted: String
            get() = when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
                else -> "${size / (1024 * 1024 * 1024)} GB"
            }
        
        val modifiedTimeFormatted: String
            get() {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                return sdf.format(Date(modifiedTime))
            }
    }
    
    /**
     * 连接配置
     */
    data class ConnectionConfig(
        val host: String,
        val port: Int = DEFAULT_PORT,
        val username: String? = null,
        val password: String? = null,
        val passiveMode: Boolean = true,
        val encoding: String = "UTF-8",
        val useTLS: Boolean = false
    ) {
        val isAnonymous: Boolean
            get() = username.isNullOrEmpty()
    }
    
    /**
     * 传输进度回调
     */
    interface TransferProgressListener {
        fun onProgress(transferred: Long, total: Long)
        fun onComplete()
        fun onError(message: String)
    }
    
    private val ftpClient = FTPClient()
    private var currentConfig: ConnectionConfig? = null
    
    val isConnected: Boolean
        get() = ftpClient.isConnected
    
    /**
     * 连接到 FTP 服务器
     */
    suspend fun connect(config: ConnectionConfig) = withContext(Dispatchers.IO) {
        disconnect()
        
        try {
            Log.i(TAG, "连接到 ${config.host}:${config.port}")
            
            // 配置客户端
            ftpClient.apply {
                controlEncoding = config.encoding
                connectTimeout = CONNECTION_TIMEOUT
                defaultTimeout = DATA_TIMEOUT
                bufferSize = BUFFER_SIZE
            }
            
            // 连接
            ftpClient.connect(config.host, config.port)
            
            // 检查连接响应
            val replyCode = ftpClient.replyCode
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                disconnect()
                throw IOException("FTP 服务器拒绝连接: $replyCode")
            }
            
            // 登录
            val username = if (config.isAnonymous) ANONYMOUS_USER else config.username!!
            val password = if (config.isAnonymous) ANONYMOUS_PASS else config.password ?: ""
            
            if (!ftpClient.login(username, password)) {
                disconnect()
                throw IOException("FTP 登录失败: 用户名或密码错误")
            }
            
            // 设置传输模式
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
            
            // 设置主动/被动模式
            if (config.passiveMode) {
                ftpClient.enterLocalPassiveMode()
                Log.d(TAG, "使用被动模式")
            } else {
                ftpClient.enterLocalActiveMode()
                Log.d(TAG, "使用主动模式")
            }
            
            currentConfig = config
            Log.i(TAG, "FTP 连接成功")
            
        } catch (e: Exception) {
            Log.e(TAG, "FTP 连接失败", e)
            disconnect()
            throw IOException("FTP 连接失败: ${e.message}", e)
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        try {
            if (ftpClient.isConnected) {
                ftpClient.logout()
                ftpClient.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "断开连接时出错", e)
        } finally {
            currentConfig = null
        }
    }
    
    /**
     * 列出目录内容
     */
    suspend fun listDirectory(path: String = "."): List<FtpFileInfo> = withContext(Dispatchers.IO) {
        requireConnected()
        
        try {
            val files = ftpClient.listFiles(path)
            
            files.mapNotNull { file ->
                // 跳过 . 和 ..
                if (file.name == "." || file.name == "..") {
                    return@mapNotNull null
                }
                
                FtpFileInfo(
                    name = file.name,
                    path = if (path == "." || path == "/") "/${file.name}" 
                           else if (path.endsWith("/")) "$path${file.name}"
                           else "$path/${file.name}",
                    size = file.size,
                    modifiedTime = file.timestamp?.timeInMillis ?: 0,
                    isDirectory = file.isDirectory,
                    isLink = file.isSymbolicLink,
                    permissions = file.rawListing?.take(10) ?: "",
                    owner = file.user ?: "",
                    group = file.group ?: ""
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "列出目录失败: $path", e)
            throw IOException("列出目录失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取当前工作目录
     */
    suspend fun pwd(): String = withContext(Dispatchers.IO) {
        requireConnected()
        ftpClient.printWorkingDirectory() ?: "/"
    }
    
    /**
     * 切换目录
     */
    suspend fun cd(path: String): Boolean = withContext(Dispatchers.IO) {
        requireConnected()
        
        try {
            val success = ftpClient.changeWorkingDirectory(path)
            if (!success) {
                Log.w(TAG, "切换目录失败: $path")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "切换目录失败: $path", e)
            throw IOException("切换目录失败: ${e.message}", e)
        }
    }
    
    /**
     * 返回上级目录
     */
    suspend fun cdup(): Boolean = withContext(Dispatchers.IO) {
        requireConnected()
        ftpClient.changeToParentDirectory()
    }
    
    /**
     * 创建目录
     */
    suspend fun mkdir(path: String): Boolean = withContext(Dispatchers.IO) {
        requireConnected()
        
        try {
            val success = ftpClient.makeDirectory(path)
            if (!success) {
                Log.w(TAG, "创建目录失败: $path, 回复: ${ftpClient.replyString}")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "创建目录失败: $path", e)
            throw IOException("创建目录失败: ${e.message}", e)
        }
    }
    
    /**
     * 递归创建目录
     */
    suspend fun mkdirs(path: String): Boolean = withContext(Dispatchers.IO) {
        requireConnected()
        
        val parts = path.split("/").filter { it.isNotEmpty() }
        var currentPath = ""
        
        for (part in parts) {
            currentPath = "$currentPath/$part"
            if (!exists(currentPath)) {
                if (!mkdir(currentPath)) {
                    return@withContext false
                }
            }
        }
        
        true
    }
    
    /**
     * 删除文件
     */
    suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        requireConnected()
        
        try {
            ftpClient.deleteFile(path)
        } catch (e: Exception) {
            Log.e(TAG, "删除文件失败: $path", e)
            throw IOException("删除文件失败: ${e.message}", e)
        }
    }
    
    /**
     * 删除目录
     */
    suspend fun rmdir(path: String): Boolean = withContext(Dispatchers.IO) {
        requireConnected()
        
        try {
            ftpClient.removeDirectory(path)
        } catch (e: Exception) {
            Log.e(TAG, "删除目录失败: $path", e)
            throw IOException("删除目录失败: ${e.message}", e)
        }
    }
    
    /**
     * 递归删除目录
     */
    suspend fun rmdirRecursive(path: String): Boolean = withContext(Dispatchers.IO) {
        requireConnected()
        
        try {
            val files = listDirectory(path)
            
            for (file in files) {
                if (file.isDirectory) {
                    rmdirRecursive(file.path)
                } else {
                    delete(file.path)
                }
            }
            
            rmdir(path)
        } catch (e: Exception) {
            Log.e(TAG, "递归删除目录失败: $path", e)
            throw IOException("递归删除目录失败: ${e.message}", e)
        }
    }
    
    /**
     * 重命名/移动文件
     */
    suspend fun rename(oldPath: String, newPath: String): Boolean = withContext(Dispatchers.IO) {
        requireConnected()
        
        try {
            ftpClient.rename(oldPath, newPath)
        } catch (e: Exception) {
            Log.e(TAG, "重命名失败: $oldPath -> $newPath", e)
            throw IOException("重命名失败: ${e.message}", e)
        }
    }
    
    /**
     * 检查文件/目录是否存在
     */
    suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        requireConnected()
        
        try {
            // 尝试获取文件信息
            val files = ftpClient.listFiles(path)
            files.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取文件大小
     */
    suspend fun size(path: String): Long = withContext(Dispatchers.IO) {
        requireConnected()
        
        try {
            val files = ftpClient.listFiles(path)
            files.firstOrNull()?.size ?: -1
        } catch (e: Exception) {
            Log.e(TAG, "获取文件大小失败: $path", e)
            -1
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
        requireConnected()
        
        try {
            val fileSize = size(remotePath)
            var transferred: Long = 0
            
            // 设置传输监听
            ftpClient.copyStreamListener = object : org.apache.commons.net.io.CopyStreamListener {
                override fun bytesTransferred(
                    totalBytesTransferred: Long,
                    bytesTransferred: Int,
                    streamSize: Long
                ) {
                    transferred = totalBytesTransferred
                    progressListener?.onProgress(transferred, fileSize)
                }
                
                override fun bytesTransferred(event: org.apache.commons.net.io.CopyStreamEvent) {
                    // 不需要处理
                }
            }
            
            FileOutputStream(localFile).use { fos ->
                val success = ftpClient.retrieveFile(remotePath, fos)
                
                if (success) {
                    progressListener?.onComplete()
                    Log.i(TAG, "下载完成: $remotePath -> ${localFile.path}")
                } else {
                    progressListener?.onError("下载失败: ${ftpClient.replyString}")
                    throw IOException("下载失败: ${ftpClient.replyString}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载文件失败: $remotePath", e)
            progressListener?.onError(e.message ?: "下载失败")
            throw IOException("下载文件失败: ${e.message}", e)
        } finally {
            ftpClient.copyStreamListener = null
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
        requireConnected()
        
        try {
            val fileSize = localFile.length()
            var transferred: Long = 0
            
            // 设置传输监听
            ftpClient.copyStreamListener = object : org.apache.commons.net.io.CopyStreamListener {
                override fun bytesTransferred(
                    totalBytesTransferred: Long,
                    bytesTransferred: Int,
                    streamSize: Long
                ) {
                    transferred = totalBytesTransferred
                    progressListener?.onProgress(transferred, fileSize)
                }
                
                override fun bytesTransferred(event: org.apache.commons.net.io.CopyStreamEvent) {
                    // 不需要处理
                }
            }
            
            FileInputStream(localFile).use { fis ->
                val success = ftpClient.storeFile(remotePath, fis)
                
                if (success) {
                    progressListener?.onComplete()
                    Log.i(TAG, "上传完成: ${localFile.path} -> $remotePath")
                } else {
                    progressListener?.onError("上传失败: ${ftpClient.replyString}")
                    throw IOException("上传失败: ${ftpClient.replyString}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "上传文件失败: ${localFile.path} -> $remotePath", e)
            progressListener?.onError(e.message ?: "上传失败")
            throw IOException("上传文件失败: ${e.message}", e)
        } finally {
            ftpClient.copyStreamListener = null
        }
    }
    
    /**
     * 断点续传下载
     */
    suspend fun resumeDownload(
        remotePath: String,
        localFile: File,
        progressListener: TransferProgressListener? = null
    ) = withContext(Dispatchers.IO) {
        requireConnected()
        
        try {
            val fileSize = size(remotePath)
            val localSize = if (localFile.exists()) localFile.length() else 0
            
            if (localSize >= fileSize) {
                progressListener?.onComplete()
                Log.i(TAG, "文件已完整下载: $remotePath")
                return@withContext
            }
            
            // 设置断点位置
            ftpClient.restartOffset = localSize
            var transferred = localSize
            
            ftpClient.copyStreamListener = object : org.apache.commons.net.io.CopyStreamListener {
                override fun bytesTransferred(
                    totalBytesTransferred: Long,
                    bytesTransferred: Int,
                    streamSize: Long
                ) {
                    transferred = localSize + totalBytesTransferred
                    progressListener?.onProgress(transferred, fileSize)
                }
                
                override fun bytesTransferred(event: org.apache.commons.net.io.CopyStreamEvent) {
                    // 不需要处理
                }
            }
            
            // 追加模式写入
            FileOutputStream(localFile, true).use { fos ->
                val inputStream = ftpClient.retrieveFileStream(remotePath)
                    ?: throw IOException("无法获取文件流: ${ftpClient.replyString}")
                
                inputStream.use { fis ->
                    fis.copyTo(fos, BUFFER_SIZE)
                }
                
                if (!ftpClient.completePendingCommand()) {
                    throw IOException("续传命令完成失败: ${ftpClient.replyString}")
                }
                
                progressListener?.onComplete()
                Log.i(TAG, "续传完成: $remotePath -> ${localFile.path}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "续传下载失败: $remotePath", e)
            progressListener?.onError(e.message ?: "续传失败")
            throw IOException("续传下载失败: ${e.message}", e)
        } finally {
            ftpClient.restartOffset = 0
            ftpClient.copyStreamListener = null
        }
    }
    
    /**
     * 读取文本文件内容
     */
    suspend fun readTextFile(remotePath: String, charset: String = "UTF-8"): String = withContext(Dispatchers.IO) {
        requireConnected()
        
        try {
            ByteArrayOutputStream().use { baos ->
                if (ftpClient.retrieveFile(remotePath, baos)) {
                    baos.toString(charset)
                } else {
                    throw IOException("读取文件失败: ${ftpClient.replyString}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取文件失败: $remotePath", e)
            throw IOException("读取文件失败: ${e.message}", e)
        }
    }
    
    /**
     * 写入文本文件
     */
    suspend fun writeTextFile(remotePath: String, content: String, charset: String = "UTF-8") = withContext(Dispatchers.IO) {
        requireConnected()
        
        try {
            ByteArrayInputStream(content.toByteArray(charset(charset))).use { bais ->
                if (!ftpClient.storeFile(remotePath, bais)) {
                    throw IOException("写入文件失败: ${ftpClient.replyString}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入文件失败: $remotePath", e)
            throw IOException("写入文件失败: ${e.message}", e)
        }
    }
    
    /**
     * 发送 NOOP 命令保持连接活跃
     */
    suspend fun keepAlive(): Boolean = withContext(Dispatchers.IO) {
        try {
            ftpClient.sendNoOp()
        } catch (e: Exception) {
            Log.w(TAG, "保持连接失败", e)
            false
        }
    }
    
    /**
     * 获取服务器系统类型
     */
    suspend fun getSystemType(): String = withContext(Dispatchers.IO) {
        requireConnected()
        ftpClient.systemType ?: "Unknown"
    }
    
    /**
     * 获取服务器状态
     */
    suspend fun getStatus(): String = withContext(Dispatchers.IO) {
        requireConnected()
        ftpClient.status ?: ""
    }
    
    private fun requireConnected() {
        if (!ftpClient.isConnected) {
            throw IOException("FTP 未连接")
        }
    }
}

