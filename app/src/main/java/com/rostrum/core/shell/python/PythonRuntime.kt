package com.rostrum.core.shell.python

import android.content.Context
import android.system.Os
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Python 运行时管理器
 * 
 * Python 可执行文件（libpython_main.so）打包在 jniLibs 中，安装时会被解压到
 * native library 目录（/data/app/.../lib/arm64/），这是 Android 允许执行的唯一位置。
 * 
 * Python 标准库和其他资源从 assets 提取到应用文件目录。
 */
class PythonRuntime(private val context: Context) {
    
    companion object {
        private const val TAG = "PythonRuntime"
        private const val ASSETS_PYTHON_DIR = "python_runtime"
        private const val EXTRACTED_PYTHON_DIR = "python_runtime"
        private const val PYTHON_EXECUTABLE_NAME = "libpython_main.so"
        // v4: 修复 AAPT2 ignoreAssetsPattern 导致下划线目录(_bundled/_path/_pyrepl等)未打包
        // v5: 创建 sitecustomize.py + libpython3.13/3.12/3.11.so 兼容性符号链接
        private const val EXTRACTION_VERSION = "5"
        private const val VERSION_FILE = "extraction_version.txt"
    }
    
    // 防止多线程同时初始化/提取
    private val initLock = Any()
    
    // Python 标准库目录（从 assets 提取）
    private val pythonDir: File
        get() = File(context.filesDir, EXTRACTED_PYTHON_DIR)
    
    // Native library 目录（Android 允许执行的目录）
    private val nativeLibDir: File
        get() = File(context.applicationInfo.nativeLibraryDir)
    
    // Python 可执行文件（在 native library 目录中）
    private val _pythonBinary: File
        get() = File(nativeLibDir, PYTHON_EXECUTABLE_NAME)
    
    /**
     * 初始化 Python 运行时
     * 
     * 1. 检查 native library 目录中的 python 可执行文件
     * 2. 从 assets 提取 Python 标准库到应用文件目录
     */
    fun initialize(): Boolean {
        synchronized(initLock) {
            return try {
                val binary = _pythonBinary
                
                // 检查 python 可执行文件是否在 native library 目录
                if (!binary.exists()) {
                    Log.e(TAG, "Python binary not found in native lib dir: ${binary.absolutePath}")
                    Log.e(TAG, "Native lib dir contents: ${nativeLibDir.listFiles()?.map { it.name }}")
                    return false
                }
                
                Log.d(TAG, "Python binary found: ${binary.absolutePath}")
                
                // 从 assets 提取 Python 标准库（如果尚未提取）
                if (!isStdlibExtracted()) {
                    Log.d(TAG, "Extracting Python stdlib from assets...")
                    extractFromAssets()
                }
                
                // 创建兼容性 libpython 符号链接
                // Chaquopy 编译的 .so 硬链接到 libpython3.13.so（或 3.12/3.11），
                // 但我们只有 libpython3.14.so，需要创建符号链接让 dlopen 能找到
                createCompatibilityLibLinks()
                
                Log.d(TAG, "Python runtime initialized")
                Log.d(TAG, "  Binary: ${binary.absolutePath}")
                Log.d(TAG, "  Stdlib: ${pythonDir.absolutePath}")
                Log.d(TAG, "  Native lib dir: ${nativeLibDir.absolutePath}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Python runtime", e)
                false
            }
        }
    }
    
    /**
     * 检查 Python 标准库是否已提取
     * 通过检查关键模块目录是否存在来验证提取是否完整
     */
    private fun isStdlibExtracted(): Boolean {
        // 首先检查版本号
        val versionFile = File(pythonDir, VERSION_FILE)
        if (!versionFile.exists() || versionFile.readText().trim() != EXTRACTION_VERSION) {
            Log.w(TAG, "Extraction version mismatch or not found, will re-extract")
            pythonDir.deleteRecursively()
            return false
        }
        
        val stdlibDir = File(pythonDir, "lib/python3.14")
        if (!stdlibDir.exists() || !stdlibDir.isDirectory) {
            Log.w(TAG, "Stdlib dir not found: ${stdlibDir.absolutePath}")
            return false
        }
        
        // 检查关键模块目录是否存在且非空
        val criticalPaths = listOf(
            "zipfile/__init__.py" to 1000L,  // 至少1KB
            "zipfile/_path/__init__.py" to 1000L,  // Python 3.10+ 必需
            "pathlib/__init__.py" to 10000L,  // pathlib 是必需的
            "subprocess.py" to 1000L,
            "ensurepip/__init__.py" to 100L
        )
        
        var allFound = true
        for ((path, minSize) in criticalPaths) {
            val file = File(stdlibDir, path)
            if (!file.exists()) {
                Log.w(TAG, "Critical path missing: $path")
                allFound = false
            } else if (file.length() < minSize) {
                Log.w(TAG, "Critical file too small (${file.length()} bytes): $path")
                allFound = false
            }
        }
        
        // 检查关键的子目录是否存在
        val criticalDirs = listOf(
            "zipfile/_path",
            "pathlib",
            "importlib",
            "importlib/resources"
        )
        
        for (dir in criticalDirs) {
            val dirFile = File(stdlibDir, dir)
            if (!dirFile.exists() || !dirFile.isDirectory) {
                Log.w(TAG, "Critical dir missing: $dir")
                allFound = false
            }
        }
        
        if (!allFound) {
            Log.w(TAG, "Some critical paths missing, will re-extract")
            pythonDir.deleteRecursively()
            return false
        }
        
        return true
    }
    
    /**
     * 从 assets 提取 Python 运行时
     */
    private fun extractFromAssets() {
        val assets = context.assets
        
        // 创建目标目录
        pythonDir.mkdirs()
        
        // 递归复制文件
        copyAssetsRecursive(ASSETS_PYTHON_DIR, pythonDir, assets)
        
        // 写入版本号文件
        File(pythonDir, VERSION_FILE).writeText(EXTRACTION_VERSION)
        Log.d(TAG, "Extraction completed, version: $EXTRACTION_VERSION")
        
        // 创建 sitecustomize.py（Python 启动时自动加载的钩子）
        createSiteCustomize()
    }
    
    /**
     * 创建 sitecustomize.py
     * 
     * 这个文件放在 site-packages 目录中，Python 启动时会自动 import 它。
     * 用途：
     * 1. 修补 ctypes.util.find_library()，让它能在 LD_LIBRARY_PATH 中搜索 .so
     * 2. 提供友好的 import 错误提示（告诉用户用 pip install 安装）
     * 3. 确保 site-packages/chaquopy/lib/ 在 LD_LIBRARY_PATH 中
     */
    private fun createSiteCustomize() {
        val pythonVersion = detectPythonVersion()
        val sitePackagesDir = File(pythonDir, "lib/$pythonVersion/site-packages")
        sitePackagesDir.mkdirs()
        
        val siteCustomize = File(sitePackagesDir, "sitecustomize.py")
        siteCustomize.writeText("""
import os, sys

# === 1. 修补 ctypes.util.find_library ===
# Android Bionic 没有标准的 ldconfig，ctypes.util.find_library 无法找到 .so
# 这里让它在 LD_LIBRARY_PATH 和 site-packages/chaquopy/lib/ 中搜索
try:
    import ctypes.util
    _original_find_library = ctypes.util.find_library

    def _android_find_library(name):
        # 先尝试原始方法
        result = _original_find_library(name)
        if result:
            return result

        # 在 LD_LIBRARY_PATH 中搜索
        ld_paths = os.environ.get('LD_LIBRARY_PATH', '').split(':')
        candidates = [f'lib{name}.so', f'{name}.so', name]

        for search_dir in ld_paths:
            if not search_dir or not os.path.isdir(search_dir):
                continue
            for candidate in candidates:
                full_path = os.path.join(search_dir, candidate)
                if os.path.isfile(full_path):
                    return full_path

        return None

    ctypes.util.find_library = _android_find_library
except Exception:
    pass

# === 2. 确保 chaquopy/lib 在 LD_LIBRARY_PATH 中 ===
# 防止环境变量在子进程中丢失
try:
    for sp_dir in sys.path:
        chaq_lib = os.path.join(sp_dir, 'chaquopy', 'lib')
        if os.path.isdir(chaq_lib):
            ld_path = os.environ.get('LD_LIBRARY_PATH', '')
            if chaq_lib not in ld_path:
                os.environ['LD_LIBRARY_PATH'] = chaq_lib + ':' + ld_path
except Exception:
    pass
""".trimIndent())
        
        siteCustomize.setReadable(true, false)
        Log.d(TAG, "Created sitecustomize.py at: ${siteCustomize.absolutePath}")
    }
    
    /**
     * 创建兼容性 libpython 符号链接
     * 
     * Chaquopy 编译的 .so 文件在 ELF 层面硬链接到 libpython3.13.so（或 3.12/3.11），
     * 但我们嵌入的 Python 是 3.14，native lib 目录只有 libpython3.14.so。
     * dlopen 找不到 libpython3.13.so 会直接报 ImportError。
     * 
     * 解决：在 python_runtime/lib/ 目录（已在 LD_LIBRARY_PATH 中）创建符号链接：
     *   libpython3.13.so → /data/app/.../lib/arm64/libpython3.14.so
     *   libpython3.12.so → (同上)
     *   libpython3.11.so → (同上)
     * 
     * CPython 3.11-3.14 的 C ABI 高度兼容，符号链接即可解决 dlopen 找不到库的问题。
     */
    private fun createCompatibilityLibLinks() {
        val libDir = File(pythonDir, "lib")
        libDir.mkdirs()
        
        val realLib = File(nativeLibDir, "libpython3.14.so")
        if (!realLib.exists()) {
            Log.w(TAG, "libpython3.14.so not found in native lib dir, skip compat links")
            return
        }
        
        // 需要创建符号链接的旧版本
        val compatVersions = listOf("3.13", "3.12", "3.11")
        
        for (version in compatVersions) {
            val linkFile = File(libDir, "libpython$version.so")
            if (linkFile.exists()) {
                // 已存在则跳过（可能是上次创建的）
                continue
            }
            try {
                Os.symlink(realLib.absolutePath, linkFile.absolutePath)
                Log.d(TAG, "Created compat symlink: libpython$version.so → ${realLib.absolutePath}")
            } catch (e: Exception) {
                // 符号链接失败则尝试复制
                Log.w(TAG, "Symlink failed for libpython$version.so, trying copy: ${e.message}")
                try {
                    realLib.copyTo(linkFile, overwrite = true)
                    linkFile.setReadable(true, false)
                    Log.d(TAG, "Created compat copy: libpython$version.so (${linkFile.length()} bytes)")
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to create compat lib for $version", e2)
                }
            }
        }
    }
    
    /**
     * 边缘文件扩展名：Android AssetManager 会把这些 zip 格式文件当成目录
     * 必须强制作为普通文件处理
     */
    private val ARCHIVE_EXTENSIONS = setOf(".whl", ".zip", ".jar", ".egg")
    
    /**
     * 递归复制 assets 文件
     */
    private fun copyAssetsRecursive(
        assetsPath: String,
        targetDir: File,
        assets: android.content.res.AssetManager
    ) {
        val files = assets.list(assetsPath) ?: return
        
        for (file in files) {
            val sourcePath = if (assetsPath.isEmpty()) file else "$assetsPath/$file"
            val targetFile = File(targetDir, file)
            
            try {
                // 强制把 .whl/.zip/.jar/.egg 当文件处理
                // （Android AssetManager 会把这些 zip 格式文件的内部条目当作子文件返回）
                val isArchiveFile = ARCHIVE_EXTENSIONS.any { file.endsWith(it, ignoreCase = true) }
                
                if (isArchiveFile) {
                    // 直接作为文件复制
                    targetFile.parentFile?.mkdirs()
                    assets.open(sourcePath).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    targetFile.setReadable(true, false)
                    Log.d(TAG, "Extracted archive file: $file (${targetFile.length()} bytes)")
                    continue
                }
                
                // 先尝试判断是否为目录
                // 方法1: 检查是否有子文件
                val subFiles = assets.list(sourcePath)
                val hasSubFiles = subFiles != null && subFiles.isNotEmpty()
                
                // 方法2: 尝试打开文件，如果失败可能是目录
                val isFile = if (!hasSubFiles) {
                    try {
                        assets.open(sourcePath).use { true }
                    } catch (e: Exception) {
                        // 无法打开，说明是空目录
                        false
                    }
                } else {
                    false
                }
                
                if (hasSubFiles || !isFile) {
                    // 是目录（有子文件，或者无法作为文件打开）
                    targetFile.mkdirs()
                    if (hasSubFiles) {
                        copyAssetsRecursive(sourcePath, targetFile, assets)
                    }
                } else {
                    // 是文件
                    targetFile.parentFile?.mkdirs()
                    assets.open(sourcePath).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    // 设置文件权限
                    // 对于 .so 文件、可执行文件和脚本文件，需要可执行权限
                    if (file.endsWith(".so") || file == "python3" || file == "python" || file.endsWith(".sh")) {
                        targetFile.setReadable(true, false)  // 所有用户可读
                        targetFile.setWritable(false, false) // 不可写（保护）
                        targetFile.setExecutable(true, false) // 所有用户可执行
                    } else {
                        // 其他文件至少需要可读
                        targetFile.setReadable(true, false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error copying $sourcePath", e)
            }
        }
    }
    
    /**
     * 获取 Python 二进制文件路径
     */
    fun getPythonBinary(): File {
        return _pythonBinary
    }
    
    // pip 用户安装目录 (PYTHONUSERBASE)
    private val pipUserBase: File
        get() = File(context.filesDir, "pip_packages")
    
    /**
     * 获取 pip 用户 site-packages 路径
     */
    fun getUserSitePackages(): String {
        val pythonVersion = detectPythonVersion()
        return "${pipUserBase.absolutePath}/lib/$pythonVersion/site-packages"
    }
    
    /**
     * 获取 CA 证书路径
     */
    fun getCaCertPath(): String {
        return File(pythonDir, "lib/cacert.pem").absolutePath
    }
    
    /**
     * 获取 stdlib site-packages 路径（pip 安装到此处）
     */
    fun getStdlibSitePackages(): String {
        val pythonVersion = detectPythonVersion()
        return File(pythonDir, "lib/$pythonVersion/site-packages").absolutePath
    }
    
    /**
     * 确保 pip 已安装
     * 直接用 zipfile 解压 pip wheel 到 site-packages，完全不依赖子进程
     * 
     * @return true if pip is available
     */
    fun ensurePipInstalled(): Boolean {
        try {
            val pythonVersion = detectPythonVersion()
            val sitePackages = File(pythonDir, "lib/$pythonVersion/site-packages")
            sitePackages.mkdirs()
            
            // 检查 pip 是否已安装
            val pipDir = File(sitePackages, "pip")
            val pipMain = File(pipDir, "__main__.py")
            if (pipMain.exists() && pipMain.length() > 100) {
                Log.d(TAG, "pip already installed at: ${pipDir.absolutePath}")
                return true
            }
            
            // 策略1: 从提取后的目录查找 wheel
            val bundledDir = File(pythonDir, "lib/$pythonVersion/ensurepip/_bundled")
            val wheelFiles = bundledDir.listFiles { _, name -> 
                name.startsWith("pip-") && name.endsWith(".whl") 
            }
            
            if (!wheelFiles.isNullOrEmpty()) {
                val wheelFile = wheelFiles[0]
                Log.d(TAG, "Extracting pip from extracted wheel: ${wheelFile.name}")
                return extractWheelToSitePackages(wheelFile.inputStream(), sitePackages, pipMain)
            }
            
            // 策略2: 从 assets 直接读取 wheel（解决 .whl 文件未被正确提取的问题）
            Log.w(TAG, "Wheel not found in extracted dir, trying assets...")
            val assetsWheelPath = "$ASSETS_PYTHON_DIR/lib/$pythonVersion/ensurepip/_bundled"
            val assetFiles = context.assets.list(assetsWheelPath)
            val wheelName = assetFiles?.firstOrNull { 
                it.startsWith("pip-") && it.endsWith(".whl") 
            }
            
            if (wheelName != null) {
                Log.d(TAG, "Extracting pip from assets wheel: $wheelName")
                val inputStream = context.assets.open("$assetsWheelPath/$wheelName")
                return extractWheelToSitePackages(inputStream, sitePackages, pipMain)
            }
            
            Log.e(TAG, "No pip wheel found in extracted dir or assets")
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure pip installed", e)
            return false
        }
    }
    
    /**
     * 解压 wheel (.whl/zip) 到 site-packages
     */
    private fun extractWheelToSitePackages(
        inputStream: java.io.InputStream, 
        sitePackages: File,
        pipMain: File
    ): Boolean {
        try {
            java.util.zip.ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                var count = 0
                while (entry != null) {
                    val targetFile = File(sitePackages, entry.name)
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        targetFile.outputStream().use { out ->
                            zip.copyTo(out)
                        }
                        count++
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                Log.d(TAG, "Extracted $count files from pip wheel")
            }
            
            if (pipMain.exists()) {
                Log.d(TAG, "pip extracted successfully to: ${sitePackages.absolutePath}")
                return true
            } else {
                Log.e(TAG, "pip extraction seemed to work but __main__.py not found")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract wheel", e)
            return false
        }
    }
    
    /**
     * 获取 Python 环境变量
     * 
     * 完整配置以支持 pip 及第三方包安装：
     * - HOME: 应用文件目录（~ 展开需要）
     * - TMPDIR: 缓存目录（tempfile 模块需要）
     * - PYTHONUSERBASE: pip --user 安装目标
     * - SSL_CERT_FILE: CA 证书（HTTPS 验证需要）
     * - PYTHONDONTWRITEBYTECODE: 避免 .pyc 写入权限问题
     */
    fun getEnvironment(): Map<String, String> {
        val env = mutableMapOf<String, String>()
        env.putAll(System.getenv())
        
        // 检测 Python 版本
        val pythonVersion = detectPythonVersion()
        
        // === 核心 Python 环境变量 ===
        env["PYTHONHOME"] = pythonDir.absolutePath
        
        // PYTHONPATH 包含：用户 site-packages + stdlib site-packages + stdlib
        val userSitePackages = getUserSitePackages()
        val stdlibPath = File(pythonDir, "lib/$pythonVersion").absolutePath
        val stdlibSitePackages = File(pythonDir, "lib/$pythonVersion/site-packages").absolutePath
        File(stdlibSitePackages).mkdirs()
        env["PYTHONPATH"] = "$userSitePackages:$stdlibSitePackages:$stdlibPath"
        
        // LD_LIBRARY_PATH 需要包含：
        // 1. native library 目录（libpython3.14.so 等）
        // 2. 提取的 lib 目录（备用）
        // 3. site-packages/chaquopy/lib/（Chaquopy 依赖库：libjpeg, libpng, libopenblas 等）
        // 4. site-packages/（部分包的 .so 直接在 site-packages 根目录）
        // 5. 用户 site-packages 下同理
        val chaquopyLibPath = "$stdlibSitePackages/chaquopy/lib"
        val userChaquopyLibPath = "$userSitePackages/chaquopy/lib"
        File(chaquopyLibPath).mkdirs()
        
        val ldLibraryPath = listOfNotNull(
            nativeLibDir.absolutePath,
            "${pythonDir.absolutePath}/lib",
            chaquopyLibPath,
            stdlibSitePackages,
            userChaquopyLibPath,
            userSitePackages,
            System.getenv("LD_LIBRARY_PATH")
        ).joinToString(":")
        env["LD_LIBRARY_PATH"] = ldLibraryPath
        
        // === pip 所需环境变量 ===
        
        // HOME - 许多工具依赖 ~ 展开（pip config, cache 等）
        env["HOME"] = context.filesDir.absolutePath
        
        // TMPDIR - tempfile 模块和 pip 安装过程需要临时目录
        val tmpDir = File(context.cacheDir, "python_tmp")
        tmpDir.mkdirs()
        env["TMPDIR"] = tmpDir.absolutePath
        env["TEMP"] = tmpDir.absolutePath
        env["TMP"] = tmpDir.absolutePath
        
        // PYTHONUSERBASE - pip --user 安装到此目录
        pipUserBase.mkdirs()
        env["PYTHONUSERBASE"] = pipUserBase.absolutePath
        
        // SSL_CERT_FILE - HTTPS 证书验证（pip 下载包必需）
        val caCertFile = File(pythonDir, "lib/cacert.pem")
        if (caCertFile.exists()) {
            env["SSL_CERT_FILE"] = caCertFile.absolutePath
            env["REQUESTS_CA_BUNDLE"] = caCertFile.absolutePath
            env["CURL_CA_BUNDLE"] = caCertFile.absolutePath
        }
        
        // 避免 .pyc 文件写入问题（Android 文件系统权限）
        env["PYTHONDONTWRITEBYTECODE"] = "1"
        
        // pip 配置：禁止尝试安装到系统目录
        env["PIP_USER"] = "1"
        
        // pip 配置：全局禁止从源码编译 C 扩展
        // 这是最后一道防线，确保无论通过哪条路径调用 pip，
        // 都不会尝试编译 C 扩展（Android 上没有编译器，必定失败）
        // 含 C 扩展的包应通过 NativePackageInstaller 安装预编译 wheel
        env["PIP_ONLY_BINARY"] = ":all:"
        env["PIP_NO_BUILD_ISOLATION"] = "1"
        
        // 确保 user site-packages 目录存在
        File(userSitePackages).mkdirs()
        
        // 确保 pip bin 目录在 PATH 中
        val pipBinDir = "${pipUserBase.absolutePath}/bin"
        File(pipBinDir).mkdirs()
        val currentPath = env["PATH"] ?: "/system/bin"
        env["PATH"] = "$pipBinDir:$currentPath"
        
        return env
    }
    
    /**
     * 检测 Python 版本
     * 通过查找 lib/pythonX.Y 目录或 libpythonX.Y.so 文件
     */
    private fun detectPythonVersion(): String {
        val libDir = File(pythonDir, "lib")
        if (!libDir.exists()) {
            return "python3.14" // 默认版本
        }
        
        // 查找 pythonX.Y 目录
        val pythonDirs = libDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("python") && file.name.matches(Regex("python[0-9]+\\.[0-9]+"))
        }
        
        if (!pythonDirs.isNullOrEmpty()) {
            return pythonDirs[0].name
        }
        
        // 查找 libpythonX.Y.so 文件
        val libPythonFiles = libDir.listFiles { _, name ->
            name.startsWith("libpython") && name.endsWith(".so")
        }
        
        if (!libPythonFiles.isNullOrEmpty()) {
            // 从 libpython3.14.so 提取版本号 -> python3.14
            val match = Regex("libpython([0-9.]+)\\.so").find(libPythonFiles[0].name)
            val version = match?.groupValues?.get(1)?.let { "python$it" } ?: "python3.14"
            return version
        }
        
        return "python3.14" // 默认版本
    }
    
    /**
     * 检查 Python 运行时是否可用
     */
    fun isAvailable(): Boolean {
        val binary = _pythonBinary
        // Native library 目录的文件已有执行权限，只需检查是否存在
        return binary.exists()
    }
    
    /**
     * 获取 Python 版本
     */
    fun getVersion(): String {
        return try {
            val binary = _pythonBinary
            val process = ProcessBuilder()
                .command(binary.absolutePath, "--version")
                .start()
            
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            output
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
}
