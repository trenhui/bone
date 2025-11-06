package com.bone.metadata.sdk.extension.plugin;

import com.bone.metadata.sdk.domain.exception.ServiceNotFoundException;
import com.bone.metadata.sdk.support.security.service.SecurityService;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认插件上下文实现
 */
public class DefaultPluginContext implements PluginContext {

    private final PluginDescriptor descriptor;
    private final PluginManager pluginManager;
    private final SecurityService securityService;
    private final Map<Class<?>, Object> localServices = new ConcurrentHashMap<>();
    private Properties config;

    public DefaultPluginContext(PluginDescriptor descriptor,
                                PluginManager pluginManager,
                                SecurityService securityService) {
        this.descriptor = descriptor;
        this.pluginManager = pluginManager;
        this.securityService = securityService;
        this.config = new Properties(descriptor.getConfig());
    }

    @Override
    public PluginManager getPluginManager() {
        return pluginManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> serviceType) throws ServiceNotFoundException {
        // 1. 检查本地服务
        T localService = (T) localServices.get(serviceType);
        if (localService != null) return localService;

        // 2. 通过插件管理器获取
        return pluginManager.getService(serviceType, descriptor.getId());
    }

    @Override
    public <T> void registerService(Class<T> serviceType, T service) {
        // 确保插件有权限注册服务
        securityService.validateServiceRegistration(descriptor.getId(), serviceType.getSimpleName());
        localServices.put(serviceType, service);
    }

    @Override
    public PluginDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public Properties getConfig() {
        return new Properties(config);
    }

    @Override
    public void updateConfig(Properties newConfig) {
        securityService.validateConfigUpdate(descriptor.getId());
        this.config = new Properties(newConfig);
    }
}