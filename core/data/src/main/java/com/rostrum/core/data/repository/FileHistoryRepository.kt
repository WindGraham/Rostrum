package com.rostrum.core.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.rostrum.core.domain.model.FileItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 文件访问历史记录项
 */
@Serializable
data class FileHistoryEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val accessType: AccessType = AccessType.VIEW
) {
    enum class AccessType {
        VIEW,      // 查看/预览
        EDIT,      // 编辑
        NAVIGATE   // 导航进入目录
    }
}

/**
 * 文件历史记录仓库接口
 */
interface FileHistoryRepository {
    /**
     * 获取所有历史记录（按时间倒序）
     */
    fun getAllHistory(): List<FileHistoryEntry>
    
    /**
     * 观察历史记录变化
     */
    fun observeHistory(): Flow<List<FileHistoryEntry>>
    
    /**
     * 添加历史记录
     */
    suspend fun addEntry(entry: FileHistoryEntry)
    
    /**
     * 添加文件访问记录（便捷方法）
     */
    suspend fun addFileAccess(fileItem: FileItem, accessType: FileHistoryEntry.AccessType = FileHistoryEntry.AccessType.VIEW)
    
    /**
     * 添加路径访问记录（便捷方法）
     */
    suspend fun addPathAccess(path: String, isDirectory: Boolean = false, accessType: FileHistoryEntry.AccessType = FileHistoryEntry.AccessType.NAVIGATE)
    
    /**
     * 删除单条记录
     */
    suspend fun removeEntry(path: String)
    
    /**
     * 清空历史
     */
    suspend fun clearHistory()
    
    /**
     * 获取最近访问的文件（排除目录）
     */
    fun getRecentFiles(limit: Int = 20): List<FileHistoryEntry>
    
    /**
     * 获取最近访问的目录
     */
    fun getRecentDirectories(limit: Int = 10): List<FileHistoryEntry>
    
    /**
     * 检查路径是否已存在于历史中
     */
    fun contains(path: String): Boolean
}

/**
 * 文件历史记录仓库实现
 */
class FileHistoryRepositoryImpl(context: Context) : FileHistoryRepository {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    
    private val _historyFlow = MutableStateFlow<List<FileHistoryEntry>>(emptyList())
    
    init {
        // 初始化时加载历史
        _historyFlow.value = loadHistory()
    }
    
    override fun getAllHistory(): List<FileHistoryEntry> {
        return _historyFlow.value
    }
    
    override fun observeHistory(): Flow<List<FileHistoryEntry>> {
        return _historyFlow.asStateFlow()
    }
    
    override suspend fun addEntry(entry: FileHistoryEntry) {
        val currentList = loadHistory().toMutableList()
        
        // 移除已存在的相同路径记录（避免重复）
        currentList.removeAll { it.path == entry.path }
        
        // 添加到开头（最新的在前）
        currentList.add(0, entry)
        
        // 限制最大记录数
        val trimmedList = currentList.take(MAX_HISTORY_SIZE)
        
        saveHistory(trimmedList)
        _historyFlow.value = trimmedList
    }
    
    override suspend fun addFileAccess(fileItem: FileItem, accessType: FileHistoryEntry.AccessType) {
        addEntry(
            FileHistoryEntry(
                path = fileItem.path,
                name = fileItem.name,
                isDirectory = fileItem.isDirectory,
                accessType = accessType
            )
        )
    }
    
    override suspend fun addPathAccess(path: String, isDirectory: Boolean, accessType: FileHistoryEntry.AccessType) {
        val name = path.substringAfterLast('/', path)
        addEntry(
            FileHistoryEntry(
                path = path,
                name = name,
                isDirectory = isDirectory,
                accessType = accessType
            )
        )
    }
    
    override suspend fun removeEntry(path: String) {
        val currentList = loadHistory().filter { it.path != path }
        saveHistory(currentList)
        _historyFlow.value = currentList
    }
    
    override suspend fun clearHistory() {
        saveHistory(emptyList())
        _historyFlow.value = emptyList()
    }
    
    override fun getRecentFiles(limit: Int): List<FileHistoryEntry> {
        return _historyFlow.value
            .filter { !it.isDirectory }
            .take(limit)
    }
    
    override fun getRecentDirectories(limit: Int): List<FileHistoryEntry> {
        return _historyFlow.value
            .filter { it.isDirectory }
            .take(limit)
    }
    
    override fun contains(path: String): Boolean {
        return _historyFlow.value.any { it.path == path }
    }
    
    private fun loadHistory(): List<FileHistoryEntry> {
        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<FileHistoryEntry>>(jsonString)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load history", e)
            emptyList()
        }
    }
    
    private fun saveHistory(history: List<FileHistoryEntry>) {
        try {
            val jsonString = json.encodeToString(history)
            prefs.edit().putString(KEY_HISTORY, jsonString).apply()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save history", e)
        }
    }
    
    companion object {
        private const val TAG = "FileHistoryRepository"
        private const val PREFS_NAME = "omnimaster_file_history"
        private const val KEY_HISTORY = "file_history_json"
        private const val MAX_HISTORY_SIZE = 100 // 最大保存100条记录
    }
}
