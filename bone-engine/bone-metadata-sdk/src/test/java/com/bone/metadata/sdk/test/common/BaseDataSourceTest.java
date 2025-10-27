package com.bone.metadata.sdk.test.common;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源测试基类
 * 提供数据源上下文管理和线程隔离支持
 */
public abstract class BaseDataSourceTest {
    
    /**
     * 数据源上下文持有者
     * 用于在不同线程中管理数据源切换
     */
    public static class DataSourceContextHolder {
        private static final ThreadLocal<String> CONTEXT_HOLDER = new ThreadLocal<>();
        private static final Map<String, String> DATA_SOURCE_KEYS = new ConcurrentHashMap<>();
        
        /**
         * 设置当前线程使用的数据源
         */
        public static void setDataSource(String dataSource) {
            CONTEXT_HOLDER.set(dataSource);
            if (dataSource != null) {
                DATA_SOURCE_KEYS.put(Thread.currentThread().getName(), dataSource);
            }
        }
        
        /**
         * 获取当前线程使用的数据源
         */
        public static String getCurrentLookupKey() {
            return CONTEXT_HOLDER.get();
        }
        
        /**
         * 清除当前线程的数据源
         */
        public static void clearDataSource() {
            String threadName = Thread.currentThread().getName();
            DATA_SOURCE_KEYS.remove(threadName);
            CONTEXT_HOLDER.remove();
        }
        
        /**
         * 检查是否已设置数据源
         */
        public static boolean hasDataSource() {
            return CONTEXT_HOLDER.get() != null;
        }
        
        /**
         * 清除所有线程的数据源（仅用于测试）
         */
        public static void clearAll() {
            DATA_SOURCE_KEYS.clear();
            CONTEXT_HOLDER.remove();
        }
        
        /**
         * 获取当前所有线程的数据源状态（仅用于调试）
         */
        public static Map<String, String> getAllActiveDataSources() {
            return new HashMap<>(DATA_SOURCE_KEYS);
        }
    }
    
    /**
     * 测试前置准备
     * 确保测试隔离性：清理数据源上下文，为每个测试创建干净的环境
     */
    public void setUp() {
        // 清理数据源上下文，确保测试环境干净
        DataSourceContextHolder.clearAll();
        System.out.println("测试环境准备完成");
    }
    
    /**
     * 测试后置清理
     * 确保测试隔离性：清理数据源上下文，防止资源泄漏和测试间相互影响
     */
    public void tearDown() {
        // 清理数据源上下文，防止资源泄漏
        DataSourceContextHolder.clearAll();
        System.out.println("测试环境清理完成");
    }
    
    /**
     * 执行测试方法
     * 这是一个简化的测试执行方法，用于在没有JUnit的情况下运行测试
     */
    public void runTest(Runnable testMethod) {
        try {
            // 前置准备
            setUp();
            
            // 执行测试方法
            testMethod.run();
            
            // 后置清理
            tearDown();
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 切换数据源
     * 为测试提供数据源切换功能
     */
    protected void switchDataSource(String dataSourceName) {
        DataSourceContextHolder.setDataSource(dataSourceName);
        System.out.println("设置数据源: " + dataSourceName);
    }
    
    /**
     * 获取当前数据源
     * 为测试提供数据源信息查询功能
     */
    protected String getCurrentDataSource() {
        String dataSource = DataSourceContextHolder.getCurrentLookupKey();
        return dataSource != null ? dataSource : "default";
    }
    
    /**
     * 获取当前数据源状态信息
     */
    public String getCurrentDataSourceInfo() {
        StringBuilder info = new StringBuilder();
        info.append("当前线程数据源: ").append(DataSourceContextHolder.getCurrentLookupKey()).append("\n");
        info.append("所有活跃数据源: ").append(DataSourceContextHolder.getAllActiveDataSources());
        return info.toString();
    }
}