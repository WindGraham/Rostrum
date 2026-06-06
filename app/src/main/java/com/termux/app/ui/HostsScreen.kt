package com.termux.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.termux.app.ssh.SshConfig
import com.termux.app.ssh.SshConnectionState
import java.util.UUID

@Composable
fun HostsScreen(
    connections: List<SshConfig>,
    connectionStates: Map<String, SshConnectionState>,
    localConnected: Boolean,
    onConnectLocal: () -> Unit,
    onAddHost: () -> Unit,
    onConnect: (SshConfig) -> Unit,
    onEdit: (SshConfig) -> Unit,
    onDelete: (SshConfig) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("主机", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text("管理本机和远程连接", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        Surface(
            onClick = onConnectLocal,
            color = if (localConnected) Color(0xFF1E3A5F) else Color(0xFF1E293B),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().height(80.dp).border(0.5.dp, Color(0xFF334155), MaterialTheme.shapes.medium)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Computer, contentDescription = null, tint = Color(0xFF38BDF8))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("本机", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("本地文件系统 · 本地终端", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        connections.forEach { connection ->
            val isActive = connectionStates[connection.id] is SshConnectionState.Connected
            Surface(
                onClick = { onConnect(connection) },
                color = if (isActive) Color(0xFF1E3A5F) else Color(0xFF1E293B),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(80.dp).padding(vertical = 4.dp).border(0.5.dp, Color(0xFF334155), MaterialTheme.shapes.medium)
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = if (isActive) Color(0xFF22C55E) else Color(0xFF64748B))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(connection.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("${connection.username}@${connection.host}:${connection.port}", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
                    }
                    if (isActive) {
                        Row(Modifier.background(Color(0xFF22C55E).copy(alpha=0.15f), MaterialTheme.shapes.small).padding(horizontal=6.dp, vertical=2.dp)) {
                            Text("已连接", color = Color(0xFF22C55E), style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = { onEdit(connection) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onDelete(connection) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "删除", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onAddHost, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("添加主机")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHostDialog(
    onDismiss: () -> Unit,
    onSaveAndConnect: (SshConfig) -> Unit,
    editConfig: SshConfig? = null
) {
    val isEditing = editConfig != null
    var name by remember { mutableStateOf(editConfig?.name ?: "") }
    var host by remember { mutableStateOf(editConfig?.host ?: "") }
    var port by remember { mutableStateOf(editConfig?.port?.toString() ?: "22") }
    var username by remember { mutableStateOf(editConfig?.username ?: "") }
    var authType by remember {
        mutableStateOf(if (editConfig?.authMethod is SshConfig.AuthMethod.Password) "密码" else "密钥文件")
    }
    var password by remember {
        mutableStateOf((editConfig?.authMethod as? SshConfig.AuthMethod.Password)?.password ?: "")
    }
    var keyPath by remember {
        mutableStateOf((editConfig?.authMethod as? SshConfig.AuthMethod.PrivateKey)?.keyId ?: "~/.ssh/id_rsa")
    }
    var initialDir by remember { mutableStateOf("/home/$username") }
    var timeout by remember { mutableStateOf(editConfig?.connectionTimeout?.let { "${it / 1000}秒" } ?: "30秒") }

    var authExpanded by remember { mutableStateOf(false) }
    var timeoutExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "编辑 SSH 主机" else "添加 SSH 主机", color = Color.White) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("连接名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("主机地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = port, onValueChange = { port = it.filter { c -> c.isDigit() } }, label = { Text("端口") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("用户名") }, singleLine = true, modifier = Modifier.weight(2f))
                }

                // Auth method dropdown
                ExposedDropdownMenuBox(expanded = authExpanded, onExpandedChange = { authExpanded = it }) {
                    OutlinedTextField(
                        value = authType, onValueChange = {},
                        readOnly = true, label = { Text("认证方式") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = authExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = authExpanded, onDismissRequest = { authExpanded = false }) {
                        DropdownMenuItem(text = { Text("密码") }, onClick = { authType = "密码"; authExpanded = false })
                        DropdownMenuItem(text = { Text("密钥文件") }, onClick = { authType = "密钥文件"; authExpanded = false })
                    }
                }

                when (authType) {
                    "密码" -> OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("密码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    "密钥文件" -> OutlinedTextField(value = keyPath, onValueChange = { keyPath = it }, label = { Text("密钥路径") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }

                OutlinedTextField(value = initialDir, onValueChange = { initialDir = it }, label = { Text("初始目录") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                // Timeout dropdown
                ExposedDropdownMenuBox(expanded = timeoutExpanded, onExpandedChange = { timeoutExpanded = it }) {
                    OutlinedTextField(
                        value = timeout, onValueChange = {},
                        readOnly = true, label = { Text("连接超时") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timeoutExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = timeoutExpanded, onDismissRequest = { timeoutExpanded = false }) {
                        listOf("10秒", "30秒", "60秒").forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = { timeout = t; timeoutExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = host.isNotBlank() && username.isNotBlank(),
                onClick = {
                    val authMethod = when (authType) {
                        "密钥文件" -> SshConfig.AuthMethod.PrivateKey(keyId = keyPath, passphrase = null)
                        else -> SshConfig.AuthMethod.Password(password)
                    }
                    val config = SshConfig(
                        id = editConfig?.id ?: UUID.randomUUID().toString(),
                        name = name.ifBlank { "$username@$host" },
                        host = host,
                        port = port.toIntOrNull() ?: 22,
                        username = username,
                        authMethod = authMethod,
                        strictHostKeyChecking = false,
                        connectionTimeout = when (timeout) { "10秒" -> 10000; "60秒" -> 60000; else -> 30000 }
                    )
                    onSaveAndConnect(config)
                }
            ) {
                Text(if (isEditing) "保存" else "连接")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
