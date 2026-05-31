package com.rostrum.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.rostrum.core.domain.model.FileItem
import java.io.File

private const val TAG = "SafAccessManager"
private const val PREFS_NAME = "saf_permissions"
private const val KEY_ANDROID_URI = "android_uri"

/**
 * Storage Access Framework 授权管理器
 * 
 * 通过让用户授权 "Android" 目录来访问 Android/data 和 Android/obb
 * 这是 MT 管理器、ES 文件浏览器等使用的方法
 * 
 * ## 历史背景：MT 管理器的绕过方法
 * 
 * MT 管理器曾经使用过多种方法来绕过 Android 11+ 对 Android/data 目录的访问限制：
 * 
 * 1. **零宽空格漏洞（2024年5月修复）**
 *    - 通过在路径中添加零宽空格（zero-width space, U+200B）来绕过系统的正则匹配
 *    - 例如：`/storage/emulated/0/Android\u200B/data` 可以绕过检查
 *    - 这个漏洞已经被 Google 修复，现在不再有效
 * 
 * 2. **直接 File API**
 *    - 某些设备或定制 ROM 可能仍然允许直接访问
 *    - 我们会在代码中尝试这种方法，但通常不会成功
 * 
 * 3. **其他未知方法**
 *    - MT 管理器可能使用了其他未公开的方法
 *    - 这些方法可能依赖于特定的系统版本或设备
 * 
 * ## 当前推荐方法
 * 
 * 由于漏洞已被修复，现在访问 Android/data 目录的合法方法只有：
 * 
 * 1. **Root 权限** - 完全访问（需要设备已 Root）
 * 2. **内置特权桥接 shell** - 通过应用内桥接执行 shell
 * 3. **SAF 授权** - 用户手动授权 Android 目录（本类实现的方法）
 * 4. **MANAGE_EXTERNAL_STORAGE** - 理论上可以访问所有文件，但对 Android/data 的限制仍然存在
 * 
 * 本类实现了 SAF 授权方法，这是最通用和可靠的方法。
 */
object SafAccessManager {
    
    private var androidUri: Uri? = null
    
    /**
     * 初始化，从 SharedPreferences 恢复已保存的授权
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        prefs.getString(KEY_ANDROID_URI, null)?.let { uriStr ->
            try {
                val uri = Uri.parse(uriStr)
                // 验证权限是否仍然有效
                if (isUriPermissionValid(context, uri)) {
                    androidUri = uri
                    Log.d(TAG, "Restored Android URI: $androidUri")
                } else {
                    Log.w(TAG, "Saved URI permission is no longer valid")
                    prefs.edit().remove(KEY_ANDROID_URI).apply()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore Android URI", e)
            }
        }
    }
    
    /**
     * 检查 URI 权限是否有效
     */
    private fun isUriPermissionValid(context: Context, uri: Uri): Boolean {
        return try {
            val persistedUris = context.contentResolver.persistedUriPermissions
            persistedUris.any { it.uri == uri && it.isReadPermission }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检查是否已有 Android 目录的授权
     */
    fun hasAndroidPermission(context: Context): Boolean {
        val uri = androidUri ?: return false
        return try {
            val docFile = DocumentFile.fromTreeUri(context, uri)
            docFile?.exists() == true && docFile.canRead()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check Android permission", e)
            false
        }
    }
    
    /**
     * 检查路径是否需要 SAF 授权且已有授权
     * 注意：这个函数只检查 SAF 权限，不检查 Root/内置特权桥接权限
     * 完整的权限检查应使用 hasAnyAccessForPath
     */
    fun hasPermissionForPath(context: Context, path: String): Boolean {
        val normalizedPath = path.replace("/sdcard/", "/storage/emulated/0/")
        
        // 只有 Android/data 和 Android/obb 需要特殊权限
        if (!normalizedPath.contains("/Android/data") && !normalizedPath.contains("/Android/obb")) {
            return true // 不需要特殊权限
        }
        
        return hasAndroidPermission(context)
    }
    
    /**
     * 检查是否有任何方式可以访问受限路径（MANAGE_EXTERNAL_STORAGE/Root/内置特权桥接/SAF）
     * 这是推荐使用的权限检查方法
     * 注意：此函数使用同步检查，不会阻塞主线程
     */
    fun hasAnyAccessForPath(context: Context, path: String): Boolean {
        val normalizedPath = path.replace("/sdcard/", "/storage/emulated/0/")
        
        // 检查是否是系统目录（如 /data, /system 等）
        val isSystemDirectory = normalizedPath.startsWith("/data") || 
                                 normalizedPath.startsWith("/system") ||
                                 normalizedPath.startsWith("/vendor") ||
                                 normalizedPath.startsWith("/product") ||
                                 normalizedPath == "/" ||
                                 normalizedPath.startsWith("/proc") ||
                                 normalizedPath.startsWith("/mnt")
        
        // 1. 优先检查 MANAGE_EXTERNAL_STORAGE 权限（最高优先级，可以访问所有文件）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (android.os.Environment.isExternalStorageManager()) {
                Log.d(TAG, "hasAnyAccessForPath: MANAGE_EXTERNAL_STORAGE permission available - can access all files")
                return true // 有这个权限，可以访问所有文件，包括 /data
            }
        }
        
        // 对于系统目录，如果没有 MANAGE_EXTERNAL_STORAGE，需要 Root 或内置特权桥接
        if (isSystemDirectory) {
            // 2. 检查 Root 权限
            if (com.rostrum.util.RootHelper.isRootAvailableSync()) {
                Log.d(TAG, "hasAnyAccessForPath: Root available for system directory")
                return true
            }

            Log.d(TAG, "hasAnyAccessForPath: System directory requires MANAGE_EXTERNAL_STORAGE, Root, or privileged bridge")
            return false
        }
        
        // 对于 Android/data 和 Android/obb，需要特殊权限
        if (normalizedPath.contains("/Android/data") || normalizedPath.contains("/Android/obb")) {
            // 0. 零宽空格方法总是可用（最高优先级）
            // 这个方法不需要任何权限，可以直接访问
            Log.d(TAG, "hasAnyAccessForPath: Zero-width space method available for Android/data or Android/obb")
            return true
            
        }
        
        // 其他路径不需要特殊权限
        return true
    }
    
    /**
     * 创建请求 Android 目录授权的 Intent
     * 关键：请求授权 "Android" 目录，而不是 "Android/data"
     * 
     * @param targetPath 可选的目标路径，用于指定初始目录（Android 8.0+）
     */
    fun createAndroidPermissionIntent(targetPath: String? = null): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        
        // 如果指定了目标路径，尝试构造对应的 URI
        val initialUri = if (targetPath != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                pathToUri(targetPath)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to convert path to URI: $targetPath", e)
                // 回退到默认的 Android 目录
                DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Android"
                )
            }
        } else {
            // 构造指向 Android 目录的 URI
            // 格式: content://com.android.externalstorage.documents/document/primary%3AAndroid
            DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:Android"
            )
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
        }
        
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
        
        return intent
    }
    
    /**
     * 将文件路径转换为 SAF URI（用于 OPEN_DOCUMENT_TREE 的初始 URI）
     * 
     * @param path 文件路径，如 /storage/emulated/0/Android/data/com.example
     * @return 对应的 URI
     */
    fun pathToUri(path: String): Uri {
        val normalizedPath = path.replace("/sdcard/", "/storage/emulated/0/").trimEnd('/')
        
        // 提取相对路径（去掉 /storage/emulated/0/）
        val relativePath = if (normalizedPath.startsWith("/storage/emulated/0/")) {
            normalizedPath.removePrefix("/storage/emulated/0/")
        } else if (normalizedPath.startsWith("/sdcard/")) {
            normalizedPath.removePrefix("/sdcard/")
        } else {
            normalizedPath.trimStart('/')
        }
        
        // 构造 URI（DocumentsContract.buildDocumentUri 会自动处理编码）
        // 格式: content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fdata
        return DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:$relativePath"
        )
    }
    
    /**
     * 将文件路径转换为 SAF Tree URI（用于 fromSingleUri）
     * 
     * 这是关键方法：通过构造特定的 URI，可以直接通过路径生成 DocumentFile 对象
     * 而不需要从授权的根目录递归遍历
     * 
     * 根据用户授权的根目录（如 Android 或 Android/data），构造子目录的 URI
     * 
     * 参考实现：
     * String path2 = path.replace("/storage/emulated/0/", "").replace("/", "%2F");
     * return DocumentFile.fromSingleUri(context, Uri.parse(
     *     "content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fdata/document/primary%3A" + path2));
     * 
     * @param context 上下文
     * @param path 文件路径，如 /storage/emulated/0/Android/data/com.example
     * @return DocumentFile 对象，如果路径无效或无法访问则返回 null
     */
    fun getDocumentFileFromPath(context: Context, path: String): DocumentFile? {
        val uri = androidUri ?: run {
            Log.w(TAG, "getDocumentFileFromPath: androidUri is null, need to grant permission first")
            return null
        }
        
        var normalizedPath = path.replace("/sdcard/", "/storage/emulated/0/").trimEnd('/')
        
        // 检查路径是否在 Android/data 或 Android/obb 下
        val isAndroidData = normalizedPath.contains("/Android/data")
        val isAndroidObb = normalizedPath.contains("/Android/obb")
        
        if (!isAndroidData && !isAndroidObb) {
            Log.w(TAG, "getDocumentFileFromPath: Path is not under Android/data or Android/obb: $path")
            return null
        }
        
        try {
            // 从授权的根 URI 中提取 tree ID
            val treeUriString = uri.toString()
            
            // 提取 tree ID（授权的根目录标识）
            // 例如：content://com.android.externalstorage.documents/tree/primary%3AAndroid
            val treeId = when {
                treeUriString.contains("/tree/") -> {
                    // Tree URI 格式: content://.../tree/primary%3AAndroid
                    val fullTreeId = treeUriString.substringAfter("/tree/")
                    // 可能包含额外的路径，只取第一部分
                    if (fullTreeId.contains("/")) {
                        fullTreeId.substringBefore("/")
                    } else {
                        fullTreeId
                    }
                }
                treeUriString.contains("/document/") -> {
                    // Document URI 格式: content://.../document/primary%3AAndroid
                    // 需要转换为 tree URI，tree ID 就是 document ID
                    treeUriString.substringAfter("/document/")
                }
                else -> {
                    Log.w(TAG, "getDocumentFileFromPath: Unknown URI format: $treeUriString")
                    return null
                }
            }
            
            // 提取相对路径（去掉 /storage/emulated/0/）
            // 然后替换 / 为 %2F
            val relativePath = normalizedPath
                .removePrefix("/storage/emulated/0/")
                .replace("/", "%2F")
            
            // 构造 document URI
            // 格式: content://com.android.externalstorage.documents/tree/primary%3AAndroid/document/primary%3AAndroid%2Fdata%2Fcom.example
            val documentUriString = "content://com.android.externalstorage.documents/tree/$treeId/document/primary%3A$relativePath"
            val documentUri = Uri.parse(documentUriString)
            
            Log.d(TAG, "getDocumentFileFromPath: path=$normalizedPath")
            Log.d(TAG, "getDocumentFileFromPath: treeId=$treeId")
            Log.d(TAG, "getDocumentFileFromPath: relativePath=$relativePath")
            Log.d(TAG, "getDocumentFileFromPath: Generated URI: $documentUriString")
            
            // 使用 fromSingleUri 生成 DocumentFile
            val docFile = DocumentFile.fromSingleUri(context, documentUri)
            
            // 验证 DocumentFile 是否有效
            if (docFile != null) {
                val exists = docFile.exists()
                Log.d(TAG, "getDocumentFileFromPath: DocumentFile created, exists=$exists")
                if (exists) {
                    return docFile
                }
            }
            
            Log.w(TAG, "getDocumentFileFromPath: DocumentFile does not exist or is invalid for $path")
            // 如果 fromSingleUri 失败，返回 null，让调用者回退到递归遍历方式
            return null
        } catch (e: Exception) {
            Log.e(TAG, "getDocumentFileFromPath: Failed to create DocumentFile from path: $path", e)
            return null
        }
    }
    
    /**
     * 处理用户选择的目录 URI
     * @return true 如果授权成功
     */
    fun handlePermissionResult(context: Context, uri: Uri?): Boolean {
        if (uri == null) {
            Log.w(TAG, "Permission result URI is null")
            return false
        }
        
        try {
            // 持久化权限
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            
            // 验证是否是 Android 目录
            val docFile = DocumentFile.fromTreeUri(context, uri)
            val name = docFile?.name?.lowercase() ?: ""
            
            if (name == "android" || uri.toString().contains("Android", ignoreCase = true)) {
                androidUri = uri
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_ANDROID_URI, uri.toString()).apply()
                Log.d(TAG, "Android permission granted: $uri")
                return true
            } else {
                Log.w(TAG, "Selected directory is not Android: $uri (name=$name)")
                // 即使不是 Android 目录，也保存下来以备后用
                androidUri = uri
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_ANDROID_URI, uri.toString()).apply()
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle permission result", e)
            return false
        }
    }
    
    /**
     * 清理路径中的零宽字符和特殊字符
     * 
     * 历史背景：
     * MT 管理器曾经通过在路径中添加零宽空格（zero-width space）来绕过系统的正则匹配。
     * 这个漏洞在 2024年5月17日 左右被修复。
     * 
     * 虽然漏洞已修复，但我们仍然应该清理路径中的这些字符，以确保路径的正确性。
     * 
     * @param path 原始路径
     * @return 清理后的路径
     */
    private fun cleanPath(path: String): String {
        return path
            // 移除零宽空格 (U+200B)
            .replace("\u200B", "")
            // 移除零宽非断空格 (U+FEFF)
            .replace("\uFEFF", "")
            // 移除零宽连接符 (U+200D)
            .replace("\u200D", "")
            // 移除零宽非断连接符 (U+200C)
            .replace("\u200C", "")
            // 移除其他可能的零宽字符
            .replace("\u200E", "") // 左到右标记
            .replace("\u200F", "") // 右到左标记
            .replace("\u202A", "") // 左到右嵌入
            .replace("\u202B", "") // 右到左嵌入
            .replace("\u202C", "") // 弹出方向格式
            .replace("\u202D", "") // 左到右覆盖
            .replace("\u202E", "") // 右到左覆盖
    }
    
    /**
     * 尝试使用零宽空格绕过路径检查（优先采用的方法）
     * 
     * 通过在路径中添加零宽空格（zero-width space, U+200B）来绕过系统的正则匹配。
     * 在 "Android" 和 "data" 之间插入零宽空格字符。
     * 
     * 例如：/storage/emulated/0/Android/data -> /storage/emulated/0/Android\u200B/data
     * 
     * @param path 原始路径
     * @return 文件列表，如果失败返回 null
     */
    private fun tryZeroWidthSpaceBypass(path: String): List<FileItem>? {
        val normalizedPath = path.replace("/sdcard/", "/storage/emulated/0/").trimEnd('/')
        
        // 只对 Android/data 和 Android/obb 路径使用零宽空格
        if (!normalizedPath.contains("/Android/data") && !normalizedPath.contains("/Android/obb")) {
            return null
        }
        
        // 零宽空格字符
        val zeroWidthSpace = "\u200B" // U+200B
        
        // 在 Android 和 data/obb 之间插入零宽空格
        val zeroWidthPath = normalizedPath
            .replace("/Android/data", "/Android${zeroWidthSpace}/data")
            .replace("/Android/obb", "/Android${zeroWidthSpace}/obb")
        
        try {
            Log.d(TAG, "tryZeroWidthSpaceBypass: original=$normalizedPath, zeroWidth=$zeroWidthPath")
            val dir = File(zeroWidthPath)
            
            // 检查目录是否存在且可读
            if (dir.exists() && dir.canRead() && dir.isDirectory) {
                val files = dir.listFiles()
                if (files != null && files.isNotEmpty()) {
                    Log.d(TAG, "tryZeroWidthSpaceBypass: SUCCESS! Found ${files.size} files")
                    return files.mapNotNull { file ->
                        try {
                            // 使用原始路径（不包含零宽空格）来创建 FileItem
                            val originalPath = file.absolutePath.replace(zeroWidthSpace, "")
                            FileItem(
                                file = File(originalPath),
                                name = file.name,
                                path = originalPath,
                                isDirectory = file.isDirectory,
                                size = if (file.isFile) file.length() else 0L,
                                lastModified = file.lastModified(),
                                extension = if (file.isFile) file.name.substringAfterLast('.', "").lowercase() else "",
                                childCount = 0
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to create FileItem: ${file.name}", e)
                            null
                        }
                    }
                } else {
                    Log.d(TAG, "tryZeroWidthSpaceBypass: Directory exists but listFiles() returned empty")
                }
            } else {
                Log.d(TAG, "tryZeroWidthSpaceBypass: Directory check failed: exists=${dir.exists()}, canRead=${dir.canRead()}, isDirectory=${dir.isDirectory}")
            }
        } catch (e: Exception) {
            Log.d(TAG, "tryZeroWidthSpaceBypass: Exception: ${e.message}")
        }
        
        Log.d(TAG, "tryZeroWidthSpaceBypass: Failed")
        return null
    }
    
    /**
     * 尝试直接使用 File API 访问受限目录
     * 
     * 历史背景：
     * MT 管理器曾经使用过多种方法来绕过 Android 11+ 的限制：
     * 1. 零宽空格漏洞（官方声称2024年5月修复，但可能在某些设备上仍然有效）
     * 2. 直接 File API（某些设备可能允许）
     * 3. 其他未知方法
     * 
     * 注意：虽然官方声称漏洞已被修复，但我们仍然尝试这些方法，因为：
     * - 某些设备或 Android 版本可能仍然允许
     * - 某些定制 ROM 可能放宽了限制
     * - 修复可能不彻底
     * - 可以作为快速路径，如果失败再使用 SAF
     */
    private fun tryDirectFileAccess(path: String): List<FileItem>? {
        // 首先清理路径中的特殊字符
        val cleanedPath = cleanPath(path)
        
        // 如果路径被清理后发生变化，记录日志
        if (cleanedPath != path) {
            Log.d(TAG, "tryDirectFileAccess: Path cleaned, original=$path, cleaned=$cleanedPath")
        }
        
        return try {
            // 优先尝试零宽空格方法（如果适用）
            val fileArray = RestrictedPathHelper.listFilesWithZeroWidthPriority(cleanedPath)
            if (fileArray != null && fileArray.isNotEmpty()) {
                Log.d(TAG, "tryDirectFileAccess: Zero-width priority succeeded for $path, found ${fileArray.size} files")
                return fileArray.mapNotNull { file ->
                    try {
                        FileItem(
                            file = file,
                            name = file.name,
                            path = file.absolutePath,
                            isDirectory = file.isDirectory,
                            size = if (file.isFile) file.length() else 0L,
                            lastModified = file.lastModified(),
                            extension = if (file.isFile) file.name.substringAfterLast('.', "").lowercase() else "",
                            childCount = 0
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to create FileItem: ${file.name}", e)
                        null
                    }
                }
            } else {
                Log.d(TAG, "tryDirectFileAccess: Zero-width priority returned null or empty")
            }
            null
        } catch (e: Exception) {
            Log.d(TAG, "tryDirectFileAccess: Failed to access $path directly: ${e.message}")
            null
        }
    }
    
    /**
     * 使用 SAF 列出受限目录的内容
     * 
     * 访问策略（类似 MT 管理器）：
     * 0. 优先尝试零宽空格绕过方法（最高优先级）
     * 1. 尝试直接 File API（某些设备可能允许）
     * 2. 如果失败，尝试 fromSingleUri() 方式
     * 3. 最后回退到从根目录递归遍历的方式
     * 
     * 支持以下情况：
     * 1. 用户授权了 "Android" 目录 - 可以访问 Android/data 和 Android/obb
     * 2. 用户授权了 "Android/data" 目录 - 只能访问 Android/data
     * 3. 用户授权了 "Android/obb" 目录 - 只能访问 Android/obb
     */
    fun listDirectoryWithSaf(context: Context, path: String): List<FileItem>? {
        val uri = androidUri ?: run {
            Log.w(TAG, "listDirectoryWithSaf: androidUri is null")
            return null
        }
        val normalizedPath = path.replace("/sdcard/", "/storage/emulated/0/").trimEnd('/')
        
        Log.d(TAG, "listDirectoryWithSaf: path=$normalizedPath, uri=$uri")
        
        try {
            // 方法0：优先尝试零宽空格绕过（最高优先级，优先采用）
            val zeroWidthResult = tryZeroWidthSpaceBypass(normalizedPath)
            if (zeroWidthResult != null && zeroWidthResult.isNotEmpty()) {
                Log.d(TAG, "listDirectoryWithSaf: Zero-width space bypass succeeded, found ${zeroWidthResult.size} files")
                return zeroWidthResult
            }
            
            // 方法1：尝试直接 File API（某些设备可能允许）
            val directFileResult = tryDirectFileAccess(normalizedPath)
            if (directFileResult != null && directFileResult.isNotEmpty()) {
                Log.d(TAG, "listDirectoryWithSaf: Direct File API succeeded, found ${directFileResult.size} files")
                return directFileResult
            }
            
            // 方法1：尝试使用 fromSingleUri() 直接通过路径获取 DocumentFile
            // 注意：根据实际测试，fromSingleUri() 可能可以创建 DocumentFile，但无法正确列出子文件
            // 因此这里只作为快速检查，如果返回空列表则回退到递归遍历
            val directDocFile = getDocumentFileFromPath(context, normalizedPath)
            if (directDocFile != null && directDocFile.exists() && directDocFile.canRead() && directDocFile.isDirectory) {
                Log.d(TAG, "listDirectoryWithSaf: Trying fromSingleUri() method")
                try {
                    val files = directDocFile.listFiles()
                    Log.d(TAG, "listDirectoryWithSaf: fromSingleUri() returned ${files.size} files")
                    
                    // 如果返回了文件，使用它；如果为空，回退到递归遍历
                    if (files.isNotEmpty()) {
                        Log.d(TAG, "listDirectoryWithSaf: Using fromSingleUri() method (found files)")
                        return files.mapNotNull { docFile ->
                            try {
                                documentFileToFileItem(docFile, normalizedPath)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to convert DocumentFile: ${docFile.name}", e)
                                null
                            }
                        }
                    } else {
                        Log.d(TAG, "listDirectoryWithSaf: fromSingleUri() returned empty list, falling back to tree navigation")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "listDirectoryWithSaf: fromSingleUri() listFiles() failed: ${e.message}, falling back to tree navigation")
                }
            } else {
                Log.d(TAG, "listDirectoryWithSaf: fromSingleUri() failed (docFile=$directDocFile), falling back to tree navigation")
            }
            
            // 方法2：从根目录递归遍历的方式（这是最可靠的方法）
            Log.d(TAG, "listDirectoryWithSaf: Using tree navigation method")
            
            // 获取根 DocumentFile（用户授权的目录）
            val rootDocFile = DocumentFile.fromTreeUri(context, uri) ?: run {
                Log.w(TAG, "listDirectoryWithSaf: Failed to get DocumentFile from URI")
                return null
            }
            
            val rootName = rootDocFile.name?.lowercase() ?: ""
            Log.d(TAG, "listDirectoryWithSaf: rootDocFile name=$rootName, canRead=${rootDocFile.canRead()}")
            
            // 计算相对路径，需要根据授权的目录来决定
            val relativePath = calculateRelativePath(normalizedPath, rootName)
            
            if (relativePath == null) {
                Log.w(TAG, "listDirectoryWithSaf: Cannot calculate relative path for $normalizedPath (root=$rootName)")
                return null
            }
            
            Log.d(TAG, "listDirectoryWithSaf: relativePath=$relativePath")
            
            // 导航到目标目录
            val targetDocFile = if (relativePath.isEmpty()) {
                rootDocFile
            } else {
                navigateToPath(rootDocFile, relativePath)
            }
            
            if (targetDocFile == null) {
                Log.w(TAG, "listDirectoryWithSaf: Target directory not found after navigation")
                return null
            }
            
            if (!targetDocFile.exists()) {
                Log.w(TAG, "listDirectoryWithSaf: Target directory does not exist")
                return null
            }
            
            if (!targetDocFile.canRead()) {
                Log.w(TAG, "listDirectoryWithSaf: Cannot read target directory")
                return null
            }
            
            // 列出文件
            val files = targetDocFile.listFiles()
            Log.d(TAG, "listDirectoryWithSaf: SAF found ${files.size} files in $path")
            
            return files.mapNotNull { docFile ->
                try {
                    documentFileToFileItem(docFile, normalizedPath)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to convert DocumentFile: ${docFile.name}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "listDirectoryWithSaf: Failed to list directory with SAF: $path", e)
            return null
        }
    }
    
    /**
     * 根据授权的目录计算相对路径
     * 
     * @param targetPath 目标路径（如 /storage/emulated/0/Android/data/com.example）
     * @param rootName 授权目录的名称（如 android, data, obb）
     * @return 相对路径，如果无法计算则返回 null
     */
    private fun calculateRelativePath(targetPath: String, rootName: String): String? {
        return when {
            // 授权的是 "Android" 目录
            rootName == "android" -> {
                val androidBasePath = "/storage/emulated/0/Android"
                if (targetPath.startsWith(androidBasePath)) {
                    targetPath.removePrefix(androidBasePath).trim('/')
                } else {
                    null
                }
            }
            
            // 授权的是 "data" 目录（Android/data）
            rootName == "data" -> {
                when {
                    // 访问 Android/data 本身
                    targetPath.endsWith("/Android/data") -> ""
                    // 访问 Android/data 的子目录
                    targetPath.contains("/Android/data/") -> {
                        targetPath.substringAfter("/Android/data/")
                    }
                    else -> null
                }
            }
            
            // 授权的是 "obb" 目录（Android/obb）
            rootName == "obb" -> {
                when {
                    // 访问 Android/obb 本身
                    targetPath.endsWith("/Android/obb") -> ""
                    // 访问 Android/obb 的子目录
                    targetPath.contains("/Android/obb/") -> {
                        targetPath.substringAfter("/Android/obb/")
                    }
                    else -> null
                }
            }
            
            // 其他情况：尝试智能匹配
            else -> {
                Log.d(TAG, "calculateRelativePath: Unknown root name '$rootName', trying smart match")
                // 检查 URI 字符串来判断授权的目录
                val uriStr = androidUri?.toString()?.lowercase() ?: ""
                when {
                    uriStr.contains("android%2fdata") || uriStr.contains("android/data") -> {
                        // 授权的是 Android/data
                        when {
                            targetPath.endsWith("/Android/data") -> ""
                            targetPath.contains("/Android/data/") -> targetPath.substringAfter("/Android/data/")
                            else -> null
                        }
                    }
                    uriStr.contains("android%2fobb") || uriStr.contains("android/obb") -> {
                        // 授权的是 Android/obb
                        when {
                            targetPath.endsWith("/Android/obb") -> ""
                            targetPath.contains("/Android/obb/") -> targetPath.substringAfter("/Android/obb/")
                            else -> null
                        }
                    }
                    uriStr.contains(":android") || uriStr.contains("%3aandroid") -> {
                        // 授权的是 Android 目录
                        val androidBasePath = "/storage/emulated/0/Android"
                        if (targetPath.startsWith(androidBasePath)) {
                            targetPath.removePrefix(androidBasePath).trim('/')
                        } else {
                            null
                        }
                    }
                    else -> {
                        // 兜底：尝试从路径末尾匹配
                        Log.w(TAG, "calculateRelativePath: Cannot determine root type from URI")
                        null
                    }
                }
            }
        }
    }
    
    /**
     * 导航到指定的相对路径
     * 
     * 使用 findFile() 方法逐级查找目录
     * 注意：findFile() 是区分大小写的，但实际文件名可能大小写不一致
     */
    private fun navigateToPath(root: DocumentFile, relativePath: String): DocumentFile? {
        if (relativePath.isEmpty()) return root
        
        val segments = relativePath.split("/").filter { it.isNotEmpty() }
        var current: DocumentFile? = root
        
        Log.d(TAG, "navigateToPath: Starting from root=${root.name}, relativePath=$relativePath, segments=${segments.size}")
        
        for ((index, segment) in segments.withIndex()) {
            Log.d(TAG, "navigateToPath: Looking for segment[$index]: $segment")
            
            if (current == null) {
                Log.w(TAG, "navigateToPath: Current is null at segment $segment")
                return null
            }
            
            // 尝试直接查找
            var next = current.findFile(segment)
            
            // 如果找不到，尝试列出所有文件进行大小写不敏感匹配
            if (next == null) {
                Log.d(TAG, "navigateToPath: findFile() failed for '$segment', trying case-insensitive search")
                val files = current.listFiles()
                next = files.firstOrNull { 
                    it.name?.equals(segment, ignoreCase = true) == true 
                }
                
                if (next != null) {
                    Log.d(TAG, "navigateToPath: Found '$segment' using case-insensitive search (actual name: ${next.name})")
                } else {
                    Log.w(TAG, "navigateToPath: Segment '$segment' not found in ${files.size} files")
                    // 列出前几个文件名用于调试
                    files.take(5).forEach { file ->
                        Log.d(TAG, "navigateToPath: Available file: ${file.name} (isDir=${file.isDirectory})")
                    }
                }
            } else {
                Log.d(TAG, "navigateToPath: Found segment '$segment' directly")
            }
            
            current = next
            if (current == null) {
                Log.w(TAG, "navigateToPath: Failed to find segment: $segment")
                return null
            }
        }
        
        Log.d(TAG, "navigateToPath: Successfully navigated to: ${current?.name}")
        return current
    }
    
    /**
     * 将 DocumentFile 转换为 FileItem
     */
    private fun documentFileToFileItem(docFile: DocumentFile, parentPath: String): FileItem {
        val name = docFile.name ?: "unknown"
        val isDirectory = docFile.isDirectory
        val filePath = if (parentPath.endsWith("/")) {
            "$parentPath$name"
        } else {
            "$parentPath/$name"
        }
        val file = File(filePath)
        
        return FileItem(
            file = file,
            name = name,
            path = filePath,
            isDirectory = isDirectory,
            size = if (isDirectory) 0L else docFile.length(),
            lastModified = docFile.lastModified(),
            extension = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase(),
            childCount = 0 // SAF 无法高效获取子项数量
        )
    }
    
    /**
     * 清除已保存的授权
     */
    fun clearPermissions(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        // 释放持久化权限
        androidUri?.let { uri ->
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri, 
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release Android permission", e)
            }
        }
        
        androidUri = null
        Log.d(TAG, "All SAF permissions cleared")
    }
    
    /**
     * 创建请求指定路径授权的 Intent
     * 
     * @param path 目标路径，如 /storage/emulated/0/Android/data/com.example
     * @return Intent，用于启动权限请求
     */
    fun createPermissionIntentForPath(path: String): Intent {
        return createAndroidPermissionIntent(path)
    }
    
    /**
     * 检查指定路径是否可以通过 SAF 访问
     * 
     * @param context 上下文
     * @param path 文件路径
     * @return true 如果可以访问
     */
    fun canAccessPath(context: Context, path: String): Boolean {
        if (!hasAndroidPermission(context)) {
            return false
        }
        
        val docFile = getDocumentFileFromPath(context, path)
        return docFile != null && docFile.exists() && docFile.canRead()
    }
    
    /**
     * 获取指定路径的 DocumentFile 对象
     * 
     * 优先使用 fromSingleUri() 方式，如果失败则返回 null
     * 
     * @param context 上下文
     * @param path 文件路径
     * @return DocumentFile 对象，如果无法访问则返回 null
     */
    fun getDocumentFile(context: Context, path: String): DocumentFile? {
        return getDocumentFileFromPath(context, path)
    }
    
    /**
     * 检查文件或目录是否存在（通过 SAF）
     * 
     * @param context 上下文
     * @param path 文件路径
     * @return true 如果存在
     */
    fun exists(context: Context, path: String): Boolean {
        val docFile = getDocumentFileFromPath(context, path)
        return docFile?.exists() == true
    }
    
    /**
     * 获取文件大小（通过 SAF）
     * 
     * @param context 上下文
     * @param path 文件路径
     * @return 文件大小（字节），如果是目录或不存在则返回 0
     */
    fun getFileSize(context: Context, path: String): Long {
        val docFile = getDocumentFileFromPath(context, path)
        return if (docFile?.exists() == true && !docFile.isDirectory) {
            docFile.length()
        } else {
            0L
        }
    }
    
    /**
     * 删除文件或目录（通过 SAF）
     * 
     * @param context 上下文
     * @param path 文件路径
     * @return true 如果删除成功
     */
    fun delete(context: Context, path: String): Boolean {
        val docFile = getDocumentFileFromPath(context, path)
        return docFile?.delete() == true
    }
    
    /**
     * 获取需要授权的目录类型（保留兼容性）
     */
    fun getRequiredPermissionType(path: String): PermissionType? {
        val normalizedPath = path.replace("/sdcard/", "/storage/emulated/0/")
        return when {
            normalizedPath.contains("/Android/data") -> PermissionType.ANDROID_DATA
            normalizedPath.contains("/Android/obb") -> PermissionType.ANDROID_OBB
            else -> null
        }
    }
    
    enum class PermissionType {
        ANDROID_DATA,
        ANDROID_OBB
    }
}
