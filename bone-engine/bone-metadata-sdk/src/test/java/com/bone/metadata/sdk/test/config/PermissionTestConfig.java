package com.bone.metadata.sdk.test.config;

import java.util.HashMap;
import java.util.Map;

/**
 * 简化的权限测试配置类
 * 移除了所有外部依赖
 */
public class PermissionTestConfig {
    
    /**
     * 配置映射
     */
    private final Map<String, Object> configs = new HashMap<>();
    
    /**
     * 简化的构造器
     */
    public PermissionTestConfig() {
        // 初始化默认配置
        initDefaultConfigs();
    }
    
    /**
     * 初始化默认配置
     */
    private void initDefaultConfigs() {
        // 创建并设置简化的数据源
        SimpleDataSource dataSource = new SimpleDataSource();
        configs.put("dataSource", dataSource);
        
        // 创建并设置简化的JdbcTemplate
        SimpleJdbcTemplate jdbcTemplate = new SimpleJdbcTemplate(dataSource);
        configs.put("jdbcTemplate", jdbcTemplate);
        
        // 创建并设置简化的NamedParameterJdbcOperations
        SimpleNamedParameterJdbcOperations namedJdbcOps = new SimpleNamedParameterJdbcOperations(dataSource);
        configs.put("namedParameterJdbcOperations", namedJdbcOps);
        
        // 创建并设置简化的列分配方言
        configs.put("columnAllocationDialect", new SimpleColumnAllocationDialect());
        configs.put("columnAllocationDialectFactory", new SimpleColumnAllocationDialectFactory());
    }
    
    /**
     * 获取配置项
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String name) {
        return (T) configs.get(name);
    }
    
    /**
     * 设置配置项
     */
    public void setConfig(String name, Object value) {
        configs.put(name, value);
    }
    
    /**
     * 获取数据源
     */
    public SimpleDataSource getDataSource() {
        return getConfig("dataSource");
    }
    
    /**
     * 获取JdbcTemplate
     */
    public SimpleJdbcTemplate getJdbcTemplate() {
        return getConfig("jdbcTemplate");
    }
    
    /**
     * 获取NamedParameterJdbcOperations
     */
    public SimpleNamedParameterJdbcOperations getNamedParameterJdbcOperations() {
        return getConfig("namedParameterJdbcOperations");
    }
    
    /**
     * 获取列分配方言
     */
    public SimpleColumnAllocationDialect getColumnAllocationDialect() {
        return getConfig("columnAllocationDialect");
    }
    
    /**
     * 获取列分配方言工厂
     */
    public SimpleColumnAllocationDialectFactory getColumnAllocationDialectFactory() {
        return getConfig("columnAllocationDialectFactory");
    }
    
    /**
     * 简化的数据源内部类
     */
    public static class SimpleDataSource {
        public void init() {
            System.out.println("初始化权限测试数据源");
            // 模拟执行SQL脚本
            executeScript("schema.sql");
            executeScript("data.sql");
        }
        
        private void executeScript(String scriptName) {
            System.out.println("执行脚本: " + scriptName);
        }
    }
    
    /**
     * 简化的JdbcTemplate内部类
     */
    public static class SimpleJdbcTemplate {
        private final SimpleDataSource dataSource;
        
        public SimpleJdbcTemplate(SimpleDataSource dataSource) {
            this.dataSource = dataSource;
        }
        
        public void execute(String sql) {
            System.out.println("执行SQL: " + sql);
        }
        
        public int update(String sql) {
            System.out.println("更新SQL: " + sql);
            return 1;
        }
    }
    
    /**
     * 简化的NamedParameterJdbcOperations内部类
     */
    public static class SimpleNamedParameterJdbcOperations {
        private final SimpleDataSource dataSource;
        
        public SimpleNamedParameterJdbcOperations(SimpleDataSource dataSource) {
            this.dataSource = dataSource;
        }
        
        public void execute(String sql, Map<String, Object> params) {
            System.out.println("执行命名参数SQL: " + sql);
            System.out.println("参数: " + params);
        }
        
        public int update(String sql, Map<String, Object> params) {
            System.out.println("更新命名参数SQL: " + sql);
            System.out.println("参数: " + params);
            return 1;
        }
    }
    
    /**
     * 简化的列分配方言内部类
     */
    public static class SimpleColumnAllocationDialect {
        public String getAllocatedColumn(String columnName) {
            return columnName;
        }
    }
    
    /**
     * 简化的列分配方言工厂内部类
     */
    public static class SimpleColumnAllocationDialectFactory {
        public SimpleColumnAllocationDialect createDialect(String dialectType) {
            return new SimpleColumnAllocationDialect();
        }
    }
}