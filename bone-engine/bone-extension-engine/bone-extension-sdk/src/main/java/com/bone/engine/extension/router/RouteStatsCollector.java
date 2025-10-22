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
 * 负责收集和管理扩展点路由的性能指标和统计数据
 * </p>
 * 实现RouterComponent.StatsCollectorComponent接口
 * 继承AbstractRouterComponent获取生命周期管理能力
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
public class RouteStatsCollector extends AbstractRouterComponent implements RouterComponent.StatsCollectorComponent {


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
     * 记录路由统计信息
     * 
     * @param extPointType 扩展点类型
     * @param method 调用方法
     * @param implementationType 实现类型
     * @param executionTimeMs 执行时间（毫秒）
     * @param success 是否成功
     */
    public void recordRouteStats(Class<?> extPointType, Method method, Class<?> implementationType, 
                               long executionTimeMs, boolean success) {
        if (extPointType == null || method == null) {
            return;
        }

        String statsKey = buildStatsKey(extPointType, method);
        String implStatsKey = buildImplStatsKey(extPointType, method, implementationType);

        // 获取或创建统计对象
        RouteStats stats = routeStatsMap.computeIfAbsent(statsKey, k -> new RouteStats());
        RouteStats implStats = routeStatsMap.computeIfAbsent(implStatsKey, k -> new RouteStats());

        // 更新总统计
        stats.incrementTotalRequests();
        implStats.incrementTotalRequests();

        if (success) {
            stats.incrementSuccessRequests(executionTimeMs);
            implStats.incrementSuccessRequests(executionTimeMs);

            // 记录慢路由警告
            if (executionTimeMs > SLOW_ROUTE_THRESHOLD_MS) {
                logger.warn("Slow route detected: {}#{} via {} - took {}ms",
                        extPointType.getSimpleName(), method.getName(),
                        implementationType != null ? implementationType.getSimpleName() : "unknown",
                        executionTimeMs);
            }
        } else {
            stats.incrementFailedRequests();
            implStats.incrementFailedRequests();
            recordRouteFailure(extPointType, method, implementationType);
        }

        // 定期记录统计日志（可配置）
        if (stats.getTotalRequests() % 100 == 0) {
            logger.info("Route stats for {}#{}, implementation {}: {}",
                    extPointType.getSimpleName(), method.getName(),
                    implementationType != null ? implementationType.getSimpleName() : "overall",
                    implStats);
        }
    }

    /**
     * 记录路由失败
     * 
     * @param extPointType 扩展点类型
     * @param method 调用方法
     * @param implementationType 实现类型
     */
    public void recordRouteFailure(Class<?> extPointType, Method method, Class<?> implementationType) {
        if (extPointType == null || method == null) {
            return;
        }

        String failureKey = buildFailureKey(extPointType, method, implementationType);
        AtomicInteger counter = failureCounterMap.computeIfAbsent(failureKey, k -> new AtomicInteger(0));
        int failureCount = counter.incrementAndGet();

        // 失败次数达到阈值时记录告警
        if (failureCount == 5 || failureCount == 10 || failureCount % 50 == 0) {
            logger.error("High failure rate detected for {}#{} via {}: {} consecutive failures",
                    extPointType.getSimpleName(), method.getName(),
                    implementationType != null ? implementationType.getSimpleName() : "unknown",
                    failureCount);
        }
    }

    /**
     * 获取路由统计信息
     * 
     * @param extPointType 扩展点类型
     * @param method 调用方法
     * @return 统计信息，不存在则返回null
     */
    public RouteStats getRouteStats(Class<?> extPointType, Method method) {
        if (extPointType == null || method == null) {
            return null;
        }
        return routeStatsMap.get(buildStatsKey(extPointType, method));
    }

    /**
     * 获取特定实现的路由统计信息
     * 
     * @param extPointType 扩展点类型
     * @param method 调用方法
     * @param implementationType 实现类型
     * @return 统计信息，不存在则返回null
     */
    public RouteStats getImplementationStats(Class<?> extPointType, Method method, 
                                           Class<?> implementationType) {
        if (extPointType == null || method == null || implementationType == null) {
            return null;
        }
        return routeStatsMap.get(buildImplStatsKey(extPointType, method, implementationType));
    }

    /**
     * 重置特定扩展点方法的统计信息
     * 
     * @param extPointType 扩展点类型
     * @param method 调用方法
     */
    public void resetRouteStats(Class<?> extPointType, Method method) {
        if (extPointType == null || method == null) {
            return;
        }

        String statsKey = buildStatsKey(extPointType, method);
        routeStatsMap.remove(statsKey);
        
        // 移除所有相关实现的统计
        String prefix = extPointType.getName() + ":" + method.getName() + ":";
        routeStatsMap.keySet().removeIf(key -> key.startsWith(prefix));
        failureCounterMap.keySet().removeIf(key -> key.startsWith(prefix));
        
        logger.info("Reset route stats for {}#{}", extPointType.getSimpleName(), method.getName());
    }

    /**
     * 重置所有统计信息
     */
    @Override
    public void resetAllStats() {
        routeStatsMap.clear();
        failureCounterMap.clear();
        implementationStatsMap.clear();
        logger.info("Reset all route stats");
    }
    
    @Override
    public void recordRouteStats(Class<?> extPointClass, Object implementation) {
        // 记录路由统计
        String extPointName = extPointClass.getSimpleName();
        String implName = implementation != null ? implementation.getClass().getSimpleName() : "Unknown";
        String statsKey = buildStatsKey(extPointName, implName);
        
        synchronized (routeStatsMap) {
            RouteStats stats = routeStatsMap.computeIfAbsent(statsKey, k -> new RouteStats());
            stats.incrementTotalRequests();
            stats.incrementSuccessRequests(0); // 使用0作为默认执行时间
        }
    }
    
    @Override
    public void recordRouteFailure(Class<?> extPointClass, Throwable ex) {
        // 记录路由失败
        String extPointName = extPointClass.getSimpleName();
        String errorType = ex != null ? ex.getClass().getSimpleName() : "UnknownError";
        String failureKey = buildFailureKey(extPointName, errorType);
        
        // 增加失败计数
        synchronized (failureCounterMap) {
            AtomicInteger counter = failureCounterMap.computeIfAbsent(failureKey, k -> new AtomicInteger(0));
            counter.incrementAndGet();
        }
        
        // 记录详细异常信息
        logger.error("Route failed for {}", extPointName, ex);
    }
    
    @Override
    public void recordMetrics(Class<?> extPointClass, boolean success, long costTime, long warningThreshold) {
        // 记录路由指标
        String extPointName = extPointClass.getSimpleName();
        String metricsKey = buildStatsKey(extPointName, success ? "success" : "failure");
        
        synchronized (routeStatsMap) {
            RouteStats stats = routeStatsMap.computeIfAbsent(metricsKey, k -> new RouteStats());
            stats.incrementTotalRequests();
            if (success) {
                stats.incrementSuccessRequests(costTime);
            } else {
                stats.incrementFailedRequests();
            }
            
            // 记录慢路由警告
            if (costTime > warningThreshold) {
                logger.warn("Slow route detected: {} took {}ms", extPointName, costTime);
            }
        }
    }
    
    @Override
    public Map<String, Map<String, Object>> getRouteStats() {
        Map<String, Map<String, Object>> result = new HashMap<>();
        
        synchronized (routeStatsMap) {
            for (Map.Entry<String, RouteStats> entry : routeStatsMap.entrySet()) {
                String key = entry.getKey();
                RouteStats stats = entry.getValue();
                
                Map<String, Object> statMap = new HashMap<>();
                statMap.put("totalRequests", stats.getTotalRequests());
                statMap.put("successRequests", stats.getSuccessRequests());
                statMap.put("failedRequests", stats.getFailedRequests());
                statMap.put("avgExecutionTimeMs", stats.getAverageExecutionTimeMs());
                statMap.put("successRate", stats.getSuccessRate());
                
                result.put(key, statMap);
            }
        }
        
        return result;
    }
    
    @Override
    public Map<String, Object> getImplementationStats(String implementationName) {
        ensureInitialized();
        Map<String, Object> result = new HashMap<>();
        
        for (Map.Entry<String, RouteStats> entry : implementationStatsMap.entrySet()) {
            if (entry.getKey().endsWith(implementationName)) {
                RouteStats stats = entry.getValue();
                result.put("totalRequests", stats.getTotalRequests());
                result.put("successRequests", stats.getSuccessRequests());
                result.put("failedRequests", stats.getFailedRequests());
                result.put("avgExecutionTimeMs", stats.getAverageExecutionTimeMs());
                break;
            }
        }
        
        return result.isEmpty() ? new HashMap<>() : result;
    }
    
    @Override
    public void resetRouteStats() {
        routeStatsMap.clear();
        failureCounterMap.clear();
        logger.info("Reset all route stats");
    }
    
    /**
     * 记录成功路由（兼容旧接口）
     */
    public void recordRouteSuccess(Class<?> extPointType, Object implementation, long timeMs, BizContext context) {
        ensureInitialized();
        recordRouteStats(extPointType, implementation);
        if (implementation != null) {
            recordMetrics(extPointType, true, timeMs, SLOW_ROUTE_THRESHOLD_MS);
        }
    }
    
    /**
     * 记录失败路由（兼容旧接口）
     */
    public void recordRouteFailure(Class<?> extPointType, Exception exception, BizContext context) {
        ensureInitialized();
        recordRouteFailure(extPointType, exception);
        if (extPointType != null) {
            recordMetrics(extPointType, false, 0, SLOW_ROUTE_THRESHOLD_MS);
        }
    }
    
    /**
     * 获取特定扩展点的路由统计（兼容旧接口）
     */
    public Map<String, Object> getRouteStats(Class<?> extPointType) {
        ensureInitialized();
        
        if (extPointType != null) {
            String extPointName = extPointType.getSimpleName();
            Map<String, Map<String, Object>> allStats = getRouteStats();
            
            for (Map.Entry<String, Map<String, Object>> entry : allStats.entrySet()) {
                if (entry.getKey().startsWith(extPointName + ":")) {
                    return entry.getValue();
                }
            }
        }
        return new HashMap<>();
    }
    
    /**
     * 获取特定实现的统计（兼容旧接口）
     */
    public Map<String, Object> getImplementationStats(Object implementation) {
        String implName = implementation != null ? implementation.getClass().getSimpleName() : "Unknown";
        return getImplementationStats(implName);
    }
    
    /**
     * 重置特定扩展点的路由统计（兼容旧接口）
     */
    public void resetRouteStats(Class<?> extPointType) {
        ensureInitialized();
        
        if (extPointType != null) {
            String prefix = extPointType.getName() + ":";
            // 清除相关的所有统计记录
            routeStatsMap.keySet().removeIf(key -> key.startsWith(prefix));
            failureCounterMap.keySet().removeIf(key -> key.startsWith(prefix));
            
            logger.info("Reset route stats for {}", extPointType.getName());
        }
    }
    
    /**
     * 获取所有路由统计（兼容旧接口）
     */
    public Map<String, Map<String, Object>> getAllRouteStats() {
        return getRouteStats();
    }
    
    @Override
    public String getComponentName() {
        return "RouteStatsCollector";
    }
    
    @Override
    protected void doInitialize() throws Exception {
        logger.info("RouteStatsCollector initialized");
    }
    
    @Override
    protected void doShutdown() {
        resetAllStats();
        logger.info("RouteStatsCollector shut down");
    }

    /**
     * 构建统计键
     */
    private String buildStatsKey(Class<?> extPointType, Method method) {
        return extPointType.getName() + ":" + method.getName();
    }

    /**
     * 构建实现统计键
     */
    private String buildImplStatsKey(Class<?> extPointType, Method method, Class<?> implementationType) {
        return buildStatsKey(extPointType, method) + ":" + 
               (implementationType != null ? implementationType.getName() : "unknown");
    }

    /**
     * 构建失败统计键
     */
    private String buildFailureKey(Class<?> extPointType, Method method, Class<?> implementationType) {
        return buildImplStatsKey(extPointType, method, implementationType) + ":failure";
    }

    /**
     * 获取当前统计摘要
     */
    public Map<String, Object> getStatsSummary() {
        Map<String, Object> summary = new ConcurrentHashMap<>();
        summary.put("totalStatsEntries", routeStatsMap.size());
        summary.put("totalFailureCounters", failureCounterMap.size());
        
        // 计算全局统计
        int totalRequests = 0;
        int totalSuccess = 0;
        int totalFailures = 0;
        long totalTime = 0;
        
        for (RouteStats stats : routeStatsMap.values()) {
            totalRequests += stats.getTotalRequests();
            totalSuccess += stats.getSuccessRequests();
            totalFailures += stats.getFailedRequests();
            totalTime += stats.getTotalExecutionTimeMs();
        }
        
        summary.put("totalRequests", totalRequests);
        summary.put("totalSuccessRequests", totalSuccess);
        summary.put("totalFailedRequests", totalFailures);
        summary.put("overallSuccessRate", totalRequests > 0 ? 
                    String.format("%.2f%%", (double) totalSuccess / totalRequests * 100) : "0%");
        summary.put("avgExecutionTimeMs", totalSuccess > 0 ? 
                    String.format("%.2f", (double) totalTime / totalSuccess) : "0.00");
        
        return summary;
    }
}