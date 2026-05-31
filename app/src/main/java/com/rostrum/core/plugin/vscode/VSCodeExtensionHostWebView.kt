package com.rostrum.core.plugin.vscode

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * VSCode Extension Host WebView实现
 * 
 * 使用WebView运行VSCode Extension Host（JavaScript版本）
 */
class VSCodeExtensionHostWebView(
    private val webView: WebView,
    private val extensionPath: String
) {
    
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    
    private val messageChannel = Channel<String>(Channel.UNLIMITED)
    
    init {
        setupWebView()
    }
    
    /**
     * 设置WebView
     */
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
        }
        
        // 添加JavaScript接口
        webView.addJavascriptInterface(ExtensionHostBridge(), "android")
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                _isReady.value = true
            }
        }
    }
    
    /**
     * 加载Extension Host
     */
    fun loadExtensionHost() {
        // TODO: 加载VSCode Extension Host的HTML/JS文件
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <script src="extensionHost.js"></script>
            </head>
            <body>
                <script>
                    // Extension Host初始化代码
                    window.extensionHost = new ExtensionHost();
                    window.extensionHost.initialize();
                </script>
            </body>
            </html>
        """.trimIndent()
        
        webView.loadDataWithBaseURL("file://$extensionPath/", html, "text/html", "UTF-8", null)
    }
    
    /**
     * 激活扩展
     */
    fun activateExtension(extensionId: String, extensionPath: String) {
        webView.evaluateJavascript(
            "window.extensionHost.activateExtension('$extensionId', '$extensionPath');",
            null
        )
    }
    
    /**
     * 执行JavaScript代码
     */
    fun evaluateJavaScript(script: String, callback: ((String?) -> Unit)? = null) {
        webView.evaluateJavascript(script) { result ->
            callback?.invoke(result)
        }
    }
    
    /**
     * Extension Host桥接接口
     * 用于JavaScript与Kotlin之间的通信
     * 
     * 注意：接口名必须是"android"，与extensionHostWorker.ts中的android对象匹配
     */
    inner class ExtensionHostBridge {
        /**
         * 接收来自Extension Host的消息
         * 对应JavaScript中的android.postMessage()
         */
        @JavascriptInterface
        fun postMessage(message: String) {
            // 接收来自Extension Host的消息
            messageChannel.trySend(message)
        }
        
        /**
         * 设置消息回调（如果需要）
         */
        @JavascriptInterface
        fun onMessage(callback: String) {
            // 设置消息回调（如果需要）
        }
        
        /**
         * 扩展激活回调
         */
        @JavascriptInterface
        fun onExtensionActivated(extensionId: String) {
            android.util.Log.d("VSCodeExtensionHost", "Extension activated: $extensionId")
        }
        
        /**
         * 扩展错误回调
         */
        @JavascriptInterface
        fun onExtensionError(extensionId: String, error: String) {
            android.util.Log.e("VSCodeExtensionHost", "Extension error: $extensionId - $error")
        }
        
        /**
         * 日志输出
         */
        @JavascriptInterface
        fun log(level: String, message: String) {
            when (level) {
                "error" -> android.util.Log.e("VSCodeExtensionHost", message)
                "warn" -> android.util.Log.w("VSCodeExtensionHost", message)
                "info" -> android.util.Log.i("VSCodeExtensionHost", message)
                else -> android.util.Log.d("VSCodeExtensionHost", message)
            }
        }
    }
}

