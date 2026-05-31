package com.rostrum.extension.api

/**
 * 窗口 API
 * 
 * 提供窗口交互功能
 */
interface WindowApi {
    /**
     * 显示信息消息
     * 
     * @param message 消息内容
     * @param items 可选操作项
     * @return 用户选择的操作项，如果取消返回 null
     */
    suspend fun showInformationMessage(message: String, vararg items: String): String?
    
    /**
     * 显示警告消息
     * 
     * @param message 消息内容
     * @param items 可选操作项
     * @return 用户选择的操作项，如果取消返回 null
     */
    suspend fun showWarningMessage(message: String, vararg items: String): String?
    
    /**
     * 显示错误消息
     * 
     * @param message 消息内容
     * @param items 可选操作项
     * @return 用户选择的操作项，如果取消返回 null
     */
    suspend fun showErrorMessage(message: String, vararg items: String): String?
    
    /**
     * 显示输入框
     * 
     * @param options 输入框选项
     * @return 用户输入的内容，如果取消返回 null
     */
    suspend fun showInputBox(options: InputBoxOptions? = null): String?
    
    /**
     * 创建状态栏项
     * 
     * @param alignment 对齐方式
     * @return 状态栏项
     */
    fun createStatusBarItem(alignment: StatusBarAlignment = StatusBarAlignment.LEFT): StatusBarItem
}

/**
 * 输入框选项
 */
data class InputBoxOptions(
    val prompt: String? = null,
    val placeHolder: String? = null,
    val value: String? = null,
    val password: Boolean = false,
    val ignoreFocusOut: Boolean = false,
    val validateInput: ((String) -> String?)? = null
)

