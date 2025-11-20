package com.bone.engine.extension.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Bone Engine 扩展点框架配置属性
 *
 * 通过配置文件自定义框架行为，支持YAML和Properties格式。
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "bone.extension")
public class ExtensionProperties {

    /** 是否启用扩展点框架 */
    private boolean enabled = true;

    /** 缓存配置 */
    @NestedConfigurationProperty
    private CacheConfig cache = new CacheConfig();

    /** 扫描配置 */
    @NestedConfigurationProperty
    private ScanConfig scan = new ScanConfig();

    /** 路由配置 */
    @NestedConfigurationProperty
    private RouterConfig router = new RouterConfig();

    /** 执行器配置 */
    @NestedConfigurationProperty
    private ExecutorConfig executor = new ExecutorConfig();

    /** 监控配置 */
    @NestedConfigurationProperty
    private MonitorConfig monitor = new MonitorConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public CacheConfig getCache() {
        return cache;
    }

    public void setCache(CacheConfig cache) {
        this.cache = cache;
    }

    public ScanConfig getScan() {
        return scan;
    }

    public void setScan(ScanConfig scan) {
        this.scan = scan;
    }

    public RouterConfig getRouter() {
        return router;
    }

    public void setRouter(RouterConfig router) {
        this.router = router;
    }

    public ExecutorConfig getExecutor() {
        return executor;
    }

    public void setExecutor(ExecutorConfig executor) {
        this.executor = executor;
    }

    public MonitorConfig getMonitor() {
        return monitor;
    }

    public void setMonitor(MonitorConfig monitor) {
        this.monitor = monitor;
    }

    /** 缓存配置 */
    public static class CacheConfig {
        /** 是否启用路由缓存 */
        private boolean enabled = true;
        /** 缓存过期时间（毫秒） */
        private long expireAfterWrite = 600_000L;
        /** 缓存最大条目数 */
        private int maximumSize = 1000;
        /** 是否记录缓存统计 */
        private boolean recordStats = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getExpireAfterWrite() {
            return expireAfterWrite;
        }

        public void setExpireAfterWrite(long expireAfterWrite) {
            this.expireAfterWrite = expireAfterWrite;
        }

        public int getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(int maximumSize) {
            this.maximumSize = maximumSize;
        }

        public boolean isRecordStats() {
            return recordStats;
        }

        public void setRecordStats(boolean recordStats) {
            this.recordStats = recordStats;
        }
    }

    /** 扫描配置 */
    public static class ScanConfig {
        /** 是否启用自动扫描 */
        private boolean enabled = true;
        /** 扫描的基础包路径 */
        private String[] basePackages = {};
        /** 是否启用类路径扫描 */
        private boolean scanClasspath = true;
        /** 扫描时包含的注解 */
        private String[] includeAnnotations = {};

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String[] getBasePackages() {
            return basePackages;
        }

        public void setBasePackages(String[] basePackages) {
            this.basePackages = basePackages;
        }

        public boolean isScanClasspath() {
            return scanClasspath;
        }

        public void setScanClasspath(boolean scanClasspath) {
            this.scanClasspath = scanClasspath;
        }

        public String[] getIncludeAnnotations() {
            return includeAnnotations;
        }

        public void setIncludeAnnotations(String[] includeAnnotations) {
            this.includeAnnotations = includeAnnotations;
        }
    }

    /** 路由配置 */
    public static class RouterConfig {
        /** 路由策略 */
        private String strategy = "score";
        /** 自定义路由器类名 */
        private String customRouter;
        /** 是否启用路由缓存 */
        private boolean cacheEnabled = true;
        /** 是否启用严格模式 */
        private boolean strictMode = false;
        /** 默认优先级 */
        private int defaultPriority = 100;

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public String getCustomRouter() {
            return customRouter;
        }

        public void setCustomRouter(String customRouter) {
            this.customRouter = customRouter;
        }

        public boolean isCacheEnabled() {
            return cacheEnabled;
        }

        public void setCacheEnabled(boolean cacheEnabled) {
            this.cacheEnabled = cacheEnabled;
        }

        public boolean isStrictMode() {
            return strictMode;
        }

        public void setStrictMode(boolean strictMode) {
            this.strictMode = strictMode;
        }

        public int getDefaultPriority() {
            return defaultPriority;
        }

        public void setDefaultPriority(int defaultPriority) {
            this.defaultPriority = defaultPriority;
        }
    }

    /** 执行器配置 */
    public static class ExecutorConfig {
        /** 执行超时时间（毫秒） */
        private long timeout = 5000L;
        /** 是否启用异步执行 */
        private boolean asyncEnabled = false;
        /** 自定义执行器类名 */
        private String customExecutor;
        /** 是否启用熔断器 */
        private boolean circuitBreakerEnabled = true;
        /** 熔断器失败阈值 */
        private int circuitBreakerThreshold = 5;

        public long getTimeout() {
            return timeout;
        }

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }

        public boolean isAsyncEnabled() {
            return asyncEnabled;
        }

        public void setAsyncEnabled(boolean asyncEnabled) {
            this.asyncEnabled = asyncEnabled;
        }

        public String getCustomExecutor() {
            return customExecutor;
        }

        public void setCustomExecutor(String customExecutor) {
            this.customExecutor = customExecutor;
        }

        public boolean isCircuitBreakerEnabled() {
            return circuitBreakerEnabled;
        }

        public void setCircuitBreakerEnabled(boolean circuitBreakerEnabled) {
            this.circuitBreakerEnabled = circuitBreakerEnabled;
        }

        public int getCircuitBreakerThreshold() {
            return circuitBreakerThreshold;
        }

        public void setCircuitBreakerThreshold(int circuitBreakerThreshold) {
            this.circuitBreakerThreshold = circuitBreakerThreshold;
        }
    }

    /** 监控配置 */
    public static class MonitorConfig {
        /** 是否启用监控 */
        private boolean enabled = true;
        /** 慢路由阈值（毫秒） */
        private long slowRouteThreshold = 100L;
        /** 是否启用指标收集 */
        private boolean metricsEnabled = true;
        /** 是否启用健康检查 */
        private boolean healthCheckEnabled = true;
        /** 指标前缀 */
        private String metricsPrefix = "bone.extension";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getSlowRouteThreshold() {
            return slowRouteThreshold;
        }

        public void setSlowRouteThreshold(long slowRouteThreshold) {
            this.slowRouteThreshold = slowRouteThreshold;
        }

        public boolean isMetricsEnabled() {
            return metricsEnabled;
        }

        public void setMetricsEnabled(boolean metricsEnabled) {
            this.metricsEnabled = metricsEnabled;
        }

        public boolean isHealthCheckEnabled() {
            return healthCheckEnabled;
        }

        public void setHealthCheckEnabled(boolean healthCheckEnabled) {
            this.healthCheckEnabled = healthCheckEnabled;
        }

        public String getMetricsPrefix() {
            return metricsPrefix;
        }

        public void setMetricsPrefix(String metricsPrefix) {
            this.metricsPrefix = metricsPrefix;
        }
    }
}