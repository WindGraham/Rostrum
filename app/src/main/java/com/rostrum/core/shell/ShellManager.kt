package com.rostrum.core.shell

import android.content.Context
import com.rostrum.core.shell.python.PythonShellProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Shell管理器
 * 统一管理不同的Shell实现
 * 
 * 当前实现：
 * - Python Shell（最高优先级，内置 Python 运行时）
 * - libsu 系统Shell（备选）
 * - 支持通过 ShellProvider 接口扩展其他Shell实现
 */
class ShellManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val libsuSession = ShellSession(scope, context)  // 传递 context 以支持 Python 集成
    private val providers = mutableListOf<ShellProvider>()
    
    // Python Provider（最高优先级，内置运行时）
    val pythonProvider = PythonShellProvider(context, scope)
    
    init {
        // 注册 Python Provider（最高优先级）
        registerProvider(pythonProvider)
        
        // 注册默认的 libsu Provider（带 Python 支持）
        registerProvider(LibsuShellProvider(scope, context))
        
        // 初始化 Python 运行时（异步）
        scope.launch {
            pythonProvider.initialize()
        }
    }
    
    /**
     * 注册Shell Provider
     */
    fun registerProvider(provider: ShellProvider) {
        providers.add(provider)
        // 按优先级排序
        providers.sortByDescending { it.priority }
    }
    
    /**
     * 获取所有可用的Shell Provider
     */
    fun getAvailableProviders(): List<ShellProvider> {
        return providers.filter { it.isAvailable }
    }
    
    /**
     * 获取所有注册的Shell Provider（包括不可用的）
     */
    fun getAllProviders(): List<ShellProvider> {
        return providers.toList()
    }
    
    /**
     * 根据ID获取特定的Shell Provider
     */
    fun getProvider(id: String): ShellProvider? {
        return providers.firstOrNull { it.id == id && it.isAvailable }
    }
    
    /**
     * 创建Shell会话
     * @param providerId 可选的Provider ID，如果为null则使用优先级最高的可用Provider
     */
    fun createSession(providerId: String? = null): IShellSession {
        val provider = if (providerId != null) {
            getProvider(providerId)
        } else {
            getAvailableProviders().firstOrNull()
        }
        
        return provider?.createSession() ?: LibsuShellSessionAdapter(libsuSession)
    }
    
    /**
     * 获取默认Shell会话
     * 使用优先级最高的可用Provider
     */
    fun getDefaultSession(): IShellSession {
        return createSession()
    }
    
    /**
     * 获取libsu Shell会话
     * 现在包含 Python 支持
     */
    fun getLibsuSession(): IShellSession {
        return LibsuShellSessionAdapter(ShellSession(scope, context))
    }
    
    /**
     * 检查命令是否可用
     */
    fun isCommandAvailable(command: String): Boolean {
        return providers.any { it.supportsCommand(command) }
    }
    
    /**
     * 获取 Python Shell 会话
     */
    fun getPythonSession(): IShellSession? {
        return if (pythonProvider.isAvailable) {
            pythonProvider.createSession()
        } else {
            null
        }
    }
    
    /**
     * Python 是否可用
     */
    fun isPythonAvailable(): Boolean {
        return pythonProvider.isAvailable
    }
    
    /**
     * 获取 Python 版本
     */
    fun getPythonVersion(): String {
        return pythonProvider.getVersion()
    }
    
    /**
     * 创建Python脚本执行会话
     * @param scriptPath 脚本文件路径
     * @param args 命令行参数
     * @return PythonScriptSession 实例，如果失败返回 null
     */
    fun createPythonScriptSession(
        scriptPath: String,
        args: List<String> = emptyList()
    ): com.rostrum.core.shell.python.PythonScriptSession? {
        return try {
            com.rostrum.core.shell.python.PythonScriptSession(
                context = context,
                scope = scope,
                scriptPath = scriptPath,
                args = args
            )
        } catch (e: Exception) {
            android.util.Log.e("ShellManager", "Failed to create Python script session", e)
            null
        }
    }
    
    /**
     * 关闭所有会话
     */
    fun shutdown() {
        // 清理资源
    }
}

/**
 * Libsu Shell Provider实现
 * 支持在系统 Shell 中集成 Python 运行时
 */
private class LibsuShellProvider(
    private val scope: CoroutineScope,
    private val context: Context  // 添加 context 参数以支持 Python 集成
) : ShellProvider {
    override val id: String = "libsu.system"
    override val name: String = "System Shell (libsu)"
    override val priority: Int = 100
    
    override val isAvailable: Boolean
        get() = true // libsu 总是可用
    
    override fun createSession(): IShellSession {
        return LibsuShellSessionAdapter(ShellSession(scope, context))  // 传递 context
    }
    
    override fun supportsCommand(command: String): Boolean {
        // libsu 支持所有系统Shell命令
        return true
    }
}

/**
 * Libsu ShellSession适配器
 * 将旧的ShellSession适配到IShellSession接口
 */
private class LibsuShellSessionAdapter(
    private val session: ShellSession
) : IShellSession {
    override val output = session.output
    override val commandHistory = session.commandHistory
    
    override fun startSession() {
        session.startSession()
    }
    
    override fun sendCommand(command: String) {
        session.sendCommand(command)
    }
    
    override fun close() {
        session.close()
    }
    
    override fun clearOutput() {
        session.clearOutput()
    }
    
    override fun appendToOutput(text: String) {
        session.appendToOutput(text)
    }
    
    override fun isReady(): Boolean {
        return session.isReady()
    }
}
