package com.rostrum.core.filesystem

import android.util.Log
import com.rostrum.core.ssh.filesystem.SshFileSystem
import com.rostrum.core.util.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 活动文件系统管理器
 * 
 * 管理当前活动的文件系统（本地 vs SSH远程）
 * 提供统一的文件系统切换和访问接口
 * 
 * @author OmniMaster
 * @license BSD-2-Clause
 */
object ActiveFileSystemManager {
    
    private const val TAG = "ActiveFileSystemManager"
    
    // 本地文件系统（单例）
    private val localFileSystem = LocalFileSystem()
    
    // 当前活动的文件系统
    private var _activeFileSystem: FileSystemService = localFileSystem
    
    // 当前SSH文件系统（如果已连接）
    private var _sshFileSystem: SshFileSystem? = null
    
    // 是否为远程文件系统
    private val _isRemote = MutableStateFlow(false)
    val isRemote: StateFlow<Boolean> = _isRemote.asStateFlow()
    
    // 当前根路径
    private val _rootPath = MutableStateFlow(getDefaultLocalRoot())
    val rootPath: StateFlow<String> = _rootPath.asStateFlow()
    
    // 文件系统切换事件
    private val _fileSystemChanged = MutableStateFlow(0L)
    val fileSystemChanged: StateFlow<Long> = _fileSystemChanged.asStateFlow()
    
    // SSH连接信息
    private var _sshHost: String? = null
    private var _sshUser: String? = null
    
    /**
     * 获取默认的本地根路径
     */
    private fun getDefaultLocalRoot(): String {
        return try {
            FileUtils.getExternalStorageRoot().absolutePath
        } catch (e: Exception) {
            "/storage/emulated/0"
        }
    }
    
    /**
     * 切换到SSH文件系统
     * 
     * @param sshFileSystem SSH文件系统实例
     * @param rootPath SSH服务器的根路径（如 /home/user）
     * @param host SSH主机地址
     * @param user SSH用户名
     */
    fun switchToSsh(
        sshFileSystem: SshFileSystem,
        rootPath: String,
        host: String? = null,
        user: String? = null
    ) {
        Log.i(TAG, "切换到SSH文件系统: $rootPath (host=$host, user=$user)")
        
        _sshFileSystem = sshFileSystem
        _activeFileSystem = sshFileSystem
        _isRemote.value = true
        _rootPath.value = rootPath
        _sshHost = host
        _sshUser = user
        
        // 触发文件系统变更事件
        notifyFileSystemChanged()
    }
    
    /**
     * 切换到本地文件系统
     */
    fun switchToLocal() {
        Log.i(TAG, "切换到本地文件系统")
        
        _sshFileSystem = null
        _activeFileSystem = localFileSystem
        _isRemote.value = false
        _rootPath.value = getDefaultLocalRoot()
        _sshHost = null
        _sshUser = null
        
        // 触发文件系统变更事件
        notifyFileSystemChanged()
    }
    
    /**
     * 获取当前活动的文件系统
     */
    fun getActiveFileSystem(): FileSystemService {
        return _activeFileSystem
    }
    
    /**
     * 获取本地文件系统
     */
    fun getLocalFileSystem(): LocalFileSystem {
        return localFileSystem
    }
    
    /**
     * 获取当前SSH文件系统（如果已连接）
     */
    fun getSshFileSystem(): SshFileSystem? {
        return _sshFileSystem
    }
    
    /**
     * 检查当前是否使用远程文件系统
     */
    fun isUsingRemote(): Boolean {
        return _isRemote.value
    }
    
    /**
     * 获取当前根路径
     */
    fun getCurrentRootPath(): String {
        return _rootPath.value
    }
    
    /**
     * 获取SSH连接主机
     */
    fun getSshHost(): String? {
        return _sshHost
    }
    
    /**
     * 获取SSH连接用户
     */
    fun getSshUser(): String? {
        return _sshUser
    }
    
    /**
     * 获取当前文件系统的显示名称
     */
    fun getDisplayName(): String {
        return if (_isRemote.value) {
            val host = _sshHost ?: "SSH"
            val user = _sshUser ?: ""
            if (user.isNotEmpty()) "$user@$host" else host
        } else {
            "本地存储"
        }
    }
    
    /**
     * 通知文件系统已变更
     */
    private fun notifyFileSystemChanged() {
        _fileSystemChanged.value = System.currentTimeMillis()
    }
    
    /**
     * 手动触发文件系统刷新事件
     */
    fun triggerRefresh() {
        notifyFileSystemChanged()
    }
}
