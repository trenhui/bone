package com.bone.metadata.sdk.test.config;

import java.util.HashMap;
import java.util.Map;

/**
 * 简化的多数据源测试配置类
 * 移除了所有外部依赖
 */
public class MultiDataSourceTestConfig {
    
    /**
     * 数据源映射
     */
    private Map<String, Object> dataSources = new HashMap<>();
    
    /**
     * 简化的构造器
     */
    public MultiDataSourceTestConfig() {
        // 初始化简化的数据源
        initDataSources();
    }
    
    /**
     * 初始化数据源
     */
    private void initDataSources() {
        // 模拟主数据源
        dataSources.put("masterDataSource", new HashMap<>());
        // 模拟从数据源
        dataSources.put("slaveDataSource", new HashMap<>());
        // 模拟租户数据源
        dataSources.put("tenantDataSource", new HashMap<>());
    }
    
    /**
     * 获取数据源
     */
    public Object getDataSource(String name) {
        return dataSources.get(name);
    }
    
    /**
     * 获取默认数据源
     */
    public Object getDefaultDataSource() {
        return getDataSource("masterDataSource");
    }
    
    /**
     * 设置数据源
     */
    public void setDataSource(String name, Object dataSource) {
        dataSources.put(name, dataSource);
    }
    
    /**
     * 获取所有数据源
     */
    public Map<String, Object> getAllDataSources() {
        return new HashMap<>(dataSources);
    }
    
    /**
     * 切换数据源
     */
    public void switchDataSource(String name) {
        System.out.println("切换到数据源: " + name);
    }
    
    /**
     * 创建简化的数据源管理器
     */
    public DataSourceManager createDataSourceManager() {
        return new DataSourceManager();
    }
    
    /**
     * 简化的数据源管理器内部类
     */
    public class DataSourceManager {
        private String currentDataSource = "masterDataSource";
        
        public void setCurrentDataSource(String name) {
            this.currentDataSource = name;
        }
        
        public String getCurrentDataSource() {
            return currentDataSource;
        }
        
        public void registerDataSource(String name, Object dataSource) {
            dataSources.put(name, dataSource);
        }
    }
}