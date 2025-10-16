package com.bone.smartmeta.starter.autoconfigure;

import com.bone.smartmeta.engine.config.SmartMetaProperties;
import com.bone.smartmeta.engine.engine.MetadataEngine;
import com.bone.smartmeta.engine.engine.ValidationEngine;
import com.bone.smartmeta.engine.engine.ExpressionEngine;
import com.bone.smartmeta.engine.engine.TransformationEngine;
import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.registry.MetadataRegistry;
import com.bone.smartmeta.engine.repository.MetadataRepository;
import com.bone.smartmeta.engine.repository.impl.InMemoryMetadataRepository;
import com.bone.smartmeta.engine.initializer.MetadataEngineInitializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Bone SmartMeta 自动配置类
 * 为Spring Boot应用提供自动配置支持
 */
@Configuration
@ConditionalOnProperty(prefix = "bone.smartmeta", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SmartMetaProperties.class)
public class SmartMetaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MetadataRegistry metadataRegistry() {
        return new MetadataRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public MetadataRepository metadataRepository() {
        return new InMemoryMetadataRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public ValidationEngine validationEngine(MetadataRegistry metadataRegistry) {
        return new ValidationEngine(metadataRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExpressionEngine expressionEngine() {
        return new ExpressionEngine();
    }

    @Bean
    @ConditionalOnMissingBean
    public TransformationEngine transformationEngine() {
        return new TransformationEngine();
    }

    @Bean
    @ConditionalOnMissingBean
    public MetadataEngine metadataEngine(MetadataRegistry metadataRegistry, MetadataRepository metadataRepository) {
        return new MetadataEngine(metadataRegistry, metadataRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public MetadataEngineInitializer metadataEngineInitializer(
            MetadataEngine metadataEngine,
            List<EntityMetadata> initialEntityMetadata) {
        return new MetadataEngineInitializer(metadataEngine, initialEntityMetadata);
    }
}