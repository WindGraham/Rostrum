package com.rostrum.core.domain.usecase.file

import com.rostrum.core.domain.error.AppResult
import com.rostrum.core.domain.model.FileItem
import com.rostrum.core.domain.repository.FileRepository
class CopyFilesUseCase(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(
        files: List<FileItem>,
        destination: String
    ): AppResult<Unit> {
        return fileRepository.copyFiles(files, destination)
    }
}
