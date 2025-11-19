package com.bone.engine.extension.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 扩展点配置属性
 * <p>
 * 用于从Spring Boot配置文件中读取扩展点相关配置
 * <strong>主要配置项：</strong>
 * <ul>
 *   <li>缓存配置</li>
 *   <li>扫描配置</li>
 *   <li>事件配置</li>
 *   <li>路由配置</li>
 * </ul>
 * </p>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
@ConfigurationProperties(prefix = "bone.extension")
public class ExtensionProperties {

    private boolean enabled = true;
    
    private CacheConfig cache = new CacheConfig();
    
    private ScanConfig scan = new ScanConfig();
    
    private EventConfig events = new EventConfig();
    
    private RouterConfig router = new RouterConfig();
    
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

    public EventConfig getEvents() {
        return events;
    }

    public void setEvents(EventConfig events) {
        this.events = events;
    }

    public RouterConfig getRouter() {
        return router;
    }

    public void setRouter(RouterConfig router) {
        this.router = router;
    }
    
    public MonitorConfig getMonitor() {
        return monitor;
    }
    
    public void setMonitor(MonitorConfig monitor) {
        this.monitor = monitor;
    }

    /**
     * 缓存配置
     */
    public static class CacheConfig {
        private boolean enabled = true;
        private long expireTime = 300000; // 默认5分钟
        private int maxSize = 1000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getExpireTime() {
            return expireTime;
        }

        public void setExpireTime(long expireTime) {
            this.expireTime = expireTime;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }
    }

    /**
     * 扫描配置
     */
    public static class ScanConfig {
        private boolean enabled = true;
        private String[] basePackages = {};
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

        public boolean isAutoRegister() {
            return autoRegister;
        }

        public void setAutoRegister(boolean autoRegister) {
            this.autoRegister = autoRegister;
        }
    }

    /**
     * 事件配置
     */
    public static class EventConfig {
        private boolean enabled = true;
        private boolean async = false;
        private String executor = "extensionEventExecutor";

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
     * 路由配置
     */
    public static class RouterConfig {
        
        /**
         * 权重路由配置
         */
        private WeightedConfig weighted = new WeightedConfig();
        
        /**
         * 灰度发布配置
         */
        private GrayReleaseConfig grayRelease = new GrayReleaseConfig();
        
        /**
         * 指标收集配置
         */
        private MetricsConfig metrics = new MetricsConfig();
        
        public WeightedConfig getWeighted() {
            return weighted;
        }
        
        public void setWeighted(WeightedConfig weighted) {
            this.weighted = weighted;
        }
        
        public GrayReleaseConfig getGrayRelease() {
            return grayRelease;
        }
        
        public void setGrayRelease(GrayReleaseConfig grayRelease) {
            this.grayRelease = grayRelease;
        }
        
        public MetricsConfig getMetrics() {
            return metrics;
        }
        
        public void setMetrics(MetricsConfig metrics) {
            this.metrics = metrics;
        }
    }
    
    /**
     * 权重路由配置类
     */
    public static class WeightedConfig {
        
        /**
         * 是否启用权重路由
         */
        private boolean enabled = false;
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
    
    /**
     * 灰度发布配置类
     */
    public static class GrayReleaseConfig {
        
        /**
         * 是否启用灰度发布
         */
        private boolean enabled = false;
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
    
    /**
     * 指标收集配置类
     */
    public static class MetricsConfig {
        
        /**
         * 是否启用指标收集
         */
        private boolean enabled = true;
        
        /**
         * 性能警告阈值（毫秒）
         */
        private long warningThreshold = 50;
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public long getWarningThreshold() {
            return warningThreshold;
        }
        
        public void setWarningThreshold(long warningThreshold) {
            this.warningThreshold = warningThreshold;
        }
    }
    
    /**
     * 监控配置
     */
    public static class MonitorConfig {
        private boolean enabled = true;
        private long slowRouteThreshold = 100; // 默认100ms
        
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
    }
}