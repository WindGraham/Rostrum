package com.rostrum.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 静态配色方案（作为后备）
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/**
 * OmniMaster 主题
 * 支持：
 * - 动态配色 - 用户可以通过滑动条选择主色调（现代风格）
 * - 学术风格 - 米白色纸张质感、宋体、细边框
 * 
 * @param darkTheme 是否使用深色主题
 * @param dynamicColor 是否使用动态配色（基于用户选择的主色调，仅现代风格有效）
 * @param content 主题内容
 */
@Composable
fun OmniMasterTheme(
    darkTheme: Boolean = when (ThemeManager.themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    },
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // 获取当前主题风格和主色调
    val primaryHue by remember { ThemeManager::primaryHue }
    val themeStyle by remember { ThemeManager::themeStyle }
    
    // 根据主题风格选择配色方案
    val colorScheme: ColorScheme = when (themeStyle) {
        ThemeStyle.ACADEMIC -> {
            if (darkTheme) {
                academicDarkColorScheme()
            } else {
                academicLightColorScheme()
            }
        }
        ThemeStyle.MODERN -> {
            when {
                dynamicColor -> {
                    if (darkTheme) {
                        ThemeManager.generateDarkColorScheme(primaryHue)
                    } else {
                        ThemeManager.generateLightColorScheme(primaryHue)
                    }
                }
                darkTheme -> DarkColorScheme
                else -> LightColorScheme
            }
        }
    }
    
    // 根据主题风格选择字体
    val typography: Typography = when (themeStyle) {
        ThemeStyle.ACADEMIC -> academicTypography()
        ThemeStyle.MODERN -> Typography
    }
    
    // 根据主题风格选择形状
    val shapes: Shapes = when (themeStyle) {
        ThemeStyle.ACADEMIC -> AcademicShapes
        ThemeStyle.MODERN -> MaterialTheme.shapes
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes,
        content = content
    )
}

/**
 * 获取当前是否为学术风格主题
 */
@Composable
fun isAcademicTheme(): Boolean {
    return ThemeManager.themeStyle == ThemeStyle.ACADEMIC
}/**
 * 获取当前主题风格
 */
@Composable
fun currentThemeStyle(): ThemeStyle {
    return ThemeManager.themeStyle
}/**
 * 获取适合当前主题的文本样式
 * 学术风格使用衬线字体，现代风格使用默认字体
 */
@Composable
fun themedTextStyle(
    baseStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge
): androidx.compose.ui.text.TextStyle {
    return if (isAcademicTheme()) {
        baseStyle.copy(fontFamily = AcademicFontFamily)
    } else {
        baseStyle
    }
}/**
 * 获取适合当前主题的代码文本样式
 */
@Composable
fun themedCodeTextStyle(
    fontSize: Int = 14,
    lineHeight: Int = 20
): androidx.compose.ui.text.TextStyle {
    return codeTextStyle(fontSize, lineHeight)
}