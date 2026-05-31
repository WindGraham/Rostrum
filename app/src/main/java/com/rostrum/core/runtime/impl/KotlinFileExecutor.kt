package com.rostrum.core.runtime.impl

import android.util.Log
import com.rostrum.core.domain.model.FileItem
import com.rostrum.core.runtime.FileExecutionContext
import com.rostrum.core.runtime.FileExecutionResult
import com.rostrum.core.runtime.FileExecutor
import com.rostrum.core.runtime.KotlinCompileRequest
import com.rostrum.core.runtime.KotlinExecutionService
import com.rostrum.core.runtime.KotlinRunRequest
import java.io.File

/**
 * Kotlin 文件执行器（.kt）。
 */
class KotlinFileExecutor(
    private val kotlinExecutionService: KotlinExecutionService
) : FileExecutor {

    companion object {
        private const val TAG = "KotlinFileExecutor"
    }

    override val supportedExtensions: List<String> = listOf("kt")
    override val name: String = "Kotlin"
    override val description: String = "Kotlin 文件执行器，支持 .kt 文件编译与运行"

    override fun isAvailable(): Boolean = kotlinExecutionService.isAvailable()

    override suspend fun execute(file: FileItem, context: FileExecutionContext): FileExecutionResult {
        val start = System.currentTimeMillis()
        val actualFile = file.file ?: return FileExecutionResult.failure(
            error = "File object is null: ${file.path}",
            executionTime = 0,
            executorName = name
        )

        if (!actualFile.exists()) {
            return FileExecutionResult.failure(
                error = "File does not exist: ${actualFile.absolutePath}",
                executionTime = System.currentTimeMillis() - start,
                executorName = name
            )
        }

        val baseDir = context.workingDirectory?.let { File(it) }
            ?: actualFile.parentFile
            ?: return FileExecutionResult.failure(
                error = "Cannot resolve working directory",
                executionTime = System.currentTimeMillis() - start,
                executorName = name
            )

        val outputDir = File(baseDir, ".omnimaster/kotlin/classes").apply { mkdirs() }
        val compileResult = kotlinExecutionService.compile(
            KotlinCompileRequest(
                sourceRoots = listOf(actualFile.parentFile?.absolutePath ?: baseDir.absolutePath),
                classpath = resolveClasspath(context, outputDir),
                outputDir = outputDir.absolutePath
            )
        )

        if (!compileResult.success) {
            val diagnosticSummary = compileResult.diagnostics
                .take(10)
                .joinToString("\n") { "[${it.severity}] ${it.message}" }
            val err = buildString {
                appendLine("Kotlin compile failed")
                if (compileResult.error.isNotBlank()) appendLine(compileResult.error)
                if (diagnosticSummary.isNotBlank()) appendLine(diagnosticSummary)
            }
            return FileExecutionResult.failure(
                error = err.trim(),
                exitCode = 1,
                executionTime = System.currentTimeMillis() - start,
                executorName = name
            )
        }

        val mainClass = detectMainClass(actualFile)
        if (mainClass == null) {
            return FileExecutionResult.failure(
                error = "Cannot infer main class from ${actualFile.name}. Expected top-level main() or object main entry.",
                exitCode = 2,
                executionTime = System.currentTimeMillis() - start,
                executorName = name
            )
        }

        Log.d(TAG, "Running Kotlin main class: $mainClass")
        val runResult = kotlinExecutionService.run(
            KotlinRunRequest(
                mainClass = mainClass,
                classpath = resolveClasspath(context, outputDir),
                args = context.arguments,
                workingDirectory = baseDir.absolutePath,
                environment = context.environment,
                timeoutMs = context.timeout
            )
        )

        return FileExecutionResult(
            success = runResult.success,
            output = runResult.stdout.ifBlank { null },
            error = runResult.stderr.ifBlank { null },
            exitCode = runResult.exitCode,
            executionTime = System.currentTimeMillis() - start,
            timedOut = runResult.timedOut,
            executorName = name
        )
    }

    private fun resolveClasspath(context: FileExecutionContext, outputDir: File): List<String> {
        val classpathFromEnv = context.environment["CLASSPATH"]
            ?.split(File.pathSeparator)
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        return buildList {
            add(outputDir.absolutePath)
            addAll(classpathFromEnv)
        }.distinct()
    }

    private fun detectMainClass(file: File): String? {
        val content = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        val packageName = Regex("^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*$", RegexOption.MULTILINE)
            .find(content)
            ?.groupValues
            ?.getOrNull(1)

        val hasTopLevelMain = Regex("""\bfun\s+main\s*\(""").containsMatchIn(content)
        if (hasTopLevelMain) {
            val generatedClass = "${file.nameWithoutExtension}Kt"
            return if (packageName.isNullOrBlank()) generatedClass else "$packageName.$generatedClass"
        }

        val objectMain = Regex("""\bobject\s+([A-Za-z_][A-Za-z0-9_]*)""").find(content)?.groupValues?.getOrNull(1)
        return objectMain?.let { if (packageName.isNullOrBlank()) it else "$packageName.$it" }
    }
}
