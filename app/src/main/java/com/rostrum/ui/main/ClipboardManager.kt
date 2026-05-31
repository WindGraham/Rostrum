package com.rostrum.ui.main

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 剪贴板管理器 - 用于文件复制/粘贴
 */
object ClipboardManager {
    private val _clipboardFiles = mutableStateListOf<String>()
    val clipboardFiles: List<String> get() = _clipboardFiles.toList()
    
    private var _isCutMode = mutableStateOf(false)
    val isCutMode: State<Boolean> = _isCutMode
    
    /**
     * 复制文件到剪贴板
     */
    fun copyFiles(filePaths: List<String>) {
        _clipboardFiles.clear()
        _clipboardFiles.addAll(filePaths)
        _isCutMode.value = false
    }
    
    /**
     * 剪切文件到剪贴板
     */
    fun cutFiles(filePaths: List<String>) {
        _clipboardFiles.clear()
        _clipboardFiles.addAll(filePaths)
        _isCutMode.value = true
    }
    
    /**
     * 清空剪贴板
     */
    fun clear() {
        _clipboardFiles.clear()
        _isCutMode.value = false
    }
    
    /**
     * 检查剪贴板是否为空
     */
    fun isEmpty(): Boolean = _clipboardFiles.isEmpty()
}

