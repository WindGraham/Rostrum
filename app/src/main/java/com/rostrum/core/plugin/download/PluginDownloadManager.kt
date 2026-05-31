package com.rostrum.core.plugin.download

import android.content.Context
import android.net.Uri
import com.rostrum.core.plugin.PluginManifest
import com.rostrum.core.plugin.PluginPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * 插件下载管理器
 * 
 * 负责从远程或本地源下载插件
 */
class PluginDownloadManager(private val context: Context) {
    
    private val downloadDir = File(context.filesDir, "downloaded_plugins")
    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()
    
    init {
        downloadDir.mkdirs()
    }
    
    /**
     * 下载插件
     */
    suspend fun downloadPlugin(
        pluginId: String,
        downloadUrl: String,
        manifest: PluginManifest
    ): PluginPackage {
        return withContext(Dispatchers.IO) {
            val progressFlow = MutableStateFlow(0f)
            _downloadProgress.value = _downloadProgress.value + (pluginId to DownloadProgress(0f, "正在下载..."))
            
            try {
                val url = URL(downloadUrl)
                val connection = url.openConnection()
                val contentLength = connection.contentLength.toLong()
                
                connection.inputStream.use { inputStream ->
                    val pluginFile = File(downloadDir, "$pluginId.apk")
                    FileOutputStream(pluginFile).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var totalBytesRead = 0L
                        var bytesRead: Int
                        
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            
                            val progress = if (contentLength > 0) {
                                (totalBytesRead.toFloat() / contentLength).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            
                            _downloadProgress.value = _downloadProgress.value + (
                                pluginId to DownloadProgress(
                                    progress = progress,
                                    status = "下载中... ${(progress * 100).toInt()}%"
                                )
                            )
                        }
                    }
                    
                    _downloadProgress.value = _downloadProgress.value + (
                        pluginId to DownloadProgress(1f, "下载完成")
                    )
                    
                    PluginPackage(
                        id = manifest.id,
                        version = manifest.version,
                        filePath = pluginFile.absolutePath,
                        manifest = manifest
                    )
                }
            } catch (e: Exception) {
                _downloadProgress.value = _downloadProgress.value + (
                    pluginId to DownloadProgress(0f, "下载失败: ${e.message}")
                )
                throw e
            }
        }
    }
    
    /**
     * 从本地文件安装插件
     */
    suspend fun installFromLocalFile(fileUri: Uri, manifest: PluginManifest): PluginPackage {
        return withContext(Dispatchers.IO) {
            val pluginFile = File(downloadDir, "${manifest.id}.apk")
            
            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                FileOutputStream(pluginFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IllegalArgumentException("Cannot read file: $fileUri")
            
            PluginPackage(
                id = manifest.id,
                version = manifest.version,
                filePath = pluginFile.absolutePath,
                manifest = manifest
            )
        }
    }
    
    /**
     * 获取已下载的插件
     */
    fun getDownloadedPlugin(pluginId: String): PluginPackage? {
        val pluginFile = File(downloadDir, "$pluginId.apk")
        return if (pluginFile.exists()) {
            // TODO: 从文件读取metadata
            null
        } else {
            null
        }
    }
    
    /**
     * 删除下载的插件
     */
    fun deleteDownloadedPlugin(pluginId: String): Boolean {
        val pluginFile = File(downloadDir, "$pluginId.apk")
        return pluginFile.delete()
    }
}

/**
 * 下载进度
 */
data class DownloadProgress(
    val progress: Float,  // 0.0 - 1.0
    val status: String
)

