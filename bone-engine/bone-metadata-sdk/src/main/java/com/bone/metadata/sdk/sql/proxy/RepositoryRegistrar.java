package com.bone.metadata.sdk.sql.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.*;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import com.bone.metadata.sdk.support.util.RepositoryClassUtils;

public class RepositoryRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {
    private static final Logger logger = LoggerFactory.getLogger(RepositoryRegistrar.class);
    private ResourceLoader resourceLoader;

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        long start = System.currentTimeMillis();
        AnnotationAttributes attrs = AnnotationAttributes.fromMap(
                metadata.getAnnotationAttributes(EnableSqlRepositories.class.getName()));
        if (attrs == null) {
            logger.warn("@EnableSqlRepositories annotation not found");
            return;
        }

        List<String> basePackages = getBasePackages(attrs, metadata);
        logger.info("Scanning for Repository interfaces in packages: {}", basePackages);

        ClassPathRepositoryScanner scanner = new ClassPathRepositoryScanner(registry);
        scanner.setResourceLoader(resourceLoader);
        scanner.registerFilters();

        int scanCount = scanner.scan(basePackages.toArray(new String[0]));
        logger.info("Scanned {} Repository interfaces in {}ms", scanCount, System.currentTimeMillis() - start);

        // 如果没有扫描到任何Repository，尝试手动注册
        if (scanCount == 0) {
            logger.warn("No Repository interfaces found during scanning. Trying manual registration...");
            manuallyRegisterRepositories(basePackages, registry);
        }
    }

    private List<String> getBasePackages(AnnotationAttributes attrs, AnnotationMetadata metadata) {
        List<String> basePackages = new ArrayList<>();
        Arrays.stream(attrs.getStringArray("value")).filter(StringUtils::hasText).forEach(basePackages::add);
        Arrays.stream(attrs.getStringArray("basePackages")).filter(StringUtils::hasText).forEach(basePackages::add);
        Arrays.stream(attrs.getClassArray("basePackageClasses")).map(ClassUtils::getPackageName).forEach(basePackages::add);
        if (basePackages.isEmpty()) {
            basePackages.add(ClassUtils.getPackageName(metadata.getClassName()));
        }
        return basePackages;
    }

    /**
     * 手动注册Repository接口
     */
    private void manuallyRegisterRepositories(List<String> basePackages, BeanDefinitionRegistry registry) {
        for (String basePackage : basePackages) {
            try {
                String packageSearchPath = "classpath*:" + basePackage.replace('.', '/') + "/**/*.class";
                Resource[] resources = ((ResourcePatternResolver) resourceLoader).getResources(packageSearchPath);

                logger.info("Found {} resources in package {}", resources.length, basePackage);

                for (Resource resource : resources) {
                    if (resource.isReadable()) {
                        try {
                            MetadataReader metadataReader = new SimpleMetadataReaderFactory().getMetadataReader(resource);
                            ClassMetadata classMetadata = metadataReader.getClassMetadata();

                            if (classMetadata.isInterface() && classMetadata.getClassName().endsWith("Repository")) {
                                logger.info("Manually registering Repository: {}", classMetadata.getClassName());

                                Class<?> repositoryInterface = Class.forName(classMetadata.getClassName());

                                // 检查是否实现了Repository接口
                                boolean implementsRepository = false;
                                for (Class<?> iface : repositoryInterface.getInterfaces()) {
                                    if (iface.getName().equals(Repository.class.getName())) {
                                        implementsRepository = true;
                                        break;
                                    }
                                }

                                if (implementsRepository) {
                                    registerRepositoryBean(repositoryInterface, registry);
                                } else {
                                    logger.warn("Interface {} does not implement Repository interface", classMetadata.getClassName());
                                }
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to process resource: {}", resource.getDescription(), e);
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to scan package: {}", basePackage, e);
            }
        }
    }

    /**
     * 注册Repository Bean
     */
    private void registerRepositoryBean(Class<?> repositoryInterface, BeanDefinitionRegistry registry) {
        try {
            // 解析泛型参数
            Class<?>[] genericTypes = RepositoryClassUtils.resolveGenericTypes(repositoryInterface);
            if (genericTypes == null || genericTypes.length != 2) {
                logger.error("Failed to resolve generic types for {}", repositoryInterface.getName());
                return;
            }

            // 创建Bean定义
            BeanDefinitionBuilder builder = BeanDefinitionBuilder
                    .genericBeanDefinition(RepositoryFactoryBean.class);

            // 设置构造器参数
            builder.addConstructorArgValue(repositoryInterface);
            builder.addConstructorArgValue(genericTypes[0]);
            builder.addConstructorArgValue(genericTypes[1]);

            // 设置属性
            builder.addPropertyReference("sqlTemplateLoader", "sqlTemplateLoader");
            builder.addPropertyReference("sqlExecutor", "sqlExecutor");
            builder.addPropertyReference("sqlProcessorFactory", "sqlProcessorFactory");

            // 设置自动装配模式
            builder.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);

            // 注册Bean定义
            String beanName = StringUtils.uncapitalize(repositoryInterface.getSimpleName());
            registry.registerBeanDefinition(beanName, builder.getBeanDefinition());

            logger.info("Successfully registered Repository: {}", repositoryInterface.getName());
        } catch (Exception e) {
            logger.error("Failed to register Repository: {}", repositoryInterface.getName(), e);
        }
    }


}