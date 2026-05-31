package com.rostrum.ui.terminal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rostrum.core.shell.IShellSession
import com.rostrum.core.shell.ShellManager
import kotlinx.coroutines.launch

/**
 * 终端屏幕 - 紧凑版
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    shellManager: ShellManager,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    
    // 返回键处理
    BackHandler { onBack() }
    
    var shellSession by remember { mutableStateOf<IShellSession?>(null) }
    var currentProviderId by remember { mutableStateOf<String?>(null) }
    var showProviderMenu by remember { mutableStateOf(false) }
    
    // 初始化
    LaunchedEffect(Unit) {
        val providers = shellManager.getAvailableProviders()
        val provider = providers.firstOrNull()
        currentProviderId = provider?.id
        shellSession = shellManager.getDefaultSession().also { it.startSession() }
    }
    
    // 切换Provider
    fun switchProvider(providerId: String) {
        if (providerId == currentProviderId) return
        scope.launch {
            shellSession?.close()
            currentProviderId = providerId
            shellSession = shellManager.createSession(providerId).also { it.startSession() }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose { shellSession?.close() }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalTheme.background)
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalTheme.surface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TerminalTheme.text,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Text(
                text = "Terminal",
                color = TerminalTheme.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Shell切换
            Box {
                TextButton(
                    onClick = { showProviderMenu = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    val providerName = when {
                        currentProviderId?.contains("python") == true -> "Python"
                        else -> "System"
                    }
                    Text(
                        text = providerName,
                        color = TerminalTheme.cyan,
                        fontSize = 11.sp
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = TerminalTheme.cyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                DropdownMenu(
                    expanded = showProviderMenu,
                    onDismissRequest = { showProviderMenu = false },
                    modifier = Modifier.background(TerminalTheme.surface)
                ) {
                    shellManager.getAllProviders().forEach { provider ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (provider.isAvailable) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = TerminalTheme.green,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Cancel,
                                            contentDescription = null,
                                            tint = TerminalTheme.red,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = provider.name,
                                        color = if (provider.isAvailable) TerminalTheme.text else TerminalTheme.textMuted,
                                        fontSize = 12.sp
                                    )
                                    if (provider.id == currentProviderId) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "✓",
                                            color = TerminalTheme.green,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            },
                            onClick = {
                                if (provider.isAvailable) {
                                    switchProvider(provider.id)
                                }
                                showProviderMenu = false
                            },
                            enabled = provider.isAvailable
                        )
                    }
                }
            }
        }
        
        // 终端视图
        shellSession?.let { session ->
            EnhancedTerminalView(
                shellSession = session,
                modifier = Modifier.fillMaxSize()
            )
        } ?: Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "加载中...",
                color = TerminalTheme.accent
            )
        }
    }
}

/**
 * 快速终端
 */
@Composable
fun QuickTerminal(
    shellManager: ShellManager,
    modifier: Modifier = Modifier,
    maxHeight: Int = 200
) {
    var session by remember { mutableStateOf<IShellSession?>(null) }
    
    LaunchedEffect(Unit) {
        session = shellManager.getDefaultSession().also { it.startSession() }
    }
    
    DisposableEffect(Unit) {
        onDispose { session?.close() }
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = TerminalTheme.background),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight.dp)
    ) {
        session?.let {
            EnhancedTerminalView(
                shellSession = it,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
