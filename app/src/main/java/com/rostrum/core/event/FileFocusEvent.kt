package com.rostrum.core.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 文件聚焦事件
 * 
 * 当后台操作创建/修改文件后，通知文件管理器面板自动导航到该文件所在目录，
 * 并高亮选中该文件（仅背景色标记，非勾选）。
 */
object FileFocusEvent {
    private val _fileFocused = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val fileFocused: SharedFlow<String> = _fileFocused.asSharedFlow()

    /**
     * 发送文件聚焦事件
     * @param filePath 要聚焦定位的文件绝对路径
     */
    fun emit(filePath: String) {
        _fileFocused.tryEmit(filePath)
    }
}
