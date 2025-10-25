package com.bone.metadata.sdk.support.dataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源自动配置类
 * 自动配置动态数据源相关的组件
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({DynamicDataSource.class, DataSourceContextHolder.class})
@EnableConfigurationProperties(DynamicDataSourceProperties.class)
@AutoConfigureAfter({DataSourceAutoConfiguration.class})
@Import({DynamicDataSourceAspectConfiguration.class})
public class DynamicDataSourceAutoConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(DynamicDataSourceAutoConfiguration.class);
    
    @Autowired
    private DynamicDataSourceProperties properties;
    
    /**
     * 创建动态数据源实例
     * @param defaultDataSource 默认数据源（Spring Boot自动配置的数据源）
     * @return 动态数据源实例
     */
    @Bean
    @ConditionalOnMissingBean(DynamicDataSource.class)
    public DynamicDataSource dynamicDataSource(ObjectProvider<DataSource> defaultDataSource) {
        log.info("Initializing DynamicDataSource");
        
        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        
        // 设置默认数据源
        DataSource primaryDataSource = defaultDataSource.getIfAvailable();
        if (primaryDataSource != null) {
            log.info("Setting primary datasource: {}", properties.getPrimary());
            dynamicDataSource.setDefaultTargetDataSource(primaryDataSource);
        }
        
        // 设置数据源映射
        Map<Object, Object> targetDataSources = new ConcurrentHashMap<>();
        
        // 添加从配置文件中读取的数据源
        if (properties.getDatasource() != null && !properties.getDatasource().isEmpty()) {
            properties.getDatasource().forEach((name, dsProperties) -> {
                DataSource dataSource = properties.getDataSource(name, dsProperties);
                if (dataSource != null) {
                    targetDataSources.put(name, dataSource);
                    log.info("Registered datasource: {}", name);
                }
            });
        }
        
        // 如果默认数据源存在且主数据源名称不在目标数据源中，添加它
        if (primaryDataSource != null && !targetDataSources.containsKey(properties.getPrimary())) {
            targetDataSources.put(properties.getPrimary(), primaryDataSource);
        }
        
        // 设置目标数据源
        if (!targetDataSources.isEmpty()) {
            dynamicDataSource.setTargetDataSources(targetDataSources);
        }
        
        // 设置严格模式
        dynamicDataSource.setStrictMode(properties.isStrict());
        
        // 设置主数据源名称
        dynamicDataSource.setPrimaryDataSourceKey(properties.getPrimary());
        
        // 初始化数据源
        dynamicDataSource.afterPropertiesSet();
        
        log.info("DynamicDataSource initialized successfully with {} datasources", targetDataSources.size());
        return dynamicDataSource;
    }
    
    /**
     * 创建动态数据源事务管理器
     * @param dynamicDataSource 动态数据源
     * @return 事务管理器
     */
    @Bean(name = "dynamicTransactionManager")
    @ConditionalOnMissingBean(name = "dynamicTransactionManager")
    public PlatformTransactionManager dynamicTransactionManager(DynamicDataSource dynamicDataSource) {
        log.info("Initializing dynamicTransactionManager");
        return new DataSourceTransactionManager(dynamicDataSource);
    }
    
    /**
     * 数据源管理器Bean
     * @return 数据源管理器实例
     */
    @Bean
    @ConditionalOnMissingBean(DataSourceManager.class)
    public DataSourceManager dataSourceManager() {
        log.info("Initializing DataSourceManager");
        return new DefaultDataSourceManager();
    }
}