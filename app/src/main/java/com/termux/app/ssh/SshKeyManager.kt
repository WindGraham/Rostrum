package com.termux.app.ssh

import android.util.Log
import java.io.File

object SshKeyManager {
    private const val TAG = "SshKeyManager"

    private val homeDir = File("/data/data/com.termux/files/home")
    val sshDir get() = File(homeDir, ".ssh")
    val privateKey get() = File(sshDir, "id_rsa")
    val publicKey get() = File(sshDir, "id_rsa.pub")
    private val sshConfig get() = File(sshDir, "config")

    fun ensureSshConfig() {
        sshDir.mkdirs()
        if (!sshConfig.exists()) {
            sshConfig.writeText("""
Host *
    ServerAliveInterval 30
    ServerAliveCountMax 3
    TCPKeepAlive yes
""".trimIndent())
            sshConfig.setReadable(true, false)
            sshConfig.setWritable(true, false)
        }
    }

    fun hasKeyPair(): Boolean = privateKey.exists() && publicKey.exists()

    fun generateKeyPair(label: String = "rostrum"): Result<Unit> {
        return try {
            sshDir.mkdirs()
            ensureSshConfig()
            val process = ProcessBuilder()
                .command(
                    "/data/data/com.termux/files/usr/bin/ssh-keygen",
                    "-t", "rsa",
                    "-b", "2048",
                    "-N", "",
                    "-C", label,
                    "-f", privateKey.absolutePath
                )
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Log.d(TAG, "SSH key pair generated successfully")
                Result.success(Unit)
            } else {
                Log.e(TAG, "ssh-keygen failed with exit code $exitCode: $output")
                Result.failure(Exception("ssh-keygen failed: $output"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate SSH key", e)
            Result.failure(e)
        }
    }

    fun getPublicKey(): String? = if (publicKey.exists()) publicKey.readText() else null

    fun getPrivateKeyPem(): String? = if (privateKey.exists()) privateKey.readText() else null
}
