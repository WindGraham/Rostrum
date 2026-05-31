package com.rostrum.core.plugin.ipc;

import com.rostrum.core.plugin.ipc.IExtensionHostCallback;

/**
 * Extension Service AIDL 接口
 * 
 * 插件端实现的服务接口，供主应用调用
 */
interface IExtensionService {
    /**
     * 激活插件
     * 
     * @param callback 回调接口，用于插件调用主应用功能
     */
    void activate(IExtensionHostCallback callback);
    
    /**
     * 停用插件
     */
    void deactivate();
    
    /**
     * 处理命令
     * 
     * @param command 命令名称
     * @param args 命令参数
     * @return 命令执行结果
     */
    String handleCommand(String command, in List<String> args);
    
    /**
     * 检查是否能处理指定文件类型
     * 
     * @param fileExtension 文件扩展名
     * @return 是否能处理
     */
    boolean canHandle(String fileExtension);
    
    /**
     * 获取插件 ID
     */
    String getPluginId();
    
    /**
     * 获取支持的文件扩展名列表
     */
    List<String> getSupportedExtensions();
}

