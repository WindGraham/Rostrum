package com.rostrum.core.runtime

/**
 * Java 执行服务接口
 *
 * 提供 Java 源码编译与主类运行能力。
 */
interface JavaExecutionService {
    suspend fun initialize(): Boolean
    fun isAvailable(): Boolean
    suspend fun compile(request: JavaCompileRequest): JavaCompileResult
    suspend fun run(request: JavaRunRequest): JavaRunResult
}

data class JavaCompileRequest(
    val sourceRoots: List<String>,
    val classpath: List<String> = emptyList(),
    val outputDir: String,
    val sourceLevel: String = "17",
    val targetLevel: String = "17"
)

data class JavaCompileResult(
    val success: Boolean,
    val diagnostics: List<JavaDiagnostic> = emptyList(),
    val output: String = "",
    val error: String = "",
    val executionTimeMs: Long = 0,
    val compiledFileCount: Int = 0
)

data class JavaRunRequest(
    val mainClass: String,
    val classpath: List<String>,
    val args: List<String> = emptyList(),
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 60_000
)

data class JavaRunResult(
    val success: Boolean,
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = -1,
    val executionTimeMs: Long = 0,
    val timedOut: Boolean = false
)

data class JavaDiagnostic(
    val severity: JavaDiagnosticSeverity,
    val message: String,
    val filePath: String,
    val line: Int,
    val column: Int,
    val length: Int,
    val code: String
)

enum class JavaDiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}
