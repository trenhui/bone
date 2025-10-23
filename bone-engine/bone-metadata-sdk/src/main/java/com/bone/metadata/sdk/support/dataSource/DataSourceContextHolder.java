package com.bone.metadata.sdk.support.dataSource;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 数据源上下文持有者 - 管理线程级别的数据源切换
 * 使用LIFO栈结构支持嵌套切换场景
 */
public final class DataSourceContextHolder {
    // 使用LIFO栈结构支持嵌套切换
    private static final ThreadLocal<Deque<String>> LOOKUP_KEY_HOLDER = 
        ThreadLocal.withInitial(ArrayDeque::new);
    
    /**
     * 切换到指定数据源
     * @param dataSource 数据源名称
     * @return 切换后的数据源名称
     */
    public static String setDataSource(String dataSource) {
        LOOKUP_KEY_HOLDER.get().push(dataSource);
        return dataSource;
    }
    
    /**
     * 恢复至上一个数据源
     * @return 恢复前的数据源名称
     */
    public static String clearDataSource() {
        Deque<String> deque = LOOKUP_KEY_HOLDER.get();
        String dataSource = deque.poll();
        if (deque.isEmpty()) {
            LOOKUP_KEY_HOLDER.remove(); // 防止内存泄漏
        }
        return dataSource;
    }
    
    /**
     * 获取当前数据源标识
     * @return 当前数据源名称，若没有则返回null
     */
    public static String getCurrentLookupKey() {
        Deque<String> deque = LOOKUP_KEY_HOLDER.get();
        return deque.isEmpty() ? null : deque.peek();
    }
    
    /**
     * 判断当前是否有数据源标识
     * @return 是否有数据源标识
     */
    public static boolean hasDataSource() {
        Deque<String> deque = LOOKUP_KEY_HOLDER.get();
        return !deque.isEmpty();
    }
    
    /**
     * 清空数据源上下文（强制清理，不建议常规使用）
     */
    public static void clearAll() {
        LOOKUP_KEY_HOLDER.remove();
    }
}