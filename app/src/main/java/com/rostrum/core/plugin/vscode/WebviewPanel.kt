package com.rostrum.core.plugin.vscode

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Webview面板
 * 
 * 用于VSCode插件兼容，提供Webview功能
 */
interface WebviewPanel {
    /**
     * 面板标题
     */
    var title: String
    
    /**
     * 是否可见
     */
    var visible: Boolean
    
    /**
     * 是否激活
     */
    var active: Boolean
    
    /**
     * Webview实例
     */
    val webview: WebView
    
    /**
     * 设置Webview内容
     */
    fun setHtmlContent(html: String)
    
    /**
     * 执行JavaScript
     */
    fun executeJavaScript(script: String)
    
    /**
     * 接收来自Webview的消息
     */
    fun onMessage(handler: (message: String) -> Unit)
    
    /**
     * 发送消息到Webview
     */
    fun postMessage(message: String)
    
    /**
     * 显示面板
     */
    fun reveal()
    
    /**
     * 隐藏面板
     */
    fun hide()
    
    /**
     * 关闭面板
     */
    fun dispose()
}

/**
 * Webview面板实现
 */
class WebviewPanelImpl(
    override var title: String,
    private val viewType: String
) : WebviewPanel {
    
    private lateinit var _webview: WebView
    
    override val webview: WebView
        get() = _webview
    
    override var visible: Boolean = true
    override var active: Boolean = false
    
    private var messageHandler: ((String) -> Unit)? = null
    
    fun initialize(webview: WebView) {
        _webview = webview
        setupWebview()
    }
    
    private fun setupWebview() {
        _webview.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
        }
        
        // 设置消息接收接口
        // TODO: 实现JavaScript接口
    }
    
    override fun setHtmlContent(html: String) {
        _webview.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
    
    override fun executeJavaScript(script: String) {
        _webview.evaluateJavascript(script, null)
    }
    
    override fun onMessage(handler: (message: String) -> Unit) {
        messageHandler = handler
    }
    
    override fun postMessage(message: String) {
        val script = "window.postMessage($message, '*');"
        executeJavaScript(script)
    }
    
    override fun reveal() {
        visible = true
        active = true
    }
    
    override fun hide() {
        visible = false
        active = false
    }
    
    override fun dispose() {
        hide()
        messageHandler = null
    }
}

/**
 * Compose组件：Webview面板适配器
 */
@Composable
fun WebviewPanelAdapter(
    panel: WebviewPanel,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    AndroidView(
        factory = { context ->
            val webview = WebView(context)
            if (panel is WebviewPanelImpl) {
                panel.initialize(webview)
            }
            webview
        },
        modifier = modifier
    )
}

