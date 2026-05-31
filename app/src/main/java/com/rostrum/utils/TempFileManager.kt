package com.rostrum.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * MT式临时文件管理器
 * 
 * 实现类似 MT 管理器的临时文件处理功能：
 * 1. 接收其他应用分享的文件时，复制到临时目录
 * 2. 编辑完成后自动检测变化并提示保存回原位置
 * 3. 支持文件修改监控和差异检测
 * 4. 自动清理过期临时文件
 */
class TempFileManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "TempFileManager"
        private const val TEMP_DIR_NAME = ".omni_temp"
        private const val METADATA_FILE = "temp_files.meta"
        private const val FILE_CHECK_INTERVAL_MS = 2000L // 文件变化检测间隔
        private const val TEMP_FILE_EXPIRE_HOURS = 24L   // 临时文件过期时间
        
        @Volatile
        private var instance: TempFileManager? = null
        
        fun getInstance(context: Context): TempFileManager {
            return instance ?: synchronized(this) {
                instance ?: TempFileManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    
    // 临时文件目录
    private val tempDir: File by lazy {
        File(context.cacheDir, TEMP_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }
    
    // 临时文件映射：临时文件路径 -> 元数据
    private val tempFileMap = ConcurrentHashMap<String, TempFileMetadata>()
    
    // 文件变化监控状态
    private val _pendingChanges = MutableStateFlow<List<PendingFileChange>>(emptyList())
    val pendingChanges: StateFlow<List<PendingFileChange>> = _pendingChanges.asStateFlow()
    
    // 文件监控任务
    private var monitorJob: Job? = null
    
    init {
        // 加载已有的临时文件元数据
        loadMetadata()
        // 清理过期文件
        cleanupExpiredFiles()
        // 启动文件变化监控
        startFileMonitor()
    }
    
    /**
     * 临时文件元数据
     */
    data class TempFileMetadata(
        val tempPath: String,           // 临时文件路径
        val originalUri: String?,       // 原始 URI (来自其他应用)
        val originalPath: String?,      // 原始文件路径 (本地文件)
        val sourceName: String,         // 源文件名
        val sourceApp: String?,         // 来源应用包名
        val createdAt: Long,            // 创建时间
        val originalHash: String,       // 原始文件 MD5 哈希
        var lastModified: Long,         // 最后修改时间
        var currentHash: String         // 当前文件 MD5 哈希
    ) {
        val isModified: Boolean
            get() = originalHash != currentHash
    }
    
    /**
     * 待处理的文件变化
     */
    data class PendingFileChange(
        val tempPath: String,
        val originalPath: String?,
        val originalUri: String?,
        val fileName: String,
        val modifiedAt: Long
    )
    
    /**
     * 从 URI 创建临时文件 (接收外部分享)
     */
    suspend fun createTempFromUri(
        uri: Uri,
        displayName: String,
        sourceApp: String? = null
    ): File? = withContext(Dispatchers.IO) {
        try {
            // 生成唯一的临时文件名
            val timestamp = System.currentTimeMillis()
            val extension = displayName.substringAfterLast('.', "")
            val baseName = displayName.substringBeforeLast('.')
            val tempFileName = "${baseName}_${timestamp}.${extension}"
            val tempFile = File(tempDir, tempFileName)
            
            // 复制文件内容
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            
            // 计算文件哈希
            val hash = calculateFileHash(tempFile)
            
            // 保存元数据
            val metadata = TempFileMetadata(
                tempPath = tempFile.absolutePath,
                originalUri = uri.toString(),
                originalPath = null,
                sourceName = displayName,
                sourceApp = sourceApp,
                createdAt = timestamp,
                originalHash = hash,
                lastModified = tempFile.lastModified(),
                currentHash = hash
            )
            tempFileMap[tempFile.absolutePath] = metadata
            saveMetadata()
            
            Log.d(TAG, "Created temp file from URI: ${tempFile.absolutePath}")
            tempFile
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create temp file from URI", e)
            null
        }
    }
    
    /**
     * 从本地文件创建临时副本 (用于安全编辑)
     */
    suspend fun createTempFromFile(originalFile: File): File? = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val tempFileName = "${originalFile.nameWithoutExtension}_temp_$timestamp.${originalFile.extension}"
            val tempFile = File(tempDir, tempFileName)
            
            // 复制文件
            originalFile.copyTo(tempFile, overwrite = true)
            
            // 计算哈希
            val hash = calculateFileHash(originalFile)
            
            // 保存元数据
            val metadata = TempFileMetadata(
                tempPath = tempFile.absolutePath,
                originalUri = null,
                originalPath = originalFile.absolutePath,
                sourceName = originalFile.name,
                sourceApp = null,
                createdAt = timestamp,
                originalHash = hash,
                lastModified = tempFile.lastModified(),
                currentHash = hash
            )
            tempFileMap[tempFile.absolutePath] = metadata
            saveMetadata()
            
            Log.d(TAG, "Created temp file from local: ${tempFile.absolutePath}")
            tempFile
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create temp file from local file", e)
            null
        }
    }
    
    /**
     * 检查临时文件是否已修改
     */
    fun isFileModified(tempPath: String): Boolean {
        val metadata = tempFileMap[tempPath] ?: return false
        return metadata.isModified
    }
    
    /**
     * 获取临时文件的原始信息
     */
    fun getOriginalInfo(tempPath: String): TempFileMetadata? {
        return tempFileMap[tempPath]
    }
    
    /**
     * 将临时文件保存回原位置
     */
    suspend fun saveBackToOriginal(tempPath: String): Boolean = withContext(Dispatchers.IO) {
        val metadata = tempFileMap[tempPath] ?: return@withContext false
        val tempFile = File(tempPath)
        
        if (!tempFile.exists()) {
            Log.e(TAG, "Temp file not found: $tempPath")
            return@withContext false
        }
        
        try {
            when {
                // 保存回本地文件
                metadata.originalPath != null -> {
                    val originalFile = File(metadata.originalPath)
                    tempFile.copyTo(originalFile, overwrite = true)
                    Log.d(TAG, "Saved back to local: ${metadata.originalPath}")
                    
                    // 更新哈希
                    val newHash = calculateFileHash(tempFile)
                    tempFileMap[tempPath] = metadata.copy(
                        originalHash = newHash,
                        currentHash = newHash,
                        lastModified = tempFile.lastModified()
                    )
                    saveMetadata()
                    true
                }
                
                // 保存回 URI (需要权限)
                metadata.originalUri != null -> {
                    val uri = Uri.parse(metadata.originalUri)
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        FileInputStream(tempFile).use { input ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Saved back to URI: ${metadata.originalUri}")
                    
                    // 更新哈希
                    val newHash = calculateFileHash(tempFile)
                    tempFileMap[tempPath] = metadata.copy(
                        originalHash = newHash,
                        currentHash = newHash,
                        lastModified = tempFile.lastModified()
                    )
                    saveMetadata()
                    true
                }
                
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save back to original", e)
            false
        }
    }
    
    /**
     * 另存为新文件
     */
    suspend fun saveAs(tempPath: String, targetFile: File): Boolean = withContext(Dispatchers.IO) {
        val tempFile = File(tempPath)
        if (!tempFile.exists()) return@withContext false
        
        try {
            tempFile.copyTo(targetFile, overwrite = true)
            Log.d(TAG, "Saved as: ${targetFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save as", e)
            false
        }
    }
    
    /**
     * 放弃临时文件的更改
     */
    suspend fun discardChanges(tempPath: String): Boolean = withContext(Dispatchers.IO) {
        val metadata = tempFileMap[tempPath] ?: return@withContext false
        
        try {
            when {
                metadata.originalPath != null -> {
                    // 从原始文件恢复
                    val originalFile = File(metadata.originalPath)
                    if (originalFile.exists()) {
                        originalFile.copyTo(File(tempPath), overwrite = true)
                        
                        // 更新元数据
                        tempFileMap[tempPath] = metadata.copy(
                            currentHash = metadata.originalHash,
                            lastModified = File(tempPath).lastModified()
                        )
                        saveMetadata()
                        
                        // 移除待处理变化
                        updatePendingChanges()
                        true
                    } else false
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to discard changes", e)
            false
        }
    }
    
    /**
     * 删除临时文件
     */
    fun deleteTempFile(tempPath: String): Boolean {
        return try {
            val file = File(tempPath)
            if (file.exists()) {
                file.delete()
            }
            tempFileMap.remove(tempPath)
            saveMetadata()
            updatePendingChanges()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete temp file", e)
            false
        }
    }
    
    /**
     * 清除所有临时文件
     */
    fun clearAllTempFiles() {
        tempDir.listFiles()?.forEach { it.delete() }
        tempFileMap.clear()
        saveMetadata()
        _pendingChanges.value = emptyList()
    }
    
    /**
     * 获取临时文件的 FileProvider URI (用于分享给其他应用)
     */
    fun getTempFileUri(tempFile: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            tempFile
        )
    }
    
    /**
     * 获取所有临时文件
     */
    fun getAllTempFiles(): List<TempFileMetadata> {
        return tempFileMap.values.toList()
    }
    
    // ============ 私有方法 ============
    
    private fun startFileMonitor() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (true) {
                delay(FILE_CHECK_INTERVAL_MS)
                checkFileChanges()
            }
        }
    }
    
    private suspend fun checkFileChanges() = withContext(Dispatchers.IO) {
        var hasChanges = false
        
        tempFileMap.forEach { (path, metadata) ->
            val file = File(path)
            if (file.exists()) {
                val currentModified = file.lastModified()
                if (currentModified != metadata.lastModified) {
                    // 文件已修改，重新计算哈希
                    val newHash = calculateFileHash(file)
                    if (newHash != metadata.currentHash) {
                        tempFileMap[path] = metadata.copy(
                            lastModified = currentModified,
                            currentHash = newHash
                        )
                        hasChanges = true
                        Log.d(TAG, "File changed: $path")
                    }
                }
            }
        }
        
        if (hasChanges) {
            saveMetadata()
            updatePendingChanges()
        }
    }
    
    private fun updatePendingChanges() {
        val changes = tempFileMap.values
            .filter { it.isModified }
            .map { metadata ->
                PendingFileChange(
                    tempPath = metadata.tempPath,
                    originalPath = metadata.originalPath,
                    originalUri = metadata.originalUri,
                    fileName = metadata.sourceName,
                    modifiedAt = metadata.lastModified
                )
            }
        _pendingChanges.value = changes
    }
    
    private fun calculateFileHash(file: File): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate hash", e)
            ""
        }
    }
    
    private fun cleanupExpiredFiles() {
        scope.launch {
            val expireTime = System.currentTimeMillis() - (TEMP_FILE_EXPIRE_HOURS * 60 * 60 * 1000)
            val expiredKeys = mutableListOf<String>()
            
            tempFileMap.forEach { (path, metadata) ->
                if (metadata.createdAt < expireTime && !metadata.isModified) {
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                    expiredKeys.add(path)
                }
            }
            
            expiredKeys.forEach { tempFileMap.remove(it) }
            if (expiredKeys.isNotEmpty()) {
                saveMetadata()
                Log.d(TAG, "Cleaned up ${expiredKeys.size} expired temp files")
            }
        }
    }
    
    private fun loadMetadata() {
        try {
            val metaFile = File(tempDir, METADATA_FILE)
            if (metaFile.exists()) {
                metaFile.readLines().forEach { line ->
                    val parts = line.split("|")
                    if (parts.size >= 9) {
                        val metadata = TempFileMetadata(
                            tempPath = parts[0],
                            originalUri = parts[1].takeIf { it != "null" },
                            originalPath = parts[2].takeIf { it != "null" },
                            sourceName = parts[3],
                            sourceApp = parts[4].takeIf { it != "null" },
                            createdAt = parts[5].toLongOrNull() ?: 0,
                            originalHash = parts[6],
                            lastModified = parts[7].toLongOrNull() ?: 0,
                            currentHash = parts[8]
                        )
                        // 只加载仍然存在的文件
                        if (File(metadata.tempPath).exists()) {
                            tempFileMap[metadata.tempPath] = metadata
                        }
                    }
                }
                Log.d(TAG, "Loaded ${tempFileMap.size} temp file metadata")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load metadata", e)
        }
    }
    
    private fun saveMetadata() {
        try {
            val metaFile = File(tempDir, METADATA_FILE)
            val lines = tempFileMap.values.map { m ->
                "${m.tempPath}|${m.originalUri ?: "null"}|${m.originalPath ?: "null"}|${m.sourceName}|${m.sourceApp ?: "null"}|${m.createdAt}|${m.originalHash}|${m.lastModified}|${m.currentHash}"
            }
            metaFile.writeText(lines.joinToString("\n"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save metadata", e)
        }
    }
    
    /**
     * 停止监控（应用退出时调用）
     */
    fun stopMonitor() {
        monitorJob?.cancel()
        monitorJob = null
    }
}
