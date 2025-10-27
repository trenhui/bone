package com.bone.engine.extension.router;

import com.bone.engine.extension.context.BizContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 路由统计收集器
 * <p>
 * 负责收集和管理扩展点路由的性能指标和统计数据，包括事件统计功能
 * </p>
 * 实现RouterComponent.StatsCollectorComponent接口
 * 继承AbstractRouterComponent获取生命周期管理能力
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
public class RouteStatsCollector extends AbstractRouterComponent implements RouterComponent.StatsCollectorComponent {

    // 事件统计计数器
    private final AtomicInteger totalPublishedEvents = new AtomicInteger(0);
    private final AtomicInteger asyncEvents = new AtomicInteger(0);
    private final AtomicInteger syncEvents = new AtomicInteger(0);
    private final AtomicInteger failedEvents = new AtomicInteger(0);


    private static final long SLOW_ROUTE_THRESHOLD_MS = 100; // 慢路由阈值（毫秒）

    /**
     * 路由统计数据类
     */
    public static class RouteStats {
        private final AtomicInteger totalRequests = new AtomicInteger(0);
        private final AtomicInteger successRequests = new AtomicInteger(0);
        private final AtomicInteger failedRequests = new AtomicInteger(0);
        private final AtomicLong totalExecutionTimeMs = new AtomicLong(0);
        private final AtomicLong maxExecutionTimeMs = new AtomicLong(0);
        private final AtomicLong minExecutionTimeMs = new AtomicLong(Long.MAX_VALUE);
        private LocalDateTime lastRequestTime;
        private LocalDateTime lastSuccessTime;
        private LocalDateTime lastFailureTime;

        public void incrementTotalRequests() {
            totalRequests.incrementAndGet();
            lastRequestTime = LocalDateTime.now();
        }

        public void incrementSuccessRequests(long executionTimeMs) {
            successRequests.incrementAndGet();
            updateExecutionTime(executionTimeMs);
            lastSuccessTime = LocalDateTime.now();
        }

        public void incrementFailedRequests() {
            failedRequests.incrementAndGet();
            lastFailureTime = LocalDateTime.now();
        }

        private void updateExecutionTime(long executionTimeMs) {
            totalExecutionTimeMs.addAndGet(executionTimeMs);
            updateMaxExecutionTime(executionTimeMs);
            updateMinExecutionTime(executionTimeMs);
        }

        private void updateMaxExecutionTime(long executionTimeMs) {
            long currentMax;
            do {
                currentMax = maxExecutionTimeMs.get();
                if (executionTimeMs <= currentMax) {
                    break;
                }
            } while (!maxExecutionTimeMs.compareAndSet(currentMax, executionTimeMs));
        }

        private void updateMinExecutionTime(long executionTimeMs) {
            long currentMin;
            do {
                currentMin = minExecutionTimeMs.get();
                if (executionTimeMs >= currentMin) {
                    break;
                }
            } while (!minExecutionTimeMs.compareAndSet(currentMin, executionTimeMs));
        }

        public double getAverageExecutionTimeMs() {
            int successCount = successRequests.get();
            return successCount > 0 ? 
                   (double) totalExecutionTimeMs.get() / successCount : 0;
        }

        public double getSuccessRate() {
            int totalCount = totalRequests.get();
            return totalCount > 0 ? 
                   (double) successRequests.get() / totalCount * 100 : 100;
        }

        // Getters
        public int getTotalRequests() { return totalRequests.get(); }
        public int getSuccessRequests() { return successRequests.get(); }
        public int getFailedRequests() { return failedRequests.get(); }
        public long getTotalExecutionTimeMs() { return totalExecutionTimeMs.get(); }
        public long getMaxExecutionTimeMs() { 
            long max = maxExecutionTimeMs.get();
            return max == Long.MAX_VALUE ? 0 : max;
        }
        public long getMinExecutionTimeMs() { 
            long min = minExecutionTimeMs.get();
            return min == Long.MAX_VALUE ? 0 : min;
        }
        public LocalDateTime getLastRequestTime() { return lastRequestTime; }
        public LocalDateTime getLastSuccessTime() { return lastSuccessTime; }
        public LocalDateTime getLastFailureTime() { return lastFailureTime; }

        @Override
        public String toString() {
            return "RouteStats{" +
                   "totalRequests=" + totalRequests +
                   ", successRequests=" + successRequests +
                   ", failedRequests=" + failedRequests +
                   ", avgExecutionTimeMs=" + String.format("%.2f", getAverageExecutionTimeMs()) +
                   ", maxExecutionTimeMs=" + getMaxExecutionTimeMs() +
                   ", minExecutionTimeMs=" + getMinExecutionTimeMs() +
                   ", successRate=" + String.format("%.2f%%", getSuccessRate()) +
                   ", lastRequestTime=" + lastRequestTime +
                   "}";
        }
    }

    // 统计数据存储
    private final Map<String, RouteStats> routeStatsMap = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> failureCounterMap = new ConcurrentHashMap<>();
    private final Map<String, RouteStats> implementationStatsMap = new ConcurrentHashMap<>(); // 单独存储实现类的统计信息

    /**
     * 构建统计键
     */
    private String buildStatsKey(Class<?> extPointClass, Method method) {
        return extPointClass.getSimpleName() + ":" + method.getName();
    }
    
    /**
     * 构建实现统计键
     */
    private String buildImplStatsKey(Class<?> extPointClass, Method method, Class<?> implementationType) {
        String implName = implementationType != null ? implementationType.getSimpleName() : "unknown";
        return buildStatsKey(extPointClass, method) + ":" + implName;
    }
    
    /**
     * 构建失败键
     */
    private String buildFailureKey(Class<?> extPointClass, Method method, Class<?> implementationType) {
        return buildImplStatsKey(extPointClass, method, implementationType) + ":failure";
    }
    
    /**
     * 记录路由统计信息内部实现
     */
    private void recordRouteStatsInternal(Class<?> extPointClass, Method method, 
                                        Class<?> implementationType, long executionTimeMs, boolean success) {
        if (extPointClass == null || method == null) {
            return;
        }

        String statsKey = buildStatsKey(extPointClass, method);
        String implStatsKey = buildImplStatsKey(extPointClass, method, implementationType);

        // 获取或创建统计对象
        RouteStats stats = routeStatsMap.computeIfAbsent(statsKey, k -> new RouteStats());
        RouteStats implStats = implementationStatsMap.computeIfAbsent(implStatsKey, k -> new RouteStats());

        // 更新总统计
        stats.incrementTotalRequests();
        implStats.incrementTotalRequests();

        if (success) {
            stats.incrementSuccessRequests(executionTimeMs);
            implStats.incrementSuccessRequests(executionTimeMs);

            // 记录慢路由警告
            if (executionTimeMs > SLOW_ROUTE_THRESHOLD_MS) {
                logger.warn("Slow route detected: {}#{} via {} - took {}ms",
                        extPointClass.getSimpleName(), method.getName(),
                        implementationType != null ? implementationType.getSimpleName() : "unknown",
                        executionTimeMs);
            }
        } else {
            stats.incrementFailedRequests();
            implStats.incrementFailedRequests();
            recordRouteFailureInternal(extPointClass, method, implementationType, null);
        }

        // 定期记录统计日志（可配置）
        if (stats.getTotalRequests() % 100 == 0) {
            logger.info("Route stats for {}#{}, implementation {}: {}",
                    extPointClass.getSimpleName(), method.getName(),
                    implementationType != null ? implementationType.getSimpleName() : "overall",
                    implStats);
        }
    }

    /**
     * 记录路由失败内部实现
     */
    private void recordRouteFailureInternal(Class<?> extPointClass, Method method, 
                                          Class<?> implementationType, Throwable ex) {
        if (extPointClass == null || method == null) {
            return;
        }
        
        // 构建统计键
        String statsKey = buildStatsKey(extPointClass, method);
        String implStatsKey = buildImplStatsKey(extPointClass, method, implementationType);
        String failureKey = buildFailureKey(extPointClass, method, implementationType);
        
        // 更新路由统计
        routeStatsMap.computeIfAbsent(statsKey, k -> new RouteStats())
                .incrementFailedRequests();
                
        // 更新实现类统计
        implementationStatsMap.computeIfAbsent(implStatsKey, k -> new RouteStats())
                .incrementFailedRequests();
        
        // 更新失败计数
        AtomicInteger counter = failureCounterMap.computeIfAbsent(failureKey, k -> new AtomicInteger(0));
        int failureCount = counter.incrementAndGet();

        // 失败次数达到阈值时记录告警
        if (failureCount == 5 || failureCount == 10 || failureCount % 50 == 0) {
            if (ex != null) {
                logger.error("High failure rate detected for {}#{} via {}: {} consecutive failures",
                        extPointClass.getSimpleName(), method.getName(),
                        implementationType != null ? implementationType.getSimpleName() : "unknown",
                        failureCount, ex);
            } else {
                logger.error("High failure rate detected for {}#{} via {}: {} consecutive failures",
                        extPointClass.getSimpleName(), method.getName(),
                        implementationType != null ? implementationType.getSimpleName() : "unknown",
                        failureCount);
            }
        }
    }
    
    // ----------- 接口方法实现 -----------
    
    /**
     * 记录路由统计信息（接口实现）
     */
    @Override
    public void recordRouteStats(Class<?> extPointClass, Method method, 
                                Class<?> implementationType, long executionTimeMs, boolean success) {
        recordRouteStatsInternal(extPointClass, method, implementationType, executionTimeMs, success);
    }
    
    /**
     * 记录路由失败（接口实现）
     */
    @Override
    public void recordRouteFailure(Class<?> extPointClass, Method method, 
                                  Class<?> implementationType) {
        recordRouteFailureInternal(extPointClass, method, implementationType, null);
    }
    
    /**
     * 获取路由统计（接口实现）
     */
    @Override
    public Map<String, Map<String, Long>> getRouteStats() {
        Map<String, Map<String, Long>> result = new HashMap<>();
        
        synchronized (routeStatsMap) {
            for (Map.Entry<String, RouteStats> entry : routeStatsMap.entrySet()) {
                String key = entry.getKey();
                RouteStats stats = entry.getValue();
                
                Map<String, Long> statMap = new HashMap<>();
                statMap.put("totalRequests", (long)stats.getTotalRequests());
                statMap.put("successRequests", (long)stats.getSuccessRequests());
                statMap.put("failedRequests", (long)stats.getFailedRequests());
                statMap.put("totalExecutionTimeMs", stats.getTotalExecutionTimeMs());
                statMap.put("maxExecutionTimeMs", stats.getMaxExecutionTimeMs());
                statMap.put("minExecutionTimeMs", stats.getMinExecutionTimeMs());
                
                result.put(key, statMap);
            }
        }
        
        return result;
    }
    
    /**
     * 获取实现统计（接口实现）
     */
    @Override
    public Map<String, Long> getImplementationStats(String implementationName) {
        ensureInitialized();
        Map<String, Long> result = new HashMap<>();
        
        for (Map.Entry<String, RouteStats> entry : implementationStatsMap.entrySet()) {
            if (entry.getKey().endsWith(implementationName)) {
                RouteStats stats = entry.getValue();
                result.put("totalRequests", (long)stats.getTotalRequests());
                result.put("successRequests", (long)stats.getSuccessRequests());
                result.put("failedRequests", (long)stats.getFailedRequests());
                result.put("totalExecutionTimeMs", stats.getTotalExecutionTimeMs());
                result.put("maxExecutionTimeMs", stats.getMaxExecutionTimeMs());
                result.put("minExecutionTimeMs", stats.getMinExecutionTimeMs());
                break;
            }
        }
        
        // 同时也检查routeStatsMap
        if (result.isEmpty()) {
            for (Map.Entry<String, RouteStats> entry : routeStatsMap.entrySet()) {
                if (entry.getKey().contains(implementationName)) {
                    RouteStats stats = entry.getValue();
                    result.put("totalRequests", (long)stats.getTotalRequests());
                    result.put("successRequests", (long)stats.getSuccessRequests());
                    result.put("failedRequests", (long)stats.getFailedRequests());
                    result.put("totalExecutionTimeMs", stats.getTotalExecutionTimeMs());
                    result.put("maxExecutionTimeMs", stats.getMaxExecutionTimeMs());
                    result.put("minExecutionTimeMs", stats.getMinExecutionTimeMs());
                    break;
                }
            }
        }
        
        return result;
    }
    
    /**
     * 重置所有统计信息（接口实现）
     */
    @Override
    public void resetAllStats() {
        synchronized (routeStatsMap) {
            routeStatsMap.clear();
            failureCounterMap.clear();
            implementationStatsMap.clear();
            logger.info("All route statistics have been reset");
        }
    }
    
    /**
     * 重置特定扩展点的统计（接口实现）
     */
    @Override
    public void resetStatsForExtPoint(Class<?> extPointClass) {
        if (extPointClass == null) {
            return;
        }
        
        String extPointPrefix = extPointClass.getSimpleName();
        synchronized (routeStatsMap) {
            // 移除扩展点相关的统计数据
            routeStatsMap.keySet().removeIf(key -> key.startsWith(extPointPrefix));
            implementationStatsMap.keySet().removeIf(key -> key.startsWith(extPointPrefix));
            failureCounterMap.keySet().removeIf(key -> key.startsWith(extPointPrefix));
            
            logger.info("Route statistics for {} have been reset", extPointPrefix);
        }
    }
    
    // ----------- 向后兼容方法 -----------
    
    /**
     * 记录路由统计信息（向后兼容方法）
     */
    public void recordRouteStats(Class<?> extPointClass, Object implementation) {
        // 提供默认实现以保持向后兼容性
        if (extPointClass != null && implementation != null) {
            try {
                // 获取第一个方法作为代表方法
                Method[] methods = extPointClass.getMethods();
                if (methods.length > 0) {
                    recordRouteStatsInternal(extPointClass, methods[0], implementation.getClass(), 0, true);
                }
            } catch (Exception e) {
                logger.error("Error in recordRouteStats for {}", extPointClass.getSimpleName(), e);
            }
        }
    }
    
    /**
     * 记录路由失败（向后兼容方法）
     */
    public void recordRouteFailure(Class<?> extPointClass, Throwable ex) {
        // 提供默认实现以保持向后兼容性
        if (extPointClass != null) {
            try {
                // 获取第一个方法作为代表方法
                Method[] methods = extPointClass.getMethods();
                if (methods.length > 0) {
                    recordRouteFailureInternal(extPointClass, methods[0], null, ex);
                }
            } catch (Exception e) {
                logger.error("Error in recordRouteFailure for {}", extPointClass.getSimpleName(), e);
            }
        }
    }
    
    /**
     * 记录路由失败（向后兼容方法）
     */
    public void recordRouteFailure(Class<?> extPointClass, Exception ex, Object context) {
        // 提供默认实现以保持向后兼容性
        recordRouteFailure(extPointClass, ex);
    }
    
    /**
     * 记录路由指标（向后兼容方法）
     */
    public void recordMetrics(Class<?> extPointClass, boolean success, long costTime, long warningThreshold) {
        if (extPointClass == null) {
            return;
        }
        
        try {
            // 获取扩展点的第一个方法作为代表方法
            Method[] methods = extPointClass.getMethods();
            Method representativeMethod = methods.length > 0 ? methods[0] : null;
            
            if (representativeMethod != null) {
                // 调用内部实现方法
                recordRouteStatsInternal(extPointClass, representativeMethod, null, costTime, success);
            }
        } catch (Exception e) {
            logger.error("Error recording metrics for {}", extPointClass.getSimpleName(), e);
        }
    }
    
    /**
     * 重置路由统计（向后兼容方法）
     */
    public void resetRouteStats() {
        // 重置所有路由统计数据
        resetAllStats();
    }
    
    /**
     * 重置特定扩展点方法的统计信息（向后兼容方法）
     */
    public void resetRouteStats(Class<?> extPointType, Method method) {
        if (extPointType == null || method == null) {
            return;
        }

        String statsKey = buildStatsKey(extPointType, method);
        routeStatsMap.remove(statsKey);
        
        // 移除所有相关实现的统计
        String prefix = extPointType.getSimpleName() + ":" + method.getName() + ":";
        routeStatsMap.keySet().removeIf(key -> key.startsWith(prefix));
        implementationStatsMap.keySet().removeIf(key -> key.startsWith(prefix));
        failureCounterMap.keySet().removeIf(key -> key.startsWith(prefix));
        
        logger.info("Reset route stats for {}#{}", extPointType.getSimpleName(), method.getName());
    }
    
    /**
     * 获取路由统计信息（向后兼容方法）
     */
    public RouteStats getRouteStats(Class<?> extPointType, Method method) {
        if (extPointType == null || method == null) {
            return null;
        }
        return routeStatsMap.get(buildStatsKey(extPointType, method));
    }

    /**
     * 获取特定实现的路由统计信息（向后兼容方法）
     */
    public RouteStats getImplementationStats(Class<?> extPointType, Method method, 
                                           Class<?> implementationType) {
        if (extPointType == null || method == null || implementationType == null) {
            return null;
        }
        return implementationStatsMap.get(buildImplStatsKey(extPointType, method, implementationType));
    }
    
    // ----------- RouterComponent接口实现 -----------
    
    @Override
    protected void doInitialize() throws Exception {
        // 初始化路由统计收集器
        logger.info("RouteStatsCollector initialized");
    }
    
    @Override
    protected void doShutdown() {
        // 清理资源
        resetAllStats();
        logger.info("RouteStatsCollector shutdown");
    }
    
    @Override
    public String getComponentName() {
        return "RouteStatsCollector";
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    // ----------- 事件统计相关方法 -----------
    
    public void incrementTotalPublishedEvents() {
        totalPublishedEvents.incrementAndGet();
    }
    
    public void incrementAsyncEvents() {
        asyncEvents.incrementAndGet();
    }
    
    public void incrementSyncEvents() {
        syncEvents.incrementAndGet();
    }
    
    public void incrementFailedEvents() {
        failedEvents.incrementAndGet();
    }
    
    public int getTotalPublishedEvents() {
        return totalPublishedEvents.get();
    }
    
    public int getAsyncEvents() {
        return asyncEvents.get();
    }
    
    public int getSyncEvents() {
        return syncEvents.get();
    }
    
    public int getFailedEvents() {
        return failedEvents.get();
    }
    
    /**
     * 获取统计摘要
     */
    public Map<String, Object> getStatsSummary() {
        Map<String, Object> summary = new HashMap<>();
        int totalRequests = 0;
        int totalSuccess = 0;
        long totalTime = 0;
        
        // 汇总所有路由统计
        for (RouteStats stats : routeStatsMap.values()) {
            totalRequests += stats.getTotalRequests();
            totalSuccess += stats.getSuccessRequests();
            totalTime += stats.getTotalExecutionTimeMs();
        }
        
        summary.put("totalRequests", totalRequests);
        summary.put("totalSuccess", totalSuccess);
        summary.put("totalFailed", totalRequests - totalSuccess);
        summary.put("overallSuccessRate", totalRequests > 0 ? 
                    String.format("%.2f%%", (double) totalSuccess / totalRequests * 100) : "0%");
        summary.put("avgExecutionTimeMs", totalSuccess > 0 ? 
                    String.format("%.2f", (double) totalTime / totalSuccess) : "0.00");
        
        // 添加事件统计信息
        summary.put("totalPublishedEvents", totalPublishedEvents.get());
        summary.put("asyncEvents", asyncEvents.get());
        summary.put("syncEvents", syncEvents.get());
        summary.put("failedEvents", failedEvents.get());
        summary.put("eventSuccessRate", totalPublishedEvents.get() > 0 ?
                    String.format("%.2f%%", (double)(totalPublishedEvents.get() - failedEvents.get()) / totalPublishedEvents.get() * 100) : "100%");
        
        return summary;
    }
}