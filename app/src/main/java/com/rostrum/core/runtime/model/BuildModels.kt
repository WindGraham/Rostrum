package com.rostrum.core.runtime.model

enum class BuildPhase {
    VALIDATE,
    AAPT2_COMPILE,
    AAPT2_LINK,
    JAVA_COMPILE,
    KOTLIN_COMPILE,
    D8,
    PACKAGE,
    ZIPALIGN,
    SIGN,
    VERIFY,
    INSTALL,
    CLEANUP
}

enum class BuildPhaseState {
    STARTED,
    SUCCEEDED,
    FAILED
}

data class BuildPhaseEvent(
    val phase: BuildPhase,
    val state: BuildPhaseState,
    val message: String = "",
    val output: String = "",
    val durationMs: Long = 0
)

data class BuildDiagnostic(
    val phase: BuildPhase,
    val message: String,
    val severity: BuildDiagnosticSeverity = BuildDiagnosticSeverity.ERROR,
    val file: String? = null,
    val line: Int? = null,
    val column: Int? = null,
    val suggestion: String? = null,
    val raw: String? = null
)

enum class BuildDiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}

/**
 * 针对 UI 的字段级签名校验结果。
 */
data class SigningValidationResult(
    val valid: Boolean,
    val fieldErrors: Map<String, String> = emptyMap()
)
