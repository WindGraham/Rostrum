package com.rostrum.core.bridge

import android.util.Log
import com.rostrum.core.event.Event
import com.rostrum.core.event.EventBusImpl
import com.rostrum.terminal.LinuxServiceManager
import com.rostrum.terminal.PRootState
import com.rostrum.terminal.ServiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class McpServerHealthEvent(
    val serverId: String,
    val isHealthy: Boolean,
    val latencyMs: Long,
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : Event()

data class McpServerStatusChanged(
    val serverId: String,
    val oldState: ServiceState,
    val newState: ServiceState,
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : Event()

data class PRootEnvironmentEvent(
    val state: PRootState,
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : Event()

@Singleton
class EventBridge @Inject constructor(
    private val linuxServiceManager: LinuxServiceManager,
    private val mcpBridgeManager: McpBridgeManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val eventBus = EventBusImpl.getInstance()
    private var healthCheckJob: Job? = null
    private var serviceMonitorJob: Job? = null
    private var lastServiceStates = mapOf<String, ServiceState>()

    companion object {
        private const val TAG = "EventBridge"
        private const val HEALTH_CHECK_INTERVAL_MS = 30_000L
        private const val HEALTH_CHECK_TIMEOUT_MS = 5_000
    }

    fun start() {
        Log.d(TAG, "Starting EventBridge")
        startHealthCheck()
        startServiceMonitor()
    }

    fun stop() {
        Log.d(TAG, "Stopping EventBridge")
        healthCheckJob?.cancel()
        healthCheckJob = null
        serviceMonitorJob?.cancel()
        serviceMonitorJob = null
        lastServiceStates = emptyMap()
    }

    private fun startHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            while (true) {
                delay(HEALTH_CHECK_INTERVAL_MS)
                checkAllServers()
            }
        }
    }

    private fun startServiceMonitor() {
        serviceMonitorJob?.cancel()
        serviceMonitorJob = scope.launch {
            linuxServiceManager.serviceStates.collect { currentStates ->
                for ((id, newState) in currentStates) {
                    val oldState = lastServiceStates[id] ?: ServiceState.Stopped
                    if (oldState != newState) {
                        eventBus.publish(McpServerStatusChanged(
                            serverId = id,
                            oldState = oldState,
                            newState = newState
                        ))
                        Log.d(TAG, "Service $id state changed: $oldState -> $newState")
                    }
                }
                lastServiceStates = currentStates.toMap()
            }
        }
    }

    private suspend fun checkAllServers() {
        val servers = mapOf(
            "filesystem-server" to 18700,
            "terminal-server" to 18701
        )
        for ((serverId, port) in servers) {
            val result = checkHealth(port)
            eventBus.publish(McpServerHealthEvent(
                serverId = serverId,
                isHealthy = result.first,
                latencyMs = result.second
            ))
        }
    }

    private fun checkHealth(port: Int): Pair<Boolean, Long> {
        val start = System.currentTimeMillis()
        return try {
            val url = URL("http://127.0.0.1:$port/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = HEALTH_CHECK_TIMEOUT_MS
            conn.readTimeout = HEALTH_CHECK_TIMEOUT_MS
            conn.requestMethod = "GET"
            val code = conn.responseCode
            conn.disconnect()
            val latency = System.currentTimeMillis() - start
            Pair(code == 200, latency)
        } catch (_: Exception) {
            val latency = System.currentTimeMillis() - start
            Pair(false, latency)
        }
    }
}
