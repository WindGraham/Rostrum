package com.termux.app.ssh

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * SSH Configuration - migrated from Rostrum
 */
@Serializable
data class SshConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = DEFAULT_PORT,
    val username: String,
    val authMethod: AuthMethod = AuthMethod.Password(""),
    val strictHostKeyChecking: Boolean = false,
    val keepAliveInterval: Int = DEFAULT_KEEP_ALIVE_INTERVAL,
    val connectionTimeout: Int = DEFAULT_CONNECTION_TIMEOUT
) {
    companion object {
        const val DEFAULT_PORT = 22
        const val DEFAULT_KEEP_ALIVE_INTERVAL = 30 // seconds
        const val DEFAULT_CONNECTION_TIMEOUT = 30_000 // ms
    }

    @Serializable
    sealed class AuthMethod {
        @Serializable
        data class Password(val password: String) : AuthMethod()

        @Serializable
        data class PrivateKey(
            val keyId: String,
            val passphrase: String? = null
        ) : AuthMethod()

        @Serializable
        data class PrivateKeyData(
            val keyData: String,
            val passphrase: String? = null
        ) : AuthMethod()
    }

    val displayName: String
        get() = if (name.isNotBlank()) name else "$username@$host"

    val connectionKey: String
        get() = "$username@$host:$port"
}

/**
 * SSH Connection State - migrated from Rostrum
 */
sealed class SshConnectionState {
    object Disconnected : SshConnectionState()
    object Connecting : SshConnectionState()
    data class Connected(
        val latencyMs: Long = 0,
        val connectedAt: Long = System.currentTimeMillis()
    ) : SshConnectionState()
    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int
    ) : SshConnectionState()
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val isRecoverable: Boolean = true
    ) : SshConnectionState()

    val isConnected: Boolean
        get() = this is Connected

    val isDisconnected: Boolean
        get() = this is Disconnected || this is Error
}
