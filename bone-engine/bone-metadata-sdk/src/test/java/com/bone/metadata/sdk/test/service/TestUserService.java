package com.bone.metadata.sdk.test.service;

import java.util.HashMap;
import java.util.Map;

/**
 * 简化的测试用用户服务类
 * 移除了所有外部依赖
 */
public class TestUserService {
    
    /**
     * 简化的数据源管理器
     */
    private static class DataSourceManager {
        private static final ThreadLocal<String> currentDataSource = new ThreadLocal<>();
        
        /**
         * 设置当前数据源
         */
        public static void setDataSource(String dataSourceName) {
            currentDataSource.set(dataSourceName);
            System.out.println("切换到数据源: " + dataSourceName);
        }
        
        /**
         * 获取当前数据源
         */
        public static String getCurrentDataSource() {
            return currentDataSource.get() != null ? currentDataSource.get() : "default";
        }
        
        /**
         * 清理当前数据源
         */
        public static void clearDataSource() {
            currentDataSource.remove();
        }
    }
    
    /**
     * 简化的JDBC模板内部类
     */
    private static class SimpleJdbcTemplate {
        private final String dataSourceName;
        
        /**
         * 构造器
         */
        public SimpleJdbcTemplate(String dataSourceName) {
            this.dataSourceName = dataSourceName;
        }
        
        /**
         * 更新方法
         */
        public void update(String sql, Object... args) {
            System.out.println("[" + dataSourceName + "] 执行更新SQL: " + sql);
            System.out.println("参数: " + java.util.Arrays.toString(args));
        }
        
        /**
         * 查询单个对象方法
         */
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            System.out.println("[" + dataSourceName + "] 执行查询SQL: " + sql);
            System.out.println("参数: " + java.util.Arrays.toString(args));
            
            // 简单模拟返回结果
            if (requiredType == String.class) {
                return requiredType.cast("测试用户_" + args[0]);
            }
            return null;
        }
    }
    
    /**
     * 主数据源JDBC模板
     */
    private final SimpleJdbcTemplate masterJdbcTemplate;
    
    /**
     * 从数据源JDBC模板
     */
    private final SimpleJdbcTemplate slaveJdbcTemplate;
    
    /**
     * 构造器
     */
    public TestUserService() {
        // 直接创建JDBC模板，不需要依赖注入
        this.masterJdbcTemplate = new SimpleJdbcTemplate("master");
        this.slaveJdbcTemplate = new SimpleJdbcTemplate("slave");
    }
    
    /**
     * 简化的数据源切换方法
     */
    private void switchDataSource(String dataSourceName) {
        DataSourceManager.setDataSource(dataSourceName);
    }
    
    /**
     * 使用主数据源创建用户（写操作）
     */
    public void createUser(Long id, String name) {
        // 手动切换数据源，替代注解功能
        switchDataSource("master");
        try {
            masterJdbcTemplate.update("INSERT INTO user (id, name) VALUES (?, ?)", id, name);
        } finally {
            // 清理数据源上下文
            DataSourceManager.clearDataSource();
        }
    }
    
    /**
     * 使用从数据源查询用户（读操作）
     */
    public String getUserNameById(Long id) {
        // 手动切换数据源，替代注解功能
        switchDataSource("slave");
        try {
            return slaveJdbcTemplate.queryForObject("SELECT name FROM user WHERE id = ?", String.class, id);
        } finally {
            // 清理数据源上下文
            DataSourceManager.clearDataSource();
        }
    }
    
    /**
     * 使用主数据源更新用户（写操作）
     */
    public void updateUserName(Long id, String newName) {
        // 手动切换数据源，替代注解功能
        switchDataSource("master");
        try {
            masterJdbcTemplate.update("UPDATE user SET name = ? WHERE id = ?", newName, id);
        } finally {
            // 清理数据源上下文
            DataSourceManager.clearDataSource();
        }
    }
    
    /**
     * 使用自定义数据源（租户数据源）
     */
    public void operateWithTenantDataSource(String tenantId, Long userId, String operation) {
        // 手动切换到租户数据源，替代注解功能
        String tenantDataSource = "tenant_" + tenantId;
        switchDataSource(tenantDataSource);
        try {
            System.out.println("在租户数据源[" + tenantDataSource + "]上执行操作: " + operation + " 用户ID: " + userId);
            // 这里可以调用相应的JDBC操作
        } finally {
            // 清理数据源上下文
            DataSourceManager.clearDataSource();
        }
    }
    
    /**
     * 获取当前使用的数据源信息
     */
    public String getCurrentDataSourceInfo() {
        return "当前使用的数据源: " + DataSourceManager.getCurrentDataSource();
    }
}