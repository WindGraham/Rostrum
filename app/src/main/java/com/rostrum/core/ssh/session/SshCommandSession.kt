package com.rostrum.core.ssh.session

import android.util.Log
import com.jcraft.jsch.ChannelExec
import com.rostrum.core.ssh.connection.ISshConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * SSH 命令执行会话
 * 
 * 用于执行单个或多个远程命令
 * 支持同步执行和流式输出
 * 
 * @author OmniMaster
 * @license BSD-2-Clause
 */
class SshCommandSession(
    override val connection: ISshConnection
) : ISshSession {
    
    companion object {
        private const val TAG = "SshCommandSession"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 10_000
    }
    
    override val id: String = UUID.randomUUID().toString()
    
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    override val state: StateFlow<SessionState> = _state.asStateFlow()
    
    override val isActive: Boolean
        get() = _state.value.isRunning
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override suspend fun start(): Result<Unit> {
        _state.value = SessionState.Running
        return Result.success(Unit)
    }
    
    override suspend fun closeAsync() {
        close()
    }
    
    override fun close() {
        _state.value = SessionState.Closed
        scope.cancel()
    }
    
    /**
     * 同步执行命令
     * 
     * @param command 要执行的命令
     * @param timeoutMs 超时时间（毫秒）
     * @return 执行结果
     */
    suspend fun exec(
        command: String,
        timeoutMs: Long = 30_000
    ): Result<CommandResult> = withContext(Dispatchers.IO) {
        val channelResult = connection.openExecChannel()
        if (channelResult.isFailure) {
            return@withContext Result.failure(channelResult.exceptionOrNull()!!)
        }
        
        val channel = channelResult.getOrThrow()
        
        try {
            channel.setCommand(command)
            
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            
            channel.outputStream = stdout
            channel.setErrStream(stderr)
            
            val startTime = System.currentTimeMillis()
            channel.connect(CONNECT_TIMEOUT)
            
            // 等待命令执行完成
            withTimeout(timeoutMs) {
                while (!channel.isClosed) {
                    delay(50)
                }
            }
            
            val elapsed = System.currentTimeMillis() - startTime
            channel.disconnect()
            connection.touch()
            
            Result.success(CommandResult(
                command = command,
                stdout = stdout.toString("UTF-8"),
                stderr = stderr.toString("UTF-8"),
                exitCode = channel.exitStatus,
                durationMs = elapsed
            ))
            
        } catch (e: TimeoutCancellationException) {
            channel.disconnect()
            Result.failure(IOException("命令执行超时: $command"))
        } catch (e: Exception) {
            Log.e(TAG, "执行命令失败: $command", e)
            channel.disconnect()
            Result.failure(IOException("执行命令失败: ${e.message}", e))
        }
    }
    
    /**
     * 流式执行命令
     * 
     * @param command 要执行的命令
     * @return 输出流
     */
    fun execStream(command: String): Flow<CommandChunk> = flow {
        val channelResult = connection.openExecChannel()
        if (channelResult.isFailure) {
            emit(CommandChunk.Error(channelResult.exceptionOrNull()?.message ?: "打开通道失败"))
            return@flow
        }
        
        val channel = channelResult.getOrThrow()
        
        try {
            channel.setCommand(command)
            
            val inputStream = channel.inputStream
            val errStream = channel.errStream
            
            emit(CommandChunk.Started(command))
            channel.connect(CONNECT_TIMEOUT)
            
            val buffer = ByteArray(BUFFER_SIZE)
            
            // 并行读取 stdout 和 stderr
            coroutineScope {
                val stdoutJob = launch {
                    readStream(inputStream, buffer) { text ->
                        emit(CommandChunk.Stdout(text))
                    }
                }
                
                val stderrJob = launch {
                    readStream(errStream, buffer) { text ->
                        emit(CommandChunk.Stderr(text))
                    }
                }
                
                // 等待命令完成
                while (!channel.isClosed) {
                    delay(50)
                }
                
                stdoutJob.cancel()
                stderrJob.cancel()
            }
            
            connection.touch()
            emit(CommandChunk.Completed(channel.exitStatus))
            
        } catch (e: Exception) {
            Log.e(TAG, "流式执行命令失败: $command", e)
            emit(CommandChunk.Error(e.message ?: "执行失败"))
        } finally {
            channel.disconnect()
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 批量执行命令
     * 
     * @param commands 命令列表
     * @param stopOnError 遇到错误是否停止
     * @return 执行结果列表
     */
    suspend fun execBatch(
        commands: List<String>,
        stopOnError: Boolean = true
    ): List<CommandResult> {
        val results = mutableListOf<CommandResult>()
        
        for (command in commands) {
            val result = exec(command)
            
            if (result.isSuccess) {
                results.add(result.getOrThrow())
                
                if (stopOnError && result.getOrThrow().exitCode != 0) {
                    break
                }
            } else {
                // 创建错误结果
                results.add(CommandResult(
                    command = command,
                    stdout = "",
                    stderr = result.exceptionOrNull()?.message ?: "执行失败",
                    exitCode = -1,
                    durationMs = 0
                ))
                
                if (stopOnError) {
                    break
                }
            }
        }
        
        return results
    }
    
    /**
     * 执行脚本
     * 
     * @param script 脚本内容
     * @param shell 解释器（默认bash）
     * @return 执行结果
     */
    suspend fun execScript(
        script: String,
        shell: String = "/bin/bash"
    ): Result<CommandResult> {
        // 使用 heredoc 方式执行脚本
        val command = """$shell << 'OMNIMASTER_SCRIPT_EOF'
$script
OMNIMASTER_SCRIPT_EOF"""
        return exec(command)
    }
    
    private suspend fun FlowCollector<CommandChunk>.readStream(
        stream: InputStream,
        buffer: ByteArray,
        onData: suspend (String) -> Unit
    ) {
        try {
            while (true) {
                val available = stream.available()
                if (available > 0) {
                    val bytesRead = stream.read(buffer, 0, minOf(available, buffer.size))
                    if (bytesRead > 0) {
                        val text = String(buffer, 0, bytesRead, Charsets.UTF_8)
                        onData(text)
                    }
                }
                delay(10)
            }
        } catch (e: Exception) {
            // 流关闭或其他错误
        }
    }
}

/**
 * 命令执行结果
 */
data class CommandResult(
    val command: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long
) {
    /**
     * 命令是否成功（退出码为0）
     */
    val isSuccess: Boolean
        get() = exitCode == 0
    
    /**
     * 获取合并的输出（stdout + stderr）
     */
    val output: String
        get() = buildString {
            if (stdout.isNotEmpty()) append(stdout)
            if (stderr.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(stderr)
            }
        }
}

/**
 * 命令输出块
 */
sealed class CommandChunk {
    /**
     * 命令开始执行
     */
    data class Started(val command: String) : CommandChunk()
    
    /**
     * 标准输出
     */
    data class Stdout(val text: String) : CommandChunk()
    
    /**
     * 标准错误
     */
    data class Stderr(val text: String) : CommandChunk()
    
    /**
     * 命令完成
     */
    data class Completed(val exitCode: Int) : CommandChunk()
    
    /**
     * 执行错误
     */
    data class Error(val message: String) : CommandChunk()
}
