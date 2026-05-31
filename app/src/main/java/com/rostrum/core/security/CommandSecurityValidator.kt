package com.rostrum.core.security

/**
 * 命令安全验证器 - 防止命令注入攻击
 */
object CommandSecurityValidator {
    
    // 危险字符黑名单
    private val DANGEROUS_CHARS = listOf(
        ';', '&', '|', '$', '`', '>', '<', '(', ')', '{', '}', 
        '\n', '\r', '\t'
    )
    
    // 危险命令黑名单
    private val DANGEROUS_COMMANDS = setOf(
        "rm -rf", "rm -r /", ":(){ :|:& };:", "> /dev/sda",
        "mkfs", "dd if=/dev/zero", "chmod -R 777 /",
        "mv /* /dev/null", "wget", "curl", "sh -c", "bash -c",
        "eval", "exec", "system"
    )
    
    /**
     * 验证命令是否安全
     * @return 是否安全
     */
    fun validateCommand(command: String): Boolean {
        if (command.isBlank()) return false
        
        val trimmed = command.trim()
        
        // 检查是否包含危险字符
        for (char in DANGEROUS_CHARS) {
            if (trimmed.contains(char)) {
                return false
            }
        }
        
        // 检查是否包含危险命令
        val lowerCommand = trimmed.lowercase()
        for (dangerous in DANGEROUS_COMMANDS) {
            if (lowerCommand.contains(dangerous.lowercase())) {
                return false
            }
        }
        
        // 只允许白名单命令
        val allowedPrefixes = listOf(
            "ls", "cat", "echo", "pwd", "cd", "mkdir", "touch",
            "cp", "mv", "rm", "find", "grep", "head", "tail",
            "chmod", "chown", "stat", "du", "df", "ps", "top",
            "whoami", "id", "uname", "date", "cal", "clear",
            "python", "python3", "pip", "node", "npm", "git",
            "adb", "am", "pm", "logcat", "dumpsys", "getprop",
            "input", "screencap", "screenrecord"
        )
        
        val firstToken = trimmed.split(" ").firstOrNull()?.lowercase() ?: ""
        return allowedPrefixes.any { firstToken == it || firstToken.startsWith("$it ") }
    }
    
    /**
     * 转义命令参数中的特殊字符
     */
    fun escapeArgument(arg: String): String {
        return arg
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("`", "\\`")
            .replace("$", "\\$")
    }
}
