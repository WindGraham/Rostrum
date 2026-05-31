package com.rostrum.extension.api

import kotlinx.coroutines.flow.StateFlow

/**
 * 工作区 API
 * 
 * 提供工作区访问功能
 */
interface WorkspaceApi {
    /**
     * 工作区文件夹列表
     */
    val folders: List<WorkspaceFolder>
    
    /**
     * 当前选中的文件
     */
    val selectedFiles: StateFlow<List<FileInfo>>
    
    /**
     * 打开文件
     * 
     * @param fileInfo 文件信息
     */
    suspend fun openFile(fileInfo: FileInfo)
    
    /**
     * 获取工作区配置
     * 
     * @param section 配置节名称，null 表示获取根配置
     * @return 配置对象
     */
    fun getConfiguration(section: String? = null): WorkspaceConfiguration
}

/**
 * 工作区文件夹
 */
data class WorkspaceFolder(
    val uri: String,
    val name: String,
    val index: Int = 0
)

/**
 * 工作区配置
 */
interface WorkspaceConfiguration {
    /**
     * 获取配置值
     * 
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值，如果不存在返回默认值
     */
    fun <T> get(key: String, defaultValue: T? = null): T?
    
    /**
     * 更新配置值
     * 
     * @param key 配置键
     * @param value 配置值
     * @return 是否更新成功
     */
    fun update(key: String, value: Any?): Boolean
}

