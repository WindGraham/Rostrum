package com.termux.app.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * Pane binding manager - manages file browser to preview pane bindings.
 * When a file is clicked in a browser pane, the bound preview pane shows its content.
 */
object PaneBindingManager {
    private val _bindings = mutableStateOf<Map<PanePosition, PanePosition>>(emptyMap())

    /** Incremented on every change to trigger recomposition in UI */
    val bindingVersion = mutableIntStateOf(0)

    /**
     * Bind a file browser pane to a preview pane.
     * When a file is clicked in the filePane, the previewPane will show it.
     */
    fun bind(filePane: PanePosition, previewPane: PanePosition) {
        val current = _bindings.value.toMutableMap()
        // Remove any existing binding for this file pane
        current.entries.removeAll { it.key == filePane }
        // Remove any existing binding TO this preview pane
        current.entries.removeAll { it.value == previewPane }
        current[filePane] = previewPane
        _bindings.value = current
        bindingVersion.intValue++
    }

    /** Get the preview pane bound to a file pane */
    fun getBoundPreviewPane(filePane: PanePosition): PanePosition? = _bindings.value[filePane]

    /** Get the file pane bound to a preview pane */
    fun getBoundFilePane(previewPane: PanePosition): PanePosition? {
        return _bindings.value.entries.firstOrNull { it.value == previewPane }?.key
    }

    /** Unbind a file browser pane */
    fun unbind(filePane: PanePosition) {
        val current = _bindings.value.toMutableMap()
        current.remove(filePane)
        _bindings.value = current
        bindingVersion.intValue++
    }

    /** Clear all bindings */
    fun clearAll() {
        _bindings.value = emptyMap()
        bindingVersion.intValue++
    }
}
