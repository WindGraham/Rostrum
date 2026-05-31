package com.rostrum.core.runtime.impl

import android.content.Context
import android.util.Log
import com.rostrum.core.runtime.JavaCompileRequest
import com.rostrum.core.runtime.JavaCompileResult
import com.rostrum.core.runtime.JavaDiagnostic
import com.rostrum.core.runtime.JavaDiagnosticSeverity
import com.rostrum.core.runtime.JavaExecutionService
import com.rostrum.core.runtime.JavaRunRequest
import com.rostrum.core.runtime.JavaRunResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Java 执行服务实现（V1）。
 *
 * 优先使用应用内 ECJ（通过反射调用），避免硬依赖外部 javac。
 */
class JavaExecutionServiceImpl(
    private val context: Context,
    private val toolchainManager: ToolchainManager
) : JavaExecutionService {

    companion object {
        private const val TAG = "JavaExecutionService"
        private const val ECJ_BATCH_COMPILER = "org.eclipse.jdt.core.compiler.batch.BatchCompiler"
    }

    @Volatile
    private var initialized = false

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        val toolchainReady = toolchainManager.initialize()

        // 允许“仅编辑诊断模式”初始化成功；真正编译时再判断 ECJ 可用性。
        initialized = toolchainReady || isEcjClassPresent()
        initialized
    }

    override fun isAvailable(): Boolean = initialized

    override suspend fun compile(request: JavaCompileRequest): JavaCompileResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()

        val sourceFiles = collectJavaFiles(request.sourceRoots)
        if (sourceFiles.isEmpty()) {
            return@withContext JavaCompileResult(
                success = false,
                diagnostics = listOf(
                    JavaDiagnostic(
                        severity = JavaDiagnosticSeverity.ERROR,
                        message = "No Java sources found in sourceRoots",
                        filePath = "",
                        line = 1,
                        column = 1,
                        length = 1,
                        code = "no_sources"
                    )
                ),
                error = "No Java sources found in sourceRoots",
                executionTimeMs = System.currentTimeMillis() - start
            )
        }

        val outputDir = File(request.outputDir)
        if (!outputDir.exists()) outputDir.mkdirs()

        if (!isEcjClassPresent()) {
            return@withContext JavaCompileResult(
                success = false,
                diagnostics = listOf(
                    JavaDiagnostic(
                        severity = JavaDiagnosticSeverity.ERROR,
                        message = "ECJ compiler not found. Add org.eclipse.jdt.core compiler to runtime.",
                        filePath = sourceFiles.firstOrNull()?.absolutePath.orEmpty(),
                        line = 1,
                        column = 1,
                        length = 1,
                        code = "ecj_missing"
                    )
                ),
                error = "ECJ compiler not found. Add org.eclipse.jdt.core compiler to runtime.",
                executionTimeMs = System.currentTimeMillis() - start,
                compiledFileCount = sourceFiles.size
            )
        }

        val outWriter = StringWriter()
        val errWriter = StringWriter()

        val success = runCatching {
            compileWithEcj(
                request = request,
                sources = sourceFiles,
                out = PrintWriter(outWriter),
                err = PrintWriter(errWriter)
            )
        }.onFailure {
            Log.e(TAG, "ECJ compile failed", it)
        }.getOrDefault(false)

        // Ensure buffered compiler output is materialized before parsing.
        runCatching {
            outWriter.flush()
            errWriter.flush()
        }

        val outText = outWriter.toString().trim()
        val errText = errWriter.toString().trim()
        val parsedDiagnostics = parseDiagnostics(
            stdout = outText,
            stderr = errText,
            fallbackFile = sourceFiles.firstOrNull()?.absolutePath.orEmpty()
        )
        val diagnostics = if (!success && parsedDiagnostics.isEmpty()) {
            listOf(
                JavaDiagnostic(
                    severity = JavaDiagnosticSeverity.ERROR,
                    message = "ECJ compile failed with empty diagnostics output",
                    filePath = sourceFiles.firstOrNull()?.absolutePath.orEmpty(),
                    line = 1,
                    column = 1,
                    length = 1,
                    code = "ecj_unknown_failure"
                )
            )
        } else {
            parsedDiagnostics
        }

        JavaCompileResult(
            success = success,
            diagnostics = diagnostics,
            output = outText,
            error = errText,
            executionTimeMs = System.currentTimeMillis() - start,
            compiledFileCount = sourceFiles.size
        )
    }

    override suspend fun run(request: JavaRunRequest): JavaRunResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()

        val javaBinary = toolchainManager.getBinaryPath(ToolchainManager.ToolchainBinary.JAVA)
            ?: File("/system/bin/java").takeIf { it.exists() }

        if (javaBinary == null) {
            return@withContext JavaRunResult(
                success = false,
                stderr = "Java runtime binary not found in toolchain",
                executionTimeMs = System.currentTimeMillis() - start
            )
        }

        val command = mutableListOf(
            javaBinary.absolutePath,
            "-cp",
            request.classpath.joinToString(File.pathSeparator),
            request.mainClass
        )
        command.addAll(request.args)

        val process = ProcessBuilder(command).apply {
            request.workingDirectory?.let { directory(File(it)) }
            environment().putAll(request.environment)
            redirectErrorStream(false)
        }.start()

        try {
            val result = withTimeoutOrNull(request.timeoutMs) {
                val stdoutDeferred = async(Dispatchers.IO) {
                    process.inputStream.bufferedReader().use { it.readText() }
                }
                val stderrDeferred = async(Dispatchers.IO) {
                    process.errorStream.bufferedReader().use { it.readText() }
                }

                val exitCode = process.waitFor()
                JavaRunResult(
                    success = exitCode == 0,
                    stdout = stdoutDeferred.await().trim(),
                    stderr = stderrDeferred.await().trim(),
                    exitCode = exitCode,
                    executionTimeMs = System.currentTimeMillis() - start,
                    timedOut = false
                )
            }

            if (result != null) {
                result
            } else {
                process.destroyForcibly()
                JavaRunResult(
                    success = false,
                    stderr = "Java process timed out",
                    executionTimeMs = System.currentTimeMillis() - start,
                    timedOut = true
                )
            }
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }

    private fun collectJavaFiles(sourceRoots: List<String>): List<File> {
        return sourceRoots
            .map { File(it) }
            .filter { it.exists() }
            .flatMap { root ->
                if (root.isFile && root.extension.equals("java", ignoreCase = true)) {
                    listOf(root)
                } else {
                    root.walkTopDown()
                        .filter { it.isFile && it.extension.equals("java", ignoreCase = true) }
                        .toList()
                }
            }
    }

    private fun compileWithEcj(
        request: JavaCompileRequest,
        sources: List<File>,
        out: PrintWriter,
        err: PrintWriter
    ): Boolean {
        val fullClasspath = request.classpath.filter { it.isNotBlank() }
        val cp = fullClasspath.joinToString(File.pathSeparator)
        val androidBootClasspath = fullClasspath.firstOrNull { entry ->
            runCatching { File(entry).name.equals("android.jar", ignoreCase = true) }.getOrDefault(false)
        }

        val args = mutableListOf<String>()
        args += listOf(
            "-source", request.sourceLevel,
            "-target", request.targetLevel,
            "-d", request.outputDir,
            "-proceedOnError",
            "-proc:none",
            "-encoding", "UTF-8"
        )

        if (!androidBootClasspath.isNullOrBlank()) {
            // Force ECJ to compile against Android platform APIs instead of host VM libraries.
            args += listOf("-bootclasspath", androidBootClasspath)
        }

        if (cp.isNotBlank()) {
            args += listOf("-classpath", cp)
        }
        args += sources.map { it.absolutePath }

        val batchCompiler = Class.forName(ECJ_BATCH_COMPILER)

        // Prefer compile(String[]...) to avoid reflection ambiguity with compile(String...).
        val stringArrayCompile = batchCompiler.methods.firstOrNull { method ->
            method.name == "compile" &&
                method.parameterCount == 4 &&
                method.parameterTypes[0].isArray &&
                method.parameterTypes[0].componentType == String::class.java
        }

        val result = if (stringArrayCompile != null) {
            stringArrayCompile.invoke(null, args.toTypedArray(), out, err, null)
        } else {
            val commandLineCompile = batchCompiler.methods.firstOrNull { method ->
                method.name == "compile" &&
                    method.parameterCount == 4 &&
                    method.parameterTypes[0] == String::class.java
            } ?: error("BatchCompiler.compile(String[]/String) method not found")

            val commandLine = args.joinToString(" ") { shellEscape(it) }
            commandLineCompile.invoke(null, commandLine, out, err, null)
        }

        return result == true
    }

    private fun parseDiagnostics(stdout: String, stderr: String, fallbackFile: String): List<JavaDiagnostic> {
        val merged = listOf(stdout, stderr)
            .filter { it.isNotBlank() }
            .joinToString("\n")

        if (merged.isBlank()) return emptyList()

        val diagnostics = mutableListOf<JavaDiagnostic>()

        // 1) ECJ block format:
        // 1. ERROR in /path/Main.java (at line 8)
        //      xxx
        //      ^^^
        // message
        val blockRegex = Regex(
            """\d+\.\s+(ERROR|WARNING) in (.+?) \(at line (\d+)\)\s*\n(.*?)\n([ \t]*)(\^+)\n(.*?)(?=\n-{2,}|\z)""",
            setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
        )

        blockRegex.findAll(merged).forEach { match ->
            val severityRaw = match.groupValues[1]
            val file = match.groupValues[2].trim()
            val line = match.groupValues[3].toIntOrNull() ?: 1
            val spaces = match.groupValues[5]
            val carets = match.groupValues[6]
            val message = match.groupValues[7].trim().ifBlank { "Compilation error" }

            diagnostics += JavaDiagnostic(
                severity = if (severityRaw.equals("ERROR", ignoreCase = true)) {
                    JavaDiagnosticSeverity.ERROR
                } else {
                    JavaDiagnosticSeverity.WARNING
                },
                message = message,
                filePath = file.ifBlank { fallbackFile },
                line = line,
                column = spaces.length + 1,
                length = carets.length.coerceAtLeast(1),
                code = "ecj"
            )
        }

        // 2) Generic file:line:col: error format
        val fileLineColRegex = Regex("^(.+?):(\\d+):(\\d+):\\s*(error|warning):\\s*(.+)$", RegexOption.IGNORE_CASE)
        val fileLineRegex = Regex("^(.+?):(\\d+):\\s*(error|warning):\\s*(.+)$", RegexOption.IGNORE_CASE)

        merged.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val lineCol = fileLineColRegex.find(line)
                if (lineCol != null) {
                    diagnostics += JavaDiagnostic(
                        severity = if (lineCol.groupValues[4].equals("error", ignoreCase = true)) {
                            JavaDiagnosticSeverity.ERROR
                        } else {
                            JavaDiagnosticSeverity.WARNING
                        },
                        message = lineCol.groupValues[5].trim(),
                        filePath = lineCol.groupValues[1],
                        line = lineCol.groupValues[2].toIntOrNull() ?: 1,
                        column = lineCol.groupValues[3].toIntOrNull() ?: 1,
                        length = 1,
                        code = "compiler"
                    )
                    return@forEach
                }

                val lineOnly = fileLineRegex.find(line)
                if (lineOnly != null) {
                    diagnostics += JavaDiagnostic(
                        severity = if (lineOnly.groupValues[3].equals("error", ignoreCase = true)) {
                            JavaDiagnosticSeverity.ERROR
                        } else {
                            JavaDiagnosticSeverity.WARNING
                        },
                        message = lineOnly.groupValues[4].trim(),
                        filePath = lineOnly.groupValues[1],
                        line = lineOnly.groupValues[2].toIntOrNull() ?: 1,
                        column = 1,
                        length = 1,
                        code = "compiler"
                    )
                }
            }

        if (diagnostics.isEmpty()) {
            diagnostics += merged.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(20)
                .map { line ->
                    JavaDiagnostic(
                        severity = when {
                            line.contains("error", ignoreCase = true) -> JavaDiagnosticSeverity.ERROR
                            line.contains("warning", ignoreCase = true) -> JavaDiagnosticSeverity.WARNING
                            else -> JavaDiagnosticSeverity.INFO
                        },
                        message = line,
                        filePath = fallbackFile,
                        line = 1,
                        column = 1,
                        length = 1,
                        code = "generic"
                    )
                }
                .toList()
        }

        return diagnostics
            .distinctBy { "${it.filePath}:${it.line}:${it.column}:${it.message}" }
            .sortedWith(compareBy<JavaDiagnostic> { it.filePath }.thenBy { it.line }.thenBy { it.column })
    }

    private fun isEcjClassPresent(): Boolean {
        return runCatching { Class.forName(ECJ_BATCH_COMPILER) }.isSuccess
    }

    private fun shellEscape(input: String): String {
        return if (input.any { it.isWhitespace() }) {
            "\"${input.replace("\"", "\\\"")}\""
        } else {
            input
        }
    }
}
