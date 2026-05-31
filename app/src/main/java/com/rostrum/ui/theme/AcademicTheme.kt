package com.rostrum.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * 学术风格主题定义
 * 特点：
 * - 米白色纸张质感背景 #f0ede8
 * - 深色文字 #0e0d0d
 * - 衬线字体（宋体风格）作为主要字体
 * - 低饱和度暖色调
 * - 细边框、半透明效果
 * - 大部分直角，小部分圆角
 */

// ==================== 学术风格颜色 ====================

// 主色调 - 低饱和度暖色
val AcademicPrimary = Color(0xFF5C5A57)           // 暖灰褐
val AcademicOnPrimary = Color(0xFFF5F3EF)         // 米白
val AcademicPrimaryContainer = Color(0xFFE8E4DD)  // 浅米
val AcademicOnPrimaryContainer = Color(0xFF3D3B38)// 深灰褐

// 次色调
val AcademicSecondary = Color(0xFF6B6966)         // 中灰
val AcademicOnSecondary = Color(0xFFF5F3EF)
val AcademicSecondaryContainer = Color(0xFFE0DBD3)
val AcademicOnSecondaryContainer = Color(0xFF454341)

// 第三色调
val AcademicTertiary = Color(0xFF7A7874)          // 浅灰
val AcademicOnTertiary = Color(0xFFF5F3EF)
val AcademicTertiaryContainer = Color(0xFFD8D3CB)
val AcademicOnTertiaryContainer = Color(0xFF4D4B48)

// 背景色 - 纸张质感
val AcademicBackground = Color(0xFFF0EDE8)        // 主背景 米白
val AcademicOnBackground = Color(0xFF0E0D0D)      // 主文字 深黑
val AcademicBackgroundDark = Color(0xFF1A1917)    // 深色模式背景
val AcademicOnBackgroundDark = Color(0xFFE8E4DD)  // 深色模式文字

// 表面色
val AcademicSurface = Color(0xFFF5F3EF)           // 卡片表面
val AcademicOnSurface = Color(0xFF0E0D0D)
val AcademicSurfaceVariant = Color(0xFFEBE7E0)    // 变体表面
val AcademicOnSurfaceVariant = Color(0xFF3D3B38)

// 深色模式表面
val AcademicSurfaceDark = Color(0xFF252422)
val AcademicOnSurfaceDark = Color(0xFFE8E4DD)
val AcademicSurfaceVariantDark = Color(0xFF32302D)
val AcademicOnSurfaceVariantDark = Color(0xFFB5B0A8)

// 错误色
val AcademicError = Color(0xFFB3261E)
val AcademicOnError = Color(0xFFFFFFFF)
val AcademicErrorContainer = Color(0xFFF9DEDC)
val AcademicOnErrorContainer = Color(0xFF410E0B)

// 轮廓色
val AcademicOutline = Color(0xFF0E0D0D).copy(alpha = 0.1f)
val AcademicOutlineVariant = Color(0xFF0E0D0D).copy(alpha = 0.05f)
val AcademicOutlineDark = Color(0xFFE8E4DD).copy(alpha = 0.2f)
val AcademicOutlineVariantDark = Color(0xFFE8E4DD).copy(alpha = 0.1f)

// 反色
val AcademicInverseSurface = Color(0xFF313033)
val AcademicInverseOnSurface = Color(0xFFF4EFF4)
val AcademicInversePrimary = Color(0xFFD0C4B8)

// 高亮标记色
val AcademicHighlight = Color(0xFFF5E6C8)         // 暖黄高亮
val AcademicHighlightDark = Color(0xFF5C4D2E)

// ==================== 学术风格配色方案 ====================

/**
 * 学术风格浅色配色方案
 */
fun academicLightColorScheme(): ColorScheme = lightColorScheme(
    primary = AcademicPrimary,
    onPrimary = AcademicOnPrimary,
    primaryContainer = AcademicPrimaryContainer,
    onPrimaryContainer = AcademicOnPrimaryContainer,
    
    secondary = AcademicSecondary,
    onSecondary = AcademicOnSecondary,
    secondaryContainer = AcademicSecondaryContainer,
    onSecondaryContainer = AcademicOnSecondaryContainer,
    
    tertiary = AcademicTertiary,
    onTertiary = AcademicOnTertiary,
    tertiaryContainer = AcademicTertiaryContainer,
    onTertiaryContainer = AcademicOnTertiaryContainer,
    
    background = AcademicBackground,
    onBackground = AcademicOnBackground,
    
    surface = AcademicSurface,
    onSurface = AcademicOnSurface,
    surfaceVariant = AcademicSurfaceVariant,
    onSurfaceVariant = AcademicOnSurfaceVariant,
    
    error = AcademicError,
    onError = AcademicOnError,
    errorContainer = AcademicErrorContainer,
    onErrorContainer = AcademicOnErrorContainer,
    
    outline = AcademicOutline,
    outlineVariant = AcademicOutlineVariant,
    
    inverseSurface = AcademicInverseSurface,
    inverseOnSurface = AcademicInverseOnSurface,
    inversePrimary = AcademicInversePrimary,
    
    surfaceTint = AcademicPrimary.copy(alpha = 0.1f)
)

/**
 * 学术风格深色配色方案
 */
fun academicDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = AcademicOnPrimary.copy(alpha = 0.9f),
    onPrimary = AcademicPrimary,
    primaryContainer = AcademicSurfaceVariantDark,
    onPrimaryContainer = AcademicOnBackgroundDark,
    
    secondary = AcademicOnSecondary.copy(alpha = 0.85f),
    onSecondary = AcademicSecondary,
    secondaryContainer = Color(0xFF3D3B38),
    onSecondaryContainer = AcademicOnBackgroundDark,
    
    tertiary = AcademicOnTertiary.copy(alpha = 0.8f),
    onTertiary = AcademicTertiary,
    tertiaryContainer = Color(0xFF4D4B48),
    onTertiaryContainer = AcademicOnBackgroundDark,
    
    background = AcademicBackgroundDark,
    onBackground = AcademicOnBackgroundDark,
    
    surface = AcademicSurfaceDark,
    onSurface = AcademicOnSurfaceDark,
    surfaceVariant = AcademicSurfaceVariantDark,
    onSurfaceVariant = AcademicOnSurfaceVariantDark,
    
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    
    outline = AcademicOutlineDark,
    outlineVariant = AcademicOutlineVariantDark,
    
    inverseSurface = AcademicSurface,
    inverseOnSurface = AcademicOnSurface,
    inversePrimary = AcademicPrimary,
    
    surfaceTint = AcademicOnPrimary.copy(alpha = 0.1f)
)

// ==================== 学术风格字体 ====================

/**
 * 学术风格字体族定义
 * 使用系统衬线字体，在中文环境下通常对应宋体类字体
 * 如果系统有 Noto Serif CJK，会优先使用
 */
val AcademicFontFamily = FontFamily.Serif

/**
 * 代码等宽字体
 */
val AcademicCodeFontFamily = FontFamily.Monospace

/**
 * 学术风格基础文本样式
 * 所有文本默认使用衬线字体
 */
private fun academicTextStyle(
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    letterSpacing: androidx.compose.ui.unit.TextUnit = 0.sp
): TextStyle = TextStyle(
    fontFamily = AcademicFontFamily,
    fontSize = fontSize,
    lineHeight = lineHeight,
    fontWeight = fontWeight,
    letterSpacing = letterSpacing
)

/**
 * 学术风格排版
 * 特点：
 * - 所有文本使用衬线字体（宋体风格）
 * - 标题使用较重字重 (600)
 * - 正文使用正常字重 (400-500)
 * - 更大的行高，提高可读性
 */
fun academicTypography(): Typography {
    return Typography(
        displayLarge = academicTextStyle(
            fontSize = 57.sp,
            lineHeight = 64.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.25).sp
        ),
        displayMedium = academicTextStyle(
            fontSize = 45.sp,
            lineHeight = 52.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp
        ),
        displaySmall = academicTextStyle(
            fontSize = 36.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp
        ),
        headlineLarge = academicTextStyle(
            fontSize = 32.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp
        ),
        headlineMedium = academicTextStyle(
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp
        ),
        headlineSmall = academicTextStyle(
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp
        ),
        titleLarge = academicTextStyle(
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp
        ),
        titleMedium = academicTextStyle(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.15.sp
        ),
        titleSmall = academicTextStyle(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.1.sp
        ),
        bodyLarge = academicTextStyle(
            fontSize = 16.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = academicTextStyle(
            fontSize = 14.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.25.sp
        ),
        bodySmall = academicTextStyle(
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.4.sp
        ),
        labelLarge = academicTextStyle(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.1.sp
        ),
        labelMedium = academicTextStyle(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp
        ),
        labelSmall = academicTextStyle(
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp
        )
    )
}

/**
 * 代码文本样式
 */
fun codeTextStyle(
    fontSize: Int = 14,
    lineHeight: Int = 20
): TextStyle = TextStyle(
    fontFamily = AcademicCodeFontFamily,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    fontWeight = FontWeight.Normal,
    letterSpacing = 0.sp
)

// ==================== 学术风格形状 ====================

/**
 * 学术风格形状定义
 * 特点：
 * - 大部分组件使用直角（小圆角）
 * - 小部分按钮使用圆角
 * - 卡片使用轻微圆角
 */
val AcademicShapes = Shapes(
    // 小形状 - 按钮、输入框等，使用小圆角
    small = RoundedCornerShape(4.dp),
    // 中等形状 - 卡片、对话框等
    medium = RoundedCornerShape(8.dp),
    // 大形状 - 底部导航、模态框等
    large = RoundedCornerShape(12.dp)
)

/**
 * 严格学术形状 - 更多直角
 */
val AcademicStrictShapes = Shapes(
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(8.dp)
)

/**
 * 简洁模式形状 - 更大的圆角，更亲民的设计
 * 用于非开发者模式下的UI组件
 */
val SimpleModeShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

// ==================== 简洁模式配色 ====================

/**
 * 简洁模式浅色配色
 * 特点：柔和的蓝绿色强调色，米白背景
 */
val SimpleModeBackground = Color(0xFFFAF9F7)       // 米白背景
val SimpleModeSurface = Color(0xFFFFFFFF)         // 纯白表面
val SimpleModePrimary = Color(0xFF26A69A)         // 柔和蓝绿色
val SimpleModePrimaryContainer = Color(0xFFB2DFDB) // 浅蓝绿容器
val SimpleModeSecondary = Color(0xFF5C6BC0)       // 柔和靛蓝
val SimpleModeSecondaryContainer = Color(0xFFE8EAF6) // 浅靛蓝容器

// ==================== 学术风格工具函数 ====================

/**
 * 获取学术风格的分隔线颜色
 */
fun academicDividerColor(isDark: Boolean): Color {
    return if (isDark) {
        AcademicOnBackgroundDark.copy(alpha = 0.1f)
    } else {
        AcademicOnBackground.copy(alpha = 0.08f)
    }
}

/**
 * 获取学术风格的卡片背景色（带轻微透明）
 */
fun academicCardBackground(isDark: Boolean): Color {
    return if (isDark) {
        AcademicSurfaceDark.copy(alpha = 0.95f)
    } else {
        AcademicSurface.copy(alpha = 0.95f)
    }
}

/**
 * 获取学术风格的玻璃态效果颜色
 */
fun academicGlassmorphismBackground(isDark: Boolean): Color {
    return if (isDark) {
        AcademicSurfaceDark.copy(alpha = 0.6f)
    } else {
        AcademicSurface.copy(alpha = 0.6f)
    }
}

/**
 * 获取学术风格的边框颜色
 */
fun academicBorderColor(isDark: Boolean): Color {
    return if (isDark) {
        AcademicOutlineDark
    } else {
        AcademicOutline
    }
}
