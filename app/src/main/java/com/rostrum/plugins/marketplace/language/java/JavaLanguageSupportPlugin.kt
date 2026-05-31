package com.rostrum.plugins.marketplace.language.java

import com.rostrum.core.plugin.PluginCapability
import com.rostrum.core.plugin.PluginCategory
import com.rostrum.core.plugin.PluginContext
import com.rostrum.core.plugin.providers.CompletionContext
import com.rostrum.core.plugin.providers.CompletionItem
import com.rostrum.core.plugin.providers.CompletionKind
import com.rostrum.core.plugin.providers.DefinitionLocation
import com.rostrum.core.plugin.providers.Diagnostic
import com.rostrum.core.plugin.providers.DiagnosticSeverity
import com.rostrum.core.plugin.providers.ExecutionContext
import com.rostrum.core.plugin.providers.ExecutionResult
import com.rostrum.core.plugin.providers.FormatOptions
import com.rostrum.core.plugin.providers.InsertTextFormat
import com.rostrum.core.plugin.providers.LanguageSupportPlugin
import com.rostrum.core.plugin.providers.SyntaxTheme
import com.rostrum.core.plugin.providers.SyntaxToken
import com.rostrum.core.plugin.providers.TextRange
import com.rostrum.core.plugin.providers.TokenType
import com.rostrum.core.runtime.JavaCompileRequest
import com.rostrum.core.runtime.JavaRunRequest
import com.rostrum.core.runtime.RuntimeInitializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.dom.ASTNode
import org.eclipse.jdt.core.dom.ASTParser
import org.eclipse.jdt.core.dom.ASTVisitor
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.core.dom.EnumDeclaration
import org.eclipse.jdt.core.dom.FieldDeclaration
import org.eclipse.jdt.core.dom.MethodDeclaration
import org.eclipse.jdt.core.dom.RecordDeclaration
import org.eclipse.jdt.core.dom.TypeDeclaration
import org.eclipse.jdt.core.dom.VariableDeclarationFragment
import java.io.File

/**
 * Java 语言支持插件（增强版）。
 */
class JavaLanguageSupportPlugin : LanguageSupportPlugin {

    override val id: String = "com.rostrum.plugin.language.java"
    override val name: String = "Java Language Support"
    override val version: String = "1.1.0"
    override val author: String = "OmniMaster Team"
    override val description: String = "Java language support with diagnostics, completions and go-to-definition"
    override val category: PluginCategory = PluginCategory.LANGUAGE
    override val dependencies: List<String> = emptyList()

    override val languageId: String = "java"
    override val languageName: String = "Java"
    override val fileExtensions: List<String> = listOf("java")

    private var pluginContext: PluginContext? = null

    private val indexMutex = Mutex()
    private var indexedRoot: String? = null
    private var indexedAtMs: Long = 0
    private val symbols = mutableListOf<IndexedSymbol>()
    private val packageNames = mutableSetOf<String>()

    private val keywords = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
        "finally", "float", "for", "if", "implements", "import", "instanceof", "int", "interface",
        "long", "native", "new", "package", "private", "protected", "public", "return", "short",
        "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient",
        "try", "void", "volatile", "while", "var", "record", "sealed", "permits"
    )

    private val commonPackages = setOf(
        "java.lang",
        "java.util",
        "java.io",
        "java.net",
        "java.time",
        "android.app",
        "android.content",
        "android.os",
        "android.view",
        "android.widget",
        "androidx.core",
        "androidx.appcompat",
        "androidx.lifecycle"
    )

    private val commonTypes = listOf(
        "String", "Object", "Integer", "Long", "Boolean", "List", "Map", "Set",
        "ArrayList", "HashMap", "HashSet", "Collections", "System", "Math", "Thread",
        "Runnable", "Exception", "RuntimeException", "Activity", "Context", "Bundle", "View", "TextView"
    )

    override suspend fun initialize(context: PluginContext): Result<Unit> {
        pluginContext = context
        return Result.success(Unit)
    }

    override suspend fun onActivate(): Result<Unit> = Result.success(Unit)

    override suspend fun onDeactivate(): Result<Unit> = Result.success(Unit)

    override suspend fun onDestroy(): Result<Unit> {
        pluginContext = null
        return Result.success(Unit)
    }

    override fun getCapabilities(): List<PluginCapability> {
        return listOf(PluginCapability.LANGUAGE_SUPPORT)
    }

    override suspend fun highlightSyntax(code: String, theme: SyntaxTheme): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()

        // Strings
        Regex("\"([^\\\"]|\\\\.)*\"").findAll(code).forEach { m ->
            tokens += SyntaxToken(TokenType.STRING, m.value, m.range.first, m.range.last + 1)
        }

        // Line comments
        Regex("//.*$", setOf(RegexOption.MULTILINE)).findAll(code).forEach { m ->
            tokens += SyntaxToken(TokenType.COMMENT, m.value, m.range.first, m.range.last + 1)
        }

        // Block comments
        Regex("/\\*.*?\\*/", setOf(RegexOption.DOT_MATCHES_ALL)).findAll(code).forEach { m ->
            tokens += SyntaxToken(TokenType.COMMENT, m.value, m.range.first, m.range.last + 1)
        }

        // Keywords
        Regex("\\b([A-Za-z_][A-Za-z0-9_]*)\\b").findAll(code).forEach { m ->
            val token = m.groupValues[1]
            if (keywords.contains(token)) {
                tokens += SyntaxToken(TokenType.KEYWORD, token, m.range.first, m.range.last + 1)
            }
        }

        return tokens.sortedBy { it.start }
    }

    override suspend fun getCompletions(
        code: String,
        cursorPosition: Int,
        context: CompletionContext
    ): List<CompletionItem> = withContext(Dispatchers.Default) {
        val prefix = if (context.prefix.isNotBlank()) context.prefix else extractPrefix(code, cursorPosition)
        ensureWorkspaceIndexed(context.filePath)

        val results = linkedMapOf<String, CompletionItem>()

        fun add(item: CompletionItem) {
            results[item.label] = item
        }

        val importContext = isImportContext(code, cursorPosition)
        val memberContext = isMemberAccessContext(code, cursorPosition)

        keywords
            .asSequence()
            .filter { it.startsWith(prefix) && !importContext }
            .sorted()
            .forEach {
                add(
                    CompletionItem(
                        label = it,
                        kind = CompletionKind.KEYWORD,
                        detail = "Java keyword",
                        insertText = it,
                        insertTextFormat = InsertTextFormat.PLAIN_TEXT
                    )
                )
            }

        commonTypes
            .asSequence()
            .filter { it.startsWith(prefix, ignoreCase = false) }
            .forEach {
                add(
                    CompletionItem(
                        label = it,
                        kind = CompletionKind.CLASS,
                        detail = "Common Java type",
                        insertText = it
                    )
                )
            }

        if (importContext) {
            (packageNames + commonPackages)
                .asSequence()
                .filter { it.startsWith(prefix) || prefix.isBlank() }
                .sorted()
                .forEach {
                    add(
                        CompletionItem(
                            label = it,
                            kind = CompletionKind.MODULE,
                            detail = "Java package",
                            insertText = it
                        )
                    )
                }
        }

        val localSymbols = parseCurrentFileSymbols(code, context.filePath)
        localSymbols
            .asSequence()
            .filter { it.name.startsWith(prefix, ignoreCase = false) }
            .filter { !importContext || it.kind == CompletionKind.CLASS }
            .forEach { symbol ->
                add(
                    CompletionItem(
                        label = symbol.name,
                        kind = symbol.kind,
                        detail = symbol.detail,
                        insertText = symbol.name
                    )
                )
            }

        val indexedCandidates = symbols
            .asSequence()
            .filter { it.name.startsWith(prefix, ignoreCase = false) }
            .filter {
                when {
                    importContext -> it.kind == CompletionKind.CLASS
                    memberContext -> it.kind == CompletionKind.METHOD || it.kind == CompletionKind.FIELD
                    else -> true
                }
            }
            .sortedBy { it.name }
            .take(150)

        indexedCandidates.forEach { symbol ->
            add(
                CompletionItem(
                    label = symbol.name,
                    kind = symbol.kind,
                    detail = symbol.detail,
                    documentation = symbol.filePath,
                    insertText = symbol.name
                )
            )
        }

        results.values.take(200).toList()
    }

    override suspend fun diagnose(code: String, filePath: String?): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val effectiveFile = filePath ?: "<memory>"

        // 1) 快速结构检查
        if (!Regex("\\b(class|interface|enum|record)\\b").containsMatchIn(code)) {
            diagnostics += Diagnostic(
                file = effectiveFile,
                line = 1,
                column = 1,
                length = 1,
                severity = DiagnosticSeverity.WARNING,
                message = "No type declaration found (class/interface/enum/record)",
                code = "java.no_type_decl",
                range = textRangeFromLineColumn(code, 1, 1, 1),
                source = "java-ls"
            )
        }

        // 2) 括号平衡检查
        val unbalancedLine = findUnbalancedBraceLine(code)
        if (unbalancedLine != null) {
            diagnostics += Diagnostic(
                file = effectiveFile,
                line = unbalancedLine,
                column = 1,
                length = 1,
                severity = DiagnosticSeverity.ERROR,
                message = "Unbalanced braces detected",
                code = "java.brace_balance",
                range = textRangeFromLineColumn(code, unbalancedLine, 1, 1),
                source = "java-ls"
            )
        }

        // 3) ECJ 编译诊断
        val javaService = RuntimeInitializer.javaExecutionService
        if (javaService != null && javaService.isAvailable()) {
            val tempDir = createTempWorkspace(filePath)
            val fileName = filePath?.substringAfterLast('/')?.takeIf { it.endsWith(".java") } ?: "Main.java"
            val sourceFile = tempDir.resolve(fileName)
            sourceFile.writeText(code, Charsets.UTF_8)

            val compileResult = javaService.compile(
                JavaCompileRequest(
                    sourceRoots = listOf(tempDir.absolutePath),
                    outputDir = tempDir.resolve("classes").absolutePath
                )
            )

            diagnostics += compileResult.diagnostics.map { d ->
                val file = d.filePath.ifBlank { effectiveFile }
                Diagnostic(
                    file = file,
                    line = d.line.coerceAtLeast(1),
                    column = d.column.coerceAtLeast(1),
                    length = d.length.coerceAtLeast(1),
                    severity = when (d.severity) {
                        com.rostrum.core.runtime.JavaDiagnosticSeverity.ERROR -> DiagnosticSeverity.ERROR
                        com.rostrum.core.runtime.JavaDiagnosticSeverity.WARNING -> DiagnosticSeverity.WARNING
                        com.rostrum.core.runtime.JavaDiagnosticSeverity.INFO -> DiagnosticSeverity.INFORMATION
                    },
                    message = d.message,
                    code = d.code,
                    range = textRangeFromLineColumn(code, d.line.coerceAtLeast(1), d.column.coerceAtLeast(1), d.length.coerceAtLeast(1)),
                    source = "ecj"
                )
            }
        }

        return diagnostics
            .distinctBy { "${it.file}:${it.line}:${it.column}:${it.message}" }
            .sortedWith(compareBy<Diagnostic> { it.line }.thenBy { it.column })
    }

    override suspend fun goToDefinition(
        code: String,
        cursorPosition: Int,
        filePath: String?
    ): DefinitionLocation? {
        val token = extractTokenAtCursor(code, cursorPosition) ?: return null

        val local = parseCurrentFileSymbols(code, filePath)
            .firstOrNull { it.name == token }

        if (local != null) {
            return DefinitionLocation(
                filePath = local.filePath,
                range = local.range,
                symbolName = local.name
            )
        }

        ensureWorkspaceIndexed(filePath)

        val indexed = symbols.firstOrNull { it.name == token }
            ?: symbols.firstOrNull { it.qualifiedName?.endsWith(".$token") == true }

        return indexed?.let {
            DefinitionLocation(
                filePath = it.filePath,
                range = it.range,
                symbolName = it.name
            )
        }
    }

    override suspend fun executeCode(
        code: String,
        executionContext: ExecutionContext
    ): ExecutionResult {
        val javaService = RuntimeInitializer.javaExecutionService
            ?: return ExecutionResult(
                exitCode = -1,
                output = "",
                error = "JavaExecutionService not initialized",
                executionTime = 0
            )

        val workspace = File(executionContext.workingDirectory, ".omnimaster/java_snippets")
        workspace.mkdirs()

        val sourceFile = File(workspace, "Main.java")
        sourceFile.writeText(ensureCompilableUnit(code), Charsets.UTF_8)

        val compileResult = javaService.compile(
            JavaCompileRequest(
                sourceRoots = listOf(workspace.absolutePath),
                outputDir = File(workspace, "classes").absolutePath
            )
        )

        if (!compileResult.success) {
            return ExecutionResult(
                exitCode = 1,
                output = compileResult.output,
                error = compileResult.error.ifBlank {
                    compileResult.diagnostics.joinToString("\n") { d -> d.message }
                },
                executionTime = compileResult.executionTimeMs
            )
        }

        val runResult = javaService.run(
            JavaRunRequest(
                mainClass = "Main",
                classpath = listOf(File(workspace, "classes").absolutePath),
                timeoutMs = executionContext.timeoutSeconds * 1000,
                environment = executionContext.environment,
                workingDirectory = workspace.absolutePath
            )
        )

        return ExecutionResult(
            exitCode = runResult.exitCode,
            output = runResult.stdout,
            error = runResult.stderr,
            executionTime = runResult.executionTimeMs
        )
    }

    override suspend fun formatCode(code: String, options: FormatOptions): Result<String> {
        return Result.success(
            code.lines()
                .map { it.trimEnd() }
                .joinToString(options.lineEnding)
        )
    }

    private suspend fun ensureWorkspaceIndexed(filePath: String?) {
        val root = detectProjectRoot(filePath) ?: return
        val now = System.currentTimeMillis()

        indexMutex.withLock {
            if (indexedRoot == root.absolutePath && now - indexedAtMs < 5_000) {
                return
            }

            val javaFiles = root.walkTopDown()
                .filter { it.isFile && it.extension.equals("java", ignoreCase = true) }
                .take(400)
                .toList()

            val nextSymbols = mutableListOf<IndexedSymbol>()
            val nextPackages = mutableSetOf<String>()

            javaFiles.forEach { file ->
                val content = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return@forEach
                val parsed = parseSymbolsWithAst(content, file.absolutePath)
                nextSymbols += parsed
                parsed.mapNotNull { it.packageName }.forEach { nextPackages += it }
            }

            symbols.clear()
            symbols += nextSymbols
            packageNames.clear()
            packageNames += nextPackages
            indexedRoot = root.absolutePath
            indexedAtMs = now
        }
    }

    private fun detectProjectRoot(filePath: String?): File? {
        val candidate = filePath?.let { File(it) } ?: return null
        var dir = if (candidate.isDirectory) candidate else candidate.parentFile

        while (dir != null) {
            if (File(dir, "omni.android.json").exists()) {
                return dir
            }
            dir = dir.parentFile
        }
        return null
    }

    private fun parseCurrentFileSymbols(code: String, filePath: String?): List<IndexedSymbol> {
        return parseSymbolsWithAst(code, filePath ?: "<memory>")
    }

    private fun parseSymbolsWithAst(code: String, filePath: String): List<IndexedSymbol> {
        val parser = ASTParser.newParser(AST.getJLSLatest())
        parser.setKind(ASTParser.K_COMPILATION_UNIT)
        parser.setSource(code.toCharArray())
        parser.setResolveBindings(false)
        parser.setCompilerOptions(JavaCore.getOptions())

        val ast = runCatching { parser.createAST(null) as CompilationUnit }.getOrNull() ?: return emptyList()
        val packageName = ast.`package`?.name?.fullyQualifiedName
        val localSymbols = mutableListOf<IndexedSymbol>()
        val typeStack = ArrayDeque<String>()

        fun range(node: ASTNode): TextRange {
            val start = node.startPosition
            val end = node.startPosition + node.length
            val startLine = ast.getLineNumber(start).coerceAtLeast(1)
            val endLine = ast.getLineNumber(end).coerceAtLeast(startLine)
            val startCol = ast.getColumnNumber(start).coerceAtLeast(0) + 1
            val endCol = ast.getColumnNumber(end).coerceAtLeast(startCol) + 1
            return TextRange(
                start = start,
                end = end,
                startLine = startLine,
                endLine = endLine,
                startColumn = startCol,
                endColumn = endCol
            )
        }

        ast.accept(object : ASTVisitor() {
            override fun visit(node: TypeDeclaration): Boolean {
                val name = node.name?.identifier ?: return true
                val r = range(node.name)
                val qualified = listOfNotNull(packageName, (typeStack + name).joinToString("."))
                    .joinToString(".")
                localSymbols += IndexedSymbol(
                    name = name,
                    kind = CompletionKind.CLASS,
                    detail = "type $qualified",
                    filePath = filePath,
                    range = r,
                    qualifiedName = qualified,
                    ownerType = typeStack.lastOrNull(),
                    packageName = packageName
                )
                typeStack += name
                return true
            }

            override fun endVisit(node: TypeDeclaration) {
                if (typeStack.isNotEmpty()) typeStack.removeLast()
            }

            override fun visit(node: EnumDeclaration): Boolean {
                val name = node.name?.identifier ?: return true
                val r = range(node.name)
                val qualified = listOfNotNull(packageName, (typeStack + name).joinToString("."))
                    .joinToString(".")
                localSymbols += IndexedSymbol(
                    name = name,
                    kind = CompletionKind.CLASS,
                    detail = "enum $qualified",
                    filePath = filePath,
                    range = r,
                    qualifiedName = qualified,
                    ownerType = typeStack.lastOrNull(),
                    packageName = packageName
                )
                typeStack += name
                return true
            }

            override fun endVisit(node: EnumDeclaration) {
                if (typeStack.isNotEmpty()) typeStack.removeLast()
            }

            override fun visit(node: RecordDeclaration): Boolean {
                val name = node.name?.identifier ?: return true
                val r = range(node.name)
                val qualified = listOfNotNull(packageName, (typeStack + name).joinToString("."))
                    .joinToString(".")
                localSymbols += IndexedSymbol(
                    name = name,
                    kind = CompletionKind.CLASS,
                    detail = "record $qualified",
                    filePath = filePath,
                    range = r,
                    qualifiedName = qualified,
                    ownerType = typeStack.lastOrNull(),
                    packageName = packageName
                )
                typeStack += name
                return true
            }

            override fun endVisit(node: RecordDeclaration) {
                if (typeStack.isNotEmpty()) typeStack.removeLast()
            }

            override fun visit(node: MethodDeclaration): Boolean {
                val name = node.name?.identifier ?: return true
                val owner = typeStack.lastOrNull()
                localSymbols += IndexedSymbol(
                    name = name,
                    kind = CompletionKind.METHOD,
                    detail = "method ${owner ?: "<global>"}.$name()",
                    filePath = filePath,
                    range = range(node.name),
                    ownerType = owner,
                    packageName = packageName
                )
                return true
            }

            override fun visit(node: FieldDeclaration): Boolean {
                val owner = typeStack.lastOrNull()
                node.fragments().forEach { fragment ->
                    val variable = fragment as? VariableDeclarationFragment ?: return@forEach
                    val name = variable.name?.identifier ?: return@forEach
                    localSymbols += IndexedSymbol(
                        name = name,
                        kind = CompletionKind.FIELD,
                        detail = "field ${owner ?: "<global>"}.$name",
                        filePath = filePath,
                        range = range(variable.name),
                        ownerType = owner,
                        packageName = packageName
                    )
                }
                return true
            }

            override fun visit(node: VariableDeclarationFragment): Boolean {
                val parent = node.parent
                if (parent is FieldDeclaration) return true
                val name = node.name?.identifier ?: return true
                localSymbols += IndexedSymbol(
                    name = name,
                    kind = CompletionKind.VARIABLE,
                    detail = "variable $name",
                    filePath = filePath,
                    range = range(node.name),
                    ownerType = typeStack.lastOrNull(),
                    packageName = packageName
                )
                return true
            }
        })

        return localSymbols
    }

    private fun isImportContext(code: String, cursor: Int): Boolean {
        val safeCursor = cursor.coerceIn(0, code.length)
        val lineStart = code.lastIndexOf('\n', safeCursor - 1).let { if (it < 0) 0 else it + 1 }
        val currentLine = code.substring(lineStart, safeCursor)
        return currentLine.trimStart().startsWith("import ")
    }

    private fun isMemberAccessContext(code: String, cursor: Int): Boolean {
        if (cursor <= 0 || cursor > code.length) return false
        var idx = cursor - 1
        while (idx >= 0 && (code[idx].isLetterOrDigit() || code[idx] == '_')) {
            idx--
        }
        return idx >= 0 && code[idx] == '.'
    }

    private fun extractPrefix(code: String, cursor: Int): String {
        if (cursor <= 0 || cursor > code.length) return ""
        var start = cursor - 1
        while (start >= 0 && (code[start].isLetterOrDigit() || code[start] == '_')) {
            start--
        }
        return code.substring(start + 1, cursor)
    }

    private fun extractTokenAtCursor(code: String, cursor: Int): String? {
        if (cursor < 0 || cursor > code.length) return null

        var start = cursor
        while (start > 0 && (code[start - 1].isLetterOrDigit() || code[start - 1] == '_')) {
            start--
        }

        var end = cursor
        while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '_')) {
            end++
        }

        if (start == end) return null
        return code.substring(start, end)
    }

    private fun textRangeFromLineColumn(code: String, line: Int, column: Int, length: Int): TextRange {
        val safeLine = line.coerceAtLeast(1)
        val safeCol = column.coerceAtLeast(1)
        val safeLen = length.coerceAtLeast(1)

        var currentLine = 1
        var lineStartOffset = 0
        var idx = 0

        while (idx < code.length && currentLine < safeLine) {
            if (code[idx] == '\n') {
                currentLine++
                lineStartOffset = idx + 1
            }
            idx++
        }

        val start = (lineStartOffset + safeCol - 1).coerceIn(0, code.length)
        val end = (start + safeLen).coerceIn(start, code.length)

        return TextRange(
            start = start,
            end = end,
            startLine = safeLine,
            endLine = safeLine,
            startColumn = safeCol,
            endColumn = safeCol + safeLen
        )
    }

    private fun findUnbalancedBraceLine(code: String): Int? {
        var balance = 0
        var line = 1
        code.forEach { c ->
            when (c) {
                '\n' -> line++
                '{' -> balance++
                '}' -> {
                    balance--
                    if (balance < 0) return line
                }
            }
        }
        return if (balance == 0) null else line
    }

    private fun ensureCompilableUnit(code: String): String {
        return if (Regex("\\bclass\\s+[A-Za-z_][A-Za-z0-9_]*").containsMatchIn(code)) {
            code
        } else {
            """
            public class Main {
                public static void main(String[] args) {
                    $code
                }
            }
            """.trimIndent()
        }
    }

    private fun createTempWorkspace(filePath: String?): File {
        val tmpRoot = System.getProperty("java.io.tmpdir") ?: "/data/local/tmp"
        val base = pluginContext?.appContext?.cacheDir ?: File(tmpRoot)
        val key = filePath?.hashCode()?.toString() ?: "adhoc"
        val dir = File(base, "java_ls/$key")
        dir.mkdirs()
        return dir
    }

    private data class IndexedSymbol(
        val name: String,
        val kind: CompletionKind,
        val detail: String,
        val filePath: String,
        val range: TextRange,
        val qualifiedName: String? = null,
        val ownerType: String? = null,
        val packageName: String? = null
    )
}
