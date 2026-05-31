package com.rostrum.core.plugin.providers

import com.rostrum.core.plugin.Plugin

/**
 * 语言支持插件接口
 * 
 * 实现此接口的插件可以提供编程语言支持功能
 */
interface LanguageSupportPlugin : Plugin {
    /**
     * 语言标识符（如 "python", "javascript"）
     */
    val languageId: String
    
    /**
     * 语言显示名称（如 "Python", "JavaScript"）
     */
    val languageName: String
    
    /**
     * 支持的文件扩展名列表
     */
    val fileExtensions: List<String>
    
    /**
     * 语法高亮
     * 
     * @param code 源代码
     * @param theme 语法主题
     * @return 语法标记列表
     */
    suspend fun highlightSyntax(
        code: String,
        theme: SyntaxTheme = SyntaxTheme.DEFAULT
    ): List<SyntaxToken>
    
    /**
     * 代码补全
     * 
     * @param code 源代码
     * @param cursorPosition 光标位置
     * @param context 补全上下文
     * @return 补全项列表
     */
    suspend fun getCompletions(
        code: String,
        cursorPosition: Int,
        context: CompletionContext
    ): List<CompletionItem>
    
    /**
     * 代码诊断
     * 
     * @param code 源代码
     * @param filePath 文件路径（可选）
     * @return 诊断信息列表
     */
    suspend fun diagnose(
        code: String,
        filePath: String? = null
    ): List<Diagnostic>

    /**
     * 跳转定义（可选能力）。
     * 默认返回 null，表示当前语言插件未实现该能力。
     */
    suspend fun goToDefinition(
        code: String,
        cursorPosition: Int,
        filePath: String? = null
    ): DefinitionLocation? = null
    
    /**
     * 执行代码
     * 
     * @param code 源代码
     * @param executionContext 执行上下文
     * @return 执行结果
     */
    suspend fun executeCode(
        code: String,
        executionContext: ExecutionContext
    ): ExecutionResult
    
    /**
     * 格式化代码
     * 
     * @param code 源代码
     * @param options 格式化选项
     * @return 格式化后的代码
     */
    suspend fun formatCode(
        code: String,
        options: FormatOptions = FormatOptions()
    ): Result<String>
}

/**
 * 语法主题
 */
enum class SyntaxTheme {
    DEFAULT,
    DARK,
    LIGHT,
    MONOKAI,
    SOLARIZED
}

/**
 * 语法标记
 */
data class SyntaxToken(
    val type: TokenType,
    val text: String,
    val start: Int,
    val end: Int
)

/**
 * 标记类型
 */
enum class TokenType {
    KEYWORD,
    STRING,
    COMMENT,
    NUMBER,
    OPERATOR,
    IDENTIFIER,
    FUNCTION,
    CLASS,
    VARIABLE
}

/**
 * 补全上下文
 */
data class CompletionContext(
    val filePath: String? = null,
    val imports: List<String> = emptyList(),
    val variables: List<String> = emptyList(),
    val functions: List<String> = emptyList(),
    val prefix: String = "",
    val cursorPosition: Int = 0,
    val contextWindow: String = ""
)

/**
 * 补全项
 */
data class CompletionItem(
    val label: String,
    val kind: CompletionKind,
    val detail: String? = null,
    val documentation: String? = null,
    val insertText: String? = null,
    val insertTextFormat: InsertTextFormat = InsertTextFormat.PLAIN_TEXT
)

/**
 * 补全类型
 */
enum class CompletionKind {
    TEXT,
    METHOD,
    FUNCTION,
    CONSTRUCTOR,
    FIELD,
    VARIABLE,
    CLASS,
    INTERFACE,
    MODULE,
    PROPERTY,
    UNIT,
    VALUE,
    KEYWORD,
    SNIPPET
}

/**
 * 插入文本格式
 */
enum class InsertTextFormat {
    PLAIN_TEXT,
    SNIPPET
}

/**
 * 诊断信息
 */
data class Diagnostic(
    val file: String,
    val line: Int,
    val column: Int,
    val length: Int,
    val severity: DiagnosticSeverity,
    val message: String,
    val code: String,
    val range: TextRange,
    val source: String? = null
)

/**
 * 诊断严重程度
 */
enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFORMATION,
    HINT
}

/**
 * 文本范围
 */
data class TextRange(
    val start: Int,
    val end: Int,
    val startLine: Int,
    val endLine: Int,
    val startColumn: Int,
    val endColumn: Int
)

data class DefinitionLocation(
    val filePath: String,
    val range: TextRange,
    val symbolName: String? = null
)

/**
 * 执行上下文
 */
data class ExecutionContext(
    val workingDirectory: String,
    val environment: Map<String, String> = emptyMap(),
    val input: String? = null,
    val timeoutSeconds: Long = 30
)

/**
 * 执行结果
 */
data class ExecutionResult(
    val exitCode: Int,
    val output: String,
    val error: String,
    val executionTime: Long
)

/**
 * 格式化选项
 */
data class FormatOptions(
    val indentSize: Int = 4,
    val useTabs: Boolean = false,
    val lineEnding: String = "\n",
    val maxLineLength: Int = 120
)
