package com.bone.metadata.sdk.support.dataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 多数据源测试配置类
 * 用于设置测试环境和嵌入式数据源
 */
@Configuration
public class MultiDataSourceTestConfig {
    
    /**
     * 创建主数据源
     */
    @Bean
    public DataSource masterDataSource() {
        return DataSourceBuilder.create()
                .url("jdbc:h2:mem:master_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();
    }
    
    /**
     * 创建从数据源
     */
    @Bean
    public DataSource slaveDataSource() {
        return DataSourceBuilder.create()
                .url("jdbc:h2:mem:slave_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();
    }
    
    /**
     * 创建租户A数据源
     */
    @Bean
    public DataSource tenantADataSource() {
        return DataSourceBuilder.create()
                .url("jdbc:h2:mem:tenant_a_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();
    }
    
    /**
     * 创建动态数据源
     */
    @Bean
    public DynamicDataSource dynamicDataSource(DataSource masterDataSource, 
                                              DataSource slaveDataSource, 
                                              DataSource tenantADataSource) {
        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        
        // 设置默认数据源
        dynamicDataSource.setDefaultTargetDataSource(masterDataSource);
        
        // 设置目标数据源映射
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("master", masterDataSource);
        targetDataSources.put("slave", slaveDataSource);
        targetDataSources.put("tenant_a", tenantADataSource);
        
        dynamicDataSource.setTargetDataSources(targetDataSources);
        dynamicDataSource.setPrimary("master");
        dynamicDataSource.setStrict(false);
        
        // 初始化
        dynamicDataSource.afterPropertiesSet();
        
        return dynamicDataSource;
    }
    
    /**
     * 测试用的数据源属性配置
     */
    @Bean
    public DynamicDataSourceProperties dynamicDataSourceProperties() {
        DynamicDataSourceProperties properties = new DynamicDataSourceProperties();
        properties.setPrimary("master");
        properties.setStrict(false);
        
        // 设置数据源配置
        Map<String, DynamicDataSourceProperties.DataSourceProperties> datasource = new HashMap<>();
        
        // 主数据源配置
        DynamicDataSourceProperties.DataSourceProperties masterProps = new DynamicDataSourceProperties.DataSourceProperties();
        masterProps.setUrl("jdbc:h2:mem:master_db");
        masterProps.setUsername("sa");
        masterProps.setPassword("");
        masterProps.setDriverClassName("org.h2.Driver");
        datasource.put("master", masterProps);
        
        // 从数据源配置
        DynamicDataSourceProperties.DataSourceProperties slaveProps = new DynamicDataSourceProperties.DataSourceProperties();
        slaveProps.setUrl("jdbc:h2:mem:slave_db");
        slaveProps.setUsername("sa");
        slaveProps.setPassword("");
        slaveProps.setDriverClassName("org.h2.Driver");
        datasource.put("slave", slaveProps);
        
        properties.setDatasource(datasource);
        
        return properties;
    }
}