package com.bone.metadata.sdk.support.dataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 数据源管理器 - 提供函数式数据源切换能力
 * 使用模板方法模式简化数据源切换逻辑
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
     * 在指定数据源下执行操作
     * @param dataSourceName 数据源名称
     * @param action 要执行的操作
     * @param <T> 返回值类型
     * @return 操作结果
     */
    public <T> T withDataSource(String dataSourceName, Supplier<T> action) {
        Assert.notNull(dataSourceName, "Data source name cannot be null");
        Assert.notNull(action, "Action cannot be null");
        
        if (log.isDebugEnabled()) {
            log.debug("Switching to data source: {}", dataSourceName);
        }
        return DataSourceContextHolder.executeInDataSourceWithResult(dataSourceName, action);
    }
    
    /**
     * 在指定数据源下执行无返回值的操作
     * @param dataSourceName 数据源名称
     * @param action 要执行的操作
     */
    public void withDataSource(String dataSourceName, Consumer<Void> action) {
        Assert.notNull(dataSourceName, "Data source name cannot be null");
        Assert.notNull(action, "Action cannot be null");
        
        withDataSource(dataSourceName, () -> {
            action.accept(null);
            return null;
        });
    }
    
    /**
     * 使用指定数据源执行操作（使用Runnable形式）
     * @param dataSourceName 数据源标识
     * @param action 需要执行的操作
     */
    public void withDataSource(String dataSourceName, Runnable action) {
        Assert.notNull(dataSourceName, "Data source name cannot be null");
        Assert.notNull(action, "Action cannot be null");
        
        DataSourceContextHolder.executeInDataSource(dataSourceName, action);
    }
    
    /**
     * 使用指定数据源执行操作（使用Function形式，支持传入参数）
     * @param <T> 输入参数类型
     * @param <R> 返回值类型
     * @param dataSourceName 数据源标识
     * @param input 输入参数
     * @param function 函数式操作
     * @return 操作的返回值
     */
    public <T, R> R withDataSource(String dataSourceName, T input, Function<T, R> function) {
        Assert.notNull(dataSourceName, "Data source name cannot be null");
        Assert.notNull(function, "Function cannot be null");
        
        return withDataSource(dataSourceName, () -> function.apply(input));
    }
    
    /**
     * 使用指定数据源执行操作（使用Consumer形式，支持传入参数）
     * @param <T> 输入参数类型
     * @param dataSourceName 数据源标识
     * @param input 输入参数
     * @param consumer 消费者操作
     */
    public <T> void withDataSource(String dataSourceName, T input, Consumer<T> consumer) {
        Assert.notNull(dataSourceName, "Data source name cannot be null");
        Assert.notNull(consumer, "Consumer cannot be null");
        
        withDataSource(dataSourceName, () -> consumer.accept(input));
    }
    
    /**
     * 执行写操作（使用主库）
     * @param action 要执行的操作
     * @param <T> 返回值类型
     * @return 操作结果
     */
    public <T> T withMaster(Supplier<T> action) {
        return withDataSource("master", action);
    }
    
    /**
     * 执行写操作（使用主库）- 无返回值
     * @param action 要执行的操作
     */
    public void withMaster(Consumer<Void> action) {
        withDataSource("master", action);
    }
    
    /**
     * 使用主数据源执行操作（无返回值）
     * @param action 需要执行的操作
     */
    public void withMaster(Runnable action) {
        withDataSource("master", action);
    }
    
    /**
     * 执行读操作（使用从库）
     * @param action 要执行的操作
     * @param <T> 返回值类型
     * @return 操作结果
     */
    public <T> T withSlave(Supplier<T> action) {
        return withDataSource("slave", action);
    }
    
    /**
     * 执行读操作（使用从库）- 无返回值
     * @param action 要执行的操作
     */
    public void withSlave(Consumer<Void> action) {
        withDataSource("slave", action);
    }
    
    /**
     * 使用从数据源执行操作（无返回值）
     * @param action 需要执行的操作
     */
    public void withSlave(Runnable action) {
        withDataSource("slave", action);
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
        return dynamicDataSource.containsDataSource(dataSource);
    }
    
    /**
     * 获取当前可用的数据源数量
     * @return 数据源数量
     */
    public int getDataSourceCount() {
        if (dynamicDataSource == null) {
            return 0;
        }
        return dynamicDataSource.getDataSourceCount();
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
        
        dynamicDataSource.addDataSource(key, dataSource);
        log.info("Added datasource dynamically: {}", key);
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
        
        dynamicDataSource.removeDataSource(key);
        log.info("Removed datasource dynamically: {}", key);
    }
    
    @Override
    public void afterPropertiesSet() {
        log.info("DataSourceManager initialized");
    }
}