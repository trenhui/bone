package com.bone.smartmeta.engine.config;

import com.bone.smartmeta.engine.ExpressionEngine;
import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.TransformationEngine;
import com.bone.smartmeta.engine.ValidationEngine;
import com.bone.smartmeta.engine.metadata.MetadataRegistry;
import com.bone.smartmeta.engine.repository.InMemoryMetadataRepository;
import com.bone.smartmeta.engine.repository.MetadataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * SmartMeta 自动配置类
 * 负责自动装配所有SmartMeta组件和配置
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SmartMetaProperties.class)
@ComponentScan(basePackages = "com.bone.smartmeta.engine")
public class SmartMetaAutoConfiguration {
    
    private final SmartMetaProperties smartMetaProperties;

    public SmartMetaAutoConfiguration(SmartMetaProperties smartMetaProperties) {
        this.smartMetaProperties = smartMetaProperties;
    }
    
    /**
     * 配置元数据注册中心
     */
    @Bean
    @ConditionalOnMissingBean
    public MetadataRegistry metadataRegistry() {
        // 直接创建并返回MetadataRegistry实例
        return new MetadataRegistry();
    }
    
    /**
     * 配置元数据仓库（默认使用内存实现）
     */
    @Bean
    @ConditionalOnMissingBean
    public MetadataRepository metadataRepository() {
        return new InMemoryMetadataRepository();
    }
    
    /**
     * 配置验证引擎
     */
    @Bean
    @ConditionalOnMissingBean
    public ValidationEngine validationEngine() {
        return new ValidationEngine();
    }
    
    /**
     * 配置表达式引擎
     */
    @Bean
    @ConditionalOnMissingBean
    public ExpressionEngine expressionEngine() {
        return new ExpressionEngine();
    }
    
    /**
     * 配置转换引擎
     */
    @Bean
    @ConditionalOnMissingBean
    public TransformationEngine transformationEngine() {
        return new TransformationEngine();
    }
    
    /**
     * 配置元数据核心引擎
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public MetadataEngine metadataEngine() {
        // 使用无参构造函数创建MetadataEngine实例
        return new MetadataEngine();
    }
    
    /**
     * 配置Jackson ObjectMapper
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        // 返回简单的ObjectMapper实例
        return new ObjectMapper();
    }
    
    /**
     * 元数据引擎初始化器
     */
    @Bean
    @ConditionalOnMissingBean
    public MetadataEngineInitializer metadataEngineInitializer(MetadataEngine metadataEngine, 
                                                              SmartMetaProperties smartMetaProperties, 
                                                              org.springframework.core.io.ResourceLoader resourceLoader) {
        return new MetadataEngineInitializer(metadataEngine, smartMetaProperties, resourceLoader);
    }
}