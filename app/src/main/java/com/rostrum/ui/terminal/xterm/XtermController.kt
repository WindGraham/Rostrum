package com.rostrum.ui.terminal.xterm

import android.util.Base64
import android.util.Log
import android.webkit.WebView
import com.rostrum.core.terminal.TerminalBackend
import com.rostrum.core.terminal.TerminalBackendState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Binds a TerminalBackend byte stream to a future xterm.js WebView renderer.
 */
class XtermController(
    private val backend: TerminalBackend,
    private val webView: WebView,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private var outputJob: Job? = null
    private var ready = false
    private val pendingOutput = mutableListOf<ByteArray>()

    suspend fun start(): Result<Unit> {
        if (outputJob != null) {
            Log.i(DIAG, "xterm.start: already attached backend=${backend.backendId}")
            return Result.success(Unit)
        }
        Log.i(DIAG, "xterm.start: backend=${backend.backendId}, state=${backend.state.value}")
        val startResult = if (backend.state.value == TerminalBackendState.Running) {
            Result.success(Unit)
        } else {
            backend.start()
        }
        if (startResult.isFailure) return startResult

        outputJob?.cancel()
        outputJob = scope.launch {
            backend.output.collectLatest { bytes ->
                Log.d(DIAG, "xterm.output: bytes=${bytes.size}, ready=$ready, backend=${backend.backendId}")
                write(bytes)
            }
        }
        return Result.success(Unit)
    }

    fun markReady() {
        ready = true
        Log.i(DIAG, "xterm.ready: backend=${backend.backendId}, pending=${pendingOutput.size}")
        evaluate("window.RostrumXterm && window.RostrumXterm.fit && window.RostrumXterm.fit()")
        pendingOutput.forEach { write(it) }
        pendingOutput.clear()
    }

    fun input(data: String) {
        Log.d(DIAG, "xterm.input: chars=${data.length}, backend=${backend.backendId}")
        scope.launch { backend.write(data.toByteArray(Charsets.UTF_8)) }
    }

    fun paste(data: String) {
        input(data)
    }

    fun resize(columns: Int, rows: Int) {
        val safeColumns = columns.coerceAtLeast(20)
        val safeRows = rows.coerceAtLeast(8)
        Log.i(DIAG, "xterm.resize: ${safeColumns}x$safeRows raw=${columns}x$rows, backend=${backend.backendId}")
        scope.launch { backend.resize(safeColumns, safeRows) }
    }

    fun write(bytes: ByteArray) {
        if (!ready) {
            pendingOutput.add(bytes)
            return
        }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        evaluate("window.RostrumXterm && window.RostrumXterm.write && window.RostrumXterm.write('$encoded')")
    }

    fun detach() {
        Log.i(DIAG, "xterm.detach: backend=${backend.backendId}")
        outputJob?.cancel()
        outputJob = null
        ready = false
        pendingOutput.clear()
    }

    private fun evaluate(script: String) {
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }

    private companion object {
        const val DIAG = "RostrumDiag"
    }
}
