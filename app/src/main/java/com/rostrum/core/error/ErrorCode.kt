package com.rostrum.core.error

/**
 * 错误码枚举
 * 
 * 统一管理所有错误类型，便于调试和错误追踪
 */
enum class ErrorCode(val code: Int, val description: String) {
    // 通用错误 (1000-1999)
    UNKNOWN(1000, "Unknown error"),
    INVALID_ARGUMENT(1001, "Invalid argument"),
    INVALID_STATE(1002, "Invalid state"),
    OPERATION_CANCELLED(1003, "Operation cancelled"),
    TIMEOUT(1004, "Operation timeout"),
    NOT_INITIALIZED(1005, "Not initialized"),
    ALREADY_EXISTS(1006, "Already exists"),
    NOT_SUPPORTED(1007, "Not supported"),
    
    // 网络错误 (2000-2999)
    NETWORK_ERROR(2000, "Network error"),
    NETWORK_TIMEOUT(2001, "Network timeout"),
    CONNECTION_FAILED(2002, "Connection failed"),
    CONNECTION_CLOSED(2003, "Connection closed"),
    HOST_UNREACHABLE(2004, "Host unreachable"),
    DNS_RESOLUTION_FAILED(2005, "DNS resolution failed"),
    SSL_ERROR(2006, "SSL/TLS error"),
    AUTHENTICATION_FAILED(2007, "Authentication failed"),
    
    // 文件操作错误 (3000-3999)
    FILE_ERROR(3000, "File operation error"),
    FILE_NOT_FOUND(3001, "File not found"),
    FILE_ACCESS_DENIED(3002, "File access denied"),
    FILE_READ_ERROR(3003, "File read error"),
    FILE_WRITE_ERROR(3004, "File write error"),
    FILE_DELETE_ERROR(3005, "File delete error"),
    FILE_COPY_ERROR(3006, "File copy error"),
    FILE_MOVE_ERROR(3007, "File move error"),
    DIRECTORY_NOT_FOUND(3008, "Directory not found"),
    DIRECTORY_NOT_EMPTY(3009, "Directory not empty"),
    DISK_FULL(3010, "Disk full"),
    FILE_TOO_LARGE(3011, "File too large"),
    
    // 插件错误 (4000-4999)
    PLUGIN_ERROR(4000, "Plugin error"),
    PLUGIN_NOT_FOUND(4001, "Plugin not found"),
    PLUGIN_LOAD_FAILED(4002, "Plugin load failed"),
    PLUGIN_INIT_FAILED(4003, "Plugin initialization failed"),
    PLUGIN_EXECUTION_FAILED(4004, "Plugin execution failed"),
    PLUGIN_INVALID_FORMAT(4005, "Invalid plugin format"),
    PLUGIN_VERSION_MISMATCH(4006, "Plugin version mismatch"),
    PLUGIN_DEPENDENCY_MISSING(4007, "Plugin dependency missing"),
    
    // 配置错误 (5000-5999)
    CONFIG_ERROR(5000, "Configuration error"),
    CONFIG_NOT_FOUND(5001, "Configuration not found"),
    CONFIG_INVALID(5002, "Invalid configuration"),
    CONFIG_SAVE_FAILED(5003, "Configuration save failed"),
    CONFIG_LOAD_FAILED(5004, "Configuration load failed"),
    
    // ADB 错误 (6000-6999)
    ADB_ERROR(6000, "ADB error"),
    ADB_CONNECTION_FAILED(6001, "ADB connection failed"),
    ADB_AUTH_FAILED(6002, "ADB authentication failed"),
    ADB_PAIRING_FAILED(6003, "ADB pairing failed"),
    ADB_INVALID_PAIRING_CODE(6004, "Invalid pairing code"),
    ADB_KEY_ERROR(6005, "ADB key error"),
    ADB_COMMAND_FAILED(6006, "ADB command failed"),
    
    // Shell 错误 (7000-7999)
    SHELL_ERROR(7000, "Shell error"),
    SHELL_EXECUTION_FAILED(7001, "Shell execution failed"),
    SHELL_TIMEOUT(7002, "Shell execution timeout"),
    SHELL_PERMISSION_DENIED(7003, "Shell permission denied"),
    
    // RPC 错误 (9000-9999)
    RPC_ERROR(9000, "RPC error"),
    RPC_CONNECTION_FAILED(9001, "RPC connection failed"),
    RPC_METHOD_NOT_FOUND(9002, "RPC method not found"),
    RPC_INVALID_PARAMS(9003, "Invalid RPC parameters"),
    RPC_TIMEOUT(9004, "RPC timeout");
    
    override fun toString(): String = "[$code] $description"
}
