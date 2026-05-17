package com.bone.metadata.sdk.support.dataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.bone.metadata.sdk.support.dataSource.DataSourceContextHolder;

/**
 * {@link DataSourceManager} 接口的默认实现类。
 * 提供管理多个数据源的核心功能，包括数据源注册、切换、健康检查和指标收集。
 */
@Component
public class DefaultDataSourceManager implements DataSourceManager, InitializingBean {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultDataSourceManager.class);
    
    /**
     * Thread-safe storage for registered data sources.
     * Uses ConcurrentHashMap to ensure thread safety during concurrent access.
     */
    private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();
    
    /**
     * Atomic reference to the currently active data source name.
     * Ensures thread-safe updates and reads of the active data source.
     */
    private final AtomicReference<String> currentDataSourceName = new AtomicReference<>();
    
    /**
     * Name of the default data source to use when no specific data source is specified.
     * Default value is "master".
     */
    private String defaultDataSourceName = "master";
    
    /**
     * Flag indicating strict mode behavior. When enabled, operations will throw exceptions
     * if requested data sources are unavailable or non-existent.
     * Default value is false (non-strict mode).
     */
    private boolean strictMode = false;
    
    /**
     * Maximum number of data sources that can be registered.
     * Used to prevent resource exhaustion.
     * Default value is 50.
     */
    private int maxDataSourceCount = 50;
    
    /**
     * Default timeout in milliseconds for database operations.
     */
    private static final int DEFAULT_TIMEOUT_MS = 1000;
    
    /**
     * Interval between data source health checks in milliseconds.
     * Default value is 30,000 ms (30 seconds).
     */
    private static final long HEALTH_CHECK_INTERVAL_MS = 30000; // 30 seconds
    
    /**
     * Default number of retry attempts for failed data source operations.
     */
    private static final int DEFAULT_RETRY_COUNT = 2;
    
    /**
     * Thread-safe storage for data source health status information.
     */
    private final Map<String, DataSourceHealthStatus> dataSourceHealthStatus = new ConcurrentHashMap<>();
    
    /**
     * Thread-safe storage for data source usage metrics and statistics.
     */
    private final Map<String, DataSourceMetrics> dataSourceMetrics = new ConcurrentHashMap<>();
    
    /**
     * Scheduled executor service responsible for running periodic health checks.
     */
    private ScheduledExecutorService healthCheckScheduler;
    
    /**
     * 注册一个具有指定名称的新数据源。
     * 如果已存在相同名称的数据源，将被替换。
     * 在严格模式下，该方法会强制执行最大数据源数量限制。
     * 
     * @param name 数据源的注册名称
     * @param dataSource 要注册的数据源
     * @return 注册成功返回true，否则返回false
     * @throws IllegalArgumentException 如果名称或数据源为null/无效
     */
    @Override
    public void registerDataSource(String name, DataSource dataSource) {
        Assert.hasText(name, "Data source name cannot be empty");
        Assert.notNull(dataSource, "Data source instance cannot be null");
        
        // Check maximum data source count limit
        if (dataSources.size() >= maxDataSourceCount) {
            String errorMessage = String.format("Maximum data source count exceeded: %d", maxDataSourceCount);
            logger.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }
        
        // Check if data source already exists
        DataSource existing = dataSources.putIfAbsent(name, dataSource);
        if (existing != null) {
            String warningMessage = String.format("Data source already exists, registration failed: %s", name);
            logger.warn(warningMessage);
            throw new IllegalStateException(String.format("Data source already exists: %s", name));
        }
        
        // Initialize health status and metrics
        dataSourceHealthStatus.put(name, new DataSourceHealthStatus(name));
        dataSourceMetrics.put(name, new DataSourceMetrics(name));
        
        // If this is the first registered data source, set it as the current data source
        if (currentDataSourceName.get() == null) {
            currentDataSourceName.set(name);
            logger.info("First data source registered, set as current active data source: {}", name);
        } else {
            logger.info("Successfully registered data source: {}", name);
        }
    }
    
    /**
     * 注销具有指定名称的数据源。
     * 如果该数据源当前处于活动状态，将被停用。
     * 
     * @param name 要注销的数据源名称
     * @return 如果数据源被成功注销返回true，如果不存在则返回false
     */
    @Override
    public boolean unregisterDataSource(String name) {
        if (name == null || name.trim().isEmpty()) {
            logger.warn("Attempting to remove data source with empty name, operation ignored");
            return false;
        }
        
        DataSource removed = dataSources.remove(name);
        if (removed != null) {
            // Clean up related resources
            dataSourceHealthStatus.remove(name);
            dataSourceMetrics.remove(name);
            
            // If removed data source was the currently active one, set current data source name to null
            if (name.equals(currentDataSourceName.get())) {
                String previousDataSource = currentDataSourceName.get();
                currentDataSourceName.set(null);
                logger.info("Removed current active data source[{}], current data source set to null", previousDataSource);
            } else {
                logger.info("Successfully removed data source: {}", name);
            }
            return true;
        }
        
        logger.warn("Data source does not exist, removal operation failed: {}", name);
        return false;
    }
    
    /**
     * 获取具有指定名称的数据源。
     * 
     * @param name 要检索的数据源名称
     * @return 指定名称的数据源，如果未找到则返回null
     */
    @Override
    public DataSource getDataSource(String name) {
        if (name == null || name.trim().isEmpty()) {
            logger.warn("Attempting to retrieve data source with empty name");
            if (strictMode) {
                throw new IllegalArgumentException("Data source name cannot be empty");
            }
            return null;
        }
        
        DataSource dataSource = dataSources.get(name);
        
        if (dataSource == null) {
            logger.warn("Data source does not exist: {}", name);
            if (strictMode) {
                throw new IllegalArgumentException(String.format("Data source does not exist: %s", name));
            }
        }
        
        return dataSource;
    }
    
    /**
     * 获取当前活动的数据源。
     * 如果当前数据源不可用，尝试使用默认数据源。
     * 在严格模式下，如果没有可用的数据源，则抛出异常。
     * 
     * @return 当前活动的数据源，如果在非严格模式下无可用数据源则返回null
     * @throws IllegalStateException 如果没有可用的数据源且启用了严格模式
     */
    @Override
    public DataSource getCurrentDataSource() {
        String currentName = currentDataSourceName.get();
        DataSource dataSource = (currentName != null) ? dataSources.get(currentName) : null;
        
        // If current data source is unavailable, try using the default data source
        if (dataSource == null) {
            dataSource = dataSources.get(defaultDataSourceName);
            if (dataSource != null) {
                currentDataSourceName.set(defaultDataSourceName);
                logger.info("Current data source[{}] unavailable, automatically switched to default data source: {}", currentName, defaultDataSourceName);
            }
        }
        
        if (dataSource == null) {
            logger.error("No available data sources");
            if (strictMode) {
                throw new IllegalStateException("No available data sources");
            }
        }
        
        return dataSource;
    }
    
    /**
     * Switches the currently active data source.
     * Updates both the internal state and the DataSourceContextHolder.
     * 
     * @param name the name of the data source to switch to
     * @return true if the switch was successful, false otherwise
     * @throws IllegalArgumentException if data source doesn't exist and strict mode is enabled
     */
    @Override
    public boolean switchDataSource(String name) {
        if (name == null || name.trim().isEmpty()) {
            logger.warn("Attempting to switch to data source with empty name, operation ignored");
            return false;
        }
        
        if (!dataSources.containsKey(name)) {
            logger.warn("Data source does not exist: {}", name);
            if (strictMode) {
                throw new IllegalArgumentException(String.format("Data source does not exist: %s", name));
            }
            return false;
        }
        
        String previousDataSource = currentDataSourceName.get();
        currentDataSourceName.set(name);
        
        // Also update DataSourceContextHolder to ensure proper context setup
        DataSourceContextHolder.setDataSource(name);
        
        logger.info("数据源切换成功: [{}] -> [{}]", previousDataSource, name);
        return true;
    }
    
    /**
     * Resets the currently active data source to the default data source.
     */
    @Override
    public void resetDataSource() {
        String previousDataSource = currentDataSourceName.get();
        currentDataSourceName.set(defaultDataSourceName);
        logger.info("数据源已重置为默认值: {} (之前: {})", defaultDataSourceName, previousDataSource);
    }
    
    /**
     * 获取所有已注册数据源的名称。
     * 
     * @return 已注册数据源名称的集合
     */
    @Override
    public Set<String> getAllDataSourceNames() {
        return new HashSet<>(dataSources.keySet());
    }
    
    /**
     * 检查特定数据源是否健康可用。
     * 执行连接测试并更新健康状态信息。
     * 
     * @param name 要检查的数据源名称
     * @return 如果数据源存在且健康则返回true，否则返回false
     */
    @Override
    public boolean isDataSourceHealthy(String name) {
        DataSource dataSource = getDataSource(name);
        if (dataSource == null) {
            logger.warn("Data source[{}] does not exist, cannot perform health check", name);
            return false;
        }
        
        try {
            // Attempt to get a connection to check health status
            try (Connection connection = dataSource.getConnection()) {
                boolean healthy = connection != null && !connection.isClosed() && connection.isValid(DEFAULT_TIMEOUT_MS);
                
                // Update health status
                DataSourceHealthStatus status = dataSourceHealthStatus.get(name);
                if (status != null) {
                    status.updateHealthStatus(healthy);
                }
                
                if (healthy) {
                    logger.debug("Data source[{}] health check passed", name);
                } else {
                    logger.warn("Data source[{}] connection is invalid", name);
                }
                
                return healthy;
            }
        } catch (Exception e) {
            logger.warn("Data source[{}] health check failed: {}", name, e.getMessage());
            
            // Update health status to unhealthy
            DataSourceHealthStatus status = dataSourceHealthStatus.get(name);
            if (status != null) {
                status.updateHealthStatus(false);
            }
            
            return false;
        }
    }
    
    /**
     * 获取当前活动数据源的名称。
     * 
     * @return 当前数据源的名称，如果未设置则返回null
     */
    @Override
    public String getCurrentDataSourceName() {
        return currentDataSourceName.get();
    }
    
    /**
     * 使用指定的数据源执行给定的供应商操作。
     * 处理自动上下文切换、指标记录，并确保正确清理。
     * 即使发生异常，也保证在执行后恢复原始数据源上下文。
     * 
     * @param dataSourceName 用于执行的数据源名称
     * @param action 要使用指定数据源执行的供应商操作
     * @param <T> 操作的返回类型
     * @return 操作执行的结果
     * @throws IllegalArgumentException 如果数据源名称无效或操作为null
     * @throws RuntimeException 如果执行失败且出现任何异常
     */
    @Override
    public <T> T executeWithDataSource(String dataSourceName, Supplier<T> action) {
        Assert.notNull(dataSourceName, "Data source cannot be null");
        Assert.hasText(dataSourceName, "Data source name cannot be empty");
        Assert.notNull(action, "Execution action cannot be null");
        
        // Check if data source exists in strict mode
        if (strictMode && !dataSources.containsKey(dataSourceName)) {
            throw new IllegalArgumentException(String.format("Data source does not exist: %s", dataSourceName));
        }
        
        // 委托给DataSourceContextHolder，添加指标记录
        long startedAt = System.currentTimeMillis();
        try {
            // 更新内部状态
            String originalDataSource = currentDataSourceName.get();
            currentDataSourceName.set(dataSourceName);
            
            // 使用DataSourceContextHolder的方法执行操作
            return DataSourceContextHolder.executeInDataSourceWithResult(dataSourceName, action);
        } finally {
            // 记录执行指标
            long executionTime = System.currentTimeMillis() - startedAt;
            DataSourceMetrics metrics = dataSourceMetrics.get(dataSourceName);
            if (metrics != null) {
                metrics.recordAccess(executionTime);
            }
            
            // 注意：DataSourceContextHolder已经处理了上下文的清理，这里不需要额外处理
        }
    }
    
    /**
     * 使用指定的数据源异步执行给定的可运行操作。
     * 委托给DataSourceContextHolder实现核心逻辑，同时保留指标记录功能。
     * 
     * @param dataSourceName 用于执行的数据源名称
     * @param action 要使用指定数据源执行的可运行操作
     * @return CompletableFuture实例，用于异步操作管理
     * @throws IllegalArgumentException 如果数据源名称无效或操作为null
     */
    public CompletableFuture<Void> executeAsyncWithDataSource(String dataSourceName, Runnable action) {
        Assert.notNull(dataSourceName, "Data source cannot be null");
        Assert.hasText(dataSourceName, "Data source name cannot be empty");
        Assert.notNull(action, "Execution action cannot be null");
        
        // Check if data source exists in strict mode
        if (strictMode && !dataSources.containsKey(dataSourceName)) {
            throw new IllegalArgumentException(String.format("Data source does not exist: %s", dataSourceName));
        }
        
        // 委托给DataSourceContextHolder，添加指标记录
        long startedAt = System.currentTimeMillis();
        
        // 更新内部状态
        String originalDataSource = currentDataSourceName.get();
        currentDataSourceName.set(dataSourceName);
        
        try {
            // 使用DataSourceContextHolder的方法执行异步操作
            return DataSourceContextHolder.executeAsyncInDataSource(dataSourceName, action)
                    .whenComplete((v, e) -> {
                        // 记录执行指标
                        long executionTime = System.currentTimeMillis() - startedAt;
                        DataSourceMetrics metrics = dataSourceMetrics.get(dataSourceName);
                        if (metrics != null) {
                            metrics.recordAccess(executionTime);
                        }
                        
                        // 注意：DataSourceContextHolder已经处理了上下文的清理
                    });
        } catch (Exception e) {
            // 记录执行指标
            long executionTime = System.currentTimeMillis() - startedAt;
            DataSourceMetrics metrics = dataSourceMetrics.get(dataSourceName);
            if (metrics != null) {
                metrics.recordAccess(executionTime);
            }
            
            // 重新抛出异常
            throw e;
        }
    }
    
    /**
     * 使用指定的数据源执行给定的可运行操作。
     * 委托给DataSourceContextHolder实现核心逻辑，同时保留指标记录功能。
     * 
     * @param dataSourceName 用于执行的数据源名称
     * @param action 要使用指定数据源执行的可运行操作
     * @throws IllegalArgumentException 如果数据源名称无效或操作为null
     * @throws RuntimeException 如果执行失败且出现任何异常
     */
    public void executeWithDataSource(String dataSourceName, Runnable action) {
        Assert.notNull(dataSourceName, "Data source cannot be null");
        Assert.hasText(dataSourceName, "Data source name cannot be empty");
        Assert.notNull(action, "Execution action cannot be null");
        
        // 委托给DataSourceContextHolder，添加指标记录
        long startedAt = System.currentTimeMillis();
        try {
            // 更新内部状态
            String originalDataSource = currentDataSourceName.get();
            currentDataSourceName.set(dataSourceName);
            
            // 使用DataSourceContextHolder的方法执行操作
            DataSourceContextHolder.executeInDataSource(dataSourceName, action);
            
            return;
        } finally {
            // 记录执行指标
            long executionTime = System.currentTimeMillis() - startedAt;
            DataSourceMetrics metrics = dataSourceMetrics.get(dataSourceName);
            if (metrics != null) {
                metrics.recordAccess(executionTime);
            }
            
            // 注意：DataSourceContextHolder已经处理了上下文的清理，这里不需要额外处理
        }
    }
    
    /**
     * 使用指定的数据源异步执行给定的供应商操作。
     * 委托给DataSourceContextHolder实现核心逻辑，同时保留指标记录功能。
     * 
     * @param <T> 返回值类型
     * @param dataSourceName 用于执行的数据源名称
     * @param action 要使用指定数据源执行的供应商操作
     * @return CompletableFuture实例，包含异步操作的结果
     * @throws IllegalArgumentException 如果数据源名称无效或操作为null
     */
    public <T> CompletableFuture<T> executeAsyncWithDataSource(String dataSourceName, Supplier<T> action) {
        Assert.notNull(dataSourceName, "Data source cannot be null");
        Assert.hasText(dataSourceName, "Data source name cannot be empty");
        Assert.notNull(action, "Execution action cannot be null");
        
        // Check if data source exists in strict mode
        if (strictMode && !dataSources.containsKey(dataSourceName)) {
            throw new IllegalArgumentException(String.format("Data source does not exist: %s", dataSourceName));
        }
        
        // 委托给DataSourceContextHolder，添加指标记录
        long startedAt = System.currentTimeMillis();
        
        // 更新内部状态
        String originalDataSource = currentDataSourceName.get();
        currentDataSourceName.set(dataSourceName);
        
        try {
            // 使用DataSourceContextHolder的方法执行异步操作
            return DataSourceContextHolder.executeAsyncInDataSourceWithResult(dataSourceName, action)
                    .whenComplete((v, e) -> {
                        // 记录执行指标
                        long executionTime = System.currentTimeMillis() - startedAt;
                        DataSourceMetrics metrics = dataSourceMetrics.get(dataSourceName);
                        if (metrics != null) {
                            metrics.recordAccess(executionTime);
                        }
                        
                        // 注意：DataSourceContextHolder已经处理了上下文的清理
                    });
        } catch (Exception e) {
            // 记录执行指标
            long executionTime = System.currentTimeMillis() - startedAt;
            DataSourceMetrics metrics = dataSourceMetrics.get(dataSourceName);
            if (metrics != null) {
                metrics.recordAccess(executionTime);
            }
            
            // 重新抛出异常
            throw e;
        }
    }
    
    /**
     * 检查具有指定名称的数据源是否存在。
     * 
     * @param name 要检查的数据源名称
     * @return 如果数据源存在则返回true，否则返回false
     */
    public boolean containsDataSource(String name) {
        return name != null && dataSources.containsKey(name);
    }
    
    /**
     * 获取已注册数据源的总数。
     * 
     * @return 已注册数据源的数量
     */
    public int getDataSourceCount() {
        return dataSources.size();
    }
    
    /**
     * 获取所有已注册数据源的副本。
     * 
     * @return 数据源名称到其实例的不可变映射
     */
    public Map<String, DataSource> getAllDataSources() {
        return Collections.unmodifiableMap(new HashMap<>(dataSources));
    }
    
    /**
     * 关闭健康检查调度器并释放相关资源。
     * 当不再需要管理器时，应调用此方法以防止资源泄漏。
     * 执行带有超时的优雅关闭，必要时进行强制关闭。
     */
    public void shutdown() {
        if (healthCheckScheduler != null && !healthCheckScheduler.isShutdown()) {
            try {
                healthCheckScheduler.shutdown();
                if (!healthCheckScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    healthCheckScheduler.shutdownNow();
                }
                logger.info("Health check scheduler shutdown completed successfully");
            } catch (InterruptedException e) {
                logger.error("Error during health check scheduler shutdown", e);
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 设置默认数据源的名称。
     * 如果当前未设置活动数据源，这将成为活动数据源。
     * 
     * @param defaultDataSourceName 默认数据源的名称
     */
    public void setDefaultDataSourceName(String defaultDataSourceName) {
        Assert.notNull(defaultDataSourceName, "Default data source name cannot be null");
        this.defaultDataSourceName = defaultDataSourceName;
        // If there is no active data source currently, set the default data source to active state
        if (currentDataSourceName.get() == null && dataSources.containsKey(defaultDataSourceName)) {
            currentDataSourceName.set(defaultDataSourceName);
            logger.info("当前未设置活动数据源，默认数据源[{}]已激活", defaultDataSourceName);
        }
    }
    
    /**
     * 获取默认数据源的名称。
     * 
     * @return 默认数据源名称
     */
    public String getDefaultDataSourceName() {
        return defaultDataSourceName;
    }
    
    /**
     * 设置严格模式标志。
     * 在严格模式下，如果找不到数据源或数据源不可用，操作将抛出异常。
     * 
     * @param strictMode 启用严格模式为true，否则为false
     */
    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
        logger.info("严格模式已{}", strictMode ? "启用" : "禁用");
    }
    
    /**
     * 获取是否启用了严格模式。
     * 
     * @return 如果启用了严格模式则返回true，否则返回false
     */
    public boolean isStrictMode() {
        return strictMode;
    }
    
    /**
     * 设置允许的最大数据源数量。
     * 尝试注册超过此限制的数据源将被拒绝。
     * 
     * @param maxDataSourceCount 最大数据源数量
     * @throws IllegalArgumentException 如果maxDataSourceCount小于或等于0
     */
    public void setMaxDataSourceCount(int maxDataSourceCount) {
        if (maxDataSourceCount <= 0) {
            throw new IllegalArgumentException("Maximum data source count must be greater than 0");
        }
        this.maxDataSourceCount = maxDataSourceCount;
        logger.info("最大数据源数量已设置为: {}", maxDataSourceCount);
    }
    
    /**
     * 获取允许的最大数据源数量。
     * 
     * @return 最大数据源数量
     */
    public int getMaxDataSourceCount() {
        return maxDataSourceCount;
    }
    
    @Override
    public void afterPropertiesSet() {
        logger.info("DefaultDataSourceManager已初始化 - 严格模式: {}, 默认数据源: {}, 最大数据源数量: {}", 
                strictMode, defaultDataSourceName, maxDataSourceCount);
        
        // 初始化健康检查调度器
        initHealthCheckScheduler();
    }
    
    /**
     * 使用守护线程初始化健康检查调度器。
     * 为所有已注册的数据源设置定期健康检查。
     */
    private void initHealthCheckScheduler() {
        // Create scheduler with named thread for easier troubleshooting
        healthCheckScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = Executors.defaultThreadFactory().newThread(r);
            thread.setName("data-source-health-checker");
            thread.setDaemon(true); // Set as daemon thread to avoid affecting application shutdown
            return thread;
        });
        
        // Schedule periodic health checks
        healthCheckScheduler.scheduleAtFixedRate(this::performHealthChecks, 
                HEALTH_CHECK_INTERVAL_MS, HEALTH_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        logger.info("数据源健康检查调度器已启动，间隔为: {}ms", HEALTH_CHECK_INTERVAL_MS);
    }
    
    /**
     * 对所有已注册的数据源执行健康检查。
     * 记录健康检查过程的统计信息。
     */
    private void performHealthChecks() {
        if (dataSources.isEmpty()) {
            logger.debug("No data sources to perform health check on");
            return;
        }
        
        logger.debug("Starting health check for {} data sources", dataSources.size());
        long startedAt = System.currentTimeMillis();
        int checkedCount = 0;
        int healthyCount = 0;
        
        // Use a copy of the key set to avoid concurrent modification issues
        for (String dataSourceName : new ArrayList<>(dataSources.keySet())) {
            try {
                boolean healthy = isDataSourceHealthy(dataSourceName);
                checkedCount++;
                if (healthy) {
                    healthyCount++;
                }
            } catch (Exception e) {
                logger.error("Data source[{}] health check exception: {}", dataSourceName, e.getMessage(), e);
            }
        }
        
        long endedAt = System.currentTimeMillis();
        logger.info("健康检查完成 - 已检查数据源: {}, 健康数据源: {}, 耗时: {}ms", 
                checkedCount, healthyCount, (endedAt - startedAt));
    }
    
    /**
     * 跟踪数据源健康状态的内部类。
     * 维护健康状态、检查时间戳和失败计数。
     */
    private static class DataSourceHealthStatus {
        /** Name of the data source */
        private final String dataSourceName;
        
        /** Current health state of the data source */
        private volatile boolean healthy = true;
        
        /** Timestamp of the last health check */
        private volatile long lastCheckTime = System.currentTimeMillis();
        
        /** Count of consecutive health check failures */
        private volatile int consecutiveFailures = 0;
        
        /**
           * 为指定的数据源创建新的DataSourceHealthStatus。
           * 
           * @param dataSourceName 数据源的名称
           */
        public DataSourceHealthStatus(String dataSourceName) {
            this.dataSourceName = dataSourceName;
        }
        
        /**
           * 更新数据源的健康状态。
           * 此方法是线程安全的，以确保状态更新的一致性。
           * 
           * @param healthy 如果数据源健康则为true，否则为false
           */
        public synchronized void updateHealthStatus(boolean healthy) {
            this.healthy = healthy;
            this.lastCheckTime = System.currentTimeMillis();
            
            if (!healthy) {
                consecutiveFailures++;
            } else {
                consecutiveFailures = 0;
            }
        }
        
        /**
           * 获取数据源当前是否健康。
           * 
           * @return 如果健康则返回true，否则返回false
           */
        public boolean isHealthy() {
            return healthy;
        }
        
        /**
           * 获取上次健康检查的时间戳。
           * 
           * @return 上次检查的时间戳（毫秒）
           */
        public long getLastCheckTime() {
            return lastCheckTime;
        }
        
        /**
           * 获取连续健康检查失败的次数。
           * 
           * @return 连续失败的计数
           */
        public int getConsecutiveFailures() {
            return consecutiveFailures;
        }
        
        @Override
        public String toString() {
            return String.format("DataSourceHealthStatus{name='%s', healthy=%s, consecutiveFailures=%d, lastCheckTime=%d}",
                    dataSourceName, healthy, consecutiveFailures, lastCheckTime);
        }
    }
    
    /**
     * 收集数据源使用指标的内部类。
     * 跟踪访问计数、查询时间和性能统计信息。
     */
    private static class DataSourceMetrics {
        /** Name of the data source */
        private final String dataSourceName;
        
        /** Total number of accesses to this data source */
        private long totalAccessCount = 0;
        
        /** Total accumulated query time in milliseconds */
        private long totalQueryTime = 0;
        
        /** Maximum query time observed in milliseconds */
        private long maxQueryTime = 0;
        
        /** Minimum query time observed in milliseconds */
        private long minQueryTime = Long.MAX_VALUE;
        
        /**
           * 为指定的数据源创建新的DataSourceMetrics。
           * 
           * @param dataSourceName 数据源的名称
           */
        public DataSourceMetrics(String dataSourceName) {
            this.dataSourceName = dataSourceName;
        }
        
        /**
           * 记录数据源访问及其执行时间。
           * 此方法是线程安全的，以确保指标收集的准确性。
           * 
           * @param queryTimeMs 执行时间（毫秒）
           */
        public synchronized void recordAccess(long queryTimeMs) {
            totalAccessCount++;
            totalQueryTime += queryTimeMs;
            
            // Update max and min query times
            if (queryTimeMs > maxQueryTime) {
                maxQueryTime = queryTimeMs;
            }
            if (queryTimeMs < minQueryTime) {
                minQueryTime = queryTimeMs;
            }
        }
        
        /**
           * 获取对此数据源的总访问次数。
           * 
           * @return 总访问计数
           */
        public long getTotalAccessCount() {
            return totalAccessCount;
        }
        
        /**
           * 获取平均查询时间（毫秒）。
           * 
           * @return 平均查询时间
           */
        public long getAverageQueryTime() {
            return totalAccessCount > 0 ? totalQueryTime / totalAccessCount : 0;
        }
        
        /**
           * 获取最大查询时间（毫秒）。
           * 
           * @return 最大查询时间
           */
        public long getMaxQueryTime() {
            return maxQueryTime;
        }
        
        /**
           * 获取最小查询时间（毫秒）。
           * 
           * @return 最小查询时间
           */
        public long getMinQueryTime() {
            return minQueryTime == Long.MAX_VALUE ? 0 : minQueryTime;
        }
        
        @Override
        public String toString() {
            return String.format("DataSourceMetrics{name='%s', accessCount=%d, avgQueryTime=%dms, maxQueryTime=%dms, minQueryTime=%dms}",
                    dataSourceName, totalAccessCount, getAverageQueryTime(), getMaxQueryTime(), getMinQueryTime());
        }
    }
}