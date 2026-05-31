package com.rostrum.core.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

/**
 * 局域网服务发现
 * 
 * 使用 JmDNS (mDNS/Bonjour) 发现局域网中的设备和服务
 * 支持发现：
 * - FTP 服务器 (_ftp._tcp)
 * - SFTP/SSH 服务器 (_ssh._tcp, _sftp._tcp)
 * - SMB 共享 (_smb._tcp)
 * - HTTP 服务器 (_http._tcp)
 * - ADB 设备 (_adb._tcp, _adb-tls-pairing._tcp)
 * 
 * @author OmniMaster
 * @license Apache-2.0
 */
class NetworkDiscoveryService {
    
    companion object {
        private const val TAG = "NetworkDiscovery"
        
        // 常用服务类型
        const val SERVICE_FTP = "_ftp._tcp.local."
        const val SERVICE_SFTP = "_sftp._tcp.local."
        const val SERVICE_SSH = "_ssh._tcp.local."
        const val SERVICE_SMB = "_smb._tcp.local."
        const val SERVICE_HTTP = "_http._tcp.local."
        const val SERVICE_HTTPS = "_https._tcp.local."
        const val SERVICE_ADB = "_adb._tcp.local."
        const val SERVICE_ADB_TLS = "_adb-tls-connect._tcp.local."
        const val SERVICE_ADB_PAIRING = "_adb-tls-pairing._tcp.local."
        
        // 预定义的服务类型列表
        val NETWORK_SERVICES = listOf(
            SERVICE_FTP,
            SERVICE_SFTP,
            SERVICE_SSH,
            SERVICE_SMB,
            SERVICE_ADB,
            SERVICE_ADB_TLS
        )
    }
    
    /**
     * 发现的服务信息
     */
    data class DiscoveredService(
        val name: String,
        val type: String,
        val host: String,
        val port: Int,
        val addresses: List<InetAddress>,
        val properties: Map<String, String>
    ) {
        /**
         * 获取服务类型的友好名称
         */
        val friendlyType: String
            get() = when {
                type.contains("ftp") -> "FTP"
                type.contains("sftp") -> "SFTP"
                type.contains("ssh") -> "SSH"
                type.contains("smb") -> "SMB"
                type.contains("http") && type.contains("https") -> "HTTPS"
                type.contains("http") -> "HTTP"
                type.contains("adb-tls-pairing") -> "ADB配对"
                type.contains("adb-tls") -> "ADB TLS"
                type.contains("adb") -> "ADB"
                else -> type.substringBefore("._tcp")
            }
        
        /**
         * 获取主要地址
         */
        val primaryAddress: String
            get() = addresses.firstOrNull()?.hostAddress ?: host
        
        /**
         * 获取连接 URI
         */
        val connectionUri: String
            get() = when {
                type.contains("ftp") -> "ftp://$primaryAddress:$port"
                type.contains("sftp") || type.contains("ssh") -> "sftp://$primaryAddress:$port"
                type.contains("smb") -> "smb://$primaryAddress"
                type.contains("https") -> "https://$primaryAddress:$port"
                type.contains("http") -> "http://$primaryAddress:$port"
                else -> "$primaryAddress:$port"
            }
    }
    
    private var jmDNS: JmDNS? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeListeners = mutableMapOf<String, ServiceListener>()
    
    private val _discoveredServices = MutableStateFlow<List<DiscoveredService>>(emptyList())
    val discoveredServices: StateFlow<List<DiscoveredService>> = _discoveredServices.asStateFlow()
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    /**
     * 启动服务发现
     * 
     * @param bindAddress 绑定的网络接口地址，null 表示使用默认接口
     */
    suspend fun start(bindAddress: InetAddress? = null) = withContext(Dispatchers.IO) {
        if (_isRunning.value) {
            Log.w(TAG, "服务发现已在运行")
            return@withContext
        }
        
        try {
            jmDNS = if (bindAddress != null) {
                JmDNS.create(bindAddress, "OmniMaster")
            } else {
                JmDNS.create("OmniMaster")
            }
            
            _isRunning.value = true
            Log.i(TAG, "JmDNS 服务发现已启动")
        } catch (e: IOException) {
            Log.e(TAG, "启动 JmDNS 失败", e)
            throw e
        }
    }
    
    /**
     * 停止服务发现
     */
    suspend fun stop() = withContext(Dispatchers.IO) {
        activeListeners.forEach { (type, listener) ->
            jmDNS?.removeServiceListener(type, listener)
        }
        activeListeners.clear()
        
        try {
            jmDNS?.close()
        } catch (e: IOException) {
            Log.e(TAG, "关闭 JmDNS 失败", e)
        }
        
        jmDNS = null
        _isRunning.value = false
        _discoveredServices.value = emptyList()
        
        Log.i(TAG, "JmDNS 服务发现已停止")
    }
    
    /**
     * 发现指定类型的服务
     * 
     * @param serviceType 服务类型，如 "_ftp._tcp.local."
     */
    fun discoverService(serviceType: String) {
        val mdns = jmDNS ?: run {
            Log.w(TAG, "JmDNS 未启动")
            return
        }
        
        if (activeListeners.containsKey(serviceType)) {
            Log.d(TAG, "已在监听服务类型: $serviceType")
            return
        }
        
        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                Log.d(TAG, "发现服务: ${event.name} (${event.type})")
                // 请求服务详情
                mdns.requestServiceInfo(event.type, event.name, 5000)
            }
            
            override fun serviceRemoved(event: ServiceEvent) {
                Log.d(TAG, "服务移除: ${event.name} (${event.type})")
                removeService(event.name, event.type)
            }
            
            override fun serviceResolved(event: ServiceEvent) {
                Log.d(TAG, "服务解析完成: ${event.name} @ ${event.info.hostAddresses.contentToString()}")
                addService(event.info)
            }
        }
        
        mdns.addServiceListener(serviceType, listener)
        activeListeners[serviceType] = listener
        
        Log.i(TAG, "开始监听服务: $serviceType")
    }
    
    /**
     * 发现所有常用网络服务
     */
    fun discoverAllNetworkServices() {
        NETWORK_SERVICES.forEach { serviceType ->
            discoverService(serviceType)
        }
    }
    
    /**
     * 停止监听指定类型的服务
     */
    fun stopDiscovering(serviceType: String) {
        activeListeners[serviceType]?.let { listener ->
            jmDNS?.removeServiceListener(serviceType, listener)
            activeListeners.remove(serviceType)
            
            // 移除该类型的所有已发现服务
            _discoveredServices.value = _discoveredServices.value.filter { it.type != serviceType }
            
            Log.i(TAG, "停止监听服务: $serviceType")
        }
    }
    
    /**
     * 手动刷新服务列表
     */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val types = activeListeners.keys.toList()
        
        // 清空当前列表
        _discoveredServices.value = emptyList()
        
        // 重新发现
        types.forEach { type ->
            jmDNS?.list(type, 5000)?.forEach { info ->
                addService(info)
            }
        }
    }
    
    /**
     * 注册本机服务
     * 
     * @param type 服务类型
     * @param name 服务名称
     * @param port 服务端口
     * @param properties 附加属性
     */
    suspend fun registerService(
        type: String,
        name: String,
        port: Int,
        properties: Map<String, String> = emptyMap()
    ) = withContext(Dispatchers.IO) {
        val mdns = jmDNS ?: run {
            Log.w(TAG, "JmDNS 未启动")
            return@withContext
        }
        
        try {
            val serviceInfo = ServiceInfo.create(type, name, port, 0, 0, properties)
            mdns.registerService(serviceInfo)
            Log.i(TAG, "服务已注册: $name ($type) on port $port")
        } catch (e: IOException) {
            Log.e(TAG, "注册服务失败: $name", e)
            throw e
        }
    }
    
    /**
     * 注销本机服务
     */
    fun unregisterAllServices() {
        jmDNS?.unregisterAllServices()
        Log.i(TAG, "所有服务已注销")
    }
    
    private fun addService(info: ServiceInfo) {
        val service = DiscoveredService(
            name = info.name,
            type = info.type,
            host = info.server ?: "",
            port = info.port,
            addresses = info.inetAddresses.toList(),
            properties = info.propertyNames.asSequence()
                .associateWith { info.getPropertyString(it) ?: "" }
        )
        
        // 检查是否已存在
        val currentServices = _discoveredServices.value.toMutableList()
        val existingIndex = currentServices.indexOfFirst { 
            it.name == service.name && it.type == service.type 
        }
        
        if (existingIndex >= 0) {
            // 更新现有服务
            currentServices[existingIndex] = service
        } else {
            // 添加新服务
            currentServices.add(service)
        }
        
        _discoveredServices.value = currentServices
    }
    
    private fun removeService(name: String, type: String) {
        _discoveredServices.value = _discoveredServices.value.filter { 
            !(it.name == name && it.type == type)
        }
    }
    
    /**
     * 按类型过滤服务
     */
    fun getServicesByType(type: String): List<DiscoveredService> {
        return _discoveredServices.value.filter { it.type == type }
    }
    
    /**
     * 获取 FTP 服务列表
     */
    fun getFtpServices(): List<DiscoveredService> = getServicesByType(SERVICE_FTP)
    
    /**
     * 获取 SSH/SFTP 服务列表
     */
    fun getSshServices(): List<DiscoveredService> = 
        _discoveredServices.value.filter { 
            it.type == SERVICE_SSH || it.type == SERVICE_SFTP 
        }
    
    /**
     * 获取 ADB 设备列表
     */
    fun getAdbDevices(): List<DiscoveredService> =
        _discoveredServices.value.filter {
            it.type.contains("adb")
        }
}

