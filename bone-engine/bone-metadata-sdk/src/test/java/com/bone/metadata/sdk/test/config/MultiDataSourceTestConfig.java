package com.bone.metadata.sdk.test.config;

import com.bone.metadata.sdk.support.dataSource.DataSourceManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 多数据源测试配置类
 * 提供测试所需的数据源管理器实现
 */
@Configuration
public class MultiDataSourceTestConfig {
    
    /**
     * 数据源映射
     */
    private Map<String, DataSource> dataSources = new HashMap<>();
    private String defaultDataSourceName = "master";
    private boolean strictMode = false;
    
    /**
     * 构造器
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
        dataSources.put("master", new MockDataSource());
        // 模拟从数据源
        dataSources.put("slave", new MockDataSource());
        // 模拟租户数据源
        dataSources.put("tenant", new MockDataSource());
    }
    
    /**
     * 创建数据源管理器
     */
    @Bean
    public DataSourceManager dataSourceManager() {
        return new TestDataSourceManager();
    }
    
    /**
     * 测试用数据源管理器实现
     */
    public class TestDataSourceManager implements DataSourceManager {
        private String currentDataSourceName = defaultDataSourceName;
        
        @Override
        public void registerDataSource(String name, DataSource dataSource) {
            if (name == null || name.isEmpty() || dataSource == null) {
                throw new IllegalArgumentException("Data source name and instance cannot be null");
            }
            dataSources.put(name, dataSource);
        }
        
        @Override
        public boolean unregisterDataSource(String name) {
            return dataSources.remove(name) != null;
        }
        
        @Override
        public DataSource getDataSource(String name) {
            DataSource dataSource = dataSources.get(name);
            if (dataSource == null && strictMode) {
                throw new IllegalArgumentException("Data source not found: " + name);
            }
            return dataSource;
        }
        
        @Override
        public DataSource getCurrentDataSource() {
            DataSource dataSource = getDataSource(currentDataSourceName);
            if (dataSource == null && strictMode) {
                throw new IllegalStateException("No current data source available");
            }
            return dataSource;
        }
        
        @Override
        public boolean switchDataSource(String name) {
            if (dataSources.containsKey(name)) {
                currentDataSourceName = name;
                return true;
            }
            return false;
        }
        
        @Override
        public void resetDataSource() {
            currentDataSourceName = defaultDataSourceName;
        }
        
        @Override
        public Set<String> getAllDataSourceNames() {
            return new HashSet<>(dataSources.keySet());
        }
        
        @Override
        public boolean isDataSourceHealthy(String name) {
            return dataSources.containsKey(name);
        }
        
        @Override
        public String getCurrentDataSourceName() {
            return currentDataSourceName;
        }
        
        @Override
        public <T> T executeWithDataSource(String dataSourceName, Supplier<T> action) {
            String originalDataSource = getCurrentDataSourceName();
            try {
                switchDataSource(dataSourceName);
                return action.get();
            } finally {
                resetDataSource();
            }
        }
        
        @Override
        public CompletableFuture<Void> executeAsyncWithDataSource(String dataSourceName, Runnable action) {
            return CompletableFuture.runAsync(() -> {
                executeWithDataSource(dataSourceName, () -> {
                    action.run();
                    return null;
                });
            });
        }
        
        @Override
        public <T> CompletableFuture<T> executeAsyncWithDataSource(String dataSourceName, Supplier<T> action) {
            return CompletableFuture.supplyAsync(() -> executeWithDataSource(dataSourceName, action));
        }
        
        @Override
        public void setDefaultDataSourceName(String defaultDataSourceName) {
            this.defaultDataSourceName = defaultDataSourceName;
        }
        
        @Override
        public void setStrictMode(boolean strictMode) {
            this.strictMode = strictMode;
        }
    }
    
    /**
     * 模拟数据源实现
     */
    public static class MockDataSource implements DataSource {
        @Override
        public java.sql.Connection getConnection() {
            return null;
        }
        
        @Override
        public java.sql.Connection getConnection(String username, String password) {
            return null;
        }
        
        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }
        
        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
        
        // 以下方法是JDBC 4.1+添加的，但为了兼容性提供空实现
        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }
        
        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }
        
        @Override
        public void setLoginTimeout(int seconds) {
        }
        
        @Override
        public int getLoginTimeout() {
            return 0;
        }
        
        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getLogger(MockDataSource.class.getName());
        }
    }
}