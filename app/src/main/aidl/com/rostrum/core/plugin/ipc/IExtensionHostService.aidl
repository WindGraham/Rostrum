package com.rostrum.core.plugin.ipc;

/**
 * Extension Host Service AIDL 接口
 * 
 * 用于主进程与插件进程之间的 IPC 通信
 */
interface IExtensionHostService {
    /**
     * 文件系统操作：读取文件
     */
    byte[] readFile(String uri);
    
    /**
     * 文件系统操作：写入文件
     */
    void writeFile(String uri, in byte[] content);
    
    /**
     * 文件系统操作：读取文本文件
     */
    String readTextFile(String uri, String encoding);
    
    /**
     * 文件系统操作：写入文本文件
     */
    void writeTextFile(String uri, String content, String encoding);
    
    /**
     * 文件系统操作：检查文件是否存在
     */
    boolean exists(String uri);
    
    /**
     * 文件系统操作：获取文件信息
     */
    String getFileInfo(String uri);  // 返回 JSON 字符串
    
    /**
     * 文件系统操作：列出目录
     */
    String listDirectory(String uri);  // 返回 JSON 字符串数组
    
    /**
     * 文件系统操作：创建目录
     */
    void createDirectory(String uri);
    
    /**
     * 文件系统操作：删除文件或目录
     */
    void delete(String uri);
    
    /**
     * 文件系统操作：复制文件或目录
     */
    void copy(String sourceUri, String targetUri);
    
    /**
     * 文件系统操作：移动文件或目录
     */
    void move(String sourceUri, String targetUri);
    
    /**
     * 终端操作：执行命令
     */
    String executeCommand(String command, String workingDirectory);
    
    /**
     * 命令注册：注册命令
     */
    void registerCommand(String commandId, String title, String category);
    
    /**
     * 命令执行：执行命令
     */
    String executeRegisteredCommand(String commandId, String argsJson);
    
    /**
     * UI 操作：注册文件预览提供者
     */
    void registerPreview(String pluginId, String mimeTypesJson, String extensionsJson);
    
    /**
     * UI 操作：注册文件编辑提供者
     */
    void registerEditor(String pluginId, String mimeTypesJson, String extensionsJson);
    
    /**
     * 窗口操作：显示信息消息
     */
    String showInformationMessage(String message, in String[] items);
    
    /**
     * 窗口操作：显示警告消息
     */
    String showWarningMessage(String message, in String[] items);
    
    /**
     * 窗口操作：显示错误消息
     */
    String showErrorMessage(String message, in String[] items);
    
    /**
     * 窗口操作：显示输入框
     */
    String showInputBox(String prompt, String placeHolder, String defaultValue, boolean password);
    
    /**
     * 事件通知：通知主进程事件
     */
    void notifyEvent(String eventType, String eventData);
    
    /**
     * 心跳检测：检查服务是否可用
     */
    boolean ping();
    
    /**
     * 批量操作：执行多个操作（减少 IPC 调用次数）
     * 
     * @param requestJson 批量操作请求（JSON 字符串）
     * @return 批量操作响应（JSON 字符串）
     */
    String executeBatch(String requestJson);
}

