package com.rostrum.core.runtime.impl

import android.util.Log
import com.rostrum.core.runtime.KotlinCompileRequest
import com.rostrum.core.runtime.KotlinCompileResult
import com.rostrum.core.runtime.KotlinDiagnostic
import com.rostrum.core.runtime.KotlinDiagnosticSeverity
import com.rostrum.core.runtime.KotlinExecutionService
import com.rostrum.core.runtime.KotlinRunRequest
import com.rostrum.core.runtime.KotlinRunResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Kotlin 执行服务实现（V1）。
 *
 * 通过工具链中的 kotlinc/kotlinc.jar 离线编译 Kotlin 源码。
 */
class KotlinExecutionServiceImpl(
    private val toolchainManager: ToolchainManager
) : KotlinExecutionService {

    companion object {
        private const val TAG = "KotlinExecutionService"
    }

    @Volatile
    private var initialized = false

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        val toolchainReady = toolchainManager.initialize()
        val hasKotlinc = toolchainManager.getBinaryPath(ToolchainManager.ToolchainBinary.KOTLINC) != null
        initialized = toolchainReady && hasKotlinc
        initialized
    }

    override fun isAvailable(): Boolean = initialized

    override suspend fun compile(request: KotlinCompileRequest): KotlinCompileResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()

        val sourceFiles = collectKotlinFiles(request.sourceRoots)
        if (sourceFiles.isEmpty()) {
            return@withContext KotlinCompileResult(
                success = true,
                executionTimeMs = System.currentTimeMillis() - startedAt,
                compiledFileCount = 0
            )
        }

        val outputDir = File(request.outputDir).apply { mkdirs() }
        val kotlinc = toolchainManager.getBinaryPath(ToolchainManager.ToolchainBinary.KOTLINC)
        if (kotlinc == null) {
            return@withContext KotlinCompileResult(
                success = false,
                diagnostics = listOf(
                    KotlinDiagnostic(
                        severity = KotlinDiagnosticSeverity.ERROR,
                        message = "kotlinc not found in packaged toolchain",
                        filePath = sourceFiles.first().absolutePath,
                        line = 1,
                        column = 1,
                        length = 1,
                        code = "kotlinc_missing"
                    )
                ),
                error = "kotlinc not found in packaged toolchain",
                executionTimeMs = System.currentTimeMillis() - startedAt,
                compiledFileCount = sourceFiles.size
            )
        }

        val command = mutableListOf<String>()
        val launcher = buildCompilerLauncher(kotlinc)
        if (launcher == null) {
            return@withContext KotlinCompileResult(
                success = false,
                diagnostics = listOf(
                    KotlinDiagnostic(
                        severity = KotlinDiagnosticSeverity.ERROR,
                        message = "kotlinc.jar requires java runtime in packaged toolchain",
                        filePath = sourceFiles.first().absolutePath,
                        line = 1,
                        column = 1,
                        length = 1,
                        code = "java_runtime_missing"
                    )
                ),
                error = "kotlinc.jar requires java runtime in packaged toolchain",
                executionTimeMs = System.currentTimeMillis() - startedAt,
                compiledFileCount = sourceFiles.size
            )
        }
        command += launcher
        command += listOf(
            "-jvm-target", request.jvmTarget,
            "-d", outputDir.absolutePath,
            "-no-stdlib",
            "-no-reflect"
        )
        if (request.classpath.isNotEmpty()) {
            command += listOf("-classpath", request.classpath.joinToString(File.pathSeparator))
        }
        command += sourceFiles.map { it.absolutePath }

        val result = runCommand(command, workingDir = outputDir.parentFile ?: outputDir)
        val diagnostics = parseDiagnostics(result.stdout, result.stderr)

        KotlinCompileResult(
            success = result.success,
            diagnostics = diagnostics,
            output = result.stdout,
            error = result.stderr,
            executionTimeMs = System.currentTimeMillis() - startedAt,
            compiledFileCount = sourceFiles.size
        )
    }

    override suspend fun run(request: KotlinRunRequest): KotlinRunResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()

        val javaBinary = toolchainManager.getBinaryPath(ToolchainManager.ToolchainBinary.JAVA)
            ?: File("/system/bin/java").takeIf { it.exists() }
        if (javaBinary == null) {
            return@withContext KotlinRunResult(
                success = false,
                stderr = "Java runtime binary not found for Kotlin execution",
                executionTimeMs = System.currentTimeMillis() - startedAt
            )
        }

        val command = mutableListOf(
            javaBinary.absolutePath,
            "-cp", request.classpath.joinToString(File.pathSeparator),
            request.mainClass
        )
        command += request.args

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
                KotlinRunResult(
                    success = exitCode == 0,
                    stdout = stdoutDeferred.await().trim(),
                    stderr = stderrDeferred.await().trim(),
                    exitCode = exitCode,
                    executionTimeMs = System.currentTimeMillis() - startedAt
                )
            }

            if (result != null) {
                result
            } else {
                process.destroyForcibly()
                KotlinRunResult(
                    success = false,
                    stderr = "Kotlin process timed out",
                    executionTimeMs = System.currentTimeMillis() - startedAt,
                    timedOut = true
                )
            }
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }

    private fun collectKotlinFiles(sourceRoots: List<String>): List<File> {
        return sourceRoots
            .map { File(it) }
            .filter { it.exists() }
            .flatMap { root ->
                if (root.isFile && root.extension.equals("kt", ignoreCase = true)) {
                    listOf(root)
                } else {
                    root.walkTopDown()
                        .filter { it.isFile && it.extension.equals("kt", ignoreCase = true) }
                        .toList()
                }
            }
    }

    private data class CommandResult(
        val success: Boolean,
        val stdout: String,
        val stderr: String
    )

    private suspend fun runCommand(command: List<String>, workingDir: File): CommandResult = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            val process = ProcessBuilder(command)
                .directory(workingDir)
                .redirectErrorStream(false)
                .start()

            try {
                val triple = withTimeoutOrNull(180_000L) {
                    val outDeferred = async(Dispatchers.IO) {
                        process.inputStream.bufferedReader().use { it.readText() }
                    }
                    val errDeferred = async(Dispatchers.IO) {
                        process.errorStream.bufferedReader().use { it.readText() }
                    }
                    val code = process.waitFor()
                    Triple(code, outDeferred.await().trim(), errDeferred.await().trim())
                }

                if (triple == null) {
                    process.destroyForcibly()
                    CommandResult(success = false, stdout = "", stderr = "Timeout (180000ms)")
                } else {
                    CommandResult(
                        success = triple.first == 0,
                        stdout = triple.second,
                        stderr = triple.third
                    )
                }
            } finally {
                if (process.isAlive) {
                    process.destroyForcibly()
                }
            }
        }.onFailure {
            Log.e(TAG, "Failed to run command: ${command.firstOrNull()}", it)
        }.getOrElse {
            CommandResult(success = false, stdout = "", stderr = it.message ?: "Unknown error")
        }
    }

    private fun parseDiagnostics(stdout: String, stderr: String): List<KotlinDiagnostic> {
        val merged = buildString {
            if (stdout.isNotBlank()) appendLine(stdout)
            if (stderr.isNotBlank()) appendLine(stderr)
        }.trim()

        if (merged.isBlank()) return emptyList()

        val fileLineColRegex = Regex("^(.+?):(\\d+):(\\d+):\\s*(error|warning):\\s*(.+)$", RegexOption.IGNORE_CASE)
        val kotlincRegex = Regex("^[ew]:\\s+(.+?):\\s*\\((\\d+),\\s*(\\d+)\\):\\s*(.+)$", RegexOption.IGNORE_CASE)

        val diagnostics = mutableListOf<KotlinDiagnostic>()
        merged.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val m1 = fileLineColRegex.find(line)
                if (m1 != null) {
                    val severity = if (m1.groupValues[4].equals("warning", ignoreCase = true)) {
                        KotlinDiagnosticSeverity.WARNING
                    } else {
                        KotlinDiagnosticSeverity.ERROR
                    }
                    diagnostics += KotlinDiagnostic(
                        severity = severity,
                        message = m1.groupValues[5].trim(),
                        filePath = m1.groupValues[1].trim(),
                        line = m1.groupValues[2].toIntOrNull() ?: 1,
                        column = m1.groupValues[3].toIntOrNull() ?: 1,
                        length = 1,
                        code = "kotlinc"
                    )
                    return@forEach
                }

                val m2 = kotlincRegex.find(line)
                if (m2 != null) {
                    val severity = if (line.startsWith("w:", ignoreCase = true)) {
                        KotlinDiagnosticSeverity.WARNING
                    } else {
                        KotlinDiagnosticSeverity.ERROR
                    }
                    diagnostics += KotlinDiagnostic(
                        severity = severity,
                        message = m2.groupValues[4].trim(),
                        filePath = m2.groupValues[1].trim(),
                        line = m2.groupValues[2].toIntOrNull() ?: 1,
                        column = m2.groupValues[3].toIntOrNull() ?: 1,
                        length = 1,
                        code = "kotlinc"
                    )
                    return@forEach
                }
            }

        if (diagnostics.isEmpty()) {
            diagnostics += KotlinDiagnostic(
                severity = KotlinDiagnosticSeverity.ERROR,
                message = merged.lineSequence().firstOrNull().orEmpty(),
                filePath = "",
                line = 1,
                column = 1,
                length = 1,
                code = "kotlinc"
            )
        }
        return diagnostics
    }

    private fun buildCompilerLauncher(kotlinc: File): List<String>? {
        return if (kotlinc.extension.equals("jar", ignoreCase = true)) {
            val javaBinary = toolchainManager.getBinaryPath(ToolchainManager.ToolchainBinary.JAVA)
                ?: File("/system/bin/java").takeIf { it.exists() }
                ?: return null
            listOf(javaBinary.absolutePath, "-jar", kotlinc.absolutePath)
        } else {
            listOf(kotlinc.absolutePath)
        }
    }
}
