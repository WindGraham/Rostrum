package com.rostrum.core.ssh.terminal

import android.util.Log
import com.rostrum.core.ssh.session.SshTerminalSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * SSH 终端代理
 * 
 * 在终端会话和UI之间提供智能转发层
 * 根据策略优化数据传输和显示
 * 
 * @author OmniMaster
 * @license BSD-2-Clause
 */
class SshTerminalProxy(
    private val session: SshTerminalSession,
    private var strategy: TerminalForwardingStrategy = TerminalForwardingStrategy.Direct
) {
    companion object {
        private const val TAG = "SshTerminalProxy"
    }
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 输出缓冲区
    private val outputBuffer = StringBuilder()
    private var lastFlushTime = System.currentTimeMillis()
    
    // 用于差分压缩的历史状态
    private var lastScreenState: String = ""
    
    // 命令预测缓存
    private val predictionCache = mutableMapOf<String, String>()
    
    // 处理后的输出流
    private val _processedOutput = MutableSharedFlow<ProcessedOutput>(
        replay = 0, 
        extraBufferCapacity = 64
    )
    val processedOutput: SharedFlow<ProcessedOutput> = _processedOutput.asSharedFlow()
    
    // 统计信息
    private var totalBytesReceived = 0L
    private var totalBytesProcessed = 0L
    
    private var flushJob: Job? = null
    
    init {
        startProcessing()
    }
    
    /**
     * 更新转发策略
     */
    fun updateStrategy(newStrategy: TerminalForwardingStrategy) {
        Log.d(TAG, "更新策略: $strategy -> $newStrategy")
        strategy = newStrategy
        
        // 根据新策略调整处理方式
        when (newStrategy) {
            is TerminalForwardingStrategy.SmartBuffering -> {
                startBufferedFlush(newStrategy.flushIntervalMs)
            }
            else -> {
                flushJob?.cancel()
            }
        }
    }
    
    /**
     * 处理输入数据
     */
    fun processOutput(data: ByteArray): String {
        totalBytesReceived += data.size
        
        val result = when (val currentStrategy = strategy) {
            is TerminalForwardingStrategy.Direct -> {
                processDirect(data)
            }
            is TerminalForwardingStrategy.SmartBuffering -> {
                processBuffered(data, currentStrategy)
            }
            is TerminalForwardingStrategy.DifferentialCompression -> {
                processDifferential(data, currentStrategy)
            }
            is TerminalForwardingStrategy.PredictivePreload -> {
                processPredictive(data, currentStrategy)
            }
            is TerminalForwardingStrategy.Hybrid -> {
                processHybrid(data, currentStrategy)
            }
        }
        
        totalBytesProcessed += result.length
        return result
    }
    
    /**
     * 直接转发处理
     */
    private fun processDirect(data: ByteArray): String {
        return String(data, Charsets.UTF_8)
    }
    
    /**
     * 缓冲处理
     */
    private fun processBuffered(
        data: ByteArray, 
        settings: TerminalForwardingStrategy.SmartBuffering
    ): String {
        val text = String(data, Charsets.UTF_8)
        
        synchronized(outputBuffer) {
            outputBuffer.append(text)
            
            // 检查是否需要刷新
            val now = System.currentTimeMillis()
            val shouldFlush = outputBuffer.length >= settings.bufferSize ||
                    (now - lastFlushTime) >= settings.flushIntervalMs
            
            if (shouldFlush) {
                val result = outputBuffer.toString()
                outputBuffer.clear()
                lastFlushTime = now
                
                // 如果启用压缩，对大数据块进行压缩传输
                return if (settings.enableCompression && result.length > 1024) {
                    // 在实际使用中，这里会进行压缩
                    // 但对于本地处理，直接返回原文
                    result
                } else {
                    result
                }
            }
        }
        
        return ""  // 缓冲中，暂不返回
    }
    
    /**
     * 差分压缩处理
     */
    private fun processDifferential(
        data: ByteArray,
        settings: TerminalForwardingStrategy.DifferentialCompression
    ): String {
        val text = String(data, Charsets.UTF_8)
        val newState = lastScreenState + text
        
        // 计算差异
        val diff = calculateDiff(lastScreenState, newState)
        lastScreenState = newState
        
        // 如果差异超过阈值，发送差分数据
        return if (diff.changeRatio > settings.diffThreshold) {
            diff.changes
        } else {
            text  // 差异较小，直接发送
        }
    }
    
    /**
     * 预测性预加载处理
     */
    private fun processPredictive(
        data: ByteArray,
        settings: TerminalForwardingStrategy.PredictivePreload
    ): String {
        val text = String(data, Charsets.UTF_8)
        
        // 检测命令提示符，尝试预测下一个命令
        if (text.contains("$") || text.contains("#") || text.contains(">")) {
            // 可以在这里实现命令预测逻辑
            // 例如：分析历史命令，预测用户可能输入的下一个命令
        }
        
        return text
    }
    
    /**
     * 混合模式处理
     */
    private fun processHybrid(
        data: ByteArray,
        settings: TerminalForwardingStrategy.Hybrid
    ): String {
        val text = String(data, Charsets.UTF_8)
        val complexity = estimateComplexity(text)
        
        return when {
            complexity < settings.localRenderThreshold -> {
                // 简单输出，本地处理
                text
            }
            complexity > settings.remoteRenderThreshold -> {
                // 复杂输出，标记为需要特殊处理
                scope.launch {
                    _processedOutput.emit(ProcessedOutput.ComplexOutput(text))
                }
                text
            }
            else -> {
                // 中等复杂度，正常处理
                text
            }
        }
    }
    
    /**
     * 启动数据处理
     */
    private fun startProcessing() {
        scope.launch {
            session.outputFlow.collect { text ->
                val processed = processOutput(text.toByteArray())
                if (processed.isNotEmpty()) {
                    _processedOutput.emit(ProcessedOutput.Text(processed))
                }
            }
        }
    }
    
    /**
     * 启动定时刷新（用于缓冲模式）
     */
    private fun startBufferedFlush(intervalMs: Long) {
        flushJob?.cancel()
        flushJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                flushBuffer()
            }
        }
    }
    
    /**
     * 刷新缓冲区
     */
    private fun flushBuffer() {
        synchronized(outputBuffer) {
            if (outputBuffer.isNotEmpty()) {
                val text = outputBuffer.toString()
                outputBuffer.clear()
                lastFlushTime = System.currentTimeMillis()
                
                scope.launch {
                    _processedOutput.emit(ProcessedOutput.Text(text))
                }
            }
        }
    }
    
    /**
     * 计算文本差异
     */
    private fun calculateDiff(old: String, new: String): DiffResult {
        if (old.isEmpty()) {
            return DiffResult(new, 1.0f)
        }
        
        // 简化的差异计算
        val commonPrefix = old.commonPrefixWith(new).length
        val commonSuffix = old.commonSuffixWith(new).length
        
        val changes = if (commonPrefix + commonSuffix < new.length) {
            new.substring(commonPrefix, new.length - commonSuffix)
        } else {
            ""
        }
        
        val changeRatio = if (old.length > 0) {
            1 - (commonPrefix + commonSuffix).toFloat() / old.length
        } else {
            1.0f
        }
        
        return DiffResult(changes, changeRatio)
    }
    
    /**
     * 估计文本复杂度
     */
    private fun estimateComplexity(text: String): Int {
        var complexity = 0
        
        // ANSI 转义序列数量
        val ansiPattern = Regex("\u001B\\[[0-9;]*m")
        complexity += ansiPattern.findAll(text).count() * 10
        
        // 特殊字符数量
        complexity += text.count { it.code < 32 || it.code > 126 }
        
        // 换行数量
        complexity += text.count { it == '\n' } * 2
        
        // 文本长度
        complexity += text.length / 100
        
        return complexity
    }
    
    /**
     * 获取统计信息
     */
    fun getStatistics(): ProxyStatistics {
        val compressionRatio = if (totalBytesReceived > 0) {
            totalBytesProcessed.toFloat() / totalBytesReceived
        } else {
            1.0f
        }
        
        return ProxyStatistics(
            totalBytesReceived = totalBytesReceived,
            totalBytesProcessed = totalBytesProcessed,
            compressionRatio = compressionRatio,
            currentStrategy = strategy
        )
    }
    
    /**
     * 关闭代理
     */
    fun close() {
        flushJob?.cancel()
        scope.cancel()
    }
    
    /**
     * 差异结果
     */
    private data class DiffResult(
        val changes: String,
        val changeRatio: Float
    )
}

/**
 * 处理后的输出
 */
sealed class ProcessedOutput {
    /**
     * 普通文本输出
     */
    data class Text(val content: String) : ProcessedOutput()
    
    /**
     * 复杂输出（需要特殊渲染）
     */
    data class ComplexOutput(val content: String) : ProcessedOutput()
    
    /**
     * 压缩数据
     */
    data class Compressed(val data: ByteArray, val originalSize: Int) : ProcessedOutput() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Compressed
            return data.contentEquals(other.data) && originalSize == other.originalSize
        }
        
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + originalSize
            return result
        }
    }
}

/**
 * 代理统计信息
 */
data class ProxyStatistics(
    val totalBytesReceived: Long,
    val totalBytesProcessed: Long,
    val compressionRatio: Float,
    val currentStrategy: TerminalForwardingStrategy
)

/**
 * 数据压缩工具
 */
object DataCompressor {
    
    /**
     * 压缩数据
     */
    fun compress(data: ByteArray, level: Int = Deflater.DEFAULT_COMPRESSION): ByteArray {
        val deflater = Deflater(level)
        deflater.setInput(data)
        deflater.finish()
        
        val outputStream = ByteArrayOutputStream(data.size)
        val buffer = ByteArray(1024)
        
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        
        deflater.end()
        return outputStream.toByteArray()
    }
    
    /**
     * 解压数据
     */
    fun decompress(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        
        val outputStream = ByteArrayOutputStream(data.size)
        val buffer = ByteArray(1024)
        
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        
        inflater.end()
        return outputStream.toByteArray()
    }
}
