package com.rostrum.ui.terminal.xterm

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rostrum.core.terminal.TerminalBackend
import java.io.ByteArrayInputStream
import kotlinx.coroutines.launch

@Composable
fun XtermTerminalPane(
    backend: TerminalBackend?,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val assetAvailable = remember { context.assetExists("terminal/terminal.html") }
    var controller by remember(backend) { mutableStateOf<XtermController?>(null) }
    var terminalWebView by remember(backend) { mutableStateOf<WebView?>(null) }
    var loading by remember(backend, assetAvailable) { mutableStateOf(assetAvailable && backend != null) }
    var error by remember(backend, assetAvailable) { mutableStateOf<String?>(null) }

    DisposableEffect(backend) {
        onDispose {
            val current = controller
            val currentWebView = terminalWebView
            controller = null
            terminalWebView = null
            if (current != null) {
                current.detach()
                currentWebView?.post {
                    currentWebView.removeJavascriptInterface(XtermTerminalContract.JS_BRIDGE_NAME)
                    currentWebView.stopLoading()
                    currentWebView.webChromeClient = null
                    currentWebView.webViewClient = WebViewClient()
                    currentWebView.destroy()
                }
            } else {
                currentWebView?.post {
                    currentWebView.removeJavascriptInterface(XtermTerminalContract.JS_BRIDGE_NAME)
                    currentWebView.stopLoading()
                    currentWebView.webChromeClient = null
                    currentWebView.webViewClient = WebViewClient()
                    currentWebView.destroy()
                }
            }
        }
    }

    Box(modifier.fillMaxSize().background(ComposeColor.Black)) {
        when {
            backend == null -> {
                XtermDiagnostic(
                    title = title,
                    subtitle = subtitle,
                    message = "Terminal backend is not ready."
                )
            }
            !assetAvailable -> {
                XtermDiagnostic(
                    title = title,
                    subtitle = subtitle,
                    message = "Missing packaged terminal assets: app/src/main/assets/terminal/terminal.html"
                )
            }
            else -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        createTerminalWebView(ctx).also { webView ->
                            terminalWebView = webView
                            val nextController = XtermController(backend, webView, scope)
                            controller = nextController
                            val bridge = XtermBridge(
                                onReady = {
                                    scope.launch {
                                        loading = false
                                        nextController.markReady()
                                    }
                                },
                                onInput = nextController::input,
                                onResize = nextController::resize,
                                onPaste = nextController::paste
                            )
                            webView.addJavascriptInterface(bridge, XtermTerminalContract.JS_BRIDGE_NAME)
                            webView.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                                val width = right - left
                                val height = bottom - top
                                Log.i("RostrumDiag", "xterm.webview.layout: ${width}x${height}")
                                if (width > 0 && height > 0) {
                                    webView.postDelayed({
                                        webView.evaluateJavascript(
                                            "window.RostrumXterm && window.RostrumXterm.fit && window.RostrumXterm.fit()",
                                            null
                                        )
                                    }, 50)
                                }
                            }
                            webView.webChromeClient = TerminalWebChromeClient { message ->
                                scope.launch {
                                    error = message
                                    loading = false
                                }
                            }
                            webView.webViewClient = TerminalAssetWebViewClient(
                                onPageFinished = {
                                    Log.i("RostrumDiag", "xterm.pageFinished")
                                    loading = false
                                },
                                onBlocked = { blocked ->
                                    scope.launch {
                                        error = "Blocked terminal WebView navigation: $blocked"
                                        loading = false
                                    }
                                }
                            )
                            webView.loadUrl(XtermTerminalContract.ASSET_URL)
                            webView.requestFocus()
                        }
                    }
                )

                LaunchedEffect(backend, controller) {
                    val startResult = controller?.start()
                    if (startResult?.isFailure == true) {
                        error = startResult.exceptionOrNull()?.message ?: "Failed to start terminal backend"
                        loading = false
                    }
                }

                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ComposeColor(0xFF38BDF8))
                    }
                }

                error?.let { message ->
                    XtermDiagnostic(title = title, subtitle = subtitle, message = message)
                }
            }
        }
    }
}

@Composable
private fun XtermDiagnostic(
    title: String,
    subtitle: String,
    message: String
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = ComposeColor.White, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = ComposeColor(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
            Text(message, color = ComposeColor(0xFFF59E0B), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Suppress("DEPRECATION")
private fun createTerminalWebView(context: Context): WebView {
    Log.i("RostrumDiag", "xterm.webview.create")
    return WebView(context).apply {
        setBackgroundColor(Color.BLACK)
        isFocusable = true
        isFocusableInTouchMode = true
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = true
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.mediaPlaybackRequiresUserGesture = true
        settings.setSupportMultipleWindows(false)
        setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                view.requestFocusFromTouch()
                evaluateJavascript(
                    "window.RostrumXterm && window.RostrumXterm.focus && window.RostrumXterm.focus()",
                    null
                )
                val inputMethodManager =
                    context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                inputMethodManager?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
            false
        }
    }
}

private class TerminalAssetWebViewClient(
    private val onPageFinished: () -> Unit,
    private val onBlocked: (String) -> Unit
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        return shouldBlock(request.url)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return shouldBlock(Uri.parse(url))
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        return if (isAllowedTerminalAsset(request.url)) {
            null
        } else {
            onBlocked(request.url.toString())
            emptyWebResourceResponse()
        }
    }

    override fun onPageFinished(view: WebView, url: String) {
        onPageFinished()
    }

    private fun shouldBlock(uri: Uri): Boolean {
        val allowed = isAllowedTerminalAsset(uri)
        if (!allowed) {
            onBlocked(uri.toString())
        }
        return !allowed
    }

    private fun isAllowedTerminalAsset(uri: Uri): Boolean {
        if (uri.scheme != "file") return false

        val segments = uri.pathSegments
        return segments.size >= 3 &&
            segments[0] == "android_asset" &&
            segments[1] == "terminal" &&
            segments.none { it == ".." || it.isBlank() }
    }

    private fun emptyWebResourceResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            Charsets.UTF_8.name(),
            ByteArrayInputStream(ByteArray(0))
        )
    }
}

private class TerminalWebChromeClient(
    private val onError: (String) -> Unit
) : WebChromeClient() {
    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        Log.d(
            "RostrumDiag",
            "xterm.console: ${consoleMessage.messageLevel()} ${consoleMessage.message()} " +
                "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
        )
        if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
            onError(
                "Terminal WebView script error: ${consoleMessage.message()} " +
                    "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
            )
        }
        return true
    }
}

private fun Context.assetExists(path: String): Boolean {
    return try {
        assets.open(path).use { true }
    } catch (_: Exception) {
        false
    }
}
