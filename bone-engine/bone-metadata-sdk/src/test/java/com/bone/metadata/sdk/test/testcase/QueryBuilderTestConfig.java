package com.bone.metadata.sdk.test.testcase;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 简单的测试配置类，仅提供必要的数据库连接
 */
@Configuration
public class QueryBuilderTestConfig {

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
}