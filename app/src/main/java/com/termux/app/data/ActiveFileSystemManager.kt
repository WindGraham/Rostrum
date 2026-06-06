package com.termux.app.data

import com.termux.app.ssh.SshFileSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Active File System Manager - singleton migrated from Rostrum
 * Routes file operations to local or remote filesystem
 */
object ActiveFileSystemManager {
    private val localFileSystem = LocalFileSystem()
    private var _activeFileSystem: FileSystemService = localFileSystem
    private var _sshFileSystem: SshFileSystem? = null
    
    private val _isRemote = MutableStateFlow(false)
    val isRemote: StateFlow<Boolean> = _isRemote.asStateFlow()
    
    private val _rootPath = MutableStateFlow("/sdcard")
    val rootPath: StateFlow<String> = _rootPath.asStateFlow()
    
    private val _fileSystemChanged = MutableStateFlow(0L)
    val fileSystemChanged: StateFlow<Long> = _fileSystemChanged.asStateFlow()
    
    fun switchToSsh(sshFileSystem: SshFileSystem, rootPath: String, host: String?, user: String?) {
        _sshFileSystem = sshFileSystem
        _activeFileSystem = sshFileSystem
        _isRemote.value = true
        _rootPath.value = rootPath
        _fileSystemChanged.value = System.currentTimeMillis()
    }
    
    fun switchToLocal() {
        _activeFileSystem = localFileSystem
        _isRemote.value = false
        _rootPath.value = "/sdcard"
        _fileSystemChanged.value = System.currentTimeMillis()
    }
    
    fun getActiveFileSystem(): FileSystemService = _activeFileSystem
    
    fun getDisplayName(): String {
        return if (_isRemote.value) {
            "Remote"
        } else {
            "本地存储"
        }
    }
}
