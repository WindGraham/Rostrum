package com.rostrum.core.plugin.providers

import androidx.compose.runtime.Composable
import com.rostrum.core.filesystem.FileSystemService
import com.rostrum.core.plugin.Plugin
import com.rostrum.core.plugin.models.FileInfo

/**
 * 文件预览插件接口
 * 
 * 实现此接口的插件可以提供文件预览功能
 * 支持本地文件和SSH远程文件
 */
interface FilePreviewPlugin : Plugin {
    /**
     * 支持的MIME类型列表
     */
    val supportedMimeTypes: List<String>
    
    /**
     * 支持的文件扩展名列表（包含点号，如 [".pdf", ".jpg"]）
     */
    val supportedExtensions: List<String>
    
    /**
     * 检查是否可以预览指定文件
     * 
     * @param file 文件信息
     * @return 是否可以预览
     */
    fun canPreview(file: FileInfo): Boolean
    
    /**
     * 创建预览组件（本地文件版本，兼容旧实现）
     * 
     * @param file 要预览的文件
     * @return 预览结果
     */
    suspend fun createPreview(file: FileInfo): PreviewResult
    
    /**
     * 创建预览组件（支持远程文件）
     * 
     * @param file 要预览的文件
     * @param fileSystem 文件系统服务，用于读取文件内容
     * @return 预览结果
     * 
     * 默认实现调用无fileSystem参数的createPreview方法（兼容旧插件）
     * 新插件应该重写此方法以支持远程文件
     */
    suspend fun createPreview(file: FileInfo, fileSystem: FileSystemService): PreviewResult {
        // 默认实现：忽略fileSystem参数，直接调用旧方法
        // 这样旧插件仍然可以工作（仅本地文件）
        return createPreview(file)
    }
    
    /**
     * 是否支持远程文件预览
     * 
     * @return true表示支持SSH远程文件预览
     */
    fun supportsRemoteFiles(): Boolean = false
    
    /**
     * 获取预览优先级
     * 当多个插件都可以预览同一文件时，优先级高的会被使用
     * 
     * @param file 文件信息
     * @return 优先级（数字越大优先级越高）
     */
    fun getPreviewPriority(file: FileInfo): Int = 0
}

/**
 * 预览结果
 */
sealed class PreviewResult {
    /**
     * 成功创建预览
     * 
     * @param previewComponent Compose预览组件
     * @param metadata 预览元数据（可选）
     */
    data class Success(
        val previewComponent: @Composable () -> Unit,
        val metadata: PreviewMetadata? = null
    ) : PreviewResult()
    
    /**
     * 预览失败
     * 
     * @param message 错误消息
     * @param error 异常信息（可选）
     */
    data class Error(
        val message: String,
        val error: Throwable? = null
    ) : PreviewResult()
    
    /**
     * 不支持的文件类型
     */
    object Unsupported : PreviewResult()
}

/**
 * 预览元数据
 */
data class PreviewMetadata(
    val title: String? = null,
    val description: String? = null,
    val thumbnail: String? = null,  // 缩略图路径或URL
    val canEdit: Boolean = false,
    val canExport: Boolean = false,
    val customActions: List<PreviewAction> = emptyList()
)

/**
 * 预览操作
 */
data class PreviewAction(
    val id: String,
    val label: String,
    val icon: String? = null,
    val handler: suspend () -> Unit
)

