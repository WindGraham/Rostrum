package com.rostrum.core.domain.usecase.bookmark

import com.rostrum.core.domain.model.Bookmark
import com.rostrum.core.domain.repository.BookmarkRepository
import com.rostrum.core.domain.usecase.NoParamsUseCase
import com.rostrum.core.domain.usecase.UseCase
import com.rostrum.core.domain.usecase.UseCaseResult

/**
 * 获取书签列表用例
 */
class GetBookmarksUseCase(
    private val repository: BookmarkRepository
) : NoParamsUseCase<List<Bookmark>> {
    
    override suspend fun invoke(): List<Bookmark> {
        return repository.getBookmarks()
    }
}

/**
 * 添加书签用例
 */
class AddBookmarkUseCase(
    private val repository: BookmarkRepository
) : UseCase<Bookmark, UseCaseResult<Unit>> {
    
    override suspend fun invoke(params: Bookmark): UseCaseResult<Unit> {
        return try {
            repository.addBookmark(params)
            UseCaseResult.Success(Unit)
        } catch (e: Exception) {
            UseCaseResult.Error(e)
        }
    }
}

/**
 * 删除书签用例
 */
class RemoveBookmarkUseCase(
    private val repository: BookmarkRepository
) : UseCase<String, UseCaseResult<Unit>> {
    
    override suspend fun invoke(params: String): UseCaseResult<Unit> {
        return try {
            repository.removeBookmark(params)
            UseCaseResult.Success(Unit)
        } catch (e: Exception) {
            UseCaseResult.Error(e)
        }
    }
}
