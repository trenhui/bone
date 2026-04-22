package com.bone.engine.extension.support.config;

import com.bone.engine.extension.api.annotation.EnableExtensionPoints;
import com.bone.engine.extension.api.spi.ExtensionPointRouter;
import com.bone.engine.extension.api.spi.ExtensionRepository;
import com.bone.engine.extension.core.cache.CacheManager;
import com.bone.engine.extension.core.event.DefaultExtensionEventPublisher;
import com.bone.engine.extension.core.event.ExtensionEventPublisher;
import com.bone.engine.extension.core.lifecycle.DefaultExtensionLifecycle;
import com.bone.engine.extension.core.lifecycle.ExtensionLifecycle;
import com.bone.engine.extension.core.metrics.ExtensionAlarmService;
import com.bone.engine.extension.core.metrics.ExtensionMetricsAlarm;
import com.bone.engine.extension.core.metrics.ExtensionMetricsCollector;
import com.bone.engine.extension.core.metrics.LoggingExtensionAlarmService;
import com.bone.engine.extension.core.register.ExtensionRegister;
import com.bone.engine.extension.core.router.DefaultExtensionPointRouter;
import com.bone.engine.extension.support.expression.SpELExpressionEvaluator;
import com.bone.engine.extension.support.repository.ExtensionRepositoryFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.type.AnnotationMetadata;

import java.time.Duration;

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
@Import({ExtensionAsyncConfig.class, ExtensionExecutorConfiguration.class, ExtensionSecurityConfiguration.class, ExtensionScaffoldConfiguration.class})
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
    @ConditionalOnMissingBean
    public ExtensionEventPublisher extensionEventPublisher(
            ApplicationEventPublisher applicationEventPublisher,
            @Qualifier(ExtensionAsyncConfig.EXTENSION_EVENT_EXECUTOR_BEAN_NAME) AsyncTaskExecutor taskExecutor) {
        return new DefaultExtensionEventPublisher(applicationEventPublisher, taskExecutor);
    }

    @Bean
    public ExtensionRegister extensionRegister(
            ExtensionRepository extensionRepository,
            ExtensionProperties extensionProperties,
            ExtensionEventPublisher eventPublisher) {
        return new ExtensionRegister(extensionRepository, extensionProperties);
    }

    // ==================== 仓库自动装配 ====================

    @Bean
    @ConditionalOnMissingBean(ExtensionRepository.class)
    public ExtensionRepository extensionRepository(ExtensionRepositoryFactory factory) {
        try {
            Class<?> repoClass = attrs.getClass("extensionRepository");
            if (repoClass != null) {
                log.info("Using custom ExtensionRepository from @EnableExtensionPoints: {}", repoClass.getName());
                return factory.create((Class<? extends ExtensionRepository>) repoClass);
            }
        } catch (Exception e) {
            log.warn("Failed to get extensionRepository attribute, using default", e);
        }
        ExtensionRepository repo = factory.getDefault();
        log.info("Using default ExtensionRepository: {}", repo.getClass().getSimpleName());
        return repo;
    }

    // ==================== 路由器自动装配 ====================

    @Bean
    @ConditionalOnMissingBean
    public com.bone.engine.extension.api.spi.ExpressionEvaluator expressionEvaluator() {
        log.info("Using SpELExpressionEvaluator as default ExpressionEvaluator");
        return new SpELExpressionEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean(ExtensionPointRouter.class)
    public ExtensionPointRouter extensionRouter(
            ExtensionRepository extensionRepo,
            com.bone.engine.extension.api.spi.ExpressionEvaluator expressionEvaluator,
            CacheManager cacheManager,
            ExtensionProperties properties,
            ExtensionRepositoryFactory factory) {

        // 1. extensionRouter Class 属性
        Class<?> routerClass = attrs.getClass("extensionPointRouter");
        if (routerClass != null && routerClass != DefaultExtensionPointRouter.class) {
            log.info("Using custom router from extensionPointRouter(): {}", routerClass.getName());
            return (ExtensionPointRouter) factory.createBean(routerClass);
        }

        // 2. 默认路由器
        log.info("Using DefaultExtensionPointRouter");
        ExtensionProperties.CacheConfig cacheProps = properties.getCache();
        return new DefaultExtensionPointRouter(
                extensionRepo,
                expressionEvaluator,
                cacheProps.getMaxSize(),
                cacheProps.getExpireTime(),
                cacheProps.isLazyLoad(),
                cacheManager
        );
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
        ExtensionProperties.CacheConfig cacheProps = properties.getCache();
        p.setProperty("cache.enabled", String.valueOf(cacheProps.isEnabled()));
        p.setProperty("cache.expireTime", String.valueOf(cacheProps.getExpireTime().toMinutes()));
        p.setProperty("cache.maxSize", String.valueOf(cacheProps.getMaxSize()));
        config.initialize(p);
        return config;
    }

    @Bean
    @ConditionalOnMissingBean
    public ExtensionMetricsCollector extensionMetricsCollector(io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        return new ExtensionMetricsCollector(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExtensionAlarmService extensionAlarmService() {
        return new LoggingExtensionAlarmService();
    }

    @Bean
    @ConditionalOnMissingBean
    public ExtensionMetricsAlarm extensionMetricsAlarm(ExtensionMetricsCollector metricsCollector, ExtensionAlarmService alarmService, ExtensionProperties properties) {
        return new ExtensionMetricsAlarm(metricsCollector, alarmService, properties);
    }
}