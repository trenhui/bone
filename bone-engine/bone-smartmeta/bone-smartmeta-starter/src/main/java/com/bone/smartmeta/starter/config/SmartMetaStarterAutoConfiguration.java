package com.bone.smartmeta.starter.config;

import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.ValidationEngine;
import com.bone.smartmeta.engine.ExpressionEngine;
import com.bone.smartmeta.engine.TransformationEngine;
import com.bone.smartmeta.engine.config.SmartMetaAutoConfiguration;
import com.bone.smartmeta.engine.config.SmartMetaProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Bone SmartMeta Starter 自动配置类
 * 提供简化的集成方式，自动装配所有必要的组件
 */
@Configuration
@Import(SmartMetaAutoConfiguration.class)
@EnableConfigurationProperties(SmartMetaProperties.class)
public class SmartMetaStarterAutoConfiguration {
    
    /**
     * 确保MetadataEngine在Spring容器中可用
     * @param properties SmartMeta配置属性
     * @return MetadataEngine实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MetadataEngine metadataEngine(SmartMetaProperties properties) {
        return new MetadataEngine(properties);
    }
    
    /**
     * 确保ValidationEngine在Spring容器中可用
     * @return ValidationEngine实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ValidationEngine validationEngine() {
        return new ValidationEngine();
    }
    
    /**
     * 确保ExpressionEngine在Spring容器中可用
     * @return ExpressionEngine实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ExpressionEngine expressionEngine() {
        return new ExpressionEngine();
    }
    
    /**
     * 确保TransformationEngine在Spring容器中可用
     * @return TransformationEngine实例
     */
    @Bean
    @ConditionalOnMissingBean
    public TransformationEngine transformationEngine() {
        return new TransformationEngine();
    }
}