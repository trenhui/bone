package com.bone.metadata.sdk.test.testcase;

import com.bone.metadata.sdk.query.SqlBuilder;

import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.sql.processor.SqlProcessorFactory;
import com.bone.metadata.sdk.sql.template.SqlFragmentLoader;
import com.bone.metadata.sdk.sql.template.SqlTemplate;
import com.bone.metadata.sdk.sql.template.SqlTemplateLoader;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;

/**
 * QueryBuilder测试配置类
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = {
    "com.bone.metadata.sdk.query",
    "com.bone.metadata.sdk.test.model",
    "com.bone.metadata.sdk.extension.repository",
    "com.bone.metadata.sdk.sql.dialect"
})
public class QueryBuilderTestConfig {

    /**
     * 配置H2内存数据库
     */
    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
    
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
    
    @Bean
    public TestDataInitializer testDataInitializer(JdbcTemplate jdbcTemplate) {
        return new TestDataInitializer(jdbcTemplate);
    }

    @Bean
    public NamedParameterJdbcOperations namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    // SqlConfigProperties已在组件扫描的包中定义，此处移除Bean定义避免冲突

    // 移除重复的sqlProcessorFactory定义，它已在组件扫描的包中被定义

    // sqlTemplateLoader方法已在组件扫描的包中定义，此处移除@Bean注解避免冲突
    // public com.bone.metadata.sdk.sql.template.SqlTemplateLoader sqlTemplateLoader() {
    //     return new com.bone.metadata.sdk.sql.template.SqlTemplateLoader() {
    //         @Override
    //         public com.bone.metadata.sdk.sql.template.SqlTemplate loadTemplate(String templateId) {
    //             return new com.bone.metadata.sdk.sql.template.SqlTemplate();
    //         }

    //         @Override
    //         public void refreshTemplate(String templateId) {
    //             // 空实现，测试环境不需要刷新模板
    //         }

    //         @Override
    //         public java.util.Map<String, com.bone.metadata.sdk.sql.template.SqlTemplate> loadTemplates(java.util.Collection<String> templateIds) {
    //             return new java.util.HashMap<>();
    //         }

    //         @Override
    //         public com.bone.metadata.sdk.sql.template.SqlTemplate loadTemplate(java.lang.reflect.Method method, String templateId) {
    //             return new com.bone.metadata.sdk.sql.template.SqlTemplate();
    //         }
    //     };
    // }

    @Bean
    public SqlExecutor sqlExecutor(NamedParameterJdbcOperations jdbcOperations, 
                                 com.bone.metadata.sdk.sql.template.SqlTemplateLoader sqlTemplateLoader, 
                                 SqlProcessorFactory sqlProcessorFactory,
                                 SqlConfigProperties sqlConfigProperties) {
        return new SqlExecutor(jdbcOperations, sqlTemplateLoader, sqlProcessorFactory, sqlConfigProperties);
    }

    // 将QueryBuilder的初始化移到单独的Bean中，避免循环依赖
    @Bean
    public QueryBuilderInitializer queryBuilderInitializer(SqlExecutor sqlExecutor, SqlBuilder sqlBuilder) {
        return new QueryBuilderInitializer(sqlExecutor, sqlBuilder);
    }
    
    public static class QueryBuilderInitializer {
        public QueryBuilderInitializer(SqlExecutor sqlExecutor, SqlBuilder sqlBuilder) {
            QueryBuilder.setSqlExecutor(sqlExecutor);
            QueryBuilder.setSqlBuilder(sqlBuilder);
        }
    }
    
    /**
     * 初始化测试所需的表结构
     */
    public static class TestDataInitializer {
        
        private final JdbcTemplate jdbcTemplate;
        
        public TestDataInitializer(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }
        
        @PostConstruct
        public void init() {
            // 创建column_allocation表
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS column_allocation (" +
                "id BIGINT PRIMARY KEY, " +
                "tenant_id BIGINT NOT NULL, " +
                "app_code VARCHAR(50) NOT NULL, " +
                "biz_identity_code VARCHAR(50) NOT NULL, " +
                "entity_type VARCHAR(100) NOT NULL, " +
                "data_type VARCHAR(20) NOT NULL, " +
                "column_name VARCHAR(100) NOT NULL, " +
                "column_index INT NOT NULL, " +
                "status VARCHAR(20) NOT NULL, " +
                "version BIGINT DEFAULT 0, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "created_by VARCHAR(50), " +
                "updated_by VARCHAR(50), " +
                "UNIQUE KEY unique_column (tenant_id, app_code, biz_identity_code, entity_type, column_name)" +
                ")");
        }
    }
}