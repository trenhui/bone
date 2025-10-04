package com.bone.metadata.sdk.extension.plugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * 插件类加载器 - 提供隔离环境
 */
public class PluginClassLoader extends URLClassLoader {
    private final String pluginId;

    public PluginClassLoader(URL[] urls, ClassLoader parent, String pluginId) {
        super(urls, parent);
        this.pluginId = pluginId;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {

        // 1. 检查是否已加载
        Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass != null) {
            return loadedClass;
        }

        // 2. 优先尝试自身加载（插件类隔离）
        try {
            Class<?> clazz = findClass(name);
            if (resolve) {
                resolveClass(clazz);
            }
            return clazz;
        } catch (ClassNotFoundException e) {
            // 忽略，继续尝试父类加载器
        }

        // 3. 安全委托给父类加载器
        try {
            return super.loadClass(name, resolve);
        } catch (ClassNotFoundException e) {
            throw new ClassNotFoundException("Class not found: " + name + " for plugin: " + pluginId);
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

    public String getPluginId() {
        return pluginId;
    }
}