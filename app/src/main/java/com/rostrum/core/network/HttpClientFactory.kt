package com.rostrum.core.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * HTTP 客户端工厂
 * 
 * 统一管理 OkHttpClient 实例，避免重复创建
 */
object HttpClientFactory {
    
    // 默认超时配置
    private const val CONNECT_TIMEOUT_SECONDS = 60L
    private const val READ_TIMEOUT_SECONDS = 120L
    private const val WRITE_TIMEOUT_SECONDS = 60L
    
    // 单例 OkHttpClient（线程安全）
    private val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    
    /**
     * 获取默认的 OkHttpClient
     */
    fun getClient(): OkHttpClient = defaultClient
    
    /**
     * 创建自定义超时的 OkHttpClient
     * 
     * 注意：这会创建新的实例，应谨慎使用
     */
    fun createClient(
        connectTimeoutSeconds: Long = CONNECT_TIMEOUT_SECONDS,
        readTimeoutSeconds: Long = READ_TIMEOUT_SECONDS,
        writeTimeoutSeconds: Long = WRITE_TIMEOUT_SECONDS
    ): OkHttpClient {
        return if (connectTimeoutSeconds == CONNECT_TIMEOUT_SECONDS &&
            readTimeoutSeconds == READ_TIMEOUT_SECONDS &&
            writeTimeoutSeconds == WRITE_TIMEOUT_SECONDS) {
            // 使用默认实例
            defaultClient
        } else {
            // 创建自定义实例
            OkHttpClient.Builder()
                .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
