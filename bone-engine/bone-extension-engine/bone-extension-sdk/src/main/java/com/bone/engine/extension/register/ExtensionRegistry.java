package com.bone.engine.extension.register;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.event.ExtensionEventPublisher;
import com.bone.engine.extension.repository.ExtPointRepository;
import com.bone.engine.extension.util.ExtensionKeyGenerator;
import com.bone.engine.extension.utils.ExtPointUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 统一的扩展点注册服务
 * 整合所有扩展点注册相关的功能，避免重复实现
 * 
 * @author Bone Engine Team
 * @version 1.0.0
 */
public class ExtensionRegistry {
    
    private static final Logger log = LoggerFactory.getLogger(ExtensionRegistry.class);
    
    private final ExtPointRepository extPointRepository;
    private final ExtensionEventPublisher eventPublisher;
    private final Set<Object> registeredProviders;
    private final Map<Class<?>, List<Object>> implementationsByExtPoint;
    private final Map<Class<?>, Object> defaultImplementations;
    
    /**
     * 构造函数
     * 
     * @param extPointRepository 扩展点仓库
     * @param eventPublisher 扩展事件发布器
     */
    public ExtensionRegistry(ExtPointRepository extPointRepository, 
                            ExtensionEventPublisher eventPublisher) {
        Assert.notNull(extPointRepository, "ExtPointRepository must not be null");
        Assert.notNull(eventPublisher, "ExtensionEventPublisher must not be null");
        
        this.extPointRepository = extPointRepository;
        this.eventPublisher = eventPublisher;
        this.registeredProviders = ConcurrentHashMap.newKeySet();
        this.implementationsByExtPoint = new ConcurrentHashMap<>();
        this.defaultImplementations = new ConcurrentHashMap<>();
    }
    
    /**
     * 注册扩展点实现
     * 
     * @param extPointClass 扩展点接口类
     * @param implementation 扩展点实现实例
     * @param <T> 扩展点类型
     */
    public <T> void registerImplementation(Class<T> extPointClass, T implementation) {
        Assert.notNull(extPointClass, "ExtPoint class must not be null");
        Assert.notNull(implementation, "Implementation must not be null");
        
        // 验证实现类是否实现了扩展点接口
        Assert.isAssignable(extPointClass, implementation.getClass(), 
                           "Implementation must implement the extPoint interface");
        
        // 获取Extension注解
        Extension extension = implementation.getClass().getAnnotation(Extension.class);
        if (extension == null) {
            log.warn("Implementation {} does not have @Extension annotation", 
                     implementation.getClass().getName());
        }
        
        // 添加到实现列表
        implementationsByExtPoint.computeIfAbsent(extPointClass, k -> new ArrayList<>())
                               .add(implementation);
        
        // 检查是否为默认实现
        if (extension != null && extension.isDefault()) {
            // 如果已有默认实现，记录日志
            Object existingDefault = defaultImplementations.put(extPointClass, implementation);
            if (existingDefault != null) {
                log.warn("Replaced default implementation: {} with {} for extPoint: {}",
                         existingDefault.getClass().getSimpleName(),
                         implementation.getClass().getSimpleName(),
                         extPointClass.getSimpleName());
            }
        }
        
        // 注册到仓库
        String registrationKey = ExtensionKeyGenerator.generateExtensionKey(extPointClass, implementation);
        extPointRepository.put(registrationKey, implementation);
        registeredProviders.add(implementation);
        
        // 发布注册事件
        String interfaceName = extPointClass.getCanonicalName();
        String providerClassName = implementation.getClass().getCanonicalName();
        eventPublisher.publishAfterRegister(this, interfaceName, providerClassName);
        
        log.info("Registered implementation: {} for extPoint: {}", 
                 implementation.getClass().getSimpleName(), 
                 extPointClass.getSimpleName());
    }
    
    /**
     * 注册单个扩展提供者（可能实现多个扩展点接口）
     * 
     * @param extProvider 扩展提供者实例
     * @return 是否成功注册
     */
    public boolean registerExtension(Object extProvider) {
        // 参数验证
        Assert.notNull(extProvider, "Extension provider must not be null");
        
        // 检查是否已经注册过
        if (registeredProviders.contains(extProvider)) {
            log.debug("Extension provider already registered: {}", extProvider.getClass().getName());
            return false; // 避免重复注册
        }
        
        final long startTime = System.currentTimeMillis();
        try {
            // 获取实际的类（处理代理对象）
            Class<?> extProviderClass = AopUtils.isAopProxy(extProvider) 
                    ? ClassUtils.getUserClass(extProvider) 
                    : extProvider.getClass();
            
            String providerClassName = extProviderClass.getCanonicalName();
            log.debug("Registering extension provider: {}", providerClassName);
            
            // 检查@Extension注解
            Extension extAnnotation = AnnotationUtils.findAnnotation(extProviderClass, Extension.class);
            if (extAnnotation == null) {
                log.warn("Class {} does not have @Extension annotation, skipping registration", providerClassName);
                return false;
            }
            
            // 获取扩展点接口 - 支持多接口实现
            List<Class<?>> extPointInterfaces = ExtPointUtils.findExtPointInterfaces(extProviderClass);
            if (CollectionUtils.isEmpty(extPointInterfaces)) {
                log.warn("Class {} does not implement any @ExtPoint interfaces, skipping registration", providerClassName);
                return false;
            }
            
            // 为每个扩展点接口注册实现
            boolean registered = false;
            for (Class<?> extPointInterface : extPointInterfaces) {
                // 检查类型兼容性
                if (!extPointInterface.isInstance(extProvider)) {
                    log.warn("Extension provider does not implement the extension point interface: {}", 
                             extPointInterface.getCanonicalName());
                    continue;
                }
                
                @SuppressWarnings("unchecked")
                Class<Object> typedInterface = (Class<Object>) extPointInterface;
                registerImplementation(typedInterface, extProvider);
                registered = true;
            }
            
            long costTime = System.currentTimeMillis() - startTime;
            log.info("Successfully registered extension provider: {} (took {}ms)", 
                    providerClassName, costTime);
            
            return registered;
        } catch (Exception e) {
            log.error("Failed to register extension provider: {}", extProvider.getClass().getName(), e);
            return false;
        }
    }
    
    /**
     * 获取指定扩展点接口的所有实现
     * 
     * @param extPointClass 扩展点接口类
     * @param <T> 扩展点类型
     * @return 实现列表
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getImplementations(Class<T> extPointClass) {
        List<Object> implementations = implementationsByExtPoint.get(extPointClass);
        if (implementations == null) {
            return Collections.emptyList();
        }
        return (List<T>) new ArrayList<>(implementations);
    }
    
    /**
     * 获取指定扩展点接口的所有实现（同getImplementations，为兼容提供）
     * 
     * @param extPointClass 扩展点接口类
     * @param <T> 扩展点类型
     * @return 实现列表
     */
    public <T> List<T> getAllImplementations(Class<T> extPointClass) {
        return getImplementations(extPointClass);
    }
    
    /**
     * 取消注册扩展点实现
     * 
     * @param extPointClass 扩展点接口类
     * @param implementation 扩展点实现实例
     * @param <T> 扩展点类型
     */
    @SuppressWarnings("unchecked")
    public <T> void unregisterImplementation(Class<T> extPointClass, T implementation) {
        Assert.notNull(extPointClass, "ExtPoint class must not be null");
        Assert.notNull(implementation, "Implementation must not be null");
        
        List<Object> implementations = implementationsByExtPoint.get(extPointClass);
        if (!CollectionUtils.isEmpty(implementations)) {
            boolean removed = implementations.remove(implementation);
            if (removed) {
                // 如果是默认实现，清除默认实现记录
                Extension extension = implementation.getClass().getAnnotation(Extension.class);
                if (extension != null && extension.isDefault()) {
                    Object currentDefault = defaultImplementations.get(extPointClass);
                    if (currentDefault == implementation) {
                        defaultImplementations.remove(extPointClass);
                        log.info("Removed default implementation: {} for extPoint: {}",
                                 implementation.getClass().getSimpleName(),
                                 extPointClass.getSimpleName());
                    }
                }
                
                // 从已注册提供者集合中移除
                registeredProviders.remove(implementation);
                
                // 从仓库中移除
                String registrationKey = ExtensionKeyGenerator.generateExtensionKey(extPointClass, implementation);
                extPointRepository.remove(registrationKey);
                
                // 发布取消注册事件
                String interfaceName = extPointClass.getCanonicalName();
                String providerClassName = implementation.getClass().getCanonicalName();
                eventPublisher.publishAfterUnregister(this, interfaceName, providerClassName);
            }
        }
    }
    
    /**
     * 获取指定扩展点接口的默认实现
     * 
     * @param extPointClass 扩展点接口类
     * @param <T> 扩展点类型
     * @return 默认实现或null
     */
    @SuppressWarnings("unchecked")
    public <T> T getDefaultImplementation(Class<T> extPointClass) {
        return (T) defaultImplementations.get(extPointClass);
    }
    
    /**
     * 设置默认实现（运行时覆盖）
     * 
     * @param extPointClass 扩展点接口类
     * @param implementation 扩展点实现实例
     * @param <T> 扩展点类型
     */
    @SuppressWarnings("unchecked")
    public <T> void setDefaultImplementation(Class<T> extPointClass, T implementation) {
        Assert.notNull(extPointClass, "ExtPoint class must not be null");
        Assert.notNull(implementation, "Implementation must not be null");
        
        // 验证实现类是否实现了扩展点接口
        Assert.isAssignable(extPointClass, implementation.getClass(), 
                           "Implementation must implement the extPoint interface");
        
        // 设置默认实现
        Object existingDefault = defaultImplementations.put(extPointClass, implementation);
        if (existingDefault != null) {
            log.info("Overridden default implementation: {} with {} for extPoint: {}",
                     existingDefault.getClass().getSimpleName(),
                     implementation.getClass().getSimpleName(),
                     extPointClass.getSimpleName());
        }
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
     * 获取扩展点数量
     * 
     * @return 已注册的扩展点接口数量
     */
    public int getExtPointCount() {
        return implementationsByExtPoint.size();
    }
    
    /**
     * 获取扩展点的实现统计信息
     * 
     * @return 统计信息映射，键为扩展点接口名称，值为实现数量
     */
    public Map<String, Integer> getImplementationStats() {
        Map<String, Integer> stats = new HashMap<>();
        implementationsByExtPoint.forEach((extPointClass, implementations) -> {
            stats.put(extPointClass.getSimpleName(), implementations.size());
        });
        return stats;
    }
    
    /**
     * 清除注册缓存
     */
    public void clearRegisteredProviders() {
        registeredProviders.clear();
        implementationsByExtPoint.clear();
        defaultImplementations.clear();
        log.info("Cleared extension provider registration cache");
    }
    
    /**
     * 批量注册扩展提供者
     * 
     * @param providers 扩展提供者列表
     * @return 注册结果统计
     */
    public RegistrationResult registerAll(Collection<Object> providers) {
        int total = providers.size();
        int success = 0;
        int failed = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        
        for (Object provider : providers) {
            try {
                if (registerExtension(provider)) {
                    success++;
                } else {
                    failed++;
                    Map<String, Object> failureInfo = new HashMap<>();
                    failureInfo.put("className", provider.getClass().getName());
                    failureInfo.put("error", "Registration rejected");
                    failures.add(failureInfo);
                }
            } catch (Exception e) {
                failed++;
                Map<String, Object> failureInfo = new HashMap<>();
                failureInfo.put("className", provider.getClass().getName());
                failureInfo.put("error", e.getMessage());
                failures.add(failureInfo);
                log.error("Exception during registration of {}", provider.getClass().getName(), e);
            }
        }
        
        return new RegistrationResult(total, success, failed, failures);
    }
    
    /**
     * 注册结果类
     */
    public static class RegistrationResult {
        private final int total;
        private final int success;
        private final int failed;
        private final List<Map<String, Object>> failures;
        
        public RegistrationResult(int total, int success, int failed, List<Map<String, Object>> failures) {
            this.total = total;
            this.success = success;
            this.failed = failed;
            this.failures = failures;
        }
        
        public int getTotal() {
            return total;
        }
        
        public int getSuccess() {
            return success;
        }
        
        public int getFailed() {
            return failed;
        }
        
        public List<Map<String, Object>> getFailures() {
            return failures;
        }
        
        public boolean isSuccess() {
            return failed == 0;
        }
    }
}