package com.rostrum.core.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rostrum.R
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 会话管理器
 * 负责管理SSH连接的后台保活
 */
class SessionManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "SessionManager"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "rostrum_session"
        private const val CHANNEL_NAME = "Session Service"
        
        @Volatile
        private var instance: SessionManager? = null
        
        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // 活跃会话
    private val activeSessions = ConcurrentHashMap<String, SessionInfo>()
    
    // 心跳任务
    private var heartbeatJob: Job? = null
    
    // 服务是否运行
    private var isServiceRunning = false
    
    /**
     * 添加会话
     */
    fun addSession(sessionId: String, sessionInfo: SessionInfo) {
        activeSessions[sessionId] = sessionInfo
        Log.i(TAG, "Session added: $sessionId")
        
        // 如果有活跃会话，启动前台服务
        if (activeSessions.isNotEmpty() && !isServiceRunning) {
            startForegroundService()
        }
    }
    
    /**
     * 移除会话
     */
    fun removeSession(sessionId: String) {
        activeSessions.remove(sessionId)
        Log.i(TAG, "Session removed: $sessionId")
        
        // 如果没有活跃会话，停止前台服务
        if (activeSessions.isEmpty() && isServiceRunning) {
            stopForegroundService()
        }
    }
    
    /**
     * 获取会话信息
     */
    fun getSession(sessionId: String): SessionInfo? {
        return activeSessions[sessionId]
    }
    
    /**
     * 获取所有活跃会话
     */
    fun getActiveSessions(): Map<String, SessionInfo> {
        return activeSessions.toMap()
    }
    
    /**
     * 检查会话是否活跃
     */
    fun isSessionActive(sessionId: String): Boolean {
        return activeSessions.containsKey(sessionId)
    }
    
    /**
     * 更新会话状态
     */
    fun updateSessionStatus(sessionId: String, status: SessionStatus) {
        activeSessions[sessionId]?.let { session ->
            activeSessions[sessionId] = session.copy(status = status)
        }
    }
    
    /**
     * 启动前台服务
     */
    private fun startForegroundService() {
        if (isServiceRunning) return
        
        Log.i(TAG, "Starting foreground service")
        
        val intent = Intent(context, SessionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        
        isServiceRunning = true
        
        // 启动心跳
        startHeartbeat()
    }
    
    /**
     * 停止前台服务
     */
    private fun stopForegroundService() {
        if (!isServiceRunning) return
        
        Log.i(TAG, "Stopping foreground service")
        
        // 停止心跳
        stopHeartbeat()
        
        val intent = Intent(context, SessionService::class.java)
        context.stopService(intent)
        
        isServiceRunning = false
    }
    
    /**
     * 启动心跳
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(30000) // 每30秒发送一次心跳
                sendHeartbeat()
            }
        }
    }
    
    /**
     * 停止心跳
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
    
    /**
     * 发送心跳
     */
    private fun sendHeartbeat() {
        activeSessions.forEach { (sessionId, sessionInfo) ->
            try {
                // 检查会话是否仍然活跃
                if (sessionInfo.status == SessionStatus.ACTIVE) {
                    // 发送心跳到SSH连接
                    // 这里可以添加实际的心跳逻辑
                    Log.d(TAG, "Heartbeat sent for session: $sessionId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send heartbeat for session: $sessionId", e)
                // 标记会话为错误状态
                updateSessionStatus(sessionId, SessionStatus.ERROR)
            }
        }
    }
    
    /**
     * 清理所有会话
     */
    fun cleanup() {
        activeSessions.clear()
        stopForegroundService()
    }
}

/**
 * 会话信息
 */
data class SessionInfo(
    val sessionId: String,
    val host: String,
    val port: Int,
    val username: String,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActivity: Long = System.currentTimeMillis()
)

/**
 * 会话状态
 */
enum class SessionStatus {
    ACTIVE,      // 活跃
    IDLE,        // 空闲
    DISCONNECTED, // 已断开
    ERROR        // 错误
}

/**
 * 会话前台服务
 */
class SessionService : Service() {
    
    companion object {
        private const val TAG = "SessionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "rostrum_session"
        private const val CHANNEL_NAME = "Session Service"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service started")
        
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SSH sessions alive in background"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val sessionManager = SessionManager.getInstance(this)
        val activeSessions = sessionManager.getActiveSessions()
        
        val contentText = if (activeSessions.isNotEmpty()) {
            "${activeSessions.size} active session(s)"
        } else {
            "No active sessions"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rostrum")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}

/**
 * 会话保活工具
 */
object SessionKeepAlive {
    
    private const val TAG = "SessionKeepAlive"
    
    /**
     * 检查并恢复会话
     */
    suspend fun checkAndRestoreSessions(context: Context) {
        val sessionManager = SessionManager.getInstance(context)
        val activeSessions = sessionManager.getActiveSessions()
        
        activeSessions.forEach { (sessionId, sessionInfo) ->
            try {
                // 检查会话是否需要恢复
                if (sessionInfo.status == SessionStatus.DISCONNECTED) {
                    Log.i(TAG, "Restoring session: $sessionId")
                    // 这里可以添加恢复逻辑
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore session: $sessionId", e)
            }
        }
    }
    
    /**
     * 清理过期会话
     */
    fun cleanupExpiredSessions(context: Context, maxIdleTime: Long = 30 * 60 * 1000) {
        val sessionManager = SessionManager.getInstance(context)
        val activeSessions = sessionManager.getActiveSessions()
        val currentTime = System.currentTimeMillis()
        
        activeSessions.forEach { (sessionId, sessionInfo) ->
            val idleTime = currentTime - sessionInfo.lastActivity
            if (idleTime > maxIdleTime) {
                Log.i(TAG, "Removing expired session: $sessionId")
                sessionManager.removeSession(sessionId)
            }
        }
    }
}