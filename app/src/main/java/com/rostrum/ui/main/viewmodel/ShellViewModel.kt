package com.rostrum.ui.main.viewmodel

import android.util.Log
import com.rostrum.core.shell.IShellSession
import com.rostrum.core.shell.ShellManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shell 相关功能的辅助类
 * 负责管理终端会话、Shell 模式切换和 Python 脚本执行
 * 
 * 作为 MainViewModel 的内部委托使用
 * 
 * 通过构造函数注入 ShellManager，可配合 ShellManagerFactory 使用
 */
class ShellViewModel(
    val shellManager: ShellManager,
    private val scope: CoroutineScope
) {
    
    companion object {
        private const val TAG = "ShellViewModel"
    }
    
    // Terminal Shell Session（支持动态切换）
    private val _shellSession = MutableStateFlow<IShellSession>(shellManager.getLibsuSession())
    val shellSession: StateFlow<IShellSession> = _shellSession
    
    // 当前 Shell 模式
    enum class ShellMode {
        SYSTEM,        // 系统 Shell（libsu）
        PYTHON,        // Python 交互式 Shell (REPL)
        PYTHON_SCRIPT  // Python 脚本执行模式
    }
    
    private val _shellMode = MutableStateFlow(ShellMode.SYSTEM)
    val shellMode: StateFlow<ShellMode> = _shellMode
    
    /**
     * 切换 Shell 模式
     * @param mode 目标模式
     * @param onSwitched 切换完成后的回调
     */
    fun switchShellMode(
        mode: ShellMode, 
        onSwitched: ((IShellSession) -> Unit)? = null
    ) {
        if (_shellMode.value == mode) {
            onSwitched?.invoke(_shellSession.value)
            return
        }
        
        scope.launch {
            try {
                // 关闭当前会话
                _shellSession.value.close()
                
                // 创建新会话
                val newSession = when (mode) {
                    ShellMode.SYSTEM -> shellManager.getLibsuSession()
                    ShellMode.PYTHON -> {
                        shellManager.getPythonSession() ?: run {
                            Log.w(TAG, "Python Shell not available, falling back to system shell")
                            onSwitched?.invoke(_shellSession.value)
                            return@launch
                        }
                    }
                    ShellMode.PYTHON_SCRIPT -> {
                        // PYTHON_SCRIPT模式不应该通过switchShellMode切换
                        // 应该使用executePythonScript方法
                        Log.w(TAG, "Cannot switch to PYTHON_SCRIPT mode directly")
                        onSwitched?.invoke(_shellSession.value)
                        return@launch
                    }
                }
                
                // 启动新会话
                newSession.startSession()
                
                // 更新状态
                _shellSession.value = newSession
                _shellMode.value = mode
                
                Log.d(TAG, "Switched to ${mode.name} shell mode")
                
                // 等待会话就绪（Python Shell 需要额外时间）
                if (mode == ShellMode.PYTHON) {
                    // 使用超时等待而非固定延迟
                    withTimeoutOrNull(5000) {
                        while (!newSession.isReady()) {
                            delay(100)
                        }
                    }
                } else {
                    delay(100)
                }
                
                // 调用回调
                onSwitched?.invoke(newSession)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to switch shell mode", e)
                onSwitched?.invoke(_shellSession.value)
            }
        }
    }
    
    /**
     * 执行Python脚本(创建独立会话)
     * @param scriptPath 脚本路径
     * @param args 命令行参数
     * @param onStarted 会话启动回调
     */
    fun executePythonScript(
        scriptPath: String,
        args: List<String> = emptyList(),
        onStarted: ((IShellSession) -> Unit)? = null
    ) {
        scope.launch {
            try {
                // 关闭当前会话
                _shellSession.value.close()
                
                // 创建脚本执行会话
                val scriptSession = shellManager.createPythonScriptSession(scriptPath, args)
                if (scriptSession == null) {
                    Log.e(TAG, "Failed to create script session")
                    // 回退到系统Shell
                    _shellSession.value = shellManager.getLibsuSession()
                    _shellMode.value = ShellMode.SYSTEM
                    _shellSession.value.startSession()
                    _shellSession.value.appendToOutput("Error: Failed to create Python script session\n")
                    return@launch
                }
                
                // 启动会话
                scriptSession.startSession()
                
                // 更新状态
                _shellSession.value = scriptSession
                _shellMode.value = ShellMode.PYTHON_SCRIPT
                
                Log.d(TAG, "Started Python script session: $scriptPath")
                
                // 等待脚本进程启动
                withTimeoutOrNull(5000) {
                    while (!scriptSession.isReady()) {
                        delay(50)
                    }
                }
                
                // 调用回调
                onStarted?.invoke(scriptSession)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute Python script", e)
                // 回退到系统Shell
                _shellSession.value = shellManager.getLibsuSession()
                _shellMode.value = ShellMode.SYSTEM
                _shellSession.value.startSession()
            }
        }
    }
    
    /**
     * 启动会话（如果尚未启动）
     */
    fun startSessionIfNeeded() {
        if (_shellSession.value.output.value.isEmpty()) {
            _shellSession.value.startSession()
        }
    }
    
    /**
     * 发送命令到当前会话
     */
    fun sendCommand(command: String) {
        _shellSession.value.sendCommand(command)
    }
    
    /**
     * 切换到指定目录并列出文件
     */
    fun cdAndList(path: String) {
        _shellSession.value.sendCommand("cd \"$path\"")
        _shellSession.value.sendCommand("ls")
    }
    
    /**
     * 关闭当前会话
     */
    fun closeSession() {
        _shellSession.value.close()
    }
    
    /**
     * 销毁资源（在 MainViewModel.onCleared 中调用）
     */
    fun destroy() {
        _shellSession.value.close()
    }
}
