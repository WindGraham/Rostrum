package com.rostrum.core.runtime

import com.rostrum.core.runtime.model.KeepAliveMode
import com.rostrum.core.runtime.model.KeepAliveResult
import com.rostrum.core.runtime.model.PrivilegedShellAuditEntry
import com.rostrum.core.runtime.model.PrivilegedBridgeStartRequest
import com.rostrum.core.runtime.model.PrivilegedBridgeStartResult
import com.rostrum.core.runtime.model.PrivilegedShellRequest
import com.rostrum.core.runtime.model.PrivilegedShellResult
import com.rostrum.core.runtime.model.PrivilegedShellStatus

interface PrivilegedShellService {
    suspend fun initialize(): Boolean
    suspend fun startBridge(request: PrivilegedBridgeStartRequest = PrivilegedBridgeStartRequest()): PrivilegedBridgeStartResult
    suspend fun status(sessionId: String? = null): PrivilegedShellStatus
    suspend fun exec(request: PrivilegedShellRequest): PrivilegedShellResult
    suspend fun keepAlive(mode: KeepAliveMode): KeepAliveResult
    fun getRecentAudit(limit: Int = 50): List<PrivilegedShellAuditEntry>
}
