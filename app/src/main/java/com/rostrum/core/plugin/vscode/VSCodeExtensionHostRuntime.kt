package com.rostrum.core.plugin.vscode

import android.content.Context
import com.rostrum.core.plugin.PluginContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.io.File

/**
 * VSCode Extension Host运行时
 * 
 * 使用Rhino JavaScript引擎运行VSCode扩展
 * 
 * 注意：这是一个简化实现，完整的Extension Host需要大量VSCode代码
 */
class VSCodeExtensionHostRuntime(
    private val context: Context,
    private val extensionPath: String
) {
    
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rhinoContext = RhinoContext.enter()
    private val scope: Scriptable = rhinoContext.initStandardObjects()
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private val activeExtensions = mutableMapOf<String, ExtensionRuntime>()
    
    // 创建一个 VSCodeExtensionHost 实例用于 ExtensionRuntime
    private val extensionHost = VSCodeExtensionHost(context)
    
    init {
        setupRhino()
    }
    
    /**
     * 设置Rhino环境
     */
    private fun setupRhino() {
        // 设置全局对象
        ScriptableObject.putProperty(scope, "console", createConsoleObject())
        ScriptableObject.putProperty(scope, "require", createRequireFunction())
    }
    
    /**
     * 创建console对象
     */
    private fun createConsoleObject(): Scriptable {
        val consoleObj = rhinoContext.newObject(scope)
        ScriptableObject.putProperty(consoleObj, "log", createRhinoFunction { args ->
            android.util.Log.d("VSCodeExtensionHost", args?.joinToString() ?: "")
            null
        })
        return consoleObj
    }
    
    /**
     * 创建require函数（简化版）
     */
    private fun createRequireFunction(): org.mozilla.javascript.Function {
        return createRhinoFunction { args ->
            val moduleName = args?.get(0)?.toString() ?: return@createRhinoFunction null
            // TODO: 实现模块加载
            null
        }
    }
    
    /**
     * 创建完整的Rhino Function实现
     * 实现所有必需的Scriptable和Function接口方法
     */
    private fun createRhinoFunction(handler: (Array<out Any?>?) -> Any?): org.mozilla.javascript.Function {
        return object : org.mozilla.javascript.Function {
            override fun call(cx: RhinoContext?, scope: Scriptable?, thisObj: Scriptable?, args: Array<out Any?>?): Any? {
                return handler(args)
            }
            
            override fun construct(cx: RhinoContext?, scope: Scriptable?, args: Array<out Any?>?): Scriptable? {
                // 构造函数调用（使用new关键字时）
                // 对于普通函数，返回null表示不支持构造函数调用
                return null
            }
            
            override fun getClassName(): String = "Function"
            
            // Scriptable接口方法
            override fun get(name: String, start: Scriptable?): Any? = org.mozilla.javascript.Scriptable.NOT_FOUND
            override fun get(index: Int, start: Scriptable?): Any? = org.mozilla.javascript.Scriptable.NOT_FOUND
            override fun has(name: String, start: Scriptable?): Boolean = false
            override fun has(index: Int, start: Scriptable?): Boolean = false
            override fun put(name: String, start: Scriptable?, value: Any?) {}
            override fun put(index: Int, start: Scriptable?, value: Any?) {}
            override fun delete(name: String) {}
            override fun delete(index: Int) {}
            override fun getPrototype(): Scriptable? = null
            override fun setPrototype(prototype: Scriptable?) {}
            override fun getParentScope(): Scriptable? = null
            override fun setParentScope(parent: Scriptable?) {}
            override fun getIds(): Array<Any> = emptyArray()
            override fun getDefaultValue(typeHint: Class<*>?): Any? = null
            override fun hasInstance(instance: Scriptable?): Boolean = false
        }
    }
    
    /**
     * 初始化Extension Host
     */
    suspend fun initialize(pluginContext: PluginContext) {
        if (_isInitialized.value) {
            return
        }
        
        // TODO: 加载VSCode Extension Host核心代码
        // 1. 加载extHost.protocol.ts（RPC协议）
        // 2. 加载extHostExtensionService.ts（扩展服务）
        // 3. 设置API适配器
        
        _isInitialized.value = true
    }
    
    /**
     * 激活扩展
     */
    suspend fun activateExtension(
        extensionId: String,
        extensionPath: String,
        activationEvents: List<String>,
        pluginContext: PluginContext
    ) {
        if (activeExtensions.containsKey(extensionId)) {
            return
        }
        
        // 读取扩展的package.json
        val packageJsonFile = File(extensionPath, "package.json")
        if (!packageJsonFile.exists()) {
            throw IllegalArgumentException("package.json not found: $extensionPath")
        }
        
        // 读取扩展的主文件
        val packageJson = org.json.JSONObject(packageJsonFile.readText())
        val mainFile = packageJson.optString("main", "extension.js")
        val extensionMainFile = File(extensionPath, mainFile)
        
        if (!extensionMainFile.exists()) {
            throw IllegalArgumentException("Extension main file not found: $extensionMainFile")
        }
        
        // 执行扩展代码
        val extensionCode = extensionMainFile.readText()
        val extensionRuntime = com.rostrum.core.plugin.vscode.ExtensionRuntime(extensionId, extensionPath, pluginContext, extensionHost)
        
        try {
            // 创建扩展作用域
            val extensionScope = rhinoContext.newObject(scope)
            
            // 注入vscode API
            injectVSCodeAPI(extensionScope, pluginContext)
            
            // 执行扩展代码
            rhinoContext.evaluateString(extensionScope, extensionCode, extensionMainFile.name, 1, null)
            
            // 调用activate函数
            val activateFunc = ScriptableObject.getProperty(extensionScope, "activate")
            if (activateFunc is org.mozilla.javascript.Function) {
                val activateContext = createActivateContext(pluginContext)
                activateFunc.call(rhinoContext, extensionScope, extensionScope, arrayOf(activateContext))
            }
            
            activeExtensions[extensionId] = extensionRuntime
        } catch (e: Exception) {
            android.util.Log.e("VSCodeExtensionHost", "Failed to activate extension: $extensionId", e)
            throw e
        }
    }
    
    /**
     * 停用扩展
     */
    suspend fun deactivateExtension(extensionId: String) {
        val extensionRuntime = activeExtensions.remove(extensionId) ?: return
        
        // 调用扩展的stop函数
        extensionRuntime.stop()
    }
    
    /**
     * 注入VSCode API到扩展作用域
     */
    private fun injectVSCodeAPI(scope: Scriptable, pluginContext: PluginContext) {
        val vscodeAPI = createVSCodeAPIObject(pluginContext)
        ScriptableObject.putProperty(scope, "vscode", vscodeAPI)
    }
    
    /**
     * 创建VSCode API对象
     */
    private fun createVSCodeAPIObject(pluginContext: PluginContext): Scriptable {
        val vscodeObj = rhinoContext.newObject(scope)
        
        // workspace API
        val workspaceObj = rhinoContext.newObject(scope)
        ScriptableObject.putProperty(vscodeObj, "workspace", workspaceObj)
        
        // window API
        val windowObj = rhinoContext.newObject(scope)
        ScriptableObject.putProperty(vscodeObj, "window", windowObj)
        
        // commands API
        val commandsObj = rhinoContext.newObject(scope)
        ScriptableObject.putProperty(vscodeObj, "commands", commandsObj)
        
        return vscodeObj
    }
    
    /**
     * 创建activate上下文
     */
    private fun createActivateContext(pluginContext: PluginContext): Scriptable {
        val contextObj = rhinoContext.newObject(scope)
        
        // subscriptions数组
        val subscriptionsArray = rhinoContext.newArray(scope, 0)
        ScriptableObject.putProperty(contextObj, "subscriptions", subscriptionsArray)
        
        return contextObj
    }
    
    /**
     * 清理资源
     */
    fun dispose() {
        activeExtensions.values.forEach { it.stop() }
        activeExtensions.clear()
        RhinoContext.exit()
    }
}

// ExtensionRuntime 已在 VSCodeExtensionHost.kt 中定义，这里注释掉避免重复
/*
/**
 * 扩展运行时
 */
class ExtensionRuntime(
    val extensionId: String,
    val extensionPath: String
) {
    fun deactivate() {
        // 清理扩展资源
    }
}
*/

