package com.rostrum.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

/**
 * Root权限管理器
 * 类似MT管理器的Root权限处理方式
 */
object RootHelper {
    private const val TAG = "RootHelper"
    
    // 缓存Root状态
    private var rootAvailable: Boolean? = null
    private var rootProcess: Process? = null
    private var rootOutputStream: DataOutputStream? = null
    
    // 常见的su命令路径
    private val suPaths = listOf(
        "su",
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/su/bin/su",
        "/magisk/.core/bin/su"
    )
    
    /**
     * 检测设备是否有Root权限（异步版本）
     */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        // 使用缓存的结果
        rootAvailable?.let { return@withContext it }
        
        val result = checkRootAvailability()
        rootAvailable = result
        result
    }
    
    /**
     * 检测设备是否有Root权限（同步版本，使用缓存）
     * 如果尚未检测过，返回 false（不阻塞）
     */
    fun isRootAvailableSync(): Boolean {
        // 使用缓存的结果，如果没有缓存则返回 false
        return rootAvailable ?: false
    }
    
    private fun checkRootAvailability(): Boolean {
        // 方法1: 检查su二进制文件是否存在
        for (path in suPaths) {
            try {
                val file = File(path)
                if (file.exists()) {
                    Log.d(TAG, "Found su at: $path")
                    // 尝试执行su来确认真的可用
                    if (testSuExecution(path)) {
                        return true
                    }
                }
            } catch (e: Exception) {
                // 忽略权限错误
            }
        }
        
        // 方法2: 直接尝试执行su
        return testSuExecution("su")
    }
    
    private fun testSuExecution(suCommand: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf(suCommand, "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            process.waitFor()
            reader.close()
            
            val hasRoot = output?.contains("uid=0") == true
            Log.d(TAG, "Su test with '$suCommand': $hasRoot, output: $output")
            hasRoot
        } catch (e: Exception) {
            Log.d(TAG, "Su test failed with '$suCommand': ${e.message}")
            false
        }
    }
    
    /**
     * 请求Root权限（会弹出Root授权对话框）
     */
    suspend fun requestRootAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            
            // 发送一个简单的命令来触发Root授权
            outputStream.writeBytes("id\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            
            val exitCode = process.waitFor()
            val success = exitCode == 0
            
            if (success) {
                rootAvailable = true
                Log.d(TAG, "Root access granted")
            } else {
                Log.d(TAG, "Root access denied, exit code: $exitCode")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request root: ${e.message}")
            false
        }
    }
    
    /**
     * 使用Root执行命令并获取输出
     */
    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            val inputReader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            outputStream.writeBytes("$command\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            
            val output = StringBuilder()
            var line: String?
            while (inputReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            val error = StringBuilder()
            while (errorReader.readLine().also { line = it } != null) {
                error.append(line).append("\n")
            }
            
            val exitCode = process.waitFor()
            
            inputReader.close()
            errorReader.close()
            outputStream.close()
            
            CommandResult(
                success = exitCode == 0,
                output = output.toString().trim(),
                error = error.toString().trim(),
                exitCode = exitCode
            )
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed: ${e.message}")
            CommandResult(
                success = false,
                output = "",
                error = e.message ?: "Unknown error",
                exitCode = -1
            )
        }
    }
    
    /**
     * 使用Root列出目录内容
     */
    suspend fun listDirectory(path: String): List<FileInfo>? = withContext(Dispatchers.IO) {
        val result = executeCommand("ls -la \"$path\"")
        
        if (!result.success) {
            Log.e(TAG, "Failed to list directory: ${result.error}")
            return@withContext null
        }
        
        parseListOutput(result.output, path)
    }
    
    /**
     * 解析ls -la输出
     */
    private fun parseListOutput(output: String, basePath: String): List<FileInfo> {
        val files = mutableListOf<FileInfo>()
        val lines = output.lines()
        
        for (line in lines) {
            if (line.isBlank() || line.startsWith("total")) continue
            
            // ls -la 输出格式:
            // drwxrwx--x  4 system system  4096 2024-01-15 10:30 com.example.app
            // -rw-r--r--  1 root   root    1234 2024-01-15 10:30 file.txt
            val parts = line.split(Regex("\\s+"), limit = 9)
            if (parts.size >= 9) {
                val permissions = parts[0]
                val name = parts[8]
                
                // 跳过 . 和 ..
                if (name == "." || name == "..") continue
                
                val isDirectory = permissions.startsWith("d")
                val isSymlink = permissions.startsWith("l")
                
                // 处理符号链接
                val actualName = if (isSymlink && name.contains(" -> ")) {
                    name.substringBefore(" -> ")
                } else {
                    name
                }
                
                files.add(FileInfo(
                    name = actualName,
                    path = "$basePath/$actualName",
                    isDirectory = isDirectory,
                    isSymlink = isSymlink,
                    permissions = permissions,
                    size = parts[4].toLongOrNull() ?: 0,
                    owner = parts[2],
                    group = parts[3]
                ))
            }
        }
        
        return files
    }
    
    /**
     * 使用Root读取文件内容
     */
    suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        val result = executeCommand("cat \"$path\"")
        if (result.success) result.output else null
    }
    
    /**
     * 使用Root检查文件/目录是否存在
     */
    suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        val result = executeCommand("[ -e \"$path\" ] && echo 'exists' || echo 'not exists'")
        result.success && result.output.contains("exists")
    }
    
    /**
     * 使用Root复制文件
     */
    suspend fun copyFile(source: String, dest: String): Boolean = withContext(Dispatchers.IO) {
        val result = executeCommand("cp -f \"$source\" \"$dest\"")
        result.success
    }
    
    /**
     * 使用Root移动文件
     */
    suspend fun moveFile(source: String, dest: String): Boolean = withContext(Dispatchers.IO) {
        val result = executeCommand("mv -f \"$source\" \"$dest\"")
        result.success
    }
    
    /**
     * 使用Root删除文件
     */
    suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        val result = executeCommand("rm -rf \"$path\"")
        result.success
    }
    
    /**
     * 使用Root创建目录
     */
    suspend fun createDirectory(path: String): Boolean = withContext(Dispatchers.IO) {
        val result = executeCommand("mkdir -p \"$path\"")
        result.success
    }
    
    /**
     * 清除Root状态缓存
     */
    fun clearCache() {
        rootAvailable = null
    }
    
    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String,
        val exitCode: Int
    )
    
    data class FileInfo(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val isSymlink: Boolean = false,
        val permissions: String = "",
        val size: Long = 0,
        val owner: String = "",
        val group: String = ""
    )
}
