package com.bone.engine.extension.config;

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
        private boolean enableRuleCache = true;
        private boolean enableDynamicRouter = false;
        private int priorityWeight = 10;

        public boolean isEnableRuleCache() {
            return enableRuleCache;
        }

        public void setEnableRuleCache(boolean enableRuleCache) {
            this.enableRuleCache = enableRuleCache;
        }

        public boolean isEnableDynamicRouter() {
            return enableDynamicRouter;
        }

        public void setEnableDynamicRouter(boolean enableDynamicRouter) {
            this.enableDynamicRouter = enableDynamicRouter;
        }

        public int getPriorityWeight() {
            return priorityWeight;
        }

        public void setPriorityWeight(int priorityWeight) {
            this.priorityWeight = priorityWeight;
        }
    }
}