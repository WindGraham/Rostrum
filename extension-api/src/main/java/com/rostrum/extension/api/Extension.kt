package com.rostrum.extension.api

/**
 * 插件扩展接口
 * 
 * 所有插件必须实现此接口，提供激活和停用方法
 * 
 * 类似 VSCode 的 Extension 接口
 */
interface Extension {
    /**
     * 激活插件
     * 
     * 当满足激活条件时，主应用会调用此方法
     * 
     * @param context 插件上下文，提供 API 访问
     */
    suspend fun activate(context: ExtensionContext)
    
    /**
     * 停用插件
     * 
     * 当插件需要停用时，主应用会调用此方法
     * 插件应该清理资源，但保留状态以便重新激活
     */
    suspend fun deactivate()
}

