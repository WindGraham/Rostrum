package com.rostrum.core.domain.model

import java.io.File

/**
 * 文件项数据模型
 * 
 * 注意：所有文件属性访问都需要安全处理，因为在系统目录（如 /）下
 * 可能没有读取权限，导致 isDirectory、lastModified 等调用失败
 */
data class FileItem(
    val file: File?,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String,
    val childCount: Int = 0,
    val permissions: String? = null,
    val mimeType: String? = null
) {
    /**
     * 从本地File对象构造（兼容旧代码）
     */
    constructor(file: File) : this(
        file = file,
        name = file.name,
        path = file.absolutePath,
        isDirectory = safeIsDirectory(file),
        size = safeGetSize(file),
        lastModified = safeGetLastModified(file),
        extension = safeGetExtension(file),
        childCount = safeGetChildCount(file)
    )
    
    /**
     * 从远程文件信息构造（不需要File对象）
     */
    constructor(
        name: String,
        path: String,
        size: Long,
        lastModified: Long,
        isDirectory: Boolean,
        extension: String,
        permissions: String? = null,
        mimeType: String? = null
    ) : this(
        file = null,
        name = name,
        path = path,
        isDirectory = isDirectory,
        size = size,
        lastModified = lastModified,
        extension = extension,
        childCount = 0,
        permissions = permissions,
        mimeType = mimeType
    )
    companion object {
        /**
         * 安全获取是否为目录
         * 优先使用 file.isDirectory，如果失败则根据文件名推断
         */
        private fun safeIsDirectory(file: File): Boolean {
            return try {
                file.isDirectory
            } catch (e: Exception) {
                // 如果无法确定，检查是否有扩展名（有扩展名通常是文件）
                // 或者检查是否是常见的系统目录名
                val name = file.name.lowercase()
                val systemDirs = setOf(
                    "storage", "emulated", "self", "obb",
                    "system", "data", "cache", "dev", "proc", "sys",
                    "mnt", "vendor", "product", "apex", "odm", "oem", "etc",
                    "bin", "sbin", "lib", "lib64", "linkerconfig", "metadata",
                    "debug_ramdisk", "second_stage_resources", "sdcard", "sdcard0"
                )
                // 数字目录名（如 "0", "100", "128"）通常也是目录
                val isNumeric = name.all { it.isDigit() }
                name in systemDirs || isNumeric || !file.name.contains('.')
            }
        }
        
        /**
         * 安全获取文件大小
         */
        private fun safeGetSize(file: File): Long {
            return try {
                if (file.isFile) file.length() else 0L
            } catch (e: Exception) {
                0L
            }
        }
        
        /**
         * 安全获取最后修改时间
         */
        private fun safeGetLastModified(file: File): Long {
            return try {
                file.lastModified()
            } catch (e: Exception) {
                0L
            }
        }
        
        /**
         * 安全获取扩展名
         */
        private fun safeGetExtension(file: File): String {
            return try {
                if (!safeIsDirectory(file)) {
                    val lastDot = file.name.lastIndexOf('.')
                    if (lastDot > 0) file.name.substring(lastDot + 1).lowercase() else ""
                } else ""
            } catch (e: Exception) {
                ""
            }
        }
        
        /**
         * 安全获取子项数量
         * 对于特殊的 Android 存储目录，使用 shell 命令获取
         */
        private fun safeGetChildCount(file: File): Int {
            return try {
                if (!safeIsDirectory(file)) return 0
                
                val path = file.absolutePath
                val isSpecialStorageDir = path == "/storage" || 
                                         path == "/storage/emulated" ||
                                         path == "/" ||
                                         path.startsWith("/storage/emulated/") && path.count { it == '/' } <= 3
                
                if (isSpecialStorageDir) {
                    // 对于特殊存储目录，使用 shell 命令
                    try {
                        val runtime = Runtime.getRuntime()
                        val process = runtime.exec("ls -1 $path")
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                        val lines = reader.readLines()
                        reader.close()
                        process.waitFor()
                        return lines.filter { it.isNotBlank() && it != "." && it != ".." }.size
                    } catch (e: Exception) {
                        // Shell 命令失败，返回 0
                        0
                    }
                } else {
                    // 普通目录使用标准方法
                    file.list()?.size ?: 0
                }
            } catch (e: Exception) {
                0 // 无法读取时返回 0，而不是抛出异常
            }
        }
    }
    /**
     * 获取格式化的文件大小
     */
    fun getFormattedSize(): String {
        if (isDirectory) {
            // childCount < 0 表示远程文件，不显示项数
            return if (childCount < 0) "文件夹" else "$childCount 项"
        }
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * 获取格式化的最后修改时间
     */
    fun getFormattedDate(): String {
        val date = java.util.Date(lastModified)
        val format = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }
}

