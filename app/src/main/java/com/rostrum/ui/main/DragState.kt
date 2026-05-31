package com.rostrum.ui.main

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.rostrum.core.domain.model.FileItem
import com.rostrum.ui.main.PanePosition

private const val TAG = "DragState"

/**
 * 拖动状态管理器
 * 
 * 拖动流程：
 * 1. 用户长按文件开始拖动 -> startDrag()
 * 2. 拖动过程中实时更新位置 -> updateDragPosition()
 * 3. MainScreen 根据位置检测目标区域 -> setDragTargetPaneAndType()
 * 4. CompactFileBrowserPane 检测是否悬停在文件夹上 -> setDragTarget()
 * 5. 用户松手 -> endDrag() -> 触发 DragHandler 回调
 */
object DragState {
    var draggedFile: FileItem? by mutableStateOf(null)
        private set
    
    var dragStartPosition: Offset by mutableStateOf(Offset.Zero)
        private set
    
    var dragCurrentPosition: Offset by mutableStateOf(Offset.Zero)
        private set
    
    var isDragging: Boolean by mutableStateOf(false)
        private set
    
    // 是否在取消区域内
    var isInCancelZone: Boolean by mutableStateOf(false)
        private set
    
    // 拖动到的具体文件夹路径（如果悬停在某个文件夹上）
    var dragTargetPath: String? by mutableStateOf(null)
        private set
    
    // 拖动目标区域
    var dragTargetPane: PanePosition? by mutableStateOf(null)
    
    // 目标区域的内容类型
    var dragTargetContentType: PaneContentType? by mutableStateOf(null)
    
    // 拖动来源区域
    var dragSourcePane: PanePosition? by mutableStateOf(null)
    
    /**
     * 开始拖动
     * @param file 被拖动的文件
     * @param startPosition 拖动起始位置（全局坐标）
     * @param sourcePane 来源区域
     */
    fun startDrag(file: FileItem, startPosition: Offset, sourcePane: PanePosition) {
        Log.d(TAG, "startDrag: file=${file.name}, sourcePane=$sourcePane")
        draggedFile = file
        dragStartPosition = startPosition
        dragCurrentPosition = startPosition
        isDragging = true
        dragTargetPane = null
        dragTargetContentType = null
        dragSourcePane = sourcePane
        dragTargetPath = null
    }
    
    /**
     * 更新拖动位置（全局坐标）
     */
    fun updateDragPosition(position: Offset) {
        dragCurrentPosition = position
    }
    
    /**
     * 获取拖动偏移量（相对于开始位置）
     */
    val dragOffset: Offset
        get() = dragCurrentPosition - dragStartPosition
    
    /**
     * 设置拖动目标路径（悬停在某个文件夹上时）
     */
    fun setDragTarget(path: String?) {
        if (dragTargetPath != path) {
            Log.d(TAG, "setDragTarget: $path")
            dragTargetPath = path
        }
    }
    
    /**
     * 设置拖动目标区域和内容类型
     */
    fun setDragTargetPaneAndType(pane: PanePosition?, contentType: PaneContentType?) {
        if (dragTargetPane != pane) {
            Log.d(TAG, "setDragTargetPane: $pane, contentType=$contentType")
        }
        dragTargetPane = pane
        dragTargetContentType = contentType
    }
    
    /**
     * 更新是否在取消区域内
     */
    fun updateCancelZone(inCancelZone: Boolean) {
        if (isInCancelZone != inCancelZone) {
            Log.d(TAG, "updateCancelZone: $inCancelZone")
            isInCancelZone = inCancelZone
        }
    }
    
    /**
     * 结束拖动并触发回调
     */
    fun endDrag() {
        val file = draggedFile
        val targetPath = dragTargetPath
        val targetPane = dragTargetPane
        val targetContentType = dragTargetContentType
        val sourcePane = dragSourcePane
        val wasCancelled = isInCancelZone
        
        Log.d(TAG, "endDrag: file=${file?.name}, targetPane=$targetPane, targetPath=$targetPath, sourcePane=$sourcePane, cancelled=$wasCancelled")
        
        // 先保存所有需要的值，再清理状态
        val dropInfo = if (file != null && targetPane != null && !wasCancelled) {
            DropInfo(file, targetPane, targetContentType, sourcePane, targetPath)
        } else {
            null
        }
        
        // 清理状态
        draggedFile = null
        dragStartPosition = Offset.Zero
        dragCurrentPosition = Offset.Zero
        isDragging = false
        dragTargetPath = null
        dragTargetPane = null
        dragTargetContentType = null
        dragSourcePane = null
        isInCancelZone = false
        
        // 触发回调（只有未取消时）
        dropInfo?.let {
            DragHandler.handlePaneDrop(it.file, it.targetPane, it.targetContentType, it.sourcePane, it.targetPath)
        }
    }
    
    /**
     * 取消拖动（不触发回调）
     */
    fun cancelDrag() {
        Log.d(TAG, "cancelDrag")
        draggedFile = null
        dragStartPosition = Offset.Zero
        dragCurrentPosition = Offset.Zero
        isDragging = false
        dragTargetPath = null
        dragTargetPane = null
        dragTargetContentType = null
        dragSourcePane = null
        isInCancelZone = false
    }
    
    private data class DropInfo(
        val file: FileItem,
        val targetPane: PanePosition,
        val targetContentType: PaneContentType?,
        val sourcePane: PanePosition?,
        val targetPath: String?
    )
}

/**
 * 拖动处理器 - 处理拖放完成后的操作
 */
object DragHandler {
    var onPaneDrop: ((FileItem, PanePosition, PaneContentType?, PanePosition?, String?) -> Unit)? = null
    
    fun handlePaneDrop(
        file: FileItem, 
        targetPane: PanePosition, 
        targetContentType: PaneContentType? = null,
        sourcePane: PanePosition? = null,
        targetPath: String? = null
    ) {
        Log.d(TAG, "handlePaneDrop: file=${file.name}, targetPane=$targetPane, targetPath=$targetPath")
        onPaneDrop?.invoke(file, targetPane, targetContentType, sourcePane, targetPath)
    }
}

