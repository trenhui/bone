package com.bone.smartmeta.engine.cache;

import lombok.Data;

/**
 * 缓存统计信息类
 */
@Data
public class CacheStats {
    private long hits = 0;
    private long misses = 0;
    private long totalRequests = 0;
    private long putOperations = 0;
    private long removeOperations = 0;
    private long evictions = 0;
    private long currentSize = 0;
    
    /**
     * 增加命中计数
     */
    public void incrementHits() {
        hits++;
        totalRequests++;
    }
    
    /**
     * 增加未命中计数
     */
    public void incrementMisses() {
        misses++;
        totalRequests++;
    }
    
    /**
     * 增加放入操作计数
     */
    public void incrementPuts() {
        putOperations++;
        currentSize++;
    }
    
    /**
     * 增加移除操作计数
     */
    public void incrementRemoves() {
        removeOperations++;
        if (currentSize > 0) {
            currentSize--;
        }
    }
    
    /**
     * 增加驱逐计数
     */
    public void incrementEvictions() {
        evictions++;
        if (currentSize > 0) {
            currentSize--;
        }
    }
    
    /**
     * 获取命中率
     */
    public double getHitRate() {
        return totalRequests > 0 ? (double) hits / totalRequests : 0.0;
    }
}