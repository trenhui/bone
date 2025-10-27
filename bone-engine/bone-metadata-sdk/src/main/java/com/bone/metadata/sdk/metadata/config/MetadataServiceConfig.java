package com.bone.metadata.sdk.metadata.config;

import com.bone.metadata.sdk.metadata.DelegatingMetadataService;
import com.bone.metadata.sdk.metadata.cache.CachingMetadataService;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 元数据服务配置类
 * 提供元数据服务的依赖注入和装饰器配置
 */
@Configuration
public class MetadataServiceConfig {

    /**
     * 创建一个BeanPostProcessor，用于在DelegatingMetadataService之后应用缓存装饰器
     */
    @Bean
    public BeanPostProcessor metadataServiceCacheDecorator() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                // 只装饰主MetadataService实例，而不是内部的委托实例
                if (bean instanceof MetadataService && !beanName.equals("remoteMetadataService") 
                        && !beanName.equals("embeddedMetadataService") 
                        && !(bean instanceof CachingMetadataService)) {
                    // 对于DelegatingMetadataService，我们需要在外部包装缓存装饰器
                    return new CachingMetadataService((MetadataService) bean);
                }
                return bean;
            }
        };
    }
    
    /**
     * 如果不使用BeanPostProcessor，也可以直接定义Bean
     * 但使用BeanPostProcessor更加灵活，可以处理自动装配的情况
     */
    // @Bean
    // public MetadataService cachingMetadataService(DelegatingMetadataService delegatingMetadataService) {
    //     return new CachingMetadataService(delegatingMetadataService);
    // }
}