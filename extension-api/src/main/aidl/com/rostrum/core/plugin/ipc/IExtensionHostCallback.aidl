package com.rostrum.core.plugin.ipc;

/**
 * Extension Host Callback AIDL 接口
 * 
 * 主应用提供的回调接口，供插件调用
 * 插件通过此接口访问主应用的功能
 */
interface IExtensionHostCallback {
    
    // ========== 文件系统操作 ==========
    
    /**
     * 读取文件
     */
    byte[] readFile(String uri);
    
    /**
     * 写入文件
     */
    void writeFile(String uri, in byte[] content);
    
    /**
     * 读取文本文件
     */
    String readTextFile(String uri, String encoding);
    
    /**
     * 检查文件是否存在
     */
    boolean fileExists(String uri);
    
    /**
     * 获取文件信息 (返回 JSON)
     */
    String getFileInfo(String uri);
    
    /**
     * 列出目录内容 (返回 JSON 数组)
     */
    String listDirectory(String uri);
    
    // ========== 命令操作 ==========
    
    /**
     * 注册命令
     */
    void registerCommand(String pluginId, String commandId, String title);
    
    /**
     * 执行命令
     */
    String executeCommand(String commandId, String argsJson);
    
    // ========== UI 操作 ==========
    
    /**
     * 显示信息消息
     */
    String showInformationMessage(String message, in String[] items);
    
    /**
     * 显示警告消息
     */
    String showWarningMessage(String message, in String[] items);
    
    /**
     * 显示错误消息
     */
    String showErrorMessage(String message, in String[] items);
    
    /**
     * 显示输入框
     */
    String showInputBox(String prompt, String placeholder, String defaultValue);
    
    /**
     * 显示状态栏消息
     */
    void showStatusBarMessage(String message, int timeoutMs);
    
    // ========== 预览/编辑器注册 ==========
    
    /**
     * 注册文件预览提供者
     */
    void registerPreviewProvider(String pluginId, String providerId, String extensionsJson);
    
    /**
     * 注册文件编辑器提供者
     */
    void registerEditorProvider(String pluginId, String providerId, String extensionsJson);
    
    // ========== 事件通知 ==========
    
    /**
     * 通知事件
     */
    void notifyEvent(String pluginId, String eventType, String eventData);
    
    /**
     * 日志输出
     */
    void log(String pluginId, String level, String message);
}

