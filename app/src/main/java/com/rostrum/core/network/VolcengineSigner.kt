package com.rostrum.core.network

import android.util.Log
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 火山引擎 V4 签名工具
 * 
 * 基于官方 SDK 实现：volcengine-java-sdk-core
 * 官方签名只包含 x-date header
 */
object VolcengineSigner {
    
    private const val TAG = "VolcengineSigner"
    private const val ALGORITHM = "HMAC-SHA256"
    
    // URL 编码保留字符 (RFC 3986)
    private val URL_ENCODER = BitSet(256).apply {
        // a-z
        for (i in 97..122) set(i)
        // A-Z
        for (i in 65..90) set(i)
        // 0-9
        for (i in 48..57) set(i)
        set('-'.code)
        set('_'.code)
        set('.'.code)
        set('~'.code)
    }
    private const val CONST_ENCODE = "0123456789ABCDEF"
    
    /**
     * 签名结果
     */
    data class SignatureResult(
        val authorization: String,
        val xDate: String,
        val xContentSha256: String
    )
    
    /**
     * 生成 V4 签名
     * 
     * 注意：官方 SDK 只签名 x-date header，不签名 content-type、host 等
     */
    fun sign(
        method: String,
        path: String,
        queryString: String = "",
        body: String,
        ak: String,
        sk: String,
        service: String = "ml_platform",
        region: String = "cn-beijing"
    ): SignatureResult {
        // 1. 生成日期
        val now = Date()
        val xDate = formatDate(now)
        val shortXDate = xDate.substring(0, 8)
        
        // 2. 计算 body 的 SHA256
        val payloadHash = sha256(body)
        
        // 3. 构建规范请求 (只包含 x-date header)
        val canonicalRequest = buildCanonicalRequest(
            method = method,
            path = path,
            queryString = queryString,
            xDate = xDate,
            payloadHash = payloadHash
        )
        
        // 4. 构建待签名字符串
        val credentialScope = "$shortXDate/$region/$service/request"
        val stringToSign = buildStringToSign(xDate, credentialScope, canonicalRequest)
        
        // 5. 计算签名密钥 (官方 SDK 方式)
        val signingKey = getSigningKey(sk, shortXDate, region, service)
        val signature = hmacSha256Hex(signingKey, stringToSign)
        
        // 6. 构建 Authorization
        val authorization = "$ALGORITHM Credential=$ak/$credentialScope, SignedHeaders=x-date, Signature=$signature"
        
        // 调试日志
        Log.d(TAG, "=== Volcengine Sign ===")
        Log.d(TAG, "xDate: $xDate")
        Log.d(TAG, "CredentialScope: $credentialScope")
        Log.d(TAG, "CanonicalRequest:\n$canonicalRequest")
        Log.d(TAG, "StringToSign:\n$stringToSign")
        Log.d(TAG, "Signature: ${signature.take(16)}...")
        Log.d(TAG, "=======================")
        
        return SignatureResult(
            authorization = authorization,
            xDate = xDate,
            xContentSha256 = payloadHash
        )
    }
    
    /**
     * 构建规范请求
     * 
     * 格式：
     * HTTP_METHOD\n
     * URI\n
     * CANONICAL_QUERY_STRING\n
     * CANONICAL_HEADERS\n
     * SIGNED_HEADERS\n
     * PAYLOAD_HASH
     */
    private fun buildCanonicalRequest(
        method: String,
        path: String,
        queryString: String,
        xDate: String,
        payloadHash: String
    ): String {
        val sb = StringBuilder()
        
        // 1. HTTP 方法
        sb.append(method.uppercase())
        sb.append("\n")
        
        // 2. URI (官方 SDK 固定使用 "/")
        sb.append("/")
        sb.append("\n")
        
        // 3. 规范查询字符串
        sb.append(buildCanonicalQueryString(queryString))
        sb.append("\n")
        
        // 4. 规范头部 (只包含 x-date)
        sb.append("x-date:$xDate\n")
        sb.append("\n")
        
        // 5. 已签名头部
        sb.append("x-date")
        sb.append("\n")
        
        // 6. 载荷哈希
        sb.append(payloadHash)
        
        return sb.toString()
    }
    
    /**
     * 构建规范查询字符串
     */
    private fun buildCanonicalQueryString(queryString: String): String {
        if (queryString.isEmpty()) return ""
        
        return queryString.split("&")
            .filter { it.isNotEmpty() }
            .map { it.split("=", limit = 2) }
            .map { parts ->
                val key = parts[0]
                val value = if (parts.size > 1) parts[1] else ""
                urlEncode(key) to urlEncode(value)
            }
            .sortedBy { it.first }
            .joinToString("&") { "${it.first}=${it.second}" }
    }
    
    /**
     * 构建待签名字符串
     */
    private fun buildStringToSign(
        xDate: String,
        credentialScope: String,
        canonicalRequest: String
    ): String {
        return "$ALGORITHM\n$xDate\n$credentialScope\n${sha256(canonicalRequest)}"
    }
    
    /**
     * 获取签名密钥
     * 
     * 官方 SDK 密钥派生链：
     * kDate = HMAC(sk, date)
     * kRegion = HMAC(kDate, region)
     * kService = HMAC(kRegion, service)
     * kSigning = HMAC(kService, "request")
     */
    private fun getSigningKey(
        secretKey: String,
        date: String,
        region: String,
        service: String
    ): ByteArray {
        val skBytes = secretKey.toByteArray(StandardCharsets.UTF_8)
        val kDate = hmacSha256(date, skBytes)
        val kRegion = hmacSha256(region, kDate)
        val kService = hmacSha256(service, kRegion)
        return hmacSha256("request", kService)
    }
    
    /**
     * SHA256 哈希
     */
    private fun sha256(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(data.toByteArray(StandardCharsets.UTF_8))
        return byte2Hex(digest.digest())
    }
    
    /**
     * HMAC-SHA256
     */
    private fun hmacSha256(data: String, key: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }
    
    /**
     * HMAC-SHA256 并返回 Hex
     */
    private fun hmacSha256Hex(key: ByteArray, data: String): String {
        return byte2Hex(hmacSha256(data, key))
    }
    
    /**
     * ByteArray 转 Hex (小写)
     */
    private fun byte2Hex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            val temp = Integer.toHexString(b.toInt() and 0xFF)
            if (temp.length == 1) {
                sb.append("0")
            }
            sb.append(temp)
        }
        return sb.toString()
    }
    
    /**
     * URL 编码 (RFC 3986)
     * 空格编码为 %20
     */
    private fun urlEncode(source: String): String {
        val buf = StringBuilder(source.length)
        val bb = StandardCharsets.UTF_8.encode(source)
        
        while (bb.hasRemaining()) {
            val b = bb.get().toInt() and 0xFF
            when {
                URL_ENCODER.get(b) -> buf.append(b.toChar())
                b == 32 -> buf.append("%20")  // 空格
                else -> {
                    buf.append("%")
                    buf.append(CONST_ENCODE[b shr 4])
                    buf.append(CONST_ENCODE[b and 0xF])
                }
            }
        }
        
        return buf.toString()
    }
    
    /**
     * 格式化日期 (ISO8601 GMT)
     */
    private fun formatDate(date: Date): String {
        val sdf = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("GMT")
        return sdf.format(date)
    }
}

/**
 * 火山引擎配置
 */
data class VolcengineCredentials(
    val accessKeyId: String,
    val secretAccessKey: String,
    val region: String = "cn-beijing",
    val service: String = "ml_platform"
)
