package com.bone.engine.extension.core.router;

import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.bone.engine.extension.api.model.definition.ExtensionPointDefinition;
import com.bone.engine.extension.core.register.ExtensionRegister;
import com.bone.engine.extension.support.context.BizContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高性能扩展点路由器 - 完全匹配接口规范
 */
@Slf4j
@Component
public class DefaultExtPointRouter implements ExtPointRouter, InitializingBean, DisposableBean, SmartLifecycle {

    private final ExtensionRegister extensionRegister;
    private final Cache<String, Object> routeResultCache;
    private final ConcurrentMap<Class<?>, List<ExtensionDefinition>> extensionCache = new ConcurrentHashMap<>();
    private final RouterStatsCollector statsCollector = new RouterStatsCollector();

    private volatile boolean warmedUp = false;
    private volatile boolean running = false;

    // 配置常量
    private static final int DEFAULT_CACHE_MAX_SIZE = 2000;
    private static final int DEFAULT_CACHE_EXPIRE_MINUTES = 10;
    private static final long WARMUP_TIMEOUT_MS = 10000L;

    @Autowired
    public DefaultExtPointRouter(@NonNull ExtensionRegister extensionRegister) {
        this(extensionRegister, DEFAULT_CACHE_MAX_SIZE, DEFAULT_CACHE_EXPIRE_MINUTES);
    }

    public DefaultExtPointRouter(@NonNull ExtensionRegister extensionRegister,
                                 int cacheMaxSize, int cacheExpireMinutes) {
        Assert.notNull(extensionRegister, "ExtensionRegister cannot be null");

        this.extensionRegister = extensionRegister;
        this.routeResultCache = Caffeine.newBuilder()
                .maximumSize(cacheMaxSize)
                .expireAfterWrite(Duration.ofMinutes(cacheExpireMinutes))
                .build();

        log.info("DefaultExtPointRouter initialized");
    }

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
    @NonNull
    public RouterStats getStats() {
        return statsCollector.getStats();
    }

    @Override
    @NonNull
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

    // ==================== 内部实现方法 ====================

    @Nullable
    private <T> T executeRoute(@NonNull Class<T> extPointClass, @NonNull BizContext<?> context) {
        List<ExtensionDefinition> extensions = getCachedExtensions(extPointClass);
        if (extensions == null || extensions.isEmpty()) {
            return null;
        }

        ExtensionDefinition bestMatch = findBestMatch(extensions, context);
        return bestMatch != null ? extPointClass.cast(bestMatch.getInstance()) : null;
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

    @Nullable
    private ExtensionDefinition findBestMatch(@NonNull List<ExtensionDefinition> extensions,
                                              @NonNull BizContext<?> context) {
        ExtensionDefinition bestMatch = null;
        int bestScore = -1;

        for (ExtensionDefinition extension : extensions) {
            if (!extension.isEnabled()) continue;

            int score = calculateMatchScore(extension, context);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = extension;
            }
        }
        return bestMatch;
    }

    private int calculateMatchScore(@NonNull ExtensionDefinition extension,
                                    @NonNull BizContext<?> context) {
        int score = 0;

        if (matchesPattern(extension.getTenantPattern(), context.getTenant())) score += 1000;
        if (matchesPattern(extension.getBizCodePattern(), context.getBizCode())) score += 100;
        if (matchesPattern(extension.getUseCasePattern(), context.getUseCase())) score += 10;
        if (matchesPattern(extension.getScenarioPattern(), context.getScenario())) score += 1;
        if (matchesPattern(extension.getEnvPattern(), context.getEnv())) score += 10000;

        if (extension.getConditionPredicate() != null) {
            try {
                if (extension.getConditionPredicate().test(context)) {
                    score += 100000;
                } else {
                    return -1;
                }
            } catch (Exception e) {
                log.warn("Condition evaluation failed for extension: {}", extension.getCode(), e);
                return -1;
            }
        }
        return score;
    }

    private boolean matchesPattern(java.util.regex.Pattern pattern, String value) {
        return pattern != null && value != null && pattern.matcher(value).matches();
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
        log.info("All route cache cleared");
    }

    // ==================== 内部统计类 ====================

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