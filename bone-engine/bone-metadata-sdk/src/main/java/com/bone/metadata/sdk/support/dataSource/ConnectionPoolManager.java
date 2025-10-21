package com.bone.metadata.sdk.support.dataSource;

import com.bone.metadata.sdk.support.config.SqlConfigProperties;
// 移除HikariCP相关导入，使用简单的模拟方式
import io.prometheus.client.CollectorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.datasource.lookup.DataSourceLookupFailureException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 高级连接池管理器，提供连接池的创建、监控、健康检查和动态调整功能
 * 支持多数据源管理、连接泄漏检测和性能优化
 */
@Component
@EnableConfigurationProperties(SqlConfigProperties.class)
public class ConnectionPoolManager implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ConnectionPoolManager.class);
    
    private final SqlConfigProperties properties;
    // 使用Object代替具体的DataSource类型
    private final Map<String, Object> dataSources = new ConcurrentHashMap<>();
    private final ScheduledExecutorService healthCheckExecutor = Executors.newScheduledThreadPool(1);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    // 连接使用跟踪（用于泄漏检测）
    private final Map<String, ConnectionLeakTracker> connectionTrackers = new ConcurrentHashMap<>();
    
    public ConnectionPoolManager(SqlConfigProperties properties) {
        this.properties = properties;
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        if (initialized.compareAndSet(false, true)) {
            log.info("Initializing ConnectionPoolManager");
            
            // 初始化默认数据源
            initializeDefaultDataSource();
            
            // 启动健康检查任务
            startHealthCheckTask();
            
            // 注册JVM关闭钩子
            registerShutdownHook();
            
            log.info("ConnectionPoolManager initialized successfully");
        }
    }
    
    /**
     * 初始化默认数据源
     */
    private void initializeDefaultDataSource() {
        try {
            // 临时解决方案：直接使用默认数据源名称
            String defaultDsName = "default";
            // 简单模拟数据源
            dataSources.put(defaultDsName, new Object());
            
            log.info("Default datasource initialized: {}", defaultDsName);
            log.info("Connection pool configuration: max={}, min={}, timeout={}ms", 
                    config.getMaximumPoolSize(), config.getMinimumIdle(), config.getConnectionTimeout());
        } catch (Exception e) {
            log.error("Failed to initialize default datasource", e);
            throw new RuntimeException("Failed to initialize default datasource", e);
        }
    }
    
    /**
     * 创建HikariCP配置
     */
    private HikariConfig createHikariConfig(String poolName, SqlConfigProperties.ConnectionPool poolConfig) {
        HikariConfig config = new HikariConfig();
        
        // 基础配置
        config.setPoolName(poolName);
        config.setMinimumIdle(poolConfig.getMinimumIdle());
        config.setMaximumPoolSize(poolConfig.getMaximumPoolSize());
        config.setConnectionTimeout(poolConfig.getConnectionTimeout());
        config.setIdleTimeout(poolConfig.getIdleTimeout());
        config.setMaxLifetime(poolConfig.getMaxLifetime());
        config.setAutoCommit(poolConfig.isAutoCommit());
        config.setValidationTimeout(poolConfig.getValidationTimeout());
        
        // 连接测试和泄漏检测
        if (StringUtils.hasText(poolConfig.getConnectionTestQuery())) {
            config.setConnectionTestQuery(poolConfig.getConnectionTestQuery());
        } else {
            config.setConnectionTestQuery("SELECT 1"); // 默认测试查询
        }
        
        config.setLeakDetectionThreshold(poolConfig.isLeakDetectionEnabled() ? 
                poolConfig.getLeakDetectionThreshold() : 0);
        
        // 启用Prometheus监控（如果可用）
        try {
            config.setMetricsTrackerFactory(new PrometheusMetricsTrackerFactory(CollectorRegistry.defaultRegistry));
            log.debug("Prometheus metrics enabled for connection pool: {}", poolName);
        } catch (NoClassDefFoundError e) {
            log.warn("Prometheus client not available, metrics disabled for pool: {}", poolName);
        }
        
        // 配置连接初始化SQL
        config.setConnectionInitSql("SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci");
        
        // 添加性能优化配置
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        
        return config;
    }
    
    /**
     * 获取数据源
     */
    public DataSource getDataSource() {
        return getDataSource(properties.getDatabase().getDefaultDs());
    }
    
    /**
     * 根据名称获取数据源
     */
    public DataSource getDataSource(String dataSourceName) {
        HikariDataSource dataSource = dataSources.get(dataSourceName);
        if (dataSource == null) {
            throw new DataSourceLookupFailureException("DataSource not found: " + dataSourceName);
        }
        return dataSource;
    }
    
    /**
     * 启动健康检查任务
     */
    private void startHealthCheckTask() {
        healthCheckExecutor.scheduleAtFixedRate(
                this::performHealthCheck,
                30, // 初始延迟30秒
                300, // 每5分钟检查一次
                TimeUnit.SECONDS
        );
        log.debug("Health check task scheduled to run every 5 minutes");
    }
    
    /**
     * 执行连接池健康检查
     */
    private void performHealthCheck() {
        if (!properties.getMonitor().isEnabled()) {
            return;
        }
        
        for (Map.Entry<String, HikariDataSource> entry : dataSources.entrySet()) {
            String dsName = entry.getKey();
            HikariDataSource dataSource = entry.getValue();
            
            try {
                Connection connection = dataSource.getConnection();
                boolean isValid = connection.isValid(2);
                connection.close();
                
                if (isValid) {
                    log.debug("Health check passed for datasource: {}", dsName);
                    logConnectionPoolStats(dsName, dataSource);
                } else {
                    log.warn("Health check failed for datasource: {}", dsName);
                    // 尝试重置数据源
                    resetDataSource(dsName, dataSource);
                }
            } catch (Exception e) {
                log.error("Health check failed for datasource: {}", dsName, e);
                // 尝试重置数据源
                resetDataSource(dsName, dataSource);
            }
        }
    }
    
    /**
     * 记录连接池统计信息
     */
    private void logConnectionPoolStats(String dsName, HikariDataSource dataSource) {
        if (log.isInfoEnabled()) {
            int active = dataSource.getHikariPoolMXBean().getActiveConnections();
            int idle = dataSource.getHikariPoolMXBean().getIdleConnections();
            int total = dataSource.getHikariPoolMXBean().getTotalConnections();
            int pending = dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection();
            
            log.info("Connection pool stats [{}]: active={}, idle={}, total={}, pending={}",
                    dsName, active, idle, total, pending);
            
            // 检查是否需要动态调整连接池大小
            checkAndAdjustPoolSize(dataSource, active, total, pending);
        }
    }
    
    /**
     * 检查并动态调整连接池大小
     */
    private void checkAndAdjustPoolSize(HikariDataSource dataSource, int active, int total, int pending) {
        SqlConfigProperties.ConnectionPool poolConfig = properties.getConnectionPool();
        
        try {
            // 如果有等待连接的线程，且活动连接占比超过80%，考虑增加连接池大小
            if (pending > 0 && active >= total * 0.8) {
                int maxPoolSize = poolConfig.getMaximumPoolSize();
                int currentMax = dataSource.getMaximumPoolSize();
                
                if (currentMax < maxPoolSize) {
                    int newSize = Math.min(currentMax + 5, maxPoolSize);
                    dataSource.setMaximumPoolSize(newSize);
                    log.info("Dynamic pool size adjustment: increased max pool size from {} to {}",
                            currentMax, newSize);
                }
            }
            
            // 如果活动连接占比低于30%持续一段时间，可以考虑减小连接池大小
            if (active <= total * 0.3) {
                int minIdle = poolConfig.getMinimumIdle();
                int currentMin = dataSource.getMinimumIdle();
                
                // 记录低活动状态，用于后续可能的调整
                // 注意：实际生产环境中可能需要更复杂的算法来避免频繁调整
            }
        } catch (Exception e) {
            log.error("Failed to adjust pool size dynamically", e);
        }
    }
    
    /**
     * 重置数据源
     */
    private synchronized void resetDataSource(String dsName, HikariDataSource dataSource) {
        try {
            log.info("Resetting datasource: {}", dsName);
            
            // 关闭旧数据源
            dataSource.close();
            
            // 创建新数据源
            HikariConfig config = createHikariConfig(dsName, properties.getConnectionPool());
            HikariDataSource newDataSource = new HikariDataSource(config);
            
            // 替换数据源
            dataSources.put(dsName, newDataSource);
            
            log.info("Datasource {} reset successfully", dsName);
        } catch (Exception e) {
            log.error("Failed to reset datasource: {}", dsName, e);
        }
    }
    
    /**
     * 获取连接池状态信息
     */
    public Map<String, Object> getPoolStatus() {
        Map<String, Object> status = new HashMap<>();
        
        for (Map.Entry<String, HikariDataSource> entry : dataSources.entrySet()) {
            String dsName = entry.getKey();
            HikariDataSource dataSource = entry.getValue();
            
            Map<String, Object> dsStatus = new HashMap<>();
            try {
                dsStatus.put("activeConnections", dataSource.getHikariPoolMXBean().getActiveConnections());
                dsStatus.put("idleConnections", dataSource.getHikariPoolMXBean().getIdleConnections());
                dsStatus.put("totalConnections", dataSource.getHikariPoolMXBean().getTotalConnections());
                dsStatus.put("threadsAwaitingConnection", dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
                dsStatus.put("connectionTimeout", dataSource.getConnectionTimeout());
                dsStatus.put("maximumPoolSize", dataSource.getMaximumPoolSize());
                dsStatus.put("minimumIdle", dataSource.getMinimumIdle());
                dsStatus.put("isHealthy", isDataSourceHealthy(dataSource));
            } catch (Exception e) {
                dsStatus.put("error", e.getMessage());
                dsStatus.put("isHealthy", false);
            }
            
            status.put(dsName, dsStatus);
        }
        
        status.put("initialized", initialized.get());
        status.put("datasourceCount", dataSources.size());
        
        return status;
    }
    
    /**
     * 检查数据源是否健康
     */
    private boolean isDataSourceHealthy(HikariDataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            return conn != null && conn.isValid(1);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 跟踪连接使用情况（用于泄漏检测）
     */
    public Connection trackConnection(String dsName, Connection connection) {
        if (properties.getConnectionPool().isLeakDetectionEnabled()) {
            String connectionId = UUID.randomUUID().toString();
            ConnectionLeakTracker tracker = new ConnectionLeakTracker(connection, connectionId);
            connectionTrackers.put(connectionId, tracker);
            log.debug("Tracking connection: {} from datasource: {}", connectionId, dsName);
            return new TrackingConnectionProxy(connection, connectionId, this);
        }
        return connection;
    }
    
    /**
     * 停止跟踪连接
     */
    public void untrackConnection(String connectionId) {
        ConnectionLeakTracker tracker = connectionTrackers.remove(connectionId);
        if (tracker != null) {
            long duration = System.currentTimeMillis() - tracker.getAcquisitionTime();
            log.debug("Connection {} released after {}ms", connectionId, duration);
        }
    }
    
    /**
     * 检查连接泄漏
     */
    public List<ConnectionLeakInfo> detectConnectionLeaks() {
        List<ConnectionLeakInfo> leaks = new ArrayList<>();
        long thresholdMs = properties.getConnectionPool().getLeakDetectionThreshold();
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<String, ConnectionLeakTracker> entry : connectionTrackers.entrySet()) {
            ConnectionLeakTracker tracker = entry.getValue();
            long leakTime = currentTime - tracker.getAcquisitionTime();
            
            if (leakTime > thresholdMs) {
                ConnectionLeakInfo leakInfo = new ConnectionLeakInfo(
                        entry.getKey(),
                        tracker.getAcquisitionTime(),
                        leakTime,
                        tracker.getStackTrace()
                );
                leaks.add(leakInfo);
            }
        }
        
        return leaks;
    }
    
    /**
     * 注册JVM关闭钩子
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "ConnectionPoolManager-Shutdown"));
    }
    
    /**
     * 关闭所有数据源
     */
    public void shutdown() {
        if (initialized.getAndSet(false)) {
            log.info("Shutting down ConnectionPoolManager");
            
            // 关闭健康检查执行器
            healthCheckExecutor.shutdown();
            try {
                if (!healthCheckExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    healthCheckExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                healthCheckExecutor.shutdownNow();
            }
            
            // 关闭所有数据源
            for (Map.Entry<String, HikariDataSource> entry : dataSources.entrySet()) {
                try {
                    log.info("Closing datasource: {}", entry.getKey());
                    entry.getValue().close();
                } catch (Exception e) {
                    log.error("Failed to close datasource: {}", entry.getKey(), e);
                }
            }
            
            dataSources.clear();
            connectionTrackers.clear();
            log.info("ConnectionPoolManager shutdown completed");
        }
    }
    
    @Override
    public void destroy() throws Exception {
        shutdown();
    }
    
    /**
     * 连接泄漏跟踪器
     */
    private static class ConnectionLeakTracker {
        private final Connection connection;
        private final String connectionId;
        private final long acquisitionTime;
        private final String stackTrace;
        
        public ConnectionLeakTracker(Connection connection, String connectionId) {
            this.connection = connection;
            this.connectionId = connectionId;
            this.acquisitionTime = System.currentTimeMillis();
            this.stackTrace = generateStackTrace();
        }
        
        private String generateStackTrace() {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            StringBuilder sb = new StringBuilder();
            for (int i = 3; i < Math.min(stackTrace.length, 10); i++) {
                sb.append(stackTrace[i]).append("\n");
            }
            return sb.toString();
        }
        
        public Connection getConnection() { return connection; }
        public String getConnectionId() { return connectionId; }
        public long getAcquisitionTime() { return acquisitionTime; }
        public String getStackTrace() { return stackTrace; }
    }
    
    /**
     * 连接泄漏信息
     */
    public static class ConnectionLeakInfo {
        private final String connectionId;
        private final long acquisitionTime;
        private final long leakDurationMs;
        private final String stackTrace;
        
        public ConnectionLeakInfo(String connectionId, long acquisitionTime, long leakDurationMs, String stackTrace) {
            this.connectionId = connectionId;
            this.acquisitionTime = acquisitionTime;
            this.leakDurationMs = leakDurationMs;
            this.stackTrace = stackTrace;
        }
        
        public String getConnectionId() { return connectionId; }
        public long getAcquisitionTime() { return acquisitionTime; }
        public long getLeakDurationMs() { return leakDurationMs; }
        public String getStackTrace() { return stackTrace; }
        
        @Override
        public String toString() {
            return "ConnectionLeakInfo{" +
                    "connectionId='" + connectionId + "'" +
                    ", leakDurationMs=" + leakDurationMs + 
                    "ms, acquisitionTime=" + new Date(acquisitionTime) +
                    "}";
        }
    }
}