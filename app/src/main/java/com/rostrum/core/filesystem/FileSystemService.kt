package com.rostrum.core.filesystem

import com.rostrum.core.plugin.models.FileInfo

/**
 * 文件系统服务接口
 */
interface FileSystemService : FileSystemBackend {
    override val backendId: String
        get() = javaClass.simpleName.ifBlank { "filesystem" }

    override val kind: FileSystemBackendKind
        get() = FileSystemBackendKind.UNKNOWN

    /**
     * 读取文件内容
     * 
     * @param uri 文件URI或路径
     * @return 文件内容（字节数组）
     */
    suspend fun readFile(uri: String): Result<ByteArray>
    
    /**
     * 写入文件内容
     * 
     * @param uri 文件URI或路径
     * @param content 文件内容（字节数组）
     * @return 写入结果
     */
    suspend fun writeFile(uri: String, content: ByteArray): Result<Unit>
    
    /**
     * 读取文本文件
     * 
     * @param uri 文件URI或路径
     * @param encoding 字符编码，默认为UTF-8
     * @return 文件内容（字符串）
     */
    suspend fun readTextFile(uri: String, encoding: String = "UTF-8"): Result<String>
    
    /**
     * 写入文本文件
     * 
     * @param uri 文件URI或路径
     * @param content 文件内容（字符串）
     * @param encoding 字符编码，默认为UTF-8
     * @return 写入结果
     */
    suspend fun writeTextFile(uri: String, content: String, encoding: String = "UTF-8"): Result<Unit>
    
    /**
     * 检查文件是否存在
     * 
     * @param uri 文件URI或路径
     * @return 是否存在
     */
    override suspend fun exists(uri: String): Boolean
    
    /**
     * 获取文件信息
     * 
     * @param uri 文件URI或路径
     * @return 文件信息，如果不存在返回null
     */
    suspend fun getFileInfo(uri: String): FileInfo?
    
    /**
     * 列出目录内容
     * 
     * @param uri 目录URI或路径
     * @return 文件信息列表
     */
    suspend fun listDirectory(uri: String): Result<List<FileInfo>>
    
    /**
     * 创建目录
     * 
     * @param uri 目录URI或路径
     * @return 创建结果
     */
    suspend fun createDirectory(uri: String): Result<Unit>
    
    /**
     * 删除文件或目录
     * 
     * @param uri 文件或目录URI或路径
     * @return 删除结果
     */
    override suspend fun delete(uri: String): Result<Unit>
    
    /**
     * 复制文件或目录
     * 
     * @param sourceUri 源URI或路径
     * @param targetUri 目标URI或路径
     * @return 复制结果
     */
    override suspend fun copy(sourceUri: String, targetUri: String): Result<Unit>
    
    /**
     * 移动文件或目录
     * 
     * @param sourceUri 源URI或路径
     * @param targetUri 目标URI或路径
     * @return 移动结果
     */
    override suspend fun move(sourceUri: String, targetUri: String): Result<Unit>

    override suspend fun read(path: String): Result<ByteArray> = readFile(path)

    override suspend fun write(path: String, content: ByteArray): Result<Unit> = writeFile(path, content)

    override suspend fun readText(path: String, encoding: String): Result<String> = readTextFile(path, encoding)

    override suspend fun writeText(path: String, content: String, encoding: String): Result<Unit> =
        writeTextFile(path, content, encoding)

    override suspend fun stat(path: String): FileInfo? = getFileInfo(path)

    override suspend fun list(path: String): Result<List<FileInfo>> = listDirectory(path)

    override suspend fun mkdir(path: String): Result<Unit> = createDirectory(path)
}
