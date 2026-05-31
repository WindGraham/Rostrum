package com.rostrum.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Termius 风格的深色主题颜色
object TermiusColors {
    // 背景色
    val Background = Color(0xFF1E1E1E)
    val Surface = Color(0xFF252526)
    val SurfaceVariant = Color(0xFF2D2D2D)
    val SurfaceTint = Color(0xFF333333)
    
    // 主色调
    val Primary = Color(0xFF4FC3F7)      // 亮蓝色
    val PrimaryVariant = Color(0xFF03A9F4)
    val OnPrimary = Color(0xFF000000)
    
    // 次要色
    val Secondary = Color(0xFF81C784)    // 柔和绿色
    val SecondaryVariant = Color(0xFF4CAF50)
    val OnSecondary = Color(0xFF000000)
    
    // 第三色
    val Tertiary = Color(0xFFFFB74D)    // 橙色
    val OnTertiary = Color(0xFF000000)
    
    // 错误色
    val Error = Color(0xFFEF5350)
    val OnError = Color(0xFFFFFFFF)
    
    // 文本色
    val OnBackground = Color(0xFFD4D4D4)
    val OnSurface = Color(0xFFD4D4D4)
    val OnSurfaceVariant = Color(0xFF9E9E9E)
    
    // 边框色
    val Outline = Color(0xFF404040)
    val OutlineVariant = Color(0xFF333333)
    
    // 终端颜色
    val TerminalBackground = Color(0xFF1E1E1E)
    val TerminalForeground = Color(0xFFD4D4D4)
    val TerminalCursor = Color(0xFFD4D4D4)
    val TerminalSelection = Color(0xFF264F78)
    
    // 终端ANSI颜色
    val TerminalBlack = Color(0xFF000000)
    val TerminalRed = Color(0xFFCD3131)
    val TerminalGreen = Color(0xFF0DBC79)
    val TerminalYellow = Color(0xFFE5E510)
    val TerminalBlue = Color(0xFF2472C8)
    val TerminalMagenta = Color(0xFFBC3FBC)
    val TerminalCyan = Color(0xFF11A8CD)
    val TerminalWhite = Color(0xFFE5E5E5)
    val TerminalBrightBlack = Color(0xFF666666)
    val TerminalBrightRed = Color(0xFFF14C4C)
    val TerminalBrightGreen = Color(0xFF23D18B)
    val TerminalBrightYellow = Color(0xFFF5F543)
    val TerminalBrightBlue = Color(0xFF3B8EEA)
    val TerminalBrightMagenta = Color(0xFFD670D6)
    val TerminalBrightCyan = Color(0xFF29B8DB)
    val TerminalBrightWhite = Color(0xFFFFFFFF)
}

// Termius 风格的深色配色方案
fun termiusDarkColorScheme(): ColorScheme {
    return darkColorScheme(
        primary = TermiusColors.Primary,
        onPrimary = TermiusColors.OnPrimary,
        primaryContainer = TermiusColors.PrimaryVariant,
        onPrimaryContainer = TermiusColors.OnPrimary,
        secondary = TermiusColors.Secondary,
        onSecondary = TermiusColors.OnSecondary,
        secondaryContainer = TermiusColors.SecondaryVariant,
        onSecondaryContainer = TermiusColors.OnSecondary,
        tertiary = TermiusColors.Tertiary,
        onTertiary = TermiusColors.OnTertiary,
        tertiaryContainer = TermiusColors.Tertiary,
        onTertiaryContainer = TermiusColors.OnTertiary,
        error = TermiusColors.Error,
        onError = TermiusColors.OnError,
        errorContainer = TermiusColors.Error,
        onErrorContainer = TermiusColors.OnError,
        background = TermiusColors.Background,
        onBackground = TermiusColors.OnBackground,
        surface = TermiusColors.Surface,
        onSurface = TermiusColors.OnSurface,
        surfaceVariant = TermiusColors.SurfaceVariant,
        onSurfaceVariant = TermiusColors.OnSurfaceVariant,
        outline = TermiusColors.Outline,
        outlineVariant = TermiusColors.OutlineVariant,
        inverseSurface = TermiusColors.OnSurface,
        inverseOnSurface = TermiusColors.Surface,
        inversePrimary = TermiusColors.PrimaryVariant,
        surfaceTint = TermiusColors.SurfaceTint
    )
}

// Termius 风格的浅色配色方案
fun termiusLightColorScheme(): ColorScheme {
    return lightColorScheme(
        primary = TermiusColors.PrimaryVariant,
        onPrimary = TermiusColors.OnPrimary,
        primaryContainer = TermiusColors.Primary,
        onPrimaryContainer = TermiusColors.OnPrimary,
        secondary = TermiusColors.SecondaryVariant,
        onSecondary = TermiusColors.OnSecondary,
        secondaryContainer = TermiusColors.Secondary,
        onSecondaryContainer = TermiusColors.OnSecondary,
        tertiary = TermiusColors.Tertiary,
        onTertiary = TermiusColors.OnTertiary,
        tertiaryContainer = TermiusColors.Tertiary,
        onTertiaryContainer = TermiusColors.OnTertiary,
        error = TermiusColors.Error,
        onError = TermiusColors.OnError,
        errorContainer = TermiusColors.Error,
        onErrorContainer = TermiusColors.OnError,
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1E1E1E),
        surface = Color(0xFFF5F5F5),
        onSurface = Color(0xFF1E1E1E),
        surfaceVariant = Color(0xFFE0E0E0),
        onSurfaceVariant = Color(0xFF666666),
        outline = Color(0xFFBDBDBD),
        outlineVariant = Color(0xFFE0E0E0),
        inverseSurface = Color(0xFF1E1E1E),
        inverseOnSurface = Color(0xFFF5F5F5),
        inversePrimary = TermiusColors.Primary,
        surfaceTint = Color(0xFFF5F5F5)
    )
}

/**
 * Rostrum 主题（Termius 风格）
 * 
 * @param darkTheme 是否使用深色主题
 * @param content 主题内容
 */
@Composable
fun RostrumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        termiusDarkColorScheme()
    } else {
        termiusLightColorScheme()
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(),
        content = content
    )
}

/**
 * 终端颜色方案
 */
data class TerminalColorScheme(
    val background: Color,
    val foreground: Color,
    val cursor: Color,
    val selection: Color,
    val black: Color,
    val red: Color,
    val green: Color,
    val yellow: Color,
    val blue: Color,
    val magenta: Color,
    val cyan: Color,
    val white: Color,
    val brightBlack: Color,
    val brightRed: Color,
    val brightGreen: Color,
    val brightYellow: Color,
    val brightBlue: Color,
    val brightMagenta: Color,
    val brightCyan: Color,
    val brightWhite: Color
)

/**
 * 获取Termius风格的终端颜色方案
 */
fun termiusTerminalColorScheme(): TerminalColorScheme {
    return TerminalColorScheme(
        background = TermiusColors.TerminalBackground,
        foreground = TermiusColors.TerminalForeground,
        cursor = TermiusColors.TerminalCursor,
        selection = TermiusColors.TerminalSelection,
        black = TermiusColors.TerminalBlack,
        red = TermiusColors.TerminalRed,
        green = TermiusColors.TerminalGreen,
        yellow = TermiusColors.TerminalYellow,
        blue = TermiusColors.TerminalBlue,
        magenta = TermiusColors.TerminalMagenta,
        cyan = TermiusColors.TerminalCyan,
        white = TermiusColors.TerminalWhite,
        brightBlack = TermiusColors.TerminalBrightBlack,
        brightRed = TermiusColors.TerminalBrightRed,
        brightGreen = TermiusColors.TerminalBrightGreen,
        brightYellow = TermiusColors.TerminalBrightYellow,
        brightBlue = TermiusColors.TerminalBrightBlue,
        brightMagenta = TermiusColors.TerminalBrightMagenta,
        brightCyan = TermiusColors.TerminalBrightCyan,
        brightWhite = TermiusColors.TerminalBrightWhite
    )
}