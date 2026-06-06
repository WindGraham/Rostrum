package com.termux.app.settings

import android.content.Context
import android.content.SharedPreferences

object SettingsStore {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("rostrum_settings", Context.MODE_PRIVATE)
    }

    var terminalFontSize: Int
        get() = prefs.getInt("terminal_font_size", 20)
        set(v) = prefs.edit().putInt("terminal_font_size", v).apply()

    var terminalFontFamily: String
        get() = prefs.getString("terminal_font_family", "Monospace") ?: "Monospace"
        set(v) = prefs.edit().putString("terminal_font_family", v).apply()

    var cursorStyle: String
        get() = prefs.getString("cursor_style", "Block") ?: "Block"
        set(v) = prefs.edit().putString("cursor_style", v).apply()

    var backgroundColor: String
        get() = prefs.getString("background_color", "#000000") ?: "#000000"
        set(v) = prefs.edit().putString("background_color", v).apply()

    var strictHostKeyChecking: Boolean
        get() = prefs.getBoolean("strict_host_key", false)
        set(v) = prefs.edit().putBoolean("strict_host_key", v).apply()

    var defaultPort: Int
        get() = prefs.getInt("default_port", 22)
        set(v) = prefs.edit().putInt("default_port", v).apply()

    var connectionTimeout: Int
        get() = prefs.getInt("connection_timeout", 30)
        set(v) = prefs.edit().putInt("connection_timeout", v).apply()

    var maxPreviewSize: Long
        get() = prefs.getLong("max_preview_size", 10 * 1024 * 1024)
        set(v) = prefs.edit().putLong("max_preview_size", v).apply()

    var autoPreview: Boolean
        get() = prefs.getBoolean("auto_preview", true)
        set(v) = prefs.edit().putBoolean("auto_preview", v).apply()

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
            else -> "%.1f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
        }
    }
}
