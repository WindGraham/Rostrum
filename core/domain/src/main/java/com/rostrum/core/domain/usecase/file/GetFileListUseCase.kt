package com.rostrum.core.domain.usecase.file

import com.rostrum.core.domain.error.AppResult
import com.rostrum.core.domain.model.FileItem
import com.rostrum.core.domain.repository.FileRepository

/**
 * 获取文件列表用例 - 解耦后的Clean Architecture实现
 * Domain层使用纯Kotlin，不依赖Hilt等框架
 */
class GetFileListUseCase(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(path: String): AppResult<List<FileItem>> {
        return fileRepository.getFiles(path)
    }
}
