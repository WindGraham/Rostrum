package com.rostrum.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.rostrum.ui.theme.RostrumTheme
import com.rostrum.utils.TempFileManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * 主Activity，应用入口
 * 
 * @AndroidEntryPoint 启用 Hilt 依赖注入
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        
        // 用于保存从外部 Intent 传入的文件路径
        var pendingFilePath = mutableStateOf<String?>(null)
            private set
        
        // 临时文件管理器引用
        var tempFileManager: TempFileManager? = null
            private set
        
        /**
         * 清除待处理的文件路径
         */
        fun clearPendingFile() {
            pendingFilePath.value = null
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.Main)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化临时文件管理器
        tempFileManager = TempFileManager.getInstance(this)
        
        // 处理外部文件打开 Intent
        handleIntent(intent)
        
        setContent {
            RostrumTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        tempFileManager?.stopMonitor()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 处理新的 Intent（当 Activity 已经在前台时）
        handleIntent(intent)
    }
    
    /**
     * 处理文件打开 Intent
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        
        val action = intent.action
        val data = intent.data
        
        Log.d(TAG, "Handling intent: action=$action, data=$data")
        
        when (action) {
            Intent.ACTION_VIEW -> {
                // 从 Intent 获取文件路径
                data?.let { uri ->
                    val filePath = getFilePathFromUri(uri)
                    if (filePath != null) {
                        Log.d(TAG, "Opening file from intent: $filePath")
                        pendingFilePath.value = filePath
                    } else {
                        Log.w(TAG, "Could not resolve file path from URI: $uri")
                    }
                }
            }
            Intent.ACTION_SEND -> {
                // 接收单个分享文件 - 使用临时文件管理器
                @Suppress("DEPRECATION")
                (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uri ->
                    handleSharedUri(uri, intent.`package`)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                // 接收多个分享文件（取第一个）
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()?.let { uri ->
                    handleSharedUri(uri, intent.`package`)
                }
            }
        }
    }
    
    /**
     * 从 URI 获取文件路径
     */
    private fun getFilePathFromUri(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file" -> {
                    // file:// 类型的 URI，直接获取路径
                    uri.path
                }
                "content" -> {
                    // content:// 类型的 URI，需要通过 ContentResolver 获取
                    getPathFromContentUri(uri)
                }
                else -> {
                    Log.w(TAG, "Unsupported URI scheme: ${uri.scheme}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file path from URI", e)
            null
        }
    }
    
    /**
     * 从 content:// URI 获取文件路径
     */
    private fun getPathFromContentUri(uri: Uri): String? {
        // 尝试直接获取路径（对于一些文件管理器可能有效）
        val path = uri.path
        if (path != null && File(path).exists()) {
            return path
        }
        
        // 尝试从 document URI 获取
        try {
            // 对于 DocumentProvider
            if (android.provider.DocumentsContract.isDocumentUri(this, uri)) {
                val docId = android.provider.DocumentsContract.getDocumentId(uri)
                
                when {
                    // ExternalStorageProvider
                    uri.authority == "com.android.externalstorage.documents" -> {
                        val split = docId.split(":")
                        val type = split[0]
                        if ("primary".equals(type, ignoreCase = true)) {
                            return "${android.os.Environment.getExternalStorageDirectory()}/${split.getOrElse(1) { "" }}"
                        }
                    }
                    // DownloadsProvider
                    uri.authority == "com.android.providers.downloads.documents" -> {
                        // 处理下载目录
                        if (docId.startsWith("raw:")) {
                            return docId.removePrefix("raw:")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing document URI", e)
        }
        
        // 尝试使用 ContentResolver 获取文件名，然后复制到缓存目录
        try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val fileName = cursor.getString(nameIndex)
                        // 将文件复制到缓存目录以便访问
                        val cacheFile = File(cacheDir, "external_files/$fileName")
                        cacheFile.parentFile?.mkdirs()
                        
                        contentResolver.openInputStream(uri)?.use { input ->
                            cacheFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        if (cacheFile.exists()) {
                            Log.d(TAG, "Copied content to cache: ${cacheFile.absolutePath}")
                            return cacheFile.absolutePath
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying content to cache", e)
        }
        
        return null
    }
    
    /**
     * 处理分享的 URI - 创建临时文件
     */
    private fun handleSharedUri(uri: Uri, sourcePackage: String?) {
        scope.launch(Dispatchers.IO) {
            try {
                // 获取文件名
                val displayName = getDisplayNameFromUri(uri) ?: "shared_file_${System.currentTimeMillis()}"
                
                Log.d(TAG, "Handling shared URI: $uri, name: $displayName, from: $sourcePackage")
                
                // 创建临时文件
                val tempFile = tempFileManager?.createTempFromUri(uri, displayName, sourcePackage)
                
                if (tempFile != null) {
                    Log.d(TAG, "Created temp file: ${tempFile.absolutePath}")
                    // 在主线程更新 UI
                    scope.launch(Dispatchers.Main) {
                        pendingFilePath.value = tempFile.absolutePath
                    }
                } else {
                    // 降级处理：直接尝试获取文件路径
                    val filePath = getFilePathFromUri(uri)
                    if (filePath != null) {
                        scope.launch(Dispatchers.Main) {
                            pendingFilePath.value = filePath
                        }
                    } else {
                        Log.w(TAG, "Could not handle shared URI: $uri")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling shared URI", e)
            }
        }
    }
    
    /**
     * 从 URI 获取显示名称
     */
    private fun getDisplayNameFromUri(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        cursor.getString(nameIndex)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting display name", e)
            null
        }
    }
}
