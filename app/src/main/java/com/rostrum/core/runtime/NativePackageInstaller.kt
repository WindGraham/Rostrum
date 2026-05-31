package com.rostrum.core.runtime

import android.content.Context
import android.util.Log
import com.rostrum.core.shell.python.PythonRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * 原生 Python 包安装器
 * 
 * 处理含 C 扩展的 Python 包（如 Pillow、NumPy 等）在 Android 上的安装。
 * 
 * Android 设备没有 C 编译器，无法通过 pip 编译安装含原生代码的包。
 * 此安装器通过以下方式解决：
 * 
 * 1. **内置预编译包**：从 assets/python_packages/ 目录安装预编译的 .whl 文件
 * 2. **Chaquopy 仓库**：从 Chaquopy 开源 Android Python 包仓库下载用 NDK 编译的 wheel
 *    （使用 Bionic libc，是 Android 上最可靠的 C 扩展包来源）
 * 3. **PyPI manylinux 降级**：尝试从 PyPI 下载 manylinux_aarch64 wheel（部分包可能兼容）
 * 4. **自动 .so 权限处理**：提取 wheel 后自动为 .so 文件设置可执行权限
 * 
 * Chaquopy (https://chaquo.com/pypi-13.1/) 维护了 100+ 个用 Android NDK 编译的包，
 * 包含 Pillow、NumPy、pandas、OpenCV、lxml、cryptography 等常用库。
 * 这些 wheel 使用 Bionic libc，可以直接在 Android 上加载。
 * 
 * 目标平台：
 * - Python: cpython-3.14（优先使用 cp313 wheel，CPython ABI 通常向前兼容）
 * - Architecture: arm64-v8a (aarch64)
 */
class NativePackageInstaller(
    private val context: Context,
    private val pythonRuntime: PythonRuntime
) {
    companion object {
        private const val TAG = "NativePackageInstaller"
        
        // assets 中预编译包的目录
        private const val ASSETS_PACKAGES_DIR = "python_packages"
        
        /**
         * Chaquopy 开源 Android Python 包仓库
         * 
         * Chaquopy 维护了 100+ 个用 Android NDK 编译的 Python 包（使用 Bionic libc），
         * 是目前最可靠的 Android Python C 扩展包来源。
         * 包含: Pillow, NumPy, pandas, OpenCV, lxml, cryptography 等
         */
        private const val CHAQUOPY_INDEX_URL = "https://chaquo.com/pypi-13.1/"
        
        /**
         * 下载策略定义
         */
        data class DownloadStrategy(
            val name: String,
            val extraIndexUrl: String?,
            val platform: String,
            val pythonVersion: String,
            val abi: String
        )
        
        /**
         * 按优先级排列的下载策略列表
         * 
         * 1. 优先从 Chaquopy 下载 Android NDK 编译的 wheel（Bionic libc，最可靠）
         * 2. 降级尝试 PyPI manylinux wheel（glibc，部分简单包可能兼容）
         */
        private val DOWNLOAD_STRATEGIES = listOf(
            // === Chaquopy: Android NDK 编译（Bionic libc，最佳选择）===
            DownloadStrategy("Chaquopy/cp313", CHAQUOPY_INDEX_URL, "android_24_arm64_v8a", "3.13", "cp313"),
            DownloadStrategy("Chaquopy/cp312", CHAQUOPY_INDEX_URL, "android_24_arm64_v8a", "3.12", "cp312"),
            DownloadStrategy("Chaquopy/cp311", CHAQUOPY_INDEX_URL, "android_24_arm64_v8a", "3.11", "cp311"),
            DownloadStrategy("Chaquopy/abi3",  CHAQUOPY_INDEX_URL, "android_24_arm64_v8a", "3.13", "abi3"),
            // === PyPI: manylinux aarch64（glibc，降级方案）===
            DownloadStrategy("PyPI/cp314",     null, "manylinux_2_28_aarch64", "3.14", "cp314"),
            DownloadStrategy("PyPI/cp313",     null, "manylinux_2_28_aarch64", "3.13", "cp313"),
            DownloadStrategy("PyPI/cp314-m17", null, "manylinux_2_17_aarch64", "3.14", "cp314"),
            DownloadStrategy("PyPI/cp313-m17", null, "manylinux_2_17_aarch64", "3.13", "cp313"),
            DownloadStrategy("PyPI/abi3",      null, "manylinux_2_28_aarch64", "3.14", "abi3"),
        )
        
        /**
         * 已知的含 C 扩展的包名映射（小写名 -> PyPI 包名）
         * 
         * 这些包在标准 pip 安装中会因缺少编译器而失败。
         */
        val NATIVE_PACKAGES = mapOf(
            // 图像处理
            "pillow" to "Pillow",
            "pil" to "Pillow",
            
            // 数值计算
            "numpy" to "numpy",
            "scipy" to "scipy",
            
            // 数据处理
            "pandas" to "pandas",
            
            // 可视化
            "matplotlib" to "matplotlib",
            
            // XML
            "lxml" to "lxml",
            
            // 加密
            "cryptography" to "cryptography",
            "cffi" to "cffi",
            
            // 系统
            "psutil" to "psutil",
            "greenlet" to "greenlet",
            
            // 序列化
            "ujson" to "ujson",
            "msgpack" to "msgpack",
            "orjson" to "orjson",
            
            // 数据格式
            "pyyaml" to "PyYAML",
            "yaml" to "PyYAML",
            "markupsafe" to "MarkupSafe",
            
            // Data processing
            "tokenizers" to "tokenizers",
            "regex" to "regex",
            
            // 网络
            "aiohttp" to "aiohttp",
            "yarl" to "yarl",
            "multidict" to "multidict",
            "frozenlist" to "frozenlist",
            
            // 其他常用
            "charset-normalizer" to "charset_normalizer",
            "charset_normalizer" to "charset_normalizer"
        )
    }
    
    // 下载缓存目录
    private val cacheDir: File
        get() = File(context.cacheDir, "native_wheels").also { it.mkdirs() }
    
    // site-packages 目录
    private val sitePackages: File
        get() = File(pythonRuntime.getStdlibSitePackages())
    
    // 用户 site-packages（pip --user 安装目录）
    private val userSitePackages: File
        get() = File(pythonRuntime.getUserSitePackages())
    
    /**
     * 安装结果
     */
    data class InstallResult(
        val success: Boolean,
        val installedPackages: List<String> = emptyList(),
        val failedPackages: List<String> = emptyList(),
        val message: String = "",
        val details: String = ""
    )
    
    /**
     * 检查包是否是已知的含 C 扩展的包
     */
    fun isNativePackage(packageName: String): Boolean {
        val baseName = extractBaseName(packageName)
        return NATIVE_PACKAGES.containsKey(baseName)
    }
    
    /**
     * 检查原生包是否已安装
     */
    fun isInstalled(packageName: String): Boolean {
        val baseName = extractBaseName(packageName)
        val pypiName = NATIVE_PACKAGES[baseName] ?: baseName
        val dirName = pypiName.lowercase().replace("-", "_")
        
        return listOf(sitePackages, userSitePackages).any { spDir ->
            File(spDir, dirName).exists() ||
            File(spDir, pypiName).exists() ||
            // Pillow 特例：安装为 PIL 目录
            (baseName == "pillow" || baseName == "pil") && File(spDir, "PIL").exists()
        }
    }
    
    /**
     * 安装一组包
     * 
     * 安装策略（按优先级）：
     * 1. 检查 assets/python_packages/ 中的内置 wheel
     * 2. 从 Chaquopy 仓库下载 Android NDK 编译的 wheel（最可靠）
     * 3. 从 PyPI 下载 manylinux_aarch64 wheel（降级方案）
     * 4. 手动解压 wheel 到 site-packages（绕过 pip 平台检查）
     */
    suspend fun installPackages(
        packages: List<String>,
        upgrade: Boolean = false
    ): InstallResult = withContext(Dispatchers.IO) {
        val installed = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val details = StringBuilder()
        
        // 先修复已安装包的 .so（补丁 ELF NEEDED、重命名后缀等）
        // 这是幂等操作，确保之前安装的包也能正常加载
        repairInstalledPackages()
        
        // 确保 pip 可用（pip download 依赖它）
        pythonRuntime.ensurePipInstalled()
        
        for (pkg in packages) {
            val baseName = extractBaseName(pkg)
            
            if (!upgrade && isInstalled(baseName)) {
                installed.add(baseName)
                details.appendLine("✓ $baseName: 已安装，跳过")
                continue
            }
            
            details.appendLine("→ $baseName: 开始安装...")
            val result = installSinglePackage(baseName, pkg, details)
            if (result) {
                // 安装后验证 import
                val importOk = verifyImport(baseName)
                if (importOk) {
                    installed.add(baseName)
                    details.appendLine("✓ $baseName: 安装成功，import 验证通过")
                } else {
                    installed.add(baseName) // 仍然标记为安装成功（文件已在 site-packages）
                    details.appendLine("⚠ $baseName: 文件已安装，但 import 验证失败（可能缺少依赖库）")
                    Log.w(TAG, "$baseName: import 验证失败，可能需要额外的 .so 依赖")
                }
            } else {
                failed.add(baseName)
                details.appendLine("✗ $baseName: 安装失败")
            }
        }
        
        val success = failed.isEmpty()
        val message = buildString {
            if (installed.isNotEmpty()) {
                appendLine("成功安装: ${installed.joinToString(", ")}")
            }
            if (failed.isNotEmpty()) {
                appendLine("安装失败: ${failed.joinToString(", ")}")
                appendLine("可能原因: Chaquopy 和 PyPI 上尚无该包的 Android aarch64 预编译 wheel")
            }
        }
        
        InstallResult(
            success = success,
            installedPackages = installed,
            failedPackages = failed,
            message = message,
            details = details.toString()
        )
    }
    
    /**
     * 安装单个包
     */
    private suspend fun installSinglePackage(
        baseName: String,
        packageSpec: String,
        details: StringBuilder
    ): Boolean {
        val pypiName = NATIVE_PACKAGES[baseName] ?: baseName
        
        Log.d(TAG, "尝试安装原生包: $baseName (PyPI: $pypiName)")
        
        // 策略 1: 从 assets 安装
        if (installFromAssets(pypiName)) {
            details.appendLine("  [assets] 从内置预编译包安装成功")
            return true
        }
        
        // 策略 2: 从 Chaquopy（Android NDK wheel）或 PyPI（manylinux wheel）下载
        // 不使用 --no-deps：下载完整依赖树，所有 wheel 都会被解压
        val wheelFiles = downloadPrecompiledWheels(packageSpec, details)
        if (wheelFiles.isNotEmpty()) {
            var allSuccess = true
            for (wheel in wheelFiles) {
                details.appendLine("  [下载] 获取到: ${wheel.name} (${wheel.length() / 1024}KB)")
                if (extractWheelToSitePackages(wheel.inputStream())) {
                    // 提取后执行 .so 后缀重命名（cp313 → cp314）
                    renameSoForCurrentPython()
                    details.appendLine("  [解压] ${wheel.name} 已解压到 site-packages")
                } else {
                    details.appendLine("  [解压] ${wheel.name} 解压失败")
                    allSuccess = false
                }
            }
            if (allSuccess) return true
        }
        
        Log.w(TAG, "$baseName: 所有安装策略均失败")
        return false
    }
    
    /**
     * 从 assets 目录安装预编译 wheel
     */
    private fun installFromAssets(pypiName: String): Boolean {
        try {
            val assetFiles = context.assets.list(ASSETS_PACKAGES_DIR) ?: return false
            
            val wheelFile = assetFiles.firstOrNull { file ->
                file.endsWith(".whl") && file.startsWith(pypiName, ignoreCase = true)
            }
            
            if (wheelFile != null) {
                Log.d(TAG, "在 assets 中找到 wheel: $wheelFile")
                val inputStream = context.assets.open("$ASSETS_PACKAGES_DIR/$wheelFile")
                return extractWheelToSitePackages(inputStream)
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "从 assets 安装失败: $pypiName", e)
            return false
        }
    }
    
    /**
     * 下载预编译 wheel（含完整依赖树）
     * 
     * 按策略列表顺序尝试下载：
     * 1. Chaquopy 仓库：Android NDK 编译的 wheel（Bionic libc，最可靠）
     * 2. PyPI：manylinux_aarch64 wheel（glibc，部分包可能兼容）
     * 
     * 不使用 --no-deps：pip download 会同时下载所有依赖的 wheel。
     * 例如安装 Pillow 时，会同时下载 chaquopy-libjpeg、chaquopy-libpng 等依赖。
     * 
     * @param packageSpec 包规格（如 "pillow" 或 "pillow>=10.0"）
     * @param details 详情日志
     * @return 下载到的所有 wheel 文件列表，失败返回空列表
     */
    private suspend fun downloadPrecompiledWheels(
        packageSpec: String,
        details: StringBuilder
    ): List<File> = withContext(Dispatchers.IO) {
        val downloadDir = File(cacheDir, "pypi_dl").also { 
            it.deleteRecursively()
            it.mkdirs() 
        }
        
        val pythonBinary = pythonRuntime.getPythonBinary()
        if (!pythonBinary.exists()) {
            details.appendLine("  [PyPI] Python 二进制文件不存在")
            return@withContext emptyList()
        }
        
        // 构建环境变量
        val env = pythonRuntime.getEnvironment().toMutableMap()
        // 临时移除 PIP_ONLY_BINARY（我们通过命令行参数 --only-binary 控制）
        env.remove("PIP_ONLY_BINARY")
        // 临时移除 PIP_NO_BUILD_ISOLATION（pip download 不需要）
        env.remove("PIP_NO_BUILD_ISOLATION")
        
        // 按策略列表顺序尝试（Chaquopy Android wheel → PyPI manylinux wheel）
        for ((index, strategy) in DOWNLOAD_STRATEGIES.withIndex()) {
            // 清空下载目录
            downloadDir.listFiles()?.forEach { it.delete() }
            
            val cmd = mutableListOf(
                pythonBinary.absolutePath,
                "-m", "pip", "download",
                "--only-binary=:all:",
                // 注意：不使用 --no-deps，让 pip 下载完整依赖树
                "--platform", strategy.platform,
                "--python-version", strategy.pythonVersion,
                "--abi", strategy.abi,
                "--dest", downloadDir.absolutePath
            )
            
            // 添加额外索引源（如 Chaquopy Android 包仓库）
            if (strategy.extraIndexUrl != null) {
                cmd.addAll(listOf("--extra-index-url", strategy.extraIndexUrl))
            }
            
            cmd.add(packageSpec)
            
            Log.d(TAG, "策略 #${index + 1} [${strategy.name}]: plat=${strategy.platform} py=${strategy.pythonVersion} abi=${strategy.abi}")
            
            try {
                val process = ProcessBuilder(cmd).apply {
                    environment().clear()
                    environment().putAll(env)
                    redirectErrorStream(true)
                }.start()
                
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                
                if (exitCode == 0) {
                    val wheelFiles = downloadDir.listFiles { f -> f.name.endsWith(".whl") }
                    if (!wheelFiles.isNullOrEmpty()) {
                        details.appendLine("  [${strategy.name}] 下载成功，共 ${wheelFiles.size} 个 wheel:")
                        
                        // 缓存所有 wheel 文件
                        val cachedFiles = mutableListOf<File>()
                        for (wheel in wheelFiles) {
                            details.appendLine("    - ${wheel.name} (${wheel.length() / 1024}KB)")
                            Log.d(TAG, "下载成功 [${strategy.name}]: ${wheel.name} (${wheel.length()} bytes)")
                            val cached = File(cacheDir, wheel.name)
                            wheel.copyTo(cached, overwrite = true)
                            cachedFiles.add(cached)
                        }
                        
                        return@withContext cachedFiles
                    }
                } else {
                    Log.d(TAG, "策略 [${strategy.name}] 失败 (exit=$exitCode): ${output.take(200)}")
                }
            } catch (e: Exception) {
                Log.d(TAG, "策略 [${strategy.name}] 异常: ${e.message}")
            }
        }
        
        details.appendLine("  未找到兼容的预编译 wheel (已尝试 Chaquopy + PyPI 共 ${DOWNLOAD_STRATEGIES.size} 种策略)")
        emptyList()
    }
    
    /**
     * 提取 wheel (.whl/zip) 到 site-packages
     * 
     * 关键：对 .so 文件设置可执行权限，否则 Python 无法加载
     */
    private fun extractWheelToSitePackages(inputStream: java.io.InputStream): Boolean {
        val targetDir = sitePackages.also { it.mkdirs() }
        var extractedFiles = 0
        var extractedSoFiles = 0
        
        try {
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val targetFile = File(targetDir, entry.name)
                    
                    // 安全检查：防止 zip slip
                    if (!targetFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                        Log.w(TAG, "跳过可疑路径: ${entry.name}")
                        zip.closeEntry()
                        entry = zip.nextEntry
                        continue
                    }
                    
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).use { out ->
                            zip.copyTo(out)
                        }
                        
                        // 对 .so 文件设置可执行权限
                        if (entry.name.endsWith(".so")) {
                            targetFile.setReadable(true, false)
                            targetFile.setExecutable(true, false)
                            extractedSoFiles++
                            Log.d(TAG, "提取 .so: ${entry.name} (${targetFile.length()} bytes)")
                        } else {
                            targetFile.setReadable(true, false)
                        }
                        
                        extractedFiles++
                    }
                    
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            
            Log.d(TAG, "Wheel 提取完成: $extractedFiles 个文件, 其中 $extractedSoFiles 个 .so")
            return extractedFiles > 0
        } catch (e: Exception) {
            Log.e(TAG, "Wheel 提取失败", e)
            return false
        }
    }
    
    /**
     * 修复已安装包中的 .so 文件
     * 
     * 在应用启动时调用，确保之前安装的包也能被正确加载。
     * 主要修复 Chaquopy cp313 wheel 中 .so 对 libpython3.13.so 的硬链接。
     * 
     * 此方法是幂等的，可安全多次调用。已修复的文件不会被重复处理。
     */
    fun repairInstalledPackages() {
        Log.d(TAG, "开始检查并修复已安装的原生包...")
        try {
            // 修复 stdlib site-packages
            renameSoForCurrentPython(sitePackages)
            // 修复 user site-packages
            renameSoForCurrentPython(userSitePackages)
            Log.d(TAG, "原生包修复完成")
        } catch (e: Exception) {
            Log.e(TAG, "修复已安装包时异常", e)
        }
    }

    /**
     * 处理 .so 文件以匹配当前 Python 版本
     * 
     * 三个关键操作：
     * 
     * 1. **重命名文件后缀**: cpython-313 → cpython-314
     *    部分包的 .so 文件名含 cpython 版本后缀（如 _module.cpython-313-aarch64-linux-android.so），
     *    Python 的 importlib 只搜索当前版本的后缀（cpython-314），需要重命名。
     * 
     * 2. **补丁 ELF NEEDED 条目**: libpython3.13.so → libpython3.14.so
     *    Chaquopy 编译的 .so 在 ELF 动态链接段中硬编码了 libpython3.13.so（或3.12/3.11），
     *    但设备上只有 libpython3.14.so。dlopen 加载时如果找不到 libpython3.13.so 会直接
     *    报 ImportError。
     *    
     *    虽然 PythonRuntime 中已创建了 libpython3.13.so → libpython3.14.so 符号链接作为后备，
     *    但在 Android 上由于 linker namespace 限制，符号链接可能不被动态链接器识别。
     *    直接修改 ELF 二进制中的字符串是最可靠的方案（字符串长度相同，不破坏 ELF 结构）。
     * 
     * 3. **收集 chaquopy 依赖库**: 将 chaquopy-libjpeg 等包的 .so 收集到 chaquopy/lib/，
     *    供 LD_LIBRARY_PATH 使用。
     * 
     * CPython 的 C ABI 在 minor 版本间通常向前兼容（313→314 没问题）。
     */
    private fun renameSoForCurrentPython(targetDir: File = sitePackages) {
        if (!targetDir.exists()) return
        
        // 当前 Python 版本的 SOABI 后缀（如 cpython-314）
        val currentSoSuffix = "cpython-314-aarch64-linux-android.so"
        // 需要重命名的旧版本后缀模式
        val oldSoPattern = Regex("""\.cpython-31[0-3]-aarch64-linux-android\.so$""")
        
        // ELF NEEDED 补丁：将旧版 libpython 库名替换为当前版本
        // libpython3.13.so 和 libpython3.14.so 都是 16 字节（+1 null），二进制替换安全
        val libpythonOldNames = listOf(
            "libpython3.13.so",
            "libpython3.12.so",
            "libpython3.11.so"
        )
        val libpythonNewName = "libpython3.14.so"
        
        var renamedCount = 0
        var patchedCount = 0
        var collectedLibs = 0
        
        // 创建 chaquopy/lib 目录用于收集依赖库的 .so
        val chaquopyLibDir = File(targetDir, "chaquopy/lib").also { it.mkdirs() }
        
        // 递归扫描 site-packages
        targetDir.walkTopDown().forEach { file ->
            if (file.isFile && file.name.endsWith(".so")) {
                var actualFile = file
                
                // 1. 重命名 cpython-31x → cpython-314（文件名后缀）
                if (oldSoPattern.containsMatchIn(file.name)) {
                    val newName = oldSoPattern.replace(file.name, ".$currentSoSuffix")
                    val newFile = File(file.parentFile, newName)
                    if (file.renameTo(newFile)) {
                        renamedCount++
                        Log.d(TAG, "重命名 .so: ${file.name} → $newName")
                        actualFile = newFile
                    }
                }
                
                // 2. 补丁 ELF NEEDED: libpython3.1x.so → libpython3.14.so
                if (patchElfLibpythonNeeded(actualFile, libpythonOldNames, libpythonNewName)) {
                    patchedCount++
                }
                
                // 3. 收集 chaquopy 依赖库的 .so 到 chaquopy/lib/
                // chaquopy-libjpeg, chaquopy-libpng 等包的 .so 需要在 LD_LIBRARY_PATH 中
                val parentPath = actualFile.parentFile?.absolutePath ?: ""
                if (parentPath.contains("chaquopy") && 
                    !parentPath.contains("chaquopy/lib") &&
                    (actualFile.name.startsWith("lib") || actualFile.name.contains(".so"))) {
                    val targetLib = File(chaquopyLibDir, actualFile.name)
                    if (!targetLib.exists()) {
                        actualFile.copyTo(targetLib, overwrite = true)
                        targetLib.setReadable(true, false)
                        targetLib.setExecutable(true, false)
                        collectedLibs++
                        Log.d(TAG, "收集 chaquopy 依赖库: ${actualFile.name} → chaquopy/lib/")
                    }
                }
            }
        }
        
        if (renamedCount > 0) {
            Log.d(TAG, "共重命名 $renamedCount 个 .so 文件 (→ $currentSoSuffix)")
        }
        if (patchedCount > 0) {
            Log.d(TAG, "共补丁 $patchedCount 个 .so 文件的 ELF NEEDED (→ $libpythonNewName)")
        }
        if (collectedLibs > 0) {
            Log.d(TAG, "共收集 $collectedLibs 个 chaquopy 依赖库到 chaquopy/lib/")
        }
    }
    
    /**
     * 补丁 ELF 二进制文件中的 libpython NEEDED 条目
     * 
     * ELF 文件的 .dynstr 段中存储动态链接库名称（null 终止字符串）。
     * 直接在二进制中替换字符串，要求新旧字符串长度完全相同。
     * 
     * 例如: libpython3.13.so → libpython3.14.so（都是16字节 + null终止符）
     * 
     * @param file .so 文件
     * @param oldNames 需要替换的旧库名列表
     * @param newName 新库名
     * @return true 如果文件被修改
     */
    private fun patchElfLibpythonNeeded(
        file: File,
        oldNames: List<String>,
        newName: String
    ): Boolean {
        try {
            // 只处理合理大小的 .so 文件（避免对超大文件消耗过多内存）
            if (file.length() > 50 * 1024 * 1024) { // 50MB 上限
                Log.d(TAG, "跳过过大文件: ${file.name} (${file.length() / 1024 / 1024}MB)")
                return false
            }
            
            val bytes = file.readBytes()
            var modified = false
            
            for (oldName in oldNames) {
                // ELF .dynstr 中的字符串是 null 终止的
                val oldBytes = "$oldName\u0000".toByteArray(Charsets.US_ASCII)
                val newBytes = "$newName\u0000".toByteArray(Charsets.US_ASCII)
                
                // 安全检查：长度必须相同
                if (oldBytes.size != newBytes.size) {
                    Log.w(TAG, "补丁长度不匹配: $oldName(${oldBytes.size}) vs $newName(${newBytes.size}), 跳过")
                    continue
                }
                
                // 在字节数组中搜索并替换
                var pos = 0
                while (pos <= bytes.size - oldBytes.size) {
                    var match = true
                    for (i in oldBytes.indices) {
                        if (bytes[pos + i] != oldBytes[i]) {
                            match = false
                            break
                        }
                    }
                    if (match) {
                        System.arraycopy(newBytes, 0, bytes, pos, newBytes.size)
                        modified = true
                        Log.d(TAG, "补丁 ELF: ${file.name} offset=$pos: $oldName → $newName")
                        pos += newBytes.size
                    } else {
                        pos++
                    }
                }
            }
            
            if (modified) {
                file.writeBytes(bytes)
            }
            
            return modified
        } catch (e: Exception) {
            Log.w(TAG, "补丁 ELF 失败: ${file.name}: ${e.message}")
            return false
        }
    }
    
    /**
     * 验证包是否可以成功 import
     * 
     * 安装完成后运行 Python 脚本测试 import。
     * 如果失败，会尝试用 ctypes.cdll 加载 .so 获取更详细的 dlopen 错误信息，
     * 以区分是"库文件缺失"还是"ABI 不兼容"等问题。
     * 
     * @param baseName 包名（如 "pillow"）
     * @return true 如果 import 成功
     */
    private fun verifyImport(baseName: String): Boolean {
        // 包名到实际 import 名的映射
        val importName = when (baseName) {
            "pillow", "pil" -> "PIL"
            "pyyaml", "yaml" -> "yaml"
            "scikit_learn", "sklearn" -> "sklearn"
            "opencv_python", "opencv_python_headless" -> "cv2"
            "charset_normalizer" -> "charset_normalizer"
            "markupsafe" -> "markupsafe"
            else -> baseName
        }
        
        return try {
            val pythonBinary = pythonRuntime.getPythonBinary()
            if (!pythonBinary.exists()) return false
            
            val env = pythonRuntime.getEnvironment()
            
            // 增强的验证脚本：import 失败时输出详细的诊断信息
            val verifyCode = buildString {
                appendLine("import sys, os")
                appendLine("try:")
                appendLine("    import $importName")
                appendLine("    print('OK')")
                appendLine("except ImportError as e:")
                appendLine("    print(f'ImportError: {e}')")
                appendLine("    import traceback")
                appendLine("    traceback.print_exc()")
                appendLine("    # 诊断: 尝试用 ctypes 加载 .so 获取更详细的 dlopen 错误")
                appendLine("    try:")
                appendLine("        import ctypes, pathlib")
                appendLine("        sp = os.environ.get('PYTHONHOME', '') + '/lib/python3.14/site-packages'")
                appendLine("        for p in pathlib.Path(sp).rglob('*.so'):")
                appendLine("            name_lower = str(p).lower()")
                appendLine("            if '${importName.lowercase()}' in name_lower or '_imaging' in name_lower:")
                appendLine("                try:")
                appendLine("                    ctypes.cdll.LoadLibrary(str(p))")
                appendLine("                    print(f'  dlopen OK: {p.name}')")
                appendLine("                except OSError as e2:")
                appendLine("                    print(f'  dlopen FAIL: {p.name}: {e2}')")
                appendLine("    except Exception as diag_err:")
                appendLine("        print(f'  诊断失败: {diag_err}')")
                appendLine("    print('LD_LIBRARY_PATH=' + os.environ.get('LD_LIBRARY_PATH', 'NOT SET'))")
                appendLine("    sys.exit(1)")
            }
            
            val process = ProcessBuilder(
                pythonBinary.absolutePath, "-c", verifyCode
            ).apply {
                environment().clear()
                environment().putAll(env)
                redirectErrorStream(true)
            }.start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0 && output.contains("OK")) {
                Log.d(TAG, "import 验证成功: $importName")
                true
            } else {
                Log.w(TAG, "import 验证失败: $importName (exit=$exitCode):\n$output")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "import 验证异常: $importName", e)
            false
        }
    }
    
    /**
     * 从包规格中提取包名（去除版本号）
     */
    private fun extractBaseName(packageSpec: String): String {
        return packageSpec.lowercase()
            .split(">=", "<=", "==", "~=", "!=", ">", "<", "[", ";")
            .first()
            .trim()
            .replace("-", "_")
    }
    
    /**
     * 获取所有已安装的原生包列表
     */
    fun getInstalledNativePackages(): List<String> {
        val installed = mutableListOf<String>()
        
        for ((name, pypiName) in NATIVE_PACKAGES) {
            val dirName = pypiName.lowercase().replace("-", "_")
            val isPillow = name == "pillow" || name == "pil"
            
            val exists = listOf(sitePackages, userSitePackages).any { spDir ->
                File(spDir, dirName).exists() ||
                (isPillow && File(spDir, "PIL").exists())
            }
            
            if (exists) installed.add(name)
        }
        
        return installed.distinct()
    }
    
    /**
     * 清理下载缓存
     */
    fun clearCache() {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
        Log.d(TAG, "下载缓存已清理")
    }
    
    /**
     * 获取 assets 中可用的预编译包列表
     */
    fun getAvailableAssetPackages(): List<String> {
        return try {
            val files = context.assets.list(ASSETS_PACKAGES_DIR) ?: emptyArray()
            files.filter { it.endsWith(".whl") }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
