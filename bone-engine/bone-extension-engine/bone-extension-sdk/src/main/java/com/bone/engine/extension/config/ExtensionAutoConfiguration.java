package com.bone.engine.extension.config;

import com.bone.engine.extension.annotation.EnableExtPoints;
import com.bone.engine.extension.lifecycle.DefaultExtensionLifecycle;
import com.bone.engine.extension.lifecycle.ExtensionLifecycle;
import com.bone.engine.extension.loader.ExtensionLoader;
import com.bone.engine.extension.proxy.ExtPointProxyFactory;
import com.bone.engine.extension.router.DefaultExtPointRouter;
import com.bone.engine.extension.router.ExtPointRouter;
import com.bone.engine.extension.event.DefaultExtensionEventPublisher;
import com.bone.engine.extension.event.ExtensionEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 扩展点框架自动配置类
 * <p>
 * 负责自动配置扩展点框架的核心组件，包括路由引擎、代理工厂等
 * <strong>主要职责：</strong>
 * <ul>
 *   <li>自动装配扩展点路由引擎</li>
 *   <li>配置扩展点代理工厂</li>
 *   <li>处理扩展点扫描和注册</li>
 * </ul>
 * </p>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
@Configuration
@EnableConfigurationProperties(ExtensionProperties.class)
public class ExtensionAutoConfiguration implements ImportAware {

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
    public ExtPointRouter extPointRouter() {
        DefaultExtPointRouter router = new DefaultExtPointRouter();
        router.setEnableCache(enableCache);
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

    // 扩展点扫描功能已集成到ExtensionRegister中

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
     * 配置扩展点加载器
     * 注意：ExtensionLoader是泛型类，这里返回一个FactoryBean用于创建具体类型的加载器
     */
    @Bean
    @ConditionalOnMissingBean(name = "extensionLoaderFactory")
    public Object extensionLoaderFactory() {
        return new Object(); // 使用占位符，实际使用时通过ExtensionLoader.getExtensionLoader()获取具体类型的加载器
    }
}