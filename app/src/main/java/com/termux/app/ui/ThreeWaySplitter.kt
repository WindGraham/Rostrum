package com.termux.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.*
import android.util.Log

/**
 * 区域内容类型 - 扩展版
 */
enum class PaneContentType(val label: String, val icon: ImageVector) {
    EMPTY("空白", Icons.Default.CheckBoxOutlineBlank),  // 空白区域
    FILE_BROWSER("文件", Icons.Default.Folder),
    PREVIEW("预览", Icons.Default.Visibility),
    EDITOR("编辑", Icons.Default.Edit),
    TERMINAL("终端", Icons.Default.Terminal),
    REMOTE("远程", Icons.Default.Computer),
    BUILD("构建", Icons.Default.Build),
    HEX_VIEW("Hex", Icons.Default.DataArray),
    BOOKMARKS("书签", Icons.Default.Bookmark),       // 原SEARCH，改为书签管理
    PROPERTIES("属性", Icons.Default.Info),
    MIGRATE("迁移", Icons.Default.DriveFileMove),    // 原CLIPBOARD，改为迁移功能
    HISTORY("历史", Icons.Default.History),
    PLUGINS("插件", Icons.Default.Extension),
    QUICK_ACTIONS("快捷", Icons.Default.FlashOn)     // 快捷操作
}

/**
 * 区域标识
 */
enum class PanePosition(val label: String, val icon: ImageVector) {
    TOP_LEFT("左上", Icons.Default.NorthWest),
    TOP_RIGHT("右上", Icons.Default.NorthEast),
    BOTTOM("下方", Icons.Default.South)
}

// 边缘距离阈值（增大以便更早隐藏）
private val EDGE_THRESHOLD = 50.dp
// 小杆子长度
private val STEM_LENGTH = 20.dp
// 小球距离边缘的最小距离（增大隐藏距离）
private val MIN_EDGE_DISTANCE = 25.dp

/**
 * 三区域可调整分割器
 */
@Composable
fun ThreeWaySplitter(
    modifier: Modifier = Modifier,
    topLeftContent: @Composable BoxScope.() -> Unit,
    topRightContent: @Composable BoxScope.() -> Unit,
    bottomContent: @Composable BoxScope.() -> Unit,
    initialVerticalWeight: Float = 0.5f,
    initialHorizontalWeight: Float = 0.5f,
    hideThreshold: Float = 0.05f,
    dividerThickness: Dp = 10.dp,
    ballSize: Dp = 28.dp,
    developerMode: Boolean = false,  // 开发者模式：控制圆盘菜单显示的选项数量
    onPaneContentChange: ((PanePosition, PaneContentType) -> Unit)? = null,
    onAddBookmark: ((PanePosition) -> Unit)? = null,
    onLoadBookmark: ((PanePosition) -> Unit)? = null,
    onWeightChange: ((Float, Float) -> Unit)? = null,
    // 快捷操作回调
    onNewFile: ((PanePosition) -> Unit)? = null,
    onNewFolder: ((PanePosition) -> Unit)? = null,
    onPaste: ((PanePosition) -> Unit)? = null,
    onOpenTerminal: (() -> Unit)? = null,
    onRefresh: ((PanePosition) -> Unit)? = null,
    onSearch: ((PanePosition) -> Unit)? = null,
    onSort: ((PanePosition) -> Unit)? = null,
    // 扩展快捷操作回调
    onCopy: ((PanePosition) -> Unit)? = null,
    onCut: ((PanePosition) -> Unit)? = null,
    onDelete: ((PanePosition) -> Unit)? = null,
    onRename: ((PanePosition) -> Unit)? = null,
    onBookmark: ((PanePosition) -> Unit)? = null,
    onShowBookmarks: (() -> Unit)? = null,
    onShowHistory: (() -> Unit)? = null,
    onNetworkDiscovery: (() -> Unit)? = null,
    onSftpConnect: (() -> Unit)? = null,
    onFtpConnect: (() -> Unit)? = null,
    onHexEditor: ((PanePosition) -> Unit)? = null,
    onProperties: ((PanePosition) -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onPlugins: (() -> Unit)? = null,
    onAbout: (() -> Unit)? = null,
    // 外部触发打开菜单的回调注册器 - 接收一个函数，该函数可用于打开菜单并选中指定窗口
    provideOpenMenuCallback: ((openMenuForPane: (PanePosition) -> Unit) -> Unit)? = null
) {
    var verticalWeight by remember { mutableStateOf(initialVerticalWeight) }
    var horizontalWeight by remember { mutableStateOf(initialHorizontalWeight) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    
    // 通知外部当前比例
    LaunchedEffect(verticalWeight, horizontalWeight) {
        onWeightChange?.invoke(verticalWeight, horizontalWeight)
    }
    
    // 圆盘菜单状态
    var showRadialMenu by remember { mutableStateOf(false) }
    var selectedPane by remember { mutableStateOf<PanePosition?>(null) }
    
    // 向外部提供打开菜单的方法
    LaunchedEffect(Unit) {
        provideOpenMenuCallback?.invoke { pane ->
            selectedPane = pane
            showRadialMenu = true
        }
    }
    
    val density = LocalDensity.current
    
    // 计算各区域的可见性
    val showTopLeft = horizontalWeight > hideThreshold && verticalWeight > hideThreshold
    val showTopRight = horizontalWeight < (1f - hideThreshold) && verticalWeight > hideThreshold
    val showBottom = verticalWeight < (1f - hideThreshold)
    val showTopRow = verticalWeight > hideThreshold
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        val containerWidth = with(density) { containerSize.width.toDp() }
        val containerHeight = with(density) { containerSize.height.toDp() }
        
        if (containerSize.width > 0 && containerSize.height > 0) {
            val effectiveVerticalWeight = verticalWeight.coerceIn(0f, 1f)
            val effectiveHorizontalWeight = horizontalWeight.coerceIn(0f, 1f)
            
            val topHeight = containerHeight * effectiveVerticalWeight
            val bottomHeight = containerHeight * (1f - effectiveVerticalWeight)
            val leftWidth = containerWidth * effectiveHorizontalWeight
            val rightWidth = containerWidth * (1f - effectiveHorizontalWeight)
            
            // 计算小球的原始位置
            val originalBallX = containerWidth * effectiveHorizontalWeight - ballSize / 2
            val originalBallY = containerHeight * effectiveVerticalWeight - ballSize / 2
            
            // 检测是否贴边
            val isNearLeftEdge = originalBallX < EDGE_THRESHOLD
            val isNearRightEdge = originalBallX > containerWidth - ballSize - EDGE_THRESHOLD
            val isNearTopEdge = originalBallY < EDGE_THRESHOLD
            val isNearBottomEdge = originalBallY > containerHeight - ballSize - EDGE_THRESHOLD
            val isNearEdge = isNearLeftEdge || isNearRightEdge || isNearTopEdge || isNearBottomEdge
            
            // 调整后的小球位置（贴边时保持距离）
            val adjustedBallX = when {
                isNearLeftEdge -> MIN_EDGE_DISTANCE
                isNearRightEdge -> containerWidth - ballSize - MIN_EDGE_DISTANCE
                else -> originalBallX
            }.coerceIn(0.dp, containerWidth - ballSize)
            
            val adjustedBallY = when {
                isNearTopEdge -> MIN_EDGE_DISTANCE
                isNearBottomEdge -> containerHeight - ballSize - MIN_EDGE_DISTANCE
                else -> originalBallY
            }.coerceIn(0.dp, containerHeight - ballSize)
            
            // 屏幕中央位置
            val centerX = containerWidth / 2 - ballSize / 2
            val centerY = containerHeight / 2 - ballSize / 2
            
            // 小球飞行动画 - 只有菜单打开时才飞到中央
            val ballXAnimation by animateDpAsState(
                targetValue = if (showRadialMenu) centerX else adjustedBallX,
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = 300f
                ),
                label = "ballX"
            )
            
            val ballYAnimation by animateDpAsState(
                targetValue = if (showRadialMenu) centerY else adjustedBallY,
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = 300f
                ),
                label = "ballY"
            )
            
            // 小球缩放动画
            val animatedBallScale by animateFloatAsState(
                targetValue = when {
                    showRadialMenu -> 1.1f
                    isDragging -> 1.2f
                    else -> 1f
                },
                animationSpec = spring(dampingRatio = 0.7f),
                label = "ballScale"
            )
            
            // ========== 上方区域行 ==========
            if (showTopRow) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topHeight - if (showBottom) dividerThickness / 2 else 0.dp)
                        .align(Alignment.TopStart)
                ) {
                    AnimatedVisibility(
                        visible = showTopLeft,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(150))
                    ) {
                        Box(
                            modifier = Modifier
                                .width(if (showTopRight) leftWidth - dividerThickness / 2 else containerWidth)
                                .fillMaxHeight()
                        ) {
                            topLeftContent()
                        }
                    }
                    
                    if (showTopLeft && showTopRight) {
                        VerticalDividerBar(
                            modifier = Modifier
                                .width(dividerThickness)
                                .fillMaxHeight(),
                            isDragging = isDragging,
                            onDrag = { deltaX ->
                                val weightDelta = deltaX / containerWidth.value
                                horizontalWeight = (horizontalWeight + weightDelta).coerceIn(0f, 1f)
                            }
                        )
                    }
                    
                    AnimatedVisibility(
                        visible = showTopRight,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(150))
                    ) {
                        Box(
                            modifier = Modifier
                                .width(if (showTopLeft) rightWidth - dividerThickness / 2 else containerWidth)
                                .fillMaxHeight()
                        ) {
                            topRightContent()
                        }
                    }
                }
            }
            
            // ========== 水平分割线 ==========
            if (showTopRow && showBottom) {
                HorizontalDividerBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dividerThickness)
                        .offset(x = 0.dp, y = topHeight - dividerThickness / 2),
                    isDragging = isDragging,
                    onDrag = { deltaY ->
                        val weightDelta = deltaY / containerHeight.value
                        verticalWeight = (verticalWeight + weightDelta).coerceIn(0f, 1f)
                    }
                )
            }
            
            // ========== 下方区域 ==========
            AnimatedVisibility(
                visible = showBottom,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (showTopRow) bottomHeight - dividerThickness / 2 else containerHeight)
                ) {
                    bottomContent()
                }
            }
            
            // ========== 小杆子（贴边时显示）==========
            if (isNearEdge && !showRadialMenu) {
                BallStem(
                    ballX = adjustedBallX,
                    ballY = adjustedBallY,
                    ballSize = ballSize,
                    originalX = originalBallX,
                    originalY = originalBallY,
                    isNearLeftEdge = isNearLeftEdge,
                    isNearRightEdge = isNearRightEdge,
                    isNearTopEdge = isNearTopEdge,
                    isNearBottomEdge = isNearBottomEdge,
                    containerWidth = containerWidth,
                    containerHeight = containerHeight
                )
            }
            
            // ========== 圆盘菜单遮罩 ==========
            if (showRadialMenu) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showRadialMenu = false
                            selectedPane = null
                        }
                        .zIndex(99f)
                )
            }
            
            // ========== 圆盘菜单（固定在屏幕中央）==========
            if (showRadialMenu) {
                Box(
                    modifier = Modifier
                        .offset(x = centerX, y = centerY)
                        .zIndex(100f)
                ) {
                    UnifiedRadialMenu(
                        isVisible = true,
                        selectedPane = selectedPane,
                        showTopLeft = showTopLeft,
                        showTopRight = showTopRight,
                        showBottom = showBottom,
                        ballSize = ballSize,
                        developerMode = developerMode,
                        onPaneSelect = { pane ->
                            selectedPane = if (selectedPane == pane) null else pane
                        },
                        onContentSelect = { pane, contentType ->
                            onPaneContentChange?.invoke(pane, contentType)
                            showRadialMenu = false
                            selectedPane = null
                        },
                        onAddBookmark = onAddBookmark,
                        onLoadBookmark = onLoadBookmark,
                        onQuickAction = { actionId ->
                            // 执行快捷操作
                            val targetPane = selectedPane
                            when (actionId) {
                                // 文件操作
                                "new_file" -> targetPane?.let { onNewFile?.invoke(it) }
                                "new_folder" -> targetPane?.let { onNewFolder?.invoke(it) }
                                "copy" -> targetPane?.let { onCopy?.invoke(it) }
                                "cut" -> targetPane?.let { onCut?.invoke(it) }
                                "paste" -> targetPane?.let { onPaste?.invoke(it) }
                                "delete" -> targetPane?.let { onDelete?.invoke(it) }
                                "rename" -> targetPane?.let { onRename?.invoke(it) }
                                // 搜索和导航
                                "search" -> targetPane?.let { onSearch?.invoke(it) }
                                "bookmark" -> targetPane?.let { onBookmark?.invoke(it) }
                                "bookmarks" -> onShowBookmarks?.invoke()
                                "history" -> onShowHistory?.invoke()
                                // 网络功能
                                "network_discovery" -> onNetworkDiscovery?.invoke()
                                "sftp_connect" -> onSftpConnect?.invoke()
                                "ftp_connect" -> onFtpConnect?.invoke()
                                // 工具功能
                                "terminal" -> onOpenTerminal?.invoke()
                                "hex_editor" -> targetPane?.let { onHexEditor?.invoke(it) }
                                "properties" -> targetPane?.let { onProperties?.invoke(it) }
                                // 刷新和排序
                                "refresh" -> targetPane?.let { onRefresh?.invoke(it) }
                                "sort" -> targetPane?.let { onSort?.invoke(it) }
                                // 其他
                                "settings" -> onSettings?.invoke()
                                "plugins" -> onPlugins?.invoke()
                                "about" -> onAbout?.invoke()
                            }
                            showRadialMenu = false
                            selectedPane = null
                        }
                    )
                }
            }
            
            // ========== 可拖动圆球 ==========
            // 添加更大的透明触摸热区，使控制球更容易点中
            val touchPadding = 12.dp  // 额外触摸区域
            Box(
                modifier = Modifier
                    .offset(
                        x = ballXAnimation - touchPadding,
                        y = ballYAnimation - touchPadding
                    )
                    .size(ballSize * animatedBallScale + touchPadding * 2)
                    .zIndex(101f),
                contentAlignment = Alignment.Center
            ) {
                DraggableBall(
                    modifier = Modifier.size(ballSize * animatedBallScale),
                    isDragging = isDragging,
                    isMenuOpen = showRadialMenu,
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDrag = { deltaX, deltaY ->
                        val hDelta = deltaX / containerWidth.value
                        val vDelta = deltaY / containerHeight.value
                        horizontalWeight = (horizontalWeight + hDelta).coerceIn(0f, 1f)
                        verticalWeight = (verticalWeight + vDelta).coerceIn(0f, 1f)
                    },
                    onClick = {
                        if (!showRadialMenu) {
                            showRadialMenu = true
                        }
                    },
                    touchPadding = touchPadding
                )
            }
        }
    }
}

/**
 * 小杆子 - 连接小球和边缘的锚点
 */
@Composable
private fun BallStem(
    ballX: Dp,
    ballY: Dp,
    ballSize: Dp,
    originalX: Dp,
    originalY: Dp,
    isNearLeftEdge: Boolean,
    isNearRightEdge: Boolean,
    isNearTopEdge: Boolean,
    isNearBottomEdge: Boolean,
    containerWidth: Dp,
    containerHeight: Dp
) {
    val density = LocalDensity.current
    
    // 预计算锚点和绘制区域，限制 Canvas 大小避免全屏重绘
    val ballCenterXDp = ballX + ballSize / 2
    val ballCenterYDp = ballY + ballSize / 2
    val anchorXDp: Dp
    val anchorYDp: Dp
    when {
        isNearLeftEdge -> { anchorXDp = 0.dp; anchorYDp = ballCenterYDp }
        isNearRightEdge -> { anchorXDp = containerWidth; anchorYDp = ballCenterYDp }
        isNearTopEdge -> { anchorXDp = ballCenterXDp; anchorYDp = 0.dp }
        isNearBottomEdge -> { anchorXDp = ballCenterXDp; anchorYDp = containerHeight }
        else -> return
    }
    val stemLeft = minOf(anchorXDp, ballCenterXDp) - 8.dp
    val stemTop = minOf(anchorYDp, ballCenterYDp) - 8.dp
    val stemWidth = (maxOf(anchorXDp, ballCenterXDp) - minOf(anchorXDp, ballCenterXDp)) + 16.dp
    val stemHeight = (maxOf(anchorYDp, ballCenterYDp) - minOf(anchorYDp, ballCenterYDp)) + 16.dp
    
    Canvas(
        modifier = Modifier
            .offset(x = stemLeft, y = stemTop)
            .size(width = stemWidth, height = stemHeight)
            .zIndex(98f)
    ) {
        val ballCenterX = with(density) { (ballCenterXDp - stemLeft).toPx() }
        val ballCenterY = with(density) { (ballCenterYDp - stemTop).toPx() }
        val anchorX = with(density) { (anchorXDp - stemLeft).toPx() }
        val anchorY = with(density) { (anchorYDp - stemTop).toPx() }
        
        // 绘制小杆子
        val stemColor = Color(0xFF6366F1).copy(alpha = 0.6f)
        
        // 杆子主体
        drawLine(
            color = stemColor,
            start = Offset(anchorX, anchorY),
            end = Offset(ballCenterX, ballCenterY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        
        // 锚点圆点
        drawCircle(
            color = stemColor,
            radius = 6.dp.toPx(),
            center = Offset(anchorX, anchorY)
        )
        
        // 锚点内圈
        drawCircle(
            color = Color.White.copy(alpha = 0.5f),
            radius = 3.dp.toPx(),
            center = Offset(anchorX, anchorY)
        )
    }
}

/**
 * 统一圆盘菜单
 */
@Composable
private fun UnifiedRadialMenu(
    isVisible: Boolean,
    selectedPane: PanePosition?,
    showTopLeft: Boolean,
    showTopRight: Boolean,
    showBottom: Boolean,
    ballSize: Dp,
    developerMode: Boolean = false,
    onPaneSelect: (PanePosition) -> Unit,
    onContentSelect: (PanePosition, PaneContentType) -> Unit,
    onAddBookmark: ((PanePosition) -> Unit)? = null,
    onLoadBookmark: ((PanePosition) -> Unit)? = null,
    onQuickAction: ((String) -> Unit)? = null
) {
    val innerRadius = 35.dp
    val innerOuterRadius = 75.dp
    // 简洁模式下外层圆环更小更紧凑
    val outerInnerRadius = if (developerMode) 82.dp else 78.dp
    val outerOuterRadius = if (developerMode) 145.dp else 130.dp
    
    val menuScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "menuScale"
    )
    
    val outerRingScale by animateFloatAsState(
        targetValue = if (selectedPane != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 350f),
        label = "outerRingScale"
    )
    
    data class PaneSectorDef(
        val pane: PanePosition,
        val startAngle: Float,
        val sweepAngle: Float,
        val color: Color,
        val visible: Boolean
    )
    
    val paneSectors = listOf(
        PaneSectorDef(PanePosition.TOP_LEFT, 180f, 90f, Color(0xFF6366F1), showTopLeft),
        PaneSectorDef(PanePosition.TOP_RIGHT, 270f, 90f, Color(0xFF10B981), showTopRight),
        PaneSectorDef(PanePosition.BOTTOM, 0f, 180f, Color(0xFFF59E0B), showBottom)
    )
    
    // 小球菜单只负责切换窗格内容，不承载主机/构建这类工作区级入口。
    val contentTypes = if (developerMode) {
        listOf(
            PaneContentType.FILE_BROWSER,
            PaneContentType.PREVIEW,
            PaneContentType.EDITOR,
            PaneContentType.TERMINAL,
            PaneContentType.HEX_VIEW,
            PaneContentType.BOOKMARKS,      // 书签管理
            PaneContentType.HISTORY,        // 历史记录（已实现）
            PaneContentType.PROPERTIES,     // 文件属性（已实现）
            // PaneContentType.MIGRATE,     // 未实现，暂时隐藏
            PaneContentType.PLUGINS,        // 插件管理
            PaneContentType.QUICK_ACTIONS   // 快捷操作
        )
    } else {
        // 简洁模式：仅保留核心功能
        listOf(
            PaneContentType.FILE_BROWSER,
            PaneContentType.PREVIEW,
            PaneContentType.EDITOR,
            PaneContentType.TERMINAL
        )
    }
    
    val density = LocalDensity.current
    var pressedPaneIndex by remember { mutableStateOf(-1) }
    var pressedContentIndex by remember { mutableStateOf(-1) }
    
    // 书签子菜单状态
    var showBookmarkSubMenu by remember { mutableStateOf(false) }
    var bookmarkSubMenuPane by remember { mutableStateOf<PanePosition?>(null) }
    
    // 快捷操作对话框状态
    var showQuickActionsDialog by remember { mutableStateOf(false) }
    
    // 书签子菜单半径
    val bookmarkSubRadius = 160.dp
    val bookmarkSubOuterRadius = 195.dp
    
    if (menuScale > 0.01f) {
        Box(
            modifier = Modifier
                .offset(
                    x = ballSize / 2 - outerOuterRadius,
                    y = ballSize / 2 - outerOuterRadius
                )
                .size(outerOuterRadius * 2),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isVisible, selectedPane, showBookmarkSubMenu) {
                        detectTapGestures(
                            onPress = { offset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val dx = offset.x - center.x
                                val dy = offset.y - center.y
                                val distance = sqrt(dx * dx + dy * dy)
                                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                if (angle < 0) angle += 360f
                                
                                val innerR = innerRadius.toPx() * menuScale
                                val innerOuterR = innerOuterRadius.toPx() * menuScale
                                val outerInnerR = outerInnerRadius.toPx() * outerRingScale
                                val outerOuterR = outerOuterRadius.toPx() * outerRingScale
                                
                                if (distance >= innerR && distance <= innerOuterR) {
                                    paneSectors.forEachIndexed { index, sector ->
                                        if (sector.visible && isAngleInSector(angle, sector.startAngle, sector.sweepAngle)) {
                                            pressedPaneIndex = index
                                        }
                                    }
                                } else if (selectedPane != null) {
                                    // 检查书签子菜单
                                    val bookmarkSubInnerR = bookmarkSubRadius.toPx()
                                    val bookmarkSubOuterR = bookmarkSubOuterRadius.toPx()
                                    if (showBookmarkSubMenu && distance >= bookmarkSubInnerR && distance <= bookmarkSubOuterR) {
                                        val sweepPerItem = 360f / contentTypes.size
                                        val bookmarkIndex = contentTypes.indexOf(PaneContentType.BOOKMARKS)
                                        val bookmarkCenterAngle = (270f + bookmarkIndex * sweepPerItem + sweepPerItem / 2) % 360f
                                        val subSweep = 25f
                                        val subGap = 4f
                                        
                                        // 添加书签弧形 (-15度)
                                        val addBookmarkStart = bookmarkCenterAngle - subSweep - subGap/2
                                        if (isAngleInSector(angle, addBookmarkStart, subSweep)) {
                                            pressedContentIndex = -2 // 标记为添加书签
                                        }
                                        // 加载书签弧形 (+15度)
                                        val loadBookmarkStart = bookmarkCenterAngle + subGap/2
                                        if (isAngleInSector(angle, loadBookmarkStart, subSweep)) {
                                            pressedContentIndex = -3 // 标记为加载书签
                                        }
                                    }
                                    
                                    // 检查是否点击了外层圆环的主项
                                    if (distance >= outerInnerR && distance <= outerOuterR) {
                                        val sweepPerItem = 360f / contentTypes.size
                                        contentTypes.forEachIndexed { index, contentType ->
                                            val startAngle = (270f + index * sweepPerItem) % 360f
                                            if (isAngleInSector(angle, startAngle, sweepPerItem)) {
                                                pressedContentIndex = index
                                            }
                                        }
                                    }
                                }
                                
                                tryAwaitRelease()
                                pressedPaneIndex = -1
                                pressedContentIndex = -1
                            },
                            onTap = { offset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val dx = offset.x - center.x
                                val dy = offset.y - center.y
                                val distance = sqrt(dx * dx + dy * dy)
                                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                if (angle < 0) angle += 360f
                                
                                val innerR = innerRadius.toPx() * menuScale
                                val innerOuterR = innerOuterRadius.toPx() * menuScale
                                val outerInnerR = outerInnerRadius.toPx() * outerRingScale
                                val outerOuterR = outerOuterRadius.toPx() * outerRingScale
                                
                                if (distance >= innerR && distance <= innerOuterR) {
                                    Log.d("QuickActions", "========== Inner Ring Tap ==========")
                                    Log.d("QuickActions", "Click position: x=${offset.x}, y=${offset.y}")
                                    Log.d("QuickActions", "Distance: $distance, Angle: $angle")
                                    paneSectors.forEach { sector ->
                                        if (sector.visible && isAngleInSector(angle, sector.startAngle, sector.sweepAngle)) {
                                            Log.d("QuickActions", "Selected pane: ${sector.pane}, current selectedPane: $selectedPane")
                                            // 如果点击的是已选中的pane，则取消选择；否则选择新pane
                                            val newSelectedPane = if (selectedPane == sector.pane) null else sector.pane
                                            
                                            // 如果切换pane，需要检查子菜单状态
                                            if (newSelectedPane != selectedPane && newSelectedPane != null) {
                                                // 如果新选择的pane和子菜单的pane不一致，关闭子菜单
                                                if (showBookmarkSubMenu && bookmarkSubMenuPane != newSelectedPane) {
                                                    showBookmarkSubMenu = false
                                                    bookmarkSubMenuPane = null
                                                }
                                            }
                                            onPaneSelect(sector.pane)
                                        }
                                    }
                                } else if (selectedPane != null) {
                                    // 检查书签子菜单
                                    val bookmarkSubInnerR = bookmarkSubRadius.toPx()
                                    val bookmarkSubOuterR = bookmarkSubOuterRadius.toPx()
                                    
                                    if (showBookmarkSubMenu && distance >= bookmarkSubInnerR && distance <= bookmarkSubOuterR) {
                                        val sweepPerItem = 360f / contentTypes.size
                                        val bookmarkIndex = contentTypes.indexOf(PaneContentType.BOOKMARKS)
                                        val bookmarkCenterAngle = (270f + bookmarkIndex * sweepPerItem + sweepPerItem / 2) % 360f
                                        val subSweep = 25f
                                        val subGap = 4f
                                        
                                        // 添加书签弧形 (-15度)
                                        val addBookmarkStart = bookmarkCenterAngle - subSweep - subGap/2
                                        if (isAngleInSector(angle, addBookmarkStart, subSweep)) {
                                            onAddBookmark?.invoke(selectedPane)
                                            showBookmarkSubMenu = false
                                            bookmarkSubMenuPane = null
                                            return@detectTapGestures
                                        }
                                        // 加载书签弧形 (+15度)
                                        val loadBookmarkStart = bookmarkCenterAngle + subGap/2
                                        if (isAngleInSector(angle, loadBookmarkStart, subSweep)) {
                                            onLoadBookmark?.invoke(selectedPane)
                                            showBookmarkSubMenu = false
                                            bookmarkSubMenuPane = null
                                            return@detectTapGestures
                                        }
                                    }
                                    
                                    // 检查是否点击了外层圆环的主项
                                    if (distance >= outerInnerR && distance <= outerOuterR) {
                                        Log.d("QuickActions", "========== Outer Ring Tap ==========")
                                        Log.d("QuickActions", "Click position: x=${offset.x}, y=${offset.y}")
                                        Log.d("QuickActions", "Distance: $distance, Angle: $angle")
                                        val sweepPerItem = 360f / contentTypes.size
                                        contentTypes.forEachIndexed { index, contentType ->
                                            val startAngle = (270f + index * sweepPerItem) % 360f
                                            val endAngle = startAngle + sweepPerItem
                                            val isInSector = isAngleInSector(angle, startAngle, sweepPerItem)
                                            Log.d("QuickActions", "Outer item[$index] ${contentType.label}: start=$startAngle, end=$endAngle, angle=$angle, isInSector=$isInSector")
                                            if (isInSector) {
                                                Log.d("QuickActions", "✓✓✓ Clicked outer ring item: contentType=$contentType, index=$index ✓✓✓")
                                                if (contentType == PaneContentType.BOOKMARKS) {
                                                    // 点击书签主项，展开/收起子菜单
                                                    showBookmarkSubMenu = !showBookmarkSubMenu
                                                    bookmarkSubMenuPane = if (showBookmarkSubMenu) selectedPane else null
                                                    Log.d("QuickActions", "Bookmark submenu toggled: $showBookmarkSubMenu")
                                                } else if (contentType == PaneContentType.QUICK_ACTIONS) {
                                                    // 点击快捷操作主项，直接打开对话框
                                                    showQuickActionsDialog = true
                                                    // 关闭书签子菜单（如果打开）
                                                    showBookmarkSubMenu = false
                                                    bookmarkSubMenuPane = null
                                                } else {
                                                    // 关闭所有子菜单
                                                    showBookmarkSubMenu = false
                                                    bookmarkSubMenuPane = null
                                                    onContentSelect(selectedPane, contentType)
                                                }
                                            }
                                        }
                                    } else {
                                        Log.d("QuickActions", "Not in outer ring: distance=$distance, outerInnerR=$outerInnerR, outerOuterR=$outerOuterR")
                                    }
                                }
                            }
                        )
                    }
            ) {
                val centerX = size.width / 2
                val centerY = size.height / 2
                val innerR = innerRadius.toPx() * menuScale
                val innerOuterR = innerOuterRadius.toPx() * menuScale
                val outerInnerR = outerInnerRadius.toPx() * outerRingScale
                val outerOuterR = outerOuterRadius.toPx() * outerRingScale
                
                // 绘制内层扇形
                paneSectors.forEachIndexed { index, sector ->
                    if (sector.visible) {
                        val isSelected = selectedPane == sector.pane
                        val isPressed = pressedPaneIndex == index
                        val alpha = when {
                            isSelected -> 1f
                            isPressed -> 0.9f
                            else -> 0.75f
                        }
                        
                        drawSector(
                            centerX = centerX,
                            centerY = centerY,
                            innerRadius = innerR,
                            outerRadius = innerOuterR,
                            startAngle = sector.startAngle,
                            sweepAngle = sector.sweepAngle,
                            color = sector.color.copy(alpha = alpha),
                            gapAngle = 4f
                        )
                        
                        if (isSelected) {
                            drawSectorStroke(
                                centerX = centerX,
                                centerY = centerY,
                                innerRadius = innerR,
                                outerRadius = innerOuterR,
                                startAngle = sector.startAngle,
                                sweepAngle = sector.sweepAngle,
                                color = Color.White,
                                strokeWidth = 3.dp.toPx(),
                                gapAngle = 4f
                            )
                        }
                    }
                }
                
                // 绘制外层圆环
                if (selectedPane != null && outerRingScale > 0.01f) {
                    val baseColor = when (selectedPane) {
                        PanePosition.TOP_LEFT -> Color(0xFF6366F1)
                        PanePosition.TOP_RIGHT -> Color(0xFF10B981)
                        PanePosition.BOTTOM -> Color(0xFFF59E0B)
                    }
                    
                    val sweepPerItem = 360f / contentTypes.size
                    contentTypes.forEachIndexed { index, contentType ->
                        val startAngle = (270f + index * sweepPerItem) % 360f
                        val isPressed = pressedContentIndex == index
                        val itemAlpha = if (isPressed) 0.9f else 0.55f + 0.15f * ((index % 4) / 4f)
                        
                        drawSector(
                            centerX = centerX,
                            centerY = centerY,
                            innerRadius = outerInnerR,
                            outerRadius = outerOuterR,
                            startAngle = startAngle,
                            sweepAngle = sweepPerItem,
                            color = baseColor.copy(alpha = itemAlpha),
                            gapAngle = 2f
                        )
                        
                        if (isPressed) {
                            drawSectorStroke(
                                centerX = centerX,
                                centerY = centerY,
                                innerRadius = outerInnerR,
                                outerRadius = outerOuterR,
                                startAngle = startAngle,
                                sweepAngle = sweepPerItem,
                                color = Color.White,
                                strokeWidth = 2.dp.toPx(),
                                gapAngle = 2f
                            )
                        }
                    }
                    
                    // 绘制书签子菜单弧形
                    if (showBookmarkSubMenu && bookmarkSubMenuPane == selectedPane) {
                        val bookmarkIndex = contentTypes.indexOf(PaneContentType.BOOKMARKS)
                        val bookmarkCenterAngle = (270f + bookmarkIndex * sweepPerItem + sweepPerItem / 2) % 360f
                        val subSweep = 25f
                        val subGap = 4f
                        
                        // 添加书签弧形 (-15度) - 绿色
                        val addBookmarkStart = bookmarkCenterAngle - subSweep - subGap/2
                        val isAddBookmarkPressed = pressedContentIndex == -2
                        drawSector(
                            centerX = centerX,
                            centerY = centerY,
                            innerRadius = bookmarkSubRadius.toPx(),
                            outerRadius = bookmarkSubOuterRadius.toPx(),
                            startAngle = addBookmarkStart,
                            sweepAngle = subSweep,
                            color = Color(0xFF10B981).copy(alpha = if (isAddBookmarkPressed) 0.9f else 0.7f),
                            gapAngle = 2f
                        )
                        if (isAddBookmarkPressed) {
                            drawSectorStroke(
                                centerX = centerX,
                                centerY = centerY,
                                innerRadius = bookmarkSubRadius.toPx(),
                                outerRadius = bookmarkSubOuterRadius.toPx(),
                                startAngle = addBookmarkStart,
                                sweepAngle = subSweep,
                                color = Color.White,
                                strokeWidth = 2.dp.toPx(),
                                gapAngle = 2f
                            )
                        }
                        
                        // 加载书签弧形 (+15度) - 紫色
                        val loadBookmarkStart = bookmarkCenterAngle + subGap/2
                        val isLoadBookmarkPressed = pressedContentIndex == -3
                        drawSector(
                            centerX = centerX,
                            centerY = centerY,
                            innerRadius = bookmarkSubRadius.toPx(),
                            outerRadius = bookmarkSubOuterRadius.toPx(),
                            startAngle = loadBookmarkStart,
                            sweepAngle = subSweep,
                            color = Color(0xFF6366F1).copy(alpha = if (isLoadBookmarkPressed) 0.9f else 0.7f),
                            gapAngle = 2f
                        )
                        if (isLoadBookmarkPressed) {
                            drawSectorStroke(
                                centerX = centerX,
                                centerY = centerY,
                                innerRadius = bookmarkSubRadius.toPx(),
                                outerRadius = bookmarkSubOuterRadius.toPx(),
                                startAngle = loadBookmarkStart,
                                sweepAngle = subSweep,
                                color = Color.White,
                                strokeWidth = 2.dp.toPx(),
                                gapAngle = 2f
                            )
                        }
                    }
                    
                }
                
                // 中心圆
                drawCircle(
                    color = Color(0xFF1E1E2E),
                    radius = innerR - 3.dp.toPx(),
                    center = Offset(centerX, centerY)
                )
                drawCircle(
                    color = Color(0xFF6366F1).copy(alpha = 0.4f),
                    radius = 5.dp.toPx() * menuScale,
                    center = Offset(centerX, centerY)
                )
            }
            
            // 内层图标
            if (menuScale > 0.5f) {
                paneSectors.forEach { sector ->
                    if (sector.visible) {
                        val midAngle = sector.startAngle + sector.sweepAngle / 2
                        val midRadius = (innerRadius + innerOuterRadius) / 2
                        val iconX = with(density) {
                            (midRadius.toPx() * menuScale * cos(Math.toRadians(midAngle.toDouble()))).toFloat().toDp()
                        }
                        val iconY = with(density) {
                            (midRadius.toPx() * menuScale * sin(Math.toRadians(midAngle.toDouble()))).toFloat().toDp()
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(x = iconX, y = iconY),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.alpha(menuScale)
                            ) {
                                Icon(
                                    imageVector = sector.pane.icon,
                                    contentDescription = sector.pane.label,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = sector.pane.label,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            
            // 外层图标
            if (selectedPane != null && outerRingScale > 0.5f) {
                val sweepPerItem = 360f / contentTypes.size
                contentTypes.forEachIndexed { index, contentType ->
                    val startAngle = (270f + index * sweepPerItem) % 360f
                    val midAngle = startAngle + sweepPerItem / 2
                    val midRadius = (outerInnerRadius + outerOuterRadius) / 2
                    val iconX = with(density) {
                        (midRadius.toPx() * outerRingScale * cos(Math.toRadians(midAngle.toDouble()))).toFloat().toDp()
                    }
                    val iconY = with(density) {
                        (midRadius.toPx() * outerRingScale * sin(Math.toRadians(midAngle.toDouble()))).toFloat().toDp()
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(x = iconX, y = iconY),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.alpha(outerRingScale)
                        ) {
                            Icon(
                                imageVector = contentType.icon,
                                contentDescription = contentType.label,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = contentType.label,
                                color = Color.White,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                // 书签子菜单图标
                if (showBookmarkSubMenu && bookmarkSubMenuPane == selectedPane) {
                    val bookmarkIndex = contentTypes.indexOf(PaneContentType.BOOKMARKS)
                    val bookmarkCenterAngle = (270f + bookmarkIndex * sweepPerItem + sweepPerItem / 2) % 360f
                    val subSweep = 25f
                    val subGap = 4f
                    val subMidRadius = (bookmarkSubRadius + bookmarkSubOuterRadius) / 2
                    
                    // 添加书签图标
                    val addBookmarkMidAngle = bookmarkCenterAngle - subSweep / 2 - subGap / 2
                    val addIconX = with(density) {
                        (subMidRadius.toPx() * cos(Math.toRadians(addBookmarkMidAngle.toDouble()))).toFloat().toDp()
                    }
                    val addIconY = with(density) {
                        (subMidRadius.toPx() * sin(Math.toRadians(addBookmarkMidAngle.toDouble()))).toFloat().toDp()
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(x = addIconX, y = addIconY),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.alpha(outerRingScale)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = "添加书签",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "添加",
                                color = Color.White,
                                fontSize = 6.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    // 加载书签图标
                    val loadBookmarkMidAngle = bookmarkCenterAngle + subSweep / 2 + subGap / 2
                    val loadIconX = with(density) {
                        (subMidRadius.toPx() * cos(Math.toRadians(loadBookmarkMidAngle.toDouble()))).toFloat().toDp()
                    }
                    val loadIconY = with(density) {
                        (subMidRadius.toPx() * sin(Math.toRadians(loadBookmarkMidAngle.toDouble()))).toFloat().toDp()
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(x = loadIconX, y = loadIconY),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.alpha(outerRingScale)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkBorder,
                                contentDescription = "加载书签",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "加载",
                                color = Color.White,
                                fontSize = 6.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
            }
        }
        
        // 快捷操作对话框 (placeholder - will be implemented later)
        if (showQuickActionsDialog) {
            showQuickActionsDialog = false
        }
    }
}

private fun isAngleInSector(angle: Float, startAngle: Float, sweepAngle: Float): Boolean {
    val normalizedAngle = ((angle % 360f) + 360f) % 360f
    val normalizedStart = ((startAngle % 360f) + 360f) % 360f
    val endAngle = normalizedStart + sweepAngle
    
    return if (endAngle <= 360f) {
        normalizedAngle >= normalizedStart && normalizedAngle < endAngle
    } else {
        normalizedAngle >= normalizedStart || normalizedAngle < (endAngle - 360f)
    }
}

private fun DrawScope.drawSector(
    centerX: Float,
    centerY: Float,
    innerRadius: Float,
    outerRadius: Float,
    startAngle: Float,
    sweepAngle: Float,
    color: Color,
    gapAngle: Float = 2f
) {
    val adjustedStart = startAngle + gapAngle / 2
    val adjustedSweep = sweepAngle - gapAngle
    
    val path = Path().apply {
        val startRad = Math.toRadians(adjustedStart.toDouble())
        val endRad = Math.toRadians((adjustedStart + adjustedSweep).toDouble())
        
        moveTo(
            centerX + outerRadius * cos(startRad).toFloat(),
            centerY + outerRadius * sin(startRad).toFloat()
        )
        
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                centerX - outerRadius,
                centerY - outerRadius,
                centerX + outerRadius,
                centerY + outerRadius
            ),
            startAngleDegrees = adjustedStart,
            sweepAngleDegrees = adjustedSweep,
            forceMoveTo = false
        )
        
        lineTo(
            centerX + innerRadius * cos(endRad).toFloat(),
            centerY + innerRadius * sin(endRad).toFloat()
        )
        
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                centerX - innerRadius,
                centerY - innerRadius,
                centerX + innerRadius,
                centerY + innerRadius
            ),
            startAngleDegrees = adjustedStart + adjustedSweep,
            sweepAngleDegrees = -adjustedSweep,
            forceMoveTo = false
        )
        
        close()
    }
    
    drawPath(path, color, style = Fill)
}

private fun DrawScope.drawSectorStroke(
    centerX: Float,
    centerY: Float,
    innerRadius: Float,
    outerRadius: Float,
    startAngle: Float,
    sweepAngle: Float,
    color: Color,
    strokeWidth: Float,
    gapAngle: Float = 2f
) {
    val adjustedStart = startAngle + gapAngle / 2
    val adjustedSweep = sweepAngle - gapAngle
    
    val path = Path().apply {
        val startRad = Math.toRadians(adjustedStart.toDouble())
        val endRad = Math.toRadians((adjustedStart + adjustedSweep).toDouble())
        
        moveTo(
            centerX + outerRadius * cos(startRad).toFloat(),
            centerY + outerRadius * sin(startRad).toFloat()
        )
        
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                centerX - outerRadius,
                centerY - outerRadius,
                centerX + outerRadius,
                centerY + outerRadius
            ),
            startAngleDegrees = adjustedStart,
            sweepAngleDegrees = adjustedSweep,
            forceMoveTo = false
        )
        
        lineTo(
            centerX + innerRadius * cos(endRad).toFloat(),
            centerY + innerRadius * sin(endRad).toFloat()
        )
        
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                centerX - innerRadius,
                centerY - innerRadius,
                centerX + innerRadius,
                centerY + innerRadius
            ),
            startAngleDegrees = adjustedStart + adjustedSweep,
            sweepAngleDegrees = -adjustedSweep,
            forceMoveTo = false
        )
        
        close()
    }
    
    drawPath(path, color, style = Stroke(width = strokeWidth))
}

@Composable
private fun DraggableBall(
    modifier: Modifier = Modifier,
    isDragging: Boolean,
    isMenuOpen: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (deltaX: Float, deltaY: Float) -> Unit,
    onClick: () -> Unit,
    touchPadding: Dp = 0.dp  // 额外触摸区域参数
) {
    val density = LocalDensity.current
    var dragDistance by remember { mutableStateOf(0f) }
    
    val ballColor by animateColorAsState(
        targetValue = when {
            isMenuOpen -> MaterialTheme.colorScheme.primary
            isDragging -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(150),
        label = "ballColor"
    )
    
    Box(
        modifier = modifier
            .shadow(
                elevation = if (isDragging || isMenuOpen) 8.dp else 4.dp,
                shape = CircleShape
            )
            .clip(CircleShape)
            .background(ballColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // 增大点击识别阈值，更容易点中
                        if (dragDistance < 25f) {
                            onClick()
                        }
                        dragDistance = 0f
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { _ ->
                        dragDistance = 0f
                        onDragStart()
                    },
                    onDragEnd = {
                        onDragEnd()
                    },
                    onDragCancel = {},
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragDistance += sqrt((dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y).toDouble()).toFloat()
                        val deltaX = with(density) { dragAmount.x.toDp().value }
                        val deltaY = with(density) { dragAmount.y.toDp().value }
                        onDrag(deltaX, deltaY)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val iconColor = when {
                isMenuOpen -> Color.White
                isDragging -> Color.White
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            }
            
            Box(
                modifier = Modifier
                    .size(4.dp, 2.dp)
                    .background(iconColor, shape = CircleShape)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(2.dp, 4.dp)
                        .background(iconColor, shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(iconColor, shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(2.dp, 4.dp)
                        .background(iconColor, shape = CircleShape)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(4.dp, 2.dp)
                    .background(iconColor, shape = CircleShape)
            )
        }
    }
}

@Composable
private fun HorizontalDividerBar(
    modifier: Modifier = Modifier,
    isDragging: Boolean,
    onDrag: (deltaY: Float) -> Unit
) {
    val density = LocalDensity.current

    val defaultColor = Color(0xFF1E293B)
    val dragColor = Color(0xFF6366F1)
    val animatedBg by animateColorAsState(
        targetValue = if (isDragging) dragColor else defaultColor,
        animationSpec = tween(200),
        label = "hDivBg"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.3f else 0f,
        animationSpec = tween(200),
        label = "hDivGlow"
    )

    Surface(
        modifier = modifier
            .drawBehind {
                if (glowAlpha > 0.01f) {
                    drawCircle(
                        color = dragColor.copy(alpha = glowAlpha),
                        radius = size.maxDimension,
                        center = Offset(size.width / 2, size.height / 2)
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val deltaY = with(density) { dragAmount.y.toDp().value }
                    onDrag(deltaY)
                }
            },
        color = animatedBg
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(5) {
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(2.dp)
                            .background(
                                if (isDragging) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun VerticalDividerBar(
    modifier: Modifier = Modifier,
    isDragging: Boolean,
    onDrag: (deltaX: Float) -> Unit
) {
    val density = LocalDensity.current

    val defaultColor = Color(0xFF1E293B)
    val dragColor = Color(0xFF6366F1)
    val animatedBg by animateColorAsState(
        targetValue = if (isDragging) dragColor else defaultColor,
        animationSpec = tween(200),
        label = "vDivBg"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.3f else 0f,
        animationSpec = tween(200),
        label = "vDivGlow"
    )

    Surface(
        modifier = modifier
            .drawBehind {
                if (glowAlpha > 0.01f) {
                    drawCircle(
                        color = dragColor.copy(alpha = glowAlpha),
                        radius = size.maxDimension,
                        center = Offset(size.width / 2, size.height / 2)
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val deltaX = with(density) { dragAmount.x.toDp().value }
                    onDrag(deltaX)
                }
            },
        color = animatedBg
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(5) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(8.dp)
                            .background(
                                if (isDragging) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}
