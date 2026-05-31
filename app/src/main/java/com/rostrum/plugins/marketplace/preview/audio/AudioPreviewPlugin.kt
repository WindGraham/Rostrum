package com.rostrum.plugins.marketplace.preview.audio

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rostrum.core.mcp.*
import com.rostrum.core.plugin.Plugin
import com.rostrum.core.plugin.PluginCapability
import com.rostrum.core.plugin.PluginCategory
import com.rostrum.core.plugin.PluginContext
import com.rostrum.core.plugin.models.FileInfo
import com.rostrum.core.plugin.providers.FilePreviewPlugin
import com.rostrum.core.plugin.providers.PreviewMetadata
import com.rostrum.core.plugin.providers.PreviewResult
import com.rostrum.core.plugin.providers.ToolPlugin
import kotlinx.coroutines.delay
import java.io.File

/**
 * 音频预览插件
 * 
 * 支持: mp3, wav, ogg, m4a, flac, aac
 * 
 * 实现 ToolPlugin 接口，提供MCP工具供AI调用
 */
class AudioPreviewPlugin : Plugin, FilePreviewPlugin, ToolPlugin {
    
    override val id = "com.rostrum.plugin.preview.audio"
    override val name = "Audio Preview"
    override val version = "1.0.0"
    override val author = "OmniMaster Team"
    override val description = "音频预览插件，支持播放控制和元数据显示"
    override val category = PluginCategory.PREVIEW
    override val dependencies: List<String> = emptyList()
    
    override val supportedMimeTypes = listOf(
        "audio/mpeg", "audio/wav", "audio/ogg", "audio/mp4",
        "audio/flac", "audio/aac", "audio/midi"
    )
    
    override val supportedExtensions = listOf(
        ".mp3", ".wav", ".ogg", ".m4a", ".flac", ".aac", ".mid", ".midi", ".opus"
    )
    
    private var pluginContext: PluginContext? = null
    
    override suspend fun initialize(context: PluginContext): Result<Unit> {
        pluginContext = context
        return Result.success(Unit)
    }
    
    override suspend fun onActivate() = Result.success(Unit)
    override suspend fun onDeactivate() = Result.success(Unit)
    override suspend fun onDestroy(): Result<Unit> {
        pluginContext = null
        return Result.success(Unit)
    }
    
    override fun getCapabilities() = listOf(PluginCapability.FILE_PREVIEW)
    
    override fun canPreview(file: FileInfo): Boolean {
        return supportedExtensions.any { file.extension.equals(it, ignoreCase = true) } ||
               file.mimeType?.startsWith("audio/") == true
    }
    
    override suspend fun createPreview(file: FileInfo): PreviewResult {
        if (!canPreview(file)) return PreviewResult.Unsupported
        
        val fileObj = File(file.path)
        if (!fileObj.exists()) {
            return PreviewResult.Error("文件不存在: ${file.path}")
        }
        
        return PreviewResult.Success(
            previewComponent = { AudioPreviewContent(fileObj) },
            metadata = PreviewMetadata(
                title = file.name,
                description = "音频预览"
            )
        )
    }
    
    override fun getPreviewPriority(file: FileInfo) = 90
    
    // ==================== ToolPlugin 实现 ====================
    
    override val toolCategory = ToolCategory.FILE_SYSTEM
    
    override fun getMCPTools(): List<MCPTool> {
        return listOf(
            MCPTool(
                name = "audio_get_metadata",
                description = "获取音频文件的元数据（标题、艺术家、时长等）",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "音频文件路径")
                    ),
                    required = listOf("filePath")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "title" to JsonSchemaProperty(type = "string"),
                        "artist" to JsonSchemaProperty(type = "string"),
                        "album" to JsonSchemaProperty(type = "string"),
                        "duration" to JsonSchemaProperty(type = "number", description = "时长（秒）"),
                        "bitrate" to JsonSchemaProperty(type = "number", description = "比特率"),
                        "format" to JsonSchemaProperty(type = "string", description = "音频格式")
                    )
                ),
                handler = { args, _ ->
                    try {
                        val filePath = args["filePath"] as? String
                            ?: return@MCPTool MCPResult(success = false, error = MCPError("INVALID_ARGS", "缺少filePath参数"))
                        
                        val file = java.io.File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(success = false, error = MCPError("FILE_NOT_FOUND", "文件不存在"))
                        }
                        
                        val retriever = android.media.MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(filePath)
                            
                            val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension
                            val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "未知"
                            val album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "未知"
                            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                            val duration = durationStr?.toLongOrNull()?.div(1000) ?: 0L
                            val bitrate = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
                            val mimeType = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "unknown"
                            
                            MCPResult(
                                success = true,
                                data = mapOf(
                                    "success" to true,
                                    "title" to title,
                                    "artist" to artist,
                                    "album" to album,
                                    "duration" to duration,
                                    "bitrate" to bitrate,
                                    "format" to mimeType
                                )
                            )
                        } finally {
                            retriever.release()
                        }
                    } catch (e: Exception) {
                        MCPResult(success = false, error = MCPError("ERROR", e.message ?: "未知错误"))
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE)
            )
        )
    }
}

@Composable
private fun AudioPreviewContent(file: File) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var metadata by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // 加载媒体元数据
    LaunchedEffect(file) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            
            metadata = mapOf(
                "标题" to (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension),
                "艺术家" to (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "未知"),
                "专辑" to (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "未知"),
                "时长" to formatDuration(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0),
                "比特率" to "${(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0) / 1000} kbps"
            )
            retriever.release()
        } catch (e: Exception) {
            metadata = mapOf("文件" to file.name)
        }
    }
    
    // 初始化播放器
    DisposableEffect(file.absolutePath) {
        var player: MediaPlayer? = null
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
            }
            mediaPlayer = player
            duration = player.duration
        } catch (e: Exception) {
            error = "无法加载音频: ${e.message}"
            player?.release()
        }
        
        onDispose {
            try {
                mediaPlayer?.release()
            } catch (e: Exception) {
                // 忽略释放错误
            }
        }
    }
    
    // 更新进度
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let {
                currentPosition = it.currentPosition
            }
            delay(500)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (error != null) {
            Text(error!!, color = Color.Red)
            return@Column
        }
        
        Spacer(Modifier.height(32.dp))
        
        // 专辑图标
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = Color(0xFF1DB954)
        )
        
        Spacer(Modifier.height(24.dp))
        
        // 标题
        Text(
            metadata["标题"] ?: file.name,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            metadata["艺术家"] ?: "",
            color = Color.Gray,
            fontSize = 14.sp
        )
        
        Spacer(Modifier.height(32.dp))
        
        // 进度条
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                onValueChange = { 
                    mediaPlayer?.seekTo((it * duration).toInt())
                    currentPosition = (it * duration).toInt()
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF1DB954),
                    activeTrackColor = Color(0xFF1DB954)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(currentPosition.toLong()), color = Color.Gray, fontSize = 12.sp)
                Text(formatDuration(duration.toLong()), color = Color.Gray, fontSize = 12.sp)
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // 播放控制
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 后退 10s
            IconButton(onClick = {
                mediaPlayer?.let {
                    it.seekTo((it.currentPosition - 10000).coerceAtLeast(0))
                    currentPosition = it.currentPosition
                }
            }) {
                Icon(Icons.Default.Replay10, "后退10秒", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            
            // 播放/暂停
            FloatingActionButton(
                onClick = {
                    mediaPlayer?.let {
                        if (isPlaying) {
                            it.pause()
                        } else {
                            it.start()
                        }
                        isPlaying = !isPlaying
                    }
                },
                containerColor = Color(0xFF1DB954)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(36.dp)
                )
            }
            
            // 前进 10s
            IconButton(onClick = {
                mediaPlayer?.let {
                    it.seekTo((it.currentPosition + 10000).coerceAtMost(duration))
                    currentPosition = it.currentPosition
                }
            }) {
                Icon(Icons.Default.Forward10, "前进10秒", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        // 元数据
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF282828))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("文件信息", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                metadata.forEach { (key, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(key, color = Color.Gray, fontSize = 13.sp)
                        Text(value, color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 1000 / 60) % 60
    val hours = ms / 1000 / 60 / 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

