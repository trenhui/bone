package com.bone.metadata.sdk.support.dataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * 数据源上下文持有者
 * 使用ThreadLocal管理当前线程的数据源选择
 * 使用LIFO栈结构支持嵌套切换场景
 */
public final class DataSourceContextHolder {
    
    private static final Logger log = LoggerFactory.getLogger(DataSourceContextHolder.class);
    
    // 使用LIFO栈结构支持嵌套切换
    private static final ThreadLocal<Deque<String>> LOOKUP_KEY_HOLDER = 
            ThreadLocal.withInitial(() -> new ArrayDeque<>(8));
    
    /**
     * 设置当前线程使用的数据源
     * @param dataSource 数据源标识
     * @return 设置的数据源标识
     */
    public static String setDataSource(String dataSource) {
        Objects.requireNonNull(dataSource, "Data source cannot be null");
        LOOKUP_KEY_HOLDER.get().push(dataSource);
        log.debug("Set datasource to: {}, stack size: {}", dataSource, LOOKUP_KEY_HOLDER.get().size());
        return dataSource;
    }
    
    /**
     * 弹出并获取当前线程正在使用的数据源标识
     * @return 当前数据源标识
     */
    public static String clearDataSource() {
        Deque<String> deque = LOOKUP_KEY_HOLDER.get();
        String dataSource = deque.poll();
        log.debug("Cleared datasource: {}, remaining stack size: {}", dataSource, deque.size());
        if (deque.isEmpty()) {
            LOOKUP_KEY_HOLDER.remove(); // 防止内存泄漏
        }
        return dataSource;
    }
    
    /**
     * 获取当前线程正在使用的数据源标识
     * @return 当前数据源标识，如果没有设置则返回null
     */
    public static String getCurrentLookupKey() {
        Deque<String> deque = LOOKUP_KEY_HOLDER.get();
        String current = deque.isEmpty() ? null : deque.peek();
        log.debug("Current datasource lookup key: {}", current);
        return current;
    }
    
    /**
     * 判断是否有正在使用的数据源
     * @return 是否有正在使用的数据源
     */
    public static boolean hasDataSource() {
        return !LOOKUP_KEY_HOLDER.get().isEmpty();
    }
    
    /**
     * 判断数据源上下文是否为空
     * @return 数据源上下文是否为空
     */
    public static boolean isEmpty() {
        return LOOKUP_KEY_HOLDER.get().isEmpty();
    }
    
    /**
     * 清除当前线程所有的数据源标识
     */
    public static void clearAll() {
        Deque<String> deque = LOOKUP_KEY_HOLDER.get();
        int size = deque.size();
        deque.clear();
        LOOKUP_KEY_HOLDER.remove();
        log.debug("Cleared all datasource contexts, removed {} entries", size);
    }
    
    /**
     * 获取当前数据源上下文栈的深度
     * 用于调试和监控
     * @return 上下文栈深度
     */
    public static int getContextStackDepth() {
        Deque<String> deque = LOOKUP_KEY_HOLDER.get();
        return deque.size();
    }
    
    /**
     * 安全地执行操作，确保数据源上下文被正确清理
     * @param dataSource 数据源标识
     * @param action 需要执行的操作
     */
    public static void executeInDataSource(String dataSource, Runnable action) {
        Objects.requireNonNull(dataSource, "Data source cannot be null");
        Objects.requireNonNull(action, "Action cannot be null");
        
        boolean success = false;
        try {
            setDataSource(dataSource);
            action.run();
            success = true;
        } catch (Exception e) {
            log.error("Error executing in datasource: {}", dataSource, e);
            throw e;
        } finally {
            if (success) {
                clearDataSource();
            } else {
                // 发生异常时，为了安全起见，清理所有上下文
                clearAll();
            }
        }
    }
    
    /**
     * 安全地执行有返回值的操作，确保数据源上下文被正确清理
     * @param <T> 返回值类型
     * @param dataSource 数据源标识
     * @param action 需要执行的操作
     * @return 操作的返回值
     */
    public static <T> T executeInDataSourceWithResult(String dataSource, java.util.function.Supplier<T> action) {
        Objects.requireNonNull(dataSource, "Data source cannot be null");
        Objects.requireNonNull(action, "Action cannot be null");
        
        boolean success = false;
        try {
            setDataSource(dataSource);
            T result = action.get();
            success = true;
            return result;
        } catch (Exception e) {
            log.error("Error executing in datasource: {}", dataSource, e);
            throw e;
        } finally {
            if (success) {
                clearDataSource();
            } else {
                // 发生异常时，为了安全起见，清理所有上下文
                clearAll();
            }
        }
    }
}