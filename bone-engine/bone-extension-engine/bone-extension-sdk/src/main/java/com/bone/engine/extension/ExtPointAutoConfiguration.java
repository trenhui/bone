package com.bone.engine.extension;

import com.bone.engine.extension.config.ExtensionConfigProperties;
import com.bone.engine.extension.config.ExtensionConfigValidator;
import com.bone.engine.extension.event.DefaultExtensionEventPublisher;
import com.bone.engine.extension.expression.ExpressionEvaluator;
import com.bone.engine.extension.repository.ExtPointRepository;
import com.bone.engine.extension.repository.MemExtPointRepository;
import com.bone.engine.extension.register.ExtensionRegister;
import com.bone.engine.extension.router.DefaultExtPointRouter;
import com.bone.engine.extension.router.ExtPointRouter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * 扩展点框架自动配置类，配置所有扩展点框架的核心组件
 */
@Configuration
@EnableConfigurationProperties(ExtensionConfigProperties.class)
public class ExtPointAutoConfiguration {
    
    private final ExtensionConfigProperties extensionConfigProperties;
    private final ExtensionConfigValidator extensionConfigValidator;
    
    public ExtPointAutoConfiguration(ExtensionConfigProperties extensionConfigProperties,
                                   ExtensionConfigValidator extensionConfigValidator) {
        this.extensionConfigProperties = extensionConfigProperties;
        this.extensionConfigValidator = extensionConfigValidator;
    }
    
    // 配置验证将在使用配置的Bean中进行
    
    /**
     * 扩展点仓库 - 存储和管理扩展点实现
     */
    @Bean
    public ExtPointRepository extPointRepository() {
        return new MemExtPointRepository();
    }
    
    /**
     * 默认扩展点路由器 - 根据业务上下文路由到合适的扩展实现
     */
    @Bean
    public ExtPointRouter extPointRouter() {
        return new DefaultExtPointRouter();
    }
    
    /**
     * 扩展提供者注册器，负责扫描和注册所有扩展实现
     */
    @Bean(initMethod = "init")
    public ExtensionRegister extProviderRegister(ExtPointRepository extPointRepository,
                                              ApplicationEventPublisher applicationEventPublisher,
                                              ExtensionConfigProperties configProperties) {
        // 创建默认的事件发布器实例，并注入Spring的ApplicationEventPublisher
        DefaultExtensionEventPublisher eventPublisher = new DefaultExtensionEventPublisher(applicationEventPublisher);
        
        // 创建扩展注册器并注入所需组件
        return new ExtensionRegister(extPointRepository, eventPublisher, configProperties);
    }
}
