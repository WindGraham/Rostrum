package com.rostrum.core.domain.repository

import com.rostrum.core.domain.error.AppResult
import com.rostrum.core.domain.model.FileItem

/**
 * 文件仓库接口 - 模块间解耦关键
 */
interface FileRepository {
    suspend fun getFiles(path: String): AppResult<List<FileItem>>
    suspend fun copyFiles(files: List<FileItem>, destination: String): AppResult<Unit>
    suspend fun moveFiles(files: List<FileItem>, destination: String): AppResult<Unit>
    suspend fun deleteFiles(files: List<FileItem>): AppResult<Unit>
    suspend fun createDirectory(path: String): AppResult<Unit>
}
