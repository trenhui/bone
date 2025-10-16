package com.bone.procurement.config;

import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.ExpressionEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * bone-smartmeta配置类
 * 配置元数据引擎和表达式引擎
 */
@Configuration
public class SmartMetaConfig {
    
    /**
     * 配置元数据引擎Bean
     */
    @Bean
    public MetadataEngine metadataEngine() {
        // 创建元数据引擎实例
        MetadataEngine engine = new MetadataEngine();
        
        // 配置引擎参数
        engine.setCacheEnabled(true);
        engine.setValidationEnabled(true);
        engine.setCalculationEnabled(true);
        
        return engine;
    }
    
    /**
     * 配置表达式引擎Bean
     */
    @Bean
    public ExpressionEngine expressionEngine() {
        // 创建表达式引擎实例
        ExpressionEngine engine = new ExpressionEngine();
        
        // 配置引擎参数
        engine.setCacheEnabled(true);
        engine.setStrictMode(false);  // 非严格模式，允许更多的动态表达式
        
        return engine;
    }
}