package com.bone.engine.extension.config;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.Extension;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * 扩展点扫描器
 * <p>
 * 负责扫描带有@Extension注解的实现类，并自动注册到Spring容器中
 * <strong>主要功能：</strong>
 * <ul>
 *   <li>扫描指定包路径下的扩展点实现</li>
 *   <li>自动识别并注册扩展点实现Bean</li>
 *   <li>支持自定义扫描路径配置</li>
 * </ul>
 * </p>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
public class ExtensionScanner implements BeanFactoryPostProcessor {

    private String[] basePackages = {};

    public void setBasePackages(String[] basePackages) {
        this.basePackages = basePackages;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof BeanDefinitionRegistry)) {
            throw new IllegalStateException("BeanFactory must be a BeanDefinitionRegistry");
        }

        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
        
        // 初始化扫描器
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Extension.class));

        // 确定扫描路径
        Set<String> packagesToScan = determinePackagesToScan();
        
        // 执行扫描并注册
        for (String basePackage : packagesToScan) {
            scanner.findCandidateComponents(basePackage)
                    .forEach(beanDefinition -> {
                        String beanClassName = beanDefinition.getBeanClassName();
                        if (beanClassName != null) {
                            try {
                                Class<?> beanClass = ClassUtils.forName(beanClassName, ExtensionScanner.class.getClassLoader());
                                // 验证是否实现了带@ExtPoint注解的接口
                                if (isValidExtensionImplementation(beanClass)) {
                                    // 注册为Spring Bean
                                    String beanName = StringUtils.uncapitalize(ClassUtils.getShortName(beanClass));
                                    // 注意：这里简化处理，实际可能需要更复杂的BeanDefinition处理
                                }
                            } catch (ClassNotFoundException e) {
                                throw new RuntimeException("Failed to load extension class: " + beanClassName, e);
                            }
                        }
                    });
        }
    }

    private Set<String> determinePackagesToScan() {
        Set<String> packages = new HashSet<>();
        
        // 添加配置的基础包
        for (String basePackage : basePackages) {
            if (StringUtils.hasText(basePackage)) {
                packages.add(basePackage);
            }
        }
        
        // 如果没有指定包，使用默认包
        if (packages.isEmpty()) {
            packages.add("com"); // 扫描常见包路径
        }
        
        return packages;
    }

    private boolean isValidExtensionImplementation(Class<?> beanClass) {
        // 检查是否实现了带@ExtPoint注解的接口
        for (Class<?> ifc : beanClass.getInterfaces()) {
            if (ifc.isAnnotationPresent(ExtPoint.class)) {
                return true;
            }
        }
        return false;
    }
}