package com.bone.metadata.sdk.support.dataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.util.function.Supplier;
import java.util.function.Consumer;

/**
 * 数据源管理器 - 提供高级数据源管理功能
 * 专注于数据源配置和事务操作，避免与DataSourceContextHolder的功能重复
 */
@Component
public class DataSourceManager implements InitializingBean {
    
    private static final Logger log = LoggerFactory.getLogger(DataSourceManager.class);
    
    /**
     * 动态数据源实例
     */
    @Autowired(required = false)
    private DynamicDataSource dynamicDataSource;
    
    /**
     * 使用指定数据源执行操作（有返回值）
     * @param dataSourceName 数据源名称
     * @param action 需要执行的操作
     * @param <T> 返回值类型
     * @return 操作结果
     */
    public <T> T withDataSource(String dataSourceName, Supplier<T> action) {
        return DataSourceContextHolder.executeInDataSourceWithResult(dataSourceName, action);
    }
    
    /**
     * 使用指定数据源执行操作（无返回值）
     * @param dataSourceName 数据源名称
     * @param action 需要执行的操作
     */
    public void withDataSource(String dataSourceName, Runnable action) {
        DataSourceContextHolder.executeInDataSource(dataSourceName, action);
    }
    

    
    /**
     * 在指定数据源上执行Consumer操作（无参数）
     * @param dataSourceName 数据源名称
     * @param action 需要执行的操作
     */
    public void withDataSource(String dataSourceName, Consumer<Void> action) {
        DataSourceContextHolder.executeInDataSource(dataSourceName, () -> action.accept(null));
    }
    
    /**
     * 在指定数据源上执行带有参数的Consumer操作
     * @param dataSourceName 数据源名称
     * @param value 传递给Consumer的参数值
     * @param action 需要执行的操作
     * @param <V> 参数类型
     */
    public <V> void withDataSource(String dataSourceName, V value, Consumer<V> action) {
        DataSourceContextHolder.executeInDataSource(dataSourceName, () -> action.accept(value));
    }
    
    /**
     * 执行写操作（使用主库）
     * @param action 要执行的操作
     * @param <T> 返回值类型
     * @return 操作结果
     */
    public <T> T withMaster(Supplier<T> action) {
        return DataSourceContextHolder.executeInDataSourceWithResult("master", action);
    }
    
    /**
     * 使用主数据源执行操作（无返回值）
     * @param action 需要执行的操作
     */
    public void withMaster(Runnable action) {
        DataSourceContextHolder.executeInDataSource("master", action);
    }
    
    /**
     * 使用主数据源执行Consumer操作（无输入参数）
     * @param action 需要执行的操作
     */
    public void withMaster(Consumer<Void> action) {
        DataSourceContextHolder.executeInDataSource("master", () -> action.accept(null));
    }
    
    /**
     * 使用主数据源执行带有参数的Consumer操作
     * @param value 传递给Consumer的参数值
     * @param action 需要执行的操作
     * @param <V> 参数类型
     */
    public <V> void withMaster(V value, Consumer<V> action) {
        DataSourceContextHolder.executeInDataSource("master", () -> action.accept(value));
    }
    
    /**
     * 执行读操作（使用从库）
     * @param action 要执行的操作
     * @param <T> 返回值类型
     * @return 操作结果
     */
    public <T> T withSlave(Supplier<T> action) {
        return DataSourceContextHolder.executeInDataSourceWithResult("slave", action);
    }
    
    /**
     * 使用从数据源执行操作（无返回值）
     * @param action 需要执行的操作
     */
    public void withSlave(Runnable action) {
        DataSourceContextHolder.executeInDataSource("slave", action);
    }
    
    /**
     * 使用从数据源执行Consumer操作（无输入参数）
     * @param action 需要执行的操作
     */
    public void withSlave(Consumer<Void> action) {
        DataSourceContextHolder.executeInDataSource("slave", () -> action.accept(null));
    }
    
    /**
     * 使用从数据源执行带有参数的Consumer操作
     * @param value 传递给Consumer的参数值
     * @param action 需要执行的操作
     * @param <V> 参数类型
     */
    public <V> void withSlave(V value, Consumer<V> action) {
        DataSourceContextHolder.executeInDataSource("slave", () -> action.accept(value));
    }
    
    /**
     * 在主数据源上执行事务操作
     * @param action 需要执行的事务操作
     */
    @Transactional(transactionManager = "dynamicTransactionManager", rollbackFor = Exception.class)
    public void withMasterTransaction(Runnable action) {
        withMaster(action);
    }
    
    /**
     * 在主数据源上执行事务操作（有返回值）
     * @param <T> 返回值类型
     * @param action 需要执行的事务操作
     * @return 操作的返回值
     */
    @Transactional(transactionManager = "dynamicTransactionManager", rollbackFor = Exception.class)
    public <T> T withMasterTransaction(Supplier<T> action) {
        return withMaster(action);
    }
    
    /**
     * 检查数据源是否可用
     * @param dataSource 数据源标识
     * @return 是否可用
     */
    public boolean isDataSourceAvailable(String dataSource) {
        if (dynamicDataSource == null) {
            return false;
        }
        // 使用正确的方法检查数据源是否存在
        try {
            return dynamicDataSource.containsDataSource(dataSource);
        } catch (Exception e) {
            log.warn("Error checking datasource availability: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取当前可用的数据源数量
     * @return 数据源数量
     */
    public int getDataSourceCount() {
        if (dynamicDataSource == null) {
            return 0;
        }
        try {
            return dynamicDataSource.getDataSourceCount();
        } catch (Exception e) {
            log.warn("Error getting datasource count: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * 动态添加数据源（运行时）
     * @param key 数据源标识
     * @param dataSource 数据源实例
     */
    public void addDataSource(String key, DataSource dataSource) {
        Assert.notNull(key, "Data source key cannot be null");
        Assert.notNull(dataSource, "Data source cannot be null");
        
        if (dynamicDataSource == null) {
            throw new IllegalStateException("DynamicDataSource is not initialized");
        }
        
        try {
            dynamicDataSource.addDataSource(key, dataSource);
            log.info("Added datasource dynamically: {}", key);
        } catch (Exception e) {
            log.error("Failed to add datasource: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 动态移除数据源（运行时）
     * @param key 数据源标识
     */
    public void removeDataSource(String key) {
        Assert.notNull(key, "Data source key cannot be null");
        
        if (dynamicDataSource == null) {
            throw new IllegalStateException("DynamicDataSource is not initialized");
        }
        
        try {
            dynamicDataSource.removeDataSource(key);
            log.info("Removed datasource dynamically: {}", key);
        } catch (Exception e) {
            log.error("Failed to remove datasource: {}", e.getMessage());
            throw e;
        }
    }
    
    @Override
    public void afterPropertiesSet() {
        log.info("DataSourceManager initialized");
    }
}