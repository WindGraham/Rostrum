package com.rostrum.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.abs

private val Context.themeDataStore by preferencesDataStore(name = "theme_preferences")

/**
 * 主题风格类型
 */
enum class ThemeStyle(val displayName: String) {
    MODERN("现代风格"),
    ACADEMIC("学术风格")
}

/**
 * 主题管理器 - 支持动态主题配色和主题风格
 * 用户可以通过滑动条选择主色调，或切换到学术风格主题
 */
object ThemeManager {
    
    // DataStore keys
    private val PRIMARY_HUE_KEY = floatPreferencesKey("primary_hue")
    private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    private val THEME_STYLE_KEY = stringPreferencesKey("theme_style")
    
    // 主色调 Hue (0-360)，默认紫色 (270)
    var primaryHue by mutableFloatStateOf(270f)
        private set
    
    // 主题模式: auto, light, dark
    var themeMode by mutableStateOf("auto")
        private set
    
    // 主题风格
    var themeStyle by mutableStateOf(ThemeStyle.MODERN)
        private set
    
    // 预设配色方案
    enum class PresetTheme(val hue: Float, val displayName: String) {
        PURPLE(270f, "紫罗兰"),
        BLUE(220f, "天空蓝"),
        CYAN(180f, "青碧"),
        GREEN(140f, "翠绿"),
        LIME(80f, "柠檬黄"),
        ORANGE(30f, "暖橙"),
        RED(0f, "热情红"),
        PINK(330f, "浪漫粉"),
        TEAL(165f, "蓝绿")
    }
    
    /**
     * 初始化主题管理器，从 DataStore 加载配置
     */
    suspend fun initialize(context: Context) {
        context.themeDataStore.data.first().let { prefs ->
            primaryHue = prefs[PRIMARY_HUE_KEY] ?: 270f
            themeMode = prefs[THEME_MODE_KEY] ?: "auto"
            themeStyle = prefs[THEME_STYLE_KEY]?.let { 
                try {
                    ThemeStyle.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    ThemeStyle.MODERN
                }
            } ?: ThemeStyle.MODERN
        }
    }
    
    /**
     * 设置主色调
     */
    suspend fun setPrimaryHue(context: Context, hue: Float) {
        primaryHue = hue.coerceIn(0f, 360f)
        context.themeDataStore.edit { prefs ->
            prefs[PRIMARY_HUE_KEY] = primaryHue
        }
    }
    
    /**
     * 设置主题模式
     */
    suspend fun setThemeMode(context: Context, mode: String) {
        themeMode = mode
        context.themeDataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode
        }
    }
    
    /**
     * 设置主题风格
     */
    suspend fun setThemeStyle(context: Context, style: ThemeStyle) {
        themeStyle = style
        context.themeDataStore.edit { prefs ->
            prefs[THEME_STYLE_KEY] = style.name
        }
    }
    
    /**
     * 应用预设主题
     */
    suspend fun applyPreset(context: Context, preset: PresetTheme) {
        setPrimaryHue(context, preset.hue)
    }
    
    /**
     * 根据主色调生成完整的深色配色方案
     */
    fun generateDarkColorScheme(hue: Float = primaryHue): ColorScheme {
        val colors = generateHarmoniousColors(hue)
        
        return darkColorScheme(
            primary = colors.primary.copy(alpha = 1f).adjustForDark(0.8f),
            onPrimary = Color.White,
            primaryContainer = colors.primary.adjustForDark(0.3f),
            onPrimaryContainer = colors.primary.adjustForDark(0.9f),
            
            secondary = colors.secondary.adjustForDark(0.7f),
            onSecondary = Color.White,
            secondaryContainer = colors.secondary.adjustForDark(0.25f),
            onSecondaryContainer = colors.secondary.adjustForDark(0.85f),
            
            tertiary = colors.tertiary.adjustForDark(0.7f),
            onTertiary = Color.White,
            tertiaryContainer = colors.tertiary.adjustForDark(0.25f),
            onTertiaryContainer = colors.tertiary.adjustForDark(0.85f),
            
            background = Color(0xFF121212),
            onBackground = Color(0xFFE6E6E6),
            
            surface = Color(0xFF1E1E1E),
            onSurface = Color(0xFFE6E6E6),
            surfaceVariant = Color(0xFF2D2D2D),
            onSurfaceVariant = Color(0xFFCACACA),
            
            error = Color(0xFFCF6679),
            onError = Color.Black,
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            
            outline = Color(0xFF938F99),
            outlineVariant = Color(0xFF49454F),
            
            inverseSurface = Color(0xFFE6E6E6),
            inverseOnSurface = Color(0xFF1E1E1E),
            inversePrimary = colors.primary.adjustForDark(0.4f)
        )
    }
    
    /**
     * 根据主色调生成完整的浅色配色方案
     */
    fun generateLightColorScheme(hue: Float = primaryHue): ColorScheme {
        val colors = generateHarmoniousColors(hue)
        
        return lightColorScheme(
            primary = colors.primary.adjustForLight(0.4f),
            onPrimary = Color.White,
            primaryContainer = colors.primary.adjustForLight(0.9f),
            onPrimaryContainer = colors.primary.adjustForLight(0.1f),
            
            secondary = colors.secondary.adjustForLight(0.45f),
            onSecondary = Color.White,
            secondaryContainer = colors.secondary.adjustForLight(0.9f),
            onSecondaryContainer = colors.secondary.adjustForLight(0.1f),
            
            tertiary = colors.tertiary.adjustForLight(0.45f),
            onTertiary = Color.White,
            tertiaryContainer = colors.tertiary.adjustForLight(0.9f),
            onTertiaryContainer = colors.tertiary.adjustForLight(0.1f),
            
            background = Color(0xFFFFFBFE),
            onBackground = Color(0xFF1C1B1F),
            
            surface = Color(0xFFFFFBFE),
            onSurface = Color(0xFF1C1B1F),
            surfaceVariant = Color(0xFFE7E0EC),
            onSurfaceVariant = Color(0xFF49454F),
            
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            
            outline = Color(0xFF79747E),
            outlineVariant = Color(0xFFCAC4D0),
            
            inverseSurface = Color(0xFF313033),
            inverseOnSurface = Color(0xFFF4EFF4),
            inversePrimary = colors.primary.adjustForLight(0.8f)
        )
    }
    
    /**
     * 生成和谐色（基于色轮理论）
     */
    fun generateHarmoniousColors(hue: Float): HarmoniousColors {
        return HarmoniousColors(
            primary = hslToColor(hue, 0.7f, 0.5f),
            secondary = hslToColor((hue + 30f) % 360f, 0.5f, 0.5f),  // 类似色
            tertiary = hslToColor((hue + 60f) % 360f, 0.5f, 0.5f),   // 类似色
            complementary = hslToColor((hue + 180f) % 360f, 0.6f, 0.5f), // 互补色
            analogous1 = hslToColor((hue - 30f + 360f) % 360f, 0.6f, 0.5f),
            analogous2 = hslToColor((hue + 30f) % 360f, 0.6f, 0.5f),
            triadic1 = hslToColor((hue + 120f) % 360f, 0.5f, 0.5f),
            triadic2 = hslToColor((hue + 240f) % 360f, 0.5f, 0.5f)
        )
    }
    
    /**
     * HSL 转 Color
     * @param h Hue (0-360)
     * @param s Saturation (0-1)
     * @param l Lightness (0-1)
     */
    private fun hslToColor(h: Float, s: Float, l: Float): Color {
        val c = (1 - abs(2 * l - 1)) * s
        val x = c * (1 - abs((h / 60) % 2 - 1))
        val m = l - c / 2
        
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        
        return Color(
            red = (r + m).coerceIn(0f, 1f),
            green = (g + m).coerceIn(0f, 1f),
            blue = (b + m).coerceIn(0f, 1f)
        )
    }
    
    /**
     * 调整颜色亮度（用于深色主题）
     */
    private fun Color.adjustForDark(targetLightness: Float): Color {
        val (h, s, l) = colorToHsl(this)
        return hslToColor(h, s * 0.9f, targetLightness)
    }
    
    /**
     * 调整颜色亮度（用于浅色主题）
     */
    private fun Color.adjustForLight(targetLightness: Float): Color {
        val (h, s, l) = colorToHsl(this)
        return hslToColor(h, s * 0.85f, targetLightness)
    }
    
    /**
     * Color 转 HSL
     */
    private fun colorToHsl(color: Color): Triple<Float, Float, Float> {
        val r = color.red
        val g = color.green
        val b = color.blue
        
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2
        
        val (h, s) = if (max == min) {
            0f to 0f
        } else {
            val d = max - min
            val s = if (l > 0.5f) d / (2 - max - min) else d / (max + min)
            val h = when (max) {
                r -> ((g - b) / d + (if (g < b) 6 else 0)) * 60
                g -> ((b - r) / d + 2) * 60
                else -> ((r - g) / d + 4) * 60
            }
            h to s
        }
        
        return Triple(h, s, l)
    }
}

/**
 * 和谐色数据类
 */
data class HarmoniousColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val complementary: Color,
    val analogous1: Color,
    val analogous2: Color,
    val triadic1: Color,
    val triadic2: Color
)
