package com.rostrum.ui.main

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val TAG = "PaneBindingManager"

/**
 * 窗口绑定关系（文件管理窗口 -> 预览窗口）
 * @param filePane 文件管理窗口
 * @param previewPane 预览窗口
 */
data class PaneBinding(
    val filePane: PanePosition,
    val previewPane: PanePosition
)

/**
 * 编辑器-预览绑定关系
 * @param editorPane 编辑器窗口
 * @param previewPane 预览窗口
 */
data class EditorPreviewBinding(
    val editorPane: PanePosition,
    val previewPane: PanePosition
)

/**
 * 窗口绑定管理器
 * 
 * 管理以下绑定关系：
 * 1. 文件管理窗口与预览窗口的绑定
 * 2. 编辑器窗口与预览窗口的绑定
 * 
 * 绑定规则：
 * - 每个文件管理窗口最多只有一个绑定关系
 * - 点击文件时，在绑定的预览窗口中预览
 * - 拖动文件到预览窗口时，建立新的绑定关系
 */
object PaneBindingManager {
    
    // 所有绑定关系（文件管理窗口 -> 预览窗口）
    private var bindings by mutableStateOf<Map<PanePosition, PanePosition>>(emptyMap())
    
    // 编辑器-预览绑定关系（编辑器窗口 -> 预览窗口）
    private var editorPreviewBindings by mutableStateOf<Map<PanePosition, PanePosition>>(emptyMap())
    
    // 绑定版本号，用于UI更新检测
    var bindingVersion by mutableStateOf(0)
        private set
    
    /**
     * 手动增加绑定版本号，强制触发UI重组
     * 用于窗口类型切换时确保组件正确重建
     */
    fun incrementBindingVersion() {
        bindingVersion++
        Log.d(TAG, "Binding version incremented to: $bindingVersion")
    }
    
    /**
     * 获取文件管理窗口绑定的预览窗口
     */
    fun getBoundPreviewPane(filePane: PanePosition): PanePosition? {
        return bindings[filePane]
    }
    
    /**
     * 获取预览窗口被哪个文件管理窗口绑定
     */
    fun getBoundFilePane(previewPane: PanePosition): PanePosition? {
        return bindings.entries.find { it.value == previewPane }?.key
    }
    
    /**
     * 检查文件管理窗口是否有绑定
     */
    fun hasBinding(filePane: PanePosition): Boolean {
        return bindings.containsKey(filePane)
    }
    
    /**
     * 检查预览窗口是否被绑定
     */
    fun isPreviewPaneBound(previewPane: PanePosition): Boolean {
        return bindings.values.contains(previewPane)
    }
    
    /**
     * 建立绑定关系
     * 
     * @param filePane 文件管理窗口
     * @param previewPane 预览窗口
     * @param clearOldBindings 是否清除旧绑定（默认为true）
     */
    fun bind(filePane: PanePosition, previewPane: PanePosition, clearOldBindings: Boolean = true) {
        Log.d(TAG, "bind: filePane=$filePane, previewPane=$previewPane")
        
        val newBindings = bindings.toMutableMap()
        
        if (clearOldBindings) {
            // 移除 filePane 的旧绑定
            newBindings.remove(filePane)
            
            // 移除其他窗口对 previewPane 的绑定
            val oldFilePane = newBindings.entries.find { it.value == previewPane }?.key
            if (oldFilePane != null && oldFilePane != filePane) {
                Log.d(TAG, "Removing old binding: $oldFilePane -> $previewPane")
                newBindings.remove(oldFilePane)
            }
        }
        
        // 建立新绑定
        newBindings[filePane] = previewPane
        bindings = newBindings
        bindingVersion++
        
        Log.d(TAG, "Current bindings: $bindings")
    }
    
    /**
     * 解除文件管理窗口的绑定
     */
    fun unbind(filePane: PanePosition) {
        Log.d(TAG, "unbind: filePane=$filePane")
        
        if (bindings.containsKey(filePane)) {
            val newBindings = bindings.toMutableMap()
            newBindings.remove(filePane)
            bindings = newBindings
            bindingVersion++
        }
    }
    
    /**
     * 解除预览窗口的绑定
     */
    fun unbindPreviewPane(previewPane: PanePosition) {
        Log.d(TAG, "unbindPreviewPane: previewPane=$previewPane")
        
        val filePane = bindings.entries.find { it.value == previewPane }?.key
        if (filePane != null) {
            val newBindings = bindings.toMutableMap()
            newBindings.remove(filePane)
            bindings = newBindings
            bindingVersion++
        }
    }
    
    /**
     * 处理拖动预览：建立来源窗口与目标预览窗口的绑定
     * 
     * @param sourcePane 文件来源的文件管理窗口
     * @param targetPreviewPane 目标预览窗口
     */
    fun handleDragPreview(sourcePane: PanePosition, targetPreviewPane: PanePosition) {
        Log.d(TAG, "handleDragPreview: sourcePane=$sourcePane, targetPreviewPane=$targetPreviewPane")
        
        // 解除来源窗口的旧绑定
        unbind(sourcePane)
        
        // 建立新绑定
        bind(sourcePane, targetPreviewPane)
    }
    
    /**
     * 为文件管理窗口找一个可用的预览窗口
     * 
     * @param filePane 文件管理窗口
     * @param availablePreviewPanes 当前可用的预览窗口列表
     * @return 可用的预览窗口，如果没有返回null
     */
    fun findAvailablePreviewPane(
        filePane: PanePosition,
        availablePreviewPanes: List<PanePosition>
    ): PanePosition? {
        // 首先检查是否已有绑定
        val existingBinding = getBoundPreviewPane(filePane)
        if (existingBinding != null && availablePreviewPanes.contains(existingBinding)) {
            return existingBinding
        }
        
        // 查找未被绑定的预览窗口
        val unboundPane = availablePreviewPanes.find { !isPreviewPaneBound(it) }
        if (unboundPane != null) {
            return unboundPane
        }
        
        // 如果所有预览窗口都被绑定，使用第一个可用的
        return availablePreviewPanes.firstOrNull()
    }
    
    /**
     * 获取所有绑定关系（用于调试或UI显示）
     */
    fun getAllBindings(): List<PaneBinding> {
        return bindings.map { (filePane, previewPane) ->
            PaneBinding(filePane, previewPane)
        }
    }
    
    /**
     * 清除所有绑定
     */
    fun clearAll() {
        Log.d(TAG, "clearAll")
        bindings = emptyMap()
        editorPreviewBindings = emptyMap()
        bindingVersion++
    }
    
    // ==================== 编辑器-预览绑定管理 ====================
    
    /**
     * 绑定编辑器窗口到预览窗口
     * 
     * @param editorPane 编辑器窗口
     * @param previewPane 预览窗口
     */
    fun bindEditorToPreview(editorPane: PanePosition, previewPane: PanePosition) {
        Log.d(TAG, "bindEditorToPreview: editorPane=$editorPane, previewPane=$previewPane")
        
        val newBindings = editorPreviewBindings.toMutableMap()
        
        // 移除 editorPane 的旧绑定
        newBindings.remove(editorPane)
        
        // 移除其他编辑器对 previewPane 的绑定
        val oldEditorPane = newBindings.entries.find { it.value == previewPane }?.key
        if (oldEditorPane != null && oldEditorPane != editorPane) {
            Log.d(TAG, "Removing old editor binding: $oldEditorPane -> $previewPane")
            newBindings.remove(oldEditorPane)
        }
        
        // 建立新绑定
        newBindings[editorPane] = previewPane
        editorPreviewBindings = newBindings
        bindingVersion++
        
        Log.d(TAG, "Current editor-preview bindings: $editorPreviewBindings")
    }
    
    /**
     * 获取编辑器窗口绑定的预览窗口
     */
    fun getEditorBoundPreviewPane(editorPane: PanePosition): PanePosition? {
        return editorPreviewBindings[editorPane]
    }
    
    /**
     * 获取预览窗口绑定的编辑器窗口
     */
    fun getBoundEditorPane(previewPane: PanePosition): PanePosition? {
        return editorPreviewBindings.entries.find { it.value == previewPane }?.key
    }
    
    /**
     * 检查编辑器窗口是否有绑定的预览窗口
     */
    fun hasEditorBinding(editorPane: PanePosition): Boolean {
        return editorPreviewBindings.containsKey(editorPane)
    }
    
    /**
     * 检查预览窗口是否被编辑器绑定
     */
    fun isPreviewBoundByEditor(previewPane: PanePosition): Boolean {
        return editorPreviewBindings.values.contains(previewPane)
    }
    
    /**
     * 解除编辑器窗口的预览绑定
     */
    fun unbindEditor(editorPane: PanePosition) {
        Log.d(TAG, "unbindEditor: editorPane=$editorPane")
        
        if (editorPreviewBindings.containsKey(editorPane)) {
            val newBindings = editorPreviewBindings.toMutableMap()
            newBindings.remove(editorPane)
            editorPreviewBindings = newBindings
            bindingVersion++
        }
    }
    
    /**
     * 解除预览窗口的编辑器绑定
     */
    fun unbindEditorFromPreview(previewPane: PanePosition) {
        Log.d(TAG, "unbindEditorFromPreview: previewPane=$previewPane")
        
        val editorPane = editorPreviewBindings.entries.find { it.value == previewPane }?.key
        if (editorPane != null) {
            val newBindings = editorPreviewBindings.toMutableMap()
            newBindings.remove(editorPane)
            editorPreviewBindings = newBindings
            bindingVersion++
        }
    }
    
    /**
     * 获取所有编辑器-预览绑定关系
     */
    fun getAllEditorPreviewBindings(): List<EditorPreviewBinding> {
        return editorPreviewBindings.map { (editorPane, previewPane) ->
            EditorPreviewBinding(editorPane, previewPane)
        }
    }
    
    /**
     * 为编辑器窗口找一个可用的预览窗口
     * 
     * @param editorPane 编辑器窗口
     * @param availablePreviewPanes 当前可用的预览窗口列表
     * @return 可用的预览窗口，如果没有返回null
     */
    fun findAvailablePreviewPaneForEditor(
        editorPane: PanePosition,
        availablePreviewPanes: List<PanePosition>
    ): PanePosition? {
        // 首先检查是否已有绑定
        val existingBinding = getEditorBoundPreviewPane(editorPane)
        if (existingBinding != null && availablePreviewPanes.contains(existingBinding)) {
            return existingBinding
        }
        
        // 查找未被编辑器绑定的预览窗口
        val unboundPane = availablePreviewPanes.find { !isPreviewBoundByEditor(it) }
        if (unboundPane != null) {
            return unboundPane
        }
        
        // 如果所有预览窗口都被绑定，使用第一个可用的
        return availablePreviewPanes.firstOrNull()
    }
}
