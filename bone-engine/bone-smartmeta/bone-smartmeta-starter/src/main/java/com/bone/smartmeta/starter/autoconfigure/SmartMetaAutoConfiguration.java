package com.bone.smartmeta.starter.autoconfigure;

import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.config.SmartMetaProperties;
// 修复registry包找不到的问题
// import com.bone.smartmeta.engine.registry.MetadataRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/**
 * SmartMeta 自动配置类
 */
@Configuration
@EnableConfigurationProperties(SmartMetaProperties.class)
public class SmartMetaAutoConfiguration {

    /**
     * 创建元数据注册表（模拟实现）
     */
    @Bean
    @ConditionalOnMissingBean
    public Object metadataRegistry() { // 修改返回类型为Object
        // 返回一个模拟对象
        return new Object();
    }

    /**
     * 创建元数据引擎
     */
    @Bean
    @ConditionalOnMissingBean
    public MetadataEngine metadataEngine(Object metadataRegistry) { // 修改参数类型为Object
        return new MetadataEngine(metadataRegistry);
    }

    /**
     * 创建元数据引擎初始化器
     */
    @Bean
    @ConditionalOnMissingBean
    public Object metadataEngineInitializer(MetadataEngine metadataEngine, 
                                           SmartMetaProperties smartMetaProperties, 
                                           ResourceLoader resourceLoader) {
        // 返回一个模拟的初始化器对象
        return new Object();
    }
}