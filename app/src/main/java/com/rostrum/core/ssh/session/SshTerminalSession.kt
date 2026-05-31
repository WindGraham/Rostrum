package com.rostrum.core.ssh.session

import android.util.Log
import com.jcraft.jsch.ChannelShell
import com.rostrum.core.shell.IShellSession
import com.rostrum.core.ssh.connection.ISshConnection
import com.rostrum.core.ssh.connection.TerminalDimensions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SSH 终端会话
 * 
 * 实现 IShellSession 接口，与现有终端UI兼容
 * 提供交互式SSH Shell功能
 * 
 * @author OmniMaster
 * @license BSD-2-Clause
 */
class SshTerminalSession(
    override val connection: ISshConnection,
    initialColumns: Int = 80,
    initialRows: Int = 24
) : ISshSession, IShellSession {
    
    companion object {
        private const val TAG = "SshTerminalSession"
        private const val BUFFER_SIZE = 8192
        private const val OUTPUT_LIMIT = 100_000
    }
    
    override val id: String = UUID.randomUUID().toString()
    
    // 终端尺寸
    private var dimensions = TerminalDimensions(initialColumns, initialRows)
    
    // JSch Shell 通道
    private var channel: ChannelShell? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    // 状态管理
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    override val state: StateFlow<SessionState> = _state.asStateFlow()
    
    override val isActive: Boolean
        get() = _state.value.isRunning && channel?.isConnected == true
    
    // IShellSession 实现
    private val _output = MutableStateFlow("")
    override val output: StateFlow<String> = _output.asStateFlow()
    
    private val _commandHistory = CopyOnWriteArrayList<String>()
    override val commandHistory: List<String> get() = _commandHistory.toList()
    
    // 流式输出
    private val _outputFlow = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 64)
    val outputFlow: SharedFlow<String> = _outputFlow.asSharedFlow()
    
    private val _errorFlow = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 64)
    val errorFlow: SharedFlow<String> = _errorFlow.asSharedFlow()
    
    // 退出码
    private val _exitCode = MutableStateFlow<Int?>(null)
    val exitCode: StateFlow<Int?> = _exitCode.asStateFlow()
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var readJob: Job? = null
    
    private val isReady = AtomicBoolean(false)
    
    override suspend fun start(): Result<Unit> = withContext(Dispatchers.IO) {
        if (_state.value.isRunning) {
            return@withContext Result.success(Unit)
        }
        
        _state.value = SessionState.Starting
        
        try {
            // 打开 Shell 通道
            val channelResult = connection.openShellChannel()
            if (channelResult.isFailure) {
                val error = channelResult.exceptionOrNull()!!
                _state.value = SessionState.Error("无法打开Shell通道", error)
                return@withContext Result.failure(error)
            }
            
            channel = channelResult.getOrThrow().apply {
                // 设置终端类型
                setPtyType("xterm-256color")
                setPtySize(dimensions.columns, dimensions.rows, 
                    dimensions.widthPixels, dimensions.heightPixels)
            }
            
            // 获取输入输出流
            inputStream = channel!!.inputStream
            outputStream = channel!!.outputStream
            
            // 连接通道
            channel!!.connect(10_000)
            
            // 启动读取任务
            startReading()
            
            _state.value = SessionState.Running
            isReady.set(true)
            
            Log.i(TAG, "SSH终端会话已启动: $id")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "启动SSH终端会话失败", e)
            _state.value = SessionState.Error("启动失败: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun closeAsync() {
        closeInternal()
    }
    
    override fun close() {
        closeInternal()
    }
    
    private fun closeInternal() {
        if (_state.value.isClosed) return
        
        Log.i(TAG, "关闭SSH终端会话: $id")
        
        isReady.set(false)
        readJob?.cancel()
        
        try {
            outputStream?.close()
            inputStream?.close()
            
            channel?.let { ch ->
                if (ch.isConnected) {
                    _exitCode.value = ch.exitStatus
                    ch.disconnect()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "关闭会话时出错", e)
        } finally {
            channel = null
            inputStream = null
            outputStream = null
            _state.value = SessionState.Closed
            scope.cancel()
        }
    }
    
    // IShellSession 实现
    
    override fun startSession() {
        scope.launch {
            start()
        }
    }
    
    override fun sendCommand(command: String) {
        if (!isActive) {
            Log.w(TAG, "会话未激活，无法发送命令")
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                // 添加到历史记录
                _commandHistory.add(command)
                
                // 发送命令（追加换行符）
                val cmdWithNewline = if (command.endsWith("\n")) command else "$command\n"
                outputStream?.write(cmdWithNewline.toByteArray())
                outputStream?.flush()
                
                connection.touch()
                
            } catch (e: Exception) {
                Log.e(TAG, "发送命令失败", e)
                _errorFlow.emit("发送命令失败: ${e.message}")
            }
        }
    }
    
    override fun clearOutput() {
        _output.value = ""
    }
    
    override fun appendToOutput(text: String) {
        val currentOutput = _output.value
        val newOutput = if (currentOutput.length + text.length > OUTPUT_LIMIT) {
            // 截断旧内容
            val keepLength = OUTPUT_LIMIT - text.length
            currentOutput.takeLast(keepLength.coerceAtLeast(0)) + text
        } else {
            currentOutput + text
        }
        _output.value = newOutput
    }
    
    override fun isReady(): Boolean = isReady.get()
    
    // SSH 扩展功能
    
    /**
     * 调整终端大小
     */
    fun resize(columns: Int, rows: Int) {
        dimensions = dimensions.copy(columns = columns, rows = rows)
        channel?.setPtySize(columns, rows, dimensions.widthPixels, dimensions.heightPixels)
        Log.d(TAG, "终端大小调整为: ${columns}x${rows}")
    }
    
    /**
     * 发送原始数据
     */
    fun sendRaw(data: ByteArray) {
        if (!isActive) return
        
        scope.launch(Dispatchers.IO) {
            try {
                outputStream?.write(data)
                outputStream?.flush()
                connection.touch()
            } catch (e: Exception) {
                Log.e(TAG, "发送原始数据失败", e)
            }
        }
    }
    
    /**
     * 发送控制字符
     */
    fun sendControl(controlChar: Char) {
        // 控制字符是 ASCII 0-31
        val ctrlCode = (controlChar.uppercaseChar() - 'A' + 1).toByte()
        sendRaw(byteArrayOf(ctrlCode))
    }
    
    /**
     * 发送中断信号 (Ctrl+C)
     */
    fun sendInterrupt() {
        sendControl('C')
    }
    
    /**
     * 发送 EOF (Ctrl+D)
     */
    fun sendEof() {
        sendControl('D')
    }
    
    /**
     * 获取当前终端尺寸
     */
    fun getDimensions(): TerminalDimensions = dimensions
    
    // 私有方法
    
    private fun startReading() {
        readJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(BUFFER_SIZE)
            val stream = inputStream ?: return@launch
            
            try {
                while (isActive) {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead == -1) {
                        // 流已关闭
                        break
                    }
                    
                    if (bytesRead > 0) {
                        val text = String(buffer, 0, bytesRead, Charsets.UTF_8)
                        
                        // 更新输出
                        appendToOutput(text)
                        
                        // 发送到流
                        _outputFlow.emit(text)
                        
                        connection.touch()
                    }
                }
            } catch (e: IOException) {
                if (isActive) {
                    Log.e(TAG, "读取输出失败", e)
                    _state.value = SessionState.Error("读取失败: ${e.message}", e)
                }
            }
            
            // 获取退出码
            channel?.let { ch ->
                if (!ch.isConnected) {
                    _exitCode.value = ch.exitStatus
                }
            }
        }
    }
}
