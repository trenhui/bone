package com.bone.metadata.sdk.extension.plugin;

import com.bone.metadata.sdk.domain.exception.ServiceNotFoundException;

import java.util.Properties;

/**
 * 插件运行时上下文接口
 */
public interface PluginContext {

    /**
     * 获取插件管理器实例
     */
    PluginManager getPluginManager();

    /**
     * 获取服务实例
     * @param serviceType 服务接口类型
     * @return 服务实例
     * @throws ServiceNotFoundException 服务未找到时抛出
     */
    <T> T getService(Class<T> serviceType) throws ServiceNotFoundException;

    /**
     * 注册服务
     * @param serviceType 服务接口类型
     * @param service 服务实现实例
     */
    <T> void registerService(Class<T> serviceType, T service);

    /**
     * 获取插件自身描述信息
     */
    PluginDescriptor getDescriptor();

    /**
     * 获取插件配置
     * @return 插件配置属性
     */
    Properties getConfig();

    /**
     * 更新插件配置（热更新）
     * @param newConfig 新配置
     */
    void updateConfig(Properties newConfig);
}