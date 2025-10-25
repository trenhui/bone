package com.bone.metadata.sdk.test.config;

import com.bone.metadata.sdk.support.dataSource.DynamicDataSource;
import com.bone.metadata.sdk.support.dataSource.DataSourceManager;
import com.bone.metadata.sdk.support.dataSource.DefaultDataSourceManager;
import com.bone.metadata.sdk.support.dataSource.DataSourceAnnotationInterceptor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.HashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * 多数据源测试配置类
 * <p>提供测试用的多数据源环境，包括主数据源、从数据源和租户数据源</p>
 * <p>遵循Spring Boot测试配置最佳实践，提供清晰、可维护的测试基础设施</p>
 */
@TestConfiguration
@ComponentScan("com.bone.metadata.sdk.test.service")
public class MultiDataSourceTestConfig {

    /**
     * 创建主数据源（使用H2嵌入式数据库）
     * @return 主数据源实例
     */
    @Bean(name = "masterDataSource")
    public DataSource masterDataSource() {
        return createEmbeddedDatabase("masterDB");
    }
    
    /**
     * 创建从数据源（使用H2嵌入式数据库）
     * @return 从数据源实例
     */
    @Bean(name = "slaveDataSource")
    public DataSource slaveDataSource() {
        return createEmbeddedDatabase("slaveDB");
    }
    
    /**
     * 创建租户A数据源（用于多租户测试）
     * @return 租户A数据源实例
     */
    @Bean(name = "tenantADataSource")
    public DataSource tenantADataSource() {
        return new EmbeddedDatabaseBuilder()
                .setName("tenantADB")
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .addScript("classpath:data.sql")
                .build();
    }
    
    /**
     * 创建嵌入式数据库的辅助方法，避免重复代码
     * @param databaseName 数据库名称
     * @return 配置好的嵌入式数据库
     */
    private DataSource createEmbeddedDatabase(String databaseName) {
        EmbeddedDatabaseBuilder builder = new EmbeddedDatabaseBuilder()
                .setName(databaseName)
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql");
        
        // 确保添加数据脚本
        builder.addScript("classpath:data.sql");
        
        // 构建并初始化数据源
        DataSource dataSource = builder.build();
        
        // 显式创建必要的表，防止schema.sql执行失败时的问题
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user (id INT PRIMARY KEY, name VARCHAR(100), age INT)");
        } catch (Exception e) {
            // 表可能已存在，忽略错误
        }
        
        return dataSource;
    }
    
    /**
     * 配置动态数据源，支持数据源路由和切换
     * @param masterDataSource 主数据源
     * @param slaveDataSource 从数据源
     * @param tenantADataSource 租户A数据源
     * @return 配置完成的动态数据源
     */
    @Bean
    @Primary
    public DynamicDataSource dynamicDataSource(
            DataSource masterDataSource,
            DataSource slaveDataSource,
            DataSource tenantADataSource) {
        
        // 使用流式API创建数据源映射，提高代码可读性和可维护性
        Map<Object, Object> targetDataSources = Stream.of(
                Map.entry("master", masterDataSource),
                Map.entry("slave", slaveDataSource),
                Map.entry("tenantA", tenantADataSource)
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, 
                (oldValue, newValue) -> newValue, LinkedHashMap::new));
        
        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        dynamicDataSource.setTargetDataSources(targetDataSources);
        dynamicDataSource.setDefaultTargetDataSource(masterDataSource);
        dynamicDataSource.afterPropertiesSet();
        
        return dynamicDataSource;
    }
    
    /**
     * 配置默认JdbcTemplate（使用动态数据源）
     * @param dynamicDataSource 动态数据源
     * @return 默认JdbcTemplate实例
     */
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DynamicDataSource dynamicDataSource) {
        return new JdbcTemplate(dynamicDataSource);
    }
    
    /**
     * 配置主数据源JdbcTemplate（用于测试验证）
     * @param masterDataSource 主数据源
     * @return JdbcTemplate实例
     */
    @Bean("masterJdbcTemplate")
    public JdbcTemplate masterJdbcTemplate(@Qualifier("masterDataSource") DataSource masterDataSource) {
        return new JdbcTemplate(masterDataSource);
    }
    
    /**
     * 配置从数据源JdbcTemplate（用于测试验证）
     * @param slaveDataSource 从数据源
     * @return JdbcTemplate实例
     */
    @Bean("slaveJdbcTemplate")
    public JdbcTemplate slaveJdbcTemplate(@Qualifier("slaveDataSource") DataSource slaveDataSource) {
        return new JdbcTemplate(slaveDataSource);
    }
    
    /**
     * 配置数据源管理器，提供程序化数据源切换能力
     * @return 数据源管理器实例
     */
    @Bean
    public DataSourceManager dataSourceManager() {
        // 创建数据源管理器，这里简化实现
        return new DefaultDataSourceManager();
    }
    
    /**
     * 配置数据源注解拦截器，处理@DataSourceSwitch注解
     * @return 数据源注解拦截器实例
     */
    @Bean
    public DataSourceAnnotationInterceptor dataSourceAnnotationInterceptor() {
        return new DataSourceAnnotationInterceptor();
    }
    
    /**
     * 配置AOP切面，拦截带有@DataSourceSwitch注解的方法调用
     * @param interceptor 数据源注解拦截器
     * @return AOP切面顾问
     */
    @Bean
    public Advisor dataSourceAdvisor(DataSourceAnnotationInterceptor interceptor) {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("@annotation(com.bone.metadata.sdk.support.dataSource.DataSourceSwitch) || " +
                              "@within(com.bone.metadata.sdk.support.dataSource.DataSourceSwitch)");
        return new DefaultPointcutAdvisor(pointcut, interceptor);
    }
}