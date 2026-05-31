package com.rostrum.core.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rostrum.core.domain.model.Bookmark
import com.rostrum.core.domain.repository.BookmarkRepository

/**
 * 书签仓储实现
 * 
 * 使用 SharedPreferences 存储书签数据
 * 已迁移至 core:data 模块
 */
class BookmarkRepositoryImpl(context: Context) : BookmarkRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "bookmark_list"

    override fun getBookmarks(): List<Bookmark> {
        val json = prefs.getString(key, null) ?: return emptyList()
        val type = object : TypeToken<List<Bookmark>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    override fun addBookmark(bookmark: Bookmark) {
        val list = getBookmarks().toMutableList()
        if (list.none { it.path == bookmark.path }) {
            list.add(bookmark)
            save(list)
        }
    }

    override fun removeBookmark(path: String) {
        val list = getBookmarks().filter { it.path != path }
        save(list)
    }

    override fun updateBookmark(oldPath: String, newBookmark: Bookmark) {
        val list = getBookmarks().toMutableList()
        val index = list.indexOfFirst { it.path == oldPath }
        if (index != -1) {
            list[index] = newBookmark
            save(list)
        }
    }

    private fun save(list: List<Bookmark>) {
        val json = gson.toJson(list)
        prefs.edit().putString(key, json).apply()
    }
}
