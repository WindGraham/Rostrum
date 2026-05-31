package com.rostrum.core.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.rostrum.core.domain.model.FileItem
import com.rostrum.core.runtime.RuntimeInitializer
import com.rostrum.core.runtime.model.PrivilegedShellRequest
import com.rostrum.core.runtime.model.ShellMcpPolicy
import com.rostrum.util.RootHelper
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

private const val TAG = "RestrictedPathHelper"

/**
 * 受限目录访问工具
 * 
 * 处理 Android 11+ 对 Android/data 和 Android/obb 目录的访问限制
 * 
 * 访问优先级（类似 MT 管理器）：
 * 1. Root 权限（最高优先级）
 * 2. 内置特权桥接 shell
 * 3. SAF 存储访问框架
 * 4. 标准 File API（可能失败）
 */
object RestrictedPathHelper {
    
    // 受限路径模式
    private val RESTRICTED_PATHS = listOf(
        "/storage/emulated/0/Android/data",
        "/storage/emulated/0/Android/obb",
        "/sdcard/Android/data",
        "/sdcard/Android/obb"
    )
    
    /**
     * 检查路径是否是受限目录（包括所有子目录）
     */
    fun isRestrictedPath(path: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false // Android 10 及以下没有限制
        }
        
        val normalizedPath = normalizePath(path)
        // 检查是否是受限路径或其子目录
        return RESTRICTED_PATHS.any { restricted ->
            normalizedPath.startsWith(restricted) || normalizedPath == restricted
        } || normalizedPath.contains("/Android/data") || normalizedPath.contains("/Android/obb")
    }
    
    /**
     * 规范化路径
     */
    private fun normalizePath(path: String): String {
        return path.replace("//", "/")
            .replace("/sdcard/", "/storage/emulated/0/")
            .trimEnd('/')
    }
    
    /**
     * 将路径转换为使用零宽空格的绕过路径
     * 在 "Android" 和 "data" 之间插入零宽空格字符 (U+200B)
     * 
     * 例如：/storage/emulated/0/Android/data -> /storage/emulated/0/Android\u200B/data
     */
    private fun convertToZeroWidthSpacePath(path: String): String {
        val normalizedPath = normalizePath(path)
        // 在 Android 和 data 之间插入零宽空格
        return normalizedPath.replace("/Android/data", "/Android\u200B/data")
            .replace("/Android/obb", "/Android\u200B/obb")
    }
    
    /**
     * 从零宽空格路径还原为正常路径
     */
    private fun convertFromZeroWidthSpacePath(path: String): String {
        return path.replace("\u200B", "")
    }
    
    /**
     * 检查路径是否是 Android/data 或 Android/obb 的父目录
     */
    fun isAndroidDirectory(path: String): Boolean {
        val normalizedPath = normalizePath(path)
        return normalizedPath.endsWith("/Android") || 
               normalizedPath == "/storage/emulated/0/Android"
    }
    
    /**
     * 尝试获取受限目录的文件列表
     * 
     * 优先级（类似 MT 管理器）：
     * 0. 零宽空格绕过方法（最高优先级，优先采用）
     * 1. MANAGE_EXTERNAL_STORAGE 权限（可以访问所有文件）
     * 2. Root 权限
     * 3. 内置特权桥接 shell
     * 4. SAF（用户授权 Android 目录后可访问）
     * 5. 标准 File API（可能有效）
     * 6. Shell 命令（兜底）
     * 
     * 特殊处理：对于系统根目录 "/"，优先使用 Shell 命令（即使没有 Root 也能列出目录名）
     */
    fun listRestrictedDirectory(path: String, context: Context? = null): RestrictedListResult {
        val dir = File(path)
        val isSystemRoot = path == "/" || path.trimEnd('/') == ""
        Log.d(TAG, "listRestrictedDirectory: path=$path, context=${context != null}, isSystemRoot=$isSystemRoot")
        
        // 0. 优先尝试零宽空格绕过方法（最高优先级）
        // 注意：对于所有 Android/data 和 Android/obb 的子目录，都应该使用零宽空格
        val normalizedPath = normalizePath(path)
        val isAndroidDataPath = normalizedPath.contains("/Android/data") || normalizedPath.contains("/Android/obb")
        if (isAndroidDataPath) {
            Log.d(TAG, "Trying zero-width space bypass method (highest priority) for: $normalizedPath")
            val zeroWidthResult = tryListWithZeroWidthSpace(path)
            if (zeroWidthResult is RestrictedListResult.Success && zeroWidthResult.files.isNotEmpty()) {
                Log.d(TAG, "Zero-width space method succeeded for: $path, found ${zeroWidthResult.files.size} files")
                return zeroWidthResult
            } else {
                Log.d(TAG, "Zero-width space method failed or returned empty: $zeroWidthResult")
            }
        }
        
        // 1. 检查 MANAGE_EXTERNAL_STORAGE 权限（可以访问所有文件，包括 /data）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (android.os.Environment.isExternalStorageManager()) {
                Log.d(TAG, "MANAGE_EXTERNAL_STORAGE permission available, using standard File API")
                try {
                    val standardResult = dir.listFiles()
                    if (standardResult != null && standardResult.isNotEmpty()) {
                        Log.d(TAG, "Standard method succeeded with MANAGE_EXTERNAL_STORAGE: $path, found ${standardResult.size} files")
                        return RestrictedListResult.Success(
                            files = standardResult.mapNotNull { file ->
                                try {
                                    FileItem(file)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to create FileItem: ${file.name}", e)
                                    null
                                }
                            }
                        )
                    } else {
                        Log.d(TAG, "Standard method returned null or empty with MANAGE_EXTERNAL_STORAGE")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Standard method failed even with MANAGE_EXTERNAL_STORAGE: ${e.message}")
                }
            }
        }
        
        // 对于系统根目录，优先尝试 Shell 命令（即使没有 Root 也能列出目录名）
        if (isSystemRoot) {
            Log.d(TAG, "System root detected, trying shell ls method first...")
            val shellResult = tryListWithShell(path)
            if (shellResult is RestrictedListResult.Success && shellResult.files.isNotEmpty()) {
                Log.d(TAG, "Shell method succeeded for system root: $path, found ${shellResult.files.size} files")
                return shellResult
            } else {
                Log.w(TAG, "Shell method failed for system root: $shellResult")
                // 即使 Shell 失败，也继续尝试其他方法
            }
        }

        // 1.5. 统一特权 shell 服务
        val privilegedResult = runBlocking { tryListWithPrivilegedShell(path) }
        if (privilegedResult is RestrictedListResult.Success && privilegedResult.files.isNotEmpty()) {
            Log.d(TAG, "Privileged shell service succeeded for: $path, found ${privilegedResult.files.size} files")
            return privilegedResult
        } else {
            Log.d(TAG, "Privileged shell service failed or returned empty: $privilegedResult")
        }
        
        // 2. 优先尝试 Root 权限
        Log.d(TAG, "Trying Root method...")
        val rootResult = runBlocking { tryListWithRootHelper(path) }
        if (rootResult is RestrictedListResult.Success && rootResult.files.isNotEmpty()) {
            Log.d(TAG, "Root method succeeded for: $path, found ${rootResult.files.size} files")
            return rootResult
        } else {
            Log.d(TAG, "Root method failed or returned empty: $rootResult")
        }
        
        // 4. 尝试 SAF（如果有 context）
        if (context != null) {
            Log.d(TAG, "Trying SAF method...")
            val safResult = SafAccessManager.listDirectoryWithSaf(context, path)
            if (safResult != null && safResult.isNotEmpty()) {
                Log.d(TAG, "SAF method succeeded for: $path, found ${safResult.size} files")
                return RestrictedListResult.Success(safResult)
            } else {
                Log.d(TAG, "SAF method failed or returned empty/null: ${safResult?.size ?: "null"}")
            }
        } else {
            Log.d(TAG, "SAF method skipped: context is null")
        }
        
        // 5. 尝试标准方法
        Log.d(TAG, "Trying standard File API method...")
        try {
            val standardResult = dir.listFiles()
            if (standardResult != null && standardResult.isNotEmpty()) {
                Log.d(TAG, "Standard method succeeded for: $path, found ${standardResult.size} files")
                return RestrictedListResult.Success(
                    files = standardResult.mapNotNull { file ->
                        try {
                            FileItem(file)
                        } catch (e: Exception) {
                            null
                        }
                    }
                )
            } else {
                Log.d(TAG, "Standard method returned null or empty: ${standardResult?.size ?: "null"}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Standard method failed: ${e.message}")
        }
        
        // 6. 尝试使用普通 shell ls 命令（对于非系统根目录）
        if (!isSystemRoot) {
            Log.d(TAG, "Trying shell ls method...")
            val shellResult = tryListWithShell(path)
            if (shellResult is RestrictedListResult.Success && shellResult.files.isNotEmpty()) {
                Log.d(TAG, "Shell method succeeded for: $path, found ${shellResult.files.size} files")
                return shellResult
            } else {
                Log.d(TAG, "Shell method failed or returned empty: $shellResult")
            }
        }
        
        // 都失败了，返回需要特殊权限的提示
        Log.w(TAG, "All methods failed for: $path")
        return RestrictedListResult.NeedPermission(
            message = buildPermissionHintMessage(context)
        )
    }
    
    /**
     * 统一的文件列表访问方法，优先使用零宽空格
     * 对所有路径都先尝试零宽空格方法，然后回退到标准方法
     */
    fun listFilesWithZeroWidthPriority(path: String): Array<File>? {
        val normalizedPath = normalizePath(path)
        val isSystemRoot = normalizedPath == "/" || normalizedPath.trimEnd('/').isEmpty()
        
        // 检查是否是特殊的 Android 存储目录
        val isStorageRoot = normalizedPath == "/storage" || normalizedPath.trimEnd('/') == "/storage"
        val isEmulatedRoot = normalizedPath == "/storage/emulated" || normalizedPath.trimEnd('/') == "/storage/emulated"
        
        // 对于系统根目录和特殊存储目录，直接使用 Shell 命令
        if (isSystemRoot || isStorageRoot || isEmulatedRoot) {
            val targetPath = when {
                isSystemRoot -> "/"
                isStorageRoot -> "/storage"
                isEmulatedRoot -> "/storage/emulated"
                else -> normalizedPath
            }
            
            Log.d(TAG, "listFilesWithZeroWidthPriority: Special directory detected: $targetPath, using shell command")
            try {
                // Android 系统上使用 Runtime.exec() 执行命令，更可靠
                val runtime = Runtime.getRuntime()
                val command = "ls -1 $targetPath"
                val process = runtime.exec(command)
                
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                val lines = reader.readLines()
                val errorLines = errorReader.readLines()
                reader.close()
                errorReader.close()
                
                val exitCode = process.waitFor()
                
                Log.d(TAG, "listFilesWithZeroWidthPriority: Shell command '$command' exit code: $exitCode, output lines: ${lines.size}, error: ${errorLines.joinToString()}")
                
                if (exitCode == 0 && lines.isNotEmpty()) {
                    val files = lines
                        .filter { it.isNotBlank() && it != "." && it != ".." }
                        .mapNotNull { name ->
                            try {
                                val filePath = if (targetPath == "/") "/$name" else "$targetPath/$name"
                                val file = File(filePath)
                                Log.d(TAG, "listFilesWithZeroWidthPriority: Created File for: $filePath")
                                file
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to create File for: $name", e)
                                null
                            }
                        }
                    
                    if (files.isNotEmpty()) {
                        Log.d(TAG, "listFilesWithZeroWidthPriority: Shell command succeeded for $targetPath, found ${files.size} files: ${files.map { it.name }.joinToString()}")
                        return files.toTypedArray()
                    } else {
                        Log.w(TAG, "listFilesWithZeroWidthPriority: Shell command returned empty files list")
                    }
                } else {
                    // 即使退出码不为 0，如果输出不为空，也尝试使用
                    if (lines.isNotEmpty()) {
                        Log.d(TAG, "listFilesWithZeroWidthPriority: Shell command exit code is $exitCode but output exists, trying to use it")
                        val files = lines
                            .filter { it.isNotBlank() && it != "." && it != ".." }
                            .mapNotNull { name ->
                                try {
                                    val filePath = if (targetPath == "/") "/$name" else "$targetPath/$name"
                                    File(filePath)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        if (files.isNotEmpty()) {
                            Log.d(TAG, "listFilesWithZeroWidthPriority: Using output despite exit code, found ${files.size} files")
                            return files.toTypedArray()
                        }
                    }
                    Log.w(TAG, "listFilesWithZeroWidthPriority: Shell command failed with exit code $exitCode, error: ${errorLines.joinToString()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "listFilesWithZeroWidthPriority: Shell command exception: ${e.message}", e)
            }
            // 如果 Shell 失败，回退到标准方法
        }
        
        // 对所有路径都优先尝试零宽空格方法
        try {
            // 如果路径包含 Android/data 或 Android/obb，使用专门的零宽空格转换
            val zeroWidthPath = if (normalizedPath.contains("/Android/data") || normalizedPath.contains("/Android/obb")) {
                convertToZeroWidthSpacePath(normalizedPath)
            } else {
                // 对于其他路径，尝试在最后一个斜杠后插入零宽空格
                // 例如：/storage/emulated/0/Download -> /storage/emulated/0/\u200BDownload
                val lastSlashIndex = normalizedPath.lastIndexOf('/')
                if (lastSlashIndex > 0 && lastSlashIndex < normalizedPath.length - 1) {
                    normalizedPath.substring(0, lastSlashIndex + 1) + "\u200B" + normalizedPath.substring(lastSlashIndex + 1)
                } else if (!isSystemRoot) {
                    // 如果不是系统根目录，尝试在开头插入零宽空格
                    "\u200B" + normalizedPath
                } else {
                    // 系统根目录不尝试零宽空格
                    normalizedPath
                }
            }
            
            if (!isSystemRoot) {
                Log.d(TAG, "listFilesWithZeroWidthPriority: Trying zero-width space for: $normalizedPath -> $zeroWidthPath")
                val dir = File(zeroWidthPath)
                if (dir.exists() && dir.canRead() && dir.isDirectory) {
                    val files = dir.listFiles()
                    if (files != null && files.isNotEmpty()) {
                        Log.d(TAG, "listFilesWithZeroWidthPriority: Zero-width space succeeded, found ${files.size} files")
                        return files
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "listFilesWithZeroWidthPriority: Zero-width space failed: ${e.message}")
        }
        
        // 回退到标准方法
        try {
            val dir = File(normalizedPath)
            if (dir.exists() && dir.canRead() && dir.isDirectory) {
                val files = dir.listFiles()
                Log.d(TAG, "listFilesWithZeroWidthPriority: Standard method for: $normalizedPath, found ${files?.size ?: 0} files")
                return files
            }
        } catch (e: Exception) {
            Log.d(TAG, "listFilesWithZeroWidthPriority: Standard method failed: ${e.message}")
        }
        
        return null
    }
    
    /**
     * 使用零宽空格绕过方法尝试列出目录
     * 这是最高优先级的方法，优先采用
     */
    private fun tryListWithZeroWidthSpace(path: String): RestrictedListResult {
        val normalizedPath = normalizePath(path)
        
        // 只对 Android/data 和 Android/obb 路径使用零宽空格
        if (!normalizedPath.contains("/Android/data") && !normalizedPath.contains("/Android/obb")) {
            return RestrictedListResult.Error("Not an Android/data or Android/obb path")
        }
        
        try {
            // 转换为零宽空格路径
            val zeroWidthPath = convertToZeroWidthSpacePath(normalizedPath)
            Log.d(TAG, "tryListWithZeroWidthSpace: original=$normalizedPath, zeroWidth=$zeroWidthPath")
            
            val dir = File(zeroWidthPath)
            
            // 检查目录是否存在且可读
            if (dir.exists() && dir.canRead() && dir.isDirectory) {
                val files = dir.listFiles()
                if (files != null && files.isNotEmpty()) {
                    Log.d(TAG, "tryListWithZeroWidthSpace: SUCCESS! Found ${files.size} files")
                    
                    // 创建文件列表，确保正确计算 childCount
                    val fileItems = files.mapNotNull { file ->
                        try {
                            // 使用原始路径（不包含零宽空格）来创建 FileItem
                            val originalPath = convertFromZeroWidthSpacePath(file.absolutePath)
                            
                            // 对于目录，使用零宽空格路径计算子项数量
                            val childCount = if (file.isDirectory) {
                                try {
                                    val childZeroWidthPath = convertToZeroWidthSpacePath(originalPath)
                                    val childDir = File(childZeroWidthPath)
                                    if (childDir.exists() && childDir.canRead() && childDir.isDirectory) {
                                        val childFiles = childDir.listFiles()
                                        val count = childFiles?.size ?: 0
                                        Log.d(TAG, "Child count for ${file.name}: $count")
                                        count
                                    } else {
                                        Log.d(TAG, "Child directory check failed for ${file.name}: exists=${childDir.exists()}, canRead=${childDir.canRead()}")
                                        0
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to get child count for ${file.name}: ${e.message}")
                                    0
                                }
                            } else {
                                0
                            }
                            
                            // 创建 FileItem，明确指定 childCount 以避免构造函数中的自动计算
                            FileItem(
                                file = File(originalPath),
                                name = file.name,
                                path = originalPath,
                                isDirectory = file.isDirectory,
                                size = if (file.isFile) file.length() else 0L,
                                lastModified = file.lastModified(),
                                extension = if (file.isFile) file.name.substringAfterLast('.', "").lowercase() else "",
                                childCount = childCount
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to create FileItem: ${file.name}", e)
                            null
                        }
                    }
                    
                    return RestrictedListResult.Success(files = fileItems)
                } else {
                    Log.d(TAG, "tryListWithZeroWidthSpace: Directory exists but listFiles() returned empty")
                }
            } else {
                Log.d(TAG, "tryListWithZeroWidthSpace: Directory check failed: exists=${dir.exists()}, canRead=${dir.canRead()}, isDirectory=${dir.isDirectory}")
            }
        } catch (e: Exception) {
            Log.d(TAG, "tryListWithZeroWidthSpace: Exception: ${e.message}")
        }
        
        return RestrictedListResult.Error("Zero-width space bypass failed")
    }

    /**
     * 统一特权 shell 服务（优先走 RuntimeInitializer 注入的服务）。
     */
    private suspend fun tryListWithPrivilegedShell(path: String): RestrictedListResult {
        return try {
            val service = RuntimeInitializer.privilegedShellService
                ?: return RestrictedListResult.Error("Privileged shell service unavailable")
            val result = service.exec(
                PrivilegedShellRequest(
                    command = "ls",
                    args = listOf("-la", path),
                    sessionId = "restricted_path_list",
                    policy = ShellMcpPolicy.SESSION_AUTO,
                    approved = true
                )
            )
            if (!result.success || result.output.isBlank()) {
                return RestrictedListResult.Error(result.error.ifBlank { "Privileged shell list failed" })
            }

            val fileItems = result.output.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("total") }
                .mapNotNull { line ->
                    try {
                        val parts = line.split(Regex("\\s+"), limit = 9)
                        if (parts.size < 8) return@mapNotNull null
                        val permissions = parts[0]
                        val size = parts.getOrNull(4)?.toLongOrNull() ?: 0L
                        val rawName = (parts.getOrNull(8) ?: parts.lastOrNull()).orEmpty().trim()
                        if (rawName.isBlank() || rawName == "." || rawName == "..") return@mapNotNull null
                        val name = rawName.substringBefore(" -> ")
                        val filePath = "$path/$name"
                        FileItem(
                            file = File(filePath),
                            name = name,
                            path = filePath,
                            isDirectory = permissions.startsWith("d"),
                            size = size,
                            lastModified = System.currentTimeMillis(),
                            extension = if (permissions.startsWith("d")) "" else name.substringAfterLast('.', ""),
                            childCount = 0
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                .toList()

            if (fileItems.isEmpty()) {
                RestrictedListResult.Error("Privileged shell listing returned empty")
            } else {
                RestrictedListResult.Success(fileItems)
            }
        } catch (e: Exception) {
            RestrictedListResult.Error(e.message ?: "Privileged shell failed")
        }
    }

    /**
     * 使用 RootHelper 尝试列出目录
     */
    private suspend fun tryListWithRootHelper(path: String): RestrictedListResult {
        return try {
            if (!RootHelper.isRootAvailable()) {
                return RestrictedListResult.Error("Root not available")
            }
            
            val files = RootHelper.listDirectory(path)
            if (files != null && files.isNotEmpty()) {
                RestrictedListResult.Success(
                    files = files.mapNotNull { fileInfo ->
                        try {
                            val file = File(fileInfo.path)
                            FileItem(
                                file = file,
                                name = fileInfo.name,
                                path = fileInfo.path,
                                isDirectory = fileInfo.isDirectory,
                                size = fileInfo.size,
                                lastModified = System.currentTimeMillis(),
                                extension = if (fileInfo.isDirectory) "" else fileInfo.name.substringAfterLast('.', ""),
                                childCount = 0
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to create FileItem: ${e.message}")
                            null
                        }
                    }
                )
            } else {
                RestrictedListResult.Error("Root listing returned empty")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Root listing failed: ${e.message}")
            RestrictedListResult.Error(e.message ?: "Root failed")
        }
    }
    
    /**
     * 构建权限提示消息
     */
    private fun buildPermissionHintMessage(context: Context?): String {
        return buildString {
            append("无法访问此目录。Android 11+ 限制了对 Android/data 和 Android/obb 的访问。\n\n")
            append("解决方案（任选其一）：\n\n")
            append("1. 【推荐】使用 SAF 授权\n")
            append("   无需 Root 或额外应用，通过系统文件选择器授权 Android 目录即可\n\n")
            append("2. 使用内置特权桥接\n")
            append("   在权限面板中启动内置桥接后可访问受限目录\n\n")
            append("3. 使用 Root 权限\n")
            append("   如果设备已 Root，启动时会自动请求")
        }
    }
    
    /**
     * 尝试使用 Shell 命令列出目录（普通 shell，非 root）
     * 
     * 这个方法对于系统根目录特别有用，因为即使没有 Root 权限，
     * `ls /` 命令也能列出根目录下的目录名（虽然无法进入这些目录）
     */
    private fun tryListWithShell(path: String): RestrictedListResult {
        return try {
            // 使用 ls 命令列出目录内容
            // 对于系统根目录，使用 ls -1 更简洁（每行一个文件名）
            // 对于其他目录，使用 ls -a 包含隐藏文件
            val isSystemRoot = path == "/" || path.trimEnd('/') == ""
            val command = if (isSystemRoot) "ls -1 /" else "ls -a \"$path\""
            
            // Android 系统上使用 Runtime.exec() 执行命令，更可靠
            val runtime = Runtime.getRuntime()
            val process = runtime.exec(command)
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = reader.readLines()
            reader.close()
            
            val exitCode = process.waitFor()
            
            if (exitCode == 0 && lines.isNotEmpty()) {
                val files = lines
                    .filter { it.isNotBlank() && it != "." && it != ".." }
                    .mapNotNull { name ->
                        try {
                            val filePath = if (path.endsWith("/")) {
                                "$path$name"
                            } else {
                                "$path/$name"
                            }
                            val file = File(filePath)
                            
                            // 尝试创建完整的 FileItem
                            try {
                                FileItem(file)
                            } catch (e: Exception) {
                                // 如果创建失败（权限不足），创建一个基本的 FileItem
                                // 对于系统根目录，这是常见情况
                                Log.d(TAG, "Cannot create full FileItem for $filePath, creating basic entry")
                                try {
                                    // 尝试判断是否为目录（通过尝试列出其内容）
                                    val testProcess = ProcessBuilder("ls", "-d", filePath)
                                        .redirectErrorStream(true)
                                        .start()
                                    val testExitCode = testProcess.waitFor()
                                    val isDirectory = testExitCode == 0
                                    
                                    FileItem(
                                        file = file,
                                        name = name,
                                        path = filePath,
                                        isDirectory = isDirectory,
                                        size = 0L,
                                        lastModified = System.currentTimeMillis(),
                                        extension = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase(),
                                        childCount = 0
                                    )
                                } catch (e2: Exception) {
                                    // 如果还是失败，假设是目录（系统目录通常是目录）
                                    FileItem(
                                        file = file,
                                        name = name,
                                        path = filePath,
                                        isDirectory = true, // 系统根目录下的项通常是目录
                                        size = 0L,
                                        lastModified = System.currentTimeMillis(),
                                        extension = "",
                                        childCount = 0
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to create FileItem for: $name", e)
                            null
                        }
                    }
                
                if (files.isNotEmpty()) {
                    Log.d(TAG, "Shell ls succeeded for $path, found ${files.size} items")
                    RestrictedListResult.Success(files)
                } else {
                    RestrictedListResult.Error("No files found")
                }
            } else {
                // 读取错误输出以便调试
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                val errorLines = errorReader.readLines()
                errorReader.close()
                val errorMsg = if (errorLines.isNotEmpty()) errorLines.joinToString("\n") else "Unknown error"
                Log.w(TAG, "ls command failed with exit code: $exitCode, error: $errorMsg")
                RestrictedListResult.Error("ls command failed with exit code: $exitCode")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Shell ls failed", e)
            RestrictedListResult.Error(e.message ?: "Shell command failed")
        }
    }
    
    /**
     * 为受限文件创建 FileItem
     * 由于权限问题，需要特殊处理
     */
    private fun createFileItemForRestrictedFile(file: File, name: String): FileItem? {
        return try {
            // 首先尝试标准方式
            FileItem(file)
        } catch (e: Exception) {
            // 如果失败，创建一个基本的 FileItem
            // 假设没有扩展名的是目录
            try {
                val isDirectory = !name.contains('.') || file.isDirectory
                FileItem(
                    file = file,
                    name = name,
                    path = file.absolutePath,
                    isDirectory = isDirectory,
                    size = 0L,
                    lastModified = System.currentTimeMillis(),
                    extension = if (isDirectory) "" else name.substringAfterLast('.', ""),
                    childCount = 0
                )
            } catch (e2: Exception) {
                null
            }
        }
    }
    
    /**
     * 受限目录列表结果
     */
    sealed class RestrictedListResult {
        data class Success(val files: List<FileItem>) : RestrictedListResult()
        data class NeedPermission(val message: String) : RestrictedListResult()
        data class Error(val message: String) : RestrictedListResult()
    }
}
