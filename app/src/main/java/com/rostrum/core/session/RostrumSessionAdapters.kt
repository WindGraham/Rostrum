package com.rostrum.core.session

import com.rostrum.core.shell.IShellSession
import com.rostrum.core.ssh.session.ISshSession
import com.rostrum.core.ssh.session.SessionState
import com.rostrum.core.ssh.session.SshCommandSession
import com.rostrum.core.ssh.session.SshFileSession
import com.rostrum.core.ssh.session.SshTerminalSession
import com.rostrum.core.terminal.TerminalSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

fun ISshSession.asRostrumSession(): RostrumSession = SshSessionBoundary(this)

fun TerminalSession.asRostrumSession(): RostrumSession = TerminalSessionBoundary(this)

fun IShellSession.asRostrumSession(id: String): RostrumSession = ShellSessionBoundary(id, this)

private class SshSessionBoundary(
    private val delegate: ISshSession
) : RostrumSession {
    override val id: String = delegate.id
    override val type: RostrumSessionType = when (delegate) {
        is SshTerminalSession -> RostrumSessionType.SSH_TERMINAL
        is SshFileSession -> RostrumSessionType.SSH_FILE
        is SshCommandSession -> RostrumSessionType.SSH_COMMAND
        else -> RostrumSessionType.UNKNOWN
    }

    private val mutableState = MutableStateFlow(delegate.state.value.toRostrumState())
    override val state: StateFlow<RostrumSessionState> = mutableState

    override suspend fun start(): Result<Unit> {
        mutableState.value = RostrumSessionState.Starting
        return delegate.start()
            .onSuccess { mutableState.value = RostrumSessionState.Active }
            .onFailure { error ->
                mutableState.value = RostrumSessionState.Failed(error.message ?: "SSH session failed", error)
            }
    }

    override suspend fun stop(): Result<Unit> {
        mutableState.value = RostrumSessionState.Stopping
        return runCatching { delegate.closeAsync() }
            .onSuccess { mutableState.value = RostrumSessionState.Stopped }
            .onFailure { error ->
                mutableState.value = RostrumSessionState.Failed(error.message ?: "SSH session stop failed", error)
            }
    }
}

private class TerminalSessionBoundary(
    private val delegate: TerminalSession
) : RostrumSession {
    override val id: String = delegate.id
    override val type: RostrumSessionType = RostrumSessionType.LOCAL_TERMINAL

    private val mutableState = MutableStateFlow(if (delegate.isRunning) RostrumSessionState.Active else RostrumSessionState.Idle)
    override val state: StateFlow<RostrumSessionState> = mutableState

    override suspend fun start(): Result<Unit> {
        mutableState.value = if (delegate.isRunning) RostrumSessionState.Active else RostrumSessionState.Idle
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        mutableState.value = RostrumSessionState.Stopping
        return runCatching { delegate.terminate() }
            .onSuccess { mutableState.value = RostrumSessionState.Stopped }
            .onFailure { error ->
                mutableState.value = RostrumSessionState.Failed(error.message ?: "Terminal session stop failed", error)
            }
    }
}

private class ShellSessionBoundary(
    override val id: String,
    private val delegate: IShellSession
) : RostrumSession {
    override val type: RostrumSessionType = RostrumSessionType.LOCAL_SHELL

    private val mutableState = MutableStateFlow(if (delegate.isReady()) RostrumSessionState.Active else RostrumSessionState.Idle)
    override val state: StateFlow<RostrumSessionState> = mutableState

    override suspend fun start(): Result<Unit> {
        mutableState.value = RostrumSessionState.Starting
        return runCatching { delegate.startSession() }
            .onSuccess {
                mutableState.value = if (delegate.isReady()) RostrumSessionState.Active else RostrumSessionState.Idle
            }
            .onFailure { error ->
                mutableState.value = RostrumSessionState.Failed(error.message ?: "Shell session start failed", error)
            }
    }

    override suspend fun stop(): Result<Unit> {
        mutableState.value = RostrumSessionState.Stopping
        return runCatching { delegate.close() }
            .onSuccess { mutableState.value = RostrumSessionState.Stopped }
            .onFailure { error ->
                mutableState.value = RostrumSessionState.Failed(error.message ?: "Shell session stop failed", error)
            }
    }
}

private fun SessionState.toRostrumState(): RostrumSessionState {
    return when (this) {
        SessionState.Idle -> RostrumSessionState.Idle
        SessionState.Starting -> RostrumSessionState.Starting
        SessionState.Running -> RostrumSessionState.Active
        SessionState.Paused -> RostrumSessionState.Idle
        SessionState.Closed -> RostrumSessionState.Stopped
        is SessionState.Error -> RostrumSessionState.Failed(message, cause)
    }
}
