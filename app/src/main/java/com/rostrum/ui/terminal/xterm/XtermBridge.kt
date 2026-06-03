package com.rostrum.ui.terminal.xterm

import android.webkit.JavascriptInterface

/**
 * Minimal JavaScript bridge exposed to packaged xterm.js assets.
 *
 * The bridge is deliberately data-only: terminal input, resize, paste, and ready.
 * Command execution and SSH ownership stay on the Kotlin side.
 */
class XtermBridge(
    private val onReady: () -> Unit,
    private val onInput: (String) -> Unit,
    private val onResize: (Int, Int) -> Unit,
    private val onPaste: (String) -> Unit
) {
    @JavascriptInterface
    fun ready() {
        onReady()
    }

    @JavascriptInterface
    fun input(data: String) {
        onInput(data)
    }

    @JavascriptInterface
    fun resize(columns: Int, rows: Int) {
        onResize(columns, rows)
    }

    @JavascriptInterface
    fun paste(data: String) {
        onPaste(data)
    }
}
