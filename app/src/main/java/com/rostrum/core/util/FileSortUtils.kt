package com.rostrum.core.util

import com.rostrum.core.domain.model.FileItem
import java.text.Collator
import java.util.*

/**
 * 文件排序类型
 */
enum class SortType {
    NAME,      // 名称
    SIZE,      // 大小
    DATE,      // 修改日期
    TYPE       // 类型（扩展名）
}

/**
 * 排序顺序
 */
enum class SortOrder {
    ASCENDING,  // 升序
    DESCENDING  // 降序
}

/**
 * 文件排序工具类
 */
object FileSortUtils {
    
    /**
     * 对文件列表进行排序
     */
    fun sortFiles(files: List<FileItem>, sortType: SortType, sortOrder: SortOrder): List<FileItem> {
        val comparator = when (sortType) {
            SortType.NAME -> compareBy<FileItem> { it.name.lowercase() }
            SortType.SIZE -> compareBy<FileItem> { it.size }
            SortType.DATE -> compareBy<FileItem> { it.lastModified }
            SortType.TYPE -> compareBy<FileItem> { it.extension }
        }
        
        // 文件夹始终在前
        val folderFirstComparator = compareBy<FileItem> { !it.isDirectory }.then(comparator)
        
        // 应用排序顺序
        val finalComparator = if (sortOrder == SortOrder.DESCENDING) {
            folderFirstComparator.reversed()
        } else {
            folderFirstComparator
        }
        
        return files.sortedWith(finalComparator)
    }
    
    /**
     * 使用中文排序（考虑中文字符）
     */
    fun sortFilesWithCollator(files: List<FileItem>, sortType: SortType, sortOrder: SortOrder): List<FileItem> {
        val collator = Collator.getInstance(Locale.getDefault())
        collator.strength = Collator.PRIMARY
        
        val comparator = when (sortType) {
            SortType.NAME -> Comparator<FileItem> { a, b ->
                // 文件夹在前
                when {
                    a.isDirectory && !b.isDirectory -> -1
                    !a.isDirectory && b.isDirectory -> 1
                    else -> collator.compare(a.name.lowercase(), b.name.lowercase())
                }
            }
            SortType.SIZE -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.size }
            SortType.DATE -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.lastModified }
            SortType.TYPE -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.extension }
        }
        
        return if (sortOrder == SortOrder.DESCENDING) {
            files.sortedWith(comparator.reversed())
        } else {
            files.sortedWith(comparator)
        }
    }
}

