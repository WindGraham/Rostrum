package com.rostrum.core.runtime.model

/**
 * 打包在 assets 的工具链元信息。
 */
data class ToolchainManifest(
    val schemaVersion: String,
    val abi: String,
    val version: String,
    val artifacts: List<ToolchainArtifact>
)

data class ToolchainArtifact(
    val name: String,
    val relativePath: String,
    val sha256: String,
    val sizeBytes: Long,
    val executable: Boolean,
    val sourceUrl: String,
    val versionOrCommit: String,
    val license: String
)

enum class ToolchainVerifyStatus {
    OK,
    MISSING,
    HASH_MISMATCH,
    NOT_EXECUTABLE,
    INCOMPATIBLE_ABI,
    INVALID_MANIFEST
}

data class ToolchainVerifyResult(
    val status: ToolchainVerifyStatus,
    val message: String,
    val details: List<String> = emptyList()
)

data class ToolchainSelfTestResult(
    val success: Boolean,
    val checks: Map<String, String> = emptyMap()
)
