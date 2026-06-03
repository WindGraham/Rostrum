package com.rostrum.core.terminal

import android.util.Log
import com.rostrum.core.ssh.session.SessionState
import com.rostrum.core.ssh.session.SshTerminalSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Adapts the existing SSH shell session to the PTY byte-stream terminal boundary.
 */
class SshTerminalBackendAdapter(
    private val session: SshTerminalSession
) : TerminalBackend {
    override val backendId: String = "ssh-pty:${session.id}"
    override val kind: TerminalBackendKind = TerminalBackendKind.SSH_PTY
    override val output: Flow<ByteArray> = session.rawOutputFlow

    private val mutableState = MutableStateFlow(session.state.value.toTerminalBackendState())
    override val state: StateFlow<TerminalBackendState> = mutableState

    override suspend fun start(): Result<Unit> {
        mutableState.value = TerminalBackendState.Starting
        return session.start()
            .onSuccess { mutableState.value = TerminalBackendState.Running }
            .onFailure { error ->
                mutableState.value = TerminalBackendState.Failed(error.message ?: "SSH PTY start failed", error)
            }
    }

    override suspend fun write(data: ByteArray): Result<Unit> {
        Log.d("RostrumDiag", "terminal.backend.write: backend=$backendId, bytes=${data.size}")
        return runCatching { session.sendRaw(data) }
            .onFailure { error ->
                mutableState.value = TerminalBackendState.Failed(error.message ?: "SSH PTY write failed", error)
            }
    }

    override suspend fun resize(columns: Int, rows: Int): Result<Unit> {
        return runCatching { session.resize(columns, rows) }
            .onFailure { error ->
                mutableState.value = TerminalBackendState.Failed(error.message ?: "SSH PTY resize failed", error)
            }
    }

    override suspend fun close(): Result<Unit> {
        mutableState.value = TerminalBackendState.Closing
        return runCatching { session.closeAsync() }
            .onSuccess { mutableState.value = TerminalBackendState.Closed }
            .onFailure { error ->
                mutableState.value = TerminalBackendState.Failed(error.message ?: "SSH PTY close failed", error)
            }
    }

    private fun SessionState.toTerminalBackendState(): TerminalBackendState {
        return when (this) {
            SessionState.Idle -> TerminalBackendState.Idle
            SessionState.Starting -> TerminalBackendState.Starting
            SessionState.Running -> TerminalBackendState.Running
            SessionState.Paused -> TerminalBackendState.Idle
            SessionState.Closed -> TerminalBackendState.Closed
            is SessionState.Error -> TerminalBackendState.Failed(message, cause)
        }
    }
}
