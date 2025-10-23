package com.bone.smartmeta.engine.config;

import com.bone.smartmeta.engine.ExpressionEngine;
import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.TransformationEngine;
import com.bone.smartmeta.engine.ValidationEngine;
import com.bone.smartmeta.engine.metadata.MetadataRegistry;
import com.bone.smartmeta.engine.metadata.OperationRegistry;
import com.bone.smartmeta.engine.metadata.processor.MetadataProcessor;
import com.bone.smartmeta.engine.metadata.processor.CompositeMetadataProcessor;
import com.bone.smartmeta.engine.repository.InMemoryMetadataRepository;
import com.bone.smartmeta.engine.repository.MetadataRepository;
import com.bone.smartmeta.engine.service.GenericOperationService;
import com.bone.smartmeta.engine.service.DynamicDataService;
import com.bone.smartmeta.engine.service.impl.InMemoryDynamicDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
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
        // 创建并配置MetadataRegistry
        return new MetadataRegistry();
    }
    
    @Bean
    @ConditionalOnMissingBean(name = "registryMetadataRegistry")
    public com.bone.smartmeta.engine.registry.MetadataRegistry registryMetadataRegistry() {
        // 创建registry包的MetadataRegistry实现
        return new com.bone.smartmeta.engine.registry.MetadataRegistry() {
            private final Map<String, Object> metadataMap = new ConcurrentHashMap<>();
            
            @Override
            public void registerMetadata(Object metadata) {
                metadataMap.put(metadata.toString(), metadata);
            }
            
            @Override
            public Object findMetadata(String entityName) {
                return metadataMap.get(entityName);
            }
            
            @Override
            public boolean unregisterMetadata(String entityName) {
                return metadataMap.remove(entityName) != null;
            }
            
            @Override
            public Iterable<String> getAllEntityNames() {
                return metadataMap.keySet();
            }
            
            @Override
            public void clear() {
                metadataMap.clear();
            }
        };
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
     * 配置复合元数据处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public CompositeMetadataProcessor compositeMetadataProcessor(
            List<com.bone.smartmeta.engine.metadata.processor.MetadataProcessor> metadataProcessors,
            ApplicationEventPublisher eventPublisher) {
        return new CompositeMetadataProcessor(metadataProcessors, eventPublisher);
    }
    
    /**
     * 配置操作元数据注册中心
     */
    @Bean
    @ConditionalOnMissingBean
    public OperationRegistry operationRegistry(MetadataRepository metadataRepository,
                                             MetadataRegistry metadataRegistry) {
        return new OperationRegistry(metadataRepository, metadataRegistry);
    }
    
    /**
     * 配置动态数据服务
     */
    @Bean
    @ConditionalOnMissingBean
    public DynamicDataService dynamicDataService(MetadataRegistry metadataRegistry) {
        return new InMemoryDynamicDataService(metadataRegistry);
    }
    
    /**
     * 配置通用操作服务
     */
    @Bean
    public GenericOperationService genericOperationService(MetadataEngine metadataEngine,
                                                           DynamicDataService dynamicDataService,
                                                           ExpressionEngine expressionEngine,
                                                           ValidationEngine validationEngine,
                                                           OperationRegistry operationRegistry,
                                                           ApplicationEventPublisher eventPublisher) {
        GenericOperationService service = new GenericOperationService(
            metadataEngine, dynamicDataService, expressionEngine,
            validationEngine, operationRegistry, eventPublisher);
        
        // 设置延迟注入，避免循环依赖
        metadataEngine.setOperationService(service);
        
        return service;
    }
    
    /**
     * 配置元数据核心引擎
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public MetadataEngine metadataEngine(com.bone.smartmeta.engine.registry.MetadataRegistry metadataRegistry,
                                         MetadataRepository metadataRepository,
                                         CompositeMetadataProcessor compositeMetadataProcessor,
                                         ApplicationEventPublisher eventPublisher) {
        // 创建一个适配器来转换CompositeMetadataProcessor到processor包的MetadataProcessor接口
        com.bone.smartmeta.engine.processor.MetadataProcessor processorAdapter = 
            new com.bone.smartmeta.engine.processor.MetadataProcessor() {
            @Override
            public com.bone.smartmeta.engine.processor.MetadataProcessor.ValidationResult validateMetadata(Object metadata) {
                // 简单实现，返回验证通过
                return new com.bone.smartmeta.engine.processor.MetadataProcessor.ValidationResult() {
                    @Override
                    public boolean isValid() { return true; }
                    @Override
                    public String getErrorMessage() { return null; }
                    @Override
                    public Map<String, Object> getDetails() { return new HashMap<>(); }
                };
            }
            
            @Override
            public Object analyzeImpact(String oldEntityName, Object newMetadata) {
                return null; // 简化实现
            }
            
            @Override
            public Object transformMetadata(Object sourceMetadata, String targetType) {
                return sourceMetadata; // 简化实现
            }
            
            @Override
            public Object enrichMetadata(Object metadata) {
                return metadata; // 简化实现
            }
            
            @Override
            public Object normalizeMetadata(Object metadata) {
                return metadata; // 简化实现
            }
            
            @Override
            public Object mergeMetadata(Object baseMetadata, Object overlayMetadata) {
                return baseMetadata; // 简化实现
            }
            
            @Override
            public Map<String, Object> extractMetadataInfo(Object metadata) {
                return new HashMap<>(); // 简化实现
            }
        };
        
        MetadataEngine engine = new MetadataEngine(metadataRegistry, metadataRepository, 
                                                   processorAdapter, eventPublisher);
        return engine;
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