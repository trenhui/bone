package com.bone.procurement.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 自定义数据源配置
 * 配置HikariCP数据源，不使用JPA
 */
@Configuration
public class DataSourceConfig {

    /**
     * 配置数据源属性
     */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        DataSourceProperties properties = new DataSourceProperties();
        // 明确指定H2驱动，确保不被其他驱动覆盖
        properties.setDriverClassName("org.h2.Driver");
        return properties;
    }

    /**
     * 配置HikariCP数据源
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(com.zaxxer.hikari.HikariDataSource.class)
                .build();
    }

    /**
     * 配置NamedParameterJdbcOperations，用于bone-metadata-sdk
     */
    @Bean
    public NamedParameterJdbcOperations namedParameterJdbcOperations(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    /**
     * 配置事务管理器
     */
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}