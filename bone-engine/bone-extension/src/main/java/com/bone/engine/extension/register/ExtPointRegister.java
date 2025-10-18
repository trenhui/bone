package com.bone.engine.extension.register;

import com.bone.engine.extension.EnableExtPoints;
import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.proxy.ExtPointProxyBeanBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.lang.NonNull;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 扩展点注册器
 * <p>
 * 实现Spring的ImportBeanDefinitionRegistrar接口，负责在Spring容器启动时自动扫描和注册扩展点及其实现。
 * 主要功能包括：
 * 1. 扫描所有带有@ExtPoint注解的接口和类
 * 2. 扫描所有带有@Extension注解的实现类
 * 3. 为扩展点创建代理Bean定义
 * </p>
 * 
 * @see EnableExtPoints 启用扩展点的核心注解
 * @see ExtPoint 扩展点标记注解
 * @see ExtensionRegister 扩展提供者注册器
 * @since 1.0.0
 */
public final class ExtPointRegister implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {
    private static final Logger log = LoggerFactory.getLogger(ExtPointRegister.class);
    
    private ResourceLoader resourceLoader;
    private Environment environment;

    /**
     * 默认构造函数
     */
    ExtPointRegister() {
        // 私有构造函数防止外部实例化
    }

    /**
     * 设置资源加载器
     * 
     * @param resourceLoader 用于加载类路径资源的加载器
     */
    @Override
    public void setResourceLoader(@NonNull ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 注册Bean定义
     * <p>
     * 扫描并注册所有扩展点和扩展提供者
     * </p>
     * 
     * @param metadata 导入类的注解元数据
     * @param registry Bean定义注册表
     */
    @Override
    public void registerBeanDefinitions(@NonNull AnnotationMetadata metadata, @NonNull BeanDefinitionRegistry registry) {
        log.debug("Starting extension point registration process");
        
        // 注册扩展点接口
        registerExtensionPoints(metadata, registry);
        
        // 注册扩展提供者实现
        registerExtensionProviders(metadata, registry);
        
        log.debug("Extension point registration process completed");
    }

    /**
     * 注册扩展提供者实现
     * <p>
     * 使用专用的扫描器查找并注册所有扩展提供者
     * </p>
     * 
     * @param metadata 导入类的注解元数据
     * @param registry Bean定义注册表
     */
    private void registerExtensionProviders(@NonNull AnnotationMetadata metadata, @NonNull BeanDefinitionRegistry registry) {
        log.debug("Registering extension providers");
        ExtensionBeanDefinitionScanner scanner = new ExtensionBeanDefinitionScanner(registry);
        scanner.scan(getBasePackages(metadata));
    }

    /**
     * 注册扩展点接口
     * <p>
     * 扫描并注册所有带有@ExtPoint注解的接口和类
     * </p>
     * 
     * @param metadata 导入类的注解元数据
     * @param registry Bean定义注册表
     */
    public void registerExtensionPoints(@NonNull AnnotationMetadata metadata, @NonNull BeanDefinitionRegistry registry) {
        log.debug("Scanning for extension points");
        
        // 收集候选组件
        LinkedHashSet<BeanDefinition> candidateComponents = new LinkedHashSet<>();
        ClassPathScanningCandidateComponentProvider scanner = getScanner();
        scanner.setResourceLoader(this.resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ExtPoint.class));
        
        // 扫描基础包
        String[] basePackages = getBasePackages(metadata);
        for (String basePackage : basePackages) {
            log.trace("Scanning package: {}", basePackage);
            candidateComponents.addAll(scanner.findCandidateComponents(basePackage));
        }

        log.debug("Found {} extension point candidates", candidateComponents.size());
        
        // 获取@EnableExtPoints注解属性
        Map<String, Object> annotationAttributes = metadata.getAnnotationAttributes(EnableExtPoints.class.getCanonicalName());
        
        // 注册Bean定义
        ExtPointProxyBeanBuilder.registerBeanDefinition(metadata, registry, annotationAttributes, candidateComponents);
    }

    /**
     * 获取类路径扫描器
     * <p>
     * 创建并配置用于扫描扩展点的组件扫描器
     * </p>
     * 
     * @return 配置好的类路径扫描器
     */
    protected ClassPathScanningCandidateComponentProvider getScanner() {
        return new ClassPathScanningCandidateComponentProvider(false, this.environment) {
            @Override
            protected boolean isCandidateComponent(@NonNull AnnotatedBeanDefinition beanDefinition) {
                // 只考虑独立的类和接口，排除注解
                return beanDefinition.getMetadata().isIndependent() && !beanDefinition.getMetadata().isAnnotation();
            }
        };
    }

    /**
     * 获取基础包路径
     * <p>
     * 从@EnableExtPoints注解中提取基础包路径，如果未指定则使用注解所在类的包
     * </p>
     * 
     * @param importingClassMetadata 导入类的元数据
     * @return 基础包路径数组
     */
    protected String[] getBasePackages(@NonNull AnnotationMetadata importingClassMetadata) {
        Map<String, Object> attributes = importingClassMetadata.getAnnotationAttributes(EnableExtPoints.class.getCanonicalName());
        Set<String> basePackages = new HashSet<>();
        
        // 从注解属性中获取包路径
        String[] basePackagesArray = (String[]) attributes.get("basePackages");
        for (String packagePath : basePackagesArray) {
            if (StringUtils.hasText(packagePath)) {
                basePackages.add(packagePath);
            }
        }
        
        // 如果未指定包路径，使用注解所在类的包
        if (basePackages.isEmpty()) {
            String defaultPackage = ClassUtils.getPackageName(importingClassMetadata.getClassName());
            basePackages.add(defaultPackage);
            log.debug("No base packages specified, using default: {}", defaultPackage);
        }

        return basePackages.toArray(new String[0]);
    }

    /**
     * 设置环境
     * 
     * @param environment Spring环境对象
     */
    @Override
    public void setEnvironment(@NonNull Environment environment) {
        this.environment = environment;
    }
}
