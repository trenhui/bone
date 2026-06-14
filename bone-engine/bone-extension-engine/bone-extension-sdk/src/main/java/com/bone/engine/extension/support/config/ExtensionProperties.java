package com.bone.engine.extension.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Bone Engine 扩展点框架配置属性
 *
 * <p>通过配置文件自定义框架行为，支持YAML和Properties格式。
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "bone.extension")
public class ExtensionProperties {

  /** 是否启用扩展点框架 */
  private boolean enabled = true;

  /** 缓存配置 */
  @NestedConfigurationProperty private CacheConfig cache = new CacheConfig();

  /** 扫描配置 */
  @NestedConfigurationProperty private ScanConfig scan = new ScanConfig();

  /** 路由配置 */
  @NestedConfigurationProperty private RouterConfig router = new RouterConfig();

  /** 执行器配置 */
  @NestedConfigurationProperty private ExecutorConfig executor = new ExecutorConfig();

  /** 监控配置 */
  @NestedConfigurationProperty private MonitorConfig monitor = new MonitorConfig();

  /** 事件配置 */
  @NestedConfigurationProperty private EventsConfig events = new EventsConfig();

  /** 异步执行配置 */
  @NestedConfigurationProperty private Async async = new Async();

  /** 运行时元数据同步 */
  @NestedConfigurationProperty private SyncConfig sync = new SyncConfig();

  /** Studio 管理台集成（执行日志上报等） */
  @NestedConfigurationProperty private StudioConfig studio = new StudioConfig();

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

  public EventsConfig getEvents() {
    return events;
  }

  public void setEvents(EventsConfig events) {
    this.events = events;
  }

  public Async getAsync() {
    return async;
  }

  public void setAsync(Async async) {
    this.async = async;
  }

  public SyncConfig getSync() {
    return sync;
  }

  public void setSync(SyncConfig sync) {
    this.sync = sync;
  }

  public StudioConfig getStudio() {
    return studio;
  }

  public void setStudio(StudioConfig studio) {
    this.studio = studio;
  }

  /** Studio 管理台配置 */
  public static class StudioConfig {
    @NestedConfigurationProperty private StudioReportConfig report = new StudioReportConfig();

    public StudioReportConfig getReport() {
      return report;
    }

    public void setReport(StudioReportConfig report) {
      this.report = report;
    }
  }

  /** 向 Studio 上报扩展执行日志 */
  public static class StudioReportConfig {
    private boolean enabled = false;
    private String baseUrl = "http://localhost:8088";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }
  }

  /** 运行时元数据同步配置 */
  public static class SyncConfig {
    private String refreshChannel = "bone:ext:metadata:refresh";

    public String getRefreshChannel() {
      return refreshChannel;
    }

    public void setRefreshChannel(String refreshChannel) {
      this.refreshChannel = refreshChannel;
    }
  }

  /** 缓存配置 */
  public static class CacheConfig {
    /** 是否启用路由缓存 */
    private boolean enabled = true;

    /** 缓存过期时间（毫秒） */
    private long expireAfterWrite = 600_000L;

    /** 缓存最大条目数 */
    private int maxSize = 1000;

    /** 是否记录缓存统计 */
    private boolean recordStats = false;

    /** 是否启用分布式缓存 */
    private boolean distributedEnabled = false;

    /** 分布式缓存前缀 */
    private String distributedPrefix = "bone:extension:route";

    /** 分布式缓存过期时间（秒） */
    private long distributedExpireSeconds = 3600;

    /** 是否懒加载 */
    private boolean lazyLoad = true;

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

    public java.time.Duration getExpireTime() {
      return java.time.Duration.ofMillis(expireAfterWrite);
    }

    public int getMaxSize() {
      return maxSize;
    }

    public void setMaxSize(int maxSize) {
      this.maxSize = maxSize;
    }

    public boolean isRecordStats() {
      return recordStats;
    }

    public void setRecordStats(boolean recordStats) {
      this.recordStats = recordStats;
    }

    public boolean isDistributedEnabled() {
      return distributedEnabled;
    }

    public void setDistributedEnabled(boolean distributedEnabled) {
      this.distributedEnabled = distributedEnabled;
    }

    public String getDistributedPrefix() {
      return distributedPrefix;
    }

    public void setDistributedPrefix(String distributedPrefix) {
      this.distributedPrefix = distributedPrefix;
    }

    public long getDistributedExpireSeconds() {
      return distributedExpireSeconds;
    }

    public void setDistributedExpireSeconds(long distributedExpireSeconds) {
      this.distributedExpireSeconds = distributedExpireSeconds;
    }

    public boolean isLazyLoad() {
      return lazyLoad;
    }

    public void setLazyLoad(boolean lazyLoad) {
      this.lazyLoad = lazyLoad;
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

    /** 是否自动注册发现的扩展点 */
    private boolean autoRegister = true;

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

    public boolean isAutoRegister() {
      return autoRegister;
    }

    public void setAutoRegister(boolean autoRegister) {
      this.autoRegister = autoRegister;
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

  /** 事件配置 */
  public static class EventsConfig {
    /** 是否启用事件 */
    private boolean enabled = true;

    /** 是否启用异步事件 */
    private boolean async = false;

    /** 事件执行器 */
    private String executor = "default";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isAsync() {
      return async;
    }

    public void setAsync(boolean async) {
      this.async = async;
    }

    public String getExecutor() {
      return executor;
    }

    public void setExecutor(String executor) {
      this.executor = executor;
    }
  }

  /**
   * 异步执行配置
   *
   * <p>支持通过配置文件调整线程池参数
   */
  public static class Async {
    /** 是否启用异步执行 */
    private boolean enabled = true;

    // 扩展点异步执行线程池配置
    /** 核心线程数 */
    private int corePoolSize = 10;

    /** 最大线程数 */
    private int maxPoolSize = 50;

    /** 队列容量 */
    private int queueCapacity = 1000;

    /** 线程存活时间（秒） */
    private int keepAliveSeconds = 60;

    // 事件处理线程池配置
    /** 事件处理核心线程数 */
    private int eventCorePoolSize = Runtime.getRuntime().availableProcessors() / 2;

    /** 事件处理最大线程数 */
    private int eventMaxPoolSize = Runtime.getRuntime().availableProcessors();

    /** 事件处理队列容量 */
    private int eventQueueCapacity = 1000;

    /** 事件处理线程存活时间（秒） */
    private int eventKeepAliveSeconds = 60;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getCorePoolSize() {
      return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
      this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
      return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
      this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
      return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
      this.queueCapacity = queueCapacity;
    }

    public int getKeepAliveSeconds() {
      return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(int keepAliveSeconds) {
      this.keepAliveSeconds = keepAliveSeconds;
    }

    public int getEventCorePoolSize() {
      return eventCorePoolSize;
    }

    public void setEventCorePoolSize(int eventCorePoolSize) {
      this.eventCorePoolSize = eventCorePoolSize;
    }

    public int getEventMaxPoolSize() {
      return eventMaxPoolSize;
    }

    public void setEventMaxPoolSize(int eventMaxPoolSize) {
      this.eventMaxPoolSize = eventMaxPoolSize;
    }

    public int getEventQueueCapacity() {
      return eventQueueCapacity;
    }

    public void setEventQueueCapacity(int eventQueueCapacity) {
      this.eventQueueCapacity = eventQueueCapacity;
    }

    public int getEventKeepAliveSeconds() {
      return eventKeepAliveSeconds;
    }

    public void setEventKeepAliveSeconds(int eventKeepAliveSeconds) {
      this.eventKeepAliveSeconds = eventKeepAliveSeconds;
    }
  }
}
