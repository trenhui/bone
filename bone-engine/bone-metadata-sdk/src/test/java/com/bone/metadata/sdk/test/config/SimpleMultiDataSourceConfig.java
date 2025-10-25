package com.bone.metadata.sdk.test.config;

import com.bone.metadata.sdk.support.dataSource.DynamicDataSource;
import com.bone.metadata.sdk.support.dataSource.DataSourceManager;
import com.bone.metadata.sdk.support.dataSource.DefaultDataSourceManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * 简单的多数据源配置类
 * 用于测试动态数据源切换功能
 */
@Configuration
@EnableTransactionManagement
public class SimpleMultiDataSourceConfig {
    
    /**
     * 创建主数据源
     * @return 主数据源
     */
    @Bean
    public DataSource masterDataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("masterDb")
                .build();
    }
    
    /**
     * 创建从数据源
     * @return 从数据源
     */
    @Bean
    public DataSource slaveDataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("slaveDb")
                .build();
    }
    
    /**
     * 创建动态数据源
     * @return 动态数据源
     */
    @Primary
    @Bean
    public DataSource dynamicDataSource() {
        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        
        // 先创建数据源实例
        DataSource masterDs = masterDataSource();
        DataSource slaveDs = slaveDataSource();
        
        // 添加数据源
        dynamicDataSource.addDataSource("master", masterDs);
        dynamicDataSource.addDataSource("slave", slaveDs);
        
        // 设置默认目标数据源
        dynamicDataSource.setDefaultTargetDataSource(masterDs);
        
        return dynamicDataSource;
    }
    
    /**
     * 初始化数据源，创建表结构和测试数据
     * @param masterJdbcTemplate 主数据源的JdbcTemplate
     * @param slaveJdbcTemplate 从数据源的JdbcTemplate
     */
    @Bean
    public void initializeDataSource(JdbcTemplate masterJdbcTemplate, JdbcTemplate slaveJdbcTemplate) {
        // 初始化主数据源
        try {
            masterJdbcTemplate.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(100))");
            // 尝试先删除已有数据避免冲突
            masterJdbcTemplate.update("DELETE FROM users WHERE id = 100");
            // 插入测试数据
            masterJdbcTemplate.update("INSERT INTO users (id, name) VALUES (100, 'master_user')");
        } catch (Exception e) {
            System.err.println("初始化主数据源失败: " + e.getMessage());
        }
        
        // 初始化从数据源
        try {
            slaveJdbcTemplate.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(100))");
            // 尝试先删除已有数据避免冲突
            slaveJdbcTemplate.update("DELETE FROM users WHERE id = 100");
            // 插入测试数据
            slaveJdbcTemplate.update("INSERT INTO users (id, name) VALUES (100, 'slave_user')");
        } catch (Exception e) {
            System.err.println("初始化从数据源失败: " + e.getMessage());
        }
    }

    /**
     * 创建主JdbcTemplate（使用动态数据源）
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dynamicDataSource) {
        return new JdbcTemplate(dynamicDataSource);
    }
    
    /**
     * 创建主数据源专用JdbcTemplate
     * 仅用于初始化测试数据
     */
    @Bean("masterJdbcTemplate")
    public JdbcTemplate masterJdbcTemplate(@Qualifier("masterDataSource") DataSource masterDataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(masterDataSource);
        initializeDataSource(jdbcTemplate, "master_user");
        return jdbcTemplate;
    }
    
    /**
     * 创建从数据源专用JdbcTemplate
     * 仅用于初始化测试数据
     */
    @Bean("slaveJdbcTemplate")
    public JdbcTemplate slaveJdbcTemplate(@Qualifier("slaveDataSource") DataSource slaveDataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(slaveDataSource);
        initializeDataSource(jdbcTemplate, "slave_user");
        return jdbcTemplate;
    }
    
    /**
     * 创建数据源管理器
     */
    @Bean
    public DataSourceManager dataSourceManager() {
        return new DefaultDataSourceManager();
    }
    
    /**
     * 配置事务管理器
     * @param dynamicDataSource 动态数据源
     * @return 事务管理器实例
     */
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dynamicDataSource) {
        return new DataSourceTransactionManager(dynamicDataSource);
    }
}