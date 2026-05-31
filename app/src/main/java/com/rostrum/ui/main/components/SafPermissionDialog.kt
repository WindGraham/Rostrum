package com.rostrum.ui.main.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rostrum.core.util.SafAccessManager
import com.rostrum.util.PrivilegedAccessManager
import com.rostrum.ui.main.PrivilegedBridgeGuideDialog
import com.rostrum.ui.main.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * SAF权限请求对话框
 * 支持多种授权方式：SAF、Root、内置特权桥接
 */
@Composable
fun SafPermissionDialog(
    permissionRequest: MainViewModel.SafPermissionRequest,
    permissionStatus: PrivilegedAccessManager.PermissionStatus?,
    context: Context,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onSafLaunch: (Intent) -> Unit,
    onRootRequest: suspend () -> Boolean,
    onPermissionResult: (Boolean) -> Unit,
    onRefreshPermissionStatus: suspend () -> PrivilegedAccessManager.PermissionStatus
) {
    var showPrivilegedBridgeDialog by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要授权访问") },
        text = {
            Column {
                val dirName = when (permissionRequest.type) {
                    SafAccessManager.PermissionType.ANDROID_DATA -> "Android/data"
                    SafAccessManager.PermissionType.ANDROID_OBB -> "Android/obb"
                    else -> "Android"
                }
                
                Text(
                    "Android 11+ 限制了对 $dirName 目录的访问。\n\n" +
                    "请选择一种授权方式（推荐使用 SAF，无需 Root 或额外应用）：",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 当前权限状态显示
                permissionStatus?.let { status ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "当前状态：",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                buildString {
                                    append(if (status.hasRoot) "✓ " else "✗ ")
                                    append("Root 权限\n")

                                    append(if (status.bridgeReady) "✓ " else "✗ ")
                                    append("内置特权桥接\n")

                                    append(if (status.hasSafPermission) "✓ " else "✗ ")
                                    append("SAF 权限")
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column {
                // SAF 授权按钮（推荐，放在最前面）
                TextButton(
                    onClick = {
                        val intent = SafAccessManager.createAndroidPermissionIntent()
                        onSafLaunch(intent)
                    }
                ) {
                    Text("📁 使用 SAF（推荐）")
                }
                
                // Root 授权按钮
                TextButton(
                    onClick = {
                        scope.launch {
                            val success = onRootRequest()
                            if (success) {
                                snackbarHostState.showSnackbar("Root 授权成功！")
                                onRefreshPermissionStatus()
                                onPermissionResult(true)
                            } else {
                                snackbarHostState.showSnackbar("Root 授权失败，请确保设备已 Root")
                            }
                        }
                    }
                ) {
                    Text("🔓 请求 Root")
                }

                // 内置高权限通道按钮（主路径）
                TextButton(
                    onClick = { showPrivilegedBridgeDialog = true }
                ) {
                    Text("🚀 内置高权限通道")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
    
    // 内置高权限通道引导对话框
    if (showPrivilegedBridgeDialog) {
        PrivilegedBridgeGuideDialog(
            onDismiss = { showPrivilegedBridgeDialog = false },
            onSuccess = {
                showPrivilegedBridgeDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("内置高权限通道已就绪")
                    onRefreshPermissionStatus()
                    onPermissionResult(true)
                }
            }
        )
    }
}
