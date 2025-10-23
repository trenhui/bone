package com.bone.engine.extension.router;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.context.BizContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import org.springframework.expression.Expression;
import org.springframework.util.StringUtils;

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

    /**
     * 默认构造函数，使用默认缓存配置
     */
    public CacheManager() {
        this.cacheMaxSize = 10000;
        this.cacheExpireTime = 10;
        // 延迟初始化，等待initialize()调用
    }

    /**
     * 构造函数，支持自定义缓存配置
     */
    public CacheManager(int maximumSize, long expireMinutes) {
        this.cacheMaxSize = maximumSize;
        this.cacheExpireTime = (int) expireMinutes;
        // 延迟初始化，等待initialize()调用
    }
    
    @Override
    public String getComponentName() {
        return "CacheManager";
    }
    
    @Override
    protected void doInitialize() throws Exception {
        initializeCache(cacheExpireTime, cacheMaxSize);
        logger.info("CacheManager initialized");
    }
    
    @Override
    protected void doShutdown() {
        clearAllCache();
        routeResultCache = null;
        logger.info("CacheManager shut down");
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // 尝试通过反射调用isInitialized方法
            Method isInitializedMethod = getClass().getSuperclass().getMethod("isInitialized");
            return (Boolean) isInitializedMethod.invoke(this) && routeResultCache != null;
        } catch (Exception e) {
            // 如果方法不存在，返回默认值
            return routeResultCache != null;
        }
    }
    
    /**
     * 初始化缓存
     */
    @Override
    public void initializeCache(int expireTime, int maxSize) {
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
     */
    public static class RouteCacheKey {
        private final Class<?> extPointType;
        private final Method method;
        private final String tenantCode;
        private final String bizCode;
        private final String useCase;
        private final String scenario;
        private final String env;
        private final String userGroup;
        private final Map<String, String> keyTags;
        
        public RouteCacheKey(Class<?> extPointType, Method method, BizContext<?> context) {
        this.extPointType = extPointType;
        this.method = method;
        this.tenantCode = safeGetString(context, "getTenantCode");
        this.bizCode = safeGetString(context, "getBizCode");
        this.useCase = safeGetString(context, "getUseCase");
        this.scenario = safeGetString(context, "getScenario");
        this.env = safeGetString(context, "getEnv");
        this.userGroup = safeGetString(context, "getUserGroup");
        
        // 提取关键标签用于缓存键
        this.keyTags = new HashMap<>();
        Map<String, Object> allTags = safeGetTags(context, "getAllTags");
        if (allTags != null) {
            for (Map.Entry<String, Object> entry : allTags.entrySet()) {
                if (entry.getValue() != null) {
                    keyTags.put(entry.getKey(), entry.getValue().toString());
                }
            }
        }
    }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RouteCacheKey that = (RouteCacheKey) o;
            return Objects.equals(extPointType, that.extPointType) &&
                   Objects.equals(method, that.method) &&
                   Objects.equals(tenantCode, that.tenantCode) &&
                   Objects.equals(bizCode, that.bizCode) &&
                   Objects.equals(useCase, that.useCase) &&
                   Objects.equals(scenario, that.scenario) &&
                   Objects.equals(env, that.env) &&
                   Objects.equals(userGroup, that.userGroup) &&
                   Objects.equals(keyTags, that.keyTags);
        }

        @Override
        public int hashCode() {
            return Objects.hash(extPointType, method, tenantCode, bizCode, 
                               useCase, scenario, env, userGroup, keyTags);
        }

        @Override
        public String toString() {
            return "RouteCacheKey{" +
                   "extPointType=" + extPointType.getSimpleName() +
                   ", method=" + method.getName() +
                   ", tenantCode='" + tenantCode + '\'' +
                   ", bizCode='" + bizCode + '\'' +
                   ", useCase='" + useCase + '\'' +
                   ", scenario='" + scenario + '\'' +
                   ", env='" + env + '\'' +
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
        return routeResultCache.getIfPresent(cacheKey);
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
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("routeRuleCacheSize", routeRuleCache.size());
        stats.put("extensionImplementationMapSize", extensionImplementationMap.size());
        stats.put("defaultImplementationCacheSize", defaultImplementationCache.size());
        stats.put("routeResultCacheStats", routeResultCache.stats());
        stats.put("expressionCacheSize", expressionCache.size());
        return stats;
    }

    /**
     * 预热缓存
     */
    @Override
    public void warmupCache(Class<?> extPointClass, BizContext<?> context) {
        // 实现缓存预热逻辑
        // 尝试调用ensureInitialized方法
        try {
            Method ensureInitializedMethod = getClass().getMethod("ensureInitialized");
            ensureInitializedMethod.invoke(this);
        } catch (Exception e) {
            // 如果方法不存在，忽略
        }
        // 可以在这里预加载一些常用的扩展点实现到缓存中
        if (extPointClass != null && context != null) {
            logger.debug("Warming up cache for extPointClass: {}", extPointClass.getName());
            // 预热实现逻辑可以根据实际需求添加
        }
    }
    
    /**
     * 安全获取字符串属性
     */
    private static String safeGetString(Object obj, String methodName) {
        if (obj == null) {
            return null;
        }
        try {
            Method method = obj.getClass().getMethod(methodName);
            Object result = method.invoke(obj);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            // 忽略方法调用失败
            return null;
        }
    }
    
    /**
     * 安全获取标签映射
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> safeGetTags(Object obj, String methodName) {
        if (obj == null) {
            return null;
        }
        try {
            Method method = obj.getClass().getMethod(methodName);
            Object result = method.invoke(obj);
            return (Map<String, Object>) result;
        } catch (Exception e) {
            // 忽略方法调用失败
            return null;
        }
    }
    
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
        // 重新初始化配置
        this.cacheExpireTime = expireTime;
        this.cacheMaxSize = maxSize;
        // 重新初始化缓存
        initializeCache(expireTime, maxSize);
        logger.info("Cache reinitialized with expireTime: {} minutes, maxSize: {}", expireTime, maxSize);
    }
    
    /**
     * 配置变更处理（兼容旧方法）
     */
    public void onConfigChanged(String configKey) {
        // 清除相关缓存
        clearAllCache();
        logger.info("Cache cleared due to config change: {}", configKey);
    }
}