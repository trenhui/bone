package com.bone.engine.extension.support.utils;

/**
 * 扩展点键生成工具类
 * 提供统一的扩展点键生成方法，避免重复实现
 * 
 * @author Bone Engine Team
 * @version 1.0.0
 */
public final class ExtensionKeyGenerator {
    
    private ExtensionKeyGenerator() {
        // 私有构造函数，避免实例化
    }
    
    /**
     * 生成扩展点注册键
     * 格式：接口名:实现类名:对象哈希码
     * 
     * @param interfaceName 接口名称
     * @param provider 提供者实例
     * @return 唯一的注册键
     */
    public static String generateExtensionKey(String interfaceName, Object provider) {
        if (interfaceName == null || provider == null) {
            throw new IllegalArgumentException("Interface name and provider must not be null");
        }
        return interfaceName + ":" + provider.getClass().getCanonicalName() + ":" + System.identityHashCode(provider);
    }
    
    /**
     * 生成扩展点注册键
     * 格式：接口类:实现类名:对象哈希码
     * 
     * @param interfaceClass 接口类
     * @param provider 提供者实例
     * @return 唯一的注册键
     */
    public static String generateExtensionKey(Class<?> interfaceClass, Object provider) {
        if (interfaceClass == null || provider == null) {
            throw new IllegalArgumentException("Interface class and provider must not be null");
        }
        return generateExtensionKey(interfaceClass.getCanonicalName(), provider);
    }
}