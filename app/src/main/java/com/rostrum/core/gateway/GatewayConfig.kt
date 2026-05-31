package com.rostrum.core.gateway

import kotlinx.serialization.Serializable

@Serializable
data class GatewayConfig(
    val port: Int = 18790,
    val maxConnections: Int = 10,
    val requestTimeout: Long = 30_000,
    val maxRequestBodySize: Int = 10 * 1024 * 1024
)
