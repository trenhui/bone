package com.bone.metadata.sdk.support.dataSource;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Supplier;
import java.util.function.Consumer;

/**
 * 数据源管理器 - 提供函数式数据源切换能力
 * 使用模板方法模式简化数据源切换逻辑
 */
@Component
public class DataSourceManager {
    
    private static final Logger log = LoggerFactory.getLogger(DataSourceManager.class);
    
    /**
     * 在指定数据源下执行操作
     * @param dataSourceName 数据源名称
     * @param action 要执行的操作
     * @param <T> 返回值类型
     * @return 操作结果
     */
    public <T> T withDataSource(String dataSourceName, Supplier<T> action) {
        boolean hasPrevious = DataSourceContextHolder.hasDataSource();
        String previousDataSource = null;
        
        try {
            if (log.isDebugEnabled()) {
                log.debug("Switching to data source: {}", dataSourceName);
            }
            previousDataSource = DataSourceContextHolder.setDataSource(dataSourceName);
            return action.get();
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("Restoring data source context, previous: {}", previousDataSource);
            }
            DataSourceContextHolder.clearDataSource();
            
            // 避免嵌套调用时的内存泄漏
            if (!hasPrevious && DataSourceContextHolder.hasDataSource()) {
                DataSourceContextHolder.clearAll();
            }
        }
    }
    
    /**
     * 在指定数据源下执行无返回值的操作
     * @param dataSourceName 数据源名称
     * @param action 要执行的操作
     */
    public void withDataSource(String dataSourceName, Consumer<Void> action) {
        withDataSource(dataSourceName, () -> {
            action.accept(null);
            return null;
        });
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
}