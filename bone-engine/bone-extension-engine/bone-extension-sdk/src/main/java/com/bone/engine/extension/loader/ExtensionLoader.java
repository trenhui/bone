package com.bone.engine.extension.loader;

import com.bone.engine.extension.ExtPoint;
import org.springframework.util.ClassUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 扩展点加载器
 * <p>
 * 基于Java SPI机制实现扩展点的自动发现和加载
 * <strong>主要功能：</strong>
 * <ul>
 *   <li>通过SPI机制自动加载扩展点实现</li>
 *   <li>管理扩展点实现的生命周期</li>
 *   <li>支持自定义类加载器</li>
 *   <li>实现扩展点的延迟加载</li>
 * </ul>
 * </p>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
public class ExtensionLoader<T> {

    private static final String SPI_RESOURCE_PREFIX = "META-INF/services/";
    private static final Map<Class<?>, ExtensionLoader<?>> LOADERS = new ConcurrentHashMap<>();
    
    private final Class<T> type;
    private final ClassLoader classLoader;
    private final Map<String, Class<?>> extensionClasses = new ConcurrentHashMap<>();
    private final Map<String, T> cachedInstances = new ConcurrentHashMap<>();
    
    private boolean initialized = false;

    private ExtensionLoader(Class<T> type, ClassLoader classLoader) {
        this.type = type;
        this.classLoader = classLoader;
    }

    /**
     * 获取指定类型的扩展点加载器
     */
    @SuppressWarnings("unchecked")
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type cannot be null");
        }
        
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type must be an interface: " + type.getName());
        }
        
        if (!type.isAnnotationPresent(ExtPoint.class)) {
            throw new IllegalArgumentException("Extension type must be annotated with @ExtPoint: " + type.getName());
        }
        
        return (ExtensionLoader<T>) LOADERS.computeIfAbsent(type, 
                k -> new ExtensionLoader<>(type, Thread.currentThread().getContextClassLoader()));
    }

    /**
     * 获取扩展点实现实例
     */
    public T getExtension(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Extension name cannot be null or empty");
        }
        
        // 检查缓存
        T instance = cachedInstances.get(name);
        if (instance == null) {
            synchronized (cachedInstances) {
                instance = cachedInstances.get(name);
                if (instance == null) {
                    // 初始化并加载扩展类
                    instance = createExtension(name);
                    cachedInstances.put(name, instance);
                }
            }
        }
        
        return instance;
    }

    /**
     * 创建扩展点实现实例
     */
    @SuppressWarnings("unchecked")
    private T createExtension(String name) {
        // 确保已初始化
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    loadExtensionClasses();
                    initialized = true;
                }
            }
        }
        
        Class<?> clazz = extensionClasses.get(name);
        if (clazz == null) {
            throw new IllegalArgumentException("No such extension: " + name + " for type: " + type.getName());
        }
        
        try {
            return (T) clazz.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create extension instance: " + name, e);
        }
    }

    /**
     * 加载扩展点实现类
     */
    private void loadExtensionClasses() {
        String resourceName = SPI_RESOURCE_PREFIX + type.getName();
        
        try {
            Enumeration<URL> urls = classLoader != null ? 
                    classLoader.getResources(resourceName) : 
                    ClassLoader.getSystemResources(resourceName);
            
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                loadFromUrl(url);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load extension resources: " + resourceName, e);
        }
    }

    /**
     * 从URL加载扩展点实现类
     */
    private void loadFromUrl(URL url) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 解析行内容，支持注释
                int commentIndex = line.indexOf('#');
                if (commentIndex >= 0) {
                    line = line.substring(0, commentIndex);
                }
                line = line.trim();
                
                if (!line.isEmpty()) {
                    try {
                        // 解析格式：className=name
                        int eqIndex = line.indexOf('=');
                        String className, name;
                        if (eqIndex >= 0) {
                            className = line.substring(0, eqIndex).trim();
                            name = line.substring(eqIndex + 1).trim();
                        } else {
                            // 默认使用类名作为扩展点名称
                            className = line;
                            name = getExtensionName(className);
                        }
                        
                        // 加载类
                        Class<?> clazz = ClassUtils.forName(className, classLoader);
                        if (type.isAssignableFrom(clazz)) {
                            extensionClasses.put(name, clazz);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to load extension class: " + line, e);
                    }
                }
            }
        }
    }

    /**
     * 从类名获取扩展点名称
     */
    private String getExtensionName(String className) {
        try {
            Class<?> clazz = ClassUtils.forName(className, classLoader);
            return ClassUtils.getShortName(clazz);
        } catch (ClassNotFoundException e) {
            // 如果无法加载类，使用简单类名
            int lastDotIndex = className.lastIndexOf('.');
            return lastDotIndex >= 0 ? className.substring(lastDotIndex + 1) : className;
        }
    }

    /**
     * 获取所有已加载的扩展点名称
     */
    public Map<String, Class<?>> getExtensionClasses() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    loadExtensionClasses();
                    initialized = true;
                }
            }
        }
        return new HashMap<>(extensionClasses);
    }

    /**
     * 清理扩展点缓存
     */
    public void clearCache() {
        cachedInstances.clear();
        extensionClasses.clear();
        initialized = false;
    }

    /**
     * 获取扩展点类型
     */
    public Class<T> getType() {
        return type;
    }
}