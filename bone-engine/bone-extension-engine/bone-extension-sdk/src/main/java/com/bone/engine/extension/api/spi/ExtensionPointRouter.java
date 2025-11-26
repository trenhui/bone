package com.bone.engine.extension.api.spi;

import com.bone.engine.extension.support.context.BizContext;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Map;

/**
 * 统一的扩展点路由器接口
 */
public interface ExtensionPointRouter {

    void stop();

    /**
     * 为扩展点选择实现
     */
    @Nullable
    <T> T route(@NonNull Class<T> extPointClass, @NonNull BizContext<?> context);

    /**
     * 预热所有扩展点
     */
    void warmupAll();

    /**
     * 预热指定扩展点
     */
    void warmup(@NonNull Class<?> extPointClass);

    /**
     * 清理扩展点缓存
     */
    void clearCache(@NonNull Class<?> extPointClass);

    /**
     * 注册扩展点实现
     */
    <T> void registerImplementation(@NonNull Class<T> extPointClass, @NonNull T implementation);

    /**
     * 取消注册扩展点实现
     */
    <T> void unregisterImplementation(@NonNull Class<T> extPointClass, @NonNull T implementation);

    /**
     * 获取默认实现
     */
    @Nullable
    <T> T getDefaultImplementation(@NonNull Class<T> extPointClass);

    Map<String, Map<String, Long>> getRouteStats();

    /**
     * 获取路由统计信息
     */
    @NonNull
    RouterStats getStats();

    /**
     * 获取路由器状态
     */
    @NonNull
    RouterStatus getStatus();

    /**
     * 重置路由统计
     */
    void resetStats();

    /**
     * 路由统计信息
     */
    class RouterStats {
        private final long totalRequests;
        private final long cacheHits;
        private final long successfulRequests;
        private final double avgResponseTime;

        public RouterStats(long totalRequests, long cacheHits,
                           long successfulRequests, double avgResponseTime) {
            this.totalRequests = totalRequests;
            this.cacheHits = cacheHits;
            this.successfulRequests = successfulRequests;
            this.avgResponseTime = avgResponseTime;
        }

        public long getTotalRequests() { return totalRequests; }
        public long getCacheHits() { return cacheHits; }
        public long getSuccessfulRequests() { return successfulRequests; }
        public double getAvgResponseTime() { return avgResponseTime; }

        public double getCacheHitRate() {
            return totalRequests > 0 ? (double) cacheHits / totalRequests : 0.0;
        }

        public double getSuccessRate() {
            return totalRequests > 0 ? (double) successfulRequests / totalRequests : 0.0;
        }
    }

    /**
     * 路由器状态信息
     */
    class RouterStatus {
        private final int pointCount;
        private final int extensionCount;
        private final long cacheSize;
        private final boolean warmedUp;

        public RouterStatus(int pointCount, int extensionCount,
                            long cacheSize, boolean warmedUp) {
            this.pointCount = pointCount;
            this.extensionCount = extensionCount;
            this.cacheSize = cacheSize;
            this.warmedUp = warmedUp;
        }

        public int getPointCount() { return pointCount; }
        public int getExtensionCount() { return extensionCount; }
        public long getCacheSize() { return cacheSize; }
        public boolean isWarmedUp() { return warmedUp; }
    }
}