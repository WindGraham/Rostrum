package com.termux.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import kotlin.math.*

private val InnerRadius = 35.dp
private val InnerOuterRadius = 75.dp
private val OuterInnerRadius = 78.dp
private val OuterOuterRadius = 130.dp

@Composable
fun RadialMenuOverlay(
    isOpen: Boolean,
    selectedPane: PanePosition?,
    onPaneSelected: (PanePosition) -> Unit,
    onContentTypeSelected: (PaneContentType) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    val menuScale by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "menuScale"
    )

    val outerRingScale by animateFloatAsState(
        targetValue = if (selectedPane != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 350f),
        label = "outerRingScale"
    )

    var pressedPaneIndex by remember { mutableStateOf(-1) }
    var pressedContentIndex by remember { mutableStateOf(-1) }

    val contentTypes = remember {
        listOf(
            PaneContentType.FILE_BROWSER,
            PaneContentType.PREVIEW,
            PaneContentType.EDITOR,
            PaneContentType.TERMINAL
        )
    }

    data class PaneSectorDef(
        val pane: PanePosition,
        val startAngle: Float,
        val sweepAngle: Float,
        val color: Color
    )

    val paneSectors = remember {
        listOf(
            PaneSectorDef(PanePosition.TOP_LEFT, 180f, 90f, Color(0xFF6366F1)),
            PaneSectorDef(PanePosition.TOP_RIGHT, 270f, 90f, Color(0xFF10B981)),
            PaneSectorDef(PanePosition.BOTTOM, 0f, 180f, Color(0xFFF59E0B))
        )
    }

    if (menuScale <= 0.01f) return

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val canvasSize = OuterOuterRadius * 2

        Canvas(
            modifier = Modifier
                .size(canvasSize)
                .pointerInput(isOpen, selectedPane) {
                    detectTapGestures(
                        onPress = { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dx = offset.x - center.x
                            val dy = offset.y - center.y
                            val distance = sqrt(dx * dx + dy * dy)
                            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            if (angle < 0) angle += 360f

                            val innerR = InnerRadius.toPx() * menuScale
                            val innerOuterR = InnerOuterRadius.toPx() * menuScale
                            val outerInnerR = OuterInnerRadius.toPx() * outerRingScale
                            val outerOuterR = OuterOuterRadius.toPx() * outerRingScale

                            if (distance >= innerR && distance <= innerOuterR) {
                                paneSectors.forEachIndexed { index, sector ->
                                    if (isAngleInSector(angle, sector.startAngle, sector.sweepAngle)) {
                                        pressedPaneIndex = index
                                    }
                                }
                            } else if (selectedPane != null && distance >= outerInnerR && distance <= outerOuterR) {
                                val sweepPerItem = 360f / contentTypes.size
                                contentTypes.forEachIndexed { index, _ ->
                                    val startAngle = (270f + index * sweepPerItem) % 360f
                                    if (isAngleInSector(angle, startAngle, sweepPerItem)) {
                                        pressedContentIndex = index
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

                            val innerR = InnerRadius.toPx() * menuScale
                            val innerOuterR = InnerOuterRadius.toPx() * menuScale
                            val outerInnerR = OuterInnerRadius.toPx() * outerRingScale
                            val outerOuterR = OuterOuterRadius.toPx() * outerRingScale

                            if (distance >= innerR && distance <= innerOuterR) {
                                Log.d("RadialMenuOverlay", "Inner ring tap: angle=$angle, distance=$distance")
                                paneSectors.forEach { sector ->
                                    if (isAngleInSector(angle, sector.startAngle, sector.sweepAngle)) {
                                        Log.d("RadialMenuOverlay", "Selected pane: ${sector.pane}")
                                        onPaneSelected(sector.pane)
                                    }
                                }
                            } else if (selectedPane != null && distance >= outerInnerR && distance <= outerOuterR) {
                                Log.d("RadialMenuOverlay", "Outer ring tap: angle=$angle, distance=$distance")
                                val sweepPerItem = 360f / contentTypes.size
                                contentTypes.forEachIndexed { index, contentType ->
                                    val startAngle = (270f + index * sweepPerItem) % 360f
                                    if (isAngleInSector(angle, startAngle, sweepPerItem)) {
                                        Log.d("RadialMenuOverlay", "Selected content type: $contentType")
                                        onContentTypeSelected(contentType)
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val innerR = InnerRadius.toPx() * menuScale
            val innerOuterR = InnerOuterRadius.toPx() * menuScale
            val outerInnerR = OuterInnerRadius.toPx() * outerRingScale
            val outerOuterR = OuterOuterRadius.toPx() * outerRingScale

            paneSectors.forEachIndexed { index, sector ->
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
                    gapAngle = 2f
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
                        gapAngle = 2f
                    )
                }
            }

            if (selectedPane != null && outerRingScale > 0.01f) {
                val baseColor = when (selectedPane) {
                    PanePosition.TOP_LEFT -> Color(0xFF6366F1)
                    PanePosition.TOP_RIGHT -> Color(0xFF10B981)
                    PanePosition.BOTTOM -> Color(0xFFF59E0B)
                }

                val sweepPerItem = 360f / contentTypes.size
                contentTypes.forEachIndexed { index, _ ->
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
            }

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

        if (menuScale > 0.5f) {
            paneSectors.forEach { sector ->
                val midAngle = sector.startAngle + sector.sweepAngle / 2
                val midRadius = (InnerRadius + InnerOuterRadius) / 2
                val iconXDp = with(density) {
                    (midRadius.toPx() * menuScale * cos(Math.toRadians(midAngle.toDouble()))).toFloat().toDp()
                }
                val iconYDp = with(density) {
                    (midRadius.toPx() * menuScale * sin(Math.toRadians(midAngle.toDouble()))).toFloat().toDp()
                }

                Box(
                    modifier = Modifier
                        .size(canvasSize)
                        .offset(x = iconXDp, y = iconYDp),
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

        if (selectedPane != null && outerRingScale > 0.5f) {
            val sweepPerItem = 360f / contentTypes.size
            contentTypes.forEachIndexed { index, contentType ->
                val startAngle = (270f + index * sweepPerItem) % 360f
                val midAngle = startAngle + sweepPerItem / 2
                val midRadius = (OuterInnerRadius + OuterOuterRadius) / 2
                val iconXDp = with(density) {
                    (midRadius.toPx() * outerRingScale * cos(Math.toRadians(midAngle.toDouble()))).toFloat().toDp()
                }
                val iconYDp = with(density) {
                    (midRadius.toPx() * outerRingScale * sin(Math.toRadians(midAngle.toDouble()))).toFloat().toDp()
                }

                Box(
                    modifier = Modifier
                        .size(canvasSize)
                        .offset(x = iconXDp, y = iconYDp),
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
            rect = Rect(
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
            rect = Rect(
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
            rect = Rect(
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
            rect = Rect(
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
