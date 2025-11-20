package com.bone.engine.extension.support.validator;

import com.bone.engine.extension.api.annotation.ExtPoint;
import com.bone.engine.extension.api.annotation.Extension;
import com.bone.engine.extension.support.repository.ExtPointRepository;
import com.bone.engine.extension.support.expression.ExpressionEvaluator;
import com.bone.engine.extension.support.utils.ExtPointUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展点启动时校验器，负责在应用启动时对扩展点进行全面的健康检查
 * <p>
 * 提供以下校验功能：
 * 1. 检查每个扩展点是否至少有一个默认实现
 * 2. 验证表达式语法的正确性
 * 3. 检查重复的路由规则
 * 4. 生成扩展点健康报告
 *
 * @author renhui.trh
 * @since 1.0.0
 */
@Component
public class ExtPointValidator implements ApplicationListener<ContextRefreshedEvent>, InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(ExtPointValidator.class);
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private ExtensionRegister extensionRegister;
    
    @Autowired
    private ExtPointRepository extPointRepository;
    

    
    // 存储扩展点健康检查报告
    private final Map<String, ExtensionPointHealth> healthReport = new ConcurrentHashMap<>();
    
    /**
     * 扩展点健康检查结果
     */
    private static class ExtensionPointHealth {
        private final String interfaceName;
        private boolean hasDefaultImplementation;
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        
        public ExtensionPointHealth(String interfaceName) {
            this.interfaceName = interfaceName;
        }
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        // 初始化时进行预热
        log.info("Extension point validator initialized");
    }
    
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 确保只执行一次校验
        if (event.getApplicationContext().getParent() == null) {
            validateAllExtensionPoints();
            generateHealthReport();
        }
    }
    
    /**
     * 校验所有扩展点
     */
    public void validateAllExtensionPoints() {
        log.info("Starting extension point validation...");
        
        try {
            // 获取所有标记了@ExtPoint的接口
            Set<Class<?>> extPointInterfaces = findAllExtPointInterfaces();
            
            for (Class<?> extPointInterface : extPointInterfaces) {
                validateExtensionPoint(extPointInterface);
            }
            
            // 检查表达式语法
            validateExpressions();
            
            // 检查重复路由规则
            checkDuplicateRoutes();
            
        } catch (Exception e) {
            log.error("Failed to validate extension points", e);
        }
    }
    
    /**
     * 查找所有标记了@ExtPoint的接口
     */
    private Set<Class<?>> findAllExtPointInterfaces() {
        Set<Class<?>> extPointInterfaces = new HashSet<>();
        
        try {
            extPointInterfaces = ExtPointUtils.findAllExtPointInterfaces(applicationContext);
        } catch (Exception e) {
            log.error("Failed to find extpoint interfaces", e);
        }
        
        return extPointInterfaces;
    }
    
    /**
     * 校验单个扩展点
     */
    private void validateExtensionPoint(Class<?> extPointInterface) {
        String interfaceName = extPointInterface.getCanonicalName();
        ExtensionPointHealth health = new ExtensionPointHealth(interfaceName);
        healthReport.put(interfaceName, health);
        
        log.debug("Validating extension point: {}", interfaceName);
        
        // 1. 检查接口方法的可见性和参数
        validateInterfaceMethods(extPointInterface, health);
        
        // 2. 检查是否有默认实现
        boolean hasDefaultImpl = checkDefaultImplementation(extPointInterface);
        health.hasDefaultImplementation = hasDefaultImpl;
        
        if (!hasDefaultImpl) {
            health.errors.add("No default implementation found for extension point");
            log.warn("No default implementation found for extension point: {}", interfaceName);
        }
    }
    
    /**
     * 校验接口方法
     */
    private void validateInterfaceMethods(Class<?> extPointInterface, ExtensionPointHealth health) {
        Method[] methods = extPointInterface.getDeclaredMethods();
        
        for (Method method : methods) {
            // 检查方法可见性
            if (!Modifier.isPublic(method.getModifiers())) {
                health.errors.add("Method must be public: " + method.getName());
                log.warn("Method is not public: {}.{}", extPointInterface.getName(), method.getName());
            }
            
            // 检查是否有过于复杂的参数列表
            if (method.getParameterCount() > 5) {
                health.warnings.add("Method has too many parameters (consider using builder pattern): " + method.getName());
                log.debug("Method has many parameters: {}.{}", extPointInterface.getName(), method.getName());
            }
        }
    }
    
    /**
     * 检查是否有默认实现
     */
    private boolean checkDefaultImplementation(Class<?> extPointInterface) {
        String interfaceName = ExtPointUtils.getExtPointIdentifier(extPointInterface);
        
        // 简化实现：检查是否有任何实现类
        Map<String, ?> beans = applicationContext.getBeansOfType(extPointInterface);
        return !beans.isEmpty();
    }
    
    /**
     * 验证表达式语法
     */
    private void validateExpressions() {
        // 获取所有标记了@Extension注解的Bean
        Map<String, Object> extensionBeans = applicationContext.getBeansWithAnnotation(Extension.class);
        
        for (Map.Entry<String, Object> entry : extensionBeans.entrySet()) {
            Object bean = entry.getValue();
            Extension extension = AnnotationUtils.findAnnotation(bean.getClass(), Extension.class);
            
            // 检查扩展注解配置
        if (extension != null) {
            // 注意：如果Extension注解没有condition方法，需要根据实际注解属性调整验证逻辑
            try {
                // 尝试获取并验证condition（如果存在）
                Method conditionMethod = Extension.class.getMethod("condition");
                String condition = (String) conditionMethod.invoke(extension);
                if (condition != null && !condition.isEmpty()) {
                    // 验证表达式语法
                    ExpressionEvaluator.evaluateWithCurrentContext(condition);
                }
            } catch (NoSuchMethodException e) {
                // Extension注解没有condition方法，跳过表达式验证
                log.debug("Extension annotation does not have condition method, skipping expression validation");
            } catch (Exception e) {
                log.error("Failed to validate extension: {}", bean.getClass().getName(), e);
            }
        }
        }
    }
    
    /**
     * 检查重复的路由规则
     */
    private void checkDuplicateRoutes() {
        Map<String, List<Object>> routeMap = new HashMap<>();
        
        // 获取所有标记了@Extension注解的Bean
        Map<String, Object> extensionBeans = applicationContext.getBeansWithAnnotation(Extension.class);
        
        for (Object bean : extensionBeans.values()) {
            Extension extension = AnnotationUtils.findAnnotation(bean.getClass(), Extension.class);
            
            if (extension != null) {
                // 获取所有实现的扩展点接口
                Class<?>[] interfaces = bean.getClass().getInterfaces();
                
                for (Class<?> iface : interfaces) {
                    if (iface.isAnnotationPresent(ExtPoint.class)) {
                        String routeKey = ExtPointUtils.generateRouteKey(
                                        iface,
                                        extension.tenantCode(),
                                        extension.bizCode(),
                                        extension.useCase(),
                                        extension.scenario());
                        
                        routeMap.computeIfAbsent(routeKey, k -> new ArrayList<>()).add(bean);
                    }
                }
            }
        }
        
        // 检查重复路由
        for (Map.Entry<String, List<Object>> entry : routeMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                log.warn("Duplicate route configuration detected for key: {}", entry.getKey());
                for (Object bean : entry.getValue()) {
                    log.warn("  - {}", bean.getClass().getName());
                }
            }
        }
    }
    
    /**
     * 生成健康报告
     */
    private void generateHealthReport() {
        int totalExtensions = healthReport.size();
        int healthyExtensions = 0;
        int warningExtensions = 0;
        int errorExtensions = 0;
        
        for (ExtensionPointHealth health : healthReport.values()) {
            if (CollectionUtils.isEmpty(health.errors)) {
                if (CollectionUtils.isEmpty(health.warnings)) {
                    healthyExtensions++;
                } else {
                    warningExtensions++;
                }
            } else {
                errorExtensions++;
            }
        }
        
        log.info("Extension Point Health Report:");
        log.info("  Total: {}", totalExtensions);
        log.info("  Healthy: {}", healthyExtensions);
        log.info("  Warnings: {}", warningExtensions);
        log.info("  Errors: {}", errorExtensions);
        
        // 打印有错误的扩展点
        if (errorExtensions > 0) {
            log.warn("Extensions with errors:");
            for (ExtensionPointHealth health : healthReport.values()) {
                if (!CollectionUtils.isEmpty(health.errors)) {
                    log.warn("  - {}:", health.interfaceName);
                    for (String error : health.errors) {
                        log.warn("    * {}", error);
                    }
                }
            }
        }
    }
    
    /**
     * 获取健康报告（用于监控或管理接口）
     */
    public Map<String, Object> getHealthReport() {
        Map<String, Object> report = new HashMap<>();
        report.put("total", healthReport.size());
        report.put("details", healthReport);
        return report;
    }
}