package com.rostrum.core.runtime.impl

import android.content.Context
import android.util.Log
import com.rostrum.core.runtime.AndroidApkBuildService
import com.rostrum.core.runtime.ApkBuildRequest
import com.rostrum.core.runtime.ApkBuildResult
import com.rostrum.core.runtime.BuildStageLog
import com.rostrum.core.runtime.BuildType
import com.rostrum.core.runtime.JavaCompileRequest
import com.rostrum.core.runtime.JavaExecutionService
import com.rostrum.core.runtime.KotlinCompileRequest
import com.rostrum.core.runtime.KotlinExecutionService
import com.rostrum.core.runtime.OmniAndroidModel
import com.rostrum.core.runtime.ProjectValidationResult
import com.rostrum.core.runtime.SigningConfig
import com.rostrum.core.runtime.model.BuildDiagnostic
import com.rostrum.core.runtime.model.BuildDiagnosticSeverity
import com.rostrum.core.runtime.model.BuildPhase
import com.rostrum.core.runtime.model.BuildPhaseEvent
import com.rostrum.core.runtime.model.BuildPhaseState
import com.rostrum.core.runtime.model.SigningValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.json.JSONObject
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Date
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Android APK 构建服务实现（V1）。
 *
 * 说明：
 * - 仅接收结构化参数，不接受 shell 原始命令。
 * - V1 使用 omni.android.json 描述项目元数据。
 */
class AndroidApkBuildServiceImpl(
    private val context: Context,
    private val toolchainManager: ToolchainManager,
    private val javaExecutionService: JavaExecutionService,
    private val kotlinExecutionService: KotlinExecutionService?
) : AndroidApkBuildService {

    companion object {
        private const val TAG = "AndroidApkBuildService"
        private const val MODEL_FILE = "omni.android.json"
        private const val PROCESS_TIMEOUT_MS = 180_000L
        private const val MAX_OUTPUT_LENGTH = 512_000

        private const val DEBUG_KEYSTORE_NAME = "debug.keystore"
        private const val DEBUG_STORE_PASSWORD = "android"
        private const val DEBUG_KEY_ALIAS = "androiddebugkey"
        private const val DEBUG_KEY_PASSWORD = "android"
    }

    private val buildMutex = Mutex()
    private val activeProcess = AtomicReference<Process?>(null)

    @Volatile
    private var buildCancelled = false

    override fun cancelCurrentBuild(): Boolean {
        buildCancelled = true
        val process = activeProcess.get()
        if (process != null && process.isAlive) {
            process.destroyForcibly()
            return true
        }
        return false
    }

    override fun validateSigningConfig(buildType: BuildType, signing: SigningConfig?): SigningValidationResult {
        if (buildType == BuildType.DEBUG && signing == null) {
            return SigningValidationResult(valid = true)
        }

        if (buildType == BuildType.RELEASE && signing == null) {
            return SigningValidationResult(
                valid = false,
                fieldErrors = mapOf("signing" to "Release build requires signing config")
            )
        }

        if (signing == null) {
            return SigningValidationResult(valid = true)
        }

        val errors = linkedMapOf<String, String>()
        if (signing.keystorePath.isBlank()) {
            errors["keystorePath"] = "Keystore path is required"
        } else {
            val file = File(signing.keystorePath)
            if (!file.exists() || !file.isFile) {
                errors["keystorePath"] = "Keystore file does not exist"
            }
        }

        if (signing.storePassword.isBlank()) {
            errors["storePassword"] = "Store password is required"
        }
        if (signing.keyAlias.isBlank()) {
            errors["keyAlias"] = "Key alias is required"
        }
        if (signing.keyPassword.isBlank()) {
            errors["keyPassword"] = "Key password is required"
        }
        if (!signing.v1Enabled && !signing.v2Enabled && !signing.v3Enabled && !signing.v4Enabled) {
            errors["signatureScheme"] = "At least one signature scheme must be enabled"
        }

        return SigningValidationResult(valid = errors.isEmpty(), fieldErrors = errors)
    }

    override suspend fun validateProject(projectRoot: String): ProjectValidationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val root = runCatching { File(projectRoot).canonicalFile }.getOrNull()
        if (root == null || !root.exists() || !root.isDirectory) {
            return@withContext ProjectValidationResult(
                success = false,
                errors = listOf("Project root does not exist or is not directory: $projectRoot")
            )
        }

        val modelFile = File(root, MODEL_FILE)
        if (!modelFile.exists()) {
            return@withContext ProjectValidationResult(
                success = false,
                errors = listOf("Missing $MODEL_FILE in project root")
            )
        }

        val model = runCatching { parseModel(modelFile) }
            .onFailure { errors += "Failed to parse $MODEL_FILE: ${it.message}" }
            .getOrNull()

        if (model == null) {
            return@withContext ProjectValidationResult(
                success = false,
                errors = errors,
                normalizedProjectRoot = root.absolutePath
            )
        }

        if (model.applicationId.isBlank()) {
            errors += "applicationId cannot be empty"
        } else {
            val packagePattern = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
            if (!packagePattern.matches(model.applicationId)) {
                errors += "applicationId is invalid: ${model.applicationId}"
            }
        }

        if (model.mainActivity.isBlank()) {
            errors += "mainActivity cannot be empty"
        }

        if (model.minSdk <= 0 || model.targetSdk <= 0 || model.compileSdk <= 0) {
            errors += "minSdk/targetSdk/compileSdk must be positive"
        }
        if (model.targetSdk < model.minSdk) {
            errors += "targetSdk cannot be less than minSdk"
        }
        if (model.compileSdk < model.targetSdk) {
            warnings += "compileSdk is lower than targetSdk"
        }

        val manifest = resolveInside(root, model.manifestPath)
        if (manifest == null || !manifest.exists()) {
            errors += "Manifest not found: ${model.manifestPath}"
        } else {
            val manifestText = runCatching { manifest.readText(Charsets.UTF_8) }.getOrDefault("")
            if (!manifestText.contains("<manifest")) {
                errors += "AndroidManifest.xml does not contain <manifest> tag"
            }
            if (!manifestText.contains("<application")) {
                errors += "AndroidManifest.xml does not contain <application> tag"
            }
            if (!Regex("package\\s*=\\s*\"[^\"]+\"").containsMatchIn(manifestText)) {
                warnings += "AndroidManifest.xml does not declare package attribute explicitly"
            }
        }

        if (resolveInside(root, model.resourceDir)?.exists() != true) {
            warnings += "Resource directory not found: ${model.resourceDir}"
        }

        val javaSourceDirs = model.sourceDirs
            .mapNotNull { sourceDir -> resolveInside(root, sourceDir) }
            .filter { it.exists() && it.isDirectory }
        val kotlinSourceDirs = model.kotlinSourceDirs
            .mapNotNull { sourceDir -> resolveInside(root, sourceDir) }
            .filter { it.exists() && it.isDirectory }

        if (javaSourceDirs.isEmpty() && kotlinSourceDirs.isEmpty()) {
            errors += "No source directory exists under sourceDirs/kotlinSourceDirs"
        }

        model.sourceDirs.forEach { sourceDir ->
            if (resolveInside(root, sourceDir)?.exists() != true) {
                warnings += "Java source directory not found: $sourceDir"
            }
        }
        model.kotlinSourceDirs.forEach { sourceDir ->
            if (resolveInside(root, sourceDir)?.exists() != true) {
                warnings += "Kotlin source directory not found: $sourceDir"
            }
        }

        val mixedPolicy = model.mixedSourcePolicy.trim().lowercase().ifBlank { "disallow" }
        if (mixedPolicy !in setOf("disallow", "allow")) {
            errors += "mixedSourcePolicy must be 'disallow' or 'allow'"
        }
        if (javaSourceDirs.isNotEmpty() && kotlinSourceDirs.isNotEmpty() && mixedPolicy != "allow") {
            errors += "Mixed Java/Kotlin sources are disabled in V1. Set mixedSourcePolicy=allow only when mixed mode is explicitly enabled."
        }

        if (model.localAars.isNotEmpty()) {
            errors += "V1 does not support localAars yet. Please convert AAR dependencies to JAR or remove localAars entries."
        }

        val localLibs = model.localJars
        localLibs.forEach { rel ->
            val file = resolveInside(root, rel)
            when {
                file == null -> errors += "Illegal dependency path outside project root: $rel"
                !file.exists() -> errors += "Local dependency not found: $rel"
                !file.canRead() -> errors += "Local dependency unreadable: $rel"
            }
        }

        ProjectValidationResult(
            success = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            normalizedProjectRoot = root.absolutePath,
            model = model
        )
    }

    override suspend fun build(request: ApkBuildRequest): ApkBuildResult = buildMutex.withLock {
        withContext(Dispatchers.IO) {
            buildCancelled = false
            val startedAt = System.currentTimeMillis()
            val logs = mutableListOf<BuildStageLog>()
            val diagnostics = mutableListOf<BuildDiagnostic>()
            val events = mutableListOf<BuildPhaseEvent>()

            fun fail(
                phase: BuildPhase,
                message: String,
                unsignedApkPath: String? = null,
                signedApkPath: String? = null
            ): ApkBuildResult {
                events += BuildPhaseEvent(phase = phase, state = BuildPhaseState.FAILED, message = message)
                return ApkBuildResult(
                    success = false,
                    phase = phase,
                    diagnostics = diagnostics,
                    phaseEvents = events,
                    apkPath = signedApkPath,
                    unsignedApkPath = unsignedApkPath,
                    signedApkPath = signedApkPath,
                    installIntentAvailable = false,
                    logs = logs,
                    error = message,
                    executionTimeMs = System.currentTimeMillis() - startedAt
                )
            }

            if (buildCancelled) {
                return@withContext fail(BuildPhase.VALIDATE, "Build cancelled")
            }

            events += BuildPhaseEvent(BuildPhase.VALIDATE, BuildPhaseState.STARTED, "Validating project")
            val validation = validateProject(request.projectRoot)
            if (!validation.success || validation.model == null || validation.normalizedProjectRoot == null) {
                validation.errors.forEach {
                    diagnostics += BuildDiagnostic(
                        phase = BuildPhase.VALIDATE,
                        message = it,
                        suggestion = "Fix project model or manifest and retry"
                    )
                }
                return@withContext fail(BuildPhase.VALIDATE, validation.errors.joinToString("; "))
            }
            events += BuildPhaseEvent(BuildPhase.VALIDATE, BuildPhaseState.SUCCEEDED, "Project validation passed")

            val signingValidation = validateSigningConfig(request.buildType, request.signing)
            if (!signingValidation.valid) {
                signingValidation.fieldErrors.forEach { (field, error) ->
                    diagnostics += BuildDiagnostic(
                        phase = BuildPhase.SIGN,
                        message = "$field: $error"
                    )
                }
                return@withContext fail(BuildPhase.SIGN, "Signing config validation failed")
            }

            if (!toolchainManager.initialize()) {
                val verify = toolchainManager.getLastVerifyResult()
                diagnostics += BuildDiagnostic(
                    phase = BuildPhase.VALIDATE,
                    message = verify.message,
                    suggestion = verify.details.joinToString("; "),
                    raw = verify.status.name
                )
                return@withContext fail(BuildPhase.VALIDATE, "Toolchain initialization failed: ${verify.message}")
            }

            val selfTest = toolchainManager.selfTest()
            if (!selfTest.success) {
                diagnostics += BuildDiagnostic(
                    phase = BuildPhase.VALIDATE,
                    message = "Toolchain self test failed",
                    suggestion = selfTest.checks.entries.joinToString("; ") { "${it.key}=${it.value}" }
                )
                return@withContext fail(BuildPhase.VALIDATE, "Toolchain self test failed")
            }

            val root = File(validation.normalizedProjectRoot)
            val model = validation.model
            val kotlinRoots = model.kotlinSourceDirs
                .mapNotNull { resolveInside(root, it)?.takeIf { dir -> dir.exists() } }
            val javaRoots = model.sourceDirs
                .mapNotNull { resolveInside(root, it)?.takeIf { dir -> dir.exists() } }

            val buildRoot = File(root, ".omnimaster/build")
            val intermediates = File(buildRoot, "intermediates")
            val classesDir = File(intermediates, "classes")
            val dexDir = File(intermediates, "dex")
            val generatedRDir = File(intermediates, "generated_r")

            if (buildRoot.exists()) buildRoot.mkdirs() else buildRoot.mkdirs()
            intermediates.mkdirs()
            classesDir.mkdirs()
            dexDir.mkdirs()
            generatedRDir.mkdirs()

            val aapt2 = toolchainManager.getBinaryPath(ToolchainManager.ToolchainBinary.AAPT2)
            val d8 = toolchainManager.getBinaryPath(ToolchainManager.ToolchainBinary.D8)
            val zipalign = toolchainManager.getBinaryPath(ToolchainManager.ToolchainBinary.ZIPALIGN)
            val apksigner = toolchainManager.getBinaryPath(ToolchainManager.ToolchainBinary.APKSIGNER)
            val androidJar = toolchainManager.getBinaryPath(ToolchainManager.ToolchainBinary.ANDROID_JAR)

            if (aapt2 == null || d8 == null || zipalign == null || androidJar == null) {
                return@withContext fail(
                    BuildPhase.VALIDATE,
                    "Missing required toolchain binary. Need aapt2/d8/zipalign/android.jar"
                )
            }

            val manifestFile = resolveInside(root, model.manifestPath)
                ?: return@withContext fail(BuildPhase.VALIDATE, "Invalid manifest path")

            val resDir = resolveInside(root, model.resourceDir)
            val assetsDir = resolveInside(root, model.assetDir)?.takeIf { it.exists() && it.isDirectory }
            val jniLibsDir = resolveInside(root, model.jniLibsDir)?.takeIf { it.exists() && it.isDirectory }

            val compiledResZip = File(intermediates, "compiled_res.zip")
            val linkedUnsignedApk = File(intermediates, "linked-unsigned.apk")

            // Step 1: aapt2 compile
            if (resDir != null && resDir.exists()) {
                val compileResLog = runBuildCommand(
                    phase = BuildPhase.AAPT2_COMPILE,
                    stageName = "aapt2-compile",
                    command = buildToolCommand(
                        binary = aapt2,
                        args = listOf(
                            "compile",
                            "--dir", resDir.absolutePath,
                            "-o", compiledResZip.absolutePath
                        )
                    ),
                    workingDir = root,
                    logs = logs,
                    events = events,
                    diagnostics = diagnostics
                )
                if (!compileResLog.success) {
                    return@withContext fail(BuildPhase.AAPT2_COMPILE, "aapt2 compile failed")
                }
            }

            // Step 2: aapt2 link
            val linkArgs = mutableListOf(
                "link",
                "-I", androidJar.absolutePath,
                "--manifest", manifestFile.absolutePath,
                "--java", generatedRDir.absolutePath,
                "--min-sdk-version", request.minSdk.toString(),
                "--target-sdk-version", request.targetSdk.toString(),
                "-o", linkedUnsignedApk.absolutePath
            )
            if (compiledResZip.exists()) {
                linkArgs += compiledResZip.absolutePath
            }
            if (assetsDir != null) {
                linkArgs += listOf("-A", assetsDir.absolutePath)
            }

            val linkLog = runBuildCommand(
                phase = BuildPhase.AAPT2_LINK,
                stageName = "aapt2-link",
                command = buildToolCommand(aapt2, linkArgs),
                workingDir = root,
                logs = logs,
                events = events,
                diagnostics = diagnostics
            )
            if (!linkLog.success) {
                return@withContext fail(BuildPhase.AAPT2_LINK, "aapt2 link failed")
            }

            // Step 3: Java compile（Java sources + generated R.java）
            val generatedJavaCount = generatedRDir.walkTopDown()
                .count { it.isFile && it.extension.equals("java", ignoreCase = true) }
            val hasJavaInputs = javaRoots.any { dir ->
                dir.walkTopDown().any { it.isFile && it.extension.equals("java", ignoreCase = true) }
            } || generatedJavaCount > 0
            val classpath = collectClasspath(root, model, androidJar)

            if (hasJavaInputs) {
                if (!javaExecutionService.initialize()) {
                    return@withContext fail(BuildPhase.JAVA_COMPILE, "Java execution service is not available")
                }

                events += BuildPhaseEvent(BuildPhase.JAVA_COMPILE, BuildPhaseState.STARTED, "Compiling Java sources")
                val javaCompileStart = System.currentTimeMillis()

                val sourceRoots = buildList {
                    addAll(javaRoots.map { it.absolutePath })
                    if (generatedJavaCount > 0) add(generatedRDir.absolutePath)
                }.distinct()

                val compileResult = javaExecutionService.compile(
                    JavaCompileRequest(
                        sourceRoots = sourceRoots,
                        classpath = classpath,
                        outputDir = classesDir.absolutePath,
                        sourceLevel = "1.8",
                        targetLevel = "1.8"
                    )
                )

                logs += BuildStageLog(
                    stage = "ecj-compile",
                    success = compileResult.success,
                    output = compileResult.output,
                    error = compileResult.error,
                    durationMs = System.currentTimeMillis() - javaCompileStart
                )

                diagnostics += compileResult.diagnostics.map {
                    BuildDiagnostic(
                        phase = BuildPhase.JAVA_COMPILE,
                        severity = when (it.severity) {
                            com.rostrum.core.runtime.JavaDiagnosticSeverity.ERROR -> BuildDiagnosticSeverity.ERROR
                            com.rostrum.core.runtime.JavaDiagnosticSeverity.WARNING -> BuildDiagnosticSeverity.WARNING
                            com.rostrum.core.runtime.JavaDiagnosticSeverity.INFO -> BuildDiagnosticSeverity.INFO
                        },
                        message = it.message,
                        file = it.filePath,
                        line = it.line,
                        column = it.column,
                        raw = it.code
                    )
                }

                events += BuildPhaseEvent(
                    phase = BuildPhase.JAVA_COMPILE,
                    state = if (compileResult.success) BuildPhaseState.SUCCEEDED else BuildPhaseState.FAILED,
                    message = if (compileResult.success) {
                        "Compiled ${compileResult.compiledFileCount} Java files"
                    } else {
                        "Java compile failed"
                    },
                    durationMs = System.currentTimeMillis() - javaCompileStart
                )

                if (!compileResult.success) {
                    return@withContext fail(BuildPhase.JAVA_COMPILE, "Java compile failed")
                }
            } else {
                events += BuildPhaseEvent(BuildPhase.JAVA_COMPILE, BuildPhaseState.SUCCEEDED, "No Java sources, skipped")
            }

            // Step 4: Kotlin compile
            val hasKotlinInputs = kotlinRoots.any { dir ->
                dir.walkTopDown().any { it.isFile && it.extension.equals("kt", ignoreCase = true) }
            }
            if (hasKotlinInputs) {
                val kotlinService = kotlinExecutionService
                    ?: return@withContext fail(BuildPhase.KOTLIN_COMPILE, "Kotlin execution service is not initialized")
                if (!kotlinService.initialize()) {
                    return@withContext fail(BuildPhase.KOTLIN_COMPILE, "Kotlin compiler is not available in toolchain")
                }

                events += BuildPhaseEvent(BuildPhase.KOTLIN_COMPILE, BuildPhaseState.STARTED, "Compiling Kotlin sources")
                val kotlinStart = System.currentTimeMillis()
                val kotlinClasspath = buildList {
                    addAll(classpath)
                    add(classesDir.absolutePath)
                }.distinct()

                val kotlinResult = kotlinService.compile(
                    KotlinCompileRequest(
                        sourceRoots = kotlinRoots.map { it.absolutePath },
                        classpath = kotlinClasspath,
                        outputDir = classesDir.absolutePath,
                        jvmTarget = "17"
                    )
                )

                logs += BuildStageLog(
                    stage = "kotlinc-compile",
                    success = kotlinResult.success,
                    output = kotlinResult.output,
                    error = kotlinResult.error,
                    durationMs = System.currentTimeMillis() - kotlinStart
                )

                diagnostics += kotlinResult.diagnostics.map {
                    BuildDiagnostic(
                        phase = BuildPhase.KOTLIN_COMPILE,
                        severity = when (it.severity) {
                            com.rostrum.core.runtime.KotlinDiagnosticSeverity.ERROR -> BuildDiagnosticSeverity.ERROR
                            com.rostrum.core.runtime.KotlinDiagnosticSeverity.WARNING -> BuildDiagnosticSeverity.WARNING
                            com.rostrum.core.runtime.KotlinDiagnosticSeverity.INFO -> BuildDiagnosticSeverity.INFO
                        },
                        message = it.message,
                        file = it.filePath,
                        line = it.line,
                        column = it.column,
                        raw = it.code
                    )
                }

                events += BuildPhaseEvent(
                    phase = BuildPhase.KOTLIN_COMPILE,
                    state = if (kotlinResult.success) BuildPhaseState.SUCCEEDED else BuildPhaseState.FAILED,
                    message = if (kotlinResult.success) {
                        "Compiled ${kotlinResult.compiledFileCount} Kotlin files"
                    } else {
                        "Kotlin compile failed"
                    },
                    durationMs = System.currentTimeMillis() - kotlinStart
                )

                if (!kotlinResult.success) {
                    return@withContext fail(BuildPhase.KOTLIN_COMPILE, "Kotlin compile failed")
                }
            }

            val classInputs = classesDir.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .map { it.absolutePath }
                .toList()

            val localJarProgramInputs = model.localJars
                .mapNotNull { rel -> resolveInside(root, rel)?.takeIf { it.exists() && it.isFile }?.absolutePath }

            if (classInputs.isEmpty() && localJarProgramInputs.isEmpty()) {
                diagnostics += BuildDiagnostic(
                    phase = BuildPhase.JAVA_COMPILE,
                    message = "No .class files produced by compiler"
                )
                return@withContext fail(BuildPhase.JAVA_COMPILE, "No .class files produced by compiler")
            }

            // Step 5: d8 dexing
            val d8Args = mutableListOf(
                "--min-api", request.minSdk.toString(),
                "--lib", androidJar.absolutePath,
                "--output", dexDir.absolutePath
            )
            d8Args += classInputs
            d8Args += localJarProgramInputs

            val d8Log = runBuildCommand(
                phase = BuildPhase.D8,
                stageName = "d8",
                command = buildToolCommand(d8, d8Args),
                workingDir = root,
                logs = logs,
                events = events,
                diagnostics = diagnostics
            )
            if (!d8Log.success) {
                return@withContext fail(BuildPhase.D8, "d8 failed")
            }

            val dexFiles = dexDir.listFiles { f -> f.isFile && f.extension == "dex" }
                ?.sortedBy { it.name }
                .orEmpty()

            if (dexFiles.isEmpty()) {
                diagnostics += BuildDiagnostic(
                    phase = BuildPhase.D8,
                    message = "No dex files generated"
                )
                return@withContext fail(BuildPhase.D8, "No dex files generated")
            }

            // Step 5: package
            events += BuildPhaseEvent(BuildPhase.PACKAGE, BuildPhaseState.STARTED, "Packaging APK")
            val packageStart = System.currentTimeMillis()

            val packagedUnsignedApk = File(buildRoot, "app-unsigned.apk")
            val packageResult = runCatching {
                packageApk(
                    baseApk = linkedUnsignedApk,
                    dexFiles = dexFiles,
                    assetsDir = assetsDir,
                    jniLibsDir = jniLibsDir,
                    outputApk = packagedUnsignedApk
                )
            }

            if (packageResult.isFailure) {
                val error = packageResult.exceptionOrNull()?.message ?: "APK packaging failed"
                diagnostics += BuildDiagnostic(
                    phase = BuildPhase.PACKAGE,
                    message = error
                )
                logs += BuildStageLog(
                    stage = "package",
                    success = false,
                    error = error,
                    durationMs = System.currentTimeMillis() - packageStart
                )
                events += BuildPhaseEvent(
                    phase = BuildPhase.PACKAGE,
                    state = BuildPhaseState.FAILED,
                    message = error,
                    durationMs = System.currentTimeMillis() - packageStart
                )
                return@withContext fail(BuildPhase.PACKAGE, error)
            }

            logs += BuildStageLog(
                stage = "package",
                success = true,
                output = packagedUnsignedApk.absolutePath,
                durationMs = System.currentTimeMillis() - packageStart
            )
            events += BuildPhaseEvent(
                phase = BuildPhase.PACKAGE,
                state = BuildPhaseState.SUCCEEDED,
                message = packagedUnsignedApk.absolutePath,
                durationMs = System.currentTimeMillis() - packageStart
            )

            // Step 6: zipalign
            val alignedApk = File(buildRoot, "app-aligned.apk")
            val alignLog = runBuildCommand(
                phase = BuildPhase.ZIPALIGN,
                stageName = "zipalign",
                command = listOf(
                    zipalign.absolutePath,
                    "-f", "4",
                    packagedUnsignedApk.absolutePath,
                    alignedApk.absolutePath
                ),
                workingDir = root,
                logs = logs,
                events = events,
                diagnostics = diagnostics
            )
            if (!alignLog.success) {
                return@withContext fail(
                    phase = BuildPhase.ZIPALIGN,
                    message = "zipalign failed",
                    unsignedApkPath = packagedUnsignedApk.absolutePath
                )
            }

            val effectiveSigning = request.signing ?: if (request.buildType == BuildType.DEBUG) {
                createOrGetDebugSigningConfig()
            } else {
                null
            }

            if (effectiveSigning == null) {
                diagnostics += BuildDiagnostic(
                    phase = BuildPhase.SIGN,
                    message = "Signing config required to produce installable APK"
                )
                return@withContext fail(
                    phase = BuildPhase.SIGN,
                    message = "Signing config required to produce installable APK",
                    unsignedApkPath = alignedApk.absolutePath
                )
            }

            if (apksigner == null) {
                diagnostics += BuildDiagnostic(
                    phase = BuildPhase.SIGN,
                    message = "apksigner not found in toolchain"
                )
                return@withContext fail(
                    phase = BuildPhase.SIGN,
                    message = "apksigner not found in toolchain",
                    unsignedApkPath = alignedApk.absolutePath
                )
            }

            // Step 7: sign
            val signedApk = File(buildRoot, "app-${request.buildType.name.lowercase()}-signed.apk")
            val signCommand = buildApkSignerCommand(
                apksigner = apksigner,
                mode = "sign",
                signingConfig = effectiveSigning,
                inputApk = alignedApk,
                outputApk = signedApk
            )

            val signLog = runBuildCommand(
                phase = BuildPhase.SIGN,
                stageName = "sign",
                command = signCommand,
                workingDir = root,
                logs = logs,
                events = events,
                diagnostics = diagnostics
            )
            if (!signLog.success) {
                return@withContext fail(
                    phase = BuildPhase.SIGN,
                    message = "APK signing failed",
                    unsignedApkPath = alignedApk.absolutePath
                )
            }

            // Step 8: verify signature
            val verifyCommand = buildApkSignerCommand(
                apksigner = apksigner,
                mode = "verify",
                signingConfig = effectiveSigning,
                inputApk = signedApk,
                outputApk = null
            )

            val verifyLog = runBuildCommand(
                phase = BuildPhase.VERIFY,
                stageName = "verify",
                command = verifyCommand,
                workingDir = root,
                logs = logs,
                events = events,
                diagnostics = diagnostics
            )
            if (!verifyLog.success) {
                return@withContext fail(
                    phase = BuildPhase.VERIFY,
                    message = "APK signature verification failed",
                    unsignedApkPath = alignedApk.absolutePath,
                    signedApkPath = signedApk.absolutePath
                )
            }

            ApkBuildResult(
                success = true,
                phase = BuildPhase.VERIFY,
                diagnostics = diagnostics,
                phaseEvents = events,
                apkPath = signedApk.absolutePath,
                unsignedApkPath = alignedApk.absolutePath,
                signedApkPath = signedApk.absolutePath,
                installIntentAvailable = true,
                logs = logs,
                executionTimeMs = System.currentTimeMillis() - startedAt
            )
        }
    }

    override suspend fun clean(projectRoot: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val root = File(projectRoot).canonicalFile
            val buildDir = File(root, ".omnimaster/build")
            if (buildDir.exists()) {
                buildDir.deleteRecursively()
            }
        }
    }

    private fun parseModel(modelFile: File): OmniAndroidModel {
        val json = JSONObject(modelFile.readText(Charsets.UTF_8))
        return OmniAndroidModel(
            applicationId = json.optString("applicationId"),
            versionCode = json.optInt("versionCode", 1),
            versionName = json.optString("versionName", "1.0.0"),
            minSdk = json.optInt("minSdk", 26),
            targetSdk = json.optInt("targetSdk", 34),
            compileSdk = json.optInt("compileSdk", 34),
            mainActivity = json.optString("mainActivity", ".MainActivity"),
            sourceDirs = json.optJSONArray("sourceDirs")?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        add(arr.getString(i))
                    }
                }
            } ?: listOf("src/main/java"),
            kotlinSourceDirs = json.optJSONArray("kotlinSourceDirs")?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        add(arr.getString(i))
                    }
                }
            } ?: listOf("src/main/kotlin"),
            mixedSourcePolicy = json.optString("mixedSourcePolicy", "disallow"),
            resourceDir = json.optString("resourceDir", "src/main/res"),
            manifestPath = json.optString("manifestPath", "src/main/AndroidManifest.xml"),
            assetDir = json.optString("assetDir", "src/main/assets"),
            jniLibsDir = json.optString("jniLibsDir", "src/main/jniLibs"),
            localJars = json.optJSONArray("localJars")?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) add(arr.getString(i))
                }
            } ?: emptyList(),
            localAars = json.optJSONArray("localAars")?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) add(arr.getString(i))
                }
            } ?: emptyList()
        )
    }

    private fun collectClasspath(
        root: File,
        model: OmniAndroidModel,
        androidJar: File
    ): List<String> {
        val jars = model.localJars
            .mapNotNull { resolveInside(root, it)?.takeIf { f -> f.exists() }?.absolutePath }

        return buildList {
            add(androidJar.absolutePath)
            addAll(jars)
        }.distinct()
    }

    private fun resolveInside(root: File, relativePath: String): File? {
        val target = File(root, relativePath).canonicalFile
        val rootPath = root.canonicalPath
        return if (target.path == rootPath || target.path.startsWith("$rootPath${File.separator}")) {
            target
        } else {
            Log.w(TAG, "Blocked path escaping project root: $relativePath")
            null
        }
    }

    private fun buildToolCommand(binary: File, args: List<String>): List<String> {
        if (isShellScript(binary)) {
            return listOf("/system/bin/sh", binary.absolutePath) + args
        }

        return if (binary.extension.equals("jar", ignoreCase = true)) {
            val javaBinary = toolchainManager.getBinaryPath(ToolchainManager.ToolchainBinary.JAVA)
                ?: File("/system/bin/java").takeIf { it.exists() }
                ?: error("${binary.name} requires java runtime")
            listOf(javaBinary.absolutePath, "-jar", binary.absolutePath) + args
        } else {
            listOf(binary.absolutePath) + args
        }
    }

    private fun isShellScript(file: File): Boolean {
        return runCatching {
            file.inputStream().buffered().use { input ->
                val header = ByteArray(2)
                val read = input.read(header)
                read == 2 && header[0] == '#'.code.toByte() && header[1] == '!'.code.toByte()
            }
        }.getOrDefault(false)
    }

    private suspend fun runBuildCommand(
        phase: BuildPhase,
        stageName: String,
        command: List<String>,
        workingDir: File,
        logs: MutableList<BuildStageLog>,
        events: MutableList<BuildPhaseEvent>,
        diagnostics: MutableList<BuildDiagnostic>
    ): BuildStageLog {
        events += BuildPhaseEvent(phase = phase, state = BuildPhaseState.STARTED, message = stageName)

        if (buildCancelled) {
            val cancelledLog = BuildStageLog(
                stage = stageName,
                success = false,
                error = "Cancelled",
                durationMs = 0
            )
            logs += cancelledLog
            events += BuildPhaseEvent(phase = phase, state = BuildPhaseState.FAILED, message = "Cancelled")
            return cancelledLog
        }

        val log = runCommand(
            stage = stageName,
            command = command,
            workingDir = workingDir,
            timeoutMs = PROCESS_TIMEOUT_MS
        )

        logs += log
        diagnostics += parseDiagnostics(phase, log.output, log.error)
        events += BuildPhaseEvent(
            phase = phase,
            state = if (log.success) BuildPhaseState.SUCCEEDED else BuildPhaseState.FAILED,
            message = if (log.success) "OK" else (log.error.ifBlank { "Command failed" }),
            output = log.output,
            durationMs = log.durationMs
        )

        return log
    }

    private suspend fun runCommand(
        stage: String,
        command: List<String>,
        workingDir: File,
        timeoutMs: Long
    ): BuildStageLog = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        return@withContext runCatching {
            if (buildCancelled) {
                return@runCatching BuildStageLog(
                    stage = stage,
                    success = false,
                    error = "Cancelled",
                    durationMs = 0
                )
            }

            val process = ProcessBuilder(command)
                .directory(workingDir)
                .redirectErrorStream(false)
                .start()

            activeProcess.set(process)

            try {
                val result = withTimeoutOrNull(timeoutMs) {
                    val outDeferred = async(Dispatchers.IO) {
                        process.inputStream.bufferedReader().use { reader ->
                            reader.readText().take(MAX_OUTPUT_LENGTH)
                        }
                    }
                    val errDeferred = async(Dispatchers.IO) {
                        process.errorStream.bufferedReader().use { reader ->
                            reader.readText().take(MAX_OUTPUT_LENGTH)
                        }
                    }
                    val exitCode = process.waitFor()
                    Triple(exitCode, outDeferred.await(), errDeferred.await())
                }

                if (result == null) {
                    process.destroyForcibly()
                    BuildStageLog(
                        stage = stage,
                        success = false,
                        error = "Timeout (${timeoutMs}ms)",
                        durationMs = System.currentTimeMillis() - started
                    )
                } else {
                    val cancelled = buildCancelled
                    BuildStageLog(
                        stage = stage,
                        success = result.first == 0 && !cancelled,
                        output = result.second.trim(),
                        error = if (cancelled) {
                            "Cancelled"
                        } else {
                            result.third.trim()
                        },
                        durationMs = System.currentTimeMillis() - started
                    )
                }
            } finally {
                activeProcess.compareAndSet(process, null)
                if (process.isAlive) process.destroyForcibly()
            }
        }.getOrElse { e ->
            BuildStageLog(
                stage = stage,
                success = false,
                error = e.message ?: "Unknown error",
                durationMs = System.currentTimeMillis() - started
            )
        }
    }

    private fun parseDiagnostics(phase: BuildPhase, stdout: String, stderr: String): List<BuildDiagnostic> {
        val lines = buildList {
            addAll(stdout.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList())
            addAll(stderr.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList())
        }

        if (lines.isEmpty()) return emptyList()

        val fileLineColPattern = Pattern.compile("^(.+?):(\\d+):(\\d+):\\s*(error|warning):\\s*(.+)$", Pattern.CASE_INSENSITIVE)
        val fileLinePattern = Pattern.compile("^(.+?):(\\d+):\\s*(error|warning):\\s*(.+)$", Pattern.CASE_INSENSITIVE)

        return lines.map { line ->
            val m1 = fileLineColPattern.matcher(line)
            val m2 = fileLinePattern.matcher(line)

            when {
                m1.matches() -> BuildDiagnostic(
                    phase = phase,
                    severity = when (m1.group(4)?.lowercase()) {
                        "warning" -> BuildDiagnosticSeverity.WARNING
                        else -> BuildDiagnosticSeverity.ERROR
                    },
                    file = m1.group(1),
                    line = m1.group(2)?.toIntOrNull(),
                    column = m1.group(3)?.toIntOrNull(),
                    message = m1.group(5) ?: line,
                    raw = line
                )

                m2.matches() -> BuildDiagnostic(
                    phase = phase,
                    severity = when (m2.group(3)?.lowercase()) {
                        "warning" -> BuildDiagnosticSeverity.WARNING
                        else -> BuildDiagnosticSeverity.ERROR
                    },
                    file = m2.group(1),
                    line = m2.group(2)?.toIntOrNull(),
                    column = null,
                    message = m2.group(4) ?: line,
                    raw = line
                )

                else -> BuildDiagnostic(
                    phase = phase,
                    severity = when {
                        line.startsWith("warning", ignoreCase = true) -> BuildDiagnosticSeverity.WARNING
                        line.startsWith("info", ignoreCase = true) -> BuildDiagnosticSeverity.INFO
                        else -> BuildDiagnosticSeverity.ERROR
                    },
                    message = line,
                    raw = line
                )
            }
        }
    }

    private fun packageApk(
        baseApk: File,
        dexFiles: List<File>,
        assetsDir: File?,
        jniLibsDir: File?,
        outputApk: File
    ) {
        outputApk.parentFile?.mkdirs()

        val seenEntries = mutableSetOf<String>()
        ZipOutputStream(outputApk.outputStream().buffered()).use { zos ->
            ZipInputStream(baseApk.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.name.startsWith("classes") || !entry.name.endsWith(".dex")) {
                        val copied = ZipEntry(entry.name).apply {
                            method = entry.method
                            time = entry.time
                            comment = entry.comment
                            extra = entry.extra
                            if (entry.method == ZipEntry.STORED) {
                                size = entry.size
                                compressedSize = entry.compressedSize
                                crc = entry.crc
                            }
                        }
                        zos.putNextEntry(copied)
                        zis.copyTo(zos)
                        zos.closeEntry()
                        seenEntries += entry.name
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            dexFiles.forEach { dex ->
                val dexEntry = dex.name
                if (seenEntries.add(dexEntry)) {
                    zos.putNextEntry(ZipEntry(dexEntry))
                    dex.inputStream().buffered().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }

            assetsDir?.takeIf { it.exists() }?.walkTopDown()
                ?.filter { it.isFile }
                ?.forEach { file ->
                    val rel = file.relativeTo(assetsDir).invariantSeparatorsPath
                    val entryName = "assets/$rel"
                    if (seenEntries.add(entryName)) {
                        zos.putNextEntry(ZipEntry(entryName))
                        file.inputStream().buffered().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }

            jniLibsDir?.takeIf { it.exists() }?.walkTopDown()
                ?.filter { it.isFile }
                ?.forEach { file ->
                    val rel = file.relativeTo(jniLibsDir).invariantSeparatorsPath
                    val entryName = "lib/$rel"
                    if (seenEntries.add(entryName)) {
                        zos.putNextEntry(ZipEntry(entryName))
                        file.inputStream().buffered().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
        }
    }

    private fun buildApkSignerCommand(
        apksigner: File,
        mode: String,
        signingConfig: SigningConfig,
        inputApk: File,
        outputApk: File?
    ): List<String> {
        val toolArgs = when (mode) {
            "sign" -> {
                listOf(
                    "sign",
                    "--ks", signingConfig.keystorePath,
                    "--ks-type", signingConfig.storeType,
                    "--ks-key-alias", signingConfig.keyAlias,
                    "--ks-pass", "pass:${signingConfig.storePassword}",
                    "--key-pass", "pass:${signingConfig.keyPassword}",
                    "--v1-signing-enabled", signingConfig.v1Enabled.toString(),
                    "--v2-signing-enabled", signingConfig.v2Enabled.toString(),
                    "--v3-signing-enabled", signingConfig.v3Enabled.toString()
                ) + listOfNotNull(
                    outputApk?.let { "--out" },
                    outputApk?.absolutePath,
                    inputApk.absolutePath
                )
            }

            "verify" -> {
                listOf(
                    "verify",
                    "--print-certs",
                    "--verbose",
                    inputApk.absolutePath
                )
            }

            else -> error("Unknown apksigner mode: $mode")
        }

        return buildToolCommand(apksigner, toolArgs)
    }

    private fun createOrGetDebugSigningConfig(): SigningConfig {
        val keystoreDir = File(context.filesDir, "build/debug_keystore")
        keystoreDir.mkdirs()

        val keystoreFile = File(keystoreDir, DEBUG_KEYSTORE_NAME)
        if (!keystoreFile.exists() || !isDebugKeystoreUsable(keystoreFile)) {
            if (keystoreFile.exists()) {
                runCatching { keystoreFile.delete() }
            }
            createDebugKeystore(keystoreFile)
        }

        return SigningConfig(
            keystorePath = keystoreFile.absolutePath,
            storePassword = DEBUG_STORE_PASSWORD,
            keyAlias = DEBUG_KEY_ALIAS,
            keyPassword = DEBUG_KEY_PASSWORD,
            storeType = "PKCS12",
            v1Enabled = true,
            v2Enabled = true,
            v3Enabled = true,
            v4Enabled = false
        )
    }

    private fun isDebugKeystoreUsable(keystoreFile: File): Boolean {
        return runCatching {
            val keyStore = KeyStore.getInstance("PKCS12")
            keystoreFile.inputStream().use { input ->
                keyStore.load(input, DEBUG_STORE_PASSWORD.toCharArray())
            }
            keyStore.containsAlias(DEBUG_KEY_ALIAS)
        }.getOrDefault(false)
    }

    private fun createDebugKeystore(keystoreFile: File) {
        val now = Date()
        val notAfter = Date(now.time + 365L * 30 * 24 * 60 * 60 * 1000)

        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val keyPair = generator.generateKeyPair()

        val subject = X500Name("CN=Android Debug, O=OmniMaster, C=US")
        val certBuilder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(System.currentTimeMillis()),
            now,
            notAfter,
            subject,
            keyPair.public
        )

        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .build(keyPair.private)

        val cert = JcaX509CertificateConverter()
            .getCertificate(certBuilder.build(signer))

        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)
        keyStore.setKeyEntry(
            DEBUG_KEY_ALIAS,
            keyPair.private,
            DEBUG_KEY_PASSWORD.toCharArray(),
            arrayOf(cert)
        )

        keystoreFile.outputStream().use { out ->
            keyStore.store(out, DEBUG_STORE_PASSWORD.toCharArray())
        }
    }
}
