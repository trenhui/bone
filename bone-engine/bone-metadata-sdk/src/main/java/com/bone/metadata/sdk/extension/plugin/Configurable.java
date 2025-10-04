package com.bone.metadata.sdk.extension.plugin;

import java.util.Properties;

/**
 * 可配置组件接口，提供标准化配置管理
 */
public interface Configurable {

    /**
     * 设置组件配置
     * @param config 配置参数
     */
    void setConfig(Properties config);

    /**
     * 获取当前配置
     * @return 配置参数集合
     */
    Properties getConfig();

    /**
     * 刷新配置（热更新支持）
     */
    default void refreshConfig() {
        // 默认空实现，子类可覆盖实现热加载逻辑
    }
}