package com.rostrum.core.plugin.models

/**
 * 文件信息
 * 
 * 表示文件系统中的文件或目录信息
 */
data class FileInfo(
    /**
     * 文件路径（绝对路径）
     */
    val path: String,
    
    /**
     * 文件名（不含路径）
     */
    val name: String,
    
    /**
     * 文件扩展名（包含点号，如 ".txt"）
     */
    val extension: String,
    
    /**
     * 文件大小（字节）
     */
    val size: Long,
    
    /**
     * 最后修改时间（Unix时间戳，毫秒）
     */
    val lastModified: Long,
    
    /**
     * 是否为目录
     */
    val isDirectory: Boolean,
    
    /**
     * 文件权限（Unix格式，如 "rw-r--r--"）
     */
    val permissions: String,
    
    /**
     * MIME类型（可选）
     */
    val mimeType: String? = null,
    
    /**
     * 是否为远程文件（SSH远程文件系统）
     */
    val isRemote: Boolean = false
)

