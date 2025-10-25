package com.bone.smartmeta.engine.rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 表达式缓存
 * 负责缓存预编译的表达式以提高性能
 */
@Component
@ConfigurationProperties(prefix = "bone.smartmeta.expression.cache")
public class ExpressionCache {

    private static final Logger log = LoggerFactory.getLogger(ExpressionCache.class);

    // 默认缓存大小
    private static final int DEFAULT_MAX_SIZE = 1000;
    
    // 缓存大小限制
    private int maxSize = DEFAULT_MAX_SIZE;
    
    // 缓存统计
    private final AtomicInteger hits = new AtomicInteger(0);
    private final AtomicInteger misses = new AtomicInteger(0);
    
    // 表达式缓存
    private final Map<String, CachedExpression> cache = new ConcurrentHashMap<>();

    /**
     * 获取缓存的表达式，如果不存在则编译并缓存
     * 
     * @param expressionString 表达式字符串
     * @param compiler 表达式编译器
     * @return 编译后的表达式
     */
    public <T> T get(String expressionString, Function<String, T> compiler) {
        if (expressionString == null || expressionString.trim().isEmpty()) {
            throw new IllegalArgumentException("表达式字符串不能为空");
        }
        
        // 检查缓存中是否存在
        @SuppressWarnings("unchecked")
        CachedExpression<T> cached = (CachedExpression<T>) cache.get(expressionString);
        if (cached != null) {
            hits.incrementAndGet();
            log.debug("表达式缓存命中: {}", expressionString);
            return cached.getExpression();
        }
        
        // 缓存未命中，编译表达式
        misses.incrementAndGet();
        log.debug("表达式缓存未命中，编译: {}", expressionString);
        
        try {
            // 编译表达式
            T compiledExpression = compiler.apply(expressionString);
            
            // 检查缓存大小限制
            if (cache.size() >= maxSize) {
                evictCache();
            }
            
            // 缓存编译后的表达式
            cache.put(expressionString, new CachedExpression<>(compiledExpression));
            log.debug("表达式已缓存，当前缓存大小: {}", cache.size());
            
            return compiledExpression;
        } catch (Exception e) {
            log.error("编译表达式失败: {}", expressionString, e);
            throw new ExpressionCompilationException("编译表达式失败: " + expressionString, e);
        }
    }

    /**
     * 清除缓存
     */
    public void clear() {
        cache.clear();
        hits.set(0);
        misses.set(0);
        log.info("已清空表达式缓存");
    }

    /**
     * 从缓存中移除指定表达式
     */
    public void remove(String expressionString) {
        if (expressionString != null) {
            cache.remove(expressionString);
            log.debug("已从缓存中移除表达式: {}", expressionString);
        }
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        int total = hits.get() + misses.get();
        double hitRate = total > 0 ? (double) hits.get() / total : 0;
        
        return new CacheStats(
            cache.size(),
            maxSize,
            hits.get(),
            misses.get(),
            hitRate
        );
    }

    /**
     * 缓存驱逐策略实现
     * 简单实现：保留最新的表达式，移除最旧的
     */
    private void evictCache() {
        int evictCount = Math.max(1, cache.size() / 10); // 驱逐10%
        log.warn("表达式缓存达到上限，开始驱逐: {}/{} 条目", evictCount, cache.size());
        
        // 简单实现：移除前N个条目
        int count = 0;
        for (Map.Entry<String, CachedExpression> entry : cache.entrySet()) {
            cache.remove(entry.getKey());
            count++;
            if (count >= evictCount) {
                break;
            }
        }
        
        log.info("已驱逐 {} 个表达式缓存条目，剩余: {}", count, cache.size());
    }

    // Getters and Setters
    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = Math.max(100, maxSize); // 最小100
        log.info("表达式缓存最大大小设置为: {}", this.maxSize);
    }

    /**
     * 缓存的表达式
     */
    private static class CachedExpression<T> {
        private final T expression;
        private final long timestamp;

        public CachedExpression(T expression) {
            this.expression = expression;
            this.timestamp = System.currentTimeMillis();
        }

        public T getExpression() {
            return expression;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final int size;
        private final int maxSize;
        private final int hits;
        private final int misses;
        private final double hitRate;

        public CacheStats(int size, int maxSize, int hits, int misses, double hitRate) {
            this.size = size;
            this.maxSize = maxSize;
            this.hits = hits;
            this.misses = misses;
            this.hitRate = hitRate;
        }

        public int getSize() {
            return size;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public int getHits() {
            return hits;
        }

        public int getMisses() {
            return misses;
        }

        public double getHitRate() {
            return hitRate;
        }

        @Override
        public String toString() {
            return "CacheStats{" +
                   "size=" + size +
                   ", maxSize=" + maxSize +
                   ", hits=" + hits +
                   ", misses=" + misses +
                   ", hitRate=" + String.format("%.2f%%", hitRate * 100) +
                   '}';
        }
    }

    /**
     * 表达式编译异常
     */
    public static class ExpressionCompilationException extends RuntimeException {
        public ExpressionCompilationException(String message) {
            super(message);
        }

        public ExpressionCompilationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}