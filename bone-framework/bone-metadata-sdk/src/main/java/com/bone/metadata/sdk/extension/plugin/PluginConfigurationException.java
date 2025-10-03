package com.bone.metadata.sdk.extension.plugin;

/**
 * 插件配置异常基类
 */
public class PluginConfigurationException extends RuntimeException {
    public PluginConfigurationException(String message) {
        super(message);
    }

    public PluginConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}