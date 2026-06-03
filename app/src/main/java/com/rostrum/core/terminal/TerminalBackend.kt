package com.rostrum.core.terminal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Byte-stream terminal boundary for real PTY-capable renderers.
 *
 * xterm.js/WebView, SSH PTY, local PTY, and future rostrum-server terminal
 * WebSocket implementations should meet here instead of routing through
 * command-oriented string APIs.
 */
interface TerminalBackend {
    val backendId: String
    val kind: TerminalBackendKind
    val state: StateFlow<TerminalBackendState>
    val output: Flow<ByteArray>

    suspend fun start(): Result<Unit>

    suspend fun write(data: ByteArray): Result<Unit>

    suspend fun resize(columns: Int, rows: Int): Result<Unit>

    suspend fun close(): Result<Unit>
}

enum class TerminalBackendKind {
    SSH_PTY,
    LOCAL_PTY,
    REMOTE_SERVER_WS,
    UNKNOWN
}

sealed class TerminalBackendState {
    object Idle : TerminalBackendState()
    object Starting : TerminalBackendState()
    object Running : TerminalBackendState()
    object Closing : TerminalBackendState()
    object Closed : TerminalBackendState()
    data class Failed(val message: String, val cause: Throwable? = null) : TerminalBackendState()
}
