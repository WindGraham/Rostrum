package com.rostrum.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.rostrum.core.runtime.RuntimeInitializer
import com.rostrum.core.runtime.model.PrivilegeBackend
import com.rostrum.core.runtime.model.PrivilegedShellRequest
import com.rostrum.core.runtime.model.ShellMcpPolicy
import com.rostrum.core.util.SafAccessManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 统一特权访问管理器。
 */
object PrivilegedAccessManager {
    private const val TAG = "PrivilegedAccessManager"

    enum class AccessMethod {
        ROOT,
        SHIZUKU_INTERNAL,
        SAF,
        STANDARD
    }

    data class PermissionStatus(
        val hasRoot: Boolean,
        val bridgeReady: Boolean,
        val hasSafPermission: Boolean,
        val hasManageExternalStorage: Boolean,
        val hasQueryAllPackages: Boolean,
        val currentMethod: AccessMethod
    )

    private val restrictedPaths = listOf(
        "/storage/emulated/0/Android/data",
        "/storage/emulated/0/Android/obb",
        "/sdcard/Android/data",
        "/sdcard/Android/obb"
    )

    fun isRestrictedPath(path: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false
        }
        val normalizedPath = path.replace("//", "/")
        return restrictedPaths.any { restricted ->
            normalizedPath.startsWith(restricted) || normalizedPath == restricted.trimEnd('/')
        }
    }

    suspend fun getPermissionStatus(context: Context): PermissionStatus = withContext(Dispatchers.IO) {
        val shellStatus = RuntimeInitializer.privilegedShellService?.status()
        val hasRoot = RootHelper.isRootAvailable()
        val bridgeReady = shellStatus?.bridgeReady == true
        val hasSaf = SafAccessManager.hasAndroidPermission(context)

        val hasManageStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

        val hasQueryAllPackages = true

        val currentMethod = when {
            shellStatus?.activeBackend == PrivilegeBackend.SHIZUKU_INTERNAL_ROOT ||
                shellStatus?.activeBackend == PrivilegeBackend.SHIZUKU_INTERNAL_ADB ||
                bridgeReady -> AccessMethod.SHIZUKU_INTERNAL
            hasRoot -> AccessMethod.ROOT
            hasSaf -> AccessMethod.SAF
            else -> AccessMethod.STANDARD
        }

        PermissionStatus(
            hasRoot = hasRoot,
            bridgeReady = bridgeReady,
            hasSafPermission = hasSaf,
            hasManageExternalStorage = hasManageStorage,
            hasQueryAllPackages = hasQueryAllPackages,
            currentMethod = currentMethod
        )
    }

    suspend fun getBestAccessMethod(context: Context): AccessMethod = withContext(Dispatchers.IO) {
        val shellStatus = RuntimeInitializer.privilegedShellService?.status()
        when (shellStatus?.activeBackend) {
            PrivilegeBackend.SHIZUKU_INTERNAL_ROOT,
            PrivilegeBackend.SHIZUKU_INTERNAL_ADB -> AccessMethod.SHIZUKU_INTERNAL
            else -> when {
                RootHelper.isRootAvailable() -> AccessMethod.ROOT
                SafAccessManager.hasAndroidPermission(context) -> AccessMethod.SAF
                else -> AccessMethod.STANDARD
            }
        }
    }

    suspend fun canAccessRestrictedPath(context: Context): Boolean = withContext(Dispatchers.IO) {
        RootHelper.isRootAvailable() ||
            (RuntimeInitializer.privilegedShellService?.status()?.bridgeReady == true) ||
            SafAccessManager.hasAndroidPermission(context)
    }

    suspend fun listDirectory(context: Context, path: String): List<FileEntry>? = withContext(Dispatchers.IO) {
        val file = File(path)

        if (!isRestrictedPath(path)) {
            val standardResult = listDirectoryStandard(file)
            if (standardResult != null) {
                return@withContext standardResult
            }
        }

        val shellService = RuntimeInitializer.privilegedShellService
        val shellResult = shellService?.exec(
            PrivilegedShellRequest(
                command = "ls",
                args = listOf("-la", path),
                sessionId = "file_access",
                policy = ShellMcpPolicy.SESSION_AUTO,
                approved = true
            )
        )
        if (shellResult?.success == true) {
            val parsed = parseLsOutput(shellResult.output, path, shellResult.backend)
            if (parsed.isNotEmpty()) {
                return@withContext parsed
            }
        }

        val method = getBestAccessMethod(context)
        Log.d(TAG, "Listing directory '$path' using fallback method: $method")

        when (method) {
            AccessMethod.ROOT -> {
                RootHelper.listDirectory(path)?.map { fileInfo ->
                    FileEntry(
                        name = fileInfo.name,
                        path = fileInfo.path,
                        isDirectory = fileInfo.isDirectory,
                        size = fileInfo.size,
                        lastModified = 0L,
                        accessMethod = AccessMethod.ROOT
                    )
                }
            }

            AccessMethod.SHIZUKU_INTERNAL -> null

            AccessMethod.SAF -> {
                SafAccessManager.listDirectoryWithSaf(context, path)?.map { fileItem ->
                    FileEntry(
                        name = fileItem.name,
                        path = fileItem.path,
                        isDirectory = fileItem.isDirectory,
                        size = fileItem.size,
                        lastModified = fileItem.lastModified,
                        accessMethod = AccessMethod.SAF
                    )
                }
            }

            AccessMethod.STANDARD -> listDirectoryStandard(file)
        }
    }

    private fun listDirectoryStandard(file: File): List<FileEntry>? {
        return try {
            if (!file.exists() || !file.isDirectory) return null

            file.listFiles()?.map { f ->
                FileEntry(
                    name = f.name,
                    path = f.absolutePath,
                    isDirectory = f.isDirectory,
                    size = if (f.isFile) f.length() else 0L,
                    lastModified = f.lastModified(),
                    accessMethod = AccessMethod.STANDARD
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Standard file listing failed: ${e.message}")
            null
        }
    }

    private fun parseLsOutput(
        output: String,
        basePath: String,
        backend: PrivilegeBackend
    ): List<FileEntry> {
        val accessMethod = when (backend) {
            PrivilegeBackend.SHIZUKU_INTERNAL_ROOT,
            PrivilegeBackend.SHIZUKU_INTERNAL_ADB -> AccessMethod.SHIZUKU_INTERNAL
            PrivilegeBackend.NONE -> AccessMethod.STANDARD
        }

        return output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("total") }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"), limit = 9)
                if (parts.size < 8) return@mapNotNull null
                val perms = parts[0]
                val size = parts.getOrNull(4)?.toLongOrNull() ?: 0L
                val name = (parts.getOrNull(8) ?: parts.lastOrNull()).orEmpty().trim()
                if (name.isBlank() || name == "." || name == "..") return@mapNotNull null
                val actualName = name.substringBefore(" -> ")
                FileEntry(
                    name = actualName,
                    path = "$basePath/$actualName",
                    isDirectory = perms.startsWith("d"),
                    size = size,
                    lastModified = 0L,
                    accessMethod = accessMethod
                )
            }
            .toList()
    }

    data class FileEntry(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,
        val accessMethod: AccessMethod
    )

    fun getMethodDescription(method: AccessMethod): String {
        return when (method) {
            AccessMethod.ROOT -> "Root权限"
            AccessMethod.SHIZUKU_INTERNAL -> "内置特权桥接"
            AccessMethod.SAF -> "存储访问框架(SAF)"
            AccessMethod.STANDARD -> "标准文件访问"
        }
    }

    fun getPermissionHint(status: PermissionStatus): String {
        return buildString {
            if (status.hasManageExternalStorage) {
                append("✓ 已获取所有文件访问权限\n")
            } else {
                append("✗ 未获取所有文件访问权限\n")
            }

            if (status.hasQueryAllPackages) {
                append("✓ 已获取应用查询权限\n")
            } else {
                append("✗ 未获取应用查询权限\n")
            }

            if (status.hasRoot) {
                append("✓ 已获取Root权限\n")
            } else {
                append("✗ 未获取Root权限\n")
            }

            if (status.bridgeReady) {
                append("✓ 内置特权桥接可用\n")
            } else {
                append("✗ 内置特权桥接不可用\n")
            }

            if (status.hasSafPermission) {
                append("✓ 已获取SAF权限")
            } else {
                append("✗ 未获取SAF权限")
            }
        }
    }

    fun canUseAppStorageManagement(status: PermissionStatus): Boolean {
        return status.hasManageExternalStorage && status.hasQueryAllPackages
    }
}
