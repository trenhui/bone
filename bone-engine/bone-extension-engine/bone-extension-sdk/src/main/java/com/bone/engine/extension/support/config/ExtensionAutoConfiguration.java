package com.bone.engine.extension.support.config;

import com.bone.engine.extension.api.annotation.EnableExtensionPoints;
import com.bone.engine.extension.api.spi.ExtPointRouter;
import com.bone.engine.extension.core.lifecycle.DefaultExtensionLifecycle;
import com.bone.engine.extension.core.lifecycle.ExtensionLifecycle;
import com.bone.engine.extension.core.register.ExtensionRegister;
import com.bone.engine.extension.core.router.*;
import com.bone.engine.extension.support.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.StringUtils;

/**
 * Bone Extension SDK v2.0 GA - 统一自动配置中心
 * 100% 兼容 @EnableExtensionPoints 所有属性
 *
 * @author Bone Engine Team
 * @since 2.0.0-GA 2025-11-21
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExtensionProperties.class)
@ConditionalOnProperty(prefix = "bone.extension", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ExtensionAutoConfiguration implements ImportAware {

    private AnnotationAttributes attrs = new AnnotationAttributes();

    @Override
    public void setImportMetadata(AnnotationMetadata metadata) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                metadata.getAnnotationAttributes(EnableExtensionPoints.class.getName(), false));
        if (attributes != null) {
            this.attrs = attributes;
        }
    }

    @Bean
    public ExtensionRegister extensionRegister() {
        return new ExtensionRegister();
    }

    @Bean
    @ConditionalOnMissingBean
    public RouteStatsCollector routeStatsCollector() {
        return new RouteStatsCollector();
    }


    // ==================== 仓库自动装配 ====================

    @Bean
    @ConditionalOnMissingBean(ExtensionRepository.class)
    public ExtensionRepository extensionRepository(ApplicationContext ctx) {
        Class<?> repoClass = attrs.getClass("extensionRepository");
        if (repoClass != null && repoClass != InMemoryExtensionRepository.class) {
            log.info("Using custom ExtensionRepository from @EnableExtensionPoints: {}", repoClass.getName());
            return ExtensionRepositoryFactory.create((Class<? extends ExtensionRepository>) repoClass);
        }
        ExtensionRepository repo = ExtensionRepositoryFactory.getDefault();
        log.info("Using default ExtensionRepository: {}", repo.getClass().getSimpleName());
        return repo;
    }

    // ==================== 路由器自动装配（三优先级） ====================

    @Bean
    @ConditionalOnMissingBean(ExtPointRouter.class)
    public ExtPointRouter extensionRouter(
            @Autowired ApplicationContext ctx,
            @Autowired ExtensionRegister register) {

        // 1. customRouter 字符串（最高优先级）
        String custom = attrs.getString("customRouter");
        if (StringUtils.hasText(custom)) {
            try {
                Class<?> clazz = Class.forName(custom.trim());
                log.info("Using custom router from customRouter(): {}", custom);
                return (ExtPointRouter) clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load custom router: " + custom, e);
            }
        }

        // 2. extensionRouter Class 属性
        Class<?> routerClass = attrs.getClass("extensionRouter");
        if (routerClass != null && routerClass != DefaultExtPointRouter.class) {
            log.info("Using custom router from extensionRouter(): {}", routerClass.getName());
            return (ExtPointRouter) ExtensionRepositoryFactory.createBean(routerClass);
        }

        // 3. 默认路由器
        log.info("Using DefaultExtPointRouter");
        return new DefaultExtPointRouter(register, ctx);
    }


    @Bean
    @ConditionalOnMissingBean
    public ExtensionLifecycle extensionLifecycle() {
        return new DefaultExtensionLifecycle();
    }

    @Bean
    public RouterConfiguration routerConfiguration(ExtensionProperties properties) {
        RouterConfiguration config = RouterConfiguration.getInstance();
        java.util.Properties p = new java.util.Properties();
        p.setProperty("cache.enabled", String.valueOf(properties.getCache().isEnabled()));
        p.setProperty("cache.expireTime", String.valueOf(properties.getCache().getExpireAfterWrite() / 60_000));
        p.setProperty("cache.maxSize", String.valueOf(properties.getCache().getMaxSize()));
        config.initialize(p);
        return config;
    }
}