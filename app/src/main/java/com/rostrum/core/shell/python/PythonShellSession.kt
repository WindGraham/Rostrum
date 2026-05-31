package com.rostrum.core.shell.python

import android.content.Context
import android.util.Log
import com.rostrum.core.shell.IShellSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Python Shell 会话
 * 通过标准输入/输出与 Python 解释器通信
 */
class PythonShellSession(
    private val context: Context,
    private val scope: CoroutineScope
) : IShellSession {
    
    companion object {
        private const val TAG = "PythonShellSession"
        
        // 常见模块名到 pip 包名的映射
        val MODULE_TO_PACKAGE = mapOf(
            "cv2" to "opencv-python",
            "PIL" to "Pillow",
            "sklearn" to "scikit-learn",
            "skimage" to "scikit-image",
            "yaml" to "pyyaml",
            "bs4" to "beautifulsoup4",
            "attr" to "attrs",
            "dateutil" to "python-dateutil",
            "dotenv" to "python-dotenv",
            "gi" to "PyGObject",
            "wx" to "wxPython",
            "serial" to "pyserial",
            "usb" to "pyusb",
            "crypto" to "pycryptodome",
            "jwt" to "PyJWT",
            "lxml" to "lxml",
            "numpy" to "numpy",
            "pandas" to "pandas",
            "matplotlib" to "matplotlib",
            "requests" to "requests",
            "flask" to "flask",
            "fastapi" to "fastapi",
            "httpx" to "httpx",
            "aiohttp" to "aiohttp",
            "scipy" to "scipy",
            "sympy" to "sympy",
            "torch" to "torch",
            "tensorflow" to "tensorflow",
        )
    }
    
    private val runtime = PythonRuntime(context)
    private var process: Process? = null
    private var outputWriter: OutputStreamWriter? = null
    private var outputReader: BufferedReader? = null
    
    private val _output = MutableStateFlow("")
    override val output: StateFlow<String> = _output
    
    private val _commandHistory = mutableListOf<String>()
    override val commandHistory: List<String> = _commandHistory
    
    private var isInitialized = false
    
    override fun startSession() {
        if (isInitialized) return
        
        scope.launch(Dispatchers.IO) {
            try {
                // 初始化 Python 运行时
                if (!runtime.initialize()) {
                    appendToOutput("Error: Failed to initialize Python runtime\n")
                    return@launch
                }
                
                // 启动 Python 解释器（交互模式）
                // 使用 NDK 编译的 python3 可执行文件，链接 libpython3.14.so
                val pythonBinary = runtime.getPythonBinary()
                val env = runtime.getEnvironment()
                
                Log.d(TAG, "Starting Python: ${pythonBinary.absolutePath}")
                Log.d(TAG, "Python binary exists: ${pythonBinary.exists()}")
                Log.d(TAG, "LD_LIBRARY_PATH: ${env["LD_LIBRARY_PATH"]}")
                Log.d(TAG, "PYTHONHOME: ${env["PYTHONHOME"]}")
                
                // 使用 shell 包装执行，确保 LD_LIBRARY_PATH 在动态链接器运行前设置
                // 这解决了 Android 上 ProcessBuilder.environment() 可能在链接器运行后才生效的问题
                val ldLibraryPath = env["LD_LIBRARY_PATH"] ?: ""
                val pythonHome = env["PYTHONHOME"] ?: ""
                val pythonPath = env["PYTHONPATH"] ?: ""
                
                // 检查库文件是否存在
                val libPython = File(runtime.getPythonBinary().parentFile, "libpython3.14.so")
                Log.d(TAG, "libpython3.14.so path: ${libPython.absolutePath}")
                Log.d(TAG, "libpython3.14.so exists: ${libPython.exists()}")
                
                val shellCommand = buildString {
                    append("export LD_LIBRARY_PATH='$ldLibraryPath' && ")
                    append("export PYTHONHOME='$pythonHome' && ")
                    append("export PYTHONPATH='$pythonPath' && ")
                    // pip 所需环境变量
                    append("export HOME='${env["HOME"] ?: context.filesDir.absolutePath}' && ")
                    append("export TMPDIR='${env["TMPDIR"] ?: context.cacheDir.absolutePath + "/python_tmp"}' && ")
                    append("export PYTHONUSERBASE='${env["PYTHONUSERBASE"] ?: context.filesDir.absolutePath + "/pip_packages"}' && ")
                    env["SSL_CERT_FILE"]?.let { append("export SSL_CERT_FILE='$it' && ") }
                    env["REQUESTS_CA_BUNDLE"]?.let { append("export REQUESTS_CA_BUNDLE='$it' && ") }
                    append("export PYTHONDONTWRITEBYTECODE=1 && ")
                    append("export PIP_USER=1 && ")
                    append("exec '${pythonBinary.absolutePath}' -i -u")
                }
                
                Log.d(TAG, "Full shell command: $shellCommand")
                
                val processBuilder = ProcessBuilder()
                    .command("/system/bin/sh", "-c", shellCommand)
                    .directory(context.filesDir)
                
                processBuilder.redirectErrorStream(true)  // 合并 stderr 到 stdout
                
                process = processBuilder.start()
                
                // 等待一小段时间让进程启动
                delay(200)
                
                // 检查进程是否还在运行（交互式 Shell 应该持续运行）
                if (process?.isAlive != true) {
                    val exitCode = process?.exitValue() ?: -1
                    val errorOutput = try {
                        BufferedReader(InputStreamReader(process?.inputStream)).readText()
                    } catch (e: Exception) {
                        "Unable to read output"
                    }
                    
                    Log.e(TAG, "Python shell exited immediately with code $exitCode")
                    Log.e(TAG, "Output: $errorOutput")
                    
                    appendToOutput("Error: Python shell exited (code: $exitCode)\n")
                    if (errorOutput.isNotEmpty()) {
                        appendToOutput("$errorOutput\n")
                    }
                    appendToOutput("\nDebug info:\n")
                    appendToOutput("  LD_LIBRARY_PATH: $ldLibraryPath\n")
                    appendToOutput("  Binary: ${pythonBinary.absolutePath}\n")
                    appendToOutput("  libpython3.14.so exists: ${libPython.exists()}\n")
                    return@launch
                }
                
                // 获取输入输出流
                outputWriter = OutputStreamWriter(process!!.outputStream)
                outputReader = BufferedReader(InputStreamReader(process!!.inputStream))
                
                // 启动输出读取协程
                startOutputReader()
                
                // 初始化 pip 支持（在 Python 中导入 pip 模块）
                setupPipSupport()
                
                // 显示详细欢迎信息
                appendToOutput("Python ${runtime.getVersion()} ready.\n")
                appendToOutput("Type 'help' for more information.\n")
                appendToOutput("TIP: Use 'pip install <package>' to install packages.\n")
                appendToOutput("TIP: Missing modules will be auto-installed on import.\n")
                appendToOutput(">>> ")
                
                isInitialized = true
                Log.d(TAG, "Python shell session started successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting Python session", e)
                appendToOutput("Error: ${e.message}\n")
            }
        }
    }
    
    /**
     * 启动输出读取协程
     */
    private fun startOutputReader() {
        val reader = outputReader ?: return
        
        scope.launch(Dispatchers.IO) {
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    appendToOutput("$line\n")
                }
            } catch (e: Exception) {
                if (isInitialized) {
                    Log.d(TAG, "Output reader stopped", e)
                }
            }
        }
    }
    
    override fun sendCommand(command: String) {
        if (command.isBlank()) {
            sendCommand("\n")  // 发送空行
            return
        }
        
        if (command.isNotBlank()) {
            _commandHistory.add(command)
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                if (!isInitialized) {
                    startSession()
                    delay(500)  // 等待初始化
                }
                
                val writer = outputWriter
                val currentProcess = process
                
                if (writer == null) {
                    appendToOutput("Error: Python session not available (writer is null)\n")
                    Log.w(TAG, "Writer is null")
                    return@launch
                }
                
                if (currentProcess == null) {
                    appendToOutput("Error: Python session not available (process is null)\n")
                    Log.w(TAG, "Process is null")
                    return@launch
                }
                
                if (!currentProcess.isAlive) {
                    val exitCode = try {
                        currentProcess.exitValue()
                    } catch (e: IllegalThreadStateException) {
                        -1
                    }
                    appendToOutput("Error: Python session not available (process exited with code $exitCode)\n")
                    Log.w(TAG, "Process is not alive, exit code: $exitCode")
                    isInitialized = false
                    return@launch
                }
                
                // 处理特殊命令
                val processedCommand = when {
                    // 直接识别 pip install xxx（不需要 ! 前缀）
                    command.startsWith("pip ") || command.startsWith("pip3 ") -> {
                        val pipArgs = command.substringAfter(" ").trim()
                        if (pipArgs.isEmpty()) {
                            "import subprocess, sys; subprocess.run([sys.executable, '-m', 'pip'])"
                        } else {
                            val argsList = pipArgs.split(" ").filter { it.isNotEmpty() }
                            val argsStr = argsList.joinToString(", ") { "'$it'" }
                            "import subprocess, sys; subprocess.run([sys.executable, '-m', 'pip', $argsStr])"
                        }
                    }
                    command.startsWith("!pip ") -> {
                        // 将 !pip install xxx 转换为 python -m pip 命令
                        val pipArgs = command.substring(5).trim()  // 去掉 "!pip "
                        if (pipArgs.isEmpty()) {
                            "import subprocess, sys; subprocess.run([sys.executable, '-m', 'pip'])"
                        } else {
                            // 将参数分割并正确转义
                            val argsList = pipArgs.split(" ").filter { it.isNotEmpty() }
                            val argsStr = argsList.joinToString(", ") { "'$it'" }
                            "import subprocess, sys; subprocess.run([sys.executable, '-m', 'pip', $argsStr])"
                        }
                    }
                    command == "!pip" || command == "!pip --version" || command == "pip --version" -> {
                        "import subprocess, sys; subprocess.run([sys.executable, '-m', 'pip', '--version'])"
                    }
                    command.startsWith("!") -> {
                        // 其他 shell 命令
                        val shellCmd = command.substring(1)
                        "import subprocess; subprocess.run('$shellCmd', shell=True)"
                    }
                    else -> command
                }
                
                // 进程正常，发送命令
                writer.write("$processedCommand\n")
                writer.flush()
                
                // 监测 ModuleNotFoundError 并自动安装
                if (!command.startsWith("!") && !command.startsWith("pip") && !command.startsWith("import subprocess")) {
                    delay(1500)  // 等待命令执行
                    checkAndAutoInstall(command)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending command", e)
                appendToOutput("Error: ${e.message}\n")
            }
        }
    }
    
    /**
     * 检测 ModuleNotFoundError 并自动安装缺失模块
     */
    private suspend fun checkAndAutoInstall(originalCommand: String) {
        val currentOutput = _output.value
        val lastLines = currentOutput.lines().takeLast(10).joinToString("\n")
        
        // 匹配 ModuleNotFoundError: No module named 'xxx'
        val modulePattern = Regex("""ModuleNotFoundError: No module named '([^']+)'""")
        val match = modulePattern.find(lastLines) ?: return
        
        val moduleName = match.groupValues[1].split(".").first()  // 取顶层模块名
        
        // 常见模块名到 pip 包名的映射
        val packageName = MODULE_TO_PACKAGE[moduleName] ?: moduleName
        
        appendToOutput("\n🔄 检测到缺失模块 '$moduleName'，自动安装 '$packageName'...\n")
        
        val writer = outputWriter ?: return
        
        // 使用 pip 安装（pip 已通过 wheel 解压安装好）
        writer.write("import subprocess, sys; _r = subprocess.run([sys.executable, '-m', 'pip', 'install', '$packageName'], capture_output=True, text=True); print(_r.stdout if _r.returncode == 0 else _r.stderr)\n")
        writer.flush()
        
        delay(5000)  // 等待安装
        
        // 检查安装结果
        val newOutput = _output.value
        val installResult = newOutput.lines().takeLast(5).joinToString("\n")
        
        if (installResult.contains("Successfully installed") || installResult.contains("already satisfied")) {
            appendToOutput("✅ '$packageName' 安装成功！请重新执行命令。\n")
            // 自动重新执行原命令
            writer.write("$originalCommand\n")
            writer.flush()
        } else {
            appendToOutput("⚠️ 自动安装可能失败，请手动尝试: pip install $packageName\n")
        }
    }
    
    override fun close() {
        try {
            outputWriter?.close()
            outputReader?.close()
            process?.destroy()
            process = null
            isInitialized = false
            Log.d(TAG, "Python shell session closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing session", e)
        }
    }
    
    override fun clearOutput() {
        _output.value = ""
    }
    
    /**
     * 设置 pip 支持 - 直接解压 pip wheel（不使用 ensurepip 子进程）
     * 然后在 REPL 中确保 site-packages 在 sys.path 中
     */
    private fun setupPipSupport() {
        scope.launch(Dispatchers.IO) {
            try {
                // 先在 Kotlin 侧直接解压 pip wheel（最可靠，不依赖子进程）
                val pipOk = runtime.ensurePipInstalled()
                if (!pipOk) {
                    appendToOutput("[Warning: pip wheel extraction failed]\n")
                    return@launch
                }
                
                delay(1000)  // 等待 Python 启动完成
                
                val writer = outputWriter ?: return@launch
                
                // 获取路径
                val userSitePackages = runtime.getUserSitePackages()
                val stdlibSitePackages = runtime.getStdlibSitePackages()
                
                // 写入临时脚本确保 sys.path 包含 site-packages
                val setupScript = File(context.filesDir, "_pip_setup.py")
                setupScript.writeText("""
import sys, os

# 确保 stdlib site-packages（pip 安装位置）在 sys.path 中
for _p in ['${stdlibSitePackages}', '${userSitePackages}']:
    os.makedirs(_p, exist_ok=True)
    if _p not in sys.path:
        sys.path.insert(0, _p)

# 验证 pip
try:
    import pip
    print(f'pip {pip.__version__} ready')
except ImportError:
    print('Warning: pip module not found in sys.path')
    print('sys.path:', sys.path[:5])
""".trimIndent())
                
                appendToOutput("[Setting up pip...]\n")
                writer.write("exec(open('${setupScript.absolutePath}').read())\n")
                writer.flush()
                
                Log.d(TAG, "Pip setup dispatched (wheel pre-extracted)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to setup pip support", e)
            }
        }
    }
    
    override fun appendToOutput(text: String) {
        val current = _output.value
        // 限制输出大小，避免内存问题
        val maxSize = 100000
        if (current.length > maxSize) {
            _output.value = current.takeLast(maxSize / 2) + text
        } else {
            _output.value = current + text
        }
    }
    
    /**
     * 检查 Python Shell 会话是否已就绪
     */
    override fun isReady(): Boolean {
        return isInitialized && process?.isAlive == true
    }
}
