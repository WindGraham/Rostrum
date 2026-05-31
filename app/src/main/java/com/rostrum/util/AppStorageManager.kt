package com.rostrum.util

import android.app.PendingIntent
import android.app.usage.StorageStats
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用存储管理器
 * 
 * 功能：
 * - 获取设备上所有已安装的应用（需要 QUERY_ALL_PACKAGES 权限）
 * - 获取应用的存储使用信息
 * - 调用其他应用的空间管理界面（Android 12+）
 * 
 * 权限要求：
 * - android.permission.QUERY_ALL_PACKAGES - 查询所有应用
 * - android.permission.MANAGE_EXTERNAL_STORAGE - 调用 getManageSpaceActivityIntent
 */
object AppStorageManager {
    private const val TAG = "AppStorageManager"
    
    /**
     * 应用信息数据类
     */
    data class AppInfo(
        val packageName: String,
        val appName: String,
        val icon: Drawable?,
        val versionName: String?,
        val versionCode: Long,
        val isSystemApp: Boolean,
        val installedTime: Long,
        val updatedTime: Long,
        val dataDir: String?,
        val sourceDir: String?
    )
    
    /**
     * 应用存储信息数据类
     */
    data class AppStorageInfo(
        val packageName: String,
        val appSize: Long,      // 应用大小（APK + lib）
        val dataSize: Long,     // 数据大小
        val cacheSize: Long,    // 缓存大小
        val totalSize: Long     // 总大小
    )
    
    /**
     * 获取所有已安装的应用列表
     * 
     * @param context 上下文
     * @param includeSystemApps 是否包含系统应用，默认为 false
     * @return 应用信息列表
     */
    suspend fun getInstalledApps(
        context: Context,
        includeSystemApps: Boolean = false
    ): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            val packageManager = context.packageManager
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
            }
            
            packages.mapNotNull { packageInfo ->
                try {
                    val isSystemApp = (packageInfo.applicationInfo?.flags ?: 0) and 
                            ApplicationInfo.FLAG_SYSTEM != 0
                    
                    // 根据参数过滤系统应用
                    if (!includeSystemApps && isSystemApp) {
                        return@mapNotNull null
                    }
                    
                    val appInfo = packageInfo.applicationInfo
                    
                    AppInfo(
                        packageName = packageInfo.packageName,
                        appName = appInfo?.loadLabel(packageManager)?.toString() 
                            ?: packageInfo.packageName,
                        icon = try { appInfo?.loadIcon(packageManager) } catch (e: Exception) { null },
                        versionName = packageInfo.versionName,
                        versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            packageInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            packageInfo.versionCode.toLong()
                        },
                        isSystemApp = isSystemApp,
                        installedTime = packageInfo.firstInstallTime,
                        updatedTime = packageInfo.lastUpdateTime,
                        dataDir = appInfo?.dataDir,
                        sourceDir = appInfo?.sourceDir
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get info for ${packageInfo.packageName}: ${e.message}")
                    null
                }
            }.sortedBy { it.appName.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get installed apps: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 获取指定应用的存储使用信息
     * 
     * 需要 Android 8.0 (API 26) 及以上
     * 
     * @param context 上下文
     * @param packageName 应用包名
     * @return 存储信息，如果获取失败返回 null
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getAppStorageInfo(
        context: Context,
        packageName: String
    ): AppStorageInfo? = withContext(Dispatchers.IO) {
        try {
            val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) 
                    as StorageStatsManager
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) 
                    as StorageManager
            
            val packageManager = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            
            val uuid = storageManager.getUuidForPath(
                Environment.getDataDirectory()
            )
            
            val storageStats: StorageStats = storageStatsManager.queryStatsForPackage(
                uuid,
                packageName,
                android.os.Process.myUserHandle()
            )
            
            AppStorageInfo(
                packageName = packageName,
                appSize = storageStats.appBytes,
                dataSize = storageStats.dataBytes,
                cacheSize = storageStats.cacheBytes,
                totalSize = storageStats.appBytes + storageStats.dataBytes + storageStats.cacheBytes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get storage info for $packageName: ${e.message}")
            null
        }
    }
    
    /**
     * 获取应用的空间管理 PendingIntent
     * 
     * 仅在 Android 12 (API 31) 及以上可用
     * 需要同时拥有 MANAGE_EXTERNAL_STORAGE 和 QUERY_ALL_PACKAGES 权限
     * 
     * @param context 上下文
     * @param packageName 目标应用包名
     * @param requestCode 请求码
     * @return PendingIntent，如果目标应用未定义空间管理 Activity 则返回 null
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun getManageSpaceIntent(
        context: Context,
        packageName: String,
        requestCode: Int = 0
    ): PendingIntent? {
        // 检查是否有 MANAGE_EXTERNAL_STORAGE 权限
        if (!Environment.isExternalStorageManager()) {
            Log.w(TAG, "MANAGE_EXTERNAL_STORAGE permission not granted")
            return null
        }
        
        return try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) 
                    as StorageManager
            
            storageManager.getManageSpaceActivityIntent(
                packageName,
                requestCode
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get manage space intent for $packageName: ${e.message}")
            null
        }
    }
    
    /**
     * 打开应用的系统存储设置页面
     * 
     * 这是通用方法，适用于所有 Android 版本
     * 
     * @param context 上下文
     * @param packageName 目标应用包名
     * @return 是否成功打开
     */
    fun openAppStorageSettings(context: Context, packageName: String): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open storage settings for $packageName: ${e.message}")
            false
        }
    }
    
    /**
     * 尝试打开应用的空间管理界面
     * 
     * 优先使用 getManageSpaceActivityIntent (Android 12+)
     * 如果不可用，则回退到系统存储设置页面
     * 
     * @param context 上下文
     * @param packageName 目标应用包名
     * @return 是否成功打开
     */
    fun openManageSpace(context: Context, packageName: String): Boolean {
        // Android 12+ 尝试使用 getManageSpaceActivityIntent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val pendingIntent = getManageSpaceIntent(context, packageName)
            if (pendingIntent != null) {
                return try {
                    pendingIntent.send()
                    true
                } catch (e: PendingIntent.CanceledException) {
                    Log.w(TAG, "PendingIntent was canceled: ${e.message}")
                    // 回退到系统设置
                    openAppStorageSettings(context, packageName)
                }
            }
        }
        
        // 回退到系统存储设置页面
        return openAppStorageSettings(context, packageName)
    }
    
    /**
     * 获取应用的 Android/data 目录路径
     * 
     * @param packageName 应用包名
     * @return 路径字符串
     */
    fun getAppDataPath(packageName: String): String {
        return "/storage/emulated/0/Android/data/$packageName"
    }
    
    /**
     * 获取应用的 Android/obb 目录路径
     * 
     * @param packageName 应用包名
     * @return 路径字符串
     */
    fun getAppObbPath(packageName: String): String {
        return "/storage/emulated/0/Android/obb/$packageName"
    }
    
    /**
     * 格式化存储大小为可读字符串
     * 
     * @param bytes 字节数
     * @return 格式化后的字符串（如 "1.5 GB"）
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
    
    /**
     * 检查指定应用是否已安装
     * 
     * @param context 上下文
     * @param packageName 应用包名
     * @return 是否已安装
     */
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * 获取设备上所有应用占用的 Android/data 目录列表
     * 
     * 注意：访问这些目录需要 Root/内置特权桥接/SAF 权限或零宽空格绕过方法
     * 优先使用零宽空格绕过方法
     * 
     * @return 包名列表
     */
    fun getAndroidDataPackages(): List<String> {
        return try {
            val dataDirPath = "/storage/emulated/0/Android/data"
            
            // 优先尝试零宽空格绕过方法
            val zeroWidthSpace = "\u200B" // U+200B
            val zeroWidthPath = dataDirPath.replace("/Android/data", "/Android${zeroWidthSpace}/data")
            val zeroWidthDir = java.io.File(zeroWidthPath)
            
            if (zeroWidthDir.exists() && zeroWidthDir.canRead() && zeroWidthDir.isDirectory) {
                val files = zeroWidthDir.listFiles()
                if (files != null && files.isNotEmpty()) {
                    Log.d(TAG, "getAndroidDataPackages: Zero-width space method succeeded, found ${files.size} packages")
                    return files.filter { it.isDirectory }.map { it.name }
                }
            }
            
            // 回退到标准方法
            val dataDir = java.io.File(dataDirPath)
            if (dataDir.exists() && dataDir.canRead()) {
                dataDir.listFiles()
                    ?.filter { it.isDirectory }
                    ?.map { it.name }
                    ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to list Android/data: ${e.message}")
            emptyList()
        }
    }
}
