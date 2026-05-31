package com.rostrum.terminal

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.system.Os
import java.io.File
import java.io.IOException
import java.nio.file.Files

sealed class PRootState {
    data object NotInitialized : PRootState()
    data class Initializing(val progress: Float, val message: String) : PRootState()
    data object Ready : PRootState()
    data class Error(val message: String) : PRootState()
}

sealed class RootfsInstallState {
    data object NotInstalled : RootfsInstallState()
    data class Extracting(val progress: Float) : RootfsInstallState()
    data object Installed : RootfsInstallState()
    data class Error(val message: String) : RootfsInstallState()
}

data class BindMount(val hostPath: String, val guestPath: String)

interface PRootEnvironment {
    val state: StateFlow<PRootState>
    val installState: StateFlow<RootfsInstallState>
    suspend fun initialize(): Result<Unit>
    suspend fun isReady(): Boolean
    fun getHomePath(): String
    fun getNativeLibPath(): String
    suspend fun installRootfs(): Result<Unit>
    suspend fun isRootfsInstalled(): Boolean
    suspend fun getRootfsVersion(): String?
    suspend fun uninstallRootfs(): Result<Unit>
    suspend fun launchProcess(command: String, env: Map<String, String> = emptyMap()): Process
    fun buildPRootCommand(innerCommand: String): List<String>
    fun getBindMounts(): List<BindMount>
}

class PRootEnvironmentImpl(
    private val context: Context
) : PRootEnvironment {

    private val _state = MutableStateFlow<PRootState>(PRootState.NotInitialized)
    override val state: StateFlow<PRootState> = _state

    private val _installState = MutableStateFlow<RootfsInstallState>(RootfsInstallState.NotInstalled)
    override val installState: StateFlow<RootfsInstallState> = _installState

    private val filesDir: File = context.filesDir
    private val usrDir: File = File(filesDir, "usr")
    private val binDir: File = File(usrDir, "bin")
    private val nativeLibDir: String = context.applicationInfo.nativeLibraryDir
    private val ubuntuPath: File = File(usrDir, "var/lib/proot-distro/installed-rootfs/ubuntu")
    private val installMarker: File = File(ubuntuPath, ".omnimaster_installed_ok")
    private val initializeMutex = Mutex()
    private val rootfsInstallMutex = Mutex()
    private var supportsKillOnExit: Boolean = false

    companion object {
        private const val TAG = "PRootEnvironment"
        private const val UBUNTU_FILENAME = "ubuntu-noble-aarch64-pd-v4.18.0.tar.xz"
        private const val MCP_SERVERS_ASSET_DIR = "servers"
        private const val OMNIMASTER_OPT_DIR = "opt/omnimaster"
        private const val MCP_TARGET_DIR = "opt/omnimaster/mcp-servers"
        private const val PER_USER_RANGE = 100000

        private val BUSYBOX_LINKS = listOf(
            "awk", "ash", "basename", "bzip2", "curl", "cp", "chmod", "cut", "cat", "du", "dd",
            "find", "grep", "gzip", "hexdump", "head", "id", "lscpu", "mkdir", "realpath", "rm",
            "sed", "stat", "sh", "tr", "tar", "uname", "xargs", "xz", "xxd"
        )
    }

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        initializeMutex.withLock {
            if (_state.value == PRootState.Ready && isRootfsInstalled()) {
                return@withLock Result.success(Unit)
            }

            _state.value = PRootState.Initializing(0f, "Creating directories...")
            runCatching {
                createDirectories()
                _state.value = PRootState.Initializing(0.05f, "Checking PRoot capabilities...")
                supportsKillOnExit = detectKillOnExitSupport()
                Log.d(TAG, "PRoot --kill-on-exit support: $supportsKillOnExit")

                _state.value = PRootState.Initializing(0.1f, "Linking native libs...")
                linkNativeLibs()
                _state.value = PRootState.Initializing(0.2f, "Creating busybox symlinks...")
                createBusyboxSymlinks()

                if (!isRootfsInstalled()) {
                    _state.value = PRootState.Initializing(0.3f, "Installing rootfs...")
                    installRootfs().getOrThrow()
                }

                _state.value = PRootState.Initializing(0.55f, "Preparing runtime files...")
                ensureRuntimeFiles()

                _state.value = PRootState.Initializing(0.6f, "Checking Node.js environment...")
                runCatching {
                    val nodeCheckProcess = launchProcess("test -f /usr/bin/node")
                    val nodeCheckExit = nodeCheckProcess.waitFor()
                    nodeCheckProcess.destroyAndCloseStreams()
                    if (nodeCheckExit != 0) {
                        Log.i(TAG, "Node.js not found in container, installing...")
                        _state.value = PRootState.Initializing(0.65f, "Installing Node.js...")
                        val installProcess = launchProcess("apt-get update -qq && apt-get install -y -qq nodejs npm")
                        val installExitCode = installProcess.waitFor()
                        installProcess.destroyAndCloseStreams()
                        if (installExitCode == 0) {
                            Log.i(TAG, "Node.js installed successfully")
                        } else {
                            Log.w(TAG, "Node.js installation failed with exit code $installExitCode")
                        }
                    } else {
                        Log.i(TAG, "Node.js is already available in container")
                    }
                }.onFailure {
                    Log.w(TAG, "Failed to check or install Node.js", it)
                }

                _state.value = PRootState.Initializing(0.75f, "Checking OpenCode...")
                runCatching {
                    val ocCheckProcess = launchProcess("test -f /usr/local/bin/opencode")
                    val ocCheckExit = ocCheckProcess.waitFor()
                    ocCheckProcess.destroyAndCloseStreams()
                    if (ocCheckExit != 0) {
                        Log.i(TAG, "OpenCode not found, attempting to install from local packages...")
                        val installScript = File(ubuntuPath, "opt/opencode-pkgs/install.sh")
                        if (installScript.exists()) {
                            val ocInstallProcess = launchProcess("bash /opt/opencode-pkgs/install.sh")
                            val ocInstallExit = ocInstallProcess.waitFor()
                            ocInstallProcess.destroyAndCloseStreams()
                            if (ocInstallExit == 0) {
                                Log.i(TAG, "OpenCode installed successfully")
                            } else {
                                Log.w(TAG, "OpenCode installation failed with exit code $ocInstallExit")
                            }
                        } else {
                            Log.w(TAG, "OpenCode install script not found at /opt/opencode-pkgs/install.sh")
                        }
                    } else {
                        Log.i(TAG, "OpenCode is already available")
                    }
                }.onFailure {
                    Log.w(TAG, "Failed to check or install OpenCode", it)
                }

                _state.value = PRootState.Initializing(0.8f, "Installing bundled services...")
                installBundledMcpServers()
                ensureWorkspace()

                _state.value = PRootState.Initializing(0.9f, "Generating startup script...")
                generateStartScript()

                // 应用运行时 PRoot 网络测试（优先级1：验证应用进程启动的 PRoot 子进程是否能联网）
                _state.value = PRootState.Initializing(0.95f, "Testing network connectivity...")
                testNetworkConnectivity()

                _state.value = PRootState.Ready
            }.onFailure {
                _state.value = PRootState.Error(it.message ?: "Unknown error")
            }
        }
    }

    override suspend fun isReady(): Boolean = _state.value == PRootState.Ready

    override fun getHomePath(): String = filesDir.absolutePath

    override fun getNativeLibPath(): String = nativeLibDir

    override suspend fun installRootfs(): Result<Unit> = withContext(Dispatchers.IO) {
        rootfsInstallMutex.withLock {
            runCatching {
                _installState.value = RootfsInstallState.Extracting(0f)
                val tmpDir = File(usrDir, "var/lib/proot-distro/installed-rootfs/.tmp_ubuntu")
                if (tmpDir.exists()) tmpDir.deleteRecursively()
                tmpDir.mkdirs()

                val tarXzFile = File(filesDir, "tmp/$UBUNTU_FILENAME")
                tarXzFile.parentFile?.mkdirs()
                context.assets.open(UBUNTU_FILENAME).use { assetInputStream ->
                    tarXzFile.outputStream().use { out ->
                        assetInputStream.copyTo(out)
                    }
                }

                _installState.value = RootfsInstallState.Extracting(0.3f)

                val extractCmd = arrayOf(
                    File(binDir, "busybox").absolutePath,
                    "tar", "xf", tarXzFile.absolutePath, "-C", tmpDir.absolutePath
                )
                val extractProcess = Runtime.getRuntime().exec(extractCmd)
                val exitCode = extractProcess.waitFor()
                if (exitCode != 0) throw RuntimeException("tar extraction failed with exit code $exitCode")

                _installState.value = RootfsInstallState.Extracting(0.8f)

                normalizeExtractedRootfs(tmpDir)

                tarXzFile.delete()

                // 双重写入 resolv.conf：rootfs 内部 + bind-mount 源目录
                // PRoot 网络问题的已知修复方案（参考 openclaw-termux #45）
                ensureRuntimeFiles()

                val hosts = File(ubuntuPath, "etc/hosts")
                hosts.writeText("127.0.0.1\tlocalhost\n127.0.1.1\tubuntu\n")

                val hostname = File(ubuntuPath, "etc/hostname")
                hostname.writeText("ubuntu\n")

                installMarker.createNewFile()

                _installState.value = RootfsInstallState.Installed
            }.onFailure {
                _installState.value = RootfsInstallState.Error(it.message ?: "Unknown error")
            }
        }
    }

    override suspend fun isRootfsInstalled(): Boolean = installMarker.exists()

    override suspend fun getRootfsVersion(): String? {
        if (!isRootfsInstalled()) return null
        return "ubuntu-noble-aarch64-pd-v4.18.0"
    }

    override suspend fun uninstallRootfs(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (ubuntuPath.exists()) ubuntuPath.deleteRecursively()
            _installState.value = RootfsInstallState.NotInstalled
            _state.value = PRootState.NotInitialized
        }
    }

    override suspend fun launchProcess(
        command: String,
        env: Map<String, String>
    ): Process = withContext(Dispatchers.IO) {
        // 每次启动 PRoot 前确保 resolv.conf 最新（修复 DNS 问题的关键）
        ensureRuntimeFiles()
        val cmdList = buildPRootCommand(command)
        val pb = ProcessBuilder(cmdList)
        pb.environment().putAll(buildEnvironment())
        pb.environment().putAll(env)
        pb.directory(filesDir)
        pb.redirectErrorStream(true)
        pb.start()
    }

    override fun buildPRootCommand(innerCommand: String): List<String> {
        val prootBin = File(binDir, "proot").absolutePath
        // 注意：不使用 -0 (fake root)，因为 Android 的 seccomp 过滤器会拒绝
        // UID 与真实进程 UID 不匹配的网络系统调用（sendmsg/connect 等）。
        // PRoot 以应用进程 UID 运行，bind mount 的文件系统访问通过 PRoot 的
        // 路径转换实现，不需要 fake root。
        val cmd = mutableListOf(prootBin, "-r", ubuntuPath.absolutePath, "--link2symlink")
        if (supportsKillOnExit) {
            cmd.add("--kill-on-exit")
        }
        for (mount in getBindMounts()) {
            cmd.add("-b")
            if (mount.hostPath == mount.guestPath) {
                cmd.add(mount.hostPath)
            } else {
                cmd.add("${mount.hostPath}:${mount.guestPath}")
            }
        }
        cmd.addAll(listOf("-w", "/"))
        cmd.addAll(listOf(
            "/usr/bin/env", "-i",
            "HOME=/root",
            "PWD=/",
            "TERM=xterm-256color",
            "LANG=en_US.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "/bin/bash", "-lc", innerCommand
        ))
        return cmd
    }

    override fun getBindMounts(): List<BindMount> {
        val userId = android.os.Process.myUid() / PER_USER_RANGE
        val emulatedStorage = "/storage/emulated/$userId"
        val appDataDir = context.applicationInfo.dataDir
        val packageName = context.packageName
        val homeDir = filesDir.absolutePath
        val userDataRoot = "/data/user/$userId"

        return buildList {
            add(BindMount("/dev", "/dev"))
            add(BindMount("/proc", "/proc"))
            add(BindMount("/sys", "/sys"))
            add(BindMount("/dev/pts", "/dev/pts"))
            add(BindMount(emulatedStorage, "/sdcard"))
            add(BindMount(emulatedStorage, emulatedStorage))
            add(BindMount("/data/local/tmp", "/data/local/tmp"))
            add(BindMount(appDataDir, "$userDataRoot/$packageName"))
            add(BindMount(appDataDir, "/data/data/$packageName"))
            add(BindMount(homeDir, homeDir))
        }
    }

    private fun buildEnvironment(): Map<String, String> = mapOf(
        "PATH" to "$binDir:${System.getenv("PATH") ?: ""}",
        "HOME" to filesDir.absolutePath,
        "PREFIX" to usrDir.absolutePath,
        "LD_LIBRARY_PATH" to "$nativeLibDir:$binDir",
        "PROOT_LOADER" to File(binDir, "loader").absolutePath,
        "TMPDIR" to File(filesDir, "tmp").absolutePath,
        "PROOT_TMP_DIR" to File(filesDir, "tmp").absolutePath,
        "PROOT_NO_SECCOMP" to "1",
        "TERM" to "xterm-256color",
        "LANG" to "en_US.UTF-8"
    )

    /**
     * Destroys the process and eagerly closes its I/O streams to avoid leaking
     * file descriptors on Android.
     */
    private fun Process.destroyAndCloseStreams() {
        try { outputStream.close() } catch (_: Exception) {}
        try { inputStream.close() } catch (_: Exception) {}
        try { errorStream.close() } catch (_: Exception) {}
        destroy()
    }

    /**
     * Detect whether the bundled PRoot binary supports `--kill-on-exit`.
     * We test by running `proot --kill-on-exit --help`; if the exit code is 0
     * the option is accepted.
     */
    private fun detectKillOnExitSupport(): Boolean {
        return try {
            val pb = ProcessBuilder(
                File(binDir, "proot").absolutePath,
                "--kill-on-exit",
                "--help"
            )
            pb.redirectErrorStream(true)
            pb.environment().putAll(buildEnvironment())
            pb.directory(filesDir)
            val process = pb.start()
            val exitCode = process.waitFor()
            process.destroyAndCloseStreams()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun createDirectories() {
        usrDir.mkdirs()
        binDir.mkdirs()
        File(filesDir, "tmp").mkdirs()
    }

    private fun ensureRuntimeFiles() {
        ensureTmpDirectory()
        ensureResolvConf()
        ensureAndroidUserDatabase()
        ensureWorkspace()
    }

    private fun ensureTmpDirectory() {
        listOf(
            File(filesDir, "tmp"),
            File(ubuntuPath, "tmp"),
            File(ubuntuPath, "var/tmp")
        ).forEach { dir ->
            dir.mkdirs()
            runCatching { Os.chmod(dir.absolutePath, 0x1FF or 0x200) } // 01777
                .onFailure {
                    dir.setReadable(true, false)
                    dir.setWritable(true, false)
                    dir.setExecutable(true, false)
                }
        }
    }

    private fun ensureAndroidUserDatabase() {
        val uid = android.os.Process.myUid()
        val gid = android.os.Process.myUid()
        val passwd = File(ubuntuPath, "etc/passwd")
        val group = File(ubuntuPath, "etc/group")
        passwd.parentFile?.mkdirs()
        group.parentFile?.mkdirs()

        if (!passwd.exists()) {
            passwd.writeText("root:x:0:0:root:/root:/bin/bash\n")
        }
        val passwdText = passwd.readText()
        if (!passwdText.lineSequence().any { it.split(':').getOrNull(2) == uid.toString() }) {
            passwd.appendText("android:x:$uid:$gid:Android App User:/root:/bin/bash\n")
        }

        if (!group.exists()) {
            group.writeText("root:x:0:\n")
        }
        val groupText = group.readText()
        if (!groupText.lineSequence().any { it.split(':').getOrNull(2) == gid.toString() }) {
            group.appendText("android:x:$gid:\n")
        }
    }

    private fun normalizeExtractedRootfs(tmpDir: File) {
        val entries = tmpDir.listFiles()
            ?.filter { it.name != "." && it.name != ".." && it.name != "__MACOSX" }
            .orEmpty()

        val extractedRoot = when {
            File(tmpDir, "bin").isDirectory && File(tmpDir, "usr").isDirectory -> tmpDir
            entries.size == 1 && entries.first().isDirectory -> entries.first()
            else -> throw IOException("Unexpected rootfs archive layout: ${entries.joinToString { it.name }}")
        }

        if (ubuntuPath.exists()) ubuntuPath.deleteRecursively()
        ubuntuPath.parentFile?.mkdirs()

        if (!extractedRoot.renameTo(ubuntuPath)) {
            extractedRoot.copyRecursively(ubuntuPath, overwrite = true)
            extractedRoot.deleteRecursively()
        }
        if (tmpDir.exists() && tmpDir != ubuntuPath) {
            tmpDir.deleteRecursively()
        }

        if (!File(ubuntuPath, "bin").isDirectory || !File(ubuntuPath, "usr").isDirectory) {
            throw IOException("Rootfs install is incomplete: ${ubuntuPath.absolutePath}")
        }
    }

    private fun installBundledMcpServers() {
        val serverNames = context.assets.list(MCP_SERVERS_ASSET_DIR)?.toList().orEmpty()
        if (serverNames.isEmpty()) {
            Log.w(TAG, "No bundled MCP servers found in assets/$MCP_SERVERS_ASSET_DIR")
            return
        }

        val targetRoot = File(ubuntuPath, MCP_TARGET_DIR)
        val sourceRoot = File(ubuntuPath, "$OMNIMASTER_OPT_DIR/linux-mcp/servers")
        targetRoot.mkdirs()
        sourceRoot.mkdirs()
        for (serverName in serverNames) {
            val assetPath = "$MCP_SERVERS_ASSET_DIR/$serverName"
            copyAssetTree(assetPath = assetPath, target = File(targetRoot, serverName))
            copyAssetTree(assetPath = assetPath, target = File(sourceRoot, serverName))
        }

        val linuxMcpDir = File(ubuntuPath, "$OMNIMASTER_OPT_DIR/linux-mcp")
        copyAssetFileIfExists("setup.sh", File(linuxMcpDir, "setup.sh"), executable = true)
        copyAssetFileIfExists("start-servers.sh", File(linuxMcpDir, "start-servers.sh"), executable = true)
    }

    private fun ensureWorkspace() {
        File(ubuntuPath, "root/.omnimaster/workspace").mkdirs()
    }

    private fun copyAssetTree(assetPath: String, target: File) {
        val children = context.assets.list(assetPath)?.toList().orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }

        target.mkdirs()
        for (child in children) {
            copyAssetTree("$assetPath/$child", File(target, child))
        }
    }

    private fun copyAssetFileIfExists(assetPath: String, target: File, executable: Boolean = false) {
        runCatching {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (executable) target.setExecutable(true)
        }.onFailure {
            Log.w(TAG, "Optional asset $assetPath is not available", it)
        }
    }

    private fun linkNativeLibs() {
        val libs = mapOf(
            "libproot.so" to "proot",
            "libloader.so" to "loader",
            "liblibtalloc.so.2.so" to "libtalloc.so.2",
            "libbash.so" to "bash",
            "libbusybox.so" to "busybox"
        )
        for ((soName, linkName) in libs) {
            val target = File(nativeLibDir, soName)
            val link = File(binDir, linkName)
            createSymbolicLink(target, link)
        }
    }

    private fun createBusyboxSymlinks() {
        val busybox = File(binDir, "busybox")
        for (linkName in BUSYBOX_LINKS) {
            val link = File(binDir, linkName)
            createSymbolicLink(busybox, link)
        }
    }

    private fun createSymbolicLink(target: File, link: File) {
        try {
            if (link.exists() || Files.isSymbolicLink(link.toPath())) {
                link.delete()
            }
            Files.createSymbolicLink(link.toPath(), target.toPath())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create symlink ${link.name} -> ${target.absolutePath}", e)
        }
    }

    /**
     * 确保 resolv.conf 存在且配置正确。
     * 写入两个位置：rootfs 内部路径（直接 fallback）和外部 bind-mount 源路径。
     * 这是修复 PRoot DNS 问题的关键措施。
     */
    fun ensureResolvConf() {
        val content = "nameserver 8.8.8.8\nnameserver 8.8.4.4\nnameserver 1.1.1.1\n"
        // 1. rootfs 内部直接路径
        val resolvInternal = File(ubuntuPath, "etc/resolv.conf")
        resolvInternal.parentFile?.mkdirs()
        resolvInternal.writeText(content)
        // 2. bind-mount 源路径（如果后续添加 resolv.conf 的 bind mount）
        val resolvExternal = File(filesDir, "config/resolv.conf")
        resolvExternal.parentFile?.mkdirs()
        resolvExternal.writeText(content)
    }

    /**
     * 测试 PRoot 内网络连通性。
     * 将结果写入 filesDir/network_test.log，供 adb 读取分析。
     */
    private suspend fun testNetworkConnectivity() = withContext(Dispatchers.IO) {
        val logFile = File(filesDir, "network_test.log")
        val results = StringBuilder()
        results.appendLine("=== PRoot Network Test ===")
        results.appendLine("Timestamp: ${System.currentTimeMillis()}")
        results.appendLine("")

        // 1. DNS 解析测试
        results.appendLine("--- DNS Test (nslookup baidu.com) ---")
        runCatching {
            val process = launchProcess("nslookup baidu.com 2>&1 || true")
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            process.destroyAndCloseStreams()
            results.appendLine("exitCode=$exitCode")
            results.appendLine(output)
        }.onFailure {
            results.appendLine("ERROR: ${it.message}")
        }
        results.appendLine("")

        // 2. HTTP 外网访问测试
        results.appendLine("--- HTTP Test (curl http://www.baidu.com) ---")
        runCatching {
            val process = launchProcess("curl -s -o /dev/null -w \"%{http_code}\" http://www.baidu.com -m 10 2>&1")
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            process.destroyAndCloseStreams()
            results.appendLine("exitCode=$exitCode")
            results.appendLine("responseCode=$output")
        }.onFailure {
            results.appendLine("ERROR: ${it.message}")
        }
        results.appendLine("")

        // 3. HTTPS 访问 OpenCode
        results.appendLine("--- HTTPS Test (curl https://opencode.ai) ---")
        runCatching {
            val process = launchProcess("curl -s -o /dev/null -w \"%{http_code}\" https://opencode.ai -m 15 2>&1")
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            process.destroyAndCloseStreams()
            results.appendLine("exitCode=$exitCode")
            results.appendLine("responseCode=$output")
        }.onFailure {
            results.appendLine("ERROR: ${it.message}")
        }
        results.appendLine("")

        // 4. Ping 8.8.8.8 (ICMP 可能被拒绝，仅作参考)
        results.appendLine("--- Ping Test (ping -c 1 8.8.8.8) ---")
        runCatching {
            val process = launchProcess("ping -c 1 -W 5 8.8.8.8 2>&1 || true")
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            process.destroyAndCloseStreams()
            results.appendLine("exitCode=$exitCode")
            results.appendLine(output)
        }.onFailure {
            results.appendLine("ERROR: ${it.message}")
        }
        results.appendLine("")

        // 5. 检查 /etc/resolv.conf 内容
        results.appendLine("--- resolv.conf contents ---")
        runCatching {
            val process = launchProcess("cat /etc/resolv.conf 2>&1 || true")
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            process.destroyAndCloseStreams()
            results.appendLine(output)
        }.onFailure {
            results.appendLine("ERROR: ${it.message}")
        }
        results.appendLine("")

        // 6. 检查 IP 路由表
        results.appendLine("--- IP Route ---")
        runCatching {
            val process = launchProcess("ip route 2>&1 || route -n 2>&1 || true")
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            process.destroyAndCloseStreams()
            results.appendLine(output)
        }.onFailure {
            results.appendLine("ERROR: ${it.message}")
        }

        results.appendLine("=== End of Network Test ===")
        logFile.writeText(results.toString())
        Log.i(TAG, "Network test results written to ${logFile.absolutePath}")
    }

    /**
     * 测试 OpenCode API 调用。
     * 运行一次简单的 opencode run，将完整输出写入日志文件。
     */
    private suspend fun testOpenCodeApi() = withContext(Dispatchers.IO) {
        val logFile = File(filesDir, "opencode_api_test.log")
        val results = StringBuilder()
        results.appendLine("=== OpenCode API Test ===")
        results.appendLine("Timestamp: ${System.currentTimeMillis()}")
        results.appendLine("")

        // 诊断 1: node --version
        results.appendLine("--- Diag 1: node --version ---")
        runCatching {
            val process = launchProcess("node --version 2>&1 || echo EXIT_CODE_$?")
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            process.destroyAndCloseStreams()
            results.appendLine("exitCode=$exitCode")
            results.appendLine(output)
        }.onFailure { results.appendLine("ERROR: ${it.message}") }
        results.appendLine("")

        // 诊断 2: which node + file node
        results.appendLine("--- Diag 2: which node && file node ---")
        runCatching {
            val process = launchProcess("which node && file \$(which node) 2>&1 || echo EXIT_CODE_$?")
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            process.destroyAndCloseStreams()
            results.appendLine("exitCode=$exitCode")
            results.appendLine(output)
        }.onFailure { results.appendLine("ERROR: ${it.message}") }
        results.appendLine("")

        // 诊断 3: ldd node
        results.appendLine("--- Diag 3: ldd node ---")
        runCatching {
            val process = launchProcess("ldd \$(which node) 2>&1 || echo EXIT_CODE_$?")
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            process.destroyAndCloseStreams()
            results.appendLine("exitCode=$exitCode")
            results.appendLine(output)
        }.onFailure { results.appendLine("ERROR: ${it.message}") }
        results.appendLine("")

        // 诊断 4: Python 能否运行（关键：MCP 服务器可能用 Python 实现）
        results.appendLine("--- Diag 4: python --version ---")
        runCatching {
            val process = launchProcess("python --version 2>&1 || python3 --version 2>&1 || /root/.local/share/uv/python/cpython-3.13.13-linux-aarch64-gnu/bin/python3.13 --version 2>&1 || echo PY_EXIT_$?")
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            process.destroyAndCloseStreams()
            results.appendLine("exitCode=$exitCode")
            results.appendLine(output)
        }.onFailure { results.appendLine("ERROR: ${it.message}") }
        results.appendLine("")

        // 诊断 5: 简单 Python 脚本
        results.appendLine("--- Diag 5: python -c 'print(1+1)' ---")
        runCatching {
            val process = launchProcess("python -c 'print(1+1)' 2>&1 || python3 -c 'print(1+1)' 2>&1 || /root/.local/share/uv/python/cpython-3.13.13-linux-aarch64-gnu/bin/python3.13 -c 'print(1+1)' 2>&1 || echo PY2_EXIT_$?")
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            process.destroyAndCloseStreams()
            results.appendLine("exitCode=$exitCode")
            results.appendLine(output)
        }.onFailure { results.appendLine("ERROR: ${it.message}") }
        results.appendLine("")

        val opencodeBin = "/usr/local/bin/opencode"
        val testMessage = "Say hi in one word"
        val model = "opencode/nemotron-3-super-free"
        val command = "$opencodeBin run '${testMessage.replace("'", "'\"'\"'")}' --model $model --format json --thinking --dangerously-skip-permissions"
        results.appendLine("Command: $command")
        results.appendLine("")

        runCatching {
            val process = launchProcess(command)
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            process.destroyAndCloseStreams()

            results.appendLine("exitCode=$exitCode")
            results.appendLine("--- STDOUT/STDERR ---")
            results.appendLine(output)
        }.onFailure {
            results.appendLine("ERROR: ${it.message}")
            Log.e(TAG, "OpenCode API test failed", it)
        }

        results.appendLine("=== End of OpenCode API Test ===")
        logFile.writeText(results.toString())
        Log.i(TAG, "OpenCode API test results written to ${logFile.absolutePath}")
    }

    private fun generateStartScript() {
        val scriptFile = File(filesDir, "common.sh")
        val binPath = binDir.absolutePath
        val homePath = filesDir.absolutePath
        val ubuntuPathStr = ubuntuPath.absolutePath

        // Build bind-mount arguments to match getBindMounts()
        val userId = android.os.Process.myUid() / PER_USER_RANGE
        val emulatedStorage = "/storage/emulated/$userId"
        val appDataDir = context.applicationInfo.dataDir
        val packageName = context.packageName
        val userDataRoot = "/data/user/$userId"

        val bindMounts = listOf(
            "/dev:/dev",
            "/proc:/proc",
            "/sys:/sys",
            "/dev/pts:/dev/pts",
            "$emulatedStorage:/sdcard",
            "$emulatedStorage:$emulatedStorage",
            "/data/local/tmp:/data/local/tmp",
            "$appDataDir:$userDataRoot/$packageName",
            "$appDataDir:/data/data/$packageName",
            "$homePath:$homePath"
        )
        val mountArgs = bindMounts.joinToString(" ") { "-b $it" }

        scriptFile.writeText("""
#!/bin/bash
# WARNING: This script is auto-generated for debugging only.
# Use the app APIs for production usage.

export TMPDIR="$homePath/tmp"
export BIN="$binPath"
export HOME="$homePath"
export UBUNTU_PATH="$ubuntuPathStr"

install_ubuntu() {
    if [ -f "$ubuntuPathStr/.omnimaster_installed_ok" ]; then
        return 0
    fi
    echo "ERROR: rootfs not installed"
    return 1
}

login_ubuntu() {
    local COMMAND_TO_EXEC="${'$'}{1:-}"
    $binPath/proot \
        -r "$ubuntuPathStr" \
        --link2symlink \
        $mountArgs \
        -w / \
        /usr/bin/env -i \
            HOME=/root \
            PWD=/ \
            TERM=xterm-256color \
            LANG=en_US.UTF-8 \
            PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
            COMMAND_TO_EXEC="${'$'}COMMAND_TO_EXEC" \
            /bin/bash -lc 'echo LOGIN_SUCCESSFUL; echo TERMINAL_READY; eval "${'$'}COMMAND_TO_EXEC"'
}

start_shell() {
    install_ubuntu || return 1
    login_ubuntu "${'$'}@"
}
""".trimIndent() + "\n")
        scriptFile.setExecutable(true)
    }
}
