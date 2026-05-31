package com.rostrum.core.runtime.impl

import android.content.Context
import android.os.Build
import android.util.Log
import com.rostrum.core.runtime.model.ToolchainArtifact
import com.rostrum.core.runtime.model.ToolchainManifest
import com.rostrum.core.runtime.model.ToolchainSelfTestResult
import com.rostrum.core.runtime.model.ToolchainVerifyResult
import com.rostrum.core.runtime.model.ToolchainVerifyStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.io.DEFAULT_BUFFER_SIZE

/**
 * Android 构建工具链管理器。
 *
 * 工具链默认从 assets/toolchains/android/<abi>/ 提取到 app 私有目录。
 */
class ToolchainManager(
    private val context: Context
) {

    companion object {
        private const val TAG = "ToolchainManager"
        private const val ASSET_ROOT = "toolchains/android"
        private const val VERSION_FILE = ".version"
        private const val MANIFEST_FILE = "manifest.json"
        private const val NATIVE_AAPT2 = "libomni_aapt2.so"
        private const val NATIVE_ZIPALIGN = "libomni_zipalign.so"
        private const val SYSTEM_SH = "/system/bin/sh"
    }

    enum class ToolchainBinary(val defaultNames: List<String>, val executable: Boolean) {
        AAPT2(listOf("aapt2"), true),
        D8(listOf("d8", "d8.sh"), true),
        ZIPALIGN(listOf("zipalign"), true),
        APKSIGNER(listOf("apksigner", "apksigner.jar"), false),
        KOTLINC(listOf("kotlinc", "kotlinc.jar"), true),
        JAVA(listOf("java"), true),
        ECJ(listOf("ecj.jar"), false),
        ANDROID_JAR(listOf("android.jar"), false)
    }

    private val toolchainRoot: File by lazy {
        File(context.filesDir, "toolchains/android")
    }

    @Volatile
    private var initialized: Boolean = false

    @Volatile
    private var lastVerifyResult: ToolchainVerifyResult = ToolchainVerifyResult(
        status = ToolchainVerifyStatus.MISSING,
        message = "Toolchain is not initialized"
    )

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (initialized && verifyInstalledToolchain()) {
            return@withContext true
        }

        runCatching {
            installOrUpdateFromAssets()
            initialized = verifyInstalledToolchain()
            initialized
        }.onFailure {
            lastVerifyResult = ToolchainVerifyResult(
                status = ToolchainVerifyStatus.INVALID_MANIFEST,
                message = it.message ?: "Failed to initialize toolchain"
            )
            Log.e(TAG, "Failed to initialize toolchain", it)
        }.getOrDefault(false)
    }

    fun isInitialized(): Boolean = initialized

    fun getLastVerifyResult(): ToolchainVerifyResult = lastVerifyResult

    fun getBinaryPath(binary: ToolchainBinary): File? {
        val nativeDir = context.applicationInfo.nativeLibraryDir
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }

        // Prefer APK-extracted native executable stubs for binaries that fail from app files dir.
        if (binary == ToolchainBinary.AAPT2) {
            val candidate = nativeDir?.let { File(it, NATIVE_AAPT2) }
            if (candidate != null && candidate.exists() && candidate.isFile) {
                return candidate
            }
        }
        if (binary == ToolchainBinary.ZIPALIGN) {
            val candidate = nativeDir?.let { File(it, NATIVE_ZIPALIGN) }
            if (candidate != null && candidate.exists() && candidate.isFile) {
                return candidate
            }
        }

        val candidates = listOf(getAbiDir(), toolchainRoot)
        for (dir in candidates) {
            for (name in binary.defaultNames) {
                val file = File(dir, name)
                if (file.exists() && file.isFile) {
                    return file
                }
            }
        }
        return null
    }

    suspend fun selfTest(timeoutMs: Long = 15_000): ToolchainSelfTestResult = withContext(Dispatchers.IO) {
        val checks = linkedMapOf<String, String>()

        val tasks = listOf(
            Triple("aapt2", ToolchainBinary.AAPT2, listOf("version")),
            Triple("d8", ToolchainBinary.D8, listOf("--version")),
            Triple("zipalign", ToolchainBinary.ZIPALIGN, emptyList()),
            Triple("apksigner", ToolchainBinary.APKSIGNER, listOf("version"))
        )

        var allPassed = true
        for ((label, binary, args) in tasks) {
            val target = getBinaryPath(binary)
            if (target == null) {
                allPassed = false
                checks[label] = "missing"
                continue
            }

            val command = buildBinaryCommand(target, args)
            if (command == null) {
                allPassed = false
                checks[label] = "missing java runtime for jar tool"
                continue
            }

            val result = runProcess(command, timeoutMs)
            val isZipalignUsageOutput =
                label == "zipalign" && result.second.contains("Zip alignment utility", ignoreCase = true)
            val passed = result.first || isZipalignUsageOutput

            checks[label] = if (passed) {
                result.second.ifBlank { "ok" }
            } else {
                allPassed = false
                "fail: ${result.second.ifBlank { "unknown" }}"
            }
        }

        ToolchainSelfTestResult(success = allPassed, checks = checks)
    }

    fun getStatusInfo(): Map<String, Any> {
        val statuses = ToolchainBinary.values().associate { binary ->
            val f = getBinaryPath(binary)
            binary.name.lowercase() to mapOf(
                "present" to (f != null),
                "path" to (f?.absolutePath ?: ""),
                "size" to (f?.length() ?: 0L)
            )
        }

        return mapOf(
            "initialized" to initialized,
            "abi" to currentAbi(),
            "root" to toolchainRoot.absolutePath,
            "verifyStatus" to lastVerifyResult.status.name,
            "verifyMessage" to lastVerifyResult.message,
            "verifyDetails" to lastVerifyResult.details,
            "tools" to statuses
        )
    }

    private fun verifyInstalledToolchain(): Boolean {
        val abiDir = getAbiDir()
        val manifestFile = File(abiDir, MANIFEST_FILE)
        if (!manifestFile.exists()) {
            lastVerifyResult = ToolchainVerifyResult(
                status = ToolchainVerifyStatus.MISSING,
                message = "Missing $MANIFEST_FILE in ${abiDir.absolutePath}"
            )
            return false
        }

        val manifest = loadManifestFromFile(manifestFile)
        if (manifest == null) {
            lastVerifyResult = ToolchainVerifyResult(
                status = ToolchainVerifyStatus.INVALID_MANIFEST,
                message = "Cannot parse $MANIFEST_FILE"
            )
            return false
        }

        val versionOk = File(toolchainRoot, VERSION_FILE).let { vf ->
            vf.exists() && vf.readText(Charsets.UTF_8).trim() == "${manifest.version}|${manifest.abi}"
        }

        if (!versionOk) {
            lastVerifyResult = ToolchainVerifyResult(
                status = ToolchainVerifyStatus.MISSING,
                message = "Toolchain version marker is missing or outdated"
            )
            return false
        }

        val verify = verifyExtractedToolchain(getAbiDir(), manifest)
        lastVerifyResult = verify
        return verify.status == ToolchainVerifyStatus.OK
    }

    private fun installOrUpdateFromAssets() {
        val abi = currentAbi()
        val abiAssetPath = "$ASSET_ROOT/$abi"

        val assetItems = runCatching { context.assets.list(abiAssetPath)?.toList().orEmpty() }
            .getOrElse { emptyList() }

        if (assetItems.isEmpty()) {
            lastVerifyResult = ToolchainVerifyResult(
                status = ToolchainVerifyStatus.INCOMPATIBLE_ABI,
                message = "No packaged toolchain for ABI: $abi",
                details = listOf("Expected assets under $abiAssetPath")
            )
            return
        }

        if (toolchainRoot.exists()) {
            toolchainRoot.deleteRecursively()
        }
        toolchainRoot.mkdirs()

        val abiDir = getAbiDir()
        copyAssetsRecursive(abiAssetPath, abiDir)

        val manifestFile = File(abiDir, MANIFEST_FILE)
        val manifest = loadManifestFromFile(manifestFile)
        if (manifest == null) {
            abiDir.deleteRecursively()
            lastVerifyResult = ToolchainVerifyResult(
                status = ToolchainVerifyStatus.INVALID_MANIFEST,
                message = "Invalid or missing $MANIFEST_FILE"
            )
            return
        }

        if (!manifest.abi.equals(abi, ignoreCase = true)) {
            abiDir.deleteRecursively()
            lastVerifyResult = ToolchainVerifyResult(
                status = ToolchainVerifyStatus.INCOMPATIBLE_ABI,
                message = "Manifest ABI mismatch: expected=$abi actual=${manifest.abi}"
            )
            return
        }

        // 按 manifest 设可执行位
        manifest.artifacts.filter { it.executable }.forEach { artifact ->
            val file = File(abiDir, artifact.relativePath)
            if (file.exists()) {
                file.setExecutable(true, false)
            }
        }

        val verify = verifyExtractedToolchain(abiDir, manifest)
        if (verify.status != ToolchainVerifyStatus.OK) {
            abiDir.deleteRecursively()
            lastVerifyResult = verify
            return
        }

        File(toolchainRoot, VERSION_FILE).writeText("${manifest.version}|${manifest.abi}", Charsets.UTF_8)
        lastVerifyResult = verify
    }

    private fun verifyExtractedToolchain(abiDir: File, manifest: ToolchainManifest): ToolchainVerifyResult {
        val details = mutableListOf<String>()

        for (artifact in manifest.artifacts) {
            val file = File(abiDir, artifact.relativePath)
            if (!file.exists() || !file.isFile) {
                details += "Missing file: ${artifact.relativePath}"
                continue
            }

            if (artifact.sizeBytes > 0 && file.length() != artifact.sizeBytes) {
                details += "Size mismatch: ${artifact.relativePath} expected=${artifact.sizeBytes} actual=${file.length()}"
                continue
            }

            val actualHash = sha256(file)
            if (!actualHash.equals(artifact.sha256, ignoreCase = true)) {
                details += "SHA256 mismatch: ${artifact.relativePath}"
                continue
            }

            if (artifact.executable && !file.canExecute()) {
                file.setExecutable(true, false)
                if (!file.canExecute()) {
                    details += "Not executable: ${artifact.relativePath}"
                }
            }
        }

        if (details.isNotEmpty()) {
            val hasHashMismatch = details.any { it.startsWith("SHA256 mismatch") }
            val hasExecIssue = details.any { it.startsWith("Not executable") }
            return ToolchainVerifyResult(
                status = when {
                    hasHashMismatch -> ToolchainVerifyStatus.HASH_MISMATCH
                    hasExecIssue -> ToolchainVerifyStatus.NOT_EXECUTABLE
                    else -> ToolchainVerifyStatus.MISSING
                },
                message = "Toolchain verification failed",
                details = details
            )
        }

        val required = listOf(
            ToolchainBinary.AAPT2,
            ToolchainBinary.D8,
            ToolchainBinary.ZIPALIGN,
            ToolchainBinary.APKSIGNER,
            ToolchainBinary.ANDROID_JAR
        )
        val missingRequired = required
            .filter { getBinaryPath(it) == null }
            .map { it.name.lowercase() }
            .toMutableList()

        val javaDependentTools = listOf(
            ToolchainBinary.D8,
            ToolchainBinary.APKSIGNER,
            ToolchainBinary.KOTLINC
        ).mapNotNull { getBinaryPath(it) }

        val needsJavaRuntime = javaDependentTools.any { tool -> toolLikelyNeedsJava(tool) }
        if (needsJavaRuntime && getBinaryPath(ToolchainBinary.JAVA) == null) {
            missingRequired += "java"
        }
        if (missingRequired.isNotEmpty()) {
            return ToolchainVerifyResult(
                status = ToolchainVerifyStatus.MISSING,
                message = "Required binaries missing",
                details = missingRequired
            )
        }

        // When java is a wrapper script delegating to bundled jre/bin/java,
        // fail early if the bundled runtime is absent or not executable.
        val javaBinary = getBinaryPath(ToolchainBinary.JAVA)
        if (javaBinary != null) {
            val javaScript = runCatching { javaBinary.readText(Charsets.UTF_8) }.getOrNull()
            if (!javaScript.isNullOrBlank() && javaScript.contains("jre/bin/java")) {
                val hasSystemFallback = javaScript.contains("/system/bin/java") ||
                    javaScript.contains("/apex/com.android.art/bin/dalvikvm") ||
                    javaScript.contains("/system/bin/dalvikvm")
                if (!hasSystemFallback) {
                    val bundledJava = File(javaBinary.parentFile, "jre/bin/java")
                    if (!bundledJava.exists() || !bundledJava.isFile) {
                        return ToolchainVerifyResult(
                            status = ToolchainVerifyStatus.MISSING,
                            message = "Bundled java runtime missing",
                            details = listOf("jre/bin/java")
                        )
                    }
                    if (!bundledJava.canExecute()) {
                        bundledJava.setExecutable(true, false)
                        if (!bundledJava.canExecute()) {
                            return ToolchainVerifyResult(
                                status = ToolchainVerifyStatus.NOT_EXECUTABLE,
                                message = "Bundled java runtime is not executable",
                                details = listOf("jre/bin/java")
                            )
                        }
                    }
                }
            }
        }

        return ToolchainVerifyResult(
            status = ToolchainVerifyStatus.OK,
            message = "Toolchain verified"
        )
    }

    private fun loadManifestFromFile(file: File): ToolchainManifest? {
        return runCatching {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val schemaVersion = json.optString("schemaVersion", "1.0.0")
            val abi = json.optString("abi")
            val version = json.optString("version")
            val arr = json.optJSONArray("artifacts")
                ?: throw IllegalArgumentException("manifest missing artifacts")

            if (abi.isBlank() || version.isBlank()) {
                throw IllegalArgumentException("manifest abi/version missing")
            }

            val artifacts = buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    add(
                        ToolchainArtifact(
                            name = item.optString("name"),
                            relativePath = item.optString("relativePath"),
                            sha256 = item.optString("sha256"),
                            sizeBytes = item.optLong("sizeBytes", 0L),
                            executable = item.optBoolean("executable", false),
                            sourceUrl = item.optString("sourceUrl"),
                            versionOrCommit = item.optString("versionOrCommit"),
                            license = item.optString("license")
                        )
                    )
                }
            }

            if (artifacts.any { it.name.isBlank() || it.relativePath.isBlank() || it.sha256.isBlank() }) {
                throw IllegalArgumentException("manifest artifacts contain empty required fields")
            }

            ToolchainManifest(
                schemaVersion = schemaVersion,
                abi = abi,
                version = version,
                artifacts = artifacts
            )
        }.onFailure {
            Log.w(TAG, "Failed to parse toolchain manifest: ${it.message}")
        }.getOrNull()
    }

    private suspend fun runProcess(command: List<String>, timeoutMs: Long): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val process = ProcessBuilder(command)
                    .directory(toolchainRoot)
                    .redirectErrorStream(false)
                    .start()

                try {
                    val result = withTimeoutOrNull(timeoutMs) {
                        val outDeferred = async(Dispatchers.IO) {
                            process.inputStream.bufferedReader().use { it.readText() }
                        }
                        val errDeferred = async(Dispatchers.IO) {
                            process.errorStream.bufferedReader().use { it.readText() }
                        }
                        val code = process.waitFor()
                        Triple(code, outDeferred.await().trim(), errDeferred.await().trim())
                    }

                    if (result == null) {
                        process.destroyForcibly()
                        false to "timeout"
                    } else {
                        val success = result.first == 0
                        val message = listOf(result.second, result.third)
                            .firstOrNull { it.isNotBlank() }
                            ?.lineSequence()
                            ?.firstOrNull()
                            ?: "exit=${result.first}"
                        success to message
                    }
                } finally {
                    if (process.isAlive) {
                        process.destroyForcibly()
                    }
                }
            }.getOrElse { false to (it.message ?: "process error") }
        }
    }

    private fun buildBinaryCommand(target: File, args: List<String>): List<String>? {
        if (isShellScript(target)) {
            return listOf(SYSTEM_SH, target.absolutePath) + args
        }

        return if (target.extension.equals("jar", ignoreCase = true)) {
            val javaBinary = getBinaryPath(ToolchainBinary.JAVA) ?: return null
            listOf(javaBinary.absolutePath, "-jar", target.absolutePath) + args
        } else {
            listOf(target.absolutePath) + args
        }
    }

    private fun toolLikelyNeedsJava(tool: File): Boolean {
        if (tool.extension.equals("jar", ignoreCase = true)) return true
        val script = runCatching { tool.readText(Charsets.UTF_8) }.getOrNull() ?: return false
        return script.contains("jre/bin/java") ||
            script.contains("/system/bin/java") ||
            script.contains(" exec java ") ||
            script.contains("\$DIR/java") ||
            script.contains(" -jar ")
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

    private fun copyAssetsRecursive(assetPath: String, targetDir: File) {
        val children = context.assets.list(assetPath) ?: return
        targetDir.mkdirs()

        for (name in children) {
            val childAssetPath = "$assetPath/$name"
            val childList = context.assets.list(childAssetPath)
            val target = File(targetDir, name)

            if (childList.isNullOrEmpty()) {
                context.assets.open(childAssetPath).use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                copyAssetsRecursive(childAssetPath, target)
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun getAbiDir(): File = File(toolchainRoot, currentAbi())

    private fun currentAbi(): String {
        return Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
    }
}
