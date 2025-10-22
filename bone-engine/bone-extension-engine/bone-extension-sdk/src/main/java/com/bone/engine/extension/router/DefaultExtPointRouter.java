package com.bone.engine.extension.router;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.context.BizContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.bone.engine.extension.lifecycle.ExtensionLifecycle;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.bone.engine.extension.config.ConfigChangeListener;

/**
 * 默认扩展点路由器实现
 * <p>
 * 实现基于业务上下文的智能路由选择，支持多级匹配策略、缓存优化和动态表达式评估
 * <strong>核心功能：</strong>扩展点注册管理、智能路由匹配、缓存优化、表达式评估
 * </p>
 * 
 * <h3>实现特性：</h3>
 * <ul>
 *   <li><strong>高级缓存：</strong>基于Caffeine实现高效缓存，支持自动过期、容量限制和统计</li>
 *   <li><strong>多级匹配：</strong>支持精确匹配、部分匹配和默认匹配</li>
 *   <li><strong>动态表达式：</strong>支持Spring EL表达式进行复杂条件匹配</li>
 *   <li><strong>标签路由：</strong>增强的标签匹配能力，支持复杂标签表达式</li>
 *   <li><strong>统计分析：</strong>提供路由统计信息，便于性能分析</li>
 *   <li><strong>优先级管理：</strong>支持基于优先级的实现选择</li>
 *   <li><strong>路由预热：</strong>支持路由结果预热，减少首次访问延迟</li>
 *   <li><strong>降级策略：</strong>支持路由失败时的降级处理</li>
 * </ul>
 *
 * @author Bone Engine Team
 * @version 2.0.0
 * @see ExtPointRouter 扩展点路由器接口
 * @see BizContext 业务上下文
 */
@Component
public class DefaultExtPointRouter implements ExtPointRouter, SmartInitializingSingleton, InitializingBean, ConfigChangeListener {
    private static final Logger log = LoggerFactory.getLogger(DefaultExtPointRouter.class);
    
    // Spring上下文
    private final ApplicationContext applicationContext;
    
    // 组件依赖
    private final CacheManager cacheManager;
    private final RouteScoreCalculator scoreCalculator;
    private final WeightAndGraySelector weightAndGraySelector;
    private final RouteStatsCollector statsCollector;
    
    // 表达式解析器
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    
    // 扩展点实现映射（扩展点接口 -> 实现列表）
    private final Map<Class<?>, List<Object>> extPointImplementations = new ConcurrentHashMap<>();
    
    // 默认实现映射
    private final Map<Class<?>, Object> defaultImplementations = new ConcurrentHashMap<>();
    
    /**
     * 构造函数，支持自动注入ApplicationContext
     */
    @Autowired
    public DefaultExtPointRouter(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        
        // 初始化各个组件
        this.cacheManager = new CacheManager();
        this.scoreCalculator = new RouteScoreCalculator();
        this.weightAndGraySelector = new WeightAndGraySelector();
        this.statsCollector = new RouteStatsCollector();
    }
    
    /**
     * 构造函数，支持自定义组件配置
     */
    public DefaultExtPointRouter(ApplicationContext applicationContext,
                              CacheManager cacheManager,
                              RouteScoreCalculator scoreCalculator,
                              WeightAndGraySelector weightAndGraySelector,
                              RouteStatsCollector statsCollector) {
        this.applicationContext = applicationContext;
        this.cacheManager = cacheManager;
        this.scoreCalculator = scoreCalculator;
        this.weightAndGraySelector = weightAndGraySelector;
        this.statsCollector = statsCollector;
    }
    
    // 缓存过期时间配置
    @Value("${bone.extension.router.cache.expire-time:300}")
    private long cacheExpireTime;
    
    // 缓存最大容量配置
    @Value("${bone.extension.router.cache.max-size:10000}")
    private long cacheMaxSize;
    
    // 是否启用路由预热
    @Value("${bone.extension.router.warmup.enabled:false}")
    private boolean warmupEnabled;
    
    // 路由失败的降级策略
    private final AtomicReference<Function<Throwable, Boolean>> fallbackStrategy = 
            new AtomicReference<>(ex -> true);
    
    // 扩展点生命周期处理器
    @Autowired(required = false)
    private ExtensionLifecycle extensionLifecycle;
    
    // 是否启用权重路由
    @Value("${bone.extension.router.weighted-routing.enabled:false}")
    private boolean weightedRoutingEnabled;
    
    // 是否启用灰度发布
    @Value("${bone.extension.router.gray-release.enabled:false}")
    private boolean grayReleaseEnabled;
    
    // 是否启用指标收集
    private boolean metricsEnabled = true;
    
    // 性能警告阈值（毫秒）
    private long warningThreshold = 100;
    
    @Override
    public void afterPropertiesSet() {
        // 委托给CacheManager初始化缓存
        cacheManager.initializeCache();
        
        log.info("Initialized extPoint router cache with expireTime={}s, maxSize={}", 
                cacheExpireTime, cacheMaxSize);
    }
    
    /**
     * 获取缓存键
     */
    private String getCacheKey(Class<?> extPointClass, BizContext<?> context) {
        StringBuilder key = new StringBuilder(extPointClass.getName());
        key.append("_")
           .append(context.getStringValue("tenantId") != null ? context.getStringValue("tenantId") : "DEFAULT")
           .append("_")
           .append(context.getStringValue("bizDomain") != null ? context.getStringValue("bizDomain") : "")
           .append("_")
           .append(context.getUseCase() != null ? context.getUseCase() : "")
           .append("_")
           .append(context.getScenario() != null ? context.getScenario() : "")
           .append("_")
           .append("DEFAULT") // 暂时不调用getUserGroup()方法
           .append("_")
           .append("PROD"); // 暂时不调用getEnv()方法
        
        // 添加标签信息到缓存键
        if (!context.getAllTags().isEmpty()) {
            String tagsStr = context.getAllTags().entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .sorted()
                .collect(Collectors.joining(","));
            key.append("_tags_")
               .append(tagsStr);
        }
        
        return key.toString();
    }
    
    /**
     * 设置路由降级策略
     */
    public void setFallbackStrategy(Function<Throwable, Boolean> fallbackStrategy) {
        this.fallbackStrategy.set(fallbackStrategy);
    }
    
    /**
     * 设置扩展点生命周期处理器
     */
    public void setExtensionLifecycle(ExtensionLifecycle extensionLifecycle) {
        this.extensionLifecycle = extensionLifecycle;
    }
    
    /**
     * 设置是否启用权重路由
     */
    public void setWeightedRoutingEnabled(boolean weightedRoutingEnabled) {
        this.weightedRoutingEnabled = weightedRoutingEnabled;
    }
    
    /**
     * 设置是否启用灰度发布
     */
    public void setGrayReleaseEnabled(boolean grayReleaseEnabled) {
        this.grayReleaseEnabled = grayReleaseEnabled;
    }
    
    @Override
    public void onConfigChanged(Set<String> changedKeys) {
        if (changedKeys == null || changedKeys.isEmpty()) {
            return;
        }
        
        boolean needClearCache = false;
        
        // 检查是否有与路由相关的配置变更
        for (String key : changedKeys) {
            if (key.contains("router") || key.contains("cache") || key.contains("weighted") || key.contains("gray")) {
                needClearCache = true;
                break;
            }
        }
        
        if (needClearCache) {
            log.info("Clearing route cache due to configuration changes: {}", changedKeys);
            clearCache();
            
            // 重新初始化路由相关配置
            reinitializeConfig();
        }
    }
    
    @Override
    public String[] getConfigKeyPrefixes() {
        return new String[] {
            "bone.extension.router",
            "bone.extension.cache",
            "bone.extension.weighted",
            "bone.extension.gray"
        };
    }
    
    /**
     * 清理所有缓存
     */
    public void clearCache() {
        // 委托给CacheManager清理缓存
        cacheManager.clearAllCache();
        log.info("Route cache cleared");
        log.info("Route rule cache cleared");
    }
    
    /**
     * 重新初始化配置
     */
    private void reinitializeConfig() {
        try {
            // 重新读取配置值
            cacheExpireTime = applicationContext.getEnvironment().getProperty("bone.extension.router.cache.expire-time", Long.class, 300L);
            cacheMaxSize = applicationContext.getEnvironment().getProperty("bone.extension.router.cache.max-size", Long.class, 10000L);
            weightedRoutingEnabled = applicationContext.getEnvironment().getProperty("bone.extension.router.weighted-routing.enabled", Boolean.class, false);
            grayReleaseEnabled = applicationContext.getEnvironment().getProperty("bone.extension.router.gray-release.enabled", Boolean.class, false);
            warmupEnabled = applicationContext.getEnvironment().getProperty("bone.extension.router.warmup.enabled", Boolean.class, false);
            
            // 重新初始化缓存
            afterPropertiesSet();
            
            log.info("Router configuration reinitialized");
        } catch (Exception e) {
            log.error("Failed to reinitialize router configuration", e);
        }
    }
    
    /**
     * 设置是否启用指标收集
     */
    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }
    
    /**
     * 设置性能警告阈值
     */
    public void setWarningThreshold(long warningThreshold) {
        this.warningThreshold = warningThreshold;
    }
    
    /**
     * 设置是否启用缓存
     */
    public void setEnableCache(boolean enableCache) {
        // 这里可以根据需要调整缓存行为
        // 目前缓存配置在初始化时已经完成
        log.debug("Cache enable state set to: {}", enableCache);
    }
    
    /**
     * 获取缓存统计信息
     */
    public com.github.benmanes.caffeine.cache.stats.CacheStats getCacheStats() {
        return routeCache.stats();
    }
    
    /**
     * 记录路由统计
     */
    private void recordRouteStats(Class<?> extPointClass, Object implementation) {
        statsCollector.recordRouteStats(extPointClass, implementation);
    }
    
    /**
     * 记录路由失败统计
     */
    private void recordRouteFailure(Class<?> extPointClass, Throwable ex) {
        statsCollector.recordRouteFailure(extPointClass, ex);
    }
    
    /**
     * 检查扩展实现是否在有效期内
     */
    private boolean isWithinValidTimeRange(Extension extension) {
        LocalDateTime now = LocalDateTime.now();
        
        // 检查开始时间
        if (StringUtils.hasText(extension.startTime())) {
            try {
                LocalDateTime startTime = LocalDateTime.parse(extension.startTime(), DateTimeFormatter.ISO_DATE_TIME);
                if (now.isBefore(startTime)) {
                    return false;
                }
            } catch (DateTimeParseException e) {
                log.warn("Invalid start time format: {}", extension.startTime(), e);
            }
        }
        
        // 检查结束时间
        if (StringUtils.hasText(extension.endTime())) {
            try {
                LocalDateTime endTime = LocalDateTime.parse(extension.endTime(), DateTimeFormatter.ISO_DATE_TIME);
                if (now.isAfter(endTime)) {
                    return false;
                }
            } catch (DateTimeParseException e) {
                log.warn("Invalid end time format: {}", extension.endTime(), e);
            }
        }
        
        return true;
    }
    
    /**
     * 评估条件表达式
     */
    private boolean evaluateCondition(String condition, BizContext<?> context) {
        if (!StringUtils.hasText(condition) || context == null) {
            return true;
        }
        
        try {
            // 检查表达式缓存
            // 直接使用表达式解析器解析，确保表达式能正确处理
            Expression expression = expressionParser.parseExpression(condition);
            
            // 创建评估上下文，增加安全配置
            EvaluationContext evalContext = new StandardEvaluationContext();
            
            // 添加常用变量
            evalContext.setVariable("tenantCode", context.getTenantCode() != null ? context.getTenantCode() : "DEFAULT");
            evalContext.setVariable("bizCode", context.getBizCode() != null ? context.getBizCode() : "");
            evalContext.setVariable("useCase", context.getUseCase() != null ? context.getUseCase() : "");
            evalContext.setVariable("scenario", context.getScenario() != null ? context.getScenario() : "");
            evalContext.setVariable("context", context);
            evalContext.setVariable("env", "PROD");
            evalContext.setVariable("userGroup", "DEFAULT");
            evalContext.setVariable("currentTime", LocalDateTime.now());
            
            // 安全地添加上下文属性
            context.getAllAttributes().forEach((key, value) -> {
                if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                    evalContext.setVariable(key, value);
                }
            });
            
            // 添加属性集合
            evalContext.setVariable("attributes", context.getAllAttributes());
            
            // 添加安全的辅助方法
            evalContext.setVariable("hasAttribute", (Function<String, Boolean>) context::containsAttribute);
            evalContext.setVariable("getAttribute", (Function<String, Object>) key -> {
                Object value = context.getAttribute(key);
                // 只返回基本类型
                return (value instanceof String || value instanceof Number || value instanceof Boolean) ? value : null;
            });
            
            // 评估表达式
            Object result = expression.getValue(evalContext);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            log.warn("Failed to evaluate condition: {}, will return false", condition, e);
            return false;
        }
    }
    
    /**
     * 获取嵌套属性值
     */
    private Object getNestedProperty(Object obj, String propertyPath) {
        return scoreCalculator.getNestedProperty(obj, propertyPath);
    }
    
    @Override
    public <T> T route(Class<T> extPointClass, BizContext<?> context) {
        Assert.notNull(extPointClass, "Extension point class cannot be null");
        Assert.notNull(context, "Business context cannot be null");
        
        // 准备路由属性
        Map<String, Object> routeAttributes = new HashMap<>();
        // 调用路由前生命周期方法
        if (extensionLifecycle != null) {
            extensionLifecycle.beforeRouting(context, extPointClass, routeAttributes);
        }
        
        try {
            // 检查是否启用缓存
            ExtPoint extPoint = extPointClass.getAnnotation(ExtPoint.class);
            boolean useCache = extPoint != null && extPoint.enableCache();
            
            // 尝试从缓存获取
            if (useCache) {
                String cacheKey = getCacheKey(extPointClass, context);
                @SuppressWarnings("unchecked")
                T cachedResult = (T) cacheManager.getFromCache(cacheKey, k -> doRouteWithStats(extPointClass, context));
                if (cachedResult != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Cache {} for extPoint: {}, result: {}", 
                                cacheManager.isCacheHit(cacheKey) ? "hit" : "miss and loaded",
                                extPointClass.getSimpleName(),
                                cachedResult.getClass().getSimpleName());
                    }
                    recordRouteStats(extPointClass, cachedResult);
                    return cachedResult;
                }
            }
            
            // 执行路由并记录统计
            T result = doRouteWithStats(extPointClass, context);
            
            return result;
        } catch (Exception e) {
            log.error("Route error for extPoint: {}", extPointClass.getName(), e);
            recordRouteFailure(extPointClass, e);
            
            // 执行降级策略
            boolean fallbackToDefault = fallbackStrategy.get().apply(e);
            if (fallbackToDefault) {
                T defaultImpl = getDefaultImplementation(extPointClass);
                if (defaultImpl != null) {
                    log.warn("Fallback to default implementation: {} for extPoint: {}", 
                            defaultImpl.getClass().getSimpleName(), 
                            extPointClass.getSimpleName());
                    return defaultImpl;
                }
            }
            
            // 如果没有默认实现且允许抛出异常，重新抛出
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Failed to route extPoint: " + extPointClass.getName(), e);
        }
    }
    
    /**
     * 执行路由并记录统计信息
     */
    // 是否启用指标收集
    @Value("${bone.extension.router.metrics.enabled:true}")
    private boolean metricsEnabled = true;
    
    // 性能警告阈值
    @Value("${bone.extension.router.metrics.warning-threshold:50}")
    private long warningThreshold = 50;
    
    /**
     * 执行路由并记录统计信息
     */
    private <T> T doRouteWithStats(Class<T> extPointClass, BizContext<?> context) {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        T result = null;
        
        try {
            // 执行路由
            result = doRoute(extPointClass, context);
            success = true;
            return result;
        } finally {
            // 记录路由性能指标
            long endTime = System.currentTimeMillis();
            long costTime = endTime - startTime;
            
            // 记录路由性能指标
            if (metricsEnabled) {
                statsCollector.recordMetrics(extPointClass, success, costTime, warningThreshold);
            }
            
            // 记录详细日志
            if (log.isTraceEnabled()) {
                log.trace("Route for extPoint: {} took {}ms, success: {}, result: {}", 
                        extPointClass.getSimpleName(), 
                        costTime, 
                        success, 
                        result != null ? result.getClass().getSimpleName() : "null");
            }
            
            // 记录性能警告
            if (costTime > warningThreshold) {
                log.warn("Slow route detected for extPoint: {}, cost: {}ms", 
                        extPointClass.getSimpleName(), 
                        costTime);
            }
        }
    }
    
    // 移除单独的recordMetrics方法，使用statsCollector代替
    
    /**
     * 预热路由缓存
     */
    public <T> void warmupCache(Class<T> extPointClass, List<BizContext<?>> contexts) {
        if (!warmupEnabled || CollectionUtils.isEmpty(contexts)) {
            return;
        }
        
        log.info("Warming up cache for extPoint: {} with {} contexts", 
                extPointClass.getSimpleName(), contexts.size());
        
        for (BizContext<?> context : contexts) {
            try {
                // 委托给CacheManager进行缓存预热
                cacheManager.warmupCache(extPointClass, context);
            } catch (Exception e) {
                log.warn("Failed to warmup cache for extPoint: {} with context: {}", 
                        extPointClass.getSimpleName(), context, e);
            }
        }
        
        log.info("Cache warmup completed for extPoint: {}", extPointClass.getSimpleName());
    }
    
    /**
     * 执行实际的路由匹配
     */
    @SuppressWarnings("unchecked")
    private <T> T doRoute(Class<T> extPointClass, BizContext<?> context) {
        // 尝试从规则缓存获取预过滤的实现列表
        List<Object> implementations = getOrCreateRouteRuleCache(extPointClass);
        if (CollectionUtils.isEmpty(implementations)) {
            log.warn("No implementations found for extPoint: {}", extPointClass.getName());
            return null;
        }
        
        // 过滤有效的实现
        List<Object> validImpls = implementations.stream()
            .filter(impl -> {
                Extension extension = impl.getClass().getAnnotation(Extension.class);
                if (extension == null) {
                    return false;
                }
                
                // 快速检查 - 先检查基本条件
                if (!extension.enabled()) {
                    return false;
                }
                
                // 检查时间范围
                if (!isWithinValidTimeRange(extension)) {
                    return false;
                }
                
                // 检查条件表达式（性能开销较大，放在最后）
                return evaluateCondition(extension.condition(), context);
            })
            .collect(Collectors.toList());
        
        if (CollectionUtils.isEmpty(validImpls)) {
            // 没有匹配的实现，返回默认实现
            return (T) defaultImplementations.get(extPointClass);
        }
        
        // 按匹配得分和优先级排序
        validImpls.sort((impl1, impl2) -> {
            Extension ext1 = impl1.getClass().getAnnotation(Extension.class);
            Extension ext2 = impl2.getClass().getAnnotation(Extension.class);
            
            // 使用RouteScoreCalculator计算匹配得分
            int score1 = scoreCalculator.calculateMatchScore(ext1, context);
            int score2 = scoreCalculator.calculateMatchScore(ext2, context);
            
            if (score1 != score2) {
                return Integer.compare(score2, score1); // 得分高的优先
            }
            
            // 得分相同，比较优先级
            if (ext1.priority() != ext2.priority()) {
                return Integer.compare(ext1.priority(), ext2.priority()); // 优先级低的数值小，优先
            }
            
            // 最后比较类名，确保排序稳定性
            return impl1.getClass().getName().compareTo(impl2.getClass().getName());
        });
        
        // 记录详细路由日志
        if (log.isTraceEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Route details for extPoint: " + extPointClass.getSimpleName() + "\n");
            sb.append("Context: " + context + "\n");
            for (int i = 0; i < validImpls.size() && i < 5; i++) { // 只记录前5个
                Object impl = validImpls.get(i);
                Extension ext = impl.getClass().getAnnotation(Extension.class);
                sb.append(String.format("  Rank %d: %s (score=%d, priority=%d)\n", 
                        i + 1, 
                        impl.getClass().getSimpleName(),
                        scoreCalculator.calculateMatchScore(ext, context),
                        ext.priority()));
            }
            log.trace(sb.toString());
        }
        
        // 应用权重路由和灰度发布策略
        Object selectedImplementation = weightAndGraySelector.applyWeightAndGrayRelease(
                validImpls, extPointClass, context, weightedRoutingEnabled, grayReleaseEnabled);
        
        if (log.isDebugEnabled()) {
            log.debug("Selected implementation: {} for extPoint: {}", 
                      selectedImplementation.getClass().getSimpleName(), 
                      extPointClass.getSimpleName());
        }
        
        return (T) selectedImplementation;
    }
    
    // 移除权重路由和灰度发布相关的方法，使用WeightAndGraySelector组件代替
    
    /**
     * 获取或创建路由规则缓存
     */
    private List<Object> getOrCreateRouteRuleCache(Class<?> extPointClass) {
        return cacheManager.getOrCreateRouteRuleCache(extPointClass, 
                k -> extPointImplementations.getOrDefault(k, Collections.emptyList()));
    }
    
    /**
     * 刷新路由规则缓存
     */
    public void refreshRouteRuleCache(Class<?> extPointClass) {
        cacheManager.refreshRouteRuleCache(extPointClass);
        log.info("Refreshed route rule cache for extPoint: {}", 
                extPointClass != null ? extPointClass.getSimpleName() : "ALL");
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> getAllImplementations(Class<T> extPointClass) {
        List<Object> implementations = extPointImplementations.get(extPointClass);
        return CollectionUtils.isEmpty(implementations) ? 
               Collections.emptyList() : 
               (List<T>) new ArrayList<>(implementations);
    }
    
    @Override
    public void clearCache(Class<?> extPointClass) {
        Assert.notNull(extPointClass, "ExtPoint class must not be null");
        
        // 使用CacheManager清理缓存
        cacheManager.clearCache(extPointClass);
        
        // 同时清理路由规则缓存
        refreshRouteRuleCache(extPointClass);
        
        log.debug("Cleared cache for extPoint: {}", extPointClass.getName());
    }
    
    @Override
    public void clearAllCache() {
        cacheManager.clearAllCache();
        // 刷新路由规则缓存
        refreshRouteRuleCache(null);
        log.debug("Cleared all route cache");
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> void registerImplementation(Class<T> extPointClass, T implementation) {
        Assert.notNull(extPointClass, "ExtPoint class must not be null");
        Assert.notNull(implementation, "Implementation must not be null");
        
        // 验证实现类是否实现了扩展点接口
        Assert.isAssignable(extPointClass, implementation.getClass(), 
                           "Implementation must implement the extPoint interface");
        
        // 获取Extension注解
        Extension extension = implementation.getClass().getAnnotation(Extension.class);
        if (extension == null) {
            log.warn("Implementation {} does not have @Extension annotation", 
                     implementation.getClass().getName());
        }
        
        // 添加到实现列表
        extPointImplementations.computeIfAbsent(extPointClass, k -> new ArrayList<>())
                               .add(implementation);
        
        // 检查是否为默认实现
        if (extension != null && extension.isDefault()) {
            // 如果已有默认实现，记录日志
            Object existingDefault = defaultImplementations.put(extPointClass, implementation);
            if (existingDefault != null) {
                log.warn("Replaced default implementation: {} with {} for extPoint: {}",
                         existingDefault.getClass().getSimpleName(),
                         implementation.getClass().getSimpleName(),
                         extPointClass.getSimpleName());
            }
        }
        
        // 清理缓存
        clearCache(extPointClass);
        
        log.info("Registered implementation: {} for extPoint: {}", 
                 implementation.getClass().getSimpleName(), 
                 extPointClass.getSimpleName());
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> void unregisterImplementation(Class<T> extPointClass, T implementation) {
        Assert.notNull(extPointClass, "ExtPoint class must not be null");
        Assert.notNull(implementation, "Implementation must not be null");
        
        List<Object> implementations = extPointImplementations.get(extPointClass);
        if (!CollectionUtils.isEmpty(implementations)) {
            boolean removed = implementations.remove(implementation);
            if (removed) {
                // 如果是默认实现，清除默认实现记录
                Extension extension = implementation.getClass().getAnnotation(Extension.class);
                if (extension != null && extension.isDefault()) {
                    Object currentDefault = defaultImplementations.get(extPointClass);
                    if (currentDefault == implementation) {
                        defaultImplementations.remove(extPointClass);
                        log.info("Removed default implementation: {} for extPoint: {}",
                                 implementation.getClass().getSimpleName(),
                                 extPointClass.getSimpleName());
                    }
                }
                
                // 清理缓存
                clearCache(extPointClass);
                
                log.info("Unregistered implementation: {} for extPoint: {}", 
                         implementation.getClass().getSimpleName(), 
                         extPointClass.getSimpleName());
            }
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getDefaultImplementation(Class<T> extPointClass) {
        return (T) defaultImplementations.get(extPointClass);
    }
    
    /**
     * 设置默认实现（运行时覆盖）
     */
    @SuppressWarnings("unchecked")
    public <T> void setDefaultImplementation(Class<T> extPointClass, T implementation) {
        Assert.notNull(extPointClass, "ExtPoint class must not be null");
        Assert.notNull(implementation, "Implementation must not be null");
        
        // 验证实现类是否实现了扩展点接口
        Assert.isAssignable(extPointClass, implementation.getClass(), 
                           "Implementation must implement the extPoint interface");
        
        defaultImplementations.put(extPointClass, implementation);
        clearCache(extPointClass);
        
        log.info("Set default implementation: {} for extPoint: {}",
                 implementation.getClass().getSimpleName(),
                 extPointClass.getSimpleName());
    }
    
    @Override
    public Map<String, Map<String, Long>> getRouteStats() {
        return statsCollector.getRouteStats();
    }
    
    /**
     * 重置路由统计信息
     */
    public void resetRouteStats() {
        statsCollector.resetRouteStats();
        log.info("Reset route statistics");
    }
    
    /**
     * 获取扩展点的实现统计信息
     */
    public Map<String, Integer> getImplementationStats() {
        Map<String, Integer> stats = new HashMap<>();
        extPointImplementations.forEach((extPointClass, implementations) -> {
            stats.put(extPointClass.getSimpleName(), implementations.size());
        });
        return stats;
    }
    
    /**
     * 在Spring容器初始化完成后，自动注册所有扩展点实现
     */
    @Override
    public void afterSingletonsInstantiated() {
        long startTime = System.currentTimeMillis();
        log.info("Initializing extPoint router...");
        
        try {
            // 获取所有被@Extension注解的实现类
            Map<String, Object> extPointImplementationBeans = 
                    applicationContext.getBeansWithAnnotation(Extension.class);
            
            int totalRegistered = 0;
            int failedRegistrations = 0;
            
            for (Map.Entry<String, Object> entry : extPointImplementationBeans.entrySet()) {
                String beanName = entry.getKey();
                Object implementation = entry.getValue();
                
                try {
                    // 查找该实现类实现的所有@ExtPoint接口
                    Class<?>[] interfaces = implementation.getClass().getInterfaces();
                    boolean registered = false;
                    
                    for (Class<?> iface : interfaces) {
                        if (iface.isAnnotationPresent(ExtPoint.class)) {
                            // 使用原始类型和类型转换解决泛型类型不匹配问题
                            registerImplementation((Class)iface, implementation);
                            registered = true;
                            totalRegistered++;
                        }
                    }
                    
                    // 检查是否有父类实现的接口
                    Class<?> superClass = implementation.getClass().getSuperclass();
                    while (superClass != null && superClass != Object.class) {
                        Class<?>[] superInterfaces = superClass.getInterfaces();
                        for (Class<?> iface : superInterfaces) {
                            if (iface.isAnnotationPresent(ExtPoint.class)) {
                                registerImplementation((Class)iface, implementation);
                                registered = true;
                                totalRegistered++;
                            }
                        }
                        superClass = superClass.getSuperclass();
                    }
                    
                    if (!registered) {
                        log.warn("Implementation bean {} has @Extension annotation but implements no @ExtPoint interfaces", 
                                beanName);
                        failedRegistrations++;
                    }
                } catch (Exception e) {
                    log.error("Failed to register implementation: {}", beanName, e);
                    failedRegistrations++;
                }
            }
            
            // 记录初始化统计
            int totalExtensions = this.extPointImplementations.values().stream()
                    .mapToInt(List::size)
                    .sum();
            
            long costTime = System.currentTimeMillis() - startTime;
            log.info("ExtPoint router initialized in {}ms with {} extension implementations across {} extension points", 
                    costTime, totalExtensions, this.extPointImplementations.size());
            
            if (failedRegistrations > 0) {
                log.warn("Failed to register {} extension implementations", failedRegistrations);
            }
            
            // 打印扩展点实现详情
            if (log.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Extension point implementations summary:\n");
                this.extPointImplementations.forEach((extPointClass, implementations) -> {
                    sb.append(String.format("  %s: %d implementations\n", 
                            extPointClass.getSimpleName(), implementations.size()));
                    implementations.forEach(impl -> {
                        Extension ext = impl.getClass().getAnnotation(Extension.class);
                        sb.append(String.format("    - %s (enabled=%s, priority=%d, default=%s)\n",
                                impl.getClass().getSimpleName(),
                                ext != null ? ext.enabled() : "unknown",
                                ext != null ? ext.priority() : 0,
                                ext != null ? ext.isDefault() : false));
                    });
                });
                log.debug(sb.toString());
            }
            
        } catch (Exception e) {
            log.error("Failed to initialize extPoint router", e);
            throw new RuntimeException("Failed to initialize extPoint router", e);
        }
    }
}