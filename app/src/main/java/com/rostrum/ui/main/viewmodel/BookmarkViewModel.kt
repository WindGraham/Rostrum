package com.rostrum.ui.main.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rostrum.core.domain.model.Bookmark
import com.rostrum.core.domain.model.FileItem
import com.rostrum.core.domain.repository.BookmarkRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 书签管理的辅助类
 * 负责书签的增删改查操作
 * 
 * 作为 MainViewModel 的内部委托使用
 * 
 * 通过 Hilt 注入 BookmarkRepository
 */
@Singleton
class BookmarkViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository
) {
    
    // 书签列表
    var bookmarks by mutableStateOf<List<Bookmark>>(emptyList())
        private set
    
    init {
        loadBookmarks()
    }
    
    /**
     * 加载书签
     */
    fun loadBookmarks() {
        bookmarks = bookmarkRepository.getBookmarks()
    }
    
    /**
     * 添加书签
     */
    fun addBookmark(fileItem: FileItem) {
        val bookmark = Bookmark(
            path = fileItem.path,
            name = fileItem.name,
            isDirectory = fileItem.isDirectory
        )
        bookmarkRepository.addBookmark(bookmark)
        loadBookmarks()
    }
    
    /**
     * 添加指定路径为书签
     */
    fun addPathAsBookmark(path: String) {
        val file = File(path)
        val bookmark = Bookmark(
            path = path,
            name = file.name.ifEmpty { "Root" },
            isDirectory = true
        )
        bookmarkRepository.addBookmark(bookmark)
        loadBookmarks()
    }
    
    /**
     * 删除书签
     */
    fun removeBookmark(path: String) {
        bookmarkRepository.removeBookmark(path)
        loadBookmarks()
    }
    
    /**
     * 更新书签
     */
    fun updateBookmark(oldPath: String, newName: String, newPath: String) {
        val existingBookmarks = bookmarkRepository.getBookmarks()
        val oldBookmark = existingBookmarks.find { it.path == oldPath }
        if (oldBookmark != null) {
            val newBookmark = oldBookmark.copy(name = newName, path = newPath)
            bookmarkRepository.updateBookmark(oldPath, newBookmark)
            loadBookmarks()
        }
    }
}
