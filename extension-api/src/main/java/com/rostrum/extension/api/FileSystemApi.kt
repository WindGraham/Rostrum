package com.rostrum.extension.api

/**
 * 文件系统 API
 * 
 * 提供安全的文件访问功能
 */
interface FileSystemApi {
    /**
     * 读取文件内容（二进制）
     * 
     * @param uri 文件 URI 或路径
     * @return 文件内容，失败返回 Result.failure
     */
    suspend fun readFile(uri: String): Result<ByteArray>
    
    /**
     * 写入文件内容（二进制）
     * 
     * @param uri 文件 URI 或路径
     * @param content 文件内容
     * @return 写入结果
     */
    suspend fun writeFile(uri: String, content: ByteArray): Result<Unit>
    
    /**
     * 读取文本文件
     * 
     * @param uri 文件 URI 或路径
     * @param encoding 字符编码，默认为 UTF-8
     * @return 文件内容，失败返回 Result.failure
     */
    suspend fun readTextFile(uri: String, encoding: String = "UTF-8"): Result<String>
    
    /**
     * 写入文本文件
     * 
     * @param uri 文件 URI 或路径
     * @param content 文件内容
     * @param encoding 字符编码，默认为 UTF-8
     * @return 写入结果
     */
    suspend fun writeTextFile(uri: String, content: String, encoding: String = "UTF-8"): Result<Unit>
    
    /**
     * 检查文件或目录是否存在
     * 
     * @param uri 文件或目录 URI 或路径
     * @return 是否存在
     */
    suspend fun exists(uri: String): Boolean
    
    /**
     * 获取文件信息
     * 
     * @param uri 文件 URI 或路径
     * @return 文件信息，如果不存在返回 null
     */
    suspend fun getFileInfo(uri: String): FileInfo?
    
    /**
     * 列出目录内容
     * 
     * @param uri 目录 URI 或路径
     * @return 文件信息列表，失败返回 Result.failure
     */
    suspend fun listDirectory(uri: String): Result<List<FileInfo>>
    
    /**
     * 创建目录
     * 
     * @param uri 目录 URI 或路径
     * @return 创建结果
     */
    suspend fun createDirectory(uri: String): Result<Unit>
    
    /**
     * 删除文件或目录
     * 
     * @param uri 文件或目录 URI 或路径
     * @return 删除结果
     */
    suspend fun delete(uri: String): Result<Unit>
    
    /**
     * 复制文件或目录
     * 
     * @param sourceUri 源 URI 或路径
     * @param targetUri 目标 URI 或路径
     * @return 复制结果
     */
    suspend fun copy(sourceUri: String, targetUri: String): Result<Unit>
    
    /**
     * 移动文件或目录
     * 
     * @param sourceUri 源 URI 或路径
     * @param targetUri 目标 URI 或路径
     * @return 移动结果
     */
    suspend fun move(sourceUri: String, targetUri: String): Result<Unit>
}

/**
 * 文件信息
 */
data class FileInfo(
    val uri: String,
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val isFile: Boolean,
    val mimeType: String? = null,
    val extension: String? = null
)

