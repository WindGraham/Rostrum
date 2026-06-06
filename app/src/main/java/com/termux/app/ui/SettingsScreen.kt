package com.termux.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.termux.app.settings.SettingsStore
import com.termux.app.ssh.SshKeyManager

private val CardBg = Color(0xFF1E293B)
private val SectionTitle = Color(0xFF94A3B8)
private val BrightText = Color(0xFFE5E7EB)
private val AccentIndigo = Color(0xFF6366F1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.height(24.dp))

        // ── Terminal Section ──
        SettingsSectionHeader("终端")
        SettingsCard {
            // Font size
            var fontSize by remember { mutableStateOf(SettingsStore.terminalFontSize.toFloat()) }
            SettingsSliderRow(
                label = "字体大小",
                value = fontSize,
                valueText = "${fontSize.toInt()}",
                onValueChange = { fontSize = it },
                onValueChangeFinished = { SettingsStore.terminalFontSize = fontSize.toInt() },
                valueRange = 12f..28f
            )

            HorizontalDivider(color = Color(0xFF1F2937))

            // Font family dropdown
            var fontFamilyExpanded by remember { mutableStateOf(false) }
            var fontFamily by remember { mutableStateOf(SettingsStore.terminalFontFamily) }
            SettingsDropdownRow(
                label = "字体",
                currentValue = fontFamily,
                expanded = fontFamilyExpanded,
                onExpandedChange = { fontFamilyExpanded = it },
                options = listOf("Monospace", "Sans-serif"),
                onSelect = {
                    fontFamily = it
                    SettingsStore.terminalFontFamily = it
                    fontFamilyExpanded = false
                }
            )

            HorizontalDivider(color = Color(0xFF1F2937))

            // Cursor style dropdown
            var cursorExpanded by remember { mutableStateOf(false) }
            var cursorStyle by remember { mutableStateOf(SettingsStore.cursorStyle) }
            SettingsDropdownRow(
                label = "光标样式",
                currentValue = cursorStyle,
                expanded = cursorExpanded,
                onExpandedChange = { cursorExpanded = it },
                options = listOf("Block", "Underline", "I-Beam"),
                onSelect = {
                    cursorStyle = it
                    SettingsStore.cursorStyle = it
                    cursorExpanded = false
                }
            )

            HorizontalDivider(color = Color(0xFF1F2937))

            // Background color picker
            var bgColorExpanded by remember { mutableStateOf(false) }
            var bgColor by remember { mutableStateOf(SettingsStore.backgroundColor) }
            val darkColors = listOf("#000000", "#1E1E1E", "#0B1120", "#121212")
            val colorMap = mapOf(
                "#000000" to "纯黑",
                "#1E1E1E" to "VS Code 暗",
                "#0B1120" to "深蓝黑",
                "#121212" to "Material 暗"
            )
            SettingsDropdownRow(
                label = "背景颜色",
                currentValue = colorMap[bgColor] ?: bgColor,
                expanded = bgColorExpanded,
                onExpandedChange = { bgColorExpanded = it },
                options = darkColors.map { colorMap[it] ?: it },
                onSelect = { displayName ->
                    val hex = darkColors.first { (colorMap[it] ?: it) == displayName }
                    bgColor = hex
                    SettingsStore.backgroundColor = hex
                    bgColorExpanded = false
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── SSH Key Section ──
        SettingsSectionHeader("SSH 密钥")
        SettingsCard {
            var showPubKeyDialog by remember { mutableStateOf(false) }
            var pubKeyContent by remember { mutableStateOf("") }
            var keyExists by remember { mutableStateOf(SshKeyManager.hasKeyPair()) }

            SettingsButtonRow(
                label = if (keyExists) "重新生成密钥对" else "生成新密钥对",
                onClick = {
                    val result = SshKeyManager.generateKeyPair("rostrum")
                    if (result.isSuccess) {
                        keyExists = true
                        Toast.makeText(context, "密钥对生成成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "生成失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            )
            HorizontalDivider(color = Color(0xFF1F2937))
            SettingsButtonRow(
                label = "复制公钥",
                onClick = {
                    val pk = SshKeyManager.getPublicKey()
                    if (pk != null) {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("SSH Public Key", pk))
                        Toast.makeText(context, "公钥已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "未找到公钥，请先生成密钥对", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            HorizontalDivider(color = Color(0xFF1F2937))
            SettingsButtonRow(
                label = "导出公钥",
                onClick = {
                    val pk = SshKeyManager.getPublicKey()
                    if (pk != null) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, pk)
                            putExtra(Intent.EXTRA_SUBJECT, "SSH Public Key (rostrum)")
                        }
                        context.startActivity(Intent.createChooser(intent, "导出公钥"))
                    } else {
                        Toast.makeText(context, "未找到公钥，请先生成密钥对", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            HorizontalDivider(color = Color(0xFF1F2937))
            SettingsButtonRow(
                label = "查看公钥",
                onClick = {
                    val pk = SshKeyManager.getPublicKey()
                    if (pk != null) {
                        pubKeyContent = pk
                        showPubKeyDialog = true
                    } else {
                        Toast.makeText(context, "未找到公钥，请先生成密钥对", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            HorizontalDivider(color = Color(0xFF1F2937))
            SettingsButtonRow(
                label = "删除密钥对",
                labelColor = Color(0xFFEF4444),
                onClick = {
                    val keyDir = SshKeyManager.sshDir
                    val deleted = keyDir.listFiles()?.fold(true) { acc, f -> acc && f.delete() } ?: false
                    keyExists = !deleted
                    Toast.makeText(context, if (deleted) "密钥对已删除" else "删除失败", Toast.LENGTH_SHORT).show()
                }
            )

            // Show public key dialog
            if (showPubKeyDialog) {
                AlertDialog(
                    onDismissRequest = { showPubKeyDialog = false },
                    title = { Text("SSH 公钥", color = BrightText) },
                    text = {
                        androidx.compose.foundation.rememberScrollState().let { scrollState ->
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(
                                    pubKeyContent,
                                    color = BrightText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.verticalScroll(scrollState).heightIn(max = 400.dp)
                                )
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showPubKeyDialog = false }) { Text("关闭") } },
                    containerColor = Color(0xFF0F172A)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── File Preview Section ──
        SettingsSectionHeader("文件预览")
        SettingsCard {
            // Max preview size
            var previewSizeExpanded by remember { mutableStateOf(false) }
            var previewSize by remember {
                mutableStateOf(
                    when (SettingsStore.maxPreviewSize) {
                        1024 * 1024L -> "1 MB"
                        5 * 1024 * 1024L -> "5 MB"
                        10 * 1024 * 1024L -> "10 MB"
                        50 * 1024 * 1024L -> "50 MB"
                        else -> "Unlimited"
                    }
                )
            }
            SettingsDropdownRow(
                label = "最大预览文件大小",
                currentValue = previewSize,
                expanded = previewSizeExpanded,
                onExpandedChange = { previewSizeExpanded = it },
                options = listOf("1 MB", "5 MB", "10 MB", "50 MB", "Unlimited"),
                onSelect = {
                    previewSize = it
                    SettingsStore.maxPreviewSize = when (it) {
                        "1 MB" -> 1024 * 1024L
                        "5 MB" -> 5L * 1024 * 1024
                        "10 MB" -> 10L * 1024 * 1024
                        "50 MB" -> 50L * 1024 * 1024
                        else -> Long.MAX_VALUE
                    }
                    previewSizeExpanded = false
                }
            )

            HorizontalDivider(color = Color(0xFF1F2937))

            // Auto preview toggle
            var autoPreview by remember { mutableStateOf(SettingsStore.autoPreview) }
            SettingsToggleRow(
                label = "单击自动预览",
                checked = autoPreview,
                onCheckedChange = {
                    autoPreview = it
                    SettingsStore.autoPreview = it
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Connection Section ──
        SettingsSectionHeader("连接")
        SettingsCard {
            // StrictHostKeyChecking toggle
            var strictHostKey by remember { mutableStateOf(SettingsStore.strictHostKeyChecking) }
            SettingsToggleRow(
                label = "StrictHostKeyChecking",
                checked = strictHostKey,
                onCheckedChange = {
                    strictHostKey = it
                    SettingsStore.strictHostKeyChecking = it
                }
            )

            HorizontalDivider(color = Color(0xFF1F2937))

            // Default port
            var defaultPort by remember { mutableStateOf(SettingsStore.defaultPort.toString()) }
            SettingsTextFieldRow(
                label = "默认端口",
                value = defaultPort,
                onValueChange = { newValue ->
                    defaultPort = newValue.filter { it.isDigit() }
                    val port = newValue.toIntOrNull()
                    if (port != null && port in 1..65535) {
                        SettingsStore.defaultPort = port
                    }
                }
            )

            HorizontalDivider(color = Color(0xFF1F2937))

            // Connection timeout dropdown
            var timeoutExpanded by remember { mutableStateOf(false) }
            var timeout by remember {
                mutableStateOf(
                    when (SettingsStore.connectionTimeout) {
                        10 -> "10 秒"
                        30 -> "30 秒"
                        60 -> "60 秒"
                        else -> "30 秒"
                    }
                )
            }
            SettingsDropdownRow(
                label = "连接超时",
                currentValue = timeout,
                expanded = timeoutExpanded,
                onExpandedChange = { timeoutExpanded = it },
                options = listOf("10 秒", "30 秒", "60 秒"),
                onSelect = {
                    timeout = it
                    SettingsStore.connectionTimeout = when (it) {
                        "10 秒" -> 10
                        "30 秒" -> 30
                        "60 秒" -> 60
                        else -> 30
                    }
                    timeoutExpanded = false
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── About Section ──
        SettingsSectionHeader("关于")
        SettingsCard {
            SettingsInfoRow(label = "版本", value = "rostrum 1.0")
            HorizontalDivider(color = Color(0xFF1F2937))
            SettingsInfoRow(label = "基于", value = "Termux")
            HorizontalDivider(color = Color(0xFF1F2937))
            SettingsClickableRow(
                label = "GitHub",
                value = "github.com/anomalyco/opencode",
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                    intent.data = android.net.Uri.parse("https://github.com/anomalyco/opencode")
                    context.startActivity(intent)
                }
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        color = SectionTitle,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsSliderRow(
    label: String,
    value: Float,
    valueText: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = BrightText, style = MaterialTheme.typography.bodyMedium)
            Text(valueText, color = AccentIndigo, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdownRow(
    label: String,
    currentValue: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = BrightText, modifier = Modifier.weight(1f))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange
        ) {
            Box(
                modifier = Modifier
                    .menuAnchor()
                    .clickable { onExpandedChange(true) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    currentValue,
                    color = SectionTitle,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { onSelect(option) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = BrightText, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsButtonRow(
    label: String,
    labelColor: Color = AccentIndigo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = labelColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsTextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = BrightText, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.width(100.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = BrightText)
        )
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = BrightText, modifier = Modifier.weight(1f))
        Text(value, color = SectionTitle, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SettingsClickableRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = BrightText, modifier = Modifier.weight(1f))
        Text(value, color = AccentIndigo, style = MaterialTheme.typography.bodyMedium)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = Color(0xFF64748B),
            modifier = Modifier.size(20.dp)
        )
    }
}
