package com.rostrum.core.server

/**
 * Structured rostrum-server state exposed to UI and workspace orchestration.
 */
data class RemoteServerState(
    val phase: RemoteServerPhase,
    val message: String,
    val endpoint: String? = null,
    val fallback: RemoteServerFallback? = null,
    val error: String? = null
) {
    val isReady: Boolean get() = phase == RemoteServerPhase.READY
    val isFallback: Boolean get() = fallback != null
}

enum class RemoteServerPhase {
    DISCONNECTED,
    CHECKING,
    INSTALLING,
    STARTING,
    TUNNELING,
    READY,
    FALLBACK,
    ERROR
}

enum class RemoteServerFallback {
    SFTP,
    NONE
}
