package com.rostrum.core.ssh.terminal

/**
 * 终端转发策略
 * 
 * 根据网络条件和终端使用模式选择最优的转发策略
 * 
 * @author OmniMaster
 * @license BSD-2-Clause
 */
sealed class TerminalForwardingStrategy {
    
    /**
     * 直接转发
     * 
     * 适用于低延迟网络环境（<50ms）
     * 直接传输原始数据，无缓冲无压缩
     */
    object Direct : TerminalForwardingStrategy() {
        override fun toString() = "Direct"
    }
    
    /**
     * 智能缓冲
     * 
     * 适用于中等延迟网络环境（50-200ms）
     * 使用缓冲区合并小数据包，减少网络往返
     * 
     * @param bufferSize 缓冲区大小（字节）
     * @param flushIntervalMs 刷新间隔（毫秒）
     * @param enableCompression 是否启用压缩
     */
    data class SmartBuffering(
        val bufferSize: Int = DEFAULT_BUFFER_SIZE,
        val flushIntervalMs: Long = DEFAULT_FLUSH_INTERVAL,
        val enableCompression: Boolean = false
    ) : TerminalForwardingStrategy() {
        companion object {
            const val DEFAULT_BUFFER_SIZE = 8192
            const val DEFAULT_FLUSH_INTERVAL = 50L
        }
    }
    
    /**
     * 差分压缩
     * 
     * 适用于高延迟网络环境（>200ms）
     * 只传输变化的部分，减少数据量
     * 
     * @param compressionLevel 压缩级别（1-9）
     * @param diffThreshold 差分阈值（变化超过此比例才进行差分）
     */
    data class DifferentialCompression(
        val compressionLevel: Int = DEFAULT_COMPRESSION_LEVEL,
        val diffThreshold: Float = DEFAULT_DIFF_THRESHOLD
    ) : TerminalForwardingStrategy() {
        companion object {
            const val DEFAULT_COMPRESSION_LEVEL = 6
            const val DEFAULT_DIFF_THRESHOLD = 0.3f
        }
    }
    
    /**
     * 预测性预加载
     * 
     * 适用于交互式命令行场景
     * 预测常用命令输出，提前缓存
     * 
     * @param prefetchCommands 预取命令列表
     * @param cacheSize 缓存大小（条目数）
     */
    data class PredictivePreload(
        val prefetchCommands: Set<String> = DEFAULT_PREFETCH_COMMANDS,
        val cacheSize: Int = DEFAULT_CACHE_SIZE
    ) : TerminalForwardingStrategy() {
        companion object {
            const val DEFAULT_CACHE_SIZE = 100
            val DEFAULT_PREFETCH_COMMANDS = setOf(
                "ls", "pwd", "cd", "cat", "echo",
                "git status", "git diff", "git log",
                "docker ps", "docker images"
            )
        }
    }
    
    /**
     * 混合模式
     * 
     * 根据数据类型自动选择最优策略
     * 简单输出本地渲染，复杂输出远程渲染
     * 
     * @param localRenderThreshold 本地渲染阈值（低于此复杂度本地渲染）
     * @param remoteRenderThreshold 远程渲染阈值（高于此复杂度远程渲染）
     */
    data class Hybrid(
        val localRenderThreshold: Int = DEFAULT_LOCAL_THRESHOLD,
        val remoteRenderThreshold: Int = DEFAULT_REMOTE_THRESHOLD
    ) : TerminalForwardingStrategy() {
        companion object {
            const val DEFAULT_LOCAL_THRESHOLD = 100
            const val DEFAULT_REMOTE_THRESHOLD = 1000
        }
    }
}

/**
 * 转发策略选择器接口
 */
interface IForwardingStrategySelector {
    /**
     * 根据网络延迟选择最优策略
     * 
     * @param latencyMs 网络延迟（毫秒）
     * @return 推荐的转发策略
     */
    suspend fun selectOptimalStrategy(latencyMs: Long): TerminalForwardingStrategy
    
    /**
     * 根据使用模式选择策略
     * 
     * @param usagePattern 使用模式
     * @param latencyMs 网络延迟
     * @return 推荐的转发策略
     */
    suspend fun selectForUsagePattern(
        usagePattern: TerminalUsagePattern,
        latencyMs: Long
    ): TerminalForwardingStrategy
}

/**
 * 终端使用模式
 */
enum class TerminalUsagePattern {
    /**
     * 交互式命令行
     */
    INTERACTIVE,
    
    /**
     * 批量输出（如日志查看）
     */
    BATCH_OUTPUT,
    
    /**
     * 文件编辑（vim/nano等）
     */
    EDITOR,
    
    /**
     * 长时间运行任务
     */
    LONG_RUNNING
}

/**
 * 默认策略选择器实现
 */
class DefaultForwardingStrategySelector : IForwardingStrategySelector {
    
    override suspend fun selectOptimalStrategy(latencyMs: Long): TerminalForwardingStrategy {
        return when {
            latencyMs < 50 -> TerminalForwardingStrategy.Direct
            latencyMs < 100 -> TerminalForwardingStrategy.SmartBuffering(
                flushIntervalMs = 30
            )
            latencyMs < 200 -> TerminalForwardingStrategy.SmartBuffering(
                flushIntervalMs = 50,
                enableCompression = true
            )
            else -> TerminalForwardingStrategy.DifferentialCompression()
        }
    }
    
    override suspend fun selectForUsagePattern(
        usagePattern: TerminalUsagePattern,
        latencyMs: Long
    ): TerminalForwardingStrategy {
        return when (usagePattern) {
            TerminalUsagePattern.INTERACTIVE -> {
                if (latencyMs < 100) {
                    TerminalForwardingStrategy.PredictivePreload()
                } else {
                    TerminalForwardingStrategy.SmartBuffering(
                        flushIntervalMs = 30,
                        enableCompression = latencyMs > 150
                    )
                }
            }
            TerminalUsagePattern.BATCH_OUTPUT -> {
                TerminalForwardingStrategy.SmartBuffering(
                    bufferSize = 16384,
                    flushIntervalMs = 100,
                    enableCompression = true
                )
            }
            TerminalUsagePattern.EDITOR -> {
                // 编辑器需要低延迟响应
                if (latencyMs < 100) {
                    TerminalForwardingStrategy.Direct
                } else {
                    TerminalForwardingStrategy.Hybrid()
                }
            }
            TerminalUsagePattern.LONG_RUNNING -> {
                TerminalForwardingStrategy.SmartBuffering(
                    bufferSize = 32768,
                    flushIntervalMs = 200,
                    enableCompression = true
                )
            }
        }
    }
}

/**
 * 网络质量级别
 */
enum class NetworkQuality {
    EXCELLENT,  // < 50ms
    GOOD,       // 50-100ms
    FAIR,       // 100-200ms
    POOR        // > 200ms
}

/**
 * 从延迟获取网络质量
 */
fun getNetworkQuality(latencyMs: Long): NetworkQuality {
    return when {
        latencyMs < 50 -> NetworkQuality.EXCELLENT
        latencyMs < 100 -> NetworkQuality.GOOD
        latencyMs < 200 -> NetworkQuality.FAIR
        else -> NetworkQuality.POOR
    }
}
