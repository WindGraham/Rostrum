package com.rostrum.core.gateway

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log

class MdnsPublisher(
    context: Context,
    private val config: GatewayConfig = GatewayConfig()
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    @Volatile
    private var registered = false

    companion object {
        private const val TAG = "MdnsPublisher"
        private const val SERVICE_TYPE = "_omnimaster._tcp."
    }

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "mDNS service registered: ${serviceInfo.serviceName}")
            registered = true
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e(TAG, "mDNS registration failed: errorCode=$errorCode")
            registered = false
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "mDNS service unregistered")
            registered = false
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e(TAG, "mDNS unregistration failed: errorCode=$errorCode")
        }
    }

    fun register() {
        if (registered) return

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = Build.MODEL
            serviceType = SERVICE_TYPE
            port = config.port
            setAttribute("version", "1.0")
            setAttribute("api", "mcp-2025-03-26")
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        Log.d(TAG, "Registering mDNS service: ${serviceInfo.serviceName} on port ${config.port}")
    }

    fun unregister() {
        if (!registered) return
        try {
            nsdManager.unregisterService(registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering mDNS service", e)
        }
    }
}
