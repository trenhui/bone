package com.bone.metadata.sdk.support.dataSource.config;

import com.bone.metadata.sdk.support.dataSource.DynamicDataSource;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 动态数据源自动配置
 * 自动配置多数据源环境
 */
@Configuration
@EnableConfigurationProperties(DynamicDataSourceProperties.class)
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "dynamic.datasource", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DynamicDataSourceAutoConfiguration {
    
    private final DynamicDataSourceProperties properties;
    
    public DynamicDataSourceAutoConfiguration(DynamicDataSourceProperties properties) {
        this.properties = properties;
    }
    
    /**
     * 配置动态数据源
     * @return 动态数据源实例
     */
    @Bean
    public DynamicDataSource dynamicDataSource() {
        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        
        // 配置目标数据源
        Map<Object, Object> targetDataSources = new HashMap<>();
        
        // 添加主数据源
        if (properties.getMaster() != null) {
            targetDataSources.put("master", properties.getMaster());
        }
        
        // 添加从数据源
        if (properties.getSlave() != null) {
            targetDataSources.put("slave", properties.getSlave());
        }
        
        // 添加自定义数据源
        if (properties.getDatasources() != null) {
            targetDataSources.putAll(properties.getDatasources());
        }
        
        dynamicDataSource.setTargetDataSources(targetDataSources);
        
        // 设置默认数据源
        dynamicDataSource.setDefaultTargetDataSource(properties.getDefaultDataSource());
        
        // 初始化
        dynamicDataSource.afterPropertiesSet();
        
        return dynamicDataSource;
    }
    
    /**
     * 配置事务管理器
     * @param dynamicDataSource 动态数据源
     * @return 事务管理器
     */
    @Bean
    public PlatformTransactionManager transactionManager(DynamicDataSource dynamicDataSource) {
        return new DataSourceTransactionManager(dynamicDataSource);
    }
}