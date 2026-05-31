@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.rostrum.ui.terminal

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rostrum.core.config.TerminalColorScheme
import com.rostrum.core.config.TerminalConfig
import com.rostrum.core.shell.IShellSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 终端颜色主题数据类
 */
data class TerminalColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val text: Color,
    val textDim: Color,
    val textMuted: Color,
    val black: Color,
    val red: Color,
    val green: Color,
    val yellow: Color,
    val blue: Color,
    val magenta: Color,
    val cyan: Color,
    val white: Color,
    val brightBlack: Color,
    val brightWhite: Color,
    val cursor: Color,
    val accent: Color,
    val orange: Color
)

/**
 * 根据配色方案获取终端颜色
 */
fun getTerminalColors(scheme: TerminalColorScheme): TerminalColors {
    return when (scheme) {
        TerminalColorScheme.DEFAULT, TerminalColorScheme.DARK -> TerminalColors(
            background = Color(0xFF1E1E2E),
            surface = Color(0xFF313244),
            surfaceVariant = Color(0xFF45475A),
            text = Color(0xFFCDD6F4),
            textDim = Color(0xFFA6ADC8),
            textMuted = Color(0xFF6C7086),
            black = Color(0xFF45475A),
            red = Color(0xFFF38BA8),
            green = Color(0xFFA6E3A1),
            yellow = Color(0xFFF9E2AF),
            blue = Color(0xFF89B4FA),
            magenta = Color(0xFFCBA6F7),
            cyan = Color(0xFF94E2D5),
            white = Color(0xFFCDD6F4),
            brightBlack = Color(0xFF6C7086),
            brightWhite = Color(0xFFFFFFFF),
            cursor = Color(0xFFF5E0DC),
            accent = Color(0xFFCBA6F7),
            orange = Color(0xFFFAB387)
        )
        TerminalColorScheme.LIGHT -> TerminalColors(
            background = Color(0xFFFAFAFA),
            surface = Color(0xFFEEEEEE),
            surfaceVariant = Color(0xFFE0E0E0),
            text = Color(0xFF212121),
            textDim = Color(0xFF616161),
            textMuted = Color(0xFF9E9E9E),
            black = Color(0xFF212121),
            red = Color(0xFFD32F2F),
            green = Color(0xFF388E3C),
            yellow = Color(0xFFF57C00),
            blue = Color(0xFF1976D2),
            magenta = Color(0xFF7B1FA2),
            cyan = Color(0xFF0097A7),
            white = Color(0xFFFAFAFA),
            brightBlack = Color(0xFF757575),
            brightWhite = Color(0xFFFFFFFF),
            cursor = Color(0xFF212121),
            accent = Color(0xFF7B1FA2),
            orange = Color(0xFFFF9800)
        )
        TerminalColorScheme.SOLARIZED -> TerminalColors(
            background = Color(0xFF002B36),
            surface = Color(0xFF073642),
            surfaceVariant = Color(0xFF586E75),
            text = Color(0xFF839496),
            textDim = Color(0xFF657B83),
            textMuted = Color(0xFF586E75),
            black = Color(0xFF073642),
            red = Color(0xFFDC322F),
            green = Color(0xFF859900),
            yellow = Color(0xFFB58900),
            blue = Color(0xFF268BD2),
            magenta = Color(0xFFD33682),
            cyan = Color(0xFF2AA198),
            white = Color(0xFFEEE8D5),
            brightBlack = Color(0xFF586E75),
            brightWhite = Color(0xFFFDF6E3),
            cursor = Color(0xFF93A1A1),
            accent = Color(0xFF268BD2),
            orange = Color(0xFFCB4B16)
        )
        TerminalColorScheme.MONOKAI -> TerminalColors(
            background = Color(0xFF272822),
            surface = Color(0xFF3E3D32),
            surfaceVariant = Color(0xFF49483E),
            text = Color(0xFFF8F8F2),
            textDim = Color(0xFFCFCFC2),
            textMuted = Color(0xFF75715E),
            black = Color(0xFF272822),
            red = Color(0xFFF92672),
            green = Color(0xFFA6E22E),
            yellow = Color(0xFFF4BF75),
            blue = Color(0xFF66D9EF),
            magenta = Color(0xFFAE81FF),
            cyan = Color(0xFFA1EFE4),
            white = Color(0xFFF8F8F2),
            brightBlack = Color(0xFF75715E),
            brightWhite = Color(0xFFF9F8F5),
            cursor = Color(0xFFF8F8F0),
            accent = Color(0xFFAE81FF),
            orange = Color(0xFFFD971F)
        )
    }
}

/**
 * 默认终端颜色主题（向后兼容）
 */
object TerminalTheme {
    val background = Color(0xFF1E1E2E)
    val surface = Color(0xFF313244)
    val surfaceVariant = Color(0xFF45475A)
    
    val text = Color(0xFFCDD6F4)
    val textDim = Color(0xFFA6ADC8)
    val textMuted = Color(0xFF6C7086)
    
    val black = Color(0xFF45475A)
    val red = Color(0xFFF38BA8)
    val green = Color(0xFFA6E3A1)
    val yellow = Color(0xFFF9E2AF)
    val blue = Color(0xFF89B4FA)
    val magenta = Color(0xFFCBA6F7)
    val cyan = Color(0xFF94E2D5)
    val white = Color(0xFFCDD6F4)
    
    val brightBlack = Color(0xFF6C7086)
    val brightWhite = Color(0xFFFFFFFF)
    
    val cursor = Color(0xFFF5E0DC)
    val accent = Color(0xFFCBA6F7)
    val orange = Color(0xFFFAB387)
}

val TerminalFont = FontFamily.Monospace

/**
 * ANSI颜色映射
 */
fun getAnsiColor(code: Int, bright: Boolean = false): Color {
    return when (code) {
        0 -> if (bright) TerminalTheme.brightBlack else TerminalTheme.black
        1 -> TerminalTheme.red
        2 -> TerminalTheme.green
        3 -> TerminalTheme.yellow
        4 -> TerminalTheme.blue
        5 -> TerminalTheme.magenta
        6 -> TerminalTheme.cyan
        7 -> if (bright) TerminalTheme.brightWhite else TerminalTheme.white
        else -> TerminalTheme.text
    }
}

/**
 * 256色支持
 */
fun get256Color(n: Int): Color {
    return when {
        n < 16 -> getAnsiColor(n % 8, n >= 8)
        n < 232 -> {
            val index = n - 16
            val r = (index / 36) * 51
            val g = ((index / 6) % 6) * 51
            val b = (index % 6) * 51
            Color(r, g, b)
        }
        else -> {
            val gray = (n - 232) * 10 + 8
            Color(gray, gray, gray)
        }
    }
}

data class AnsiSegment(
    val text: String,
    val fg: Color = TerminalTheme.text,
    val bg: Color? = null,
    val bold: Boolean = false,
    val underline: Boolean = false
)

/**
 * 解析ANSI序列
 */
fun parseAnsi(rawText: String): List<AnsiSegment> {
    // 清理控制序列（保留SGR颜色）
    var text = rawText
        .replace(Regex("\u001B\\[\\?[0-9;]*[a-zA-Z]"), "")
        .replace(Regex("\u001B\\[[0-9;]*[ABCDEFGHJKSTfnsu]"), "")
        .replace(Regex("\u001B\\]0;[^\u0007]*\u0007"), "")
        .replace(Regex("\u001B\\]0;[^\u001B]*\u001B\\\\"), "")
        .replace(Regex("\u001B[^\\[m]"), "")
    
    val segments = mutableListOf<AnsiSegment>()
    val pattern = Regex("\u001B\\[([0-9;]*)m")
    
    var fg = TerminalTheme.text
    var bg: Color? = null
    var bold = false
    var underline = false
    var lastEnd = 0
    
    pattern.findAll(text).forEach { match ->
        if (match.range.first > lastEnd) {
            val content = text.substring(lastEnd, match.range.first)
            if (content.isNotEmpty()) {
                segments.add(AnsiSegment(content, fg, bg, bold, underline))
            }
        }
        
        val params = match.groupValues[1]
        if (params.isEmpty() || params == "0") {
            fg = TerminalTheme.text
            bg = null
            bold = false
            underline = false
        } else {
            val codes = params.split(";").mapNotNull { it.toIntOrNull() }
            var i = 0
            while (i < codes.size) {
                when (val c = codes[i]) {
                    0 -> { fg = TerminalTheme.text; bg = null; bold = false; underline = false }
                    1 -> bold = true
                    4 -> underline = true
                    22 -> bold = false
                    24 -> underline = false
                    in 30..37 -> fg = getAnsiColor(c - 30, bold)
                    38 -> {
                        if (i + 2 < codes.size && codes[i + 1] == 5) {
                            fg = get256Color(codes[i + 2])
                            i += 2
                        }
                    }
                    39 -> fg = TerminalTheme.text
                    in 40..47 -> bg = getAnsiColor(c - 40, false)
                    49 -> bg = null
                    in 90..97 -> fg = getAnsiColor(c - 90, true)
                    in 100..107 -> bg = getAnsiColor(c - 100, true)
                }
                i++
            }
        }
        lastEnd = match.range.last + 1
    }
    
    if (lastEnd < text.length) {
        val remaining = text.substring(lastEnd)
        if (remaining.isNotEmpty()) {
            segments.add(AnsiSegment(remaining, fg, bg, bold, underline))
        }
    }
    
    if (segments.isEmpty() && text.isNotEmpty()) {
        segments.add(AnsiSegment(text))
    }
    
    return segments
}

fun buildStyledText(segments: List<AnsiSegment>): AnnotatedString {
    return buildAnnotatedString {
        segments.forEach { s ->
            withStyle(SpanStyle(
                color = s.fg,
                background = s.bg ?: Color.Transparent,
                fontWeight = if (s.bold) FontWeight.Bold else FontWeight.Normal,
                textDecoration = if (s.underline) 
                    androidx.compose.ui.text.style.TextDecoration.Underline else null
            )) {
                append(s.text)
            }
        }
    }
}

/**
 * 快捷键按钮数据
 */
data class QuickKey(
    val label: String,
    val action: () -> Unit
)

/**
 * 终端视图
 */
@Composable
fun EnhancedTerminalView(
    shellSession: IShellSession,
    modifier: Modifier = Modifier,
    terminalConfig: TerminalConfig = TerminalConfig(),
    @Suppress("UNUSED_PARAMETER") onExpandRequest: (() -> Unit)? = null
) {
    val output by shellSession.output.collectAsState()
    val listState = rememberLazyListState()
    rememberCoroutineScope() // for future use
    val focusRequester = remember { FocusRequester() }
    
    var input by remember { mutableStateOf("") }
    var historyIndex by remember { mutableStateOf(-1) }
    
    // 根据配置获取终端颜色
    val colors = remember(terminalConfig.colorScheme) { 
        getTerminalColors(terminalConfig.colorScheme) 
    }
    val fontSize = terminalConfig.fontSize.sp
    
    // 缓存行分割和 ANSI 解析结果，避免每次重组都重新计算
    val parsedLines = remember(output) {
        output.split("\n").map { line ->
            val segments = parseAnsi(line)
            buildStyledText(segments)
        }
    }
    
    // 自动滚动
    LaunchedEffect(parsedLines.size) {
        delay(50)
        if (parsedLines.isNotEmpty()) {
            listState.animateScrollToItem((parsedLines.size - 1).coerceAtLeast(0))
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // 顶部状态栏
        Surface(color = colors.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 连接状态
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.green)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "Shell",
                    color = colors.text,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Medium,
                    fontFamily = TerminalFont
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // 清屏
                IconButton(
                    onClick = { shellSession.clearOutput() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = "Clear",
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        // 输出区域
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(
                    count = parsedLines.size,
                    key = { index -> index }
                ) { index ->
                    Text(
                        text = parsedLines[index],
                        fontFamily = TerminalFont,
                        fontSize = fontSize,
                        lineHeight = (fontSize.value * 1.3f).sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // 输入区域
        Surface(color = colors.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "❯",
                    color = colors.green,
                    fontSize = fontSize,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        ,
                    textStyle = TextStyle(
                        color = colors.text,
                        fontFamily = TerminalFont,
                        fontSize = fontSize
                    ),
                    cursorBrush = SolidColor(colors.cursor),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            val cmd = input.trim()
                            if (cmd.isNotEmpty()) {
                                android.util.Log.d("EnhancedTerminalView", "Keyboard send: $cmd")
                                shellSession.sendCommand(cmd)
                                input = ""
                                historyIndex = -1
                            }
                        }
                    ),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box {
                            if (input.isEmpty()) {
                                Text(
                                    text = "输入命令...",
                                    color = colors.textMuted,
                                    fontSize = fontSize,
                                    fontFamily = TerminalFont
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                FilledIconButton(
                    onClick = {
                        val cmd = input.trim()
                        if (cmd.isNotEmpty()) {
                            android.util.Log.d("EnhancedTerminalView", "Sending command: $cmd")
                            shellSession.sendCommand(cmd)
                            input = ""
                            historyIndex = -1
                        }
                    },
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (input.isNotBlank()) colors.green else colors.surfaceVariant,
                        contentColor = if (input.isNotBlank()) colors.background else colors.textMuted
                    )
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * 迷你终端视图
 */
@Composable
fun MiniTerminalView(
    shellSession: IShellSession,
    modifier: Modifier = Modifier,
    maxLines: Int = 5
) {
    val output by shellSession.output.collectAsState()
    
    val displayText = remember(output) {
        output.split("\n").takeLast(maxLines).joinToString("\n")
    }
    
    Surface(
        color = TerminalTheme.background,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = buildStyledText(parseAnsi(displayText)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            fontFamily = TerminalFont,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}
