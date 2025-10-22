package com.bone.metadata.sdk.test.testcase;

import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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

import com.bone.metadata.sdk.config.SqlConfigProperties;
import com.bone.metadata.sdk.executor.SqlExecutor;
import com.bone.metadata.sdk.processor.SqlProcessorFactory;
import com.bone.metadata.sdk.query.builder.SqlBuilder;
import com.bone.metadata.sdk.query.builder.model.BatchCompiledQuery;
import com.bone.metadata.sdk.query.builder.model.CompiledQuery;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.support.database.DatabaseDialect;
import com.bone.metadata.sdk.support.database.H2DatabaseDialect;
import com.bone.metadata.sdk.support.dynamic.ExtensionCoordinator;
import com.bone.metadata.sdk.support.exception.ExceptionHandler;
import com.bone.metadata.sdk.support.metadata.MetadataService;
import com.bone.metadata.sdk.support.template.SqlTemplateLoader;

/**
 * 简单的测试配置类，仅提供必要的数据库连接
 */
@Configuration
public class QueryBuilderTestConfig {
    
    @Autowired
    private ExceptionHandler exceptionHandler;
    
    /**
     * 在应用启动时设置QueryBuilder的静态异常处理器
     */
    @PostConstruct
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
        SqlConfigProperties properties = new SqlConfigProperties();
        properties.setShowSql(true);
        return properties;
    }
    
    /**
     * 配置SqlTemplateLoader
     */
    @Bean
    public SqlTemplateLoader sqlTemplateLoader() {
        return new SqlTemplateLoader() {
            @Override
            public String loadTemplate(String templatePath) {
                return "";
            }
            
            @Override
            public Map<String, String> loadTemplates(String directoryPath) {
                return new HashMap<>();
            }
        };
    }
    
    /**
     * 配置SqlProcessorFactory
     */
    @Bean
    public SqlProcessorFactory sqlProcessorFactory(SqlTemplateLoader sqlTemplateLoader) {
        return new SqlProcessorFactory(sqlTemplateLoader);
    }
    
    /**
     * 配置SqlExecutor
     */
    @Bean
    public SqlExecutor sqlExecutor(NamedParameterJdbcOperations namedParameterJdbcOperations,
                                  SqlConfigProperties sqlConfigProperties,
                                  SqlProcessorFactory sqlProcessorFactory) {
        return new SqlExecutor(namedParameterJdbcOperations, sqlConfigProperties, sqlProcessorFactory) {
            @Override
            public <T> List<T> list(String sql, Map<String, Object> params, Class<T> resultType) {
                return Collections.emptyList();
            }
            
            @Override
            public <T> T single(String sql, Map<String, Object> params, Class<T> resultType) {
                return null;
            }
            
            @Override
            public long count(String sql, Map<String, Object> params) {
                return 0;
            }
            
            @Override
            public int update(String sql, Map<String, Object> params) {
                return 0;
            }
            
            @Override
            public int[] batchUpdate(String sql, List<Map<String, Object>> batchParams) {
                return new int[0];
            }
        };
    }
    
    /**
     * 配置ExceptionHandler
     */
    @Bean
    public ExceptionHandler exceptionHandler() {
        return new ExceptionHandler() {
            @Override
            public RuntimeException handleException(Exception e, String message) {
                return new RuntimeException(message, e);
            }
            
            // 添加QueryBuilder类中反射调用的方法签名
            public RuntimeException handleException(Exception e) {
                return new RuntimeException("Query error", e);
            }
        };
    }
    
    /**
     * 配置ExtensionCoordinator
     */
    @Bean
    public ExtensionCoordinator extensionCoordinator() {
        return new ExtensionCoordinator() {
            @Override
            public <T> List<T> getExtensions(Class<T> extensionType) {
                return Collections.emptyList();
            }
            
            @Override
            public <T> T getExtension(Class<T> extensionType, String name) {
                return null;
            }
        };
    }
    
    /**
     * 配置MetadataService
     */
    @Bean
    public MetadataService metadataService() {
        return new MetadataService() {
            @Override
            public Map<String, String> getTableColumns(String tableName) {
                return new HashMap<>();
            }
            
            @Override
            public String getPrimaryKey(String tableName) {
                return "id";
            }
        };
    }
    
    /**
     * 配置DatabaseDialect
     */
    @Bean
    public DatabaseDialect databaseDialect() {
        return new H2DatabaseDialect();
    }
    
    /**
     * 配置SqlBuilder
     */
    @Bean
    public SqlBuilder sqlBuilder(DatabaseDialect databaseDialect,
                               MetadataService metadataService,
                               ExceptionHandler exceptionHandler) {
        return new SqlBuilder(databaseDialect, metadataService, exceptionHandler) {
            @Override
            public void init() {
                // 初始化逻辑
            }
            
            @Override
            public CompiledQuery buildSelect(Class<?> cls, Criteria<?> c) {
                return new CompiledQuery("SELECT * FROM " + cls.getSimpleName().toLowerCase(), Collections.emptyMap());
            }
            
            @Override
            public BatchCompiledQuery buildBatchInsert(Class<?> cls, List<?> list) {
                return new BatchCompiledQuery("INSERT INTO " + cls.getSimpleName().toLowerCase() + " VALUES (:id, :name)", 
                        list.stream().map(obj -> Collections.emptyMap()).toList());
            }
            
            @Override
            public CompiledQuery buildCount(Class<?> cls, Criteria<?> c) {
                return new CompiledQuery("SELECT COUNT(*) FROM " + cls.getSimpleName().toLowerCase(), Collections.emptyMap());
            }
            
            @Override
            public CompiledQuery buildDelete(Class<?> cls, Criteria<?> c) {
                return new CompiledQuery("DELETE FROM " + cls.getSimpleName().toLowerCase(), Collections.emptyMap());
            }
            
            @Override
            public CompiledQuery buildUpdate(Class<?> cls, Map<String, Object> updateValues, Criteria<?> c) {
                return new CompiledQuery("UPDATE " + cls.getSimpleName().toLowerCase() + " SET id = :id", Collections.emptyMap());
            }
        };
    }
    
    /**
     * 配置DistributedLockUtil
     */
    @Bean
    public DistributedLockUtil distributedLockUtil() {
        return new DistributedLockUtil() {
            @Override
            public Lock getLock(String lockName) {
                return new ReentrantReadWriteLock().writeLock();
            }
            
            @Override
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