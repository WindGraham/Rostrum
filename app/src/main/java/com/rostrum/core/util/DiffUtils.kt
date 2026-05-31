package com.rostrum.core.util

import java.io.File

enum class DiffType {
    SAME,       // 相同
    ADDED,      // 新增 (in B but not A)
    REMOVED,    // 删除 (in A but not B)
    MODIFIED    // 修改 (Just visual, logic usually treats as remove + add)
}

data class DiffLine(
    val type: DiffType,
    val content: String,
    val lineNoA: Int? = null, // 行号 (原文件)
    val lineNoB: Int? = null  // 行号 (新文件)
)

object DiffUtils {
    
    /**
     * 计算两个文本文件的差异（简化版 Myers Diff 或 LCS）
     * 这里为了实现简单且快速，使用 LCS (Longest Common Subsequence) 算法
     */
    fun computeDiff(fileA: File, fileB: File): List<DiffLine> {
        val linesA = try { fileA.readLines() } catch (e: Exception) { return listOf(DiffLine(DiffType.REMOVED, "Error reading file A: ${e.message}")) }
        val linesB = try { fileB.readLines() } catch (e: Exception) { return listOf(DiffLine(DiffType.ADDED, "Error reading file B: ${e.message}")) }
        
        // 使用简单的 LCS 算法
        val lcsMatrix = Array(linesA.size + 1) { IntArray(linesB.size + 1) }
        
        for (i in 1..linesA.size) {
            for (j in 1..linesB.size) {
                if (linesA[i - 1] == linesB[j - 1]) {
                    lcsMatrix[i][j] = lcsMatrix[i - 1][j - 1] + 1
                } else {
                    lcsMatrix[i][j] = maxOf(lcsMatrix[i - 1][j], lcsMatrix[i][j - 1])
                }
            }
        }
        
        // 回溯生成 Diff
        val diffs = mutableListOf<DiffLine>()
        var i = linesA.size
        var j = linesB.size
        
        val stack = java.util.Stack<DiffLine>()
        
        while (i > 0 && j > 0) {
            if (linesA[i - 1] == linesB[j - 1]) {
                stack.push(DiffLine(DiffType.SAME, linesA[i - 1], i, j))
                i--
                j--
            } else if (lcsMatrix[i - 1][j] > lcsMatrix[i][j - 1]) {
                stack.push(DiffLine(DiffType.REMOVED, linesA[i - 1], i, null))
                i--
            } else {
                stack.push(DiffLine(DiffType.ADDED, linesB[j - 1], null, j))
                j--
            }
        }
        
        while (i > 0) {
            stack.push(DiffLine(DiffType.REMOVED, linesA[i - 1], i, null))
            i--
        }
        
        while (j > 0) {
            stack.push(DiffLine(DiffType.ADDED, linesB[j - 1], null, j))
            j--
        }
        
        while (stack.isNotEmpty()) {
            diffs.add(stack.pop())
        }
        
        return diffs
    }
}