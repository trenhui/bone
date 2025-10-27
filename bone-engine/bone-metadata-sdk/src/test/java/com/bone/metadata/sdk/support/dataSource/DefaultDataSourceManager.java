package com.bone.metadata.sdk.support.dataSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import javax.sql.DataSource;

/**
 * 简化的默认数据源管理器实现
 * 提供基本的数据源管理功能，不依赖外部框架
 */
public class DefaultDataSourceManager {
    
    /**
     * 数据源映射
     */
    private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();
    
    /**
     * 当前线程使用的数据源名称
     */
    private final ThreadLocal<String> currentDataSourceName = new ThreadLocal<>();
    
    /**
     * 默认数据源名称
     */
    private String defaultDataSourceName = "master";
    
    /**
     * 严格模式标志
     */
    private boolean strictMode = false;
    
    /**
     * 最大数据源数量
     */
    private int maxDataSourceCount = 100;
    
    /**
     * 注册数据源
     */
    public void registerDataSource(String dataSourceName, DataSource dataSource) {
        if (dataSourceName == null || dataSourceName.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源名称不能为空");
        }
        
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不能为null");
        }
        
        if (dataSources.containsKey(dataSourceName)) {
            throw new IllegalStateException("数据源已存在: " + dataSourceName);
        }
        
        if (dataSources.size() >= maxDataSourceCount) {
            throw new IllegalStateException("超出最大数据源数量限制: " + maxDataSourceCount);
        }
        
        dataSources.put(dataSourceName, dataSource);
        System.out.println("数据源注册成功: " + dataSourceName);
    }
    
    /**
     * 解除注册数据源
     */
    public boolean unregisterDataSource(String dataSourceName) {
        if (dataSourceName == null || dataSourceName.trim().isEmpty()) {
            return false;
        }
        
        boolean removed = dataSources.remove(dataSourceName) != null;
        
        // 如果移除的是当前使用的数据源，清除当前数据源
        String current = currentDataSourceName.get();
        if (removed && dataSourceName.equals(current)) {
            currentDataSourceName.remove();
        }
        
        System.out.println("数据源移除" + (removed ? "成功" : "失败") + ": " + dataSourceName);
        return removed;
    }
    
    /**
     * 获取数据源
     */
    public DataSource getDataSource(String dataSourceName) {
        if (dataSourceName == null || dataSourceName.trim().isEmpty()) {
            if (strictMode) {
                throw new IllegalArgumentException("数据源名称不能为空");
            }
            return null;
        }
        
        DataSource dataSource = dataSources.get(dataSourceName);
        
        if (dataSource == null && strictMode) {
            throw new IllegalArgumentException("数据源不存在: " + dataSourceName);
        }
        
        return dataSource;
    }
    
    /**
     * 获取当前数据源
     */
    public DataSource getCurrentDataSource() {
        String current = getCurrentDataSourceName();
        
        if (current == null) {
            // 如果没有设置当前数据源，尝试使用默认数据源
            if (dataSources.containsKey(defaultDataSourceName)) {
                return dataSources.get(defaultDataSourceName);
            } else if (!dataSources.isEmpty()) {
                // 如果默认数据源不存在，使用第一个数据源
                return dataSources.values().iterator().next();
            } else if (strictMode) {
                throw new IllegalStateException("没有可用的数据源");
            }
            return null;
        }
        
        return getDataSource(current);
    }
    
    /**
     * 获取当前数据源名称
     */
    public String getCurrentDataSourceName() {
        return currentDataSourceName.get();
    }
    
    /**
     * 切换数据源
     */
    public boolean switchDataSource(String dataSourceName) {
        if (dataSourceName == null || dataSourceName.trim().isEmpty()) {
            return false;
        }
        
        if (!dataSources.containsKey(dataSourceName)) {
            if (strictMode) {
                throw new IllegalArgumentException("数据源不存在: " + dataSourceName);
            }
            return false;
        }
        
        currentDataSourceName.set(dataSourceName);
        System.out.println("数据源切换成功: " + dataSourceName);
        return true;
    }
    
    /**
     * 重置数据源（切换到默认数据源）
     */
    public void resetDataSource() {
        currentDataSourceName.remove();
        System.out.println("数据源重置成功，将使用默认数据源: " + defaultDataSourceName);
    }
    
    /**
     * 获取所有数据源名称
     */
    public Set<String> getAllDataSourceNames() {
        return dataSources.keySet();
    }
    
    /**
     * 检查数据源是否健康
     */
    public boolean isDataSourceHealthy(String dataSourceName) {
        DataSource dataSource = getDataSource(dataSourceName);
        if (dataSource == null) {
            return false;
        }
        
        try (Connection connection = dataSource.getConnection()) {
            return !connection.isClosed();
        } catch (Exception e) {
            System.err.println("数据源健康检查失败: " + dataSourceName + ", 错误: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 在指定数据源上执行操作（有返回值）
     */
    public <T> T executeWithDataSource(String dataSourceName, Supplier<T> supplier) {
        String originalDataSource = getCurrentDataSourceName();
        
        try {
            switchDataSource(dataSourceName);
            return supplier.get();
        } finally {
            // 恢复原数据源
            if (originalDataSource != null) {
                switchDataSource(originalDataSource);
            } else {
                resetDataSource();
            }
        }
    }
    
    /**
     * 在指定数据源上执行操作（无返回值）
     */
    public void executeWithDataSource(String dataSourceName, Runnable runnable) {
        executeWithDataSource(dataSourceName, () -> {
            runnable.run();
            return null;
        });
    }
    
    /**
     * 设置严格模式
     */
    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }
    
    /**
     * 设置默认数据源名称
     */
    public void setDefaultDataSourceName(String defaultDataSourceName) {
        this.defaultDataSourceName = defaultDataSourceName;
    }
    
    /**
     * 设置最大数据源数量
     */
    public void setMaxDataSourceCount(int maxDataSourceCount) {
        this.maxDataSourceCount = maxDataSourceCount;
    }
    
    /**
     * 清理所有数据源
     */
    public void clearAllDataSources() {
        dataSources.clear();
        currentDataSourceName.remove();
        System.out.println("所有数据源已清理");
    }
}