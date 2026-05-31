package com.rostrum.ui.main

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.*

/**
 * 快捷操作功能定义
 */
data class QuickAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val action: () -> Unit
)

/**
 * 快捷操作界面
 * 
 * 显示扇形菜单，包含常用功能和"其他"选项
 */
@Composable
fun QuickActionsScreen(
    modifier: Modifier = Modifier,
    onActionSelected: (String) -> Unit = {}
) {
    var showMoreActionsDialog by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 扇形菜单
        RadialQuickMenu(
            commonActions = getCommonQuickActions(onActionSelected),
            onMoreClick = { showMoreActionsDialog = true }
        )
    }
    
    // 更多功能对话框
    if (showMoreActionsDialog) {
        MoreActionsDialog(
            onDismiss = { showMoreActionsDialog = false },
            onActionSelected = { actionId ->
                onActionSelected(actionId)
                showMoreActionsDialog = false
            }
        )
    }
}

/**
 * 获取常用快捷操作列表
 */
@Composable
fun getCommonQuickActions(onActionSelected: (String) -> Unit): List<QuickAction> {
    return listOf(
        QuickAction(
            id = "new_file",
            label = "新建文件",
            icon = Icons.Default.NoteAdd,
            color = Color(0xFF3B82F6),
            action = { onActionSelected("new_file") }
        ),
        QuickAction(
            id = "new_folder",
            label = "新建文件夹",
            icon = Icons.Default.CreateNewFolder,
            color = Color(0xFF10B981),
            action = { onActionSelected("new_folder") }
        ),
        QuickAction(
            id = "copy",
            label = "复制",
            icon = Icons.Default.ContentCopy,
            color = Color(0xFF6366F1),
            action = { onActionSelected("copy") }
        ),
        QuickAction(
            id = "paste",
            label = "粘贴",
            icon = Icons.Default.ContentPaste,
            color = Color(0xFFF59E0B),
            action = { onActionSelected("paste") }
        ),
        QuickAction(
            id = "search",
            label = "搜索",
            icon = Icons.Default.Search,
            color = Color(0xFFEC4899),
            action = { onActionSelected("search") }
        ),
        QuickAction(
            id = "bookmark",
            label = "添加书签",
            icon = Icons.Default.BookmarkAdd,
            color = Color(0xFF8B5CF6),
            action = { onActionSelected("bookmark") }
        )
    )
}

/**
 * 获取所有可用功能列表
 */
fun getAllQuickActions(onActionSelected: (String) -> Unit): List<QuickAction> {
    return listOf(
        // 文件操作
        QuickAction("new_file", "新建文件", Icons.Default.NoteAdd, Color(0xFF3B82F6), { onActionSelected("new_file") }),
        QuickAction("new_folder", "新建文件夹", Icons.Default.CreateNewFolder, Color(0xFF10B981), { onActionSelected("new_folder") }),
        QuickAction("copy", "复制", Icons.Default.ContentCopy, Color(0xFF6366F1), { onActionSelected("copy") }),
        QuickAction("cut", "剪切", Icons.Default.ContentCut, Color(0xFFEF4444), { onActionSelected("cut") }),
        QuickAction("paste", "粘贴", Icons.Default.ContentPaste, Color(0xFFF59E0B), { onActionSelected("paste") }),
        QuickAction("delete", "删除", Icons.Default.Delete, Color(0xFFDC2626), { onActionSelected("delete") }),
        QuickAction("rename", "重命名", Icons.Default.DriveFileRenameOutline, Color(0xFF7C3AED), { onActionSelected("rename") }),
        
        // 搜索和导航
        QuickAction("search", "搜索", Icons.Default.Search, Color(0xFFEC4899), { onActionSelected("search") }),
        QuickAction("bookmark", "添加书签", Icons.Default.BookmarkAdd, Color(0xFF8B5CF6), { onActionSelected("bookmark") }),
        QuickAction("bookmarks", "书签列表", Icons.Default.Bookmarks, Color(0xFF6366F1), { onActionSelected("bookmarks") }),
        QuickAction("history", "历史记录", Icons.Default.History, Color(0xFF64748B), { onActionSelected("history") }),
        
        // 网络功能
        QuickAction("network_discovery", "网络发现", Icons.Default.WifiFind, Color(0xFF06B6D4), { onActionSelected("network_discovery") }),
        QuickAction("sftp_connect", "SFTP连接", Icons.Default.CloudUpload, Color(0xFF10B981), { onActionSelected("sftp_connect") }),
        // FTP连接功能未实现，暂时隐藏
        // QuickAction("ftp_connect", "FTP连接", Icons.Default.Cloud, Color(0xFF3B82F6), { onActionSelected("ftp_connect") }),
        
        // 工具功能
        QuickAction("terminal", "终端", Icons.Default.Terminal, Color(0xFF1F2937), { onActionSelected("terminal") }),
        QuickAction("hex_editor", "Hex编辑器", Icons.Default.DataArray, Color(0xFF7C3AED), { onActionSelected("hex_editor") }),
        QuickAction("properties", "文件属性", Icons.Default.Info, Color(0xFF64748B), { onActionSelected("properties") }),
        
        // 其他
        QuickAction("settings", "设置", Icons.Default.Settings, Color(0xFF6B7280), { onActionSelected("settings") }),
        QuickAction("plugins", "插件管理", Icons.Default.Extension, Color(0xFF8B5CF6), { onActionSelected("plugins") }),
        QuickAction("about", "关于", Icons.Default.Info, Color(0xFF9CA3AF), { onActionSelected("about") })
    )
}

/**
 * 扇形快捷菜单
 */
@Composable
private fun RadialQuickMenu(
    commonActions: List<QuickAction>,
    onMoreClick: () -> Unit
) {
    val innerRadius = 40.dp
    val outerRadius = 180.dp
    val itemRadius = 30.dp
    
    val menuScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "menuScale"
    )
    
    val density = LocalDensity.current
    
    Box(
        modifier = Modifier.size(outerRadius * 2),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val distance = sqrt(dx * dx + dy * dy)
                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        if (angle < 0) angle += 360f
                        
                        val innerR = innerRadius.toPx() * menuScale
                        val outerR = outerRadius.toPx() * menuScale
                        
                        if (distance >= innerR && distance <= outerR) {
                            // 计算点击的是哪个扇形
                            val totalActions = commonActions.size + 1 // +1 for "更多"
                            val sectorAngle = 360f / totalActions
                            
                            val sectorIndex = ((angle + sectorAngle / 2) / sectorAngle).toInt() % totalActions
                            
                            if (sectorIndex < commonActions.size) {
                                commonActions[sectorIndex].action()
                            } else {
                                // 点击了"更多"
                                onMoreClick()
                            }
                        } else if (distance < innerR) {
                            // 点击了中心，关闭菜单
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val innerR = innerRadius.toPx() * menuScale
            val outerR = outerRadius.toPx() * menuScale
            
            val totalItems = commonActions.size + 1
            val sectorAngle = 360f / totalItems
            
            // 绘制扇形
            commonActions.forEachIndexed { index, action ->
                val startAngle = (index * sectorAngle - 90).toRadians()
                val sweepAngle = sectorAngle.toRadians()
                
                drawArc(
                    color = action.color.copy(alpha = 0.2f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - outerR, center.y - outerR),
                    size = androidx.compose.ui.geometry.Size(outerR * 2, outerR * 2)
                )
            }
            
            // 绘制"更多"扇形
            val moreIndex = commonActions.size
            val moreStartAngle = (moreIndex * sectorAngle - 90).toRadians()
            val moreSweepAngle = sectorAngle.toRadians()
            val moreColor = Color(0xFF6B7280)
            
            drawArc(
                color = moreColor.copy(alpha = 0.2f),
                startAngle = moreStartAngle,
                sweepAngle = moreSweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - outerR, center.y - outerR),
                size = androidx.compose.ui.geometry.Size(outerR * 2, outerR * 2)
            )
        }
        
        // 绘制图标和标签
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val totalItems = commonActions.size + 1
            val sectorAngle = 360f / totalItems
            val centerX = constraints.maxWidth / 2f
            val centerY = constraints.maxHeight / 2f
            val radiusPx = with(density) { (innerRadius + outerRadius).toPx() / 2 }
            
            commonActions.forEachIndexed { index, action ->
                val angle = (index * sectorAngle - 90).toRadians()
                val itemRadiusPx = with(density) { itemRadius.toPx() }
                val x = centerX + cos(angle) * radiusPx - itemRadiusPx
                val y = centerY + sin(angle) * radiusPx - itemRadiusPx
                
                Box(
                    modifier = Modifier
                        .offset(x = x.dp, y = y.dp)
                        .size(itemRadius * 2)
                        .background(
                            color = action.color,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.label,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = action.label,
                            fontSize = 8.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
            
            // "更多"按钮
            val moreAngle = (commonActions.size * sectorAngle - 90).toRadians()
            val itemRadiusPx = with(density) { itemRadius.toPx() }
            val moreX = centerX + cos(moreAngle) * radiusPx - itemRadiusPx
            val moreY = centerY + sin(moreAngle) * radiusPx - itemRadiusPx
            
            Box(
                modifier = Modifier
                    .offset(x = moreX.dp, y = moreY.dp)
                    .size(itemRadius * 2)
                    .background(
                        color = Color(0xFF6B7280),
                        shape = CircleShape
                    )
                    .clickable { onMoreClick() },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "更多",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "其他",
                        fontSize = 8.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
        
        // 中心圆
        Box(
            modifier = Modifier
                .size(innerRadius * 2)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.FlashOn,
                contentDescription = "快捷操作",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 更多功能对话框
 */
@Composable
fun MoreActionsDialog(
    onDismiss: () -> Unit,
    onActionSelected: (String) -> Unit
) {
    val allActions = getAllQuickActions(onActionSelected)
    val commonActionIds = getCommonQuickActions(onActionSelected).map { it.id }.toSet()
    val moreActions = allActions.filter { it.id !in commonActionIds }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "更多功能",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭"
                        )
                    }
                }
                
                Divider()
                
                // 功能列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(moreActions) { action ->
                        ActionItemCard(
                            action = action,
                            onClick = {
                                action.action()
                                onActionSelected(action.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 功能项卡片
 */
@Composable
private fun ActionItemCard(
    action: QuickAction,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = action.color.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = action.color,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = action.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * 角度转弧度
 */
private fun Float.toRadians(): Float = this * (PI.toFloat() / 180f)

