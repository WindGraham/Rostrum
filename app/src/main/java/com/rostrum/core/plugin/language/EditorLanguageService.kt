package com.rostrum.core.plugin.language

import com.rostrum.core.plugin.providers.CompletionContext
import com.rostrum.core.plugin.providers.CompletionItem
import com.rostrum.core.plugin.providers.DefinitionLocation
import com.rostrum.core.plugin.providers.Diagnostic

/**
 * 编辑器语言能力入口。
 */
object EditorLanguageService {

    suspend fun diagnose(filePath: String?, code: String): List<Diagnostic> {
        val plugin = LanguageSupportRegistry.getByFilePath(filePath)
            ?: return emptyList()
        return plugin.diagnose(code, filePath)
    }

    suspend fun getCompletions(
        filePath: String?,
        cursor: Int,
        prefix: String,
        contextWindow: String
    ): List<CompletionItem> {
        val plugin = LanguageSupportRegistry.getByFilePath(filePath)
            ?: return emptyList()

        return plugin.getCompletions(
            code = contextWindow,
            cursorPosition = cursor,
            context = CompletionContext(
                filePath = filePath,
                prefix = prefix,
                cursorPosition = cursor,
                contextWindow = contextWindow
            )
        )
    }

    suspend fun goToDefinition(
        filePath: String?,
        cursor: Int,
        code: String
    ): DefinitionLocation? {
        val plugin = LanguageSupportRegistry.getByFilePath(filePath)
            ?: return null
        return plugin.goToDefinition(code = code, cursorPosition = cursor, filePath = filePath)
    }

    // 兼容旧调用
    suspend fun requestCompletions(
        filePath: String?,
        code: String,
        cursorPosition: Int
    ): List<CompletionItem> {
        val prefix = extractPrefix(code, cursorPosition)
        return getCompletions(
            filePath = filePath,
            cursor = cursorPosition,
            prefix = prefix,
            contextWindow = code
        )
    }

    // 兼容旧调用
    suspend fun requestDefinition(
        filePath: String?,
        code: String,
        cursorPosition: Int
    ): Result<DefinitionLocation?> {
        return runCatching {
            goToDefinition(filePath = filePath, cursor = cursorPosition, code = code)
        }
    }

    private fun extractPrefix(code: String, cursor: Int): String {
        if (cursor <= 0 || cursor > code.length) return ""
        var start = cursor - 1
        while (start >= 0 && (code[start].isLetterOrDigit() || code[start] == '_')) {
            start--
        }
        return code.substring(start + 1, cursor)
    }
}
