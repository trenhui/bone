package com.bone.metadata.sdk.support.dataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 简化的数据源上下文持有者
 * 用于管理线程本地的数据源上下文
 */
public class DataSourceContextHolder {
    
    /**
     * 线程本地的数据源上下文栈
     */
    private static final ThreadLocal<List<String>> CONTEXT_HOLDER = ThreadLocal.withInitial(ArrayList::new);
    
    /**
     * 设置当前数据源
     */
    public static void setDataSource(String dataSource) {
        List<String> contextStack = CONTEXT_HOLDER.get();
        contextStack.add(dataSource);
        System.out.println("设置数据源: " + dataSource + " (栈深度: " + contextStack.size() + ")");
    }
    
    /**
     * 获取当前数据源
     */
    public static String getCurrentLookupKey() {
        List<String> contextStack = CONTEXT_HOLDER.get();
        if (contextStack.isEmpty()) {
            return null;
        }
        return contextStack.get(contextStack.size() - 1);
    }
    
    /**
     * 清理当前数据源（弹出栈顶）
     */
    public static String clearDataSource() {
        List<String> contextStack = CONTEXT_HOLDER.get();
        if (contextStack.isEmpty()) {
            return null;
        }
        
        String removedDataSource = contextStack.remove(contextStack.size() - 1);
        System.out.println("清理数据源: " + removedDataSource + " (剩余栈深度: " + contextStack.size() + ")");
        
        // 如果栈为空，清理ThreadLocal以避免内存泄漏
        if (contextStack.isEmpty()) {
            CONTEXT_HOLDER.remove();
        }
        
        return removedDataSource;
    }
    
    /**
     * 清理所有数据源上下文
     */
    public static void clearAll() {
        CONTEXT_HOLDER.remove();
        System.out.println("清理所有数据源上下文");
    }
    
    /**
     * 检查是否有数据源上下文
     */
    public static boolean hasDataSource() {
        List<String> contextStack = CONTEXT_HOLDER.get();
        return contextStack != null && !contextStack.isEmpty();
    }
    
    /**
     * 获取上下文栈深度
     */
    public static int getContextStackDepth() {
        List<String> contextStack = CONTEXT_HOLDER.get();
        return contextStack != null ? contextStack.size() : 0;
    }
    
    /**
     * 在指定数据源上下文中执行操作（无返回值）
     */
    public static void executeInDataSource(String dataSource, Runnable action) {
        // 保存原始上下文深度，用于恢复
        int originalDepth = getContextStackDepth();
        String originalDataSource = getCurrentLookupKey();
        
        try {
            // 设置指定的数据源
            setDataSource(dataSource);
            
            // 执行操作
            action.run();
        } finally {
            // 恢复原始上下文
            while (getContextStackDepth() > originalDepth) {
                clearDataSource();
            }
        }
    }
    
    /**
     * 在指定数据源上下文中执行操作（有返回值）
     */
    public static <T> T executeInDataSourceWithResult(String dataSource, Supplier<T> supplier) {
        // 保存原始上下文深度，用于恢复
        int originalDepth = getContextStackDepth();
        
        try {
            // 设置指定的数据源
            setDataSource(dataSource);
            
            // 执行操作并返回结果
            return supplier.get();
        } finally {
            // 恢复原始上下文
            while (getContextStackDepth() > originalDepth) {
                clearDataSource();
            }
        }
    }
}