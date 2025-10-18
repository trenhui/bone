package com.bone.metadata.sdk.test.testcase;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import jakarta.annotation.PostConstruct;

/**
 * 简单测试配置类，用于隔离测试Spring上下文初始化
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = {
    "com.bone.metadata.sdk.extension.repository",
    "com.bone.metadata.sdk.sql.dialect"
})
public class SimpleTestConfig {

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:simpleTest;DB_CLOSE_DELAY=-1;MODE=MySQL");
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