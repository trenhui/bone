package com.bone.engine.extension.utils;

import com.bone.engine.extension.ExtPoint;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 扩展点相关的通用工具类
 * 提供扩展点接口和实现类的查找、验证等通用功能
 * 
 * @author renhui.trh
 * @since 1.0.0
 */
public abstract class ExtPointUtils {

    /**
     * 查找指定实现类中所有标记了@ExtPoint注解的接口
     * 
     * @param implementationClass 实现类
     * @return 带有@ExtPoint注解的接口列表
     */
    public static List<Class<?>> findExtPointInterfaces(Class<?> implementationClass) {
        List<Class<?>> result = new ArrayList<>();
        
        // 获取所有直接实现的接口
        for (Class<?> iface : implementationClass.getInterfaces()) {
            if (iface.isAnnotationPresent(ExtPoint.class)) {
                result.add(iface);
            }
        }
        
        // 递归查找父类实现的接口
        Class<?> superClass = implementationClass.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            result.addAll(findExtPointInterfaces(superClass));
        }
        
        return result;
    }

    /**
     * 从ApplicationContext中查找所有标记了@ExtPoint的接口
     * 
     * @param applicationContext Spring应用上下文
     * @return 所有扩展点接口的集合
     */
    public static Set<Class<?>> findAllExtPointInterfaces(ApplicationContext applicationContext) {
        Set<Class<?>> extPointInterfaces = new HashSet<>();
        
        try {
            // 获取所有Bean类型
            String[] beanNames = applicationContext.getBeanDefinitionNames();
            
            for (String beanName : beanNames) {
                Class<?> beanType = applicationContext.getType(beanName);
                if (beanType != null) {
                    // 收集所有实现的扩展点接口
                    extPointInterfaces.addAll(findExtPointInterfaces(beanType));
                }
            }
        } catch (Exception e) {
            // 异常处理应在调用方进行
            throw new RuntimeException("Failed to find extpoint interfaces", e);
        }
        
        return extPointInterfaces;
    }

    /**
     * 获取扩展点的完整标识
     * 
     * @param extPointClass 扩展点接口类
     * @return 扩展点的完整类名
     */
    public static String getExtPointIdentifier(Class<?> extPointClass) {
        return extPointClass.getCanonicalName();
    }

    /**
     * 验证是否为有效的扩展点接口
     * 
     * @param clazz 待验证的类
     * @return 是否为有效的扩展点接口
     */
    public static boolean isValidExtPointInterface(Class<?> clazz) {
        return clazz != null && 
               clazz.isInterface() && 
               clazz.isAnnotationPresent(ExtPoint.class) &&
               !Object.class.equals(clazz);
    }
    
    /**
     * 检查是否为扩展点接口（简化版，只检查@ExtPoint注解）
     * 
     * @param clazz 待检查的类
     * @return 是否为扩展点接口
     */
    public static boolean isExtPointInterface(Class<?> clazz) {
        return clazz != null && clazz.isAnnotationPresent(ExtPoint.class);
    }
    
    /**
     * 检查是否为扩展实现类
     * 
     * @param clazz 待检查的类
     * @return 是否为扩展实现类
     */
    public static boolean isExtensionImplementation(Class<?> clazz) {
        if (clazz == null || clazz.isInterface()) {
            return false;
        }
        
        // 检查是否实现了至少一个扩展点接口
        return !findExtPointInterfaces(clazz).isEmpty();
    }

    /**
     * 生成路由键，用于唯一标识扩展点的路由规则
     * 
     * @param extPointClass 扩展点接口类
     * @param tenantCode 租户编码
     * @param bizCode 业务编码
     * @param useCase 用例
     * @param scenario 场景
     * @return 路由键
     */
    public static String generateRouteKey(Class<?> extPointClass, String tenantCode, 
                                         String bizCode, String useCase, String scenario) {
        return getExtPointIdentifier(extPointClass) + ":" + 
               tenantCode + ":" + 
               bizCode + ":" + 
               useCase + ":" + 
               scenario;
    }
}
