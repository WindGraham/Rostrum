package com.rostrum.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * 简单的Hex查看/编辑器
 * 目前主要实现查看功能
 */
@Composable
fun HexEditor(
    filePath: String?,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 顶部工具栏
        if (onClose != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 文件内容区域
        if (filePath == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("未选择文件")
            }
            return@Column
        }

        val file = File(filePath)
        if (!file.exists()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("文件不存在")
            }
            return@Column
        }

        // 每一行显示的字节数
        val bytesPerRow = 16
        val totalRows = (file.length() + bytesPerRow - 1) / bytesPerRow

        // 使用 key 来强制刷新，当文件改变时
        key(filePath) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                contentPadding = PaddingValues(4.dp)
            ) {
                items(
                    count = totalRows.toInt(),
                    key = { it }
                ) { rowIndex ->
                    HexRow(
                        file = file,
                        rowIndex = rowIndex.toLong(),
                        bytesPerRow = bytesPerRow
                    )
                }
            }
        }
    }
}

@Composable
private fun HexRow(
    file: File,
    rowIndex: Long,
    bytesPerRow: Int
) {
    // 读取数据
    // 注意：在Composable中做IO操作通常不推荐，但为了实现懒加载列表，
    // 这里做少量读取通常是可以接受的，或者是使用 produceState
    val offset = rowIndex * bytesPerRow
    
    // 使用 produceState 异步读取数据
    val rowData by produceState<ByteArray?>(initialValue = null, key1 = rowIndex) {
        withContext(Dispatchers.IO) {
            try {
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(offset)
                    val buffer = ByteArray(bytesPerRow)
                    val read = raf.read(buffer)
                    if (read > 0) {
                        if (read < bytesPerRow) {
                            value = buffer.copyOf(read)
                        } else {
                            value = buffer
                        }
                    } else {
                        value = ByteArray(0)
                    }
                }
            } catch (e: Exception) {
                value = null
            }
        }
    }

    // 渲染行
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp) // 给定固定高度，防止 LazyColumn 在内容未加载时无限重组导致 ANR/OOM
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        // 地址 (Always show Offset, calculation is cheap)
        Text(
            text = "%08X".format(offset),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(70.dp)
        )

        val currentRowData = rowData
        if (currentRowData != null) {
            // Hex 数据
            val hexString = StringBuilder()
            val asciiString = StringBuilder()
            
            for (i in 0 until bytesPerRow) {
                if (i < currentRowData.size) {
                    val byte = currentRowData[i]
                    hexString.append("%02X ".format(byte))
                    
                    // ASCII: 32-126 是可打印字符
                    val char = if (byte >= 32 && byte < 127) byte.toInt().toChar() else '.'
                    asciiString.append(char)
                } else {
                    hexString.append("   ") // 空白占位
                    asciiString.append(' ')
                }
                
                // 每8个字节加一个额外空格
                if (i == 7) hexString.append(" ")
            }

            Text(
                text = hexString.toString(),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(3f)
            )

            // ASCII 预览
            Text(
                text = asciiString.toString(),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        } else {
            // Loading placeholder
            Text(
                text = "Loading...",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(4f)
            )
        }
    }
}