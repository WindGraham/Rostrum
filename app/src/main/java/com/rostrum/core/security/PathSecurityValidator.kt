package com.rostrum.core.security

import java.io.File
import java.io.IOException

/**
 * 路径安全验证器 - 防止路径遍历攻击
 */
object PathSecurityValidator {
    
    // 本地文件系统的危险路径（对SSH远程不适用）
    private val LOCAL_DANGEROUS_PATHS = setOf(
        "/", "/root", "/etc", "/sys", "/proc", "/dev",
        "//", "..", "~", 
    )
    
    private val DANGEROUS_PATTERNS = listOf(
        "../", "..\\", "%2e%2e", "..%2f", "%2e.", 
        "....//", "....\\", "%252e%252e", "0x2e0x2e"
    )
    
    /**
     * 验证路径是否安全（本地文件系统）
     * @return 验证通过返回规范化后的安全路径，否则返回null
     */
    fun validatePath(path: String, basePath: String? = null): String? {
        if (path.isBlank()) return null
        
        // 检查危险模式
        val lowerPath = path.lowercase()
        for (pattern in DANGEROUS_PATTERNS) {
            if (lowerPath.contains(pattern)) {
                return null
            }
        }
        
        try {
            val file = File(path)
            val canonicalPath = file.canonicalPath
            
            // 如果指定了基础路径，确保目标路径在基础路径内
            basePath?.let {
                val canonicalBase = File(it).canonicalPath
                if (!canonicalPath.startsWith(canonicalBase)) {
                    return null
                }
            }
            
            // 检查是否是危险路径
            if (LOCAL_DANGEROUS_PATHS.any { canonicalPath == it || canonicalPath.startsWith("$it/") }) {
                return null
            }
            
            return canonicalPath
        } catch (e: IOException) {
            return null
        }
    }
    
    /**
     * 验证SSH远程路径是否安全
     * 对于SSH远程路径，只检查路径遍历攻击，不限制系统目录访问
     * 
     * @param path 远程路径
     * @return 验证通过返回原路径，否则返回null
     */
    fun validateRemotePath(path: String): String? {
        if (path.isBlank()) return null
        
        // 只检查危险模式（路径遍历攻击）
        val lowerPath = path.lowercase()
        for (pattern in DANGEROUS_PATTERNS) {
            if (lowerPath.contains(pattern)) {
                return null
            }
        }
        
        // 规范化路径（移除多余的斜杠等）
        return normalizePath(path)
    }
    
    /**
     * 规范化路径
     */
    fun normalizePath(path: String): String {
        if (path.isBlank()) return path
        
        // 移除多余的斜杠
        var normalized = path.replace(Regex("/+"), "/")
        
        // 保持开头的/
        if (path.startsWith("/") && !normalized.startsWith("/")) {
            normalized = "/$normalized"
        }
        
        // 移除尾部斜杠（除非是根目录）
        if (normalized.length > 1 && normalized.endsWith("/")) {
            normalized = normalized.dropLast(1)
        }
        
        return normalized
    }
    
    /**
     * 安全检查文件名（用于文件操作）
     */
    fun sanitizeFileName(fileName: String): String? {
        if (fileName.isBlank()) return null
        
        // 移除危险字符
        val sanitized = fileName
            .replace("..", "")
            .replace("/", "_")
            .replace("\\", "_")
            .replace("\u0000", "")
            .trim()
        
        return if (sanitized.isNotEmpty() && !sanitized.startsWith(".")) {
            sanitized
        } else null
    }
}
