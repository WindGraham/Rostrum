package com.rostrum.core.event

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.reflect.KClass

/**
 * 事件总线接口
 */
interface EventBus {
    /**
     * 发布事件
     * 
     * @param event 事件对象
     */
    fun <T : Event> publish(event: T)
    
    /**
     * 订阅事件
     * 
     * @param eventType 事件类型
     * @param subscriber 订阅者
     * @param priority 优先级（数字越大优先级越高）
     */
    fun <T : Event> subscribe(
        eventType: KClass<T>,
        subscriber: EventSubscriber<T>,
        priority: Int = 0
    )
    
    /**
     * 取消订阅
     * 
     * @param eventType 事件类型
     * @param subscriber 订阅者
     */
    fun <T : Event> unsubscribe(
        eventType: KClass<T>,
        subscriber: EventSubscriber<T>
    )
    
    /**
     * 请求-响应模式
     * 
     * @param request 请求事件
     * @param timeout 超时时间
     * @return 响应事件，如果超时返回null
     */
    suspend fun <T : Event, R : Event> request(
        request: T,
        timeout: Duration = 10.seconds
    ): R?
}

/**
 * 事件基类
 */
abstract class Event {
    abstract val id: String
    abstract val timestamp: Long
    val source: String = "unknown"
}

/**
 * 文件系统事件
 */
data class FileCreatedEvent(
    override val id: String,
    override val timestamp: Long,
    val filePath: String,
    val isDirectory: Boolean
) : Event()

data class FileDeletedEvent(
    override val id: String,
    override val timestamp: Long,
    val filePath: String,
    val isDirectory: Boolean
) : Event()

data class FileModifiedEvent(
    override val id: String,
    override val timestamp: Long,
    val filePath: String
) : Event()

/**
 * 流式文件创建事件（用于触发自动多窗口布局）
 * 
 * 当后台任务通过流式写入创建文件时触发此事件，UI 层订阅后自动切换布局：
 * - 编辑器窗口打开文件
 * - 预览窗口显示实时渲染效果
 * 
 * 支持所有文件类型（HTML、Markdown、代码文件等）
 * 
 * @param filePath 文件路径
 * @param triggeredByTask 是否由后台任务触发
 */
data class StreamingFileCreatedEvent(
    override val id: String,
    override val timestamp: Long,
    val filePath: String,
    val triggeredByTask: Boolean = true
) : Event()

/**
 * 类型别名：保持向后兼容
 * @deprecated 使用 StreamingFileCreatedEvent 代替
 */
@Deprecated("Use StreamingFileCreatedEvent instead", ReplaceWith("StreamingFileCreatedEvent"))
typealias HtmlFileCreatedEvent = StreamingFileCreatedEvent

/**
 * 文件实时编辑事件（用于触发自动多窗口布局，支持所有文件类型）
 * 
 * 当后台任务创建或打开文件时触发此事件，UI 层订阅后根据配置自动切换布局：
 * - 编辑器窗口打开文件
 * - 预览窗口显示实时渲染效果（如果支持）
 * 
 * @param filePath 文件路径
 * @param triggeredByTask 是否由后台任务触发
 * @param fileType 文件类型（扩展名，不含点号）
 */
data class FileRealtimeEditEvent(
    override val id: String,
    override val timestamp: Long,
    val filePath: String,
    val triggeredByTask: Boolean = true,
    val fileType: String? = null
) : Event()

/**
 * 文件内容更新事件（用于实时预览，无需保存文件）
 * 
 * 与 FileModifiedEvent 的区别：
 * - FileModifiedEvent: 文件已保存到磁盘后触发
 * - FileContentUpdateEvent: 编辑器内容变化时触发（文件未保存）
 * 
 * @param filePath 文件路径（规范化后的绝对路径）
 * @param content 文件内容（未保存的最新内容）
 * @param isModified 是否已修改（相对于磁盘文件）
 */
data class FileContentUpdateEvent(
    override val id: String,
    override val timestamp: Long,
    val filePath: String,
    val content: String,
    val isModified: Boolean = true
) : Event()

/**
 * 插件事件
 */
data class PluginLoadedEvent(
    override val id: String,
    override val timestamp: Long,
    val pluginId: String,
    val pluginName: String
) : Event()

data class PluginUnloadedEvent(
    override val id: String,
    override val timestamp: Long,
    val pluginId: String,
    val pluginName: String
) : Event()

/**
 * 终端事件
 */
data class TerminalOutputEvent(
    override val id: String,
    override val timestamp: Long,
    val sessionId: String,
    val output: String,
    val isError: Boolean = false
) : Event()

data class TerminalCommandEvent(
    override val id: String,
    override val timestamp: Long,
    val sessionId: String,
    val command: String,
    val workingDirectory: String
) : Event()

/**
 * 执行 Python 脚本事件
 * 
 * 当脚本执行请求触发此事件，UI 层订阅后：
 * - 自动打开代码预览窗口显示 Python 源代码
 * - 自动打开终端窗口，在其中运行 Python 脚本
 * - 用户可以在终端中与脚本交互（如 input() 函数）
 * 
 * 所有 Python 执行都使用交互式模式，统一通过 PythonScriptSession 处理。
 * 
 * @param filePath Python 文件路径
 * @param args 命令行参数
 * @param interactive 是否交互式执行（默认 true，统一使用交互式模式）
 */
data class RunPythonEvent(
    val filePath: String,
    val args: String = "",
    val interactive: Boolean = true,
    override val id: String = java.util.UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : Event()

/**
 * 浏览文件事件
 * 
 * 当文件预览请求触发此事件，UI 层订阅后：
 * - 自动打开文件预览窗口显示文件内容
 * 
 * @param filePath 文件路径
 * @param fileType 文件类型（扩展名）
 */
data class ViewFileEvent(
    val filePath: String,
    val fileType: String? = null,
    override val id: String = java.util.UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : Event()

/**
 * 文件操作浏览事件（非写入类文件操作触发）
 * 
 * 当复制、压缩、解压、批量重命名、创建文件夹等操作完成时触发，
 * UI 层订阅后自动切换为"文件浏览器 + 预览"布局：
 * - 文件浏览器窗口导航到目标文件所在目录并高亮
 * - 预览窗口显示目标文件（如果是可预览文件）
 * 
 * @param filePath 操作结果文件路径（如复制目标、压缩输出等）
 * @param operationType 操作类型
 * @param isDirectory 目标是否为目录
 */
data class FileOperationBrowseEvent(
    val filePath: String,
    val operationType: FileOperationType,
    val isDirectory: Boolean = false,
    override val id: String = java.util.UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : Event()

/**
 * 文件操作类型枚举
 */
enum class FileOperationType {
    COPY,
    MOVE,
    COMPRESS,
    EXTRACT,
    BATCH_RENAME,
    CREATE_FOLDER,
    DELETE
}

/**
 * 导出文件为图片事件
 * 
 * 当导出图片请求触发此事件，UI 层订阅后：
 * - 用 WebView 渲染 HTML/Markdown 文件
 * - 将渲染结果截图保存为 PNG 图片到相册
 * 
 * @param filePath 要导出的文件路径（HTML 或 Markdown）
 * @param outputPath 导出结果的保存路径（由 UI 层设置，工具层通过 result 获取）
 * @param result CompletableDeferred，UI 层完成后设置结果
 */
data class ExportImageEvent(
    val filePath: String,
    val result: kotlinx.coroutines.CompletableDeferred<ExportImageResult> = kotlinx.coroutines.CompletableDeferred(),
    override val id: String = java.util.UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : Event()

/**
 * 导出图片结果
 */
data class ExportImageResult(
    val success: Boolean,
    val outputPath: String? = null,
    val message: String? = null,
    val error: String? = null
)

/**
 * 事件订阅者
 */
interface EventSubscriber<T : Event> {
    suspend fun onEvent(event: T)
}
