package com.bone.metadata.sdk.test.config;

import com.bone.metadata.sdk.support.dataSource.DynamicDataSource;
import com.bone.metadata.sdk.support.dataSource.DataSourceContextHolder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 多数据源测试配置
 * 提供测试用的多数据源环境
 */
@TestConfiguration
public class MultiDataSourceTestConfig {
    
    /**
     * 创建主数据源（使用H2嵌入式数据库）
     * @return 主数据源
     */
    @Bean(name = "masterDataSource")
    public DataSource masterDataSource() {
        return new EmbeddedDatabaseBuilder()
                .setName("masterDB")
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .addScript("classpath:data.sql")
                .build();
    }
    
    /**
     * 创建从数据源（使用H2嵌入式数据库）
     * @return 从数据源
     */
    @Bean(name = "slaveDataSource")
    public DataSource slaveDataSource() {
        return new EmbeddedDatabaseBuilder()
                .setName("slaveDB")
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .addScript("classpath:data.sql")
                .build();
    }
    
    /**
     * 创建租户A数据源（用于多租户测试）
     * @return 租户A数据源
     */
    @Bean(name = "tenantADataSource")
    public DataSource tenantADataSource() {
        return new EmbeddedDatabaseBuilder()
                .setName("tenantADB")
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .build();
    }
    
    /**
     * 创建动态数据源
     * @param masterDataSource 主数据源
     * @param slaveDataSource 从数据源
     * @param tenantADataSource 租户A数据源
     * @return 动态数据源
     */
    @Bean
    public DynamicDataSource dynamicDataSource(
            DataSource masterDataSource,
            DataSource slaveDataSource,
            DataSource tenantADataSource) {
        
        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        
        // 配置目标数据源
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("master", masterDataSource);
        targetDataSources.put("slave", slaveDataSource);
        targetDataSources.put("tenantA", tenantADataSource);
        
        dynamicDataSource.setTargetDataSources(targetDataSources);
        
        // 设置默认数据源为主数据源
        dynamicDataSource.setDefaultTargetDataSource(masterDataSource);
        
        // 初始化
        dynamicDataSource.afterPropertiesSet();
        
        return dynamicDataSource;
    }
    
    /**
     * 配置主数据源JdbcTemplate（用于测试验证）
     * @param masterDataSource 主数据源
     * @return JdbcTemplate实例
     */
    @Bean(name = "masterJdbcTemplate")
    public JdbcTemplate masterJdbcTemplate(DataSource masterDataSource) {
        return new JdbcTemplate(masterDataSource);
    }
    
    /**
     * 配置从数据源JdbcTemplate（用于测试验证）
     * @param slaveDataSource 从数据源
     * @return JdbcTemplate实例
     */
    @Bean(name = "slaveJdbcTemplate")
    public JdbcTemplate slaveJdbcTemplate(DataSource slaveDataSource) {
        return new JdbcTemplate(slaveDataSource);
    }
    
    /**
     * 初始化测试数据
     */
    public void initTestData() {
        // 这个方法会在测试类中调用，用于初始化特定的测试数据
        // 确保不同数据源有不同的测试标识
    }
    
    /**
     * 配置DataSourceManager
     */
    @Bean
    public DataSourceManager dataSourceManager() {
        return new DataSourceManager();
    }
    
    /**
     * 配置数据源注解拦截器
     */
    @Bean
    public DataSourceAnnotationInterceptor dataSourceAnnotationInterceptor() {
        return new DataSourceAnnotationInterceptor();
    }
    
    /**
     * 配置AOP切面，处理@DS注解
     */
    @Bean
    public Advisor dataSourceAdvisor(DataSourceAnnotationInterceptor interceptor) {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("@annotation(com.bone.metadata.sdk.support.dataSource.annotation.DS)");
        return new DefaultPointcutAdvisor(pointcut, interceptor);
    }
}