package com.termux.app.ssh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.app.data.ActiveFileSystemManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SSH ViewModel - manages SSH connections and file system switching.
 * Provides reactive UI updates via StateFlow.
 */
class SshViewModel : ViewModel() {

    // ── Saved connections (in-memory) ────────────────────────────────
    private val _savedConnections = MutableStateFlow<List<SshConfig>>(listOf(
        SshConfig(
            id = "preinstalled-ubuntu",
            name = "Ubuntu 测试",
            host = "101.42.5.26",
            port = 22,
            username = "ubuntu",
            authMethod = SshConfig.AuthMethod.Password("Terraria."),
            strictHostKeyChecking = false
        )
    ))
    val savedConnections: StateFlow<List<SshConfig>> = _savedConnections.asStateFlow()

    // ── Active connection states (id → state) ────────────────────────
    private val _connectionStates = MutableStateFlow<Map<String, SshConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, SshConnectionState>> = _connectionStates.asStateFlow()

    // ── Currently active connection id ───────────────────────────────
    private val _activeConnectionId = MutableStateFlow<String?>(null)
    val activeConnectionId: StateFlow<String?> = _activeConnectionId.asStateFlow()

    // Internal map of active SshConnection instances
    private val activeConnections = mutableMapOf<String, SshConnection>()

    // ═════════════════════════════════════════════════════════════════
    //  Public API
    // ═════════════════════════════════════════════════════════════════

    /**
     * Initiates an SSH connection using the given [config].
     * Updates state through [SshConnectionState.Connecting] → [SshConnectionState.Connected]
     * or [SshConnectionState.Error].
     * On success, creates [SshFileSystem] and switches [ActiveFileSystemManager] to remote.
     */
    fun connect(config: SshConfig) {
        android.util.Log.d("SshViewModel", "Connecting to ${config.host}:${config.port} as ${config.username}")
        viewModelScope.launch {
            // Disconnect any existing active connection first
            _activeConnectionId.value?.let { disconnect(it) }

            val id = config.id
            setConnectionState(id, SshConnectionState.Connecting)

            val connection = SshConnection(config)
            activeConnections[id] = connection

            android.util.Log.d("SshViewModel", "Starting SSH connection...")
            val result = connection.connect()
            android.util.Log.d("SshViewModel", "Connection result: ${result.isSuccess}")
            result.fold(
                onSuccess = {
                    android.util.Log.d("SshViewModel", "SSH connected successfully")
                    val sshFileSystem = SshFileSystem(connection)
                    ActiveFileSystemManager.switchToSsh(
                        sshFileSystem = sshFileSystem,
                        rootPath = "/home/${config.username}",
                        host = config.host,
                        user = config.username
                    )
                    setConnectionState(
                        id,
                        SshConnectionState.Connected(connectedAt = System.currentTimeMillis())
                    )
                    _activeConnectionId.value = id
                },
                onFailure = { throwable ->
                    android.util.Log.e("SshViewModel", "SSH connection failed", throwable)
                    setConnectionState(
                        id,
                        SshConnectionState.Error(
                            message = throwable.message ?: "Connection failed",
                            cause = throwable,
                            isRecoverable = true
                        )
                    )
                    activeConnections.remove(id)
                }
            )
        }
    }

    /**
     * Disconnects the active connection identified by [id].
     * Cleans up resources and switches [ActiveFileSystemManager] back to local.
     */
    fun disconnect(id: String) {
        val connection = activeConnections.remove(id)
        connection?.disconnect()

        setConnectionState(id, SshConnectionState.Disconnected)

        if (_activeConnectionId.value == id) {
            _activeConnectionId.value = null
            ActiveFileSystemManager.switchToLocal()
        }
    }

    /**
     * Saves (or updates) a connection configuration.
     * If a config with the same [SshConfig.id] already exists it is replaced.
     */
    fun saveConnection(config: SshConfig) {
        _savedConnections.value = _savedConnections.value.toMutableList().apply {
            val index = indexOfFirst { it.id == config.id }
            if (index >= 0) {
                set(index, config)
            } else {
                add(config)
            }
        }
    }

    /**
     * Deletes a saved connection configuration.
     * If the deleted connection was active it is disconnected first.
     */
    fun deleteConnection(id: String) {
        if (_activeConnectionId.value == id) {
            disconnect(id)
        }
        _savedConnections.value = _savedConnections.value.filter { it.id != id }
        _connectionStates.value = _connectionStates.value - id
    }

    /**
     * Gets the active SshConnection for the given id.
     */
    fun getConnection(id: String): SshConnection? = activeConnections[id]

    /**
     * Disconnects all active connections and cleans up resources.
     * Called automatically when the ViewModel is cleared.
     */
    fun disconnectAll() {
        activeConnections.keys.toList().forEach { disconnect(it) }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ═════════════════════════════════════════════════════════════════

    override fun onCleared() {
        super.onCleared()
        disconnectAll()
    }

    // ═════════════════════════════════════════════════════════════════
    //  Helpers
    // ═════════════════════════════════════════════════════════════════

    private fun setConnectionState(id: String, state: SshConnectionState) {
        _connectionStates.value = _connectionStates.value.toMutableMap().apply {
            put(id, state)
        }
    }
}
