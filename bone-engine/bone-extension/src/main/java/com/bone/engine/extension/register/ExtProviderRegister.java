package com.bone.engine.extension.register;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.ExtProvider;
import com.bone.engine.extension.repository.ExtPointRepository;
import com.bone.engine.extension.repository.ExtPointRepositoryFactory;
import com.bone.engine.extension.spec.ExtProviderSpec;
import com.bone.core.util.ReflectionUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展提供者注册器，负责注册和管理所有的扩展实现
 *
 * @author renhui.trh 2023-10-30
 */
@Slf4j
@Component
public class ExtProviderRegister implements ApplicationContextAware {

    private ApplicationContext applicationContext;
    // 缓存已注册的扩展提供者，避免重复注册
    private final Map<String, Object> registeredProviders = new ConcurrentHashMap<>();

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
        } catch (Exception e) {
            log.error("Failed to initialize extension provider registration", e);
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
        
        // 构建扩展提供者规格
        ExtProviderSpec extProviderSpec = ExtProviderSpec.builder()
                .tenantCode(extAnnotation.tenantCode())
                .bizCode(extAnnotation.bizCode())
                .useCase(extAnnotation.useCase())
                .scenario(extAnnotation.scenario())
                .expression(extAnnotation.expression())
                .build();
        
        // 获取扩展点接口
        Class<?> extPointClass = ReflectionUtil.getInterfaceByAnnotation(extProviderClass, ExtPoint.class);
        if (extPointClass == null) {
            throw new IllegalStateException("Extension provider must implement an interface annotated with @ExtPoint: " + providerClassName);
        }
        
        String interfaceName = extPointClass.getCanonicalName();
        
        // 获取或创建扩展点仓库
        ExtPointRepository extProviderRepo = ExtPointRepositoryFactory.createExtPointRepository();
        if (extProviderRepo == null) {
            throw new IllegalStateException("Failed to create ExtPointRepository");
        }
        
        // 检查类型兼容性
        if (!extPointClass.isInstance(extProvider)) {
            throw new IllegalArgumentException("Extension provider does not implement the extension point interface: " + interfaceName);
        }
        
        // 注册扩展提供者
        if (!StringUtils.hasText(extProviderSpec.getExpression())) {
            // 基于业务标识的注册
            String registryKey = interfaceName + "." + extProviderSpec.getBizIdentity();
            
            // 检查重复注册
            if (registeredProviders.containsKey(registryKey)) {
                Object existingProvider = registeredProviders.get(registryKey);
                String errorMsg = String.format("Duplicate extension registration for key [%s]: existing=%s, new=%s",
                        registryKey, existingProvider.getClass().getSimpleName(), providerClassName);
                log.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
            
            Object preVal = extProviderRepo.put(registryKey, extProvider);
            if (preVal != null) {
                String errorMsg = String.format("Duplicate registration detected in repository for key [%s]: %s", 
                        registryKey, providerClassName);
                log.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
            
            registeredProviders.put(registryKey, extProvider);
            log.debug("Registered extension provider with key: {}", registryKey);
        } else {
            // 基于表达式的注册
            String expressionListKey = interfaceName + ".expression.extProviderList";
            List<Object> extProviderList = (List<Object>) extProviderRepo.get(expressionListKey);
            
            if (extProviderList == null) {
                extProviderList = new ArrayList<>();
                extProviderRepo.put(expressionListKey, extProviderList);
            }
            
            // 检查是否已注册
            for (Object existingProvider : extProviderList) {
                if (existingProvider.getClass().getCanonicalName().equals(providerClassName)) {
                    log.warn("Extension provider already registered by expression: {}", providerClassName);
                    return;
                }
            }
            
            extProviderList.add(extProvider);
            log.debug("Registered expression-based extension provider: {} with expression: {}",
                    providerClassName, extAnnotation.expression());
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
     * 清除注册缓存
     */
    public void clearRegisteredProviders() {
        registeredProviders.clear();
        log.info("Cleared extension provider registration cache");
    }
}
