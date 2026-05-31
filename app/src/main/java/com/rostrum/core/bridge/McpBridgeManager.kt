package com.rostrum.core.bridge

import android.util.Log
import com.rostrum.terminal.LinuxServiceManager
import com.rostrum.terminal.PRootEnvironment
import com.rostrum.terminal.ServiceConfig
import com.rostrum.terminal.ServiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed class McpBridgeManagerState {
    data object Idle : McpBridgeManagerState()
    data object Starting : McpBridgeManagerState()
    data class Ready(val connectedServers: List<String>) : McpBridgeManagerState()
    data class Error(val message: String) : McpBridgeManagerState()
}

@Singleton
class McpBridgeManager @Inject constructor(
    private val linuxServiceManager: LinuxServiceManager,
    private val prootEnvironment: PRootEnvironment
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _state = MutableStateFlow<McpBridgeManagerState>(McpBridgeManagerState.Idle)
    val state: StateFlow<McpBridgeManagerState> = _state

    private val bridges = mutableMapOf<String, McpBridge>()

    companion object {
        private const val TAG = "McpBridgeManager"

        private val DEFAULT_MCP_SERVICES = listOf(
            ServiceConfig(
                id = "filesystem-server",
                command = "cd /opt/omnimaster/mcp-servers/filesystem-server && if [ -f dist/index.js ]; then node dist/index.js; else npx tsx src/index.ts; fi",
                port = 18700,
                autoRestart = true,
                maxRestarts = 3
            ),
            ServiceConfig(
                id = "terminal-server",
                command = "cd /opt/omnimaster/mcp-servers/terminal-server && if [ -f dist/index.js ]; then node dist/index.js; else npx tsx src/index.ts; fi",
                port = 18701,
                autoRestart = true,
                maxRestarts = 3
            )
        )
    }

    fun startAll() {
        scope.launch { startAllSuspend() }
    }

    suspend fun startAllSuspend() {
        _state.value = McpBridgeManagerState.Starting

        runCatching {
            prootEnvironment.initialize().getOrThrow()
            val nodeCheck = prootEnvironment.launchProcess("test -f /usr/bin/node")
            val nodeCheckExit = nodeCheck.waitFor()
            nodeCheck.destroyAndCloseStreams()
            if (nodeCheckExit != 0) {
                Log.i(TAG, "Node.js not available in container, running setup.sh...")
                val setupProcess = prootEnvironment.launchProcess("bash /opt/omnimaster/linux-mcp/setup.sh")
                val setupExit = setupProcess.waitFor()
                setupProcess.destroyAndCloseStreams()
                Log.i(TAG, "setup.sh exited with code $setupExit")
            } else {
                Log.i(TAG, "Node.js is available in container")
            }
        }.onFailure {
            Log.w(TAG, "Failed to check Node.js or run setup.sh", it)
        }

        val connected = mutableListOf<String>()
        for (config in DEFAULT_MCP_SERVICES) {
            try {
                linuxServiceManager.startService(config)
                val bridge = McpBridge("http://127.0.0.1:${config.port}")
                bridges[config.id] = bridge

                bridge.connect().getOrThrow()

                connected.add(config.id)
                Log.d(TAG, "MCP server ${config.id} connected on port ${config.port}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MCP server ${config.id}", e)
            }
        }

        if (connected.isNotEmpty()) {
            _state.value = McpBridgeManagerState.Ready(connected)
            Log.d(TAG, "MCP Bridge Manager ready: ${connected.joinToString()}")
        } else {
            _state.value = McpBridgeManagerState.Error("No MCP servers connected")
        }
    }

    suspend fun stopAll() {
        for ((id, bridge) in bridges) {
            try {
                bridge.disconnect()
                linuxServiceManager.stopService(id)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping $id", e)
            }
        }
        bridges.clear()
        _state.value = McpBridgeManagerState.Idle
    }

    fun getBridge(serverId: String): McpBridge? = bridges[serverId]

    fun isReady(): Boolean = _state.value is McpBridgeManagerState.Ready
}

/**
 * Destroys the process and eagerly closes its I/O streams to avoid leaking
 * file descriptors on Android.
 */
private fun Process.destroyAndCloseStreams() {
    try { outputStream.close() } catch (_: Exception) {}
    try { inputStream.close() } catch (_: Exception) {}
    try { errorStream.close() } catch (_: Exception) {}
    destroy()
}
