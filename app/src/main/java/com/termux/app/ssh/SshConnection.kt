package com.termux.app.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

/**
 * Simplified SSH Connection - migrated from Rostrum
 */
class SshConnection(private val config: SshConfig) {
    private var session: Session? = null
    private var sftpChannel: ChannelSftp? = null
    
    val isConnected: Boolean
        get() = session?.isConnected == true
    
    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("SshConnection", "Creating JSch session for ${config.username}@${config.host}:${config.port}")
            val jsch = JSch()
            val sess = jsch.getSession(config.username, config.host, config.port)
            
            when (val auth = config.authMethod) {
                is SshConfig.AuthMethod.Password -> {
                    android.util.Log.d("SshConnection", "Using password auth")
                    sess.setPassword(auth.password)
                }
                is SshConfig.AuthMethod.PrivateKey -> {
                    android.util.Log.d("SshConnection", "Using private key auth")
                    // TODO: Load key from keystore
                }
                is SshConfig.AuthMethod.PrivateKeyData -> {
                    android.util.Log.d("SshConnection", "Using private key data auth")
                    // TODO: Load key data
                }
            }
            
            val props = Properties().apply {
                if (!config.strictHostKeyChecking) {
                    put("StrictHostKeyChecking", "no")
                }
                put("PreferredAuthentications", "publickey,password,keyboard-interactive")
            }
            sess.setConfig(props)
            sess.timeout = config.connectionTimeout
            
            if (config.keepAliveInterval > 0) {
                sess.serverAliveInterval = config.keepAliveInterval * 1000
                sess.serverAliveCountMax = 3
            }
            
            android.util.Log.d("SshConnection", "Connecting... timeout=${config.connectionTimeout}")
            sess.connect(config.connectionTimeout)
            session = sess
            android.util.Log.d("SshConnection", "Connected successfully!")
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("SshConnection", "Connection failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun openSftpChannel(): Result<ChannelSftp> = withContext(Dispatchers.IO) {
        try {
            val sess = session ?: return@withContext Result.failure(Exception("Not connected"))
            val channel = sess.openChannel("sftp") as ChannelSftp
            channel.connect()
            sftpChannel = channel
            Result.success(channel)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getSession(): Session? = session
    
    fun disconnect() {
        try {
            sftpChannel?.disconnect()
            session?.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        sftpChannel = null
        session = null
    }
}
