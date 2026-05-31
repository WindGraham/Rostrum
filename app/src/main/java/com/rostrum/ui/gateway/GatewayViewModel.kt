package com.rostrum.ui.gateway

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rostrum.core.gateway.GatewayConfig
import com.rostrum.core.gateway.GatewayServer
import com.rostrum.core.gateway.PairingManager
import com.rostrum.core.gateway.PairedDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GatewayUiState(
    val isGatewayRunning: Boolean = false,
    val currentIp: String = "",
    val port: Int = 0,
    val connectedDevices: Int = 0,
    val pairingCode: String = "",
    val pairingExpiry: Long = 0L,
    val pairedDevices: List<PairedDevice> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class GatewayViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "GatewayViewModel"
        private const val PAIRING_CODE_VALIDITY_MS = 5 * 60 * 1000L
    }

    private val _uiState = MutableStateFlow(GatewayUiState())
    val uiState: StateFlow<GatewayUiState> = _uiState.asStateFlow()

    private var gatewayServer: GatewayServer? = null
    private var pairingManager: PairingManager? = null

    init {
        val context = getApplication<Application>().applicationContext
        val pm = PairingManager(context)
        pairingManager = pm
        gatewayServer = GatewayServer(
            config = GatewayConfig(),
            pairingManager = pm
        )
        Log.d(TAG, "GatewayViewModel initialized with core GatewayServer")
        refreshState()
    }

    fun toggleGateway() {
        val server = gatewayServer ?: run {
            _uiState.update { it.copy(errorMessage = "网关服务未初始化") }
            Log.w(TAG, "GatewayServer is null, cannot toggle")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                if (server.isRunning()) {
                    server.stop()
                    _uiState.update {
                        it.copy(
                            isGatewayRunning = false,
                            currentIp = "",
                            port = 0,
                            connectedDevices = 0,
                            isLoading = false
                        )
                    }
                    Log.d(TAG, "网关已停止")
                } else {
                    server.start()
                    // 短暂等待让服务启动
                    delay(100)
                    val isRunning = server.isRunning()
                    if (isRunning) {
                        _uiState.update {
                            it.copy(
                                isGatewayRunning = true,
                                currentIp = server.getLocalIpAddress(),
                                port = server.getPort(),
                                connectedDevices = server.getConnectedDeviceCount(),
                                isLoading = false
                            )
                        }
                        refreshPairingCode()
                        Log.d(TAG, "网关已启动: ${server.getLocalIpAddress()}:${server.getPort()}")
                    } else {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "网关启动失败") }
                        Log.e(TAG, "网关启动失败")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "切换网关状态失败", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "操作失败: ${e.message}") }
            }
        }
    }

    fun refreshPairingCode() {
        val pairing = pairingManager ?: return

        viewModelScope.launch {
            try {
                val code = pairing.generatePairingCode()
                val expiry = System.currentTimeMillis() + PAIRING_CODE_VALIDITY_MS
                _uiState.update { it.copy(pairingCode = code, pairingExpiry = expiry) }
                Log.d(TAG, "配对码已刷新: $code")
            } catch (e: Exception) {
                Log.e(TAG, "刷新配对码失败", e)
                _uiState.update { it.copy(errorMessage = "生成配对码失败") }
            }
        }
    }

    fun revokePairing(deviceId: String) {
        val pairing = pairingManager ?: return

        viewModelScope.launch {
            try {
                pairing.revokePairing(deviceId)
                refreshPairedDevices()
                Log.d(TAG, "已撤销配对: $deviceId")
            } catch (e: Exception) {
                Log.e(TAG, "撤销配对失败", e)
                _uiState.update { it.copy(errorMessage = "撤销失败: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun refreshState() {
        val server = gatewayServer ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGatewayRunning = server.isRunning(),
                    currentIp = if (server.isRunning()) server.getLocalIpAddress() else "",
                    port = if (server.isRunning()) server.getPort() else 0,
                    connectedDevices = if (server.isRunning()) server.getConnectedDeviceCount() else 0
                )
            }
            if (server.isRunning()) {
                refreshPairedDevices()
                refreshPairingCode()
            }
        }
    }

    private fun refreshPairedDevices() {
        val pairing = pairingManager ?: return
        viewModelScope.launch {
            try {
                val devices = pairing.getPairedDevices()
                _uiState.update { it.copy(pairedDevices = devices) }
                Log.d(TAG, "已配对设备: ${devices.size}")
            } catch (e: Exception) {
                Log.e(TAG, "获取配对设备列表失败", e)
            }
        }
    }
}
