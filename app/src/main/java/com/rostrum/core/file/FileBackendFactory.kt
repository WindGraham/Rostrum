package com.rostrum.core.file

import android.util.Log
import com.rostrum.core.bridge.McpBridge
import com.rostrum.core.bridge.McpBridgeManager
import com.rostrum.core.bridge.McpConnectionState

class FileBackendFactory(
    private val mcpBridgeManager: McpBridgeManager
) {
    companion object {
        private const val TAG = "FileBackendFactory"
        private const val FILESYSTEM_SERVER_ID = "filesystem-server"
    }

    @Volatile
    private var cachedBackend: FileOperationRepository? = null
    @Volatile
    private var lastBackendType: BackendType? = null

    @Synchronized
    fun getBackend(): FileOperationRepository {
        val currentType = resolveBackendType()
        if (cachedBackend != null && lastBackendType == currentType) {
            return cachedBackend!!
        }

        val backend = when (currentType) {
            BackendType.MCP -> {
                val bridge = mcpBridgeManager.getBridge(FILESYSTEM_SERVER_ID)!!
                Log.d(TAG, "Using MCP file backend")
                McpFileBackend(bridge)
            }
            BackendType.KOTLIN -> {
                Log.d(TAG, "Using Kotlin file backend")
                KotlinFileBackend()
            }
        }

        cachedBackend = backend
        lastBackendType = currentType
        return backend
    }

    @Synchronized
    fun invalidateCache() {
        cachedBackend = null
        lastBackendType = null
    }

    private fun resolveBackendType(): BackendType {
        if (!mcpBridgeManager.isReady()) return BackendType.KOTLIN

        val bridge = mcpBridgeManager.getBridge(FILESYSTEM_SERVER_ID) ?: return BackendType.KOTLIN
        if (bridge.state.value !is McpConnectionState.Connected) return BackendType.KOTLIN

        return BackendType.MCP
    }

    private enum class BackendType { MCP, KOTLIN }
}
