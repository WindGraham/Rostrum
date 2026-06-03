package com.rostrum.core.workspace

import com.rostrum.core.filesystem.ActiveFileSystemState
import com.rostrum.core.server.RemoteServerState
import com.rostrum.core.terminal.TerminalBackendState

sealed class WorkspaceId {
    object Local : WorkspaceId()
    data class Ssh(val connectionId: String) : WorkspaceId()
}

sealed class WorkspaceTarget {
    abstract val id: WorkspaceId
    abstract val title: String
    abstract val subtitle: String

    data object Local : WorkspaceTarget() {
        override val id: WorkspaceId = WorkspaceId.Local
        override val title: String = "本机"
        override val subtitle: String = "本地文件 · 本地终端"
    }

    data class Remote(
        val connectionId: String,
        val displayName: String,
        val username: String,
        val host: String,
        val port: Int
    ) : WorkspaceTarget() {
        override val id: WorkspaceId = WorkspaceId.Ssh(connectionId)
        override val title: String = displayName
        override val subtitle: String = "$username@$host:$port"
    }
}

data class WorkspaceRuntimeState(
    val target: WorkspaceTarget,
    val fileSystem: ActiveFileSystemState? = null,
    val remoteServer: RemoteServerState? = null,
    val terminal: TerminalBackendState? = null
)
