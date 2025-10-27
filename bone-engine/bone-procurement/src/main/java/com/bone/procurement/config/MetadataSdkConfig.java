package com.bone.procurement.config;

import com.bone.metadata.sdk.sql.dialect.ColumnAllocationDialect;
import com.bone.metadata.sdk.sql.dialect.ColumnAllocationDialectFactory;
import com.bone.metadata.sdk.sql.dialect.H2ColumnAllocationDialect;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * bone-metadata-sdk配置类
 * 启用必要的组件并确保正确的包扫描
 */
@Configuration
@ComponentScan({
    "com.bone.metadata.sdk.extension",
    "com.bone.metadata.sdk.sql"
    // 移除对metadata包的扫描，避免自动注册CachingMetadataService导致循环依赖
})
public class MetadataSdkConfig {
    // 配置类，用于启用bone-metadata-sdk的组件
    
    /**
     * 配置ColumnAllocationDialectFactory bean
     * 用于处理SQL方言的列分配
     */
    @Bean
    public ColumnAllocationDialectFactory columnAllocationDialectFactory() {
        // 提供H2数据库方言实现，因为应用使用H2数据库
        ColumnAllocationDialect h2Dialect = new H2ColumnAllocationDialect();
        return new ColumnAllocationDialectFactory(List.of(h2Dialect));
    }
}