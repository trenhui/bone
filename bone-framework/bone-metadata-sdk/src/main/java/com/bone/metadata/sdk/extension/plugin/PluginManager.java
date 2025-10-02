package com.bone.metadata.sdk.extension.plugin;

import com.bone.metadata.sdk.support.security.service.SecurityService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 精简高效的插件管理器实现
 */
public class PluginManager {

    private final SecurityService securityService;
    private final Map<String, PluginDescriptor> descriptors = new ConcurrentHashMap<>();
    private final Map<String, PluginContext> contexts = new ConcurrentHashMap<>();
    private final Map<String, Plugin> plugins = new ConcurrentHashMap<>();

    // 服务注册表
    private final Map<Class<?>, Object> globalServices = new ConcurrentHashMap<>();
    private final Map<String, Map<Class<?>, Object>> pluginServices = new ConcurrentHashMap<>();

    public PluginManager(SecurityService securityService) {
        this.securityService = securityService;
        registerGlobalService(PluginManager.class, this);
        registerGlobalService(SecurityService.class, securityService);
    }

    /**
     * 安装插件
     * @param descriptor 插件描述符
     */
    public void installPlugin(PluginDescriptor descriptor) {
        descriptors.put(descriptor.getId(), descriptor);
        descriptor.setStatus(PluginStatus.INSTALLED);
    }

    /**
     * 初始化插件
     * @param pluginId 插件ID
     */
    public void initializePlugin(String pluginId) {
        PluginDescriptor descriptor = getDescriptor(pluginId);

        // 创建插件实例
        Plugin plugin = createPluginInstance(descriptor);
        plugins.put(pluginId, plugin);

        // 创建上下文
        PluginContext context = new DefaultPluginContext(descriptor, this, securityService);
        contexts.put(pluginId, context);

        // 初始化插件
        plugin.initialize(context);
        descriptor.setStatus(PluginStatus.INITIALIZED);
    }

    /**
     * 启动插件
     * @param pluginId 插件ID
     */
    public void startPlugin(String pluginId) {
        Plugin plugin = getPlugin(pluginId);
        plugin.start();
        PluginDescriptor descriptor = getDescriptor(pluginId);
        descriptor.setStatus(PluginStatus.STARTED);
    }

    /**
     * 停止插件
     * @param pluginId 插件ID
     */
    public void stopPlugin(String pluginId) {
        Plugin plugin = getPlugin(pluginId);
        plugin.stop();
        PluginDescriptor descriptor = getDescriptor(pluginId);
        descriptor.setStatus(PluginStatus.STOPPED);
    }

    /**
     * 卸载插件
     * @param pluginId 插件ID
     */
    public void uninstallPlugin(String pluginId) {
        Plugin plugin = plugins.get(pluginId);
        if (plugin != null) {
            plugin.stop();
            plugin.destroy();
            plugins.remove(pluginId);
        }

        contexts.remove(pluginId);
        descriptors.remove(pluginId);
        pluginServices.remove(pluginId);
    }

    // 服务管理
    public <T> void registerGlobalService(Class<T> serviceType, T service) {
        globalServices.put(serviceType, service);
    }

    public <T> void registerPluginService(String pluginId, Class<T> serviceType, T service) {
        pluginServices.computeIfAbsent(pluginId, k -> new HashMap<>())
                .put(serviceType, service);
    }

    public <T> T getService(Class<T> serviceType, String pluginId) {
        // 1. 检查插件特定服务
        if (pluginId != null) {
            Map<Class<?>, Object> services = pluginServices.get(pluginId);
            if (services != null) {
                T service = (T) services.get(serviceType);
                if (service != null) return service;
            }
        }

        // 2. 检查全局服务
        return (T) globalServices.get(serviceType);
    }

    // 内部方法
    private Plugin createPluginInstance(PluginDescriptor descriptor) {
        try {
            // 使用插件的类加载器创建实例
            Class<?> pluginClass = Class.forName(
                    descriptor.getEntryClass(),
                    true,
                    descriptor.getClassLoader()
            );
            return (Plugin) pluginClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new PluginInitializationException("Failed to create plugin instance", e);
        }
    }

    // Getters
    public PluginDescriptor getDescriptor(String pluginId) {
        return descriptors.get(pluginId);
    }

    public Plugin getPlugin(String pluginId) {
        return plugins.get(pluginId);
    }

    public PluginContext getContext(String pluginId) {
        return contexts.get(pluginId);
    }
}