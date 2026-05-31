package com.rostrum.extension.api

import androidx.compose.runtime.Composable

/**
 * 统一的文件查看器 API
 * 
 * 合并了预览（Preview）和编辑（Editor）功能为单一接口
 * 插件只需实现此接口即可提供文件查看和编辑能力
 * 
 * ## 设计理念
 * - **统一接口**: 查看和编辑是同一组件的两种模式
 * - **松耦合**: 插件独立运行，通过标准接口通信
 * - **易扩展**: 第三方开发者可轻松添加新文件类型支持
 * 
 * ## 使用示例
 * ```kotlin
 * class MarkdownViewerPlugin : FileViewerPlugin {
 *     override val supportedExtensions = listOf(".md", ".markdown")
 *     override val supportedMimeTypes = listOf("text/markdown")
 *     
 *     override fun canHandle(file: ViewerFileInfo) = 
 *         file.extension in supportedExtensions
 *     
 *     override fun createViewer(file: ViewerFileInfo, mode: ViewerMode) = 
 *         ViewerResult.Success { MarkdownViewer(file, mode) }
 * }
 * ```
 */
interface FileViewerPlugin {
    
    /**
     * 插件唯一标识符
     * 格式: com.{author}.viewer.{type}
     * 例如: com.rostrum.viewer.markdown
     */
    val id: String
    
    /**
     * 插件显示名称
     */
    val name: String
    
    /**
     * 插件版本 (语义化版本)
     */
    val version: String
    
    /**
     * 作者
     */
    val author: String
    
    /**
     * 插件描述
     */
    val description: String
    
    /**
     * 支持的文件扩展名列表 (包含点号)
     * 例如: [".pdf", ".doc", ".docx"]
     */
    val supportedExtensions: List<String>
    
    /**
     * 支持的 MIME 类型列表
     * 例如: ["application/pdf", "application/msword"]
     */
    val supportedMimeTypes: List<String>
    
    /**
     * 支持的查看模式
     * 默认支持只读查看
     */
    val supportedModes: Set<ViewerMode>
        get() = setOf(ViewerMode.VIEW)
    
    /**
     * 插件图标 (可选, 资源路径或 URL)
     */
    val icon: String?
        get() = null
    
    /**
     * 检查是否可以处理指定文件
     * 
     * @param file 文件信息
     * @return 是否可以处理
     */
    fun canHandle(file: ViewerFileInfo): Boolean
    
    /**
     * 创建文件查看器组件
     * 
     * @param file 要查看的文件
     * @param mode 查看模式
     * @return 查看器结果
     */
    fun createViewer(file: ViewerFileInfo, mode: ViewerMode = ViewerMode.VIEW): ViewerResult
    
    /**
     * 获取处理优先级 (数字越大优先级越高)
     * 当多个插件都能处理同一文件时，优先使用高优先级插件
     * 
     * @param file 文件信息
     * @return 优先级 (默认 50)
     */
    fun getPriority(file: ViewerFileInfo): Int = 50
    
    /**
     * 插件激活时调用
     */
    suspend fun onActivate() {}
    
    /**
     * 插件停用时调用
     */
    suspend fun onDeactivate() {}
}

/**
 * 查看模式
 */
enum class ViewerMode {
    /** 只读查看 */
    VIEW,
    
    /** 可编辑 */
    EDIT,
    
    /** 并排预览 (适用于 Markdown 等) */
    SPLIT,
    
    /** 十六进制查看 */
    HEX
}

/**
 * 文件信息 (用于查看器)
 */
data class ViewerFileInfo(
    /** 文件路径 (URI 格式) */
    val uri: String,
    
    /** 文件名 */
    val name: String,
    
    /** 文件扩展名 (小写, 包含点号) */
    val extension: String,
    
    /** MIME 类型 */
    val mimeType: String?,
    
    /** 文件大小 (字节) */
    val size: Long,
    
    /** 是否只读 */
    val isReadOnly: Boolean = false,
    
    /** 额外元数据 */
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 查看器创建结果
 */
sealed class ViewerResult {
    /**
     * 成功创建查看器
     * 
     * @param component Compose 组件
     * @param capabilities 查看器能力
     */
    data class Success(
        val component: @Composable () -> Unit,
        val capabilities: ViewerCapabilities = ViewerCapabilities()
    ) : ViewerResult()
    
    /**
     * 创建失败
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : ViewerResult()
    
    /**
     * 不支持的文件
     */
    object Unsupported : ViewerResult()
}

/**
 * 查看器能力声明
 */
data class ViewerCapabilities(
    /** 是否支持搜索 */
    val canSearch: Boolean = false,
    
    /** 是否支持缩放 */
    val canZoom: Boolean = false,
    
    /** 是否支持打印 */
    val canPrint: Boolean = false,
    
    /** 是否支持导出 */
    val canExport: Boolean = false,
    
    /** 是否支持分享 */
    val canShare: Boolean = true,
    
    /** 是否支持复制内容 */
    val canCopy: Boolean = true,
    
    /** 自定义操作列表 */
    val customActions: List<ViewerAction> = emptyList()
)

/**
 * 查看器自定义操作
 */
data class ViewerAction(
    val id: String,
    val label: String,
    val icon: String? = null,
    val handler: suspend () -> Unit
)

/**
 * 文件查看器注册表
 * 
 * 用于管理所有已注册的文件查看器插件
 */
interface FileViewerRegistry {
    /**
     * 注册查看器插件
     */
    fun register(plugin: FileViewerPlugin): Disposable
    
    /**
     * 注销查看器插件
     */
    fun unregister(pluginId: String)
    
    /**
     * 查找最适合的查看器
     */
    fun findViewer(file: ViewerFileInfo): FileViewerPlugin?
    
    /**
     * 获取所有能处理指定文件的查看器
     */
    fun findAllViewers(file: ViewerFileInfo): List<FileViewerPlugin>
    
    /**
     * 获取所有已注册的查看器
     */
    fun getAllViewers(): List<FileViewerPlugin>
}

