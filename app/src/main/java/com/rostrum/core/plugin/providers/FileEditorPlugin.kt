package com.rostrum.core.plugin.providers

import androidx.compose.runtime.Composable
import com.rostrum.core.plugin.Plugin
import com.rostrum.core.plugin.models.FileInfo

/**
 * 文件编辑插件接口
 * 
 * 实现此接口的插件可以提供文件编辑功能
 */
interface FileEditorPlugin : Plugin {
    /**
     * 支持的MIME类型列表
     */
    val supportedMimeTypes: List<String>
    
    /**
     * 支持的文件扩展名列表
     */
    val supportedExtensions: List<String>
    
    /**
     * 检查是否可以编辑指定文件
     * 
     * @param file 文件信息
     * @return 是否可以编辑
     */
    fun canEdit(file: FileInfo): Boolean
    
    /**
     * 打开编辑器
     * 
     * @param file 要编辑的文件
     * @return 编辑会话
     */
    suspend fun openEditor(file: FileInfo): Result<EditorSession>
    
    /**
     * 获取编辑优先级
     * 
     * @param file 文件信息
     * @return 优先级
     */
    fun getEditPriority(file: FileInfo): Int = 0
}

/**
 * 编辑会话
 */
interface EditorSession {
    /**
     * 正在编辑的文件
     */
    val file: FileInfo
    
    /**
     * 文件是否已修改
     */
    val isModified: Boolean
    
    /**
     * 编辑器UI组件
     */
    @Composable
    fun EditorComponent()
    
    /**
     * 保存文件
     * 
     * @return 保存结果
     */
    suspend fun save(): Result<Unit>
    
    /**
     * 另存为
     * 
     * @param newPath 新文件路径
     * @return 保存结果
     */
    suspend fun saveAs(newPath: String): Result<Unit>
    
    /**
     * 关闭编辑器
     * 
     * @return 关闭结果
     */
    suspend fun close(): Result<Unit>
    
    /**
     * 获取文件内容
     * 
     * @return 文件内容
     */
    fun getContent(): String
    
    /**
     * 设置文件内容
     * 
     * @param content 新内容
     */
    fun setContent(content: String)
    
    /**
     * 撤销操作
     * 
     * @return 是否成功撤销
     */
    fun undo(): Boolean
    
    /**
     * 重做操作
     * 
     * @return 是否成功重做
     */
    fun redo(): Boolean
    
    /**
     * 添加修改监听器
     * 
     * @param listener 监听器
     */
    fun addModificationListener(listener: (Boolean) -> Unit)
    
    /**
     * 移除修改监听器
     * 
     * @param listener 监听器
     */
    fun removeModificationListener(listener: (Boolean) -> Unit)
}

