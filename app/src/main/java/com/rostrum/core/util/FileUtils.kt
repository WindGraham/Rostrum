package com.rostrum.core.util

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * 文件工具类
 */
object FileUtils {
    
    /**
     * 获取外部存储根目录
     */
    fun getExternalStorageRoot(): File {
        return Environment.getExternalStorageDirectory()
    }
    
    /**
     * 获取应用数据目录
     */
    fun getAppDataDir(context: Context): File {
        return File(context.filesDir, "OmniMaster")
    }
    
    /**
     * 检查文件是否存在
     */
    fun exists(path: String): Boolean {
        return File(path).exists()
    }
    
    /**
     * 创建目录（如果不存在）
     */
    fun createDirectoryIfNotExists(path: String): Boolean {
        val dir = File(path)
        return if (!dir.exists()) {
            dir.mkdirs()
        } else {
            true
        }
    }
    
    /**
     * 获取文件扩展名（不含点号，小写）
     */
    fun getFileExtension(path: String): String? {
        val fileName = File(path).name
        val lastDotIndex = fileName.lastIndexOf('.')
        return if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
            fileName.substring(lastDotIndex + 1).lowercase()
        } else {
            null
        }
    }
    
    /**
     * 判断是否为文本文件
     * 
     * @param path 文件路径
     * @param allowedExtensions 允许的文件扩展名集合（不含点号，小写），如果为null则使用默认列表
     * @return 是否为文本文件
     */
    fun isTextFile(path: String, allowedExtensions: Set<String>? = null): Boolean {
        val extension = getFileExtension(path) ?: return false
        val allowed = allowedExtensions ?: DEFAULT_TEXT_FILE_EXTENSIONS
        return allowed.contains(extension)
    }
    
    /**
     * 获取文件类型分类
     */
    fun getFileTypeCategory(path: String): FileTypeCategory {
        val extension = getFileExtension(path) ?: return FileTypeCategory.UNKNOWN
        
        return when (extension) {
            in CODE_EXTENSIONS -> FileTypeCategory.CODE
            in MARKUP_EXTENSIONS -> FileTypeCategory.MARKUP
            in CONFIG_EXTENSIONS -> FileTypeCategory.CONFIG
            in SCRIPT_EXTENSIONS -> FileTypeCategory.SCRIPT
            in DATA_EXTENSIONS -> FileTypeCategory.DATA
            in TEXT_EXTENSIONS -> FileTypeCategory.TEXT
            else -> FileTypeCategory.UNKNOWN
        }
    }
    
    /**
     * 默认文本文件扩展名列表
     */
    private val DEFAULT_TEXT_FILE_EXTENSIONS = setOf(
        // 文本文件
        "txt", "md", "markdown", "rst",
        // 代码文件
        "kt", "java", "py", "js", "ts", "jsx", "tsx", "cpp", "c", "h", "hpp",
        "cs", "go", "rs", "rb", "php", "swift", "dart", "scala", "clj",
        // 配置文件
        "json", "xml", "yaml", "yml", "toml", "ini", "conf", "cfg",
        // Web文件
        "html", "htm", "css", "scss", "sass", "less",
        // 脚本文件
        "sh", "bash", "zsh", "fish", "ps1", "bat", "cmd",
        // 其他文本格式
        "log", "csv", "tsv", "sql", "diff", "patch"
    )
    
    private val CODE_EXTENSIONS = setOf(
        "kt", "java", "py", "js", "ts", "jsx", "tsx", "cpp", "c", "h", "hpp",
        "cs", "go", "rs", "rb", "php", "swift", "dart", "scala", "clj"
    )
    
    private val MARKUP_EXTENSIONS = setOf(
        "html", "htm", "xml", "md", "markdown", "rst"
    )
    
    private val CONFIG_EXTENSIONS = setOf(
        "json", "yaml", "yml", "toml", "ini", "conf", "cfg"
    )
    
    private val SCRIPT_EXTENSIONS = setOf(
        "sh", "bash", "zsh", "fish", "ps1", "bat", "cmd", "py"
    )
    
    private val DATA_EXTENSIONS = setOf(
        "csv", "tsv", "sql", "log"
    )
    
    private val TEXT_EXTENSIONS = setOf(
        "txt", "diff", "patch"
    )
}

/**
 * 文件类型分类
 */
enum class FileTypeCategory {
    CODE,      // 代码文件
    MARKUP,    // 标记语言文件（HTML, XML, Markdown等）
    CONFIG,    // 配置文件（JSON, YAML等）
    SCRIPT,    // 脚本文件
    DATA,      // 数据文件（CSV, SQL等）
    TEXT,      // 纯文本文件
    UNKNOWN    // 未知类型
}

