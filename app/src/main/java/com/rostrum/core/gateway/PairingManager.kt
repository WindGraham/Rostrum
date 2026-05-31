package com.rostrum.core.gateway

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import kotlin.random.Random

@Serializable
data class PairedDevice(
    val id: String,
    val name: String,
    val token: String,
    val pairedAt: Long,
    val lastSeen: Long
)

class PairingManager(context: Context) {

    private val prefs = context.getSharedPreferences("gateway_pairing", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var activePairingCode: String? = null
    @Volatile
    private var codeExpiresAt: Long = 0
    @Volatile
    private var failedAttempts: Int = 0
    @Volatile
    private var lockoutUntil: Long = 0

    companion object {
        private const val TAG = "PairingManager"
        private const val KEY_PAIRED_DEVICES = "paired_devices"
        private const val CODE_VALIDITY_MS = 5 * 60 * 1000L
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 60 * 1000L
    }

    fun generatePairingCode(): String {
        val code = String.format("%06d", Random.nextInt(1_000_000))
        activePairingCode = code
        codeExpiresAt = System.currentTimeMillis() + CODE_VALIDITY_MS
        Log.d(TAG, "Pairing code generated, expires in 5 minutes")
        return code
    }

    @Synchronized
    fun attemptPairing(code: String, deviceName: String): PairedDevice? {
        if (System.currentTimeMillis() < lockoutUntil) {
            Log.w(TAG, "Pairing locked out due to too many failed attempts")
            return null
        }

        val currentCode = activePairingCode
        if (currentCode == null || System.currentTimeMillis() > codeExpiresAt) {
            Log.w(TAG, "Pairing attempt with expired or absent code")
            activePairingCode = null
            return null
        }

        if (!constantTimeEquals(code, currentCode)) {
            failedAttempts++
            Log.w(TAG, "Pairing attempt with incorrect code ($failedAttempts/$MAX_FAILED_ATTEMPTS)")
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS
                activePairingCode = null
                failedAttempts = 0
                Log.w(TAG, "Too many failed attempts, locked out for 60s")
            }
            return null
        }

        activePairingCode = null
        failedAttempts = 0

        val device = PairedDevice(
            id = UUID.randomUUID().toString(),
            name = deviceName,
            token = generateSecureToken(),
            pairedAt = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis()
        )

        val devices = getPairedDevices().toMutableList()
        devices.add(device)
        savePairedDevices(devices)

        Log.d(TAG, "Paired with device: $deviceName (${device.id})")
        return device
    }

    fun validateToken(token: String): Boolean {
        val device = getPairedDevices().find { constantTimeEquals(it.token, token) } ?: return false
        updateLastSeen(device.id)
        return true
    }

    fun getPairedDevices(): List<PairedDevice> {
        val raw = prefs.getString(KEY_PAIRED_DEVICES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<PairedDevice>>(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse paired devices", e)
            emptyList()
        }
    }

    fun revokePairing(deviceId: String) {
        val devices = getPairedDevices().filterNot { it.id == deviceId }
        savePairedDevices(devices)
        Log.d(TAG, "Revoked pairing for device: $deviceId")
    }

    private fun updateLastSeen(deviceId: String) {
        val devices = getPairedDevices().map {
            if (it.id == deviceId) it.copy(lastSeen = System.currentTimeMillis()) else it
        }
        savePairedDevices(devices)
    }

    private fun savePairedDevices(devices: List<PairedDevice>) {
        prefs.edit().putString(KEY_PAIRED_DEVICES, json.encodeToString(devices)).apply()
    }

    private fun generateSecureToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        return MessageDigest.isEqual(
            a.toByteArray(Charsets.UTF_8),
            b.toByteArray(Charsets.UTF_8)
        )
    }
}
