package com.rostrum.core.plugin.vscode

/**
 * VSCode Range类型
 */
data class VSCodeRange(
    val start: VSCodePosition,
    val end: VSCodePosition
)

/**
 * VSCode Position类型
 */
data class VSCodePosition(
    val line: Int,
    val character: Int
)

