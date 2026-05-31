package com.rostrum.core.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Agent/tool 请求打开自定义前端入口时使用的轻量事件。
 */
object CustomFrontendOpenEvent {
    private val _openRequests = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val openRequests: SharedFlow<String> = _openRequests.asSharedFlow()

    fun emit(frontendId: String) {
        _openRequests.tryEmit(frontendId)
    }
}
