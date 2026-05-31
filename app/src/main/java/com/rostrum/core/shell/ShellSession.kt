package com.rostrum.core.shell

import android.content.Context
import android.util.Log
import com.rostrum.core.shell.python.PythonRuntime
import com.rostrum.core.security.CommandSecurityValidator
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Super robust shell session using libsu
 * 支持 Python 运行时集成
 * 
 * 当 libsu 不可用时，自动降级到 ProcessBuilder 方式
 */
class ShellSession(
    private val scope: CoroutineScope,
    private val context: Context? = null  // 添加 Context 参数，用于 Python 集成
) : IShellSession {
    
    companion object {
        private const val TAG = "ShellSession"
    }
    
    private var shell: Shell? = null
    
    // ProcessBuilder 降级方案
    private var fallbackProcess: Process? = null
    private var fallbackWriter: OutputStreamWriter? = null
    private var useFallback = false
    
    private val pendingCommands = mutableListOf<String>()
    private var isInitializing = false
    
    // Python 运行时（如果 context 可用）
    private val pythonRuntime: PythonRuntime? = context?.let { PythonRuntime(it) }
    
    // Terminal output buffer
    private val _output = MutableStateFlow("")
    override val output: StateFlow<String> = _output

    // Command History
    private val _commandHistory = mutableListOf<String>()
    override val commandHistory: List<String> get() = _commandHistory

    override fun startSession() {
        if (shell != null || fallbackProcess != null || isInitializing) return
        isInitializing = true

        scope.launch(Dispatchers.IO) {
            try {
                // 获取应用私有目录作为工作目录
                val workDir = context?.filesDir?.absolutePath ?: "/data/local/tmp"
                
                var shellStarted = false
                
                // 方案一：尝试使用 libsu
                try {
                    Shell.enableVerboseLogging = true
                    
                    val newShell = Shell.Builder.create()
                        .setFlags(Shell.FLAG_REDIRECT_STDERR)
                        .build("sh")
                    
                    shell = newShell
                    shellStarted = true
                    appendToOutput("$ Shell started (Libsu Backend)\n")
                } catch (e: Exception) {
                    Log.w(TAG, "Libsu shell failed, falling back to ProcessBuilder", e)
                    shell = null
                }
                
                // 方案二：ProcessBuilder 降级
                if (!shellStarted) {
                    try {
                        val processBuilder = ProcessBuilder("/system/bin/sh")
                            .redirectErrorStream(true)
                        
                        if (context != null) {
                            processBuilder.directory(context.filesDir)
                        }
                        
                        fallbackProcess = processBuilder.start()
                        fallbackWriter = OutputStreamWriter(fallbackProcess!!.outputStream)
                        useFallback = true
                        shellStarted = true
                        
                        // 启动输出读取
                        startFallbackOutputReader()
                        
                        appendToOutput("$ Shell started (ProcessBuilder Backend)\n")
                    } catch (e2: Exception) {
                        Log.e(TAG, "ProcessBuilder shell also failed", e2)
                        appendToOutput("Error: 无法启动 Shell 会话\n")
                        appendToOutput("设备可能限制了 Shell 访问\n")
                        isInitializing = false
                        return@launch
                    }
                }
                
                isInitializing = false
                
                // Initialize Environment
                execInternal("export PATH=\$PATH:/system/bin:/system/xbin")
                execInternal("cd $workDir 2>/dev/null || true")
                execInternal("alias ll='ls -l'")
                execInternal("alias la='ls -la'")
                execInternal("alias bash='sh'")
                
                // 初始化 Python 环境（如果可用）
                if (pythonRuntime != null) {
                    setupPythonEnvironment()
                }
                
                // Print environment info
                execInternal("uname -a")
                execInternal("id")
                execInternal("echo \"Working dir: \$(pwd)\"")
                
                // Process pending
                synchronized(pendingCommands) {
                    pendingCommands.forEach { cmd ->
                        execInternal(cmd)
                    }
                    pendingCommands.clear()
                }
                
            } catch (e: Exception) {
                isInitializing = false
                appendToOutput("Error starting shell: ${e.message}\n")
            }
        }
    }
    
    /**
     * 设置 Python 环境
     * 将 Python 添加到 PATH 并设置必要的环境变量
     */
    private fun setupPythonEnvironment() {
        val runtime = pythonRuntime ?: return
        
        scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing Python runtime...")
                
                // 异步初始化 Python 运行时
                if (runtime.initialize()) {
                    val pythonBinary = runtime.getPythonBinary()
                    val pythonDir = pythonBinary.parentFile
                    val env = runtime.getEnvironment()
                    
                    // 添加 Python 到 PATH
                    if (pythonDir != null) {
                        execInternal("export PATH=\$PATH:${pythonDir.absolutePath}")
                    }
                    
                    // 设置 Python 环境变量
                    env.forEach { (key, value) ->
                        execInternal("export $key=\"$value\"")
                    }
                    
                    // 创建 python 命令包装函数
                    val pythonHomeDir = pythonDir?.absolutePath ?: context?.filesDir?.absolutePath ?: "/data/data/com.rostrum/files"
                    val pythonWrapper = createPythonWrapper(pythonBinary.absolutePath, pythonHomeDir)
                    execInternal(pythonWrapper)
                    
                    // 安装 pip（使用 ensurepip 模块）
                    setupPip(pythonBinary.absolutePath, pythonHomeDir)
                    
                    // 创建 pip 命令的 alias（安装后应该存在）
                    val pipPath = pythonDir?.let { findPipPath(it) }
                    if (pipPath != null) {
                        execInternal("alias pip='$pipPath'")
                        execInternal("alias pip3='$pipPath'")
                        Log.d(TAG, "Pip configured: $pipPath")
                    } else {
                        Log.w(TAG, "Pip not found after setup")
                    }
                    
                    val version = runtime.getVersion()
                    appendToOutput("\n[Python $version ready (type 'python' to use)]\n")
                    Log.d(TAG, "Python environment setup complete: $version")
                } else {
                    Log.w(TAG, "Python runtime initialization failed")
                    appendToOutput("\n[Warning: Python runtime initialization failed]\n")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to setup Python environment", e)
                appendToOutput("\n[Warning: Failed to setup Python: ${e.message}]\n")
            }
        }
    }
    
    /**
     * 创建 Python 命令包装函数
     * 这个函数会在执行 python 命令前设置正确的环境变量
     * Python 二进制 (libpython_main.so) 在 native library 目录中，可以直接执行
     */
    private fun createPythonWrapper(pythonPath: String, pythonHomeDir: String): String {
        val runtime = pythonRuntime ?: return "python() { echo 'Python runtime not available'; }"
        val env = runtime.getEnvironment()
        val ldLibraryPath = env["LD_LIBRARY_PATH"] ?: ""
        val pythonPathEnv = env["PYTHONPATH"] ?: ""
        val home = env["HOME"] ?: context?.filesDir?.absolutePath ?: ""
        val tmpDir = env["TMPDIR"] ?: context?.cacheDir?.absolutePath?.let { "$it/python_tmp" } ?: ""
        val pythonUserBase = env["PYTHONUSERBASE"] ?: ""
        val sslCertFile = env["SSL_CERT_FILE"] ?: ""
        
        // 使用 shell 函数包装 python 命令
        // 在执行前设置必要的环境变量
        return """
            python() {
                local python_bin="$pythonPath"
                
                # 检查 Python 二进制是否存在
                if [ ! -f "${'$'}python_bin" ]; then
                    echo "Error: Python runtime not found at ${'$'}python_bin" >&2
                    return 1
                fi
                
                # 设置环境变量并执行 Python
                LD_LIBRARY_PATH="$ldLibraryPath" \
                PYTHONHOME="$pythonHomeDir" \
                PYTHONPATH="$pythonPathEnv" \
                HOME="$home" \
                TMPDIR="$tmpDir" \
                PYTHONUSERBASE="$pythonUserBase" \
                SSL_CERT_FILE="$sslCertFile" \
                REQUESTS_CA_BUNDLE="$sslCertFile" \
                PYTHONDONTWRITEBYTECODE=1 \
                PIP_USER=1 \
                "${'$'}python_bin" "${'$'}@"
            }
            
            # 同时创建 python3 别名
            alias python3='python'
        """.trimIndent()
    }
    
    /**
     * 安装 pip（直接解压 wheel，不使用 ensurepip 子进程）
     */
    private fun setupPip(pythonPath: String, pythonHomeDir: String) {
        try {
            val runtime = pythonRuntime ?: return
            
            Log.d(TAG, "Installing pip via direct wheel extraction...")
            appendToOutput("\n[Installing pip...]\n")
            
            if (runtime.ensurePipInstalled()) {
                Log.d(TAG, "Pip installed successfully via wheel extraction")
            } else {
                Log.w(TAG, "Pip wheel extraction failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup pip", e)
        }
    }
    
    /**
     * 查找 pip 可执行文件路径
     */
    private fun findPipPath(pythonDir: java.io.File): String? {
        // 可能的 pip 位置（按优先级）
        val possiblePaths = listOf(
            java.io.File(pythonDir, "bin/pip"),
            java.io.File(pythonDir, "bin/pip3"),
            java.io.File(pythonDir, "Scripts/pip.exe"),
            java.io.File(pythonDir, "Scripts/pip3.exe"),
            java.io.File(pythonDir, "local/bin/pip"),
            java.io.File(pythonDir, "local/bin/pip3")
        )
        
        for (path in possiblePaths) {
            if (path.exists() && path.canExecute()) {
                return path.absolutePath
            }
        }
        
        // 尝试查找 pip 模块并创建 wrapper
        val pipModulePath = java.io.File(pythonDir, "lib/python3.14/site-packages/pip/__main__.py")
        if (pipModulePath.exists()) {
            // 返回使用 python -m pip 的 wrapper
            return "python -m pip"
        }
        
        return null
    }

    override fun sendCommand(command: String) {
        if (command.isNotBlank()) {
            _commandHistory.add(command)
        }
        
        // 验证命令安全性
        if (!CommandSecurityValidator.validateCommand(command)) {
            appendToOutput("\n[Error: 命令包含危险字符或被列入黑名单: ${command.take(50)}]\n")
            Log.w(TAG, "Blocked dangerous command: ${command.take(100)}")
            return
        }

        if (shell == null && !useFallback) {
            if (isInitializing) {
                synchronized(pendingCommands) {
                    pendingCommands.add(command)
                }
                return
            } else {
                startSession()
                synchronized(pendingCommands) {
                    pendingCommands.add(command)
                }
                return
            }
        }
        
        scope.launch(Dispatchers.IO) {
            execInternal(command)
        }
    }

    private fun execInternal(command: String) {
        if (useFallback) {
            execFallback(command)
            return
        }
        
        val currentShell = shell ?: return
        try {
             appendToOutput("\n$ $command\n")
             
             // Create callback lists for streaming output
             val outList = CallbackList<String> { line -> appendToOutput(line + "\n") }
             val errList = CallbackList<String> { line -> appendToOutput("[ERR] " + line + "\n") }

             // Libsu newJob interface with streaming
             val result = currentShell.newJob()
                 .add(command)
                 .to(outList, errList)
                 .exec()
             
             // Debug info if everything is empty (and exit code is bad)
             if (outList.isEmpty() && errList.isEmpty()) {
                 if (!result.isSuccess) {
                     appendToOutput("[Exit Code: ${result.code} (No Output)]\n")
                 }
             }
             
        } catch (e: Exception) {
            appendToOutput("\nError sending command: ${e.message}\n")
        }
    }
    
    /**
     * ProcessBuilder 降级模式执行命令
     */
    private fun execFallback(command: String) {
        val writer = fallbackWriter ?: return
        try {
            appendToOutput("\n$ $command\n")
            writer.write("$command\n")
            writer.flush()
        } catch (e: Exception) {
            appendToOutput("\nError sending command: ${e.message}\n")
        }
    }
    
    /**
     * 启动 ProcessBuilder 降级模式的输出读取协程
     */
    private fun startFallbackOutputReader() {
        val process = fallbackProcess ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    appendToOutput("$line\n")
                }
            } catch (e: Exception) {
                if (fallbackProcess != null) {
                    Log.d(TAG, "Fallback output reader stopped", e)
                }
            }
        }
    }
    
    // Internal helper for streaming
    class CallbackList<E>(private val onAdd: (E) -> Unit) : java.util.ArrayList<E>() {
        override fun add(element: E): Boolean {
            onAdd(element)
            return super.add(element)
        }
        override fun addAll(elements: Collection<E>): Boolean {
            elements.forEach(onAdd)
            return super.addAll(elements)
        }
    }

    override fun close() {
        try {
             shell?.close()
        } catch (e: Exception) {
            com.rostrum.core.error.ErrorHandler.debug(e, "ShellSession.close")
        }
        shell = null
        
        // 清理降级模式资源
        try {
            fallbackWriter?.close()
            fallbackProcess?.destroy()
        } catch (e: Exception) {
            Log.d(TAG, "Fallback cleanup", e)
        }
        fallbackWriter = null
        fallbackProcess = null
        useFallback = false
    }

    override fun clearOutput() {
        _output.value = ""
    }

    /**
     * 追加文本到输出流（供外部调用，如显示 Python 执行结果）
     */
    override fun appendToOutput(text: String) {
        val current = _output.value
        if (current.length > 50000) {
             _output.value = current.takeLast(40000) + text
        } else {
             _output.value = current + text
        }
    }
    
    /**
     * 检查 Shell 会话是否已就绪
     */
    override fun isReady(): Boolean {
        return (shell != null || (useFallback && fallbackProcess?.isAlive == true)) && !isInitializing
    }
}
