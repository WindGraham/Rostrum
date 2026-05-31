package com.rostrum.core.runtime

/**
 * Kotlin 执行服务接口。
 *
 * V1 通过内置工具链（kotlinc/java）提供 Kotlin 编译与运行能力。
 */
interface KotlinExecutionService {
    suspend fun initialize(): Boolean
    fun isAvailable(): Boolean
    suspend fun compile(request: KotlinCompileRequest): KotlinCompileResult
    suspend fun run(request: KotlinRunRequest): KotlinRunResult
}

data class KotlinCompileRequest(
    val sourceRoots: List<String>,
    val classpath: List<String> = emptyList(),
    val outputDir: String,
    val jvmTarget: String = "17"
)

data class KotlinCompileResult(
    val success: Boolean,
    val diagnostics: List<KotlinDiagnostic> = emptyList(),
    val output: String = "",
    val error: String = "",
    val executionTimeMs: Long = 0,
    val compiledFileCount: Int = 0
)

data class KotlinRunRequest(
    val mainClass: String,
    val classpath: List<String>,
    val args: List<String> = emptyList(),
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 60_000
)

data class KotlinRunResult(
    val success: Boolean,
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = -1,
    val executionTimeMs: Long = 0,
    val timedOut: Boolean = false
)

data class KotlinDiagnostic(
    val severity: KotlinDiagnosticSeverity,
    val message: String,
    val filePath: String,
    val line: Int,
    val column: Int,
    val length: Int,
    val code: String
)

enum class KotlinDiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}
