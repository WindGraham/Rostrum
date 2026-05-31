package com.rostrum.core.shell.python

import android.content.Context
import com.rostrum.core.shell.IShellSession
import com.rostrum.core.shell.ShellProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Python Shell Provider
 * 提供内置 Python 运行时支持
 */
class PythonShellProvider(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : ShellProvider {
    
    companion object {
        private const val TAG = "PythonShellProvider"
    }
    
    private val runtime = PythonRuntime(context)
    
    override val id: String = "python.builtin"
    override val name: String = "Python Runtime"
    override val priority: Int = 300  // 最高优先级
    
    override val isAvailable: Boolean
        get() = runtime.isAvailable()
    
    override fun createSession(): IShellSession {
        return PythonShellSession(context, scope)
    }
    
    override fun supportsCommand(command: String): Boolean {
        // 支持所有命令（Python 是通用解释器）
        return true
    }
    
    /**
     * 初始化 Python 运行时
     * 应该在应用启动时调用
     */
    fun initialize(): Boolean {
        return runtime.initialize()
    }
    
    /**
     * 获取 Python 版本信息
     */
    fun getVersion(): String {
        return runtime.getVersion()
    }
}
