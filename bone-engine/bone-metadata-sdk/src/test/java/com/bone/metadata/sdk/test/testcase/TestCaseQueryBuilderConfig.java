package com.bone.metadata.sdk.test.testcase;


import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.bone.metadata.sdk.sql.dialect.DatabaseDialect;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.domain.exception.ExceptionHandler;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import com.bone.metadata.sdk.sql.processor.SqlProcessorFactory;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.sql.template.SqlTemplateLoader;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.query.BatchCompiledQuery;
import com.bone.metadata.sdk.query.criteria.Criteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 简单的测试配置类，仅提供必要的数据库连接
 */
@Configuration
public class TestCaseQueryBuilderConfig {
    
    @Autowired
    private ExceptionHandler exceptionHandler;
    
    /**
     * 在应用启动时设置QueryBuilder的静态异常处理器
     */
    // 初始化方法，在Bean创建后自动调用
    public void init() {
        // 设置QueryBuilder的静态异常处理器
        com.bone.metadata.sdk.query.dsl.QueryBuilder.setExceptionHandler(exceptionHandler);
    }

    /**
     * 配置嵌入式H2数据源
     */
    @Bean
    public DataSource dataSource() {
        return DataSourceBuilder.create()
                .url("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL")
                .driverClassName("org.h2.Driver")
                .username("sa")
                .password("")
                .build();
    }

    /**
     * 配置JdbcTemplate用于数据库操作
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        // 手动初始化表结构和数据
        initializeDatabase(jdbcTemplate);
        return jdbcTemplate;
    }
    
    /**
     * 手动初始化数据库表和数据
     */
    private void initializeDatabase(JdbcTemplate jdbcTemplate) {
        // 创建表结构
        jdbcTemplate.execute("DROP TABLE IF EXISTS users CASCADE");
        jdbcTemplate.execute("DROP TABLE IF EXISTS roles CASCADE");
        
        // 创建角色表
        jdbcTemplate.execute("CREATE TABLE roles (id BIGINT PRIMARY KEY, name VARCHAR(100), code VARCHAR(50))");
        
        // 创建用户表
        jdbcTemplate.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, username VARCHAR(100), password VARCHAR(100), email VARCHAR(255), role_id BIGINT, age INT, status INT, FOREIGN KEY (role_id) REFERENCES roles(id))");

        // 插入测试数据
        jdbcTemplate.execute("INSERT INTO roles(id, name, code) VALUES (1, '管理员', 'ADMIN')");
        jdbcTemplate.execute("INSERT INTO roles(id, name, code) VALUES (2, '普通用户', 'USER')");

        jdbcTemplate.execute("INSERT INTO users(id, username, password, email, role_id, age, status) VALUES (1, 'admin', '123456', 'admin@test.com', 1, 30, 1)");
        jdbcTemplate.execute("INSERT INTO users(id, username, password, email, role_id, age, status) VALUES (2, 'user1', '123456', 'user1@test.com', 2, 25, 1)");
        jdbcTemplate.execute("INSERT INTO users(id, username, password, email, role_id, age, status) VALUES (3, 'user2', '123456', 'user2@test.com', 2, 28, 0)");
    }
    
    /**
     * 配置事务管理器，支持@Transactional注解
     */
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
    
    /**
     * 配置NamedParameterJdbcOperations
     */
    @Bean
    public NamedParameterJdbcOperations namedParameterJdbcOperations(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
    
    /**
     * 配置SqlConfigProperties
     */
    @Bean
    public SqlConfigProperties sqlConfigProperties() {
        return Mockito.mock(SqlConfigProperties.class);
    }
    
    /**
     * 配置SqlTemplateLoader
     */
    @Bean
    public SqlTemplateLoader sqlTemplateLoader() {
        return Mockito.mock(SqlTemplateLoader.class);
    }
    
    /**
     * 配置SqlProcessorFactory
     */
    @Bean
    public SqlProcessorFactory sqlProcessorFactory() {
        return Mockito.mock(SqlProcessorFactory.class);
    }
    
    /**
     * 配置SqlExecutor
     */
    @Bean
    public SqlExecutor sqlExecutor(NamedParameterJdbcOperations namedParameterJdbcOperations,
                                  SqlConfigProperties sqlConfigProperties,
                                  SqlProcessorFactory sqlProcessorFactory) {
        return Mockito.mock(SqlExecutor.class);
    }
    
    /**
     * 配置ExceptionHandler
     */
    @Bean
    public ExceptionHandler exceptionHandler() {
        return Mockito.mock(ExceptionHandler.class);
    }
    
    /**
     * 配置ExtensionCoordinator
     */
    @Bean
    public ExtensionCoordinator extensionCoordinator(ApplicationContext applicationContext) {
        return Mockito.mock(ExtensionCoordinator.class);
    }
    
    /**
     * 配置MetadataService
     */
    @Bean
    public MetadataService metadataService() {
        MetadataService mock = Mockito.mock(MetadataService.class);
        Mockito.when(mock.isHealthy()).thenReturn(true);
        return mock;
    }
    
    /**
     * 配置DatabaseDialect
     */
    @Bean
    public DatabaseDialect databaseDialect() {
        return Mockito.mock(DatabaseDialect.class);
    }
    
    /**
     * 配置SqlBuilder
     */
    @Bean
    public SqlBuilder sqlBuilder(MetadataService metadataService,
                               DatabaseDialect databaseDialect) {
        SqlBuilder mock = Mockito.mock(SqlBuilder.class);
        Mockito.when(mock.buildSelect(Mockito.any(), Mockito.any())).thenReturn(new CompiledQuery("SELECT * FROM test", Collections.emptyMap()));
        return mock;
    }
    
    /**
     * 配置DistributedLockUtil
     */
    @Bean
    public DistributedLockUtil distributedLockUtil() {
        return new DistributedLockUtil() {
            public Lock getLock(String lockName) {
                return new ReentrantReadWriteLock().writeLock();
            }
            
            public ReadWriteLock getReadWriteLock(String lockName) {
                return new ReentrantReadWriteLock();
            }
        };
    }
    
    /**
     * 模拟分布式锁工具接口
     */
    public interface DistributedLockUtil {
        Lock getLock(String lockName);
        ReadWriteLock getReadWriteLock(String lockName);
    }
}