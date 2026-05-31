package com.rostrum.core.plugin.language

import com.rostrum.core.plugin.providers.LanguageSupportPlugin
import java.util.concurrent.ConcurrentHashMap

/**
 * 全局语言支持注册表。
 */
object LanguageSupportRegistry {

    private val byLanguageId = ConcurrentHashMap<String, LanguageSupportPlugin>()
    private val byExtension = ConcurrentHashMap<String, LanguageSupportPlugin>()

    fun register(plugin: LanguageSupportPlugin) {
        val langKey = plugin.languageId.lowercase()
        byLanguageId[langKey] = plugin

        plugin.fileExtensions.forEach { ext ->
            val normalized = normalizeExtension(ext)
            byExtension[normalized] = plugin
        }
    }

    fun unregister(plugin: LanguageSupportPlugin) {
        byLanguageId.remove(plugin.languageId.lowercase())
        byExtension.entries.removeIf { it.value.id == plugin.id }
    }

    fun getByLanguageId(languageId: String): LanguageSupportPlugin? {
        return byLanguageId[languageId.lowercase()]
    }

    fun getByFilePath(filePath: String?): LanguageSupportPlugin? {
        if (filePath.isNullOrBlank()) return null
        val fileName = filePath.substringAfterLast('/')
        val dot = fileName.lastIndexOf('.')
        if (dot < 0 || dot == fileName.lastIndex) return null
        val ext = fileName.substring(dot + 1)
        return byExtension[normalizeExtension(ext)]
    }

    fun getAll(): List<LanguageSupportPlugin> {
        return byLanguageId.values.toList().sortedBy { it.languageName }
    }

    private fun normalizeExtension(extension: String): String {
        return extension.trim().removePrefix(".").lowercase()
    }
}
