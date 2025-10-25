package com.bone.engine.extension.config;

import com.bone.engine.extension.annotation.EnableExtPoints;
import com.bone.engine.extension.config.ExtensionProperties;
import com.bone.engine.extension.lifecycle.DefaultExtensionLifecycle;
import com.bone.engine.extension.lifecycle.ExtensionLifecycle;
import com.bone.engine.extension.proxy.ExtPointProxyFactory;
import com.bone.engine.extension.router.DefaultExtPointRouter;
import com.bone.engine.extension.router.ExtPointRouter;
import com.bone.engine.extension.event.DefaultExtensionEventPublisher;
import com.bone.engine.extension.event.ExtensionEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 统一的扩展点框架自动配置类
 * <p>
 * 整合所有扩展点框架的配置管理，提供一致的配置入口
 * 解决之前多个配置类和配置属性类并存的问题
 * </p>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
@Configuration
@EnableConfigurationProperties(ExtensionProperties.class)
@ConditionalOnProperty(prefix = "bone.extension", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UnifiedExtPointAutoConfiguration implements ImportAware {
    private static final Logger log = LoggerFactory.getLogger(UnifiedExtPointAutoConfiguration.class);

    private String[] basePackages = {};
    private boolean enableAutoScan = true;
    private boolean enableCache = true;
    private boolean enableEvents = true;

    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        AnnotationAttributes attributes = AnnotationAttributes
                .fromMap(importMetadata.getAnnotationAttributes(EnableExtPoints.class.getName(), false));
        if (attributes != null) {
            this.basePackages = attributes.getStringArray("basePackages");
            this.enableAutoScan = attributes.getBoolean("enableAutoScan");
            this.enableCache = attributes.getBoolean("enableCache");
            this.enableEvents = attributes.getBoolean("enableEvents");
        }
    }

    /**
     * 配置扩展点路由引擎
     */
    @Bean
    @ConditionalOnMissingBean(ExtPointRouter.class)
    public ExtPointRouter extPointRouter(ApplicationContext applicationContext, ExtensionProperties extensionProperties) {
        // 创建DefaultExtPointRouter实例，传入ApplicationContext
        DefaultExtPointRouter router = new DefaultExtPointRouter(applicationContext);
        // 设置缓存启用状态，优先使用注解属性，其次使用配置属性
        boolean finalEnableCache = this.enableCache && extensionProperties.getCache().isEnabled();
        router.setEnableCache(finalEnableCache);
        log.info("Configured ExtPointRouter with cache enabled: {}", finalEnableCache);
        return router;
    }

    /**
     * 配置扩展点代理工厂
     */
    @Bean
    @ConditionalOnMissingBean(ExtPointProxyFactory.class)
    public ExtPointProxyFactory extPointProxyFactory() {
        ExtPointProxyFactory factory = new ExtPointProxyFactory();
        factory.setEnableCache(enableCache);
        return factory;
    }

    /**
     * 配置扩展点事件发布器
     */
    @Bean
    @ConditionalOnProperty(name = "bone.extension.events.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(ExtensionEventPublisher.class)
    public ExtensionEventPublisher extensionEventPublisher(org.springframework.context.ApplicationEventPublisher applicationEventPublisher) {
        return new DefaultExtensionEventPublisher(applicationEventPublisher);
    }

    /**
     * 配置扩展点配置管理器
     * 注意：这里返回的是一个适配层，内部使用ExtensionProperties进行实际配置管理
     */
    @Bean
    @ConditionalOnMissingBean(ExtensionConfigManager.class)
    public ExtensionConfigManager extensionConfigManager() {
        ExtensionConfigManager manager = new ExtensionConfigManager();
        manager.init();
        return manager;
    }

    /**
     * 配置扩展点生命周期管理器
     */
    @Bean
    @ConditionalOnMissingBean(ExtensionLifecycle.class)
    public ExtensionLifecycle extensionLifecycle() {
        return new DefaultExtensionLifecycle();
    }

    /**
     * 配置扩展点配置验证器
     */
    @Bean
    @ConditionalOnMissingBean(ExtensionConfigValidator.class)
    public ExtensionConfigValidator extensionConfigValidator() {
        return new ExtensionConfigValidator();
    }
}