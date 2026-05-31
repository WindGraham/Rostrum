package com.rostrum.core.domain.model

import java.util.UUID

/**
 * 统一的书签数据模型
 */
data class Bookmark(
    val id: String = UUID.randomUUID().toString(),
    val path: String,
    val name: String,
    val isDirectory: Boolean = true,
    val dateAdded: Long = System.currentTimeMillis(),
    val note: String = ""
)

/**
 * 书签序列化/反序列化帮助类
 * 注：JSON转换在data层实现，避免domain层依赖Android类
 */
object BookmarkSerializer {
    fun toMap(bookmark: Bookmark): Map<String, Any?> {
        return mapOf(
            "id" to bookmark.id,
            "path" to bookmark.path,
            "name" to bookmark.name,
            "isDirectory" to bookmark.isDirectory,
            "dateAdded" to bookmark.dateAdded,
            "note" to bookmark.note
        )
    }
    
    fun fromMap(map: Map<String, Any?>): Bookmark {
        return Bookmark(
            id = map["id"] as? String ?: UUID.randomUUID().toString(),
            path = map["path"] as? String ?: "",
            name = map["name"] as? String ?: "",
            isDirectory = map["isDirectory"] as? Boolean ?: true,
            dateAdded = (map["dateAdded"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            note = map["note"] as? String ?: ""
        )
    }
}
