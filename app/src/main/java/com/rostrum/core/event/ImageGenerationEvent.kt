package com.rostrum.core.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 图片生成事件
 * 
 * 用于通知主界面自动预览新生成的图片
 */
object ImageGenerationEvent {
    private val _imageGenerated = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val imageGenerated: SharedFlow<String> = _imageGenerated.asSharedFlow()
    
    /**
     * 发送图片生成事件
     * @param filePath 生成的图片路径
     */
    fun emit(filePath: String) {
        _imageGenerated.tryEmit(filePath)
    }
}
