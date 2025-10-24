package com.bone.engine.extension.metadata;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.ExtPointConstants;
import com.bone.engine.extension.repository.ExtPointRepository;
import com.bone.engine.extension.utils.ExtPointUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 默认扩展点元数据服务实现
 * <p>
 * 基于Spring容器和反射机制收集扩展点元数据
 * </p>
 * 
 * @since 1.0.0
 */
public class DefaultExtPointMetadataService implements ExtPointMetadataService, ApplicationContextAware, InitializingBean {
    
    private ApplicationContext applicationContext;
    private ExtPointRepository extPointRepository;
    private final Map<String, ExtPointMetadata> metadataCache = new ConcurrentHashMap<>();
    
    public DefaultExtPointMetadataService(ExtPointRepository extPointRepository) {
        this.extPointRepository = extPointRepository;
    }
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        // 初始化时收集元数据
        refreshMetadata();
    }
    
    @Override
    public List<ExtPointMetadata> getAllExtPointMetadata() {
        return new ArrayList<>(metadataCache.values());
    }
    
    @Override
    public Optional<ExtPointMetadata> getExtPointMetadata(String interfaceName) {
        return Optional.ofNullable(metadataCache.get(interfaceName));
    }
    
    @Override
    public List<ExtPointMetadata> findExtPointMetadata(Map<String, String> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return getAllExtPointMetadata();
        }
        
        return metadataCache.values().stream()
            .filter(metadata -> matchesCriteria(metadata, criteria))
            .collect(Collectors.toList());
    }
    
    private boolean matchesCriteria(ExtPointMetadata metadata, Map<String, String> criteria) {
        for (Map.Entry<String, String> entry : criteria.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            switch (key) {
                case "category":
                    if (!value.equals(metadata.getCategory())) {
                        return false;
                    }
                    break;
                case "tag":
                    if (metadata.getTags() == null || !metadata.getTags().contains(value)) {
                        return false;
                    }
                    break;
                case "owner":
                    if (!value.equals(metadata.getOwner())) {
                        return false;
                    }
                    break;
                case "tenantCode":
                    // 检查是否有匹配该租户的实现
                    if (!metadata.getImplementations().stream()
                            .anyMatch(impl -> value.equals(impl.getTenantCode()))) {
                        return false;
                    }
                    break;
                default:
                    // 其他条件
                    break;
            }
        }
        return true;
    }
    
    @Override
    public Optional<ExtensionImplMetadata> getExtensionImplMetadata(String interfaceName, String implClassName) {
        ExtPointMetadata metadata = metadataCache.get(interfaceName);
        if (metadata == null) {
            return Optional.empty();
        }
        
        return metadata.getImplementations().stream()
            .filter(impl -> impl.getImplClassName().equals(implClassName))
            .findFirst();
    }
    
    @Override
    public synchronized void refreshMetadata() {
        metadataCache.clear();
        
        // 扫描所有带有@ExtPoint注解的接口
        Map<String, Object> extPointBeans = applicationContext.getBeansWithAnnotation(ExtPoint.class);
        
        for (Map.Entry<String, Object> entry : extPointBeans.entrySet()) {
            Object bean = entry.getValue();
            Class<?> beanClass = bean.getClass();
            // 获取原始接口类型
            Class<?>[] interfaces = beanClass.getInterfaces();
            
            for (Class<?> interfaceClass : interfaces) {
                if (ExtPointUtils.isExtPointInterface(interfaceClass)) {
                    collectExtPointMetadata(interfaceClass);
                    break;
                }
            }
        }
    }
    
    private void collectExtPointMetadata(Class<?> extPointClass) {
        ExtPointMetadata metadata = new ExtPointMetadata();
        metadata.setInterfaceName(extPointClass.getName());
        metadata.setInterfaceSimpleName(extPointClass.getSimpleName());
        
        // 从@ExtPoint注解中提取元数据
        ExtPoint extPoint = AnnotationUtils.findAnnotation(extPointClass, ExtPoint.class);
        if (extPoint != null) {
            metadata.setDescription(extPoint.description());
                metadata.setOwner(""); // 使用默认值
                metadata.setDocumentationUrl(""); // 使用默认值
            metadata.setCategory(extPoint.category());
            metadata.setTags(Collections.emptyList()); // ExtPoint没有tags()方法
            metadata.setDeprecated(!extPoint.deprecatedSince().isEmpty()); // 使用deprecatedSince作为判断依据
            metadata.setDeprecatedSince(extPoint.deprecatedSince());
            metadata.setReplacement(""); // ExtPoint没有replacement()方法
        } else {
            // 从JavaDoc提取描述作为备选
            metadata.setDescription(extractDescriptionFromJavadoc(extPointClass));
            metadata.setCategory("default");
            metadata.setTags(Collections.emptyList());
        }
        
        // 收集方法信息
        List<ExtPointMethodMetadata> methodMetadataList = new ArrayList<>();
        for (Method method : extPointClass.getDeclaredMethods()) {
            ExtPointMethodMetadata methodMetadata = collectMethodMetadata(method);
            methodMetadataList.add(methodMetadata);
        }
        metadata.setMethods(methodMetadataList);
        
        // 收集实现信息
        List<ExtensionImplMetadata> implMetadataList = collectImplementationMetadata(extPointClass);
        metadata.setImplementations(implMetadataList);
        metadata.setImplementationCount(implMetadataList.size());
        
        metadata.setLastModifiedTime(new Date().toString());
        
        metadataCache.put(extPointClass.getName(), metadata);
    }
    
    private ExtPointMethodMetadata collectMethodMetadata(Method method) {
        ExtPointMethodMetadata methodMetadata = new ExtPointMethodMetadata();
        methodMetadata.setMethodName(method.getName());
        methodMetadata.setReturnType(method.getReturnType().getName());
        
        // 收集参数信息
        List<String> paramTypes = new ArrayList<>();
        List<String> paramNames = new ArrayList<>();
        for (Class<?> paramType : method.getParameterTypes()) {
            paramTypes.add(paramType.getName());
            paramNames.add("arg" + paramTypes.size()); // 简化实现，实际应使用参数名解析
        }
        methodMetadata.setParamTypes(paramTypes);
        methodMetadata.setParamNames(paramNames);
        
        // 收集异常信息
        List<String> exceptions = Arrays.stream(method.getExceptionTypes())
            .map(Class::getName)
            .collect(Collectors.toList());
        methodMetadata.setExceptions(exceptions);
        
        // 从JavaDoc提取描述
        methodMetadata.setDescription(extractDescriptionFromJavadoc(method));
        
        return methodMetadata;
    }
    
    private List<ExtensionImplMetadata> collectImplementationMetadata(Class<?> extPointClass) {
        List<ExtensionImplMetadata> implMetadataList = new ArrayList<>();
        
        // 从ApplicationContext中查找所有实现了该接口的Bean
        Map<String, ?> implementations = applicationContext.getBeansOfType(extPointClass);
        
        for (Object impl : implementations.values()) {
            Class<?> implClass = impl.getClass();
            // 跳过代理类
            if (ClassUtils.isCglibProxyClass(implClass)) {
                continue;
            }
            
            Extension extension = AnnotationUtils.findAnnotation(implClass, Extension.class);
            if (extension != null && ExtPointUtils.isExtensionImplementation(implClass)) {
                ExtensionImplMetadata implMetadata = new ExtensionImplMetadata();
                implMetadata.setImplClassName(implClass.getName());
                implMetadata.setImplSimpleName(implClass.getSimpleName());
                
                // 设置路由配置信息
            implMetadata.setTenantCode(extension.tenantCode()); // 假设需要字符串
            implMetadata.setBizCode(extension.bizCode()); // 使用字符串
                implMetadata.setUseCase(extension.useCase());
                implMetadata.setScenario(extension.scenario());
                implMetadata.setExpression(""); // Extension没有expression()方法
            
            // 设置版本信息
            implMetadata.setVersion(extension.version());
            implMetadata.setCompatibleWith(new String[0]); // Extension没有compatibleWith()方法，使用空数组
                
                // 从@Extension注解中提取新添加的元数据
                implMetadata.setDescription(extension.description());
                implMetadata.setAuthor(extension.author());
                implMetadata.setDefault(extension.isDefault() || ExtPointConstants.DEFAULT_VALUE.equals(extension.bizCode()));
                // 不再重复设置已经设置过的字段
                implMetadata.setRecommended(false); // 使用默认值
                implMetadata.setPriority(String.valueOf(extension.priority()));
                implMetadata.setDependencies(new String[0]); // 使用默认值
                
                // 使用空的属性映射
                Map<String, String> propertiesMap = new HashMap<>();
                implMetadata.setProperties(propertiesMap);
                
                // 设置时间信息
                implMetadata.setCreateTime(new Date().toString());
                implMetadata.setLastUpdateTime(new Date().toString());
                
                implMetadataList.add(implMetadata);
            }
        }
        
        return implMetadataList;
    }
    
    private String extractDescriptionFromJavadoc(Class<?> clazz) {
        // 简化实现，实际应解析JavaDoc
        String comment = clazz.getSimpleName();
        if (StringUtils.hasText(clazz.getSimpleName())) {
            return clazz.getSimpleName();
        }
        return "";
    }
    
    private String extractDescriptionFromJavadoc(Method method) {
        // 简化实现，实际应解析JavaDoc
        return method.getName();
    }
    
    @Override
    public boolean updateExtensionRoutingConfig(String interfaceName, String implClassName, Map<String, String> routingConfig) {
        // 简化实现，实际应更新扩展实现的路由配置
        // 这里只是演示，实际需要与路由机制集成
        return false;
    }
    
    @Override
    public Map<String, ExtensionUsageStats> getExtPointUsageStats() {
        // 简化实现，实际应收集真实的调用统计
        return new HashMap<>();
    }
    
    @Override
    public String exportMetadataAsJson() {
        // 简化实现，实际应使用JSON序列化
        return "{}";
    }
    
    @Override
    public ImportResult importMetadataFromJson(String metadataJson) {
        // 简化实现，返回成功结果
        return new ImportResult() {
            @Override
            public boolean isSuccess() {
                return true;
            }
            
            @Override
            public int getUpdatedCount() {
                return 0;
            }
            
            @Override
            public int getFailedCount() {
                return 0;
            }
            
            @Override
            public List<String> getErrorMessages() {
                return Collections.emptyList();
            }
        };
    }
}