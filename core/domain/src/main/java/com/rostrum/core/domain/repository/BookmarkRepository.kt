package com.rostrum.core.domain.repository

import com.rostrum.core.domain.model.Bookmark

interface BookmarkRepository {
    fun getBookmarks(): List<Bookmark>
    fun addBookmark(bookmark: Bookmark)
    fun removeBookmark(path: String)
    fun updateBookmark(oldPath: String, newBookmark: Bookmark)
}