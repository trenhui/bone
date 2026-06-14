package com.bone.engine.extension.support.config;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 路由配置管理类
 *
 * <p>集中管理所有路由和缓存相关配置，提供统一的配置访问接口 采用单例模式确保配置的一致性
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
public class RouterConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(RouterConfiguration.class);
  private static final RouterConfiguration INSTANCE = new RouterConfiguration();

  // 默认配置值
  private static final int DEFAULT_CACHE_EXPIRE_TIME = 10; // 分钟
  private static final int DEFAULT_CACHE_MAX_SIZE = 10000;
  private static final int DEFAULT_SLOW_ROUTE_THRESHOLD_MS = 100;
  private static final boolean DEFAULT_CACHE_ENABLED = true;
  private static final boolean DEFAULT_STATS_ENABLED = true;
  private static final int DEFAULT_OPTIMIZE_INTERVAL_MS = 60000; // 1分钟

  // 配置项
  private int cacheExpireTime = DEFAULT_CACHE_EXPIRE_TIME;
  private int cacheMaxSize = DEFAULT_CACHE_MAX_SIZE;
  private int slowRouteThresholdMs = DEFAULT_SLOW_ROUTE_THRESHOLD_MS;
  private boolean cacheEnabled = DEFAULT_CACHE_ENABLED;
  private boolean statsEnabled = DEFAULT_STATS_ENABLED;
  private int optimizeIntervalMs = DEFAULT_OPTIMIZE_INTERVAL_MS;

  // 配置是否已初始化
  private final AtomicBoolean initialized = new AtomicBoolean(false);

  /** 私有构造函数，防止外部实例化 */
  private RouterConfiguration() {
    // 初始化默认配置
  }

  /** 获取单例实例 */
  public static RouterConfiguration getInstance() {
    return INSTANCE;
  }

  /** 初始化配置 */
  public synchronized void initialize(Properties properties) {
    if (initialized.get()) {
      logger.warn("RouterConfiguration already initialized");
      return;
    }

    if (properties != null) {
      // 从Properties加载配置
      this.cacheExpireTime =
          getIntProperty(properties, "cache.expireTime", DEFAULT_CACHE_EXPIRE_TIME);
      this.cacheMaxSize = getIntProperty(properties, "cache.maxSize", DEFAULT_CACHE_MAX_SIZE);
      this.slowRouteThresholdMs =
          getIntProperty(properties, "route.slowThresholdMs", DEFAULT_SLOW_ROUTE_THRESHOLD_MS);
      this.cacheEnabled = getBooleanProperty(properties, "cache.enabled", DEFAULT_CACHE_ENABLED);
      this.statsEnabled = getBooleanProperty(properties, "stats.enabled", DEFAULT_STATS_ENABLED);
      this.optimizeIntervalMs =
          getIntProperty(properties, "cache.optimizeIntervalMs", DEFAULT_OPTIMIZE_INTERVAL_MS);
    }

    initialized.set(true);
    logger.info(
        "RouterConfiguration initialized: cacheExpireTime={}min, cacheMaxSize={}, cacheEnabled={}",
        cacheExpireTime,
        cacheMaxSize,
        cacheEnabled);
  }

  /** 更新配置 */
  public synchronized void updateConfig(String key, String value) {
    if (key == null || value == null) {
      return;
    }

    switch (key) {
      case "cache.expireTime":
        this.cacheExpireTime = parseInteger(value, DEFAULT_CACHE_EXPIRE_TIME);
        break;
      case "cache.maxSize":
        this.cacheMaxSize = parseInteger(value, DEFAULT_CACHE_MAX_SIZE);
        break;
      case "route.slowThresholdMs":
        this.slowRouteThresholdMs = parseInteger(value, DEFAULT_SLOW_ROUTE_THRESHOLD_MS);
        break;
      case "cache.enabled":
        this.cacheEnabled = Boolean.parseBoolean(value);
        break;
      case "stats.enabled":
        this.statsEnabled = Boolean.parseBoolean(value);
        break;
      case "cache.optimizeIntervalMs":
        this.optimizeIntervalMs = parseInteger(value, DEFAULT_OPTIMIZE_INTERVAL_MS);
        break;
      default:
        logger.warn("Unknown configuration key: {}", key);
    }
  }

  // 辅助方法
  private int getIntProperty(Properties properties, String key, int defaultValue) {
    String value = properties.getProperty(key);
    return parseInteger(value, defaultValue);
  }

  private boolean getBooleanProperty(Properties properties, String key, boolean defaultValue) {
    String value = properties.getProperty(key);
    return value != null ? Boolean.parseBoolean(value) : defaultValue;
  }

  private int parseInteger(String value, int defaultValue) {
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      logger.warn("Invalid integer value: {}, using default: {}", value, defaultValue);
      return defaultValue;
    }
  }

  // Getter方法
  public int getCacheExpireTime() {
    return cacheExpireTime;
  }

  public int getCacheMaxSize() {
    return cacheMaxSize;
  }

  public int getSlowRouteThresholdMs() {
    return slowRouteThresholdMs;
  }

  public boolean isCacheEnabled() {
    return cacheEnabled;
  }

  public boolean isStatsEnabled() {
    return statsEnabled;
  }

  public int getOptimizeIntervalMs() {
    return optimizeIntervalMs;
  }

  public boolean isInitialized() {
    return initialized.get();
  }

  // 缓存优化相关方法
  public boolean isCacheOptimizationEnabled() {
    return cacheEnabled;
  }

  public int getOptimizeCallThreshold() {
    return 100;
  } // 默认阈值为100次调用

  public int getSlowThresholdMs() {
    return slowRouteThresholdMs;
  }

  // 路由策略相关方法
  public boolean isWeightedRoutingEnabled() {
    return false;
  } // 默认禁用权重路由

  public boolean isGrayReleaseEnabled() {
    return false;
  } // 默认禁用灰度发布
}
