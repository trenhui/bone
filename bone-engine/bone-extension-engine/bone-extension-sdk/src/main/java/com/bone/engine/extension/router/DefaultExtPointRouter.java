package com.bone.engine.extension.router;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.context.BizContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.bone.engine.extension.lifecycle.ExtensionLifecycle;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

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
public class DefaultExtPointRouter implements ExtPointRouter, SmartInitializingSingleton, InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(DefaultExtPointRouter.class);
    
    // Spring上下文
    @Autowired
    private ApplicationContext applicationContext;
    
    // 表达式解析器
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    
    // 参数名发现器
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    
    // 扩展点实现映射（扩展点接口 -> 实现列表）
    private final Map<Class<?>, List<Object>> extPointImplementations = new ConcurrentHashMap<>();
    
    // 默认实现映射
    private final Map<Class<?>, Object> defaultImplementations = new ConcurrentHashMap<>();
    
    // 路由缓存 - 使用Caffeine替代简单Map
    private Cache<String, Object> routeCache;
    
    // 路由统计信息
    private final Map<String, Map<String, AtomicLong>> routeStats = new ConcurrentHashMap<>();
    
    // 路由规则缓存
    private final Map<Class<?>, List<Object>> routeRuleCache = new ConcurrentHashMap<>();
    
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
    
    @Override
    public void afterPropertiesSet() {
        // 初始化Caffeine缓存
        this.routeCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(cacheExpireTime))
                .maximumSize(cacheMaxSize)
                .recordStats()
                .removalListener((RemovalListener<String, Object>) (key, value, cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Cache entry removed: {}, cause: {}", key, cause);
                    }
                })
                .build();
        
        log.info("Initialized extPoint router cache with expireTime={}s, maxSize={}", 
                cacheExpireTime, cacheMaxSize);
    }
    
    /**
     * 获取缓存键
     */
    private String getCacheKey(Class<?> extPointClass, BizContext<?> context) {
        StringBuilder key = new StringBuilder(extPointClass.getName());
        key.append("_")
           .append(context.getTenantCode() != null ? context.getTenantCode() : "DEFAULT")
           .append("_")
           .append(context.getBizCode() != null ? context.getBizCode() : "")
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
        try {
            if (implementation == null) {
                return;
            }
            
            String extPointName = extPointClass.getSimpleName();
            String implName = implementation.getClass().getSimpleName();
            
            routeStats.computeIfAbsent(extPointName, k -> new ConcurrentHashMap<>())
                      .computeIfAbsent(implName, k -> new AtomicLong())
                      .incrementAndGet();
            
            // 记录总体调用次数
            routeStats.computeIfAbsent("TOTAL", k -> new ConcurrentHashMap<>())
                      .computeIfAbsent(extPointName, k -> new AtomicLong())
                      .incrementAndGet();
        } catch (Exception e) {
            // 统计记录失败不影响主流程
            log.debug("Failed to record route stats", e);
        }
    }
    
    /**
     * 记录路由失败统计
     */
    private void recordRouteFailure(Class<?> extPointClass, Throwable ex) {
        try {
            String extPointName = extPointClass.getSimpleName();
            String errorType = ex.getClass().getSimpleName();
            
            routeStats.computeIfAbsent("FAILURE", k -> new ConcurrentHashMap<>())
                      .computeIfAbsent(extPointName + ":" + errorType, k -> new AtomicLong())
                      .incrementAndGet();
        } catch (Exception e) {
            log.debug("Failed to record route failure stats", e);
        }
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
        if (!StringUtils.hasText(condition)) {
            return true;
        }
        
        try {
            EvaluationContext evalContext = new StandardEvaluationContext();
            evalContext.setVariable("tenantCode", context.getTenantCode() != null ? context.getTenantCode() : "DEFAULT");
            evalContext.setVariable("bizCode", context.getBizCode() != null ? context.getBizCode() : "");
            evalContext.setVariable("useCase", context.getUseCase() != null ? context.getUseCase() : "");
            evalContext.setVariable("scenario", context.getScenario() != null ? context.getScenario() : "");
            evalContext.setVariable("data", null);
            evalContext.setVariable("context", context);
            evalContext.setVariable("env", "PROD");
            evalContext.setVariable("userGroup", "DEFAULT");
            evalContext.setVariable("currentTime", LocalDateTime.now());
            
            // 添加上下文属性到评估环境
            context.getAllAttributes().forEach(evalContext::setVariable);
            
            // 添加属性集合
            evalContext.setVariable("attributes", context.getAllAttributes());
            
            // 添加辅助方法
            evalContext.setVariable("hasAttribute", (Function<String, Boolean>) context::containsAttribute);
            evalContext.setVariable("getAttribute", (Function<String, Object>) context::getAttribute);
            
            Expression expression = expressionParser.parseExpression(condition);
            return Boolean.TRUE.equals(expression.getValue(evalContext, Boolean.class));
        } catch (Exception e) {
            log.warn("Failed to evaluate condition: {}, will return false", condition, e);
            return false;
        }
    }
    
    /**
     * 计算匹配得分
     */
    private int calculateMatchScore(Extension extension, BizContext<?> context) {
        int score = 0;
        String tenantCode = context.getTenantCode() != null ? context.getTenantCode() : "DEFAULT";
        String bizCode = context.getBizCode() != null ? context.getBizCode() : "";
        String useCase = context.getUseCase() != null ? context.getUseCase() : "";
        String scenario = context.getScenario() != null ? context.getScenario() : "";
        String userGroup = "DEFAULT";
        String env = "PROD";
        
        // 租户匹配 - 权重最高
        if (StringUtils.hasText(extension.tenantCode())) {
            if (Objects.equals(extension.tenantCode(), tenantCode)) {
                score += 1000;
            }
        }
        
        // 多租户匹配
        if (!ObjectUtils.isEmpty(extension.multiTenantCodes())) {
            if (Arrays.asList(extension.multiTenantCodes()).contains(tenantCode)) {
                score += 1000;
            }
        }
        
        // 业务域匹配 - 高权重
        if (StringUtils.hasText(extension.bizCode())) {
            if (Objects.equals(extension.bizCode(), bizCode)) {
                score += 100;
            }
        }
        
        // 多业务域匹配
        if (!ObjectUtils.isEmpty(extension.multiBizCodes())) {
            if (Arrays.asList(extension.multiBizCodes()).contains(bizCode)) {
                score += 100;
            }
        }
        
        // 用例匹配 - 中高权重
        if (StringUtils.hasText(extension.useCase())) {
            if (Objects.equals(extension.useCase(), useCase)) {
                score += 50;
            }
        }
        
        // 场景匹配 - 中权重
        if (StringUtils.hasText(extension.scenario())) {
            if (Objects.equals(extension.scenario(), scenario)) {
                score += 20;
            }
        }
        
        // 环境匹配 - 中权重
        if (StringUtils.hasText(extension.env())) {
            if (Objects.equals(extension.env(), env)) {
                score += 20;
            }
        }
        
        // 用户组匹配 - 中权重
        if (StringUtils.hasText(extension.userGroup())) {
            if (Objects.equals(extension.userGroup(), userGroup)) {
                score += 20;
            }
        }
        
        // 增强的标签匹配 - 支持复杂标签表达式
        if (!ObjectUtils.isEmpty(extension.tags())) {
            int tagMatchCount = 0;
            for (String tag : extension.tags()) {
                if (tag.contains(":")) {
                    String[] parts = tag.split(":", 2);
                    String tagKey = parts[0];
                    String tagValue = parts[1];
                    
                    // 支持通配符匹配
                    if (tagValue.contains("*")) {
                        Object contextTagValue = context.getTag(tagKey);
                        if (contextTagValue != null) {
                            String pattern = tagValue.replace("*", ".*");
                            if (contextTagValue.toString().matches(pattern)) {
                                score += 10;
                                tagMatchCount++;
                            }
                        }
                    } else {
                        // 精确匹配
                        Object contextTagValue = context.getTag(tagKey);
                        if (contextTagValue != null && Objects.equals(contextTagValue.toString(), tagValue)) {
                            score += 10;
                            tagMatchCount++;
                        }
                    }
                }
            }
            
            // 标签匹配率奖励
            if (tagMatchCount > 0) {
                double matchRate = (double) tagMatchCount / extension.tags().length;
                if (matchRate > 0.7) {
                    score += 20; // 高匹配率奖励
                } else if (matchRate > 0.5) {
                    score += 10; // 中等匹配率奖励
                }
            }
        }
        
        // 检查支付方式匹配（如果是支付相关扩展点）
        if (StringUtils.hasText(extension.paymentMethod())) {
            try {
                // 跳过paymentMethod检查，避免直接调用getData()
                if (false) {
                    score += 30;
                }
            } catch (Exception e) {
                // 忽略反射异常，说明不是支付相关的业务数据
            }
        }
        
        // 添加优先级调整
        score += (100 - extension.priority()) * 5; // 优先级数值越小，实际优先级越高
        
        return score;
    }
    
    /**
     * 获取嵌套属性值
     */
    private Object getNestedProperty(Object obj, String propertyPath) {
        if (obj == null || !StringUtils.hasText(propertyPath)) {
            return null;
        }
        
        try {
            // 支持嵌套属性访问，如 "order.payment.method"
            String[] parts = propertyPath.split("\\.");
            Object current = obj;
            
            for (String part : parts) {
                String getterMethodName = "get" + Character.toUpperCase(part.charAt(0)) + part.substring(1);
                Method method = current.getClass().getMethod(getterMethodName);
                current = method.invoke(current);
                
                if (current == null) {
                    return null;
                }
            }
            
            return current;
        } catch (Exception e) {
            return null;
        }
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
                T cachedResult = (T) routeCache.get(cacheKey, k -> doRouteWithStats(extPointClass, context));
                if (cachedResult != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Cache {} for extPoint: {}, result: {}", 
                                routeCache.getIfPresent(cacheKey) == null ? "miss and loaded" : "hit",
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
            
            // 记录统计信息
            if (metricsEnabled) {
                recordMetrics(extPointClass, success, costTime);
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
    
    /**
     * 记录路由性能指标
     */
    private <T> void recordMetrics(Class<T> extPointClass, boolean success, long costTime) {
        try {
            // 这里可以集成Prometheus、Micrometer等监控框架
            // 记录路由次数、成功率、耗时等指标
            String extPointName = extPointClass.getSimpleName();
            
            if (log.isDebugEnabled()) {
                log.debug("Metrics for extPoint: {} - Success: {}, Cost: {}ms", 
                        extPointName, success, costTime);
            }
        } catch (Exception e) {
            // 确保监控代码不会影响核心功能
            log.error("Failed to record metrics for extPoint: {}", extPointClass.getName(), e);
        }
    }
    
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
                String cacheKey = getCacheKey(extPointClass, context);
                routeCache.put(cacheKey, doRoute(extPointClass, context));
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
            
            // 计算匹配得分
            int score1 = calculateMatchScore(ext1, context);
            int score2 = calculateMatchScore(ext2, context);
            
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
                        calculateMatchScore(ext, context),
                        ext.priority()));
            }
            log.trace(sb.toString());
        }
        
        // 应用权重路由和灰度发布策略
        Object selectedImplementation = applyWeightAndGrayRelease(validImpls, extPointClass, context);
        
        if (log.isDebugEnabled()) {
            log.debug("Selected implementation: {} for extPoint: {}", 
                      selectedImplementation.getClass().getSimpleName(), 
                      extPointClass.getSimpleName());
        }
        
        return (T) selectedImplementation;
    }
    
    /**
     * 应用权重路由和灰度发布策略
     * @param implementations 有效实现列表
     * @param extPointClass 扩展点接口类
     * @param context 业务上下文
     * @return 选择的实现
     */
    @SuppressWarnings("unchecked")
    private Object applyWeightAndGrayRelease(List<Object> implementations, Class<?> extPointClass, BizContext<?> context) {
        // 如果只有一个实现或功能未启用，直接返回第一个
        if (implementations.size() == 1 || (!weightedRoutingEnabled && !grayReleaseEnabled)) {
            return implementations.get(0);
        }
        
        // 应用灰度发布
        if (grayReleaseEnabled) {
            Object selectedByGray = selectByGrayRelease(implementations, extPointClass, context);
            if (selectedByGray != null) {
                return selectedByGray;
            }
        }
        
        // 应用权重路由
        if (weightedRoutingEnabled) {
            Object selectedByWeight = selectByWeight(implementations, extPointClass, context);
            if (selectedByWeight != null) {
                return selectedByWeight;
            }
        }
        
        // 默认返回第一个实现
        return implementations.get(0);
    }
    
    /**
     * 根据权重选择实现
     */
    private Object selectByWeight(List<Object> implementations, Class<?> extPointClass, BizContext<?> context) {
        try {
            // 计算总权重
            int totalWeight = 0;
            Map<Object, Integer> weightMap = new LinkedHashMap<>();
            
            for (Object impl : implementations) {
                int weight = getWeight(impl, extPointClass);
                if (weight <= 0) {
                    weight = 1; // 默认权重
                }
                weightMap.put(impl, weight);
                totalWeight += weight;
            }
            
            if (totalWeight <= 0) {
                return null; // 无法进行权重选择
            }
            
            // 随机选择
            int randomWeight = new Random().nextInt(totalWeight) + 1;
            int currentWeight = 0;
            
            for (Map.Entry<Object, Integer> entry : weightMap.entrySet()) {
                currentWeight += entry.getValue();
                if (randomWeight <= currentWeight) {
                    return entry.getKey();
                }
            }
            
            return null;
        } catch (Exception e) {
            log.warn("Failed to select implementation by weight for extPoint: {}", extPointClass.getName(), e);
            return null;
        }
    }
    
    /**
     * 根据灰度发布规则选择实现
     */
    private Object selectByGrayRelease(List<Object> implementations, Class<?> extPointClass, BizContext<?> context) {
        try {
            // 查找流量比例小于100的实现（灰度实现）
            List<Object> grayImplementations = new ArrayList<>();
            Map<Object, Integer> trafficRateMap = new HashMap<>();
            
            for (Object impl : implementations) {
                Extension extension = impl.getClass().getAnnotation(Extension.class);
                if (extension != null && extension.trafficRate() > 0 && extension.trafficRate() < 100) {
                    grayImplementations.add(impl);
                    trafficRateMap.put(impl, extension.trafficRate());
                }
            }
            
            if (CollectionUtils.isEmpty(grayImplementations)) {
                return null; // 没有灰度实现
            }
            
            // 根据流量比例决定是否选择灰度实现
            int randomValue = new Random().nextInt(100);
            Object selectedImpl = null;
            int currentRate = 0;
            
            for (Object impl : grayImplementations) {
                int trafficRate = trafficRateMap.getOrDefault(impl, 10);
                currentRate += trafficRate;
                if (randomValue < currentRate) {
                    selectedImpl = impl;
                    break;
                }
            }
            
            return selectedImpl;
        } catch (Exception e) {
            log.warn("Failed to select implementation by gray release for extPoint: {}", extPointClass.getName(), e);
            return null;
        }
    }
    
    /**
     * 获取实现的权重
     */
    protected int getWeight(Object implementation, Class<?> extPointClass) {
        // 从Extension注解中获取权重
        Extension extension = implementation.getClass().getAnnotation(Extension.class);
        return extension != null ? extension.weight() : 1;
    }
    
    /**
     * 检查是否匹配灰度发布条件
     */
    protected boolean matchGrayReleaseCondition(BizContext<?> context) {
        // 可以基于用户ID、时间等条件进行灰度判断
        // 默认实现使用简单的10%流量灰度
        return new Random().nextInt(100) < 10;
    }
    
    /**
     * 获取或创建路由规则缓存
     */
    private List<Object> getOrCreateRouteRuleCache(Class<?> extPointClass) {
        return routeRuleCache.computeIfAbsent(extPointClass, 
                k -> extPointImplementations.getOrDefault(k, Collections.emptyList()));
    }
    
    /**
     * 刷新路由规则缓存
     */
    public void refreshRouteRuleCache(Class<?> extPointClass) {
        routeRuleCache.remove(extPointClass);
        // 预热规则缓存
        if (extPointClass != null) {
            getOrCreateRouteRuleCache(extPointClass);
        } else {
            // 刷新所有
            routeRuleCache.clear();
        }
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
        
        // 清理该扩展点的所有缓存条目
        String prefix = extPointClass.getName();
        routeCache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
        
        // 同时清理路由规则缓存
        refreshRouteRuleCache(extPointClass);
        
        log.debug("Cleared cache for extPoint: {}", extPointClass.getName());
    }
    
    @Override
    public void clearAllCache() {
        routeCache.invalidateAll();
        routeRuleCache.clear();
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
        return routeStats.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().entrySet().stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        v -> v.getValue().get()
                    ))
            ));
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
     * 重置路由统计信息
     */
    public void resetRouteStats() {
        routeStats.clear();
        log.info("Reset route statistics");
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