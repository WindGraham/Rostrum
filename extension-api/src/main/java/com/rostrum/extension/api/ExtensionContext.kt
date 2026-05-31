package com.rostrum.extension.api

import android.content.Context

/**
 * 插件上下文接口
 * 
 * 提供插件访问主应用功能的统一接口
 * 类似 VSCode 的 ExtensionContext
 */
interface ExtensionContext {
    /**
     * Android 应用上下文
     */
    val appContext: Context
    
    /**
     * 文件系统 API
     */
    val fileSystem: FileSystemApi
    
    /**
     * 终端 API
     */
    val terminal: TerminalApi
    
    /**
     * UI API
     */
    val ui: UIApi
    
    /**
     * 命令 API
     */
    val commands: CommandApi
    
    /**
     * 工作区 API
     */
    val workspace: WorkspaceApi
    
    /**
     * 窗口 API
     */
    val window: WindowApi
    
    /**
     * 插件扩展路径（插件所在目录）
     */
    val extensionPath: String
    
    /**
     * 插件存储路径（插件私有存储）
     */
    val storagePath: String
    
    /**
     * 全局存储路径（所有插件共享）
     */
    val globalStoragePath: String
    
    /**
     * 订阅列表（用于资源清理）
     * 
     * 插件注册的 Disposable 资源会自动管理
     */
    val subscriptions: MutableList<Disposable>
    
    /**
     * 将相对路径转换为绝对路径
     * 
     * @param relativePath 相对于 extensionPath 的相对路径
     * @return 绝对路径
     */
    fun asAbsolutePath(relativePath: String): String
    
    /**
     * 获取工作区配置
     * 
     * @param section 配置节名称，null 表示获取根配置
     * @return 配置对象
     */
    fun getConfiguration(section: String? = null): WorkspaceConfiguration
}

/**
 * 可释放资源接口
 */
interface Disposable {
    /**
     * 释放资源
     */
    fun dispose()
}

