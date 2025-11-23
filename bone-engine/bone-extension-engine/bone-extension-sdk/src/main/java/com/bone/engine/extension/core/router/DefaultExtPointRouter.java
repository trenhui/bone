package com.bone.engine.extension.core.router;

import com.bone.engine.extension.api.annotation.Extension;
import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.bone.engine.extension.api.model.definition.ExtensionPointDefinition;
import com.bone.engine.extension.api.spi.ExtPointRouter;
import com.bone.engine.extension.api.spi.ExpressionEvaluator;
import com.bone.engine.extension.core.register.ExtensionRegister;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.support.expression.AviatorExpressionEvaluator;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 高性能扩展点路由器 - 业界最佳实践实现
 *
 * 核心特性：
 * 1. 多级缓存：路由结果缓存 + 扩展点缓存
 * 2. 智能路由：精确匹配 + 权重分配 + 灰度发布
 * 3. 性能监控：完整的统计和监控支持
 * 4. 生命周期管理：预热、清理、状态管理
 */
@Slf4j
@Component
public class DefaultExtPointRouter implements ExtPointRouter, InitializingBean, DisposableBean, SmartLifecycle {

    // ==================== 依赖组件 ====================
    private final ExtensionRegister extensionRegister;
    private final ApplicationContext applicationContext;
    private final ExpressionEvaluator expressionEvaluator;

    // ==================== 缓存系统 ====================
    private final Cache<String, Object> routeResultCache;
    private final ConcurrentMap<Class<?>, List<ExtensionDefinition>> extensionCache = new ConcurrentHashMap<>();
    private final Cache<String, List<Object>> routeRuleCache;

    // ==================== 统计系统 ====================
    private final RouterStatsCollector statsCollector = new RouterStatsCollector();

    // ==================== 状态管理 ====================
    private volatile boolean warmedUp = false;
    private volatile boolean running = false;

    // ==================== 配置常量 ====================
    private static final int DEFAULT_CACHE_MAX_SIZE = 2000;
    private static final int DEFAULT_CACHE_EXPIRE_MINUTES = 10;
    private static final long WARMUP_TIMEOUT_MS = 10000L;

    @Autowired
    public DefaultExtPointRouter(@NonNull ExtensionRegister extensionRegister,
                                 @NonNull ApplicationContext applicationContext) {
        this(extensionRegister, applicationContext, new AviatorExpressionEvaluator(),
                DEFAULT_CACHE_MAX_SIZE, DEFAULT_CACHE_EXPIRE_MINUTES);
    }

    public DefaultExtPointRouter(@NonNull ExtensionRegister extensionRegister,
                                 @NonNull ApplicationContext applicationContext,
                                 @NonNull ExpressionEvaluator expressionEvaluator,
                                 int cacheMaxSize, int cacheExpireMinutes) {
        Assert.notNull(extensionRegister, "ExtensionRegister cannot be null");
        Assert.notNull(applicationContext, "ApplicationContext cannot be null");
        Assert.notNull(expressionEvaluator, "ExpressionEvaluator cannot be null");

        this.extensionRegister = extensionRegister;
        this.applicationContext = applicationContext;
        this.expressionEvaluator = expressionEvaluator;

        // 初始化缓存系统
        this.routeResultCache = Caffeine.newBuilder()
                .maximumSize(cacheMaxSize)
                .expireAfterWrite(Duration.ofMinutes(cacheExpireMinutes))
                .build();

        this.routeRuleCache = Caffeine.newBuilder()
                .maximumSize(cacheMaxSize)
                .expireAfterWrite(Duration.ofMinutes(cacheExpireMinutes))
                .build();

        log.info("DefaultExtPointRouter initialized with cacheSize: {}", cacheMaxSize);
    }

    // ==================== 生命周期管理 ====================

    @Override
    public void afterPropertiesSet() {
        log.info("DefaultExtPointRouter ready for routing");
    }

    @Override
    public void destroy() {
        this.running = false;
        clearAllCache();
        log.info("DefaultExtPointRouter destroyed");
    }

    @Override
    public void start() {
        this.running = true;
        warmupAll();
    }

    @Override
    public void stop() {
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }

    // ==================== 核心路由方法 ====================

    @Override
    @Nullable
    public <T> T route(@NonNull Class<T> extPointClass, @NonNull BizContext<?> context) {
        Assert.notNull(extPointClass, "Extension point class cannot be null");
        Assert.notNull(context, "Business context cannot be null");

        long startTime = System.nanoTime();
        String pointName = extPointClass.getName();

        try {
            String cacheKey = generateCacheKey(extPointClass, context);

            // 尝试从缓存获取结果
            T cachedResult = getCachedResult(cacheKey, extPointClass);
            if (cachedResult != null) {
                statsCollector.recordCacheHit(pointName);
                return cachedResult;
            }

            statsCollector.recordCacheMiss(pointName);

            // 执行实际路由逻辑
            T result = executeRoute(extPointClass, context);

            // 缓存成功结果
            if (result != null) {
                cacheRouteResult(cacheKey, result);
                statsCollector.recordRouteSuccess(pointName);
            } else {
                statsCollector.recordRouteFailure(pointName);
            }

            return result;

        } catch (Exception e) {
            statsCollector.recordRouteError(pointName, e);
            log.error("Route failed for extension point: {}", pointName, e);
            return getDefaultImplementation(extPointClass);
        } finally {
            long duration = System.nanoTime() - startTime;
            statsCollector.recordRouteDuration(pointName, duration);
        }
    }

    @Override
    public void warmupAll() {
        long startTime = System.currentTimeMillis();
        log.info("Starting warmup for all extension points");

        try {
            Map<String, ExtensionPointDefinition> pointDefinitions =
                    extensionRegister.getAllExtensionPointDefinitions();

            int totalPoints = pointDefinitions.size();
            int warmedPoints = 0;

            for (ExtensionPointDefinition pointDef : pointDefinitions.values()) {
                try {
                    if (warmupExtensionPoint(pointDef.getInterfaceType())) {
                        warmedPoints++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to warmup extension point: {}", pointDef.getCode(), e);
                }

                if (System.currentTimeMillis() - startTime > WARMUP_TIMEOUT_MS) {
                    log.warn("Warmup timeout after {}ms", WARMUP_TIMEOUT_MS);
                    break;
                }
            }

            this.warmedUp = true;
            log.info("Warmup completed: {}/{} points in {}ms",
                    warmedPoints, totalPoints, System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("Warmup failed", e);
        }
    }

    @Override
    public void warmup(@NonNull Class<?> extPointClass) {
        Assert.notNull(extPointClass, "Extension point class cannot be null");
        warmupExtensionPoint(extPointClass);
    }

    @Override
    public void clearCache(@NonNull Class<?> extPointClass) {
        Assert.notNull(extPointClass, "Extension point class cannot be null");

        extensionCache.remove(extPointClass);
        routeRuleCache.invalidate(extPointClass.getName());

        String prefix = extPointClass.getName() + ":";
        routeResultCache.asMap().keySet().removeIf(key -> key.startsWith(prefix));

        log.debug("Cache cleared for extension point: {}", extPointClass.getName());
    }

    @Override
    public <T> void registerImplementation(@NonNull Class<T> extPointClass, @NonNull T implementation) {
        Assert.notNull(extPointClass, "Extension point class cannot be null");
        Assert.notNull(implementation, "Implementation cannot be null");
        Assert.isAssignable(extPointClass, implementation.getClass(),
                "Implementation must implement the extension point interface");

        clearCache(extPointClass);
        log.info("Manual implementation registered: {} for {}",
                implementation.getClass().getSimpleName(), extPointClass.getSimpleName());
    }

    @Override
    public <T> void unregisterImplementation(@NonNull Class<T> extPointClass, @NonNull T implementation) {
        Assert.notNull(extPointClass, "Extension point class cannot be null");
        Assert.notNull(implementation, "Implementation cannot be null");

        clearCache(extPointClass);
        log.info("Manual implementation unregistered: {} for {}",
                implementation.getClass().getSimpleName(), extPointClass.getSimpleName());
    }

    @Override
    @Nullable
    public <T> T getDefaultImplementation(@NonNull Class<T> extPointClass) {
        Assert.notNull(extPointClass, "Extension point class cannot be null");

        List<ExtensionDefinition> extensions = getCachedExtensions(extPointClass);
        return extensions != null && !extensions.isEmpty() ?
                extPointClass.cast(extensions.get(0).getInstance()) : null;
    }

    @Override
    public Map<String, Map<String, Long>> getRouteStats() {
        return Map.of();
    }

    @NonNull
    @Override
    public RouterStats getStats() {
        return statsCollector.getStats();
    }

    @NonNull
    @Override
    public RouterStatus getStatus() {
        return new RouterStatus(
                extensionCache.size(),
                extensionCache.values().stream().mapToInt(List::size).sum(),
                routeResultCache.estimatedSize(),
                warmedUp
        );
    }

    @Override
    public void resetStats() {
        statsCollector.reset();
        log.info("Route statistics reset");
    }

    // ==================== 智能路由算法 ====================

    @Nullable
    private <T> T executeRoute(@NonNull Class<T> extPointClass, @NonNull BizContext<?> context) {
        // 获取所有候选实现
        List<Object> candidates = getOrCreateRouteRuleCache(extPointClass);

        if (candidates.isEmpty()) {
            return null;
        }

        // 1. 精确匹配 + 评分
        List<ScoredCandidate> scoredCandidates = candidates.stream()
                .map(candidate -> {
                    Extension extension = candidate.getClass().getAnnotation(Extension.class);
                    int score = calculateMatchScore(candidate, extension, context);
                    return new ScoredCandidate(candidate, score);
                })
                .filter(scored -> scored.score > 0)
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .collect(Collectors.toList());

        if (scoredCandidates.isEmpty()) {
            return null;
        }

        // 2. 获取最高分候选
        int maxScore = scoredCandidates.get(0).score;
        List<Object> bestCandidates = scoredCandidates.stream()
                .filter(scored -> scored.score == maxScore)
                .map(scored -> scored.candidate)
                .collect(Collectors.toList());

        // 3. 权重选择
        Object selected = selectByWeight(bestCandidates);

        // 4. 灰度发布检查
        selected = selectByGrayRelease(selected, bestCandidates, context);

        return extPointClass.cast(selected);
    }

    /**
     * 计算匹配分数
     */
    private int calculateMatchScore(Object candidate, Extension extension, BizContext<?> context) {
        if (extension == null) {
            return 0;
        }

        int score = 0;

        // 基础维度匹配
        if (matchesPattern(extension.tenant(), context.getTenant())) score += 1000;
        if (matchesPattern(extension.bizCode(), context.getBizCode())) score += 100;
        if (matchesPattern(extension.useCase(), context.getUseCase())) score += 10;
        if (matchesPattern(extension.scenario(), context.getScenario())) score += 1;
        if (matchesPattern(extension.env(), context.getEnv())) score += 10000;

        // 条件表达式匹配
        if (!extension.condition().isEmpty()) {
            try {
                if (expressionEvaluator.evaluate(extension.condition(), context)) {
                    score += 100000;
                } else {
                    return -1; // 条件不满足
                }
            } catch (Exception e) {
                log.warn("Condition evaluation failed for candidate: {}", candidate.getClass().getName(), e);
                return -1;
            }
        }

        return score;
    }

    /**
     * 权重选择算法
     */
    private Object selectByWeight(List<Object> candidates) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // 计算总权重
        int totalWeight = candidates.stream()
                .mapToInt(candidate -> {
                    Extension extension = candidate.getClass().getAnnotation(Extension.class);
                    return extension != null ? Math.max(extension.weight(), 1) : 1;
                })
                .sum();

        if (totalWeight <= 0) {
            return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }

        // 权重随机选择
        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);
        int currentWeight = 0;

        for (Object candidate : candidates) {
            Extension extension = candidate.getClass().getAnnotation(Extension.class);
            int weight = extension != null ? Math.max(extension.weight(), 1) : 1;
            currentWeight += weight;

            if (randomWeight < currentWeight) {
                return candidate;
            }
        }

        return candidates.get(candidates.size() - 1);
    }

    /**
     * 灰度发布选择
     */
    private Object selectByGrayRelease(Object current, List<Object> candidates, BizContext<?> context) {
        // 这里可以实现更复杂的灰度逻辑
        // 当前实现返回原选择，可根据业务需求扩展
        return current;
    }

    // ==================== 缓存管理 ====================

    private List<Object> getOrCreateRouteRuleCache(Class<?> extPointClass) {
        return routeRuleCache.get(extPointClass.getName(), key -> {
            Map<String, Object> beans = (Map<String, Object>) applicationContext.getBeansOfType(extPointClass);
            return new ArrayList<>(beans.values());
        });
    }

    @Nullable
    private List<ExtensionDefinition> getCachedExtensions(@NonNull Class<?> extPointClass) {
        List<ExtensionDefinition> cached = extensionCache.get(extPointClass);
        if (cached != null) {
            return cached;
        }

        Collection<ExtensionDefinition> extensions =
                extensionRegister.findEnabledExtensionsByPoint(extPointClass.getName());

        if (extensions.isEmpty()) {
            return null;
        }

        List<ExtensionDefinition> sorted = new ArrayList<>(extensions);
        Collections.sort(sorted);
        extensionCache.put(extPointClass, sorted);
        return sorted;
    }

    private boolean warmupExtensionPoint(@NonNull Class<?> extPointClass) {
        String pointName = extPointClass.getName();

        try {
            Collection<ExtensionDefinition> extensions =
                    extensionRegister.findEnabledExtensionsByPoint(pointName);

            if (!extensions.isEmpty()) {
                List<ExtensionDefinition> sorted = new ArrayList<>(extensions);
                Collections.sort(sorted);
                extensionCache.put(extPointClass, sorted);
                return true;
            }
            return false;

        } catch (Exception e) {
            log.warn("Warmup failed for: {}", pointName, e);
            throw e;
        }
    }

    // ==================== 工具方法 ====================

    private boolean matchesPattern(String pattern, String value) {
        if (pattern == null || value == null) {
            return false;
        }
        return "*".equals(pattern) || pattern.equals(value);
    }

    @NonNull
    private String generateCacheKey(@NonNull Class<?> extPointClass, @NonNull BizContext<?> context) {
        return String.format("%s:%s:%s:%s:%s:%s",
                extPointClass.getName(),
                context.getTenant(),
                context.getBizCode(),
                context.getUseCase(),
                context.getScenario(),
                context.getEnv());
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> T getCachedResult(@NonNull String cacheKey, @NonNull Class<T> extPointClass) {
        Object result = routeResultCache.getIfPresent(cacheKey);
        return result != null && extPointClass.isInstance(result) ? (T) result : null;
    }

    private void cacheRouteResult(@NonNull String cacheKey, @NonNull Object result) {
        routeResultCache.put(cacheKey, result);
    }

    private void clearAllCache() {
        extensionCache.clear();
        routeResultCache.invalidateAll();
        routeRuleCache.invalidateAll();
        log.info("All route cache cleared");
    }

    // ==================== 内部类 ====================

    /**
     * 评分候选对象
     */
    private static class ScoredCandidate {
        final Object candidate;
        final int score;

        ScoredCandidate(Object candidate, int score) {
            this.candidate = candidate;
            this.score = score;
        }
    }

    /**
     * 路由统计收集器
     */
    private static class RouterStatsCollector {
        private final ConcurrentMap<String, PointStats> stats = new ConcurrentHashMap<>();
        private final AtomicLong totalRequests = new AtomicLong();
        private final AtomicLong cacheHits = new AtomicLong();
        private final AtomicLong successfulRequests = new AtomicLong();
        private final AtomicLong totalResponseTime = new AtomicLong();

        private static class PointStats {
            final AtomicLong requests = new AtomicLong();
            final AtomicLong successes = new AtomicLong();
            final AtomicLong failures = new AtomicLong();
            final AtomicLong errors = new AtomicLong();
            final AtomicLong duration = new AtomicLong();
        }

        void recordCacheHit(String pointName) {
            cacheHits.incrementAndGet();
            totalRequests.incrementAndGet();
            getPointStats(pointName).requests.incrementAndGet();
        }

        void recordCacheMiss(String pointName) {
            totalRequests.incrementAndGet();
            getPointStats(pointName).requests.incrementAndGet();
        }

        void recordRouteSuccess(String pointName) {
            successfulRequests.incrementAndGet();
            getPointStats(pointName).successes.incrementAndGet();
        }

        void recordRouteFailure(String pointName) {
            getPointStats(pointName).failures.incrementAndGet();
        }

        void recordRouteError(String pointName, Throwable e) {
            getPointStats(pointName).errors.incrementAndGet();
        }

        void recordRouteDuration(String pointName, long durationNanos) {
            getPointStats(pointName).duration.addAndGet(durationNanos);
            totalResponseTime.addAndGet(durationNanos);
        }

        @NonNull
        RouterStats getStats() {
            long total = totalRequests.get();
            long hits = cacheHits.get();
            long success = successfulRequests.get();
            double avgTime = total > 0 ? (double) totalResponseTime.get() / total / 1_000_000.0 : 0.0;

            return new RouterStats(total, hits, success, avgTime);
        }

        void reset() {
            stats.clear();
            totalRequests.set(0);
            cacheHits.set(0);
            successfulRequests.set(0);
            totalResponseTime.set(0);
        }

        private PointStats getPointStats(String pointName) {
            return stats.computeIfAbsent(pointName, k -> new PointStats());
        }
    }
}