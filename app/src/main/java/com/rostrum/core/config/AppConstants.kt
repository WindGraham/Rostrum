package com.rostrum.core.config

/**
 * 应用全局常量配置
 * 
 * 集中管理所有硬编码配置值，便于维护和修改
 */
object AppConstants {
    
    /**
     * 网络相关配置
     */
    object Network {
        // 通用超时配置（毫秒）
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val READ_TIMEOUT_MS = 60_000L
        const val WRITE_TIMEOUT_MS = 30_000L
        
        // FTP 配置
        const val FTP_DEFAULT_PORT = 21
        const val FTP_CONNECTION_TIMEOUT_MS = 30_000
        const val FTP_DATA_TIMEOUT_MS = 60_000
        const val FTP_ANONYMOUS_USER = "anonymous"
        const val FTP_ANONYMOUS_PASS = "anonymous@omnimaster.app"
        
        // SFTP/SSH 配置
        const val SFTP_DEFAULT_PORT = 22
        const val SFTP_CONNECTION_TIMEOUT_MS = 30_000
        const val SFTP_CHANNEL_TYPE = "sftp"
        const val SFTP_EXEC_CHANNEL_TYPE = "exec"
        
        // ADB 配置
        const val ADB_DEFAULT_PORT = 5555
        const val ADB_CONNECT_TIMEOUT_MS = 10_000
        const val ADB_READ_TIMEOUT_MS = 30_000
        const val ADB_PAIRING_TIMEOUT_MS = 30_000
        const val ADB_PORT_SCAN_TIMEOUT_MS = 100
        const val ADB_PORT_SCAN_BATCH_SIZE = 50
        const val ADB_PORT_RANGE_START = 37000
        const val ADB_PORT_RANGE_END = 44000
        
        // 本地地址
        const val LOCALHOST = "127.0.0.1"
        const val LOCALHOST_NAME = "localhost"
    }
    
    /**
     * 缓存相关配置
     */
    object Cache {
        const val IPC_CACHE_TTL_MS = 5 * 60 * 1000L  // 5 分钟
        const val FILE_CACHE_TTL_MS = 60_000L        // 1 分钟
        const val DISCOVERY_CACHE_DURATION_MS = 30_000L  // 30 秒
    }
    
    /**
     * 大小限制配置
     */
    object Limits {
        const val MAX_OUTPUT_SIZE = 1024 * 1024           // 1 MB
        const val MAX_SHELL_OUTPUT_SIZE = 100_000         // 100 KB
        const val MAX_SHELL_BUFFER_SIZE = 50_000          // 50 KB
        const val MAX_SHELL_TRIM_SIZE = 40_000            // 40 KB
        const val MAX_PREVIEW_CACHE = 10
        const val BUFFER_SIZE = 8192
        const val MAX_FILE_SIZE_FOR_EDITOR = 1024 * 1024  // 1 MB
        const val MAX_TEXT_PREVIEW_SIZE = 10 * 1024 * 1024   // 10 MB
        const val MAX_CODE_PREVIEW_SIZE = 10 * 1024 * 1024   // 10 MB
        const val MAX_PDF_PREVIEW_SIZE = 100 * 1024 * 1024   // 100 MB
    }
    
    /**
     * 执行服务配置
     */
    object Execution {
        const val DEFAULT_TIMEOUT_MS = 30_000L   // 30 秒
        const val LONG_TIMEOUT_MS = 60_000L      // 60 秒
        const val EXTENSION_TIMEOUT_MS = 30_000L // 30 秒
    }
    
    /**
     * UI 相关配置
     */
    object UI {
        const val STREAMING_UPDATE_INTERVAL_MS = 30L
        const val STREAMING_MIN_CHARS = 10
        const val POLL_INTERVAL_MS = 100L
        const val SAVE_DELAY_MS = 1000L
        const val SAVE_SUCCESS_DISPLAY_MS = 2000L
        const val CLEANUP_INTERVAL_MS = 30_000L
        const val FILE_EDITOR_SUBSCRIBE_WAIT_MS = 300L
    }
    
    /**
     * 路径配置
     */
    object Paths {
        const val DEFAULT_WORKSPACE = "/storage/emulated/0"
        const val PLUGINS_DIR = "plugins"
        const val BUILTIN_PLUGINS_DIR = "builtin_plugins"
        const val INSTALLED_PLUGINS_DIR = "installed_plugins"
        const val EXTERNAL_PLUGINS_DIR = "OmniMaster/plugins"
        const val PYTHON_RUNTIME_DIR = "python_runtime"
        const val PYTHON_EXECUTABLE = "libpython_main.so"
        const val ADB_PRIVATE_KEY_FILE = "adb_private.key"
        const val ADB_PUBLIC_KEY_FILE = "adb_public.key"
        const val BUILTIN_PLUGINS_CONFIG = "plugins/builtin-plugins.json"
    }
    
    /**
     * 协议相关
     */
    object Protocol {
        // ADB 协议
        const val ADB_HEADER_SIZE = 24
        const val ADB_MAX_PAYLOAD = 1024 * 1024  // 1 MB
        const val ADB_DEFAULT_MAX_PAYLOAD = 4096
        const val ADB_KEY_SIZE = 2048
        
        // mDNS 服务类型
        const val MDNS_ADB_TLS_CONNECT = "_adb-tls-connect._tcp"
        const val MDNS_ADB_TLS_PAIRING = "_adb-tls-pairing._tcp"
        const val MDNS_FTP = "_ftp._tcp.local."
        const val MDNS_SFTP = "_sftp._tcp.local."
        const val MDNS_SSH = "_ssh._tcp.local."
        const val MDNS_SMB = "_smb._tcp.local."
        const val MDNS_HTTP = "_http._tcp.local."
        const val MDNS_HTTPS = "_https._tcp.local."
    }
    
    /**
     * 通知配置
     */
    object Notification {
        const val ADB_PAIRING_CHANNEL = "adb_pairing"
        const val ADB_PAIRING_NOTIFICATION_ID = 1001
    }
    
    /**
     * SharedPreferences 名称
     */
    object Preferences {
        const val BOOKMARKS = "omnimaster_bookmarks"
        const val SAF_PERMISSIONS = "saf_permissions"
        const val ADB_KEY = "adb_key"
        const val ADB_PAIRING = "adb_pairing"
    }
    
}
