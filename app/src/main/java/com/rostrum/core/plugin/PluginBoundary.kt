package com.rostrum.core.plugin

import com.rostrum.core.plugin.providers.FileEditorPlugin
import com.rostrum.core.plugin.providers.FilePreviewPlugin
import com.rostrum.core.plugin.providers.LanguageSupportPlugin
import com.rostrum.core.plugin.providers.ToolPlugin

/**
 * Narrow capability boundary exposed by a plugin.
 *
 * Keep this list small. New product integrations should be routed through one
 * of these boundaries before adding another plugin surface area.
 */
enum class PluginBoundary {
    PREVIEW,
    EDITOR,
    TOOL,
    LANGUAGE,
    OTHER
}

fun Plugin.boundaries(): Set<PluginBoundary> {
    val boundaries = linkedSetOf<PluginBoundary>()
    if (this is FilePreviewPlugin) boundaries += PluginBoundary.PREVIEW
    if (this is FileEditorPlugin) boundaries += PluginBoundary.EDITOR
    if (this is ToolPlugin) boundaries += PluginBoundary.TOOL
    if (this is LanguageSupportPlugin) boundaries += PluginBoundary.LANGUAGE
    if (boundaries.isEmpty()) boundaries += PluginBoundary.OTHER
    return boundaries
}

fun Plugin.primaryBoundary(): PluginBoundary = boundaries().first()
