package com.rostrum.core.runtime

import com.rostrum.core.runtime.model.BuildDiagnostic
import com.rostrum.core.runtime.model.BuildPhase
import com.rostrum.core.runtime.model.BuildPhaseEvent
import com.rostrum.core.runtime.model.SigningValidationResult

/**
 * Android APK 构建服务接口
 *
 * 采用结构化参数驱动构建，避免 shell 字符串注入风险。
 */
interface AndroidApkBuildService {
    suspend fun validateProject(projectRoot: String): ProjectValidationResult
    suspend fun build(request: ApkBuildRequest): ApkBuildResult
    suspend fun clean(projectRoot: String): Result<Unit>
    fun validateSigningConfig(buildType: BuildType, signing: SigningConfig?): SigningValidationResult
    fun cancelCurrentBuild(): Boolean
}

enum class BuildType {
    DEBUG,
    RELEASE
}

data class SigningConfig(
    val keystorePath: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
    val storeType: String = "JKS",
    val v1Enabled: Boolean = true,
    val v2Enabled: Boolean = true,
    val v3Enabled: Boolean = true,
    val v4Enabled: Boolean = false
)

data class ApkBuildRequest(
    val projectRoot: String,
    val buildType: BuildType,
    val signing: SigningConfig?,
    val minSdk: Int = 26,
    val targetSdk: Int = 34,
    val compileSdk: Int = 34,
    val installAfterBuild: Boolean = false
)

data class ProjectValidationResult(
    val success: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val normalizedProjectRoot: String? = null,
    val model: OmniAndroidModel? = null
)

data class ApkBuildResult(
    val success: Boolean,
    val phase: BuildPhase? = null,
    val diagnostics: List<BuildDiagnostic> = emptyList(),
    val phaseEvents: List<BuildPhaseEvent> = emptyList(),
    val apkPath: String? = null,
    val unsignedApkPath: String? = null,
    val signedApkPath: String? = null,
    val installIntentAvailable: Boolean = false,
    val logs: List<BuildStageLog> = emptyList(),
    val error: String? = null,
    val executionTimeMs: Long = 0
)

data class BuildStageLog(
    val stage: String,
    val success: Boolean,
    val output: String = "",
    val error: String = "",
    val durationMs: Long = 0
)

/**
 * V1 项目模型，避免直接解析复杂 Gradle DSL。
 */
data class OmniAndroidModel(
    val applicationId: String,
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int,
    val targetSdk: Int,
    val compileSdk: Int,
    val mainActivity: String,
    val sourceDirs: List<String> = listOf("src/main/java"),
    val kotlinSourceDirs: List<String> = listOf("src/main/kotlin"),
    val mixedSourcePolicy: String = "disallow",
    val resourceDir: String = "src/main/res",
    val manifestPath: String = "src/main/AndroidManifest.xml",
    val assetDir: String = "src/main/assets",
    val jniLibsDir: String = "src/main/jniLibs",
    val localJars: List<String> = emptyList(),
    val localAars: List<String> = emptyList()
)
