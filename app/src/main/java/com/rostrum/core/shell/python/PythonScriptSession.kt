package com.rostrum.core.shell.python

import android.content.Context
import android.util.Log
import com.rostrum.core.shell.IShellSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.File

/**
 * Python 脚本执行会话
 * 
 * 专门用于执行 Python 脚本文件，每次执行创建独立的 Python 进程
 * 支持交互式输入（如 input() 函数）
 * 
 * 与 PythonShellSession 的区别：
 * - PythonShellSession: 交互式 REPL (python3 -i -u)
 * - PythonScriptSession: 脚本执行 (python3 script.py)
 */
class PythonScriptSession(
    private val context: Context,
    private val scope: CoroutineScope,
    private val scriptPath: String,
    private val args: List<String> = emptyList()
) : IShellSession {
    
    companion object {
        private const val TAG = "PythonScriptSession"
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
    private var scriptFinished = false
    
    override fun startSession() {
        if (isInitialized) return
        
        scope.launch(Dispatchers.IO) {
            try {
                // 验证脚本文件
                val scriptFile = File(scriptPath)
                if (!scriptFile.exists()) {
                    appendToOutput("Error: Script not found: $scriptPath\n")
                    Log.e(TAG, "Script file not found: $scriptPath")
                    return@launch
                }
                
                if (!scriptFile.isFile) {
                    appendToOutput("Error: Not a file: $scriptPath\n")
                    Log.e(TAG, "Path is not a file: $scriptPath")
                    return@launch
                }
                
                // 初始化 Python 运行时
                if (!runtime.initialize()) {
                    appendToOutput("Error: Failed to initialize Python runtime\n")
                    Log.e(TAG, "Failed to initialize Python runtime")
                    return@launch
                }
                
                val pythonBinary = runtime.getPythonBinary()
                val env = runtime.getEnvironment()
                
                // 构建命令: python3 script.py [args...]
                Log.d(TAG, "Executing script: $scriptPath with args: $args")
                Log.d(TAG, "Working directory: ${scriptFile.parentFile?.absolutePath}")
                Log.d(TAG, "Python binary: ${pythonBinary.absolutePath}")
                Log.d(TAG, "Python binary exists: ${pythonBinary.exists()}")
                Log.d(TAG, "LD_LIBRARY_PATH: ${env["LD_LIBRARY_PATH"]}")
                Log.d(TAG, "PYTHONHOME: ${env["PYTHONHOME"]}")
                
                // 检查库文件是否存在
                val libPython = File(pythonBinary.parentFile, "libpython3.14.so")
                Log.d(TAG, "libpython3.14.so path: ${libPython.absolutePath}")
                Log.d(TAG, "libpython3.14.so exists: ${libPython.exists()}")
                
                appendToOutput("Executing: ${scriptFile.name}")
                if (args.isNotEmpty()) {
                    appendToOutput(" ${args.joinToString(" ")}")
                }
                appendToOutput("\n")
                appendToOutput("---\n")
                
                // 使用 shell 包装执行，确保 LD_LIBRARY_PATH 在动态链接器运行前设置
                // 这解决了 Android 上 ProcessBuilder.environment() 可能在链接器运行后才生效的问题
                val ldLibraryPath = env["LD_LIBRARY_PATH"] ?: ""
                val pythonHome = env["PYTHONHOME"] ?: ""
                val pythonPath = env["PYTHONPATH"] ?: ""
                
                // 构建带参数的 shell 命令
                val argsStr = args.joinToString(" ") { "'$it'" }
                val shellCommand = buildString {
                    append("export LD_LIBRARY_PATH='$ldLibraryPath' && ")
                    append("export PYTHONHOME='$pythonHome' && ")
                    append("export PYTHONPATH='$pythonPath' && ")
                    // pip 所需环境变量 - 确保脚本中 import 第三方包能正常工作
                    append("export HOME='${env["HOME"] ?: context.filesDir.absolutePath}' && ")
                    append("export TMPDIR='${env["TMPDIR"] ?: context.cacheDir.absolutePath + "/python_tmp"}' && ")
                    append("export PYTHONUSERBASE='${env["PYTHONUSERBASE"] ?: context.filesDir.absolutePath + "/pip_packages"}' && ")
                    env["SSL_CERT_FILE"]?.let { append("export SSL_CERT_FILE='$it' && ") }
                    env["REQUESTS_CA_BUNDLE"]?.let { append("export REQUESTS_CA_BUNDLE='$it' && ") }
                    append("export PYTHONDONTWRITEBYTECODE=1 && ")
                    append("exec '${pythonBinary.absolutePath}' '$scriptPath'")
                    if (args.isNotEmpty()) {
                        append(" $argsStr")
                    }
                }
                
                Log.d(TAG, "Full shell command: $shellCommand")
                
                // 启动 Python 脚本进程
                val processBuilder = ProcessBuilder()
                    .command("/system/bin/sh", "-c", shellCommand)
                    .directory(scriptFile.parentFile ?: context.filesDir)
                
                processBuilder.redirectErrorStream(true)  // 合并 stderr 到 stdout
                
                process = processBuilder.start()
                
                // 等待一小段时间让进程启动
                delay(50)
                
                // 检查进程状态
                // 注意：脚本可能执行很快就完成了（exitCode=0），这是正常情况
                if (process?.isAlive != true) {
                    val exitCode = process?.exitValue() ?: -1
                    val output = try {
                        BufferedReader(InputStreamReader(process?.inputStream)).readText()
                    } catch (e: Exception) {
                        ""
                    }
                    
                    if (exitCode == 0) {
                        // 脚本成功执行完成（可能是简单脚本，执行很快）
                        Log.d(TAG, "Python script completed quickly with exit code 0")
                        if (output.isNotEmpty()) {
                            appendToOutput(output)
                            if (!output.endsWith("\n")) appendToOutput("\n")
                        }
                        appendToOutput("---\n")
                        appendToOutput("[Script finished with exit code: 0]\n")
                        appendToOutput("(Type 'exit' to return to System Shell)\n")
                        scriptFinished = true
                        isInitialized = true
                        return@launch
                    } else {
                        // 脚本执行失败
                        Log.e(TAG, "Python process exited with error code $exitCode")
                        Log.e(TAG, "Output: $output")
                        appendToOutput("Error: Python process exited with code $exitCode\n")
                        if (output.isNotEmpty()) {
                            appendToOutput("Output: $output\n")
                        }
                        scriptFinished = true
                        return@launch
                    }
                }
                
                // 获取输入输出流
                outputWriter = OutputStreamWriter(process!!.outputStream)
                outputReader = BufferedReader(InputStreamReader(process!!.inputStream))
                
                // 启动输出读取协程
                startOutputReader()
                
                // 监控进程结束
                scope.launch(Dispatchers.IO) {
                    try {
                        val exitCode = process?.waitFor() ?: -1
                        withContext(Dispatchers.Main) {
                            scriptFinished = true
                            appendToOutput("\n---\n")
                            appendToOutput("[Script finished with exit code: $exitCode]\n")
                            appendToOutput("(Type 'exit' to return to System Shell)\n")
                            Log.d(TAG, "Script execution finished with exit code: $exitCode")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error waiting for process", e)
                    }
                }
                
                isInitialized = true
                Log.d(TAG, "Python script session started successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting script session", e)
                appendToOutput("Error: ${e.message}\n")
                scriptFinished = true
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
                if (isInitialized && !scriptFinished) {
                    Log.d(TAG, "Output reader stopped", e)
                }
            }
        }
    }
    
    override fun sendCommand(command: String) {
        if (command.isBlank()) {
            // 发送空行
            sendInput("\n")
            return
        }
        
        _commandHistory.add(command)
        
        // 如果脚本已结束，忽略输入
        if (scriptFinished) {
            Log.d(TAG, "Script has finished, ignoring input: $command")
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                if (!isInitialized) {
                    Log.w(TAG, "Session not initialized")
                    return@launch
                }
                
                val writer = outputWriter
                val currentProcess = process
                
                if (writer == null) {
                    Log.w(TAG, "Writer is null")
                    return@launch
                }
                
                if (currentProcess == null || !currentProcess.isAlive) {
                    Log.w(TAG, "Process is not alive")
                    scriptFinished = true
                    return@launch
                }
                
                // 发送输入到脚本进程
                writer.write("$command\n")
                writer.flush()
                
                Log.d(TAG, "Sent input to script: $command")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending input to script", e)
                appendToOutput("Error sending input: ${e.message}\n")
            }
        }
    }
    
    /**
     * 直接发送输入（不添加到历史）
     */
    private fun sendInput(input: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val writer = outputWriter ?: return@launch
                writer.write(input)
                writer.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending input", e)
            }
        }
    }
    
    override fun close() {
        try {
            outputWriter?.close()
            outputReader?.close()
            process?.destroy()
            process = null
            isInitialized = false
            scriptFinished = true
            Log.d(TAG, "Python script session closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing script session", e)
        }
    }
    
    override fun clearOutput() {
        _output.value = ""
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
     * 检查脚本是否已结束
     */
    fun isScriptFinished(): Boolean {
        return scriptFinished
    }
    
    /**
     * 检查 Python 脚本会话是否已就绪
     */
    override fun isReady(): Boolean {
        return isInitialized && (process?.isAlive == true || scriptFinished)
    }
}
