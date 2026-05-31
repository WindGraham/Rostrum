package com.rostrum.extension.api

/**
 * UI API
 * 
 * 提供 UI 扩展功能
 */
interface UIApi {
    /**
     * 注册文件预览提供者
     * 
     * @param provider 预览提供者
     * @return Disposable，用于取消注册
     */
    fun registerPreview(provider: FilePreviewProvider): Disposable
    
    /**
     * 注册文件编辑提供者
     * 
     * @param provider 编辑提供者
     * @return Disposable，用于取消注册
     */
    fun registerEditor(provider: FileEditorProvider): Disposable
    
    /**
     * 创建 Webview 面板（用于 VSCode 插件兼容）
     * 
     * @param viewType 视图类型
     * @param title 标题
     * @return Webview 面板
     */
    fun createWebviewPanel(viewType: String, title: String): WebviewPanel
    
    /**
     * 注册状态栏项
     * 
     * @param alignment 对齐方式
     * @return 状态栏项
     */
    fun registerStatusBarItem(alignment: StatusBarAlignment = StatusBarAlignment.LEFT): StatusBarItem
}

/**
 * 文件预览提供者
 */
interface FilePreviewProvider {
    /**
     * 支持的 MIME 类型列表
     */
    val supportedMimeTypes: List<String>
    
    /**
     * 支持的文件扩展名列表
     */
    val supportedExtensions: List<String>
    
    /**
     * 检查是否可以预览指定文件
     * 
     * @param fileInfo 文件信息
     * @return 是否可以预览
     */
    fun canPreview(fileInfo: FileInfo): Boolean
    
    /**
     * 创建预览组件
     * 
     * @param fileInfo 文件信息
     * @return 预览组件工厂函数，如果不支持返回 null
     * 
     * 注意：返回的是 Compose 组件的工厂函数，实际渲染由主应用负责
     */
    fun createPreview(fileInfo: FileInfo): (() -> Unit)?
    
    /**
     * 获取预览优先级（数字越大优先级越高）
     * 
     * @param fileInfo 文件信息
     * @return 优先级
     */
    fun getPreviewPriority(fileInfo: FileInfo): Int
}

/**
 * 文件编辑提供者
 */
interface FileEditorProvider {
    /**
     * 支持的 MIME 类型列表
     */
    val supportedMimeTypes: List<String>
    
    /**
     * 支持的文件扩展名列表
     */
    val supportedExtensions: List<String>
    
    /**
     * 检查是否可以编辑指定文件
     * 
     * @param fileInfo 文件信息
     * @return 是否可以编辑
     */
    fun canEdit(fileInfo: FileInfo): Boolean
    
    /**
     * 创建编辑组件
     * 
     * @param fileInfo 文件信息
     * @return 编辑组件工厂函数，如果不支持返回 null
     * 
     * 注意：返回的是 Compose 组件的工厂函数，实际渲染由主应用负责
     */
    fun createEditor(fileInfo: FileInfo): (() -> Unit)?
}

/**
 * Webview 面板
 */
interface WebviewPanel {
    val title: String
    val viewType: String
    
    /**
     * 设置 HTML 内容
     */
    fun setHtml(html: String)
    
    /**
     * 执行 JavaScript
     */
    fun executeJavaScript(script: String)
    
    /**
     * 显示面板
     */
    fun show()
    
    /**
     * 隐藏面板
     */
    fun hide()
    
    /**
     * 释放资源
     */
    fun dispose()
}

/**
 * 状态栏对齐方式
 */
enum class StatusBarAlignment {
    LEFT,
    RIGHT
}

/**
 * 状态栏项
 */
interface StatusBarItem {
    var text: String
    var tooltip: String?
    var command: String?
    
    fun show()
    fun hide()
    fun dispose()
}

