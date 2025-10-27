package com.bone.metadata.sdk.test.config;

import java.util.HashMap;
import java.util.Map;

/**
 * 简化的测试配置类
 * 移除了所有外部依赖
 */
public class SimpleTestConfig {
    
    /**
     * 配置映射
     */
    private final Map<String, Object> configs = new HashMap<>();
    
    /**
     * 简化的构造器
     */
    public SimpleTestConfig() {
        // 初始化默认配置
        initDefaultConfigs();
    }
    
    /**
     * 初始化默认配置
     */
    private void initDefaultConfigs() {
        // 设置简化的配置项
        configs.put("metadataService", new SimpleMetadataService());
        configs.put("sqlBuilder", new SimpleSqlBuilder());
        configs.put("sqlExecutor", new SimpleSqlExecutor());
        configs.put("databaseDialect", new SimpleDatabaseDialect());
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
     * 获取元数据服务
     */
    public SimpleMetadataService getMetadataService() {
        return (SimpleMetadataService) configs.get("metadataService");
    }
    
    /**
     * 获取SQL构建器
     */
    public SimpleSqlBuilder getSqlBuilder() {
        return (SimpleSqlBuilder) configs.get("sqlBuilder");
    }
    
    /**
     * 简化的元数据服务内部类
     */
    public static class SimpleMetadataService {
        public void loadTableMetadata() {
            System.out.println("加载表元数据");
        }
        
        public String getTableName(Class<?> entityClass) {
            return entityClass.getSimpleName();
        }
    }
    
    /**
     * 简化的SQL构建器内部类
     */
    public static class SimpleSqlBuilder {
        public String buildQuery(String tableName) {
            return "SELECT * FROM " + tableName;
        }
        
        public String buildCountQuery(String tableName) {
            return "SELECT COUNT(*) FROM " + tableName;
        }
    }
    
    /**
     * 简化的SQL执行器内部类
     */
    public static class SimpleSqlExecutor {
        public void executeUpdate(String sql) {
            System.out.println("执行更新SQL: " + sql);
        }
        
        public int executeBatch(String sql) {
            System.out.println("执行批量SQL: " + sql);
            return 0;
        }
    }
    
    /**
     * 简化的数据库方言内部类
     */
    public static class SimpleDatabaseDialect {
        public String getPaginationSql(String sql, int offset, int limit) {
            return sql + " LIMIT " + limit + " OFFSET " + offset;
        }
        
        public String getQuoteIdentifier(String identifier) {
            return "`" + identifier + "`";
        }
    }
    
    /**
     * 简化的扩展协调器内部类
     */
    public static class SimpleExtensionCoordinator {
        public void init() {
            System.out.println("初始化扩展协调器");
        }
        
        public void shutdown() {
            System.out.println("关闭扩展协调器");
        }
    }
}