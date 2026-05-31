package com.rostrum.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.*
import com.rostrum.core.shell.IShellSession
import com.rostrum.ui.main.viewmodel.ShellViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun TerminalView(
    shellSession: StateFlow<IShellSession>,
    shellMode: StateFlow<ShellViewModel.ShellMode>,
    directSession: IShellSession? = null,
    title: String = "本机",
    subtitle: String = "Local shell",
    onSwitchShellMode: (ShellViewModel.ShellMode, ((IShellSession) -> Unit)?) -> Unit,
    onExecutePythonScript: (String, List<String>) -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val localSession by shellSession.collectAsState()
    val currentSession = directSession ?: localSession
    val currentMode by shellMode.collectAsState()
    val output by currentSession.output.collectAsState()
    var input by remember { mutableStateOf("") }
    var historyIndex by remember { mutableStateOf(-1) }
    var fontSize by remember { mutableStateOf(13f) }
    val listState = rememberLazyListState()
    val isRemoteTerminal = directSession != null

    // 缓存 ANSI 解析后的行列表，避免每次重组都重新分割和解析
    val parsedLines = remember(output) {
        output.split("\n").map { line -> AnsiUtils.parseAnsi(line) }
    }

    // Auto scroll to bottom when output changes
    LaunchedEffect(parsedLines.size) {
        if (parsedLines.isNotEmpty()) {
            listState.animateScrollToItem((parsedLines.size - 1).coerceAtLeast(0))
        }
    }
    
    // 处理命令的辅助函数
    fun handleCommand(command: String) {
        if (isRemoteTerminal) {
            currentSession.sendCommand(command)
            return
        }
        val trimmedInput = command.trim()
        
        when {
            // 切换到 Python REPL
            (trimmedInput == "python" || trimmedInput == "ipython") && 
            currentMode != ShellViewModel.ShellMode.PYTHON -> {
                onSwitchShellMode(ShellViewModel.ShellMode.PYTHON, null)
            }
            
            // 从 Python 模式退出
            (trimmedInput == "exit" || trimmedInput == "quit()") && 
            (currentMode == ShellViewModel.ShellMode.PYTHON || 
             currentMode == ShellViewModel.ShellMode.PYTHON_SCRIPT) -> {
                onSwitchShellMode(ShellViewModel.ShellMode.SYSTEM, null)
            }
            
            // 在 System Shell 中执行 python script.py [args]
            trimmedInput.matches(Regex("^python\\s+.*\\.py.*$")) && 
            currentMode == ShellViewModel.ShellMode.SYSTEM -> {
                // 解析命令: python script.py arg1 arg2
                val parts = trimmedInput.substringAfter("python").trim().split("\\s+".toRegex())
                val scriptPath = parts[0]
                val args = parts.drop(1)
                
                // 使用MainViewModel的新方法执行脚本
                onExecutePythonScript(scriptPath, args)
            }
            
            // 在 Python REPL 中输入 .py 文件路径
            trimmedInput.endsWith(".py") && 
            currentMode == ShellViewModel.ShellMode.PYTHON &&
            !trimmedInput.contains(" ") -> {
                // 验证文件存在
                val file = java.io.File(trimmedInput)
                if (file.exists() && file.isFile) {
                    // 切换到脚本执行模式
                    onExecutePythonScript(trimmedInput, emptyList())
                } else {
                    // 当作普通 Python 代码处理
                    currentSession.sendCommand(command)
                }
            }
            
            // 在 Python Script 模式下,所有输入都发送给脚本进程
            currentMode == ShellViewModel.ShellMode.PYTHON_SCRIPT -> {
                currentSession.sendCommand(command)
            }
            
            // 默认：直接发送命令
            else -> {
                currentSession.sendCommand(command)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF060B12))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(Color(0xFF0A1220))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (currentSession.isReady()) Color(0xFF22C55E) else Color(0xFFF59E0B))
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(subtitle, color = Color(0xFF64748B), fontSize = 10.sp, maxLines = 1)
            }
            Text("${fontSize.toInt()}sp", color = Color(0xFF64748B), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            IconButton(onClick = { currentSession.clearOutput() }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "清屏", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
            }
            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                }
            }
        }

        // Output Area - 使用 LazyColumn 虚拟化渲染，避免一次渲染全部终端输出
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        fontSize = (fontSize * zoom).coerceIn(9f, 24f)
                    }
                }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            items(
                count = parsedLines.size,
                key = { index -> index }  // 稳定 key 避免不必要的重组
            ) { index ->
                Text(
                    text = parsedLines[index],
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.26f).sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        
        // Quick Actions
        LazyRow(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF07111D)),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val commands = if (isRemoteTerminal) {
                listOf("ls", "cd ..", "pwd", "clear", "Ctrl+C")
            } else when (currentMode) {
                ShellViewModel.ShellMode.PYTHON ->
                    listOf("print('hello')", "import sys", "sys.version", "dir()", "help()", "📁 Script", "exit")
                ShellViewModel.ShellMode.PYTHON_SCRIPT ->
                    listOf("exit")  // 脚本执行时只显示退出选项
                ShellViewModel.ShellMode.SYSTEM ->
                    listOf("ls", "cd ..", "pwd", "whoami", "clear", "Python")
            }
            items(commands) { cmd ->
                OutlinedButton(
                    onClick = {
                        when {
                            cmd == "clear" -> currentSession.clearOutput()
                            cmd == "Ctrl+C" -> currentSession.sendCommand("\u0003")
                            cmd == "Python" -> onSwitchShellMode(ShellViewModel.ShellMode.PYTHON, null)
                            cmd == "📁 Script" -> {
                                currentSession.appendToOutput("Hint: Enter a .py file path directly to execute it\n")
                                currentSession.appendToOutput("Example: /storage/emulated/0/script.py\n>>> ")
                            }
                            else -> currentSession.sendCommand(cmd)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFE5E7EB)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Text(cmd, fontSize = 11.sp)
                }
            }
        }

        // Input Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A1220))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isRemoteTerminal) "ssh $ " else when (currentMode) {
                    ShellViewModel.ShellMode.PYTHON -> ">>> "
                    ShellViewModel.ShellMode.PYTHON_SCRIPT -> "(script) "
                    ShellViewModel.ShellMode.SYSTEM -> "$ "
                },
                color = when (currentMode) {
                    ShellViewModel.ShellMode.PYTHON -> Color(0xFF4CAF50)
                    ShellViewModel.ShellMode.PYTHON_SCRIPT -> Color(0xFFFF9800)
                    ShellViewModel.ShellMode.SYSTEM -> Color(0xFF2196F3)
                },
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold
            )
            
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionUp -> {
                                    val history = currentSession.commandHistory
                                    if (history.isNotEmpty()) {
                                        val newIndex = if (historyIndex == -1) history.lastIndex else (historyIndex - 1).coerceAtLeast(0)
                                        historyIndex = newIndex
                                        input = history[newIndex]
                                        true
                                    } else false
                                }
                                Key.DirectionDown -> {
                                    val history = currentSession.commandHistory
                                    if (historyIndex != -1) {
                                        val newIndex = historyIndex + 1
                                        if (newIndex < history.size) {
                                            historyIndex = newIndex
                                            input = history[newIndex]
                                        } else {
                                            historyIndex = -1
                                            input = ""
                                        }
                                        true
                                    } else false
                                }
                                else -> false
                            }
                        } else false
                    },
                textStyle = TextStyle(
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp
                ),
                cursorBrush = SolidColor(Color.Green),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (input.isNotBlank()) {
                        handleCommand(input)
                        input = ""
                        historyIndex = -1
                    }
                })
            )
            
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        handleCommand(input)
                        input = ""
                        historyIndex = -1
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
