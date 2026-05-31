package com.rostrum.core.plugin.ipc.batch

/**
 * 批量操作请求
 * 
 * 用于将多个操作合并为一次 IPC 调用
 */
data class BatchOperationRequest(
    /**
     * 操作列表
     */
    val operations: List<Operation>
) {
    /**
     * 单个操作
     */
    data class Operation(
        /**
         * 操作类型
         */
        val type: OperationType,
        
        /**
         * 操作 ID（用于标识结果）
         */
        val id: String,
        
        /**
         * 操作参数（JSON 字符串）
         */
        val params: String
    )
    
    /**
     * 操作类型
     */
    enum class OperationType {
        READ_FILE,
        WRITE_FILE,
        EXISTS,
        GET_FILE_INFO,
        LIST_DIRECTORY,
        CREATE_DIRECTORY,
        DELETE,
        COPY,
        MOVE,
        EXECUTE_COMMAND
    }
}

/**
 * 批量操作响应
 */
data class BatchOperationResponse(
    /**
     * 结果列表（按操作 ID 索引）
     */
    val results: Map<String, OperationResult>
) {
    /**
     * 单个操作结果
     */
    data class OperationResult(
        /**
         * 是否成功
         */
        val success: Boolean,
        
        /**
         * 结果数据（JSON 字符串或二进制数据的 Base64）
         */
        val data: String? = null,
        
        /**
         * 错误信息
         */
        val error: String? = null
    )
}

