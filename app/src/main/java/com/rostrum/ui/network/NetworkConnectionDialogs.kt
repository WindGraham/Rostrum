package com.rostrum.ui.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 网络连接类型
 */
enum class NetworkConnectionType(
    val label: String,
    val icon: ImageVector,
    val description: String
) {
    SSH("SSH 终端", Icons.Default.Terminal, "远程命令行终端"),
    SFTP("SFTP 文件", Icons.Default.Folder, "安全文件传输"),
    FTP("FTP 文件", Icons.Default.CloudDownload, "文件传输协议"),
    DISCOVERY("服务发现", Icons.Default.WifiFind, "扫描局域网服务")
}

/**
 * 网络连接类型选择器对话框
 */
@Composable
fun NetworkConnectionSelectorDialog(
    onDismiss: () -> Unit,
    onSelectType: (NetworkConnectionType) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "网络连接",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                // 连接类型列表
                NetworkConnectionType.entries.forEach { type ->
                    NetworkConnectionTypeItem(
                        type = type,
                        onClick = {
                            onSelectType(type)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkConnectionTypeItem(
    type: NetworkConnectionType,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = type.icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = type.label,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = type.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * SSH 连接对话框
 * 
 * @param savedConnections 已保存的连接列表，用于显示和选择
 * @param onDismiss 关闭回调
 * @param onConnect 连接回调，包含是否保存连接的参数
 */
@Composable
fun SshConnectionDialog(
    savedConnections: List<com.rostrum.core.ssh.connection.SshConfig> = emptyList(),
    onDismiss: () -> Unit,
    onConnect: (host: String, port: Int, username: String, password: String?, useKeyAuth: Boolean, shouldSave: Boolean, connectionName: String) -> Unit,
    onSelectSavedConnection: ((com.rostrum.core.ssh.connection.SshConfig) -> Unit)? = null,
    onDeleteSavedConnection: ((String) -> Unit)? = null
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useKeyAuth by remember { mutableStateOf(false) }
    var saveConnection by remember { mutableStateOf(true) }
    var connectionName by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var showSavedConnections by remember { mutableStateOf(savedConnections.isNotEmpty()) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    
    // 当选择已保存的连接时填充表单
    fun fillFormFromSavedConnection(config: com.rostrum.core.ssh.connection.SshConfig) {
        host = config.host
        port = config.port.toString()
        username = config.username
        connectionName = config.name
        // 不填充密码，需要用户重新输入
        password = ""
        saveConnection = false // 从已保存连接选择时默认不重复保存
    }
    
    Dialog(onDismissRequest = { if (!isConnecting) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SSH 连接",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, enabled = !isConnecting) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                }
                
                Divider()
                
                // 已保存的连接列表（可折叠）
                if (savedConnections.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSavedConnections = !showSavedConnections },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "已保存的连接 (${savedConnections.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = if (showSavedConnections) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showSavedConnections) "收起" else "展开"
                                )
                            }
                            
                            if (showSavedConnections) {
                                Spacer(modifier = Modifier.height(8.dp))
                                savedConnections.forEach { config ->
                                    SavedConnectionItem(
                                        config = config,
                                        onSelect = {
                                            fillFormFromSavedConnection(config)
                                            onSelectSavedConnection?.invoke(config)
                                            showSavedConnections = false // 选择后自动收起列表
                                            // 选择后自动滚动到表单顶部
                                            scope.launch {
                                                delay(100) // 等待折叠动画
                                                scrollState.animateScrollTo(0)
                                            }
                                        },
                                        onDelete = { onDeleteSavedConnection?.invoke(config.id) }
                                    )
                                }
                            }
                        }
                    }
                    
                    Divider()
                }
                
                // 连接名称（仅在保存时显示）
                if (saveConnection) {
                    OutlinedTextField(
                        value = connectionName,
                        onValueChange = { connectionName = it },
                        label = { Text("连接名称（可选）") },
                        placeholder = { Text("例如: 我的服务器") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Label, null) },
                        singleLine = true,
                        enabled = !isConnecting
                    )
                }
                
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("例如: 192.168.1.100") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Cloud, null) },
                    singleLine = true,
                    enabled = !isConnecting
                )
                
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("端口") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Numbers, null) },
                    singleLine = true,
                    enabled = !isConnecting
                )
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    placeholder = { Text("例如: root") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    singleLine = true,
                    enabled = !isConnecting
                )
                
                if (!useKeyAuth) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !isConnecting
                    )
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useKeyAuth,
                        onCheckedChange = { useKeyAuth = it },
                        enabled = !isConnecting,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "使用密钥认证",
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !isConnecting) { useKeyAuth = !useKeyAuth },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = saveConnection,
                        onCheckedChange = { saveConnection = it },
                        enabled = !isConnecting,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "保存连接信息",
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !isConnecting) { saveConnection = !saveConnection },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isConnecting
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            if (host.isNotBlank() && username.isNotBlank()) {
                                isConnecting = true
                                onConnect(
                                    host,
                                    port.toIntOrNull() ?: 22,
                                    username,
                                    if (useKeyAuth) null else password,
                                    useKeyAuth,
                                    saveConnection,
                                    connectionName.takeIf { it.isNotBlank() } ?: "$username@$host"
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isConnecting && host.isNotBlank() && username.isNotBlank() && (useKeyAuth || password.isNotBlank())
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isConnecting) "连接中..." else "连接")
                    }
                }
            }
        }
    }
}

/**
 * 已保存的连接项
 */
@Composable
private fun SavedConnectionItem(
    config: com.rostrum.core.ssh.connection.SshConfig,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Computer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = config.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${config.username}@${config.host}:${config.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 网络发现对话框
 */
@Composable
fun NetworkDiscoveryDialog(
    onDismiss: () -> Unit,
    onServiceFound: (String, Int, String) -> Unit = { _, _, _ -> }
) {
    var isDiscovering by remember { mutableStateOf(false) }
    var discoveredServices by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "网络服务发现",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                if (isDiscovering) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("正在发现网络服务...")
                        }
                    }
                } else if (discoveredServices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.WifiFind,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "点击下方按钮开始发现网络服务",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(discoveredServices.size) { index ->
                            val (serviceName, port) = discoveredServices[index]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    onServiceFound(serviceName, port, "")
                                    onDismiss()
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = serviceName,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "端口: $port",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.Default.ChevronRight, null)
                                }
                            }
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            isDiscovering = true
                            discoveredServices = emptyList()
                            // TODO: 实现实际的网络发现逻辑
                            // 暂时模拟一些服务
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                kotlinx.coroutines.delay(2000)
                                discoveredServices = listOf(
                                    Pair("FTP Server", 21),
                                    Pair("SFTP Server", 22),
                                    Pair("SMB Server", 445)
                                )
                                isDiscovering = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isDiscovering
                    ) {
                        Text(if (isDiscovering) "发现中..." else "开始发现")
                    }
                }
            }
        }
    }
}

/**
 * SFTP 连接对话框
 */
@Composable
fun SftpConnectionDialog(
    onDismiss: () -> Unit,
    onConnect: (String, Int, String, String?) -> Unit = { _, _, _, _ -> }
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useKeyAuth by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SFTP 连接",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                }
                
                Divider()
                
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("服务器地址") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Cloud, null) }
                )
                
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("端口") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Numbers, null) }
                )
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Person, null) }
                )
                
                if (!useKeyAuth) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            if (host.isNotBlank() && username.isNotBlank()) {
                                onConnect(host, port.toIntOrNull() ?: 22, username, if (useKeyAuth) null else password)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = host.isNotBlank() && username.isNotBlank()
                    ) {
                        Text("连接")
                    }
                }
            }
        }
    }
}

/**
 * FTP 连接对话框
 */
@Composable
fun FtpConnectionDialog(
    onDismiss: () -> Unit,
    onConnect: (String, Int, String?, String?) -> Unit = { _, _, _, _ -> }
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("21") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var anonymous by remember { mutableStateOf(true) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FTP 连接",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                }
                
                Divider()
                
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("服务器地址") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Cloud, null) }
                )
                
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("端口") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Numbers, null) }
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = anonymous,
                        onCheckedChange = { anonymous = it }
                    )
                    Text("匿名登录", modifier = Modifier.padding(start = 8.dp))
                }
                
                if (!anonymous) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("用户名") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, null) }
                    )
                    
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            if (host.isNotBlank()) {
                                onConnect(
                                    host,
                                    port.toIntOrNull() ?: 21,
                                    if (anonymous) null else username,
                                    if (anonymous) null else password
                                )
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = host.isNotBlank() && (anonymous || username.isNotBlank())
                    ) {
                        Text("连接")
                    }
                }
            }
        }
    }
}

