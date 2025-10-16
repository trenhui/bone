package com.bone.smartmeta.starter.config;

import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.core.SmartBaseEntity;
import com.bone.smartmeta.engine.engine.ValidationEngine;
import com.bone.smartmeta.engine.metadata.MetadataRegistry;
import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.FieldMetadata;
import com.bone.smartmeta.engine.registry.MetadataRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.HashMap;
import java.util.Map;

/**
 * SmartMeta Starter 自动配置类
 */
@Configuration
public class SmartMetaStarterAutoConfiguration {

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
        return new MetadataEngine();
    }

    /**
     * 创建验证引擎
     */
    @Bean
    @ConditionalOnMissingBean
    public ValidationEngine validationEngine(Object metadataRegistry) { // 修改参数类型为Object
        return new ValidationEngine(metadataRegistry);
    }
}