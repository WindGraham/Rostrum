package com.rostrum.terminal

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.Writer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface PtySession {
    val sessionId: String
    val outputFlow: Flow<ByteArray>
    val exitCodeFlow: Flow<Int>
    val isAlive: Boolean

    suspend fun write(data: ByteArray)
    suspend fun write(text: String)
    suspend fun resize(columns: Int, rows: Int)
    suspend fun close()
}

interface PtySessionManager {
    suspend fun createSession(
        command: String,
        env: Map<String, String> = emptyMap(),
        workingDir: File? = null,
        columns: Int = 80,
        rows: Int = 24
    ): PtySession

    /**
     * Create a session by passing a command list directly to the OS without
     * intermediate shell parsing. This is safer than [createSession] when the
     * command contains special characters or when exact argument boundaries
     * must be preserved.
     */
    suspend fun createSession(
        command: List<String>,
        env: Map<String, String> = emptyMap(),
        workingDir: File? = null,
        columns: Int = 80,
        rows: Int = 24
    ): PtySession

    fun getSession(sessionId: String): PtySession?
    fun getAllSessions(): List<PtySession>
    suspend fun closeSession(sessionId: String)
    suspend fun closeAllSessions()
}

class PtySessionImpl(
    override val sessionId: String,
    private val pty: Pty,
    private val scope: CoroutineScope
) : PtySession {

    private val _outputFlow = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 64)
    override val outputFlow: Flow<ByteArray> = _outputFlow.asSharedFlow()

    private val _exitCodeFlow = MutableSharedFlow<Int>(replay = 1)
    override val exitCodeFlow: Flow<Int> = _exitCodeFlow

    private var readJob: Job? = null
    private var waitJob: Job? = null

    @Volatile
    override var isAlive: Boolean = true
        private set

    init {
        readJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            try {
                while (true) {
                    val bytesRead = pty.stdout.read(buffer)
                    if (bytesRead <= 0) break
                    _outputFlow.emit(buffer.copyOf(bytesRead))
                }
            } catch (_: Exception) {
            } finally {
                isAlive = false
            }
        }

        waitJob = scope.launch(Dispatchers.IO) {
            val exitCode = pty.waitFor()
            isAlive = false
            _exitCodeFlow.emit(exitCode)
        }
    }

    override suspend fun write(data: ByteArray) {
        pty.stdin.write(data)
        pty.stdin.flush()
    }

    override suspend fun write(text: String) {
        write(text.toByteArray())
    }

    override suspend fun resize(columns: Int, rows: Int) {
        pty.setWindowSize(rows, columns)
    }

    override suspend fun close() {
        isAlive = false
        readJob?.cancel()
        waitJob?.cancel()
        pty.destroy()
    }
}

class PtySessionManagerImpl : PtySessionManager {

    private val sessions = ConcurrentHashMap<String, PtySessionImpl>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun createSession(
        command: String,
        env: Map<String, String>,
        workingDir: File?,
        columns: Int,
        rows: Int
    ): PtySession {
        val sessionId = UUID.randomUUID().toString()
        val cmdArray = arrayOf("/bin/sh", "-c", command)
        val dir = workingDir ?: File("/")

        val pty = Pty.start(cmdArray, env, dir)
        pty.setWindowSize(rows, columns)

        val session = PtySessionImpl(sessionId, pty, scope)
        sessions[sessionId] = session
        return session
    }

    override suspend fun createSession(
        command: List<String>,
        env: Map<String, String>,
        workingDir: File?,
        columns: Int,
        rows: Int
    ): PtySession {
        val sessionId = UUID.randomUUID().toString()
        val cmdArray = command.toTypedArray()
        val dir = workingDir ?: File("/")

        val pty = Pty.start(cmdArray, env, dir)
        pty.setWindowSize(rows, columns)

        val session = PtySessionImpl(sessionId, pty, scope)
        sessions[sessionId] = session
        return session
    }

    override fun getSession(sessionId: String): PtySession? = sessions[sessionId]

    override fun getAllSessions(): List<PtySession> = sessions.values.toList()

    override suspend fun closeSession(sessionId: String) {
        sessions.remove(sessionId)?.close()
    }

    override suspend fun closeAllSessions() {
        sessions.values.forEach { it.close() }
        sessions.clear()
    }
}
