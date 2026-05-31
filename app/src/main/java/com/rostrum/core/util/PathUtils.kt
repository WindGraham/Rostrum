package com.rostrum.core.util

import android.util.Log
import java.io.File

/**
 * 路径工具类
 * 
 * 提供统一的路径规范化方法，解决不同来源的文件路径格式不一致问题
 */
object PathUtils {
    
    private const val TAG = "PathUtils"
    
    /**
     * 规范化文件路径，确保返回绝对路径
     * 
     * @param path 原始路径（可能是相对路径或绝对路径）
     * @return 规范化后的绝对路径
     */
    fun normalizePath(path: String): String {
        return try {
            File(path).absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Failed to normalize path: $path, error: ${e.message}")
            path
        }
    }
    
    /**
     * 比较两个路径是否指向同一个文件
     * 
     * @param path1 第一个路径
     * @param path2 第二个路径
     * @return 如果两个路径指向同一个文件则返回 true
     */
    fun pathsEqual(path1: String, path2: String): Boolean {
        return try {
            normalizePath(path1) == normalizePath(path2)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compare paths: $path1, $path2, error: ${e.message}")
            path1 == path2
        }
    }
    
    /**
     * 获取文件的规范化路径（处理符号链接等）
     * 
     * @param path 原始路径
     * @return 规范化路径
     */
    fun getCanonicalPath(path: String): String {
        return try {
            File(path).canonicalPath
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get canonical path: $path, error: ${e.message}")
            normalizePath(path)
        }
    }
}
