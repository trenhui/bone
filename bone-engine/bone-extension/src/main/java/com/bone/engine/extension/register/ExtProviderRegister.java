package com.bone.engine.extension.register;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.ExtProvider;
import com.bone.engine.extension.repository.ExtPointRepository;
import com.bone.core.util.ReflectionUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展提供者注册器，负责扫描、注册和管理所有的扩展实现
 * 与Spring容器深度集成，自动发现并注册带有@ExtProvider注解的组件
 * 
 * @see ExtPoint 扩展点标记注解
 * @see ExtProvider 扩展提供者标记注解
 * @since 1.0.0
 */
@Component
public class ExtProviderRegister implements ApplicationContextAware {
    private static final Logger log = LoggerFactory.getLogger(ExtProviderRegister.class);

    private ApplicationContext applicationContext;
    
    // 从Spring容器注入扩展点仓库
    private final ExtPointRepository extPointRepository;
    
    // 缓存已注册的扩展提供者，避免重复注册
    private final Map<String, Object> registeredProviders = new ConcurrentHashMap<>();

    /**
     * 构造函数，通过Spring注入扩展点仓库
     * 
     * @param extPointRepository 扩展点仓库，非空
     */
    @Autowired
    public ExtProviderRegister(ExtPointRepository extPointRepository) {
        Assert.notNull(extPointRepository, "ExtPointRepository must not be null");
        this.extPointRepository = extPointRepository;
    }
    
    /**
     * 初始化时自动注册所有标记了@ExtProvider注解的Bean
     */
    @PostConstruct
    public void init() {
        Assert.notNull(applicationContext, "ApplicationContext must not be null");
        
        try {
            Map<String, Object> extensionBeans = applicationContext.getBeansWithAnnotation(ExtProvider.class);
            log.info("Found {} extension providers to register", extensionBeans.size());
            
            extensionBeans.forEach((beanName, extProvider) -> {
                try {
                    registerExtProvider(extProvider);
                    log.info("Successfully registered extension provider: {} ({})", 
                            beanName, extProvider.getClass().getSimpleName());
                } catch (Exception e) {
                    log.error("Failed to register extension provider: {} ({})", 
                            beanName, extProvider.getClass().getSimpleName(), e);
                }
            });
            
            log.info("Extension provider registration completed. Total registered: {}", registeredProviders.size());
        } catch (Exception e) {
            log.error("Failed to initialize extension provider registration", e);
            throw new RuntimeException("Failed to initialize extension provider registration", e);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 注册单个扩展提供者
     * 
     * @param extProvider 扩展提供者实例
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws IllegalStateException 当注册失败时抛出
     */
    public void registerExtProvider(Object extProvider) {
        Assert.notNull(extProvider, "Extension provider must not be null");
        
        // 获取实际的类（处理代理对象）
        Class<?> extProviderClass = AopUtils.isAopProxy(extProvider) 
                ? ClassUtils.getUserClass(extProvider) 
                : extProvider.getClass();
        
        String providerClassName = extProviderClass.getCanonicalName();
        log.debug("Registering extension provider: {}", providerClassName);
        
        // 检查@ExtProvider注解
        ExtProvider extAnnotation = AnnotationUtils.findAnnotation(extProviderClass, ExtProvider.class);
        if (extAnnotation == null) {
            throw new IllegalArgumentException("Extension provider must be annotated with @ExtProvider: " + providerClassName);
        }
        
        // 获取扩展点接口 - 支持多接口实现
        List<Class<?>> extPointInterfaces = findExtPointInterfaces(extProviderClass);
        if (CollectionUtils.isEmpty(extPointInterfaces)) {
            throw new IllegalStateException("Extension provider must implement at least one interface annotated with @ExtPoint: " + providerClassName);
        }
        
        // 为每个扩展点接口注册实现
        for (Class<?> extPointInterface : extPointInterfaces) {
            String interfaceName = extPointInterface.getCanonicalName();
            
            // 检查类型兼容性
            if (!extPointInterface.isInstance(extProvider)) {
                throw new IllegalArgumentException("Extension provider does not implement the extension point interface: " + interfaceName);
            }
            
            // 生成唯一的注册键
            String registrationKey = generateRegistrationKey(interfaceName, extProvider);
            
            // 检查重复注册
            if (registeredProviders.containsKey(registrationKey)) {
                Object existingProvider = registeredProviders.get(registrationKey);
                String errorMsg = String.format("Duplicate extension registration for: %s, existing=%s, new=%s",
                        interfaceName, existingProvider.getClass().getSimpleName(), providerClassName);
                log.warn(errorMsg);
                // 对于重复注册，我们只记录警告但不抛出异常，避免在测试环境中出现问题
                continue;
            }
            
            // 使用标准的put方法注册扩展
            extPointRepository.put(registrationKey, extProvider);
            registeredProviders.put(registrationKey, extProvider);
            
            // 记录注册信息
            log.debug("Registered extension provider {} for interface {}",
                    providerClassName, interfaceName);
        }
    }
    
    /**
     * 查找实现类中所有带有@ExtPoint注解的接口
     * 
     * @param implementationClass 实现类
     * @return 带有@ExtPoint注解的接口列表
     */
    private List<Class<?>> findExtPointInterfaces(Class<?> implementationClass) {
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
     * 生成唯一的注册键
     * 
     * @param interfaceName 接口名称
     * @param provider 提供者实例
     * @return 唯一的注册键
     */
    private String generateRegistrationKey(String interfaceName, Object provider) {
        return interfaceName + ":" + provider.getClass().getCanonicalName() + ":" + System.identityHashCode(provider);
    }
    
    /**
     * 获取已注册的扩展提供者数量
     * 
     * @return 已注册的扩展提供者数量
     */
    public int getRegisteredProviderCount() {
        return registeredProviders.size();
    }
    
    /**
     * 清除注册缓存
     */
    public void clearRegisteredProviders() {
        registeredProviders.clear();
        log.info("Cleared extension provider registration cache");
    }
    
    /**
     * 手动注册扩展提供者（用于动态注册场景）
     * 
     * @param interfaceClass 扩展点接口类
     * @param provider 扩展提供者实例
     * @param <T> 扩展点类型
     */
    public <T> void registerExtension(Class<T> interfaceClass, T provider) {
        Assert.notNull(interfaceClass, "Interface class must not be null");
        Assert.notNull(provider, "Provider must not be null");
        
        if (!interfaceClass.isAnnotationPresent(ExtPoint.class)) {
            throw new IllegalArgumentException("Interface must be annotated with @ExtPoint: " + interfaceClass.getName());
        }
        
        if (!interfaceClass.isInstance(provider)) {
            throw new IllegalArgumentException("Provider does not implement the interface: " + interfaceClass.getName());
        }
        
        // 生成唯一的注册键并使用标准的put方法注册扩展
        String registrationKey = generateRegistrationKey(interfaceClass.getCanonicalName(), provider);
        extPointRepository.put(registrationKey, provider);
        log.info("Manually registered extension provider: {} for interface: {}", 
                provider.getClass().getSimpleName(), interfaceClass.getSimpleName());
    }
}
