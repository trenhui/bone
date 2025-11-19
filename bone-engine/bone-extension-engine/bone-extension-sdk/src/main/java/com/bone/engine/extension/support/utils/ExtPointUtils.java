package com.bone.engine.extension.support.utils;

import com.bone.engine.extension.api.annotation.ExtPoint;
import com.bone.engine.extension.api.annotation.Extension;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.core.router.CacheManager;
import com.bone.engine.extension.core.router.RouteKey;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 扩展点相关的通用工具类
 * 提供扩展点接口和实现类的查找、验证等通用功能
 * 
 * @author renhui.trh
 * @since 1.0.0
 */
public class ExtPointUtils implements ApplicationContextAware {

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

    // 使用统一的缓存管理器
    private static ApplicationContext applicationContext;
    private static CacheManager cacheManager;
    
    // 缓存键前缀，用于区分不同类型的缓存
    private static final String EXT_POINT_NAME_KEY_PREFIX = "extPointName:";
    private static final String EXTENSION_ID_KEY_PREFIX = "extensionId:";
    
    @Override
    public void setApplicationContext(ApplicationContext context) {
        ExtPointUtils.applicationContext = context;
    }
    
    /**
     * 获取缓存管理器实例
     */
    private static CacheManager getCacheManager() {
        if (cacheManager == null) {
            // 如果ApplicationContext已初始化，从容器中获取CacheManager
            if (applicationContext != null) {
                try {
                    cacheManager = applicationContext.getBean(CacheManager.class);
                } catch (Exception e) {
                    // 如果容器中没有CacheManager，创建一个默认实例作为回退
                    cacheManager = new CacheManager();
                    try {
                        cacheManager.initialize();
                    } catch (Exception ex) {
                        throw new RuntimeException("Failed to initialize fallback CacheManager", ex);
                    }
                }
            } else {
                // ApplicationContext尚未初始化时，创建默认实例作为回退
                cacheManager = new CacheManager();
                try {
                    cacheManager.initialize();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to initialize fallback CacheManager", e);
                }
            }
        }
        return cacheManager;
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
     * 获取扩展点名称
     */
    public static String getExtPointName(Class<?> extPointInterface) {
        // 使用统一缓存管理器减少重复计算
        String cacheKey = EXT_POINT_NAME_KEY_PREFIX + extPointInterface.getName();
        return getCacheManager().getFromCache(cacheKey, key -> {
            // 获取@ExtPoint注解
            ExtPoint extPoint = extPointInterface.getAnnotation(ExtPoint.class);
            if (extPoint != null && StringUtils.hasText(extPoint.name())) {
                return extPoint.name();
            }
            return extPointInterface.getSimpleName();
        });
    }

    /**
     * 获取扩展点实现标识
     */
    public static String getExtensionId(Object extensionImpl) {
        // 使用统一缓存管理器减少重复计算
        String cacheKey = EXTENSION_ID_KEY_PREFIX + extensionImpl.getClass().getName() + ":" + extensionImpl.hashCode();
        return getCacheManager().getFromCache(cacheKey, key -> {
            Extension extension = extensionImpl.getClass().getAnnotation(Extension.class);
            if (extension != null && StringUtils.hasText(extension.name())) {
                return extension.name();
            }
            return extensionImpl.getClass().getSimpleName();
        });
    }
    
    /**
     * 检查是否是有效的扩展点实现
     */
    public static boolean isValidExtensionImplementation(Object impl) {
        if (impl == null) {
            return false;
        }
        
        Class<?> clazz = impl.getClass();
        if (!clazz.isAnnotationPresent(Extension.class)) {
            return false;
        }
        
        // 检查是否实现了至少一个带@ExtPoint注解的接口
        for (Class<?> ifc : clazz.getInterfaces()) {
            if (ifc.isAnnotationPresent(ExtPoint.class)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 获取扩展点接口的实现类
     */
    public static Class<?> getExtPointInterface(Object extensionImpl) {
        if (extensionImpl == null) {
            return null;
        }
        
        for (Class<?> ifc : extensionImpl.getClass().getInterfaces()) {
            if (ifc.isAnnotationPresent(ExtPoint.class)) {
                return ifc;
            }
        }
        
        return null;
    }
    
    /**
     * 从方法参数中提取业务上下文
     */
    public static <T> BizContext<T> extractBizContext(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        
        for (Object arg : args) {
            if (arg instanceof BizContext) {
                return (BizContext<T>) arg;
            }
        }
        
        return null;
    }
    
    /**
     * 确保业务上下文存在
     */
    public static <T> BizContext<T> ensureBizContext(Object[] args) {
        // 尝试从参数中提取BizContext
        BizContext<T> context = extractBizContext(args);
        
        // 如果没有找到上下文，创建一个默认的BizContext
        if (context == null) {
            context = BizContext.createEmpty();
        }
        
        return context;
    }
    
    /**
     * 生成方法签名字符串
     */
    public static String getMethodSignature(Method method) {
        StringBuilder signature = new StringBuilder(method.getName()).append("(");
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            signature.append(paramTypes[i].getSimpleName());
            if (i < paramTypes.length - 1) {
                signature.append(", ");
            }
        }
        signature.append(")");
        return signature.toString();
    }
    
    /**
     * 检查两个业务上下文是否匹配路由条件
     */
    public static <T, U> boolean matchContext(BizContext<T> context1, BizContext<U> context2) {
        if (context1 == null || context2 == null) {
            return false;
        }
        
        // 业务代码匹配
        if (context1.getBizCode() != null && !context1.getBizCode().equals(context2.getBizCode())) {
            return false;
        }
        
        // 租户代码匹配
        if (context1.getTenantCode() != null && !context1.getTenantCode().equals(context2.getTenantCode())) {
            return false;
        }
        
        // 场景匹配
        if (context1.getScenario() != null && !context1.getScenario().equals(context2.getScenario())) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 计算路由匹配得分
     */
    public static int calculateMatchScore(Extension extension, BizContext<?> context) {
        int score = 0;
        
        // 检查业务代码匹配
        if (Arrays.asList(extension.bizCode()).contains(context.getBizCode())) {
            score += 100;
        }
        
        // 检查租户代码匹配
        if (Arrays.asList(extension.tenantCode()).contains(context.getTenantCode())) {
            score += 80;
        }
        
        // 检查场景匹配
        if (Arrays.asList(extension.scenario()).contains(context.getScenario())) {
            score += 60;
        }
        
        // 优先级权重
        score += extension.priority() * 10;
        
        return score;
    }
    
    /**
     * 清除所有缓存
     */
    public static void clearCache() {
        // 使用统一的缓存管理器清理缓存
        getCacheManager().clearAllCache();
    }

    /**
     * 生成路由键，用于唯一标识扩展点的路由规则
     * 
     * @param extPointClass 扩展点接口类
     * @param tenantCode 租户编码
     * @param bizCode 业务编码
     * @param useCase 用例
     * @param scenario 场景
     * @return 路由键字符串
     */
    public static String generateRouteKey(Class<?> extPointClass, String tenantCode, 
                                         String bizCode, String useCase, String scenario) {
        // 使用RouteKey类创建路由键对象，然后返回其字符串表示
        RouteKey routeKey = new RouteKey(extPointClass, tenantCode, bizCode, useCase, scenario, null, null);
        return routeKey.toString();
    }
    
    /**
     * 生成路由键
     */
    public static <T> String generateRouteKey(Class<?> extPointInterface, BizContext<T> context) {
        StringBuilder key = new StringBuilder(getExtPointName(extPointInterface));
        
        if (context != null) {
            key.append(":")
               .append(context.getBizCode() != null ? context.getBizCode() : "default")
               .append(":")
               .append(context.getTenantCode() != null ? context.getTenantCode() : "default")
               .append(":")
               .append(context.getScenario() != null ? context.getScenario() : "default")
               .append(":")
               .append("default")
               .append(":")
               .append("DEFAULT"); // 暂时不调用getGroup()方法
        }
        
        return key.toString();
    }
    
    /**
     * 创建路由键对象
     * 
     * @param extPointClass 扩展点接口类
     * @param tenantCode 租户编码
     * @param bizCode 业务编码
     * @param useCase 用例
     * @param scenario 场景
     * @param env 环境
     * @param group 分组
     * @return 路由键对象
     */
    public static RouteKey createRouteKey(Class<?> extPointClass, String tenantCode, 
                                         String bizCode, String useCase, String scenario, 
                                         String env, String group) {
        return new RouteKey(extPointClass, tenantCode, bizCode, useCase, scenario, env, group);
    }
}
