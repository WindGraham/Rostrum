package com.rostrum.core.runtime.model

enum class PrivilegeBackend {
    SHIZUKU_INTERNAL_ROOT,
    SHIZUKU_INTERNAL_ADB,
    NONE
}

enum class ShellRiskLevel {
    SAFE,
    ELEVATED,
    DANGEROUS
}

enum class ShellMcpPolicy {
    APPROVAL_REQUIRED,
    SESSION_AUTO,
    FULL_AUTO
}

enum class KeepAliveMode {
    DISABLED,
    ENABLED
}

enum class BridgeStartStrategy {
    AUTO,
    ROOT,
    WIRELESS_ADB
}

enum class BridgeStartMode {
    WIRELESS_ADB,
    ROOT,
    NONE
}

enum class BridgeTransport {
    ROOT,
    WIRELESS_ADB,
    NONE
}

data class PrivilegedBridgeStatus(
    val serverReady: Boolean,
    val binderConnected: Boolean,
    val startMode: BridgeStartMode,
    val transport: BridgeTransport = BridgeTransport.NONE,
    val serverPid: Int? = null,
    val serverUid: Int? = null,
    val omniAdbVersion: String? = null,
    val serverApiVersion: Int? = null,
    val lastHandshakeError: String? = null,
    val lastStartError: String? = null,
    val diagnostics: List<String> = emptyList()
)

data class PrivilegedShellStatus(
    val initialized: Boolean,
    val activeBackend: PrivilegeBackend,
    val agentPrivilegeGranted: Boolean = false,
    val omniAdbPermissionGranted: Boolean = false,
    val bridgeReady: Boolean = false,
    val binderAlive: Boolean = false,
    val transport: BridgeTransport = BridgeTransport.NONE,
    val bridgeDiagnostics: List<String> = emptyList(),
    val permissionMode: String = "UNKNOWN",
    val authorizedAppCount: Int = 0,
    val keepAliveEnabled: Boolean,
    val sessionApproved: Boolean,
    val availableCapabilities: List<String> = emptyList(),
    val lastError: String? = null,
    val recentAuditSize: Int = 0
)

data class PrivilegedShellRequest(
    val command: String,
    val args: List<String> = emptyList(),
    val cwd: String? = null,
    val timeoutMs: Long = 30_000L,
    val env: Map<String, String> = emptyMap(),
    val sessionId: String? = null,
    val policy: ShellMcpPolicy = ShellMcpPolicy.APPROVAL_REQUIRED,
    val approved: Boolean = false,
    val dangerousRequireConfirm: Boolean = true
)

data class PrivilegedShellResult(
    val success: Boolean,
    val backend: PrivilegeBackend,
    val transport: BridgeTransport = BridgeTransport.NONE,
    val riskLevel: ShellRiskLevel,
    val commandLine: String = "",
    val output: String = "",
    val error: String = "",
    val exitCode: Int = -1,
    val durationMs: Long = 0L,
    val approvalRequired: Boolean = false,
    val diagnostics: List<String> = emptyList()
)

data class PrivilegedBridgeStartRequest(
    val strategy: BridgeStartStrategy = BridgeStartStrategy.AUTO,
    val host: String? = null,
    val port: Int? = null,
    val pairingCode: String? = null,
    val ensurePermission: Boolean = true
)

data class PrivilegedBridgeStartResult(
    val success: Boolean,
    val status: PrivilegedBridgeStatus,
    val agentPrivilegeGranted: Boolean = false,
    val omniAdbPermissionGranted: Boolean = false,
    val message: String = "",
    val diagnostics: List<String> = emptyList()
)

data class KeepAliveResult(
    val success: Boolean,
    val mode: KeepAliveMode,
    val message: String
)

data class PrivilegedShellAuditEntry(
    val timestampMs: Long,
    val sessionId: String?,
    val backend: PrivilegeBackend,
    val riskLevel: ShellRiskLevel,
    val commandLine: String,
    val success: Boolean,
    val exitCode: Int,
    val durationMs: Long,
    val error: String = ""
)
