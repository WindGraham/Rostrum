package com.rostrum.terminal

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

data class ServiceConfig(
    val id: String,
    val command: String,
    val port: Int = 0,
    val env: Map<String, String> = emptyMap(),
    val autoRestart: Boolean = true,
    val maxRestarts: Int = 5,
    val preheat: Boolean = false
)

sealed class ServiceState {
    data object Stopped : ServiceState()
    data object Starting : ServiceState()
    data class Running(val pid: Int, val port: Int) : ServiceState()
    data class Failed(val error: String) : ServiceState()
}

interface LinuxServiceManager {
    val serviceStates: StateFlow<Map<String, ServiceState>>
    suspend fun startService(config: ServiceConfig): Result<Unit>
    suspend fun stopService(serviceId: String): Result<Unit>
    suspend fun restartService(serviceId: String): Result<Unit>
    fun isServiceRunning(serviceId: String): Boolean
    suspend fun preheatServices()
}

class PortAllocator {
    private val allocatedPorts = mutableSetOf<Int>()
    private val portRange = 18700..18799

    @Synchronized
    fun allocate(): Int {
        for (port in portRange) {
            if (port !in allocatedPorts && isPortFree(port)) {
                allocatedPorts.add(port)
                return port
            }
        }
        throw IllegalStateException("No free ports in range $portRange")
    }

    @Synchronized
    fun release(port: Int) {
        allocatedPorts.remove(port)
    }

    private fun isPortFree(port: Int): Boolean = try {
        ServerSocket(port).use { true }
    } catch (_: Exception) { false }
}

class LinuxServiceManagerImpl(
    private val prootEnvironment: PRootEnvironment,
    private val ptySessionManager: PtySessionManager,
    private val portAllocator: PortAllocator
) : LinuxServiceManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _serviceStates = MutableStateFlow<Map<String, ServiceState>>(emptyMap())
    override val serviceStates: StateFlow<Map<String, ServiceState>> = _serviceStates

    private val serviceConfigs = ConcurrentHashMap<String, ServiceConfig>()
    private val serviceSessions = ConcurrentHashMap<String, PtySession>()
    private val restartCounts = ConcurrentHashMap<String, Int>()

    companion object {
        private const val TAG = "LinuxServiceManager"
    }

    override suspend fun startService(config: ServiceConfig): Result<Unit> = runCatching {
        prootEnvironment.initialize().getOrThrow()

        val port = if (config.port == 0) portAllocator.allocate() else config.port
        val actualConfig = config.copy(port = port)
        serviceConfigs[config.id] = actualConfig

        updateState(config.id, ServiceState.Starting)

        val command = actualConfig.command.replace("\$PORT", port.toString())
        val env = buildMap {
            putAll(actualConfig.env)
            put("PORT", port.toString())
        }

        val prootCommand = prootEnvironment.buildPRootCommand(command)
        val session = ptySessionManager.createSession(
            command = prootCommand,
            env = env
        )
        serviceSessions[config.id] = session

        scope.launch {
            var running = false
            session.outputFlow.collect { data ->
                val output = String(data)
                if (!running && (output.contains("listening") || output.contains("ready") || output.contains("started"))) {
                    running = true
                    updateState(config.id, ServiceState.Running(pid = 0, port = port))
                }
            }
        }

        scope.launch {
            session.exitCodeFlow.collect { exitCode ->
                Log.w(TAG, "Service ${config.id} exited with code $exitCode")
                if (actualConfig.autoRestart) {
                    val count = restartCounts.getOrDefault(config.id, 0)
                    if (count < actualConfig.maxRestarts) {
                        restartCounts[config.id] = count + 1
                        delay(1000L * (count + 1))
                        startService(actualConfig)
                    } else {
                        updateState(config.id, ServiceState.Failed("Max restarts reached"))
                    }
                } else {
                    updateState(config.id, ServiceState.Stopped)
                }
            }
        }

        // Wait for service to report ready
        delay(2000)
        if (_serviceStates.value[config.id] is ServiceState.Starting) {
            updateState(config.id, ServiceState.Running(pid = 0, port = port))
        }
    }

    override suspend fun stopService(serviceId: String): Result<Unit> = runCatching {
        serviceSessions.remove(serviceId)?.close()
        serviceConfigs[serviceId]?.let { config ->
            if (config.port != 0) portAllocator.release(config.port)
        }
        restartCounts.remove(serviceId)
        updateState(serviceId, ServiceState.Stopped)
    }

    override suspend fun restartService(serviceId: String): Result<Unit> {
        val config = serviceConfigs[serviceId]
            ?: return Result.failure(IllegalArgumentException("Unknown service: $serviceId"))
        stopService(serviceId)
        return startService(config)
    }

    override fun isServiceRunning(serviceId: String): Boolean {
        return _serviceStates.value[serviceId] is ServiceState.Running
    }

    override suspend fun preheatServices() {
        // Subclasses or DI configuration can provide preheat configs
        Log.d(TAG, "preheatServices() called — no default preheat configs")
    }

    private fun updateState(serviceId: String, state: ServiceState) {
        _serviceStates.value = _serviceStates.value.toMutableMap().apply {
            put(serviceId, state)
        }
    }


}
