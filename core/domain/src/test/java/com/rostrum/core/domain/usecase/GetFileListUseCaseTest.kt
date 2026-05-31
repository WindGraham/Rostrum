package com.rostrum.core.domain.usecase

import com.rostrum.core.domain.error.AppError
import com.rostrum.core.domain.model.FileItem
import com.rostrum.core.domain.repository.FileRepository
import com.rostrum.core.domain.usecase.file.GetFileListUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * GetFileListUseCase 单元测试
 * 
 * 展示 Domain 层独立测试的能力（无需 Android 依赖）
 */
class GetFileListUseCaseTest {
    
    /**
     * 模拟的 FileRepository 实现
     */
    private class FakeFileRepository : FileRepository {
        var shouldReturnError = false
        var filesToReturn = emptyList<FileItem>()
        
        override suspend fun getFiles(path: String): com.rostrum.core.domain.error.AppResult<List<FileItem>> {
            return if (shouldReturnError) {
                Result.failure(
                    AppError.FileError(
                        path = path,
                        operation = com.rostrum.core.domain.error.FileOperation.READ,
                        message = "测试错误"
                    )
                )
            } else {
                Result.success(filesToReturn)
            }
        }
        
        override suspend fun copyFiles(files: List<FileItem>, destination: String) = 
            Result.success(Unit)
        
        override suspend fun moveFiles(files: List<FileItem>, destination: String) = 
            Result.success(Unit)
        
        override suspend fun deleteFiles(files: List<FileItem>) = 
            Result.success(Unit)
        
        override suspend fun createDirectory(path: String) = 
            Result.success(Unit)
    }
    
    @Test
    fun `invoke returns success when repository returns files`() = runBlocking {
        // Given
        val fakeRepository = FakeFileRepository()
        val expectedFiles = listOf(
            FileItem(
                name = "file1.txt", 
                path = "/test/file1.txt", 
                size = 100L,
                lastModified = 0L,
                isDirectory = false,
                extension = "txt"
            ),
            FileItem(
                name = "file2.txt", 
                path = "/test/file2.txt",
                size = 200L,
                lastModified = 0L,
                isDirectory = false,
                extension = "txt"
            )
        )
        fakeRepository.filesToReturn = expectedFiles
        
        val useCase = GetFileListUseCase(fakeRepository)
        
        // When
        val result = useCase("/test")
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedFiles, result.getOrNull())
    }
    
    @Test
    fun `invoke returns error when repository fails`() = runBlocking {
        // Given
        val fakeRepository = FakeFileRepository()
        fakeRepository.shouldReturnError = true
        
        val useCase = GetFileListUseCase(fakeRepository)
        
        // When
        val result = useCase("/test")
        
        // Then
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `invoke returns empty list when no files`() = runBlocking {
        // Given
        val fakeRepository = FakeFileRepository()
        fakeRepository.filesToReturn = emptyList()
        
        val useCase = GetFileListUseCase(fakeRepository)
        
        // When
        val result = useCase("/test")
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(emptyList<FileItem>(), result.getOrNull())
    }
    
    @Test
    fun `use case invokes repository with correct path`() = runBlocking {
        // Given
        val testPath = "/storage/emulated/0/Documents"
        var capturedPath: String? = null
        
        val fakeRepository = object : FileRepository {
            override suspend fun getFiles(path: String): com.rostrum.core.domain.error.AppResult<List<FileItem>> {
                capturedPath = path
                return Result.success(emptyList())
            }
            override suspend fun copyFiles(files: List<FileItem>, destination: String) = Result.success(Unit)
            override suspend fun moveFiles(files: List<FileItem>, destination: String) = Result.success(Unit)
            override suspend fun deleteFiles(files: List<FileItem>) = Result.success(Unit)
            override suspend fun createDirectory(path: String) = Result.success(Unit)
        }
        
        val useCase = GetFileListUseCase(fakeRepository)
        
        // When
        useCase(testPath)
        
        // Then
        assertEquals(testPath, capturedPath)
    }
}
