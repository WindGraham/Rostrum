package com.rostrum.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 用户协议和隐私政策同意弹窗
 * 
 * 首次打开应用时强制弹出，用户必须同意后才能使用应用。
 * 不可通过返回键或点击外部关闭。
 */
@Composable
fun AgreementDialog(
    onAgree: () -> Unit
) {
    var checked by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showUserAgreement by remember { mutableStateOf(false) }
    
    // 子弹窗：隐私政策
    if (showPrivacy) {
        AgreementContentDialog(
            title = "隐私政策",
            content = PRIVACY_POLICY_TEXT,
            onDismiss = { showPrivacy = false }
        )
    }
    
    // 子弹窗：用户协议
    if (showUserAgreement) {
        AgreementContentDialog(
            title = "用户协议",
            content = USER_AGREEMENT_TEXT,
            onDismiss = { showUserAgreement = false }
        )
    }
    
    Dialog(
        onDismissRequest = { /* 不允许关闭 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题
                Text(
                    text = "欢迎使用万灵驭手",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "OmniMaster",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 说明文字
                Text(
                    text = "在使用本应用之前，请您仔细阅读并同意以下协议：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 协议链接
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showUserAgreement = true }) {
                        Text(
                            text = "《用户协议》",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline
                        )
                    }
                    
                    Text(
                        text = " 和 ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    TextButton(onClick = { showPrivacy = true }) {
                        Text(
                            text = "《隐私政策》",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 勾选框
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { checked = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "我已阅读并同意以上协议",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 同意按钮
                Button(
                    onClick = onAgree,
                    enabled = checked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "同意并继续",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 协议内容查看弹窗
 */
@Composable
private fun AgreementContentDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

// ===== 协议内容 =====

private const val PRIVACY_POLICY_TEXT = """
隐私政策
最后更新日期：2026年2月

万灵驭手（OmniMaster）尊重并保护您的隐私。本隐私政策说明我们如何处理您的信息。

1. 信息收集
万灵驭手不会收集、存储或传输您的个人信息到任何服务器。所有数据都存储在您的设备本地。

2. 本地数据存储
应用配置和连接信息存储在您的设备本地，不会主动上传到任何服务器。

3. 文件访问
应用需要文件访问权限以提供文件管理功能。我们不会读取、上传或分享您的任何文件内容。

4. 网络访问
网络权限仅用于：
• SSH/SFTP 远程连接（您主动配置的服务器）
• 检查应用更新

5. 第三方服务
当您主动配置并连接远程服务器时，相关网络通信仅用于完成您发起的连接、文件传输和终端操作。

6. 联系我们
如果您对隐私政策有任何疑问，请通过 windgraham@foxmail.com 联系我们。
"""

private const val USER_AGREEMENT_TEXT = """
用户协议
最后更新日期：2026年2月

欢迎使用万灵驭手（OmniMaster）。使用本应用即表示您同意以下条款。

1. 服务说明
万灵驭手是一款面向 Android 平台的文件管理应用，提供文件管理、远程连接、终端模拟器、代码编辑等功能。

2. 使用条款
• 请勿使用本应用执行任何违法操作或破坏系统安全
• 终端功能具有系统级权限，请谨慎使用

3. 免责声明
• 本应用按「现状」提供，不做任何明示或暗示的保证
• 因使用本应用导致的数据丢失、系统损坏等问题，开发者不承担责任
• 文件操作（删除、移动等）不可逆，请确认后再执行

4. 知识产权
本应用的源代码、设计和文档受知识产权法保护。本应用使用了多个开源组件，详见「开源许可」页面。

5. 协议变更
我们保留随时修改本协议的权利。继续使用本应用即表示您接受修改后的协议条款。

6. 联系方式
如有疑问，请通过 windgraham@foxmail.com 联系我们。
"""
