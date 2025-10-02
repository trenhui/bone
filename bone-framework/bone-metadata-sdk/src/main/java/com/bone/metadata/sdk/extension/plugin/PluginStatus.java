package com.bone.metadata.sdk.extension.plugin;

/**
 * 插件生命周期状态
 */
public enum PluginStatus {
    INSTALLED,     // 已安装但未初始化
    INITIALIZED,   // 已初始化
    STARTED,       // 已启动运行中
    STOPPED,       // 已停止
    UNINSTALLED    // 已卸载
}