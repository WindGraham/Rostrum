package com.termux.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.termux.app.ssh.SshConfig
import com.termux.app.ssh.SshConnectionState

@Composable
fun ConnectionsScreen(
    connections: List<SshConfig>,
    connectionStates: Map<String, SshConnectionState>,
    onDisconnect: (SshConfig) -> Unit
) {
    val connectedConnections = connections.filter { connectionStates[it.id] is SshConnectionState.Connected }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("连接管理", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.height(16.dp))
        if (connectedConnections.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF334155), modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("没有活动连接", color = Color(0xFF94A3B8))
                    Spacer(Modifier.height(4.dp))
                    Text("在主机页连接本机或 SSH 主机", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            connectedConnections.forEach { connection ->
                val state = connectionStates[connection.id]
                val uptime = if (state is SshConnectionState.Connected) {
                    val diff = System.currentTimeMillis() - state.connectedAt
                    val minutes = diff / 60000
                    if (minutes < 60) "${minutes}分钟"
                    else "${minutes / 60}小时${minutes % 60}分钟"
                } else ""
                Surface(
                    color = Color(0xFF1E293B),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(72.dp).padding(vertical = 4.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF22C55E))
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(connection.displayName, color = Color.White)
                            Text("${connection.username}@${connection.host}:${connection.port} · $uptime", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onDisconnect(connection) }) {
                            Text("断开", color = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }
    }
}
