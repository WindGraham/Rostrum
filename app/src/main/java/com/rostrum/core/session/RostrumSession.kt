package com.rostrum.core.session

import kotlinx.coroutines.flow.StateFlow

/**
 * Product-level lifecycle boundary for anything that behaves like a session.
 *
 * SSH connections, SSH terminal/file/command sessions, local shells, terminal
 * panes, gateway links, and remote rostrum-server sessions should be exposed
 * through this shape before UI or orchestration code depends on them directly.
 */
interface RostrumSession {
    val id: String
    val type: RostrumSessionType
    val state: StateFlow<RostrumSessionState>

    val isActive: Boolean
        get() = state.value == RostrumSessionState.Active

    suspend fun start(): Result<Unit>

    suspend fun stop(): Result<Unit>
}

enum class RostrumSessionType {
    LOCAL_SHELL,
    LOCAL_TERMINAL,
    SSH_CONNECTION,
    SSH_TERMINAL,
    SSH_FILE,
    SSH_COMMAND,
    REMOTE_SERVER,
    GATEWAY,
    PLUGIN,
    UNKNOWN
}

sealed class RostrumSessionState {
    object Idle : RostrumSessionState()
    object Starting : RostrumSessionState()
    object Active : RostrumSessionState()
    object Stopping : RostrumSessionState()
    object Stopped : RostrumSessionState()
    data class Failed(val message: String, val cause: Throwable? = null) : RostrumSessionState()
}
