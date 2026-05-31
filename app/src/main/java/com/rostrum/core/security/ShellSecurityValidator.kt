package com.rostrum.core.security

object ShellSecurityValidator {
    private const val MAX_COMMAND_LENGTH = 10000
    
    private val DANGEROUS_PATTERNS = listOf(
        ";", "&&", "||", "|", "`", "$()",
        ">>", "<", "2>", ">&",
        "rm -rf /", "rm -rf /*",
        "mkfs", "dd if=", ":(){ :|:& };:",
        "chmod 777 /", "chown -R",
        "wget", "curl", ">/dev/null",
        "eval", "exec"
    )
    
    private val ALLOWED_COMMANDS = setOf(
        "ls", "cat", "echo", "pwd", "cd",
        "mkdir", "touch", "cp", "mv", "rm",
        "head", "tail", "grep", "find", "wc",
        "ps", "top", "df", "du", "chmod", "chown",
        "tar", "zip", "unzip", "git", "python",
        "node", "npm", "java", "javac"
    )
    
    fun validateCommand(command: String): ValidationResult {
        if (command.isBlank()) {
            return ValidationResult.Failure("命令不能为空")
        }
        
        if (command.length > MAX_COMMAND_LENGTH) {
            return ValidationResult.Failure("命令过长")
        }
        
        // 检测危险模式
        DANGEROUS_PATTERNS.forEach { pattern ->
            if (command.contains(pattern)) {
                return ValidationResult.Failure("检测到危险字符/模式: $pattern")
            }
        }
        
        // 提取命令主体
        val cmdParts = command.trim().split(Regex("\\s+"))
        if (cmdParts.isEmpty()) {
            return ValidationResult.Failure("无效命令")
        }
        
        val baseCmd = cmdParts[0].substringAfterLast("/")
        
        return ValidationResult.Success(command)
    }
    
    fun sanitizeArgument(arg: String): String {
        return arg
            .replace(";", "")
            .replace("&", "")
            .replace("|", "")
            .replace("`", "")
            .replace("$", "")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("<", "")
            .replace(">", "")
            .trim()
    }
    
    fun escapeForShell(input: String): String {
        return "'" + input.replace("'", "'\\''") + "'"
    }
    
    sealed class ValidationResult {
        data class Success(val command: String) : ValidationResult()
        data class Failure(val reason: String) : ValidationResult()
    }
}
