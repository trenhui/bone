package com.bone.metadata.sdk.test.config;

import java.util.HashMap;
import java.util.Map;

/**
 * 简化的QueryBuilder测试配置类
 * 移除了所有外部依赖
 */
public class QueryBuilderTestConfig {
    
    /**
     * 配置映射
     */
    private final Map<String, Object> configs = new HashMap<>();
    
    /**
     * 简化的构造器
     */
    public QueryBuilderTestConfig() {
        // 初始化默认配置
        initDefaultConfigs();
    }
    
    /**
     * 初始化默认配置
     */
    private void initDefaultConfigs() {
        // 设置简化的SQL配置
        configs.put("sqlBuilder", new SimpleSqlBuilder());
        configs.put("sqlExecutor", new SimpleSqlExecutor());
        configs.put("metadataService", new SimpleMetadataService());
        configs.put("extensionCoordinator", new SimpleExtensionCoordinator());
    }
    
    /**
     * 获取配置项
     */
    public Object getConfig(String name) {
        return configs.get(name);
    }
    
    /**
     * 设置配置项
     */
    public void setConfig(String name, Object config) {
        configs.put(name, config);
    }
    
    /**
     * 获取所有配置
     */
    public Map<String, Object> getAllConfigs() {
        return new HashMap<>(configs);
    }
    
    /**
     * 简化的SQL构建器内部类
     */
    public static class SimpleSqlBuilder {
        public String buildQuery(String entityName) {
            return "SELECT * FROM " + entityName;
        }
        
        public String buildInsert(String entityName) {
            return "INSERT INTO " + entityName + " VALUES (?)";
        }
        
        public String buildUpdate(String entityName) {
            return "UPDATE " + entityName + " SET ? WHERE id = ?";
        }
        
        public String buildDelete(String entityName) {
            return "DELETE FROM " + entityName + " WHERE id = ?";
        }
    }
    
    /**
     * 简化的SQL执行器内部类
     */
    public static class SimpleSqlExecutor {
        public void execute(String sql) {
            System.out.println("执行SQL: " + sql);
        }
        
        public <T> T executeQuery(String sql, Class<T> returnType) {
            System.out.println("执行查询SQL: " + sql);
            return null;
        }
    }
    
    /**
     * 简化的元数据服务内部类
     */
    public static class SimpleMetadataService {
        public void loadMetadata() {
            System.out.println("加载元数据");
        }
    }
    
    /**
     * 简化的扩展协调器内部类
     */
    public static class SimpleExtensionCoordinator {
        public void registerExtension(String name, Object extension) {
            System.out.println("注册扩展: " + name);
        }
    }
}