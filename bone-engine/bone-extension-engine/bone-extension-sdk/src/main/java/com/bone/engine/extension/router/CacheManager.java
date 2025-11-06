package com.bone.engine.extension.router;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.config.RouterConfiguration;
import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.router.RouteKey;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import org.springframework.expression.Expression;
import org.springframework.util.StringUtils;

import com.bone.engine.extension.util.ReflectionUtils;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 扩展点路由缓存管理器
 * <p>
 * 负责管理扩展点路由相关的各类缓存，提供统一的缓存访问和清理机制
 * </p>
 * 实现RouterComponent.CacheManagerComponent接口
 * 继承AbstractRouterComponent获取生命周期管理能力
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
public class CacheManager extends AbstractRouterComponent implements RouterComponent.CacheManagerComponent {

    private int cacheExpireTime = 10;
    private int cacheMaxSize = 10000;
    
    // 路由规则缓存 - 存储扩展点实现列表
    private final Map<Class<?>, List<Object>> routeRuleCache = new ConcurrentHashMap<>();
    
    // 扩展点实现映射缓存 - 存储类与实现的映射关系
    private final Map<Class<?>, Map<Object, Extension>> extensionImplementationMap = new ConcurrentHashMap<>();
    
    // 默认实现缓存
    private final Map<Class<?>, Object> defaultImplementationCache = new ConcurrentHashMap<>();
    
    // 方法路由结果缓存 - 使用Caffeine进行本地缓存
    private Cache<RouteCacheKey, Object> routeResultCache;
    
    // Spring EL表达式缓存
    private final Map<String, Object> expressionCache = new ConcurrentHashMap<>();
    
    // 路由统计收集器
    private final RouteStatsCollector statsCollector;
    
    // 统一配置管理组件
    private final RouterConfiguration config;
    
    // 随机数生成器，用于采样统计
    private final Random random = new Random();

    /**
     * 默认构造函数，使用默认缓存配置和全局RouterConfiguration
     */
    public CacheManager() {
        this(RouterConfiguration.getInstance());
    }
    
    /**
     * 构造函数，使用指定的RouterConfiguration
     */
    public CacheManager(RouterConfiguration config) {
        this.config = config != null ? config : RouterConfiguration.getInstance();
        // 从统一配置获取缓存设置
        this.cacheMaxSize = config.getCacheMaxSize();
        this.cacheExpireTime = config.getCacheExpireTime();
        this.statsCollector = new RouteStatsCollector();
        // 延迟初始化，等待initialize()调用
    }

    /**
     * 构造函数，支持自定义缓存配置
     */
    public CacheManager(int maximumSize, long expireMinutes) {
        this.config = RouterConfiguration.getInstance();
        this.cacheMaxSize = maximumSize;
        this.cacheExpireTime = (int) expireMinutes;
        this.statsCollector = new RouteStatsCollector();
        // 延迟初始化，等待initialize()调用
    }
    
    @Override
    public String getComponentName() {
        return "CacheManager";
    }
    
    @Override
    protected void doInitialize() throws Exception {
        // 初始化缓存，使用统一配置的值
        int expireTime = config.getCacheExpireTime();
        int maxSize = config.getCacheMaxSize();
        initializeCache(expireTime, maxSize);
        logger.info("CacheManager initialized with cacheExpireTime={} minutes, cacheMaxSize={}", 
                   expireTime, maxSize);
    }
    
    @Override
    protected void doShutdown() {
        clearAllCache();
        routeResultCache = null;
        // 关闭统计收集器
        if (statsCollector != null) {
            statsCollector.shutdown();
        }
        logger.info("CacheManager shut down");
    }
    
    @Override
    public boolean isAvailable() {
        // 直接调用父类的isAvailable方法
        return super.isAvailable() && routeResultCache != null;
    }
    
    /**
     * 初始化缓存
     */
    @Override
    public void initializeCache(int expireTime, int maxSize) {
        // 更新本地配置值
        this.cacheExpireTime = expireTime;
        this.cacheMaxSize = maxSize;
        
        this.routeResultCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofMinutes(expireTime))
                .recordStats()
                .removalListener((RemovalListener<RouteCacheKey, Object>) (key, value, cause) -> {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Route cache entry removed: {}, cause: {}", key, cause);
                    }
                })
                .build();
    }

    /**
     * 路由缓存键，用于唯一标识路由请求
     * <p>
     * 扩展了基础的RouteKey，增加了方法和标签信息
     */
    public static class RouteCacheKey extends RouteKey {
        private final Method method;
        private final String userGroup;
        private final Map<String, String> keyTags;
        private final int hashCode;
        
        public RouteCacheKey(Class<?> extPointType, Method method, BizContext<?> context) {
            super(extPointType, context);
            this.method = method;
            this.userGroup = ReflectionUtils.safeGetString(context, "getUserGroup");
            
            // 提取关键标签用于缓存键
            this.keyTags = new HashMap<>();
            Map<String, Object> allTags = ReflectionUtils.safeGetTags(context, "getAllTags");
            if (allTags != null) {
                for (Map.Entry<String, Object> entry : allTags.entrySet()) {
                    if (entry.getValue() != null) {
                        keyTags.put(entry.getKey(), entry.getValue().toString());
                    }
                }
            }
            
            // 计算哈希码，包含父类属性和新增属性
            this.hashCode = Objects.hash(
                super.hashCode(), 
                method, 
                userGroup, 
                keyTags
            );
        }

        public Method getMethod() {
            return method;
        }
        
        public String getUserGroup() {
            return userGroup;
        }
        
        public Map<String, String> getKeyTags() {
            return keyTags;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RouteCacheKey that = (RouteCacheKey) o;
            return super.equals(o) &&
                   Objects.equals(method, that.method) &&
                   Objects.equals(userGroup, that.userGroup) &&
                   Objects.equals(keyTags, that.keyTags);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return "RouteCacheKey{" +
                   "extPointType=" + getExtPointClass().getSimpleName() +
                   ", method=" + method.getName() +
                   ", tenantCode='" + getTenantCode() + '\'' +
                   ", bizCode='" + getBizCode() + '\'' +
                   ", useCase='" + getUseCase() + '\'' +
                   ", scenario='" + getScenario() + '\'' +
                   ", env='" + getEnv() + '\'' +
                   ", userGroup='" + userGroup + '\'' +
                   ", keyTags=" + keyTags +
                   "}";
        }
    }

    /**
     * 生成路由缓存键
     */
    public RouteCacheKey generateRouteCacheKey(Class<?> extPointType, Method method, BizContext<?> context) {
        return new RouteCacheKey(extPointType, method, context);
    }

    /**
     * 从缓存获取路由结果
     */
    public Object getRouteResult(RouteCacheKey cacheKey) {
        Object result = routeResultCache.getIfPresent(cacheKey);
        boolean isHit = result != null;
        
        // 记录缓存统计（仅当统计功能启用时）
        if (config.isStatsEnabled()) {
            statsCollector.recordCacheStats("routeResultCache", isHit);
        }
        
        return result;
    }
    
    /**
     * 从缓存获取值，如果不存在则计算并缓存
     */
    @Override
    public <V> V getFromCache(String key, Function<String, V> loader) {
        // 这里使用表达式缓存作为通用缓存的实现
        if (isAvailable() && loader != null) {
            // 对于字符串键，我们需要转换为适合缓存的形式
            synchronized (expressionCache) {
                return (V) expressionCache.computeIfAbsent(key, (k) -> loader.apply(k));
            }
        }
        return null;
    }
    
    /**
     * 检查缓存键是否命中
     */
    @Override
    public boolean isCacheHit(String key) {
        return isAvailable() && expressionCache.containsKey(key);
    }

    /**
     * 缓存路由结果
     */
    public void putRouteResult(RouteCacheKey cacheKey, Object result, boolean enableCache) {
        if (enableCache && result != null) {
            routeResultCache.put(cacheKey, result);
            // 定期更新缓存大小统计（采样更新，避免每次put都更新）
            if (statsCollector != null && random.nextBoolean()) {
                statsCollector.updateCacheSize(cacheKey.getExtPointClass().getName(), routeResultCache.estimatedSize());
            }
        }
    }

    /**
     * 获取路由规则缓存
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getRouteRuleCache(Class<T> extPointType) {
        return (List<T>) routeRuleCache.getOrDefault(extPointType, Collections.emptyList());
    }
    
    /**
     * 获取或创建路由规则缓存
     */
    @Override
    public List<Object> getOrCreateRouteRuleCache(Class<?> extPointClass, Function<Class<?>, List<Object>> loader) {
        return routeRuleCache.computeIfAbsent(extPointClass, loader);
    }

    /**
     * 设置路由规则缓存
     */
    public <T> void setRouteRuleCache(Class<T> extPointType, List<T> implementations) {
        routeRuleCache.put(extPointType, new ArrayList<>(implementations));
    }

    /**
     * 获取扩展点实现与注解的映射
     */
    @SuppressWarnings("unchecked")
    public <T> Map<T, Extension> getExtensionImplementationMap(Class<T> extPointType) {
        return (Map<T, Extension>) extensionImplementationMap.computeIfAbsent(extPointType, 
                k -> new ConcurrentHashMap<>());
    }

    /**
     * 添加扩展点实现与注解的映射
     */
    public <T> void addExtensionImplementation(Class<T> extPointType, T implementation, Extension extension) {
        getExtensionImplementationMap(extPointType).put(implementation, extension);
    }

    /**
     * 获取默认实现
     */
    @SuppressWarnings("unchecked")
    public <T> T getDefaultImplementation(Class<T> extPointType) {
        return (T) defaultImplementationCache.get(extPointType);
    }

    /**
     * 设置默认实现
     */
    public <T> void setDefaultImplementation(Class<T> extPointType, T implementation) {
        defaultImplementationCache.put(extPointType, implementation);
    }

    /**
     * 获取表达式缓存
     */
    public String getExpressionCache(String key) {
        Object value = expressionCache.get(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * 获取或创建表达式缓存
     */
    @Override
    public Expression getExpression(String expressionString, Function<String, Expression> parser) {
        if (isAvailable() && StringUtils.hasText(expressionString) && parser != null) {
            return (Expression) expressionCache.computeIfAbsent(expressionString, k -> parser.apply(k));
        }
        return null;
    }

    /**
     * 设置表达式缓存
     */
    public void setExpressionCache(String key, String expression) {
        if (StringUtils.hasText(key) && StringUtils.hasText(expression)) {
            expressionCache.put(key, expression);
        }
    }

    /**
     * 刷新路由规则缓存
     */
    @Override
    public void refreshRouteRuleCache(Class<?> extPointType) {
        if (extPointType != null) {
            routeRuleCache.remove(extPointType);
            logger.info("Refreshed route rule cache for extPointType: {}", extPointType.getName());
        }
    }

    /**
     * 清理特定扩展点类型的所有缓存
     */
    @Override
    public void clearCache(Class<?> extPointType) {
        if (extPointType != null) {
            // 清理路由规则缓存
            routeRuleCache.remove(extPointType);
            // 清理扩展点实现映射
            extensionImplementationMap.remove(extPointType);
            // 清理默认实现
            defaultImplementationCache.remove(extPointType);
            logger.info("Cleared all cache for extPointType: {}", extPointType.getName());
        }
    }

    /**
     * 清理所有缓存
     */
    @Override
    public void clearAllCache() {
        routeRuleCache.clear();
        extensionImplementationMap.clear();
        defaultImplementationCache.clear();
        if (routeResultCache != null) {
            routeResultCache.invalidateAll();
        }
        expressionCache.clear();
        logger.info("Cleared all extension router caches");
    }

    /**
     * 获取缓存统计信息
     * @return 缓存统计数据
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        if (routeResultCache != null) {
            stats.put("hitCount", routeResultCache.stats().hitCount());
            stats.put("missCount", routeResultCache.stats().missCount());
            stats.put("requestCount", routeResultCache.stats().requestCount());
            stats.put("hitRate", routeResultCache.stats().hitRate());
            stats.put("evictionCount", routeResultCache.stats().evictionCount());
            stats.put("loadFailureCount", routeResultCache.stats().loadFailureCount());
        }
        stats.put("routeRuleCacheSize", routeRuleCache.size());
        stats.put("extensionImplementationMapSize", extensionImplementationMap.size());
        stats.put("defaultImplementationCacheSize", defaultImplementationCache.size());
        stats.put("expressionCacheSize", expressionCache.size());
        return stats;
    }
    
    /**
     * 自适应优化缓存配置
     * 根据运行时统计数据动态调整缓存参数
     * 当命中率高时延长缓存时间，命中率低时缩短缓存时间
     */
    public void optimizeCacheConfig() {
        Map<String, Object> stats = getCacheStats();
        Double hitRate = (Double) stats.getOrDefault("hitRate", 0.0);
        long requestCount = (Long) stats.getOrDefault("requestCount", 0L);
        
        // 只有当请求数达到一定量时才进行优化，避免统计偏差
        if (requestCount < 100) {
            if (logger.isDebugEnabled()) {
                logger.debug("Skip cache optimization, request count too low: {}", requestCount);
            }
            return;
        }
        
        int oldExpireTime = this.cacheExpireTime;
        
        // 根据命中率动态调整缓存过期时间
        if (hitRate > 0.8) {
            // 命中率高，提高缓存时间，减少缓存刷新频率
            int newExpireTime = (int)(cacheExpireTime * 1.5);
            // 限制最大缓存时间，避免缓存数据过期不及时
            newExpireTime = Math.min(newExpireTime, 120); // 最多2小时
            if (newExpireTime != oldExpireTime) {
                this.cacheExpireTime = newExpireTime;
                reinitializeConfig(newExpireTime, cacheMaxSize);
                logger.info("Cache optimization: increased expire time from {} to {} minutes (hit rate: {:.2f})", 
                        oldExpireTime, newExpireTime, hitRate);
            }
        } else if (hitRate < 0.4) {
            // 命中率低，降低缓存时间，提高缓存新鲜度
            int newExpireTime = Math.max(1, cacheExpireTime / 2);
            if (newExpireTime != oldExpireTime) {
                this.cacheExpireTime = newExpireTime;
                reinitializeConfig(newExpireTime, cacheMaxSize);
                logger.info("Cache optimization: decreased expire time from {} to {} minutes (hit rate: {:.2f})", 
                        oldExpireTime, newExpireTime, hitRate);
            }
        }
    }

    /**
     * 预热缓存
     */
    @Override
    public void warmupCache(Class<?> extPointClass, BizContext<?> context) {
        // 实现缓存预热逻辑
        // 直接调用父类的ensureInitialized方法
        ensureInitialized();
        
        // 可以在这里预加载一些常用的扩展点实现到缓存中
        if (extPointClass != null && context != null) {
            logger.debug("Warming up cache for extPointClass: {}", extPointClass.getName());
            // 预热实现逻辑可以根据实际需求添加
        }
    }
    
    // 使用ReflectionUtils代替重复的方法实现
    
    /**
     * 处理配置变更
     */
    @Override
    public void onConfigChanged(String key, String value) {
        // 实现配置变更处理逻辑
        ensureInitialized();
        // 清除相关缓存
        clearAllCache();
        logger.info("Cache cleared due to config change: {}, value: {}", key, value);
    }
    
    /**
     * 重新初始化配置
     */
    @Override
    public void reinitializeConfig(int expireTime, int maxSize) {
        // 更新本地配置值，优先使用传入的参数
        this.cacheExpireTime = expireTime;
        this.cacheMaxSize = maxSize;
        
        // 重新初始化缓存以应用新配置
        initializeCache(expireTime, maxSize);
        logger.info("CacheManager configuration reinitialized with expireTime={}, maxSize={}", 
                   expireTime, maxSize);
    }
    
    /**
     * 配置变更处理（兼容旧方法）
     */
    public void onConfigChanged(String configKey) {
        // 处理配置变更 - 委托给统一配置管理
        switch (configKey) {
            case "cacheExpireTime":
            case "cacheMaxSize":
                initializeCache(config.getCacheExpireTime(), config.getCacheMaxSize());
                logger.info("CacheManager reinitialized due to config change: {}", configKey);
                break;
            default:
                break;
        }
    }
}