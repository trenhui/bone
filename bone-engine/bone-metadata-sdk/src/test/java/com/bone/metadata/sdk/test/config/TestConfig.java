package com.bone.metadata.sdk.test.config;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 简化的测试配置类
 * 移除了所有外部依赖
 */
public class TestConfig {
    
    /**
     * 配置映射
     */
    private final Map<String, Object> configMap = new HashMap<>();
    
    /**
     * 简化的构造器
     */
    public TestConfig() {
        // 初始化配置
        initConfig();
    }
    
    /**
     * 初始化配置
     */
    private void initConfig() {
        // 设置基本配置项
        configMap.put("dataSource", new SimpleDataSource());
        configMap.put("transactionManager", new SimpleTransactionManager());
        configMap.put("redisClient", new SimpleRedisClient());
        configMap.put("exceptionHandler", new SimpleExceptionHandler());
        configMap.put("dataSourceManager", new SimpleDataSourceManager());
    }
    
    /**
     * 获取配置项
     */
    public Object getConfig(String name) {
        return configMap.get(name);
    }
    
    /**
     * 设置配置项
     */
    public void setConfig(String name, Object value) {
        configMap.put(name, value);
    }
    
    /**
     * 获取数据源
     */
    public SimpleDataSource getDataSource() {
        return (SimpleDataSource) configMap.get("dataSource");
    }
    
    /**
     * 获取事务管理器
     */
    public SimpleTransactionManager getTransactionManager() {
        return (SimpleTransactionManager) configMap.get("transactionManager");
    }
    
    /**
     * 简化的数据源内部类
     */
    public static class SimpleDataSource {
        public void init() {
            System.out.println("初始化数据源");
        }
        
        public void close() {
            System.out.println("关闭数据源");
        }
    }
    
    /**
     * 简化的事务管理器内部类
     */
    public static class SimpleTransactionManager {
        private final AtomicBoolean transactionActive = new AtomicBoolean(false);
        
        public void beginTransaction() {
            transactionActive.set(true);
            System.out.println("开始事务");
        }
        
        public void commit() {
            transactionActive.set(false);
            System.out.println("提交事务");
        }
        
        public void rollback() {
            transactionActive.set(false);
            System.out.println("回滚事务");
        }
        
        public boolean isTransactionActive() {
            return transactionActive.get();
        }
    }
    
    /**
     * 简化的Redis客户端内部类
     */
    public static class SimpleRedisClient {
        public SimpleAtomicLong getAtomicLong(String key) {
            return new SimpleAtomicLong(key);
        }
        
        public SimpleLock getLock(String name) {
            return new SimpleLock(name);
        }
    }
    
    /**
     * 简化的原子长整型内部类
     */
    public static class SimpleAtomicLong {
        private final String key;
        private long value;
        
        public SimpleAtomicLong(String key) {
            this.key = key;
            this.value = 0;
        }
        
        public long incrementAndGet() {
            return ++value;
        }
        
        public long get() {
            return value;
        }
    }
    
    /**
     * 简化的锁内部类
     */
    public static class SimpleLock {
        private final String name;
        private final AtomicBoolean locked = new AtomicBoolean(false);
        
        public SimpleLock(String name) {
            this.name = name;
        }
        
        public boolean tryLock(long waitTime, long leaseTime) {
            boolean acquired = locked.compareAndSet(false, true);
            System.out.println("尝试获取锁: " + name + " 结果: " + acquired);
            return acquired;
        }
        
        public void unlock() {
            locked.set(false);
            System.out.println("释放锁: " + name);
        }
    }
    
    /**
     * 简化的异常处理器内部类
     */
    public static class SimpleExceptionHandler {
        private static final SimpleExceptionHandler INSTANCE = new SimpleExceptionHandler();
        
        private SimpleExceptionHandler() {
        }
        
        public static SimpleExceptionHandler getInstance() {
            return INSTANCE;
        }
        
        public void handleException(Exception e) {
            System.err.println("处理异常: " + e.getMessage());
        }
    }
    
    /**
     * 简化的数据源管理器内部类
     */
    public static class SimpleDataSourceManager {
        private String currentDataSource = "default";
        
        public void setCurrentDataSource(String name) {
            this.currentDataSource = name;
            System.out.println("切换到数据源: " + name);
        }
        
        public String getCurrentDataSource() {
            return currentDataSource;
        }
    }
}